package com.secretdiary.app.ui.register

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secretdiary.app.data.remote.api.ApiService
import com.secretdiary.app.data.remote.dto.KdfParams
import com.secretdiary.app.data.remote.dto.RegisterRequest
import com.secretdiary.app.security.CryptoManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val saltPrefs = context.getSharedPreferences("diary_salts", Context.MODE_PRIVATE)

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
                // 使用同一个 salt 派生 authKey 和 KEK（与 Web 端对齐）
                val salt = cryptoManager.generateSalt()
                val authKey = cryptoManager.deriveAuthKey(state.password, salt, iterations)
                val kek = cryptoManager.deriveKEK(state.password, salt, iterations)
                val dek = cryptoManager.generateDEK()
                val encryptedDek = cryptoManager.wrapKey(dek, kek)

                val saltB64 = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP)
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
                    saltPrefs.edit()
                        .putString("saltAuth_${state.username}", saltB64)
                        .putString("saltEnc_${state.username}", saltB64)
                        .apply()
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
