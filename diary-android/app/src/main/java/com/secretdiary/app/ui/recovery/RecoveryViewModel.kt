package com.secretdiary.app.ui.recovery

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secretdiary.app.data.remote.api.ApiService
import com.secretdiary.app.data.remote.dto.KdfParams
import com.secretdiary.app.data.remote.dto.RecoveryResetRequest
import com.secretdiary.app.security.CryptoManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecoveryUiState(
    val step: Int = 1,          // 1=输入用户名, 2=输入恢复口令, 3=设置新密码
    val username: String = "",
    val recoveryPhrase: String = "",
    val newPassword: String = "",
    val newPasswordConfirm: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    // 从服务端获取的托管信息
    val recoveryData: String = "",
    val recoverySalt: String = "",
    val saltEnc: String = "",
    val challenge: String = "",
    val challengeIv: String = ""
)

@HiltViewModel
class RecoveryViewModel @Inject constructor(
    private val apiService: ApiService,
    private val cryptoManager: CryptoManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val saltPrefs = context.getSharedPreferences("diary_salts", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(RecoveryUiState())
    val uiState: StateFlow<RecoveryUiState> = _uiState.asStateFlow()

    fun onUsernameChanged(v: String) { _uiState.value = _uiState.value.copy(username = v) }
    fun onRecoveryPhraseChanged(v: String) { _uiState.value = _uiState.value.copy(recoveryPhrase = v) }
    fun onNewPasswordChanged(v: String) { _uiState.value = _uiState.value.copy(newPassword = v) }
    fun onNewPasswordConfirmChanged(v: String) { _uiState.value = _uiState.value.copy(newPasswordConfirm = v) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    /** 步骤1：查询托管信息 */
    fun fetchRecoveryInfo() {
        val username = _uiState.value.username
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = apiService.getRecoveryInfo(username)
                if (response.isSuccessful && response.body()?.code == 0) {
                    val data = response.body()!!.data
                    if (data == null || data.recoveryData.isNullOrEmpty()) {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = "该账户未设置恢复口令托管")
                        return@launch
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false, step = 2,
                        recoveryData = data.recoveryData ?: "",
                        recoverySalt = data.recoverySalt ?: "",
                        saltEnc = data.saltEnc ?: "",
                        challenge = data.challenge ?: "",
                        challengeIv = data.challengeIv ?: ""
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "用户不存在或未设置托管")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    /** 步骤2：验证恢复口令并进入步骤3 */
    fun verifyRecoveryPhrase() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            try {
                val config = apiService.getConfig()
                val iterations = config.body()?.data?.kdf?.iterations ?: 600_000
                val saltBytes = android.util.Base64.decode(state.recoverySalt, android.util.Base64.NO_WRAP)
                val recoveryKek = cryptoManager.deriveKEK(state.recoveryPhrase, saltBytes, iterations)
                // 尝试解密 recoveryData 验证恢复口令正确性
                cryptoManager.unwrapKey(state.recoveryData, recoveryKek)
                _uiState.value = state.copy(isLoading = false, step = 3)
            } catch (e: Exception) {
                _uiState.value = state.copy(isLoading = false, error = "恢复口令不正确")
            }
        }
    }

    /** 步骤3：重置密码 */
    fun resetPassword() {
        val state = _uiState.value
        if (state.newPassword != state.newPasswordConfirm) {
            _uiState.value = state.copy(error = "两次密码不一致"); return
        }
        if (state.newPassword == state.recoveryPhrase) {
            _uiState.value = state.copy(error = "新密码不能与恢复口令相同"); return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            try {
                val config = apiService.getConfig()
                val iterations = config.body()?.data?.kdf?.iterations ?: 600_000
                // 对齐 Web 端：使用统一的盐派生 authKey 和 KEK
                val newSalt = cryptoManager.generateSalt()
                val newSaltB64 = android.util.Base64.encodeToString(newSalt, android.util.Base64.NO_WRAP)
                val newAuthKey = cryptoManager.deriveAuthKey(state.newPassword, newSalt, iterations)
                val newKek = cryptoManager.deriveKEK(state.newPassword, newSalt, iterations)

                // 用恢复口令解密 DEK
                val recoverySaltBytes = android.util.Base64.decode(state.recoverySalt, android.util.Base64.NO_WRAP)
                val recoveryKek = cryptoManager.deriveKEK(state.recoveryPhrase, recoverySaltBytes, iterations)
                val dek = cryptoManager.unwrapKey(state.recoveryData, recoveryKek)
                val newEncryptedDek = cryptoManager.wrapKey(dek, newKek)

                // 使用服务端返回的 challenge_iv 重新加密 challenge
                val challengeBytes = android.util.Base64.decode(state.challenge, android.util.Base64.NO_WRAP)
                val challengeIv = android.util.Base64.decode(state.challengeIv, android.util.Base64.NO_WRAP)
                val (ctB64, ivB64) = cryptoManager.encryptWithIV(challengeBytes, recoveryKek, challengeIv)
                val encryptedChallenge = "$ctB64:$ivB64"

                val response = apiService.recoveryReset(RecoveryResetRequest(
                    username = state.username,
                    newAuthKeyHash = newAuthKey,
                    newEncryptedDek = newEncryptedDek,
                    newSaltEnc = newSaltB64,
                    newKdfParams = KdfParams("pbkdf2-sha256", iterations),
                    encryptedChallenge = encryptedChallenge
                ))
                if (response.isSuccessful && response.body()?.code == 0) {
                    // 持久化统一盐，确保后续登录能正确派生密钥
                    saltPrefs.edit()
                        .putString("saltAuth_${state.username}", newSaltB64)
                        .putString("saltEnc_${state.username}", newSaltB64)
                        .apply()
                    _uiState.value = state.copy(isLoading = false, success = true)
                } else {
                    _uiState.value = state.copy(isLoading = false, error = response.body()?.message ?: "重置失败")
                }
            } catch (e: Exception) {
                _uiState.value = state.copy(isLoading = false, error = e.message ?: "重置失败")
            }
        }
    }
}
