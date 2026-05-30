package com.secretdiary.app.security

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import javax.crypto.AEADBadTagException
import javax.crypto.SecretKey

class CryptoManagerTest {

    private lateinit var cryptoManager: CryptoManager

    @Before
    fun setUp() {
        cryptoManager = CryptoManager()
    }

    // -------------------- 密钥派生 --------------------

    @Test
    fun `deriveAuthKey produces non-empty Base64 string`() {
        val salt = cryptoManager.generateSalt()
        val authKey = cryptoManager.deriveAuthKey("testPassword123", salt)
        assertNotNull(authKey)
        assertTrue(authKey.isNotEmpty())
        // Base64 编码不含换行
        assertFalse(authKey.contains("\n"))
    }

    @Test
    fun `deriveAuthKey with same inputs produces same output`() {
        val salt = cryptoManager.generateSalt()
        val authKey1 = cryptoManager.deriveAuthKey("testPassword123", salt)
        val authKey2 = cryptoManager.deriveAuthKey("testPassword123", salt)
        assertEquals(authKey1, authKey2)
    }

    @Test
    fun `deriveAuthKey with different passwords produces different output`() {
        val salt = cryptoManager.generateSalt()
        val authKey1 = cryptoManager.deriveAuthKey("passwordA", salt)
        val authKey2 = cryptoManager.deriveAuthKey("passwordB", salt)
        assertNotEquals(authKey1, authKey2)
    }

    @Test
    fun `deriveAuthKey with different salts produces different output`() {
        val salt1 = cryptoManager.generateSalt()
        val salt2 = cryptoManager.generateSalt()
        val authKey1 = cryptoManager.deriveAuthKey("testPassword123", salt1)
        val authKey2 = cryptoManager.deriveAuthKey("testPassword123", salt2)
        assertNotEquals(authKey1, authKey2)
    }

    @Test
    fun `deriveKEK returns valid AES-256 SecretKey`() {
        val salt = cryptoManager.generateSalt()
        val kek = cryptoManager.deriveKEK("testPassword123", salt)
        assertEquals("AES", kek.algorithm)
        assertEquals(32, kek.encoded.size) // 256 bits = 32 bytes
    }

    @Test
    fun `deriveKEK with same inputs produces same key`() {
        val salt = cryptoManager.generateSalt()
        val kek1 = cryptoManager.deriveKEK("testPassword123", salt)
        val kek2 = cryptoManager.deriveKEK("testPassword123", salt)
        assertArrayEquals(kek1.encoded, kek2.encoded)
    }

    // -------------------- DEK 生成 --------------------

    @Test
    fun `generateDEK creates AES-256 key`() {
        val dek = cryptoManager.generateDEK()
        assertEquals("AES", dek.algorithm)
        assertEquals(32, dek.encoded.size)
    }

    @Test
    fun `generateDEK produces unique keys`() {
        val keys = (1..10).map { cryptoManager.generateDEK() }
        val uniqueEncoded = keys.map { it.encoded.contentToString() }.distinct()
        assertEquals(10, uniqueEncoded.size)
    }

    // -------------------- wrapKey / unwrapKey --------------------

    @Test
    fun `wrapKey and unwrapKey roundtrip succeeds`() {
        val dek = cryptoManager.generateDEK()
        val kek = cryptoManager.deriveKEK("password123", cryptoManager.generateSalt())
        val wrapped = cryptoManager.wrapKey(dek, kek)
        assertNotNull(wrapped)
        assertTrue(wrapped.isNotEmpty())
        val unwrapped = cryptoManager.unwrapKey(wrapped, kek)
        assertArrayEquals(dek.encoded, unwrapped.encoded)
    }

    @Test
    fun `unwrapKey with wrong KEK throws exception`() {
        val dek = cryptoManager.generateDEK()
        val kek1 = cryptoManager.deriveKEK("password1", cryptoManager.generateSalt())
        val kek2 = cryptoManager.deriveKEK("password2", cryptoManager.generateSalt())
        val wrapped = cryptoManager.wrapKey(dek, kek1)
        assertThrows(Exception::class.java) {
            cryptoManager.unwrapKey(wrapped, kek2)
        }
    }

    @Test
    fun `unwrapKey with corrupted data throws exception`() {
        val dek = cryptoManager.generateDEK()
        val kek = cryptoManager.deriveKEK("password123", cryptoManager.generateSalt())
        val wrapped = cryptoManager.wrapKey(dek, kek)
        // 损坏数据
        val corrupted = wrapped.dropLast(4) + "AAAA"
        assertThrows(Exception::class.java) {
            cryptoManager.unwrapKey(corrupted, kek)
        }
    }

    // -------------------- 加密/解密 --------------------

