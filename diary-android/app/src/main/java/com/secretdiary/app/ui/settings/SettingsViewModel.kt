package com.secretdiary.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secretdiary.app.data.local.SecretDiaryDatabase
import com.secretdiary.app.data.remote.api.ApiService
import com.secretdiary.app.data.remote.dto.*
import com.secretdiary.app.data.repository.DiaryRepository
import com.secretdiary.app.security.CryptoManager
import com.secretdiary.app.security.SaltPreferencesManager
import com.secretdiary.app.security.SessionManager
import com.secretdiary.app.sync.SyncManager
import com.secretdiary.app.util.Base64Util
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val username: String = "",
    val hasRecovery: Boolean = false,
    val lastSyncTime: String? = null,
    val isBiometricEnabled: Boolean = false,
    val isSyncing: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiService: ApiService,
    private val cryptoManager: CryptoManager,
    private val sessionManager: SessionManager,
    private val syncManager: SyncManager,
    private val repository: DiaryRepository,
    private val database: SecretDiaryDatabase,
    private val saltPrefs: SaltPreferencesManager,
    @ApplicationContext private val context: Context  // 仅用于 cacheDir/filesDir 清理
) : ViewModel() {
    private var syncObserverJob: Job? = null

    private val _uiState = MutableStateFlow(SettingsUiState(
        username = sessionManager.getUsername() ?: "",
        hasRecovery = sessionManager.hasRecovery(),
        isBiometricEnabled = sessionManager.isBiometricEnabled()
    ))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSyncTime()
    }

    private fun loadSyncTime() {
        viewModelScope.launch { _uiState.value = _uiState.value.copy(lastSyncTime = repository.getLastSyncTimestamp()) }
    }

    fun triggerSync() {
        syncManager.performSync()
        _uiState.value = _uiState.value.copy(isSyncing = true)
        // 取消旧的观察者，避免收集器泄漏
        syncObserverJob?.cancel()
        syncObserverJob = viewModelScope.launch {
            syncManager.syncState.collect { state ->
                if (state is com.secretdiary.app.sync.SyncState.Success || state is com.secretdiary.app.sync.SyncState.Failed) {
                    _uiState.value = _uiState.value.copy(isSyncing = false)
                    loadSyncTime()
                }
            }
        }
    }

    fun changePassword(oldPassword: String, newPassword: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val unifiedSaltB64 = sessionManager.getSaltAuthB64()
                    ?: throw IllegalStateException("Salt not found — please re-login")
                val unifiedSalt = Base64Util.decode(unifiedSaltB64)
                val iters = apiService.getConfig().body()?.data?.kdf?.iterations ?: 600_000

                // PBKDF2 在 Default 线程执行
                val oldAuthKey = withContext(Dispatchers.Default) {
                    cryptoManager.deriveAuthKey(oldPassword, unifiedSalt, iters)
                }
                val newSalt = cryptoManager.generateSalt()
                val newSaltB64 = Base64Util.encodeToString(newSalt)
                val newKek = withContext(Dispatchers.Default) {
                    cryptoManager.deriveKEK(newPassword, newSalt, iters)
                }
                val newAuthKey = withContext(Dispatchers.Default) {
                    cryptoManager.deriveAuthKey(newPassword, newSalt, iters)
                }
                val dek = sessionManager.getActiveDEK()
                    ?: throw IllegalStateException("DEK not available")
                val newEncryptedDek = cryptoManager.wrapKey(dek, newKek)

                apiService.changePassword(ChangePasswordRequest(
                    oldAuthKey = oldAuthKey,
                    newAuthKeyHash = newAuthKey,
                    newEncryptedDek = newEncryptedDek,
                    newSaltEnc = newSaltB64,
                    newKdfParams = KdfParams("pbkdf2-sha256", iters)
                ))
                // 更新本地存储的统一盐
                val username = sessionManager.getUsername() ?: ""
                saltPrefs.putSaltAuth(username, newSaltB64)
                saltPrefs.putSaltEnc(username, newSaltB64)
                try { apiService.logout() } catch (_: Exception) {}
                sessionManager.clearAll()
                _uiState.value = _uiState.value.copy(isLoading = false, message = "密码已修改，请重新登录")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun setRecovery(recoveryPhrase: String, loginPassword: String) {
        if (recoveryPhrase == loginPassword) {
            _uiState.value = _uiState.value.copy(error = "恢复口令不能与登录密码相同")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val config = apiService.getConfig()
                val iters = config.body()?.data?.kdf?.iterations ?: 600_000
                val recoverySalt = cryptoManager.generateSalt()
                val recoveryKek = withContext(Dispatchers.Default) {
                    cryptoManager.deriveKEK(recoveryPhrase, recoverySalt, iters)
                }
                val dek = sessionManager.getActiveDEK() ?: throw IllegalStateException("DEK not available")
                val recoveryData = cryptoManager.wrapKey(dek, recoveryKek)
                val challenge = cryptoManager.generateChallenge()
                val (ctB64, ivB64) = cryptoManager.encrypt(challenge, recoveryKek)
                val encryptedChallenge = "$ctB64:$ivB64"

                apiService.setRecovery(SetRecoveryRequest(
                    recoveryData = recoveryData,
                    recoverySalt = Base64Util.encodeToString(recoverySalt),
                    challenge = Base64Util.encodeToString(challenge),
                    encryptedChallenge = encryptedChallenge
                ))
                sessionManager.setHasRecovery(true)
                _uiState.value = _uiState.value.copy(isLoading = false, hasRecovery = true, message = "托管已开启")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun deleteRecovery(password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val saltAuthB64 = sessionManager.getSaltAuthB64()
                    ?: throw IllegalStateException("SaltAuth not found — please re-login")
                val saltAuth = Base64Util.decode(saltAuthB64)

                val config = apiService.getConfig()
                val iters = config.body()?.data?.kdf?.iterations ?: 600_000
                val authKey = withContext(Dispatchers.Default) {
                    cryptoManager.deriveAuthKey(password, saltAuth, iters)
                }
                apiService.deleteRecovery(DeleteRecoveryRequest(authKey))
                sessionManager.setHasRecovery(false)
                _uiState.value = _uiState.value.copy(isLoading = false, hasRecovery = false, message = "托管已关闭")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun deleteAccount(password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val saltAuthB64 = sessionManager.getSaltAuthB64()
                    ?: throw IllegalStateException("SaltAuth not found — please re-login")
                val saltAuth = Base64Util.decode(saltAuthB64)

                val config = apiService.getConfig()
                val iters = config.body()?.data?.kdf?.iterations ?: 600_000
                val authKey = withContext(Dispatchers.Default) {
                    cryptoManager.deriveAuthKey(password, saltAuth, iters)
                }

                apiService.deleteAccount(DeleteAccountRequest(authKey))

                // 清除本地 SQLite 数据（需在 IO 线程执行）
                withContext(Dispatchers.IO) { database.clearAllTables() }

                // 清除文件缓存
                val cacheDir = File(context.cacheDir, "attachments")
                if (cacheDir.exists()) cacheDir.deleteRecursively()
                val filesDir = File(context.filesDir, "attachments")
                if (filesDir.exists()) filesDir.deleteRecursively()

                // 清除 salt 记录
                saltPrefs.clear()

                sessionManager.clearAll()
                _uiState.value = _uiState.value.copy(isLoading = false, message = "账户已注销")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try { apiService.logout() } catch (_: Exception) {}
            sessionManager.clearAll()
            _uiState.value = _uiState.value.copy(message = "已登出")
        }
    }

    fun changePassword(oldPassword: String, newPassword: String, confirmPassword: String) {
        if (newPassword != confirmPassword) {
            _uiState.value = _uiState.value.copy(error = "两次新密码不一致")
            return
        }
        changePassword(oldPassword, newPassword)
    }

    fun toggleBiometric(enabled: Boolean) {
        if (enabled) {
            if (sessionManager.getActiveDEK() != null) {
                sessionManager.setBiometricEnabled(true)
                _uiState.value = _uiState.value.copy(isBiometricEnabled = true)
            } else {
                _uiState.value = _uiState.value.copy(error = "DEK 已过期，请重新登录后开启")
            }
        } else {
            sessionManager.clearDEK()
            sessionManager.setBiometricEnabled(false)
            _uiState.value = _uiState.value.copy(isBiometricEnabled = false)
        }
    }

    fun clearMessage() { _uiState.value = _uiState.value.copy(message = null) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
