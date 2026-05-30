package com.secretdiary.app.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.text.Charsets.UTF_8

/**
 * 零知识加密核心管理器。
 * 负责 PBKDF2 密钥派生、AES-256-GCM 加解密、DEK 生成与 wrap/unwrap。
 */
@Singleton
class CryptoManager @Inject constructor() {

    companion object {
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val AES_GCM_ALGORITHM = "AES/GCM/NoPadding"
        private const val AES_KEY_ALGORITHM = "AES"
        private const val IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val DEK_KEY_SIZE_BITS = 256
        private const val DEFAULT_ITERATIONS = 600_000
        private const val AUTH_KEY_LENGTH_BITS = 256
        private const val KEK_KEY_LENGTH_BITS = 256
        // Web 端密钥派生使用的域名标签，必须保持一致
        private const val AUTH_KEY_LABEL = "diary-auth-key:"
        private const val ENC_KEY_LABEL = "diary-encrypt-key:"
    }

    /**
     * 使用 PBKDF2-SHA256 从密码派生 AuthKey 原始字节（256位），返回 Base64 编码。
     * 对齐 Web 端：密码加前缀 diary-auth-key:
     */
    fun deriveAuthKey(password: String, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): String {
        val keyBytes = deriveKeyBytes(AUTH_KEY_LABEL + password, salt, iterations, AUTH_KEY_LENGTH_BITS)
        return Base64.getEncoder().encodeToString(keyBytes)
    }

    /**
     * 使用 PBKDF2-SHA256 从密码派生 KEK（AES-256 SecretKey），用于加密 DEK。
     * 对齐 Web 端：密码加前缀 diary-encrypt-key:
     */
    fun deriveKEK(password: String, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): SecretKey {
        val keyBytes = deriveKeyBytes(ENC_KEY_LABEL + password, salt, iterations, KEK_KEY_LENGTH_BITS)
        return SecretKeySpec(keyBytes, AES_KEY_ALGORITHM)
    }

    /**
     * 生成 256 位 DEK，extractable=true（支持 wrap/unwrap 操作）。
     */
    fun generateDEK(): SecretKey {
        val keyGen = KeyGenerator.getInstance(AES_KEY_ALGORITHM)
        keyGen.init(DEK_KEY_SIZE_BITS)
        return keyGen.generateKey()
    }

    /**
     * 用 KEK 加密 DEK → 返回 "Base64(ciphertext):Base64(iv)"，对齐 Web 端 encryptDEK 格式。
     */
    fun wrapKey(dek: SecretKey, kek: SecretKey): String {
        val iv = generateIV()
        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        cipher.init(Cipher.WRAP_MODE, kek, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val wrappedBytes = cipher.wrap(dek)
        val ctB64 = Base64.getEncoder().encodeToString(wrappedBytes)
        val ivB64 = Base64.getEncoder().encodeToString(iv)
        return "$ctB64:$ivB64"
    }

    /**
     * 用 KEK 解密 wrapped DEK → 返回 SecretKey。
     * 输入格式对齐 Web 端：Base64(ciphertext):Base64(iv)
     */
    fun unwrapKey(wrappedData: String, kek: SecretKey): SecretKey {
        val parts = wrappedData.split(":")
        val ciphertext = Base64.getDecoder().decode(parts[0])
        val iv = if (parts.size > 1) {
            Base64.getDecoder().decode(parts[1])
        } else {
            // 兼容旧格式：Base64(iv || ciphertext)
            val combined = Base64.getDecoder().decode(wrappedData)
            val rawIv = combined.copyOfRange(0, IV_LENGTH_BYTES)
            val rawCt = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)
            val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
            cipher.init(Cipher.UNWRAP_MODE, kek, GCMParameterSpec(GCM_TAG_LENGTH_BITS, rawIv))
            return cipher.unwrap(rawCt, AES_KEY_ALGORITHM, Cipher.SECRET_KEY) as SecretKey
        }
        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        cipher.init(Cipher.UNWRAP_MODE, kek, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.unwrap(ciphertext, AES_KEY_ALGORITHM, Cipher.SECRET_KEY) as SecretKey
    }

    /**
     * AES-256-GCM 加密。
     * @param plaintext 明文字节
     * @param key 加密密钥
     * @return Pair(ciphertext Base64, iv Base64)
     */
    fun encrypt(plaintext: ByteArray, key: SecretKey): Pair<String, String> {
        val iv = generateIV()
        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return Pair(
            Base64.getEncoder().encodeToString(ciphertext),
            Base64.getEncoder().encodeToString(iv)
        )
    }

    /**
     * AES-256-GCM 加密（使用指定 IV，用于恢复口令质询应答等需要匹配特定 IV 的场景）。
     * @param plaintext 明文字节
     * @param key 加密密钥
     * @param iv 初始向量字节（12 字节）
     * @return Pair(ciphertext Base64, iv Base64)
     */
    fun encryptWithIV(plaintext: ByteArray, key: SecretKey, iv: ByteArray): Pair<String, String> {
        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return Pair(
            Base64.getEncoder().encodeToString(ciphertext),
            Base64.getEncoder().encodeToString(iv)
        )
    }

    /**
     * AES-256-GCM 解密。
     * @param ciphertextB64 Base64 密文
     * @param ivB64 Base64 IV
     * @param key 解密密钥
     * @return 明文字节
     */
    fun decrypt(ciphertextB64: String, ivB64: String, key: SecretKey): ByteArray {
        val ciphertext = Base64.getDecoder().decode(ciphertextB64)
        val iv = Base64.getDecoder().decode(ivB64)
        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    /**
     * 计算 SHA-256 哈希，返回小写十六进制字符串（64字符）。
     */
    fun sha256Hex(data: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * 生成随机盐（用于 PBKDF2）。
     */
    fun generateSalt(length: Int = 16): ByteArray {
        val salt = ByteArray(length)
        SecureRandom().nextBytes(salt)
        return salt
    }

    /**
     * 生成 12 字节随机 IV。
     */
    fun generateIV(): ByteArray {
        val iv = ByteArray(IV_LENGTH_BYTES)
        SecureRandom().nextBytes(iv)
        return iv
    }

    /**
     * 生成 32 字节随机质询（challenge），用于恢复口令托管。
     */
    fun generateChallenge(): ByteArray {
        val challenge = ByteArray(32)
        SecureRandom().nextBytes(challenge)
        return challenge
    }

    // -------------------- 私有方法 --------------------

    private fun deriveKeyBytes(password: String, salt: ByteArray, iterations: Int, keyLengthBits: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLengthBits)
        val factory = javax.crypto.SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        return factory.generateSecret(spec).encoded
    }
}
