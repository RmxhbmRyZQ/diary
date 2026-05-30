package com.secretdiary.app.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 管理 DEK 本地缓存与会话状态。
 * DEK 经 EncryptedSharedPreferences 加密存储（由 Android Keystore 主密钥保护）。
 * 缓存有效期 30 分钟（TTL），超时后需重新通过密码或生物识别获取。
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "secret_diary_session"
        private const val KEY_WRAPPED_DEK = "wrapped_dek"
        private const val KEY_SALT_ENC = "salt_enc"
        private const val KEY_SALT_AUTH = "salt_auth"
        private const val KEY_USERNAME = "current_username"
        private const val KEY_DEK_STORED_AT = "dek_stored_at"
        private const val KEY_LAST_SESSION = "last_session_active"
        private const val KEY_HAS_RECOVERY = "has_recovery"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_DEK_RAW = "dek_raw"  // DEK 原始字节（Base64），用于重启后恢复
        private const val DEK_TTL_MILLIS = 30 * 60 * 1000L // 30 分钟
    }

    // 内存中的活跃 DEK（永不被 EncryptedSharedPreferences 明文存储）
    @Volatile
    private var activeDEK: javax.crypto.SecretKey? = null

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * 登录成功后缓存会话数据。
     * @param username 当前登录用户名
     * @param wrappedDek 用 KEK 加密的 DEK（格式 "ciphertext:iv" 的 Base64）
     * @param saltEnc KEK 派生盐值（Base64）
     * @param saltAuth AuthKey 派生盐值（Base64），用于后续修改密码/删除托管等二次验证
     * @param dek 解密后的 DEK 明文（仅存内存）
     * @param hasRecovery 是否已设置恢复口令托管
     */
    fun onLoginSuccess(
        username: String,
        wrappedDek: String,
        saltEnc: String,
        saltAuth: String,
        dek: javax.crypto.SecretKey,
        hasRecovery: Boolean = false
    ) {
        this.activeDEK = dek
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_WRAPPED_DEK, wrappedDek)
            .putString(KEY_SALT_ENC, saltEnc)
            .putString(KEY_SALT_AUTH, saltAuth)
            .putString(KEY_DEK_RAW, android.util.Base64.encodeToString(dek.encoded, android.util.Base64.NO_WRAP))
            .putLong(KEY_DEK_STORED_AT, currentTimeMillis())
            .putBoolean(KEY_LAST_SESSION, true)
            .putBoolean(KEY_HAS_RECOVERY, hasRecovery)
            .apply()
    }

    /** 获取当前登录用户名 */
    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)

    /** 获取 AuthKey 派生盐值（Base64），用于二次验证 */
    fun getSaltAuthB64(): String? = prefs.getString(KEY_SALT_AUTH, null)

    /** 获取 KEK 派生盐值（Base64） */
    fun getSaltEncB64(): String? = prefs.getString(KEY_SALT_ENC, null)

    /** 获取/设置恢复口令托管状态 */
    fun hasRecovery(): Boolean = prefs.getBoolean(KEY_HAS_RECOVERY, false)
    fun setHasRecovery(value: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_RECOVERY, value).apply()
    }

    /** 生物识别开关偏好（持久化，独立于 DEK 状态） */
    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    fun setBiometricEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()
    }

    /**
     * 获取内存中的活跃 DEK，若 TTL 过期则返回 null。
     */
    fun getActiveDEK(): javax.crypto.SecretKey? {
        val storedAt = prefs.getLong(KEY_DEK_STORED_AT, 0L)
        if (currentTimeMillis() - storedAt > DEK_TTL_MILLIS) {
            clearAll()
            return null
        }
        // 内存中有则直接返回
        if (activeDEK != null) return activeDEK
        // 重启后从持久化恢复（EncryptedSharedPreferences 由 Keystore 保护）
        val rawB64 = prefs.getString(KEY_DEK_RAW, null) ?: return null
        return try {
            val keyBytes = android.util.Base64.decode(rawB64, android.util.Base64.NO_WRAP)
            val key = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
            activeDEK = key
            key
        } catch (_: Exception) { null }
    }

    /**
     * 读取加密存储的 wrapped DEK 和 saltEnc（用于从密码恢复 DEK）。
     * @return Pair(wrappedDekB64, saltEncB64) 或 null
     */
    fun loadWrappedDEK(): Pair<String, String>? {
        val storedAt = prefs.getLong(KEY_DEK_STORED_AT, 0L)
        if (currentTimeMillis() - storedAt > DEK_TTL_MILLIS) {
            clearAll()
            return null
        }
        val wrappedDek = prefs.getString(KEY_WRAPPED_DEK, null) ?: return null
        val saltEnc = prefs.getString(KEY_SALT_ENC, null) ?: return null
        return Pair(wrappedDek, saltEnc)
    }

    /**
     * 直接从密码和加密存储数据中恢复 DEK（用于标准密码登录和生物识别引导的密码回退）。
     * @param iterations KDF 迭代次数，须从服务端 /config 动态获取
     */
    fun recoverDEKFromPassword(password: String, cryptoManager: CryptoManager, iterations: Int = 600_000): javax.crypto.SecretKey? {
        val (wrappedDek, saltEnc) = loadWrappedDEK() ?: return null
        val saltBytes = android.util.Base64.decode(saltEnc, android.util.Base64.NO_WRAP)
        val kek = cryptoManager.deriveKEK(password, saltBytes, iterations)
        return try {
            cryptoManager.unwrapKey(wrappedDek, kek)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 检查是否存有有效的 DEK 缓存（未过期且 DEK 在内存中）。
     */
    fun hasValidDEK(): Boolean = getActiveDEK() != null

    /**
     * 清除 DEK 缓存（内存 + 持久化）。
     */
    fun clearDEK() {
        activeDEK = null
        prefs.edit()
            .remove(KEY_WRAPPED_DEK)
            .remove(KEY_SALT_ENC)
            .remove(KEY_DEK_RAW)
            .remove(KEY_DEK_STORED_AT)
            .putBoolean(KEY_LAST_SESSION, false)
            .apply()
    }

    /**
     * 清除会话数据（用于 401 / 登出 / 密码修改）。
     * 保留用户偏好：生物识别开关、恢复托管状态。
     */
    fun clearAll() {
        val biometric = isBiometricEnabled()
        val recovery = hasRecovery()
        prefs.edit().clear().apply()
        setBiometricEnabled(biometric)
        if (recovery) setHasRecovery(true)
    }

    /**
     * 以北京时间返回当前时间戳（毫秒）。
     * 存储用 UTC epoch 即可，因为仅用于 TTL 比较（差值无关时区）。
     */
    private fun currentTimeMillis(): Long = System.currentTimeMillis()
}