    @Test
    fun `encrypt and decrypt roundtrip succeeds`() {
        val key = cryptoManager.generateDEK()
        val plaintext = "Hello, 隐秘日记！".toByteArray(Charsets.UTF_8)
        val (ciphertext, iv) = cryptoManager.encrypt(plaintext, key)
        assertNotNull(ciphertext)
        assertTrue(ciphertext.isNotEmpty())
        assertNotNull(iv)
        val decrypted = cryptoManager.decrypt(ciphertext, iv, key)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypt produces different ciphertext for same plaintext`() {
        val key = cryptoManager.generateDEK()
        val plaintext = "test data".toByteArray(Charsets.UTF_8)
        val (ct1, iv1) = cryptoManager.encrypt(plaintext, key)
        val (ct2, iv2) = cryptoManager.encrypt(plaintext, key)
        // IV 应不同（概率上不会碰撞）
        assertNotEquals(iv1, iv2)
        // 密文应不同
        assertNotEquals(ct1, ct2)
    }

    @Test
    fun `decrypt with wrong key throws AEADBadTagException`() {
        val key1 = cryptoManager.generateDEK()
        val key2 = cryptoManager.generateDEK()
        val plaintext = "test".toByteArray(Charsets.UTF_8)
        val (ciphertext, iv) = cryptoManager.encrypt(plaintext, key1)
        assertThrows(AEADBadTagException::class.java) {
            cryptoManager.decrypt(ciphertext, iv, key2)
        }
    }

    @Test
    fun `decrypt with wrong IV throws exception`() {
        val key = cryptoManager.generateDEK()
        val plaintext = "test".toByteArray(Charsets.UTF_8)
        val (ciphertext, _) = cryptoManager.encrypt(plaintext, key)
        val wrongIv = cryptoManager.generateIV()
        val wrongIvB64 = java.util.Base64.getEncoder().withoutPadding().encodeToString(wrongIv)
        assertThrows(AEADBadTagException::class.java) {
            cryptoManager.decrypt(ciphertext, wrongIvB64, key)
        }
    }

    @Test
    fun `encrypt large data succeeds`() {
        val key = cryptoManager.generateDEK()
        val plaintext = ByteArray(1024 * 100) { (it % 256).toByte() }
        val (ciphertext, iv) = cryptoManager.encrypt(plaintext, key)
        val decrypted = cryptoManager.decrypt(ciphertext, iv, key)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypt empty data succeeds`() {
        val key = cryptoManager.generateDEK()
        val plaintext = ByteArray(0)
        val (ciphertext, iv) = cryptoManager.encrypt(plaintext, key)
        val decrypted = cryptoManager.decrypt(ciphertext, iv, key)
        assertEquals(0, decrypted.size)
    }

    @Test
    fun `decrypt with tampered ciphertext throws exception`() {
        val key = cryptoManager.generateDEK()
        val plaintext = "sensitive data".toByteArray(Charsets.UTF_8)
        val (ciphertext, iv) = cryptoManager.encrypt(plaintext, key)
        val tampered = ciphertext.dropLast(4) + "BBBB"
        assertThrows(AEADBadTagException::class.java) {
            cryptoManager.decrypt(tampered, iv, key)
        }
    }

    // -------------------- SHA-256 --------------------

    @Test
    fun `sha256Hex returns 64-character hex string`() {
        val data = "hello".toByteArray(Charsets.UTF_8)
        val hash = cryptoManager.sha256Hex(data)
        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun `sha256Hex is deterministic`() {
        val data = "test".toByteArray(Charsets.UTF_8)
        val h1 = cryptoManager.sha256Hex(data)
        val h2 = cryptoManager.sha256Hex(data)
        assertEquals(h1, h2)
    }

    @Test
    fun `sha256Hex produces different hashes for different inputs`() {
        val h1 = cryptoManager.sha256Hex("data1".toByteArray(Charsets.UTF_8))
        val h2 = cryptoManager.sha256Hex("data2".toByteArray(Charsets.UTF_8))
        assertNotEquals(h1, h2)
    }

    // -------------------- 辅助方法 --------------------

    @Test
    fun `generateSalt returns bytes of requested length`() {
        val salt = cryptoManager.generateSalt(16)
        assertEquals(16, salt.size)
    }

    @Test
    fun `generateSalt produces random output`() {
        val s1 = cryptoManager.generateSalt()
        val s2 = cryptoManager.generateSalt()
        assertFalse(s1.contentEquals(s2))
    }

    @Test
    fun `generateIV returns 12 bytes`() {
        val iv = cryptoManager.generateIV()
        assertEquals(12, iv.size)
    }

    @Test
    fun `generateChallenge returns 32 bytes`() {
        val challenge = cryptoManager.generateChallenge()
        assertEquals(32, challenge.size)
    }

    @Test
    fun `deriveAuthKey with different iterations produces different output`() {
        val salt = cryptoManager.generateSalt()
        val key1 = cryptoManager.deriveAuthKey("password", salt, 100_000)
        val key2 = cryptoManager.deriveAuthKey("password", salt, 600_000)
        assertNotEquals(key1, key2)
    }
}
