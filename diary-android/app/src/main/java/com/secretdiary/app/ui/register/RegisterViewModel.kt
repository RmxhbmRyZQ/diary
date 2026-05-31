package com.secretdiary.app.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secretdiary.app.data.remote.api.ApiService
import com.secretdiary.app.data.remote.dto.KdfParams
import com.secretdiary.app.data.remote.dto.RegisterRequest
import com.secretdiary.app.security.CryptoManager
import com.secretdiary.app.security.SaltPreferencesManager
import com.secretdiary.app.util.Base64Util
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class RegisterUiState(
    val username: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val registerSuccess: Boolean = false
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val apiService: ApiService,
    private val cryptoManager: CryptoManager,
    private val saltPrefs: SaltPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onUsernameChanged(v: String) { _uiState.value = _uiState.value.copy(username = v) }
    fun onPasswordChanged(v: String) { _uiState.value = _uiState.value.copy(password = v) }
    fun onPasswordConfirmChanged(v: String) { _uiState.value = _uiState.value.copy(passwordConfirm = v) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    fun register() {
        val state = _uiState.value
        if (state.password != state.passwordConfirm) { _uiState.value = state.copy(error = "两次密码不一致"); return }
        if (!isPasswordValid(state.password)) { _uiState.value = state.copy(error = "密码需8位以上，包含大小写字母和数字"); return }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val config = apiService.getConfig()
                val iterations = config.body()?.data?.kdf?.iterations ?: 600_000
                // PBKDF2 计算密集，在 Default 线程执行，避免阻塞 UI 动画
                val (saltB64, authKey, encryptedDek) = withContext(Dispatchers.Default) {
                    val salt = cryptoManager.generateSalt()
                    val authKey = cryptoManager.deriveAuthKey(state.password, salt, iterations)
                    val kek = cryptoManager.deriveKEK(state.password, salt, iterations)
                    val dek = cryptoManager.generateDEK()
                    val encryptedDek = cryptoManager.wrapKey(dek, kek)
                    Triple(Base64Util.encodeToString(salt), authKey, encryptedDek)
                }
                val response = apiService.register(RegisterRequest(
                    username = state.username,
                    authKey = authKey,
                    saltAuth = saltB64,
                    encryptedDek = encryptedDek,
                    saltEnc = saltB64,
                    kdfParams = KdfParams("pbkdf2-sha256", iterations)
                ))
                if (response.isSuccessful && response.body()?.code == 0) {
                    // 持久化 salt 值，供登录及后续二次验证使用
                    saltPrefs.putSaltAuth(state.username, saltB64)
                    saltPrefs.putSaltEnc(state.username, saltB64)
                    _uiState.value = _uiState.value.copy(isLoading = false, registerSuccess = true)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = response.body()?.message ?: "注册失败")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "注册失败")
            }
        }
    }

    private fun isPasswordValid(pw: String): Boolean =
        pw.length >= 8 && pw.any { it.isUpperCase() } && pw.any { it.isLowerCase() } && pw.any { it.isDigit() }
}
