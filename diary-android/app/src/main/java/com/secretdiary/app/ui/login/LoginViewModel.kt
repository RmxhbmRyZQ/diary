package com.secretdiary.app.ui.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secretdiary.app.data.remote.api.ApiService
import com.secretdiary.app.data.remote.dto.LoginRequest
import com.secretdiary.app.security.BiometricAuth
import com.secretdiary.app.security.CryptoManager
import com.secretdiary.app.security.SessionManager
import com.secretdiary.app.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val saltPrefs = context.getSharedPreferences("diary_salts", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(LoginUiState(isBiometricAvailable = biometricAuth.canAuthenticate()))
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
                val iterations = apiService.getConfig().body()?.data?.kdf?.iterations ?: 600_000

                // 对齐 Web 端：saltAuth == saltEnc（统一盐）
                // 优先从服务端获取 salt_enc，失败时回退到本地存储
                var saltEncB64: String? = null
                try {
                    val recoveryResult = apiService.getRecoveryInfo(state.username)
                    if (recoveryResult.isSuccessful && recoveryResult.body()?.code == 0) {
                        saltEncB64 = recoveryResult.body()!!.data?.saltEnc
                    }
                } catch (_: Exception) { }

                // 回退 1：本地 diary_salts（同设备之前登录过）
                if (saltEncB64.isNullOrEmpty()) {
                    saltEncB64 = saltPrefs.getString("saltAuth_${state.username}", null)
                }

                // 回退 2：本地存储的 saltEnc
                if (saltEncB64.isNullOrEmpty()) {
                    saltEncB64 = saltPrefs.getString("saltEnc_${state.username}", null)
                }

                if (saltEncB64.isNullOrEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "未找到加密参数，请确认该账户已注册或恢复口令后重试"
                    )
                    return@launch
                }

                val saltBytes = android.util.Base64.decode(saltEncB64, android.util.Base64.NO_WRAP)

                // 1. 派生 authKey → 登录验证
                val authKey = cryptoManager.deriveAuthKey(state.password, saltBytes, iterations)
                val loginResponse = apiService.login(LoginRequest(state.username, authKey))
                if (!loginResponse.isSuccessful || loginResponse.body()?.code != 0) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = loginResponse.body()?.message ?: "用户名或密码错误"
                    )
                    return@launch
                }

                val data = loginResponse.body()!!.data!!

                // 2. 派生 KEK → 解密 DEK（使用同一个盐）
                val kek = cryptoManager.deriveKEK(state.password, saltBytes, data.kdfParams.iterations)
                val dek = cryptoManager.unwrapKey(data.encryptedDek, kek)

                // 3. 持久化盐值，供后续修改密码等操作使用
                saltPrefs.edit()
                    .putString("saltAuth_${state.username}", saltEncB64)
                    .putString("saltEnc_${state.username}", saltEncB64)
                    .apply()

                sessionManager.onLoginSuccess(
                    username = state.username,
                    wrappedDek = data.encryptedDek,
                    saltEnc = saltEncB64,
                    saltAuth = saltEncB64,
                    dek = dek,
                    hasRecovery = data.hasRecovery
                )
                // 先跳转，同步在后台进行
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
