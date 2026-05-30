package com.secretdiary.app.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 生物识别（指纹/面容）封装。
 * 提供设备支持检查、可用性判断及 BiometricPrompt 调起。
 */
@Singleton
class BiometricAuth @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** 生物识别是否可用（设备支持且已录入） */
    fun canAuthenticate(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * 显示生物识别提示，成功后通过回调返回。
     * @param activity 当前 Activity
     * @param onSuccess 识别成功回调
     * @param onError 识别失败/错误回调
     * @param onCancel 用户取消回调
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "生物识别登录",
        subtitle: String = "请验证指纹或面容以解锁",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(context)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("使用密码")
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_USER_CANCELED
                ) {
                    onCancel()
                } else {
                    onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                onError("生物识别验证失败，请重试")
            }
        })

        biometricPrompt.authenticate(promptInfo)
    }
}
