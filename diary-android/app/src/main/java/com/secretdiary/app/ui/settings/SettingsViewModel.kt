package com.secretdiary.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secretdiary.app.data.local.SecretDiaryDatabase
import com.secretdiary.app.data.remote.api.ApiService
import com.secretdiary.app.data.remote.dto.*
import com.secretdiary.app.data.repository.DiaryRepository
import com.secretdiary.app.security.CryptoManager
import com.secretdiary.app.security.SessionManager
import com.secretdiary.app.sync.SyncManager
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
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val saltPrefs = context.getSharedPreferences("diary_salts", Context.MODE_PRIVATE)
    private var syncObserverJob: Job? = null

    private val _uiState = MutableStateFlow(SettingsUiState(
        hasRecovery = sessionManager.hasRecovery(),
        isBiometricEnabled = sessionManager.getActiveDEK() != null  // DEK 存在说明生物识别已启用
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
                // 对齐 Web 端：使用统一的盐（saltAuth == saltEnc）
                val unifiedSaltB64 = sessionManager.getSaltAuthB64()
                    ?: throw IllegalStateException("Salt not found — please re-login")
                val unifiedSalt = android.util.Base64.decode(unifiedSaltB64, android.util.Base64.NO_WRAP)

                val iters = apiService.getConfig().body()?.data?.kdf?.iterations ?: 600_000
                val oldAuthKey = cryptoManager.deriveAuthKey(oldPassword, unifiedSalt, iters)

                // 生成一个新的统一盐，同时用于 authKey 和 KEK（与 Web 端一致）
                val newSalt = cryptoManager.generateSalt()
                val newSaltB64 = android.util.Base64.encodeToString(newSalt, android.util.Base64.NO_WRAP)
                val newKek = cryptoManager.deriveKEK(newPassword, newSalt, iters)
                val newAuthKey = cryptoManager.deriveAuthKey(newPassword, newSalt, iters)

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
                saltPrefs.edit()
                    .putString("saltAuth_$username", newSaltB64)
                    .putString("saltEnc_$username", newSaltB64)
                    .apply()
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
                val recoveryKek = cryptoManager.deriveKEK(recoveryPhrase, recoverySalt, iters)
                val dek = sessionManager.getActiveDEK() ?: throw IllegalStateException("DEK not available")
                val recoveryData = cryptoManager.wrapKey(dek, recoveryKek)
                val challenge = cryptoManager.generateChallenge()
                val (ctB64, ivB64) = cryptoManager.encrypt(challenge, recoveryKek)
                val encryptedChallenge = "$ctB64:$ivB64"

                apiService.setRecovery(SetRecoveryRequest(
                    recoveryData = recoveryData,
                    recoverySalt = android.util.Base64.encodeToString(recoverySalt, android.util.Base64.NO_WRAP),
                    challenge = android.util.Base64.encodeToString(challenge, android.util.Base64.NO_WRAP),
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
                val saltAuth = android.util.Base64.decode(saltAuthB64, android.util.Base64.NO_WRAP)

                val config = apiService.getConfig()
                val iters = config.body()?.data?.kdf?.iterations ?: 600_000
                val authKey = cryptoManager.deriveAuthKey(password, saltAuth, iters)
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
                val saltAuth = android.util.Base64.decode(saltAuthB64, android.util.Base64.NO_WRAP)

                val config = apiService.getConfig()
                val iters = config.body()?.data?.kdf?.iterations ?: 600_000
                val authKey = cryptoManager.deriveAuthKey(password, saltAuth, iters)

                apiService.deleteAccount(DeleteAccountRequest(authKey))

                // 清除本地 SQLite 数据（需在 IO 线程执行）
                withContext(Dispatchers.IO) { database.clearAllTables() }

                // 清除文件缓存
                val cacheDir = File(context.cacheDir, "attachments")
                if (cacheDir.exists()) cacheDir.deleteRecursively()
                val filesDir = File(context.filesDir, "attachments")
                if (filesDir.exists()) filesDir.deleteRecursively()

                // 清除 salt 记录
                saltPrefs.edit().clear().apply()

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
            // 生物识别开启依赖 DEK 是否在内存中
            if (sessionManager.getActiveDEK() != null) {
                _uiState.value = _uiState.value.copy(isBiometricEnabled = true)
            } else {
                _uiState.value = _uiState.value.copy(error = "DEK 已过期，请重新登录后开启")
            }
        } else {
            sessionManager.clearDEK()
            _uiState.value = _uiState.value.copy(isBiometricEnabled = false)
        }
    }

    fun clearMessage() { _uiState.value = _uiState.value.copy(message = null) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
