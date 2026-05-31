package com.secretdiary.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secretdiary.app.data.remote.api.ApiService
import com.secretdiary.app.data.remote.dto.LoginRequest
import com.secretdiary.app.security.BiometricAuth
import com.secretdiary.app.security.CryptoManager
import com.secretdiary.app.security.SaltPreferencesManager
import com.secretdiary.app.security.SessionManager
import com.secretdiary.app.sync.SyncManager
import com.secretdiary.app.util.Base64Util
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isBiometricAvailable: Boolean = false,
    val loginSuccess: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val apiService: ApiService,
    private val cryptoManager: CryptoManager,
    private val sessionManager: SessionManager,
    private val biometricAuth: BiometricAuth,
    private val syncManager: SyncManager,
    private val saltPrefs: SaltPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState(
        isBiometricAvailable = biometricAuth.canAuthenticate()
            && sessionManager.isBiometricEnabled()
            && sessionManager.hasValidDEK()
    ))
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onUsernameChanged(value: String) { _uiState.value = _uiState.value.copy(username = value) }
    fun onPasswordChanged(value: String) { _uiState.value = _uiState.value.copy(password = value) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    fun login() {
        val state = _uiState.value
        if (state.username.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = "请输入用户名和密码")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // 网络请求在 IO 线程并行执行
                val configDeferred = async(Dispatchers.IO) {
                    apiService.getConfig().body()?.data?.kdf?.iterations ?: 600_000
                }
                val saltDeferred = async(Dispatchers.IO) {
                    try {
                        val r = apiService.getRecoveryInfo(state.username)
                        if (r.isSuccessful && r.body()?.code == 0) r.body()!!.data?.saltEnc else null
                    } catch (_: Exception) { null }
                }

                val iterations = configDeferred.await()
                var saltEncB64: String? = saltDeferred.await()

                if (saltEncB64.isNullOrEmpty()) {
                    saltEncB64 = saltPrefs.getSaltAuth(state.username)
                }
                if (saltEncB64.isNullOrEmpty()) {
                    saltEncB64 = saltPrefs.getSaltEnc(state.username)
                }

                if (saltEncB64.isNullOrEmpty()) {
                    _uiState.value = _uiState.value.copy(isLoading = false,
                        error = "未找到加密参数，请确认该账户已注册或恢复口令后重试")
                    return@launch
                }

                val saltBytes = Base64Util.decode(saltEncB64)
                val password = state.password

                // PBKDF2 计算密集，在 Default 线程执行，避免阻塞 UI 动画
                val loginData = withContext(Dispatchers.Default) {
                    val ak = cryptoManager.deriveAuthKey(password, saltBytes, iterations)
                    apiService.login(LoginRequest(state.username, ak))
                }

                if (!loginData.isSuccessful || loginData.body()?.code != 0) {
                    _uiState.value = _uiState.value.copy(isLoading = false,
                        error = loginData.body()?.message ?: "用户名或密码错误")
                    return@launch
                }

                val data = loginData.body()!!.data!!

                // KEK 派生 + DEK 解包也在 Default 线程
                val dek = withContext(Dispatchers.Default) {
                    val kek = cryptoManager.deriveKEK(password, saltBytes, data.kdfParams.iterations)
                    cryptoManager.unwrapKey(data.encryptedDek, kek)
                }

                saltPrefs.putSaltAuth(state.username, saltEncB64)
                saltPrefs.putSaltEnc(state.username, saltEncB64)

                sessionManager.onLoginSuccess(
                    username = state.username, wrappedDek = data.encryptedDek,
                    saltEnc = saltEncB64, saltAuth = saltEncB64,
                    dek = dek, hasRecovery = data.hasRecovery
                )
                _uiState.value = _uiState.value.copy(isLoading = false, loginSuccess = true)
                syncManager.performSync()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "登录失败")
            }
        }
    }

    fun startBiometricAuth(activity: androidx.fragment.app.FragmentActivity) {
        biometricAuth.authenticate(
            activity = activity,
            onSuccess = {
                val dek = sessionManager.getActiveDEK()
                if (dek != null) {
                    viewModelScope.launch {
                        syncManager.performSync()
                        _uiState.value = _uiState.value.copy(loginSuccess = true)
                    }
                }
            },
            onError = { _uiState.value = _uiState.value.copy(error = "生物识别失败，请使用密码登录") },
            onCancel = { /* 用户取消，留在登录页 */ }
        )
    }
}
