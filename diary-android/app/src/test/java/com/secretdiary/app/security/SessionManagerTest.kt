package com.secretdiary.app.security

import android.content.Context
import android.content.SharedPreferences
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import javax.crypto.SecretKey
import javax.crypto.KeyGenerator

class SessionManagerTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var sessionManager: SessionManager
    private val prefsMap = mutableMapOf<String, Any>()
    private lateinit var testDEK: SecretKey

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        editor = mockk(relaxed = true)

        every { editor.putString(any(), any()) } answers { prefsMap[firstArg()] = secondArg(); editor }
        every { editor.putLong(any(), any()) } answers { prefsMap[firstArg()] = secondArg(); editor }
        every { editor.putBoolean(any(), any()) } answers { prefsMap[firstArg()] = secondArg(); editor }
        every { editor.remove(any()) } answers { prefsMap.remove(firstArg()); editor }
        every { editor.clear() } answers { prefsMap.clear(); editor }
        every { editor.apply() } answers { Unit }

        every { prefs.getString(any(), any()) } answers { prefsMap[firstArg()] as? String ?: secondArg() }
        every { prefs.getLong(any(), any()) } answers { (prefsMap[firstArg()] as? Long) ?: secondArg<Long>() }
        every { prefs.getBoolean(any(), any()) } answers { (prefsMap[firstArg()] as? Boolean) ?: secondArg<Boolean>() }
        every { prefs.edit() } returns editor

        mockkStatic(androidx.security.crypto.EncryptedSharedPreferences::class)
        every {
            androidx.security.crypto.EncryptedSharedPreferences.create(
                any<Context>(), any(), any(), any(), any()
            )
        } returns prefs

        sessionManager = SessionManager(context)

        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        testDEK = keyGen.generateKey()
    }

    @After
    fun tearDown() {
        prefsMap.clear()
        unmockkAll()
    }

    @Test
    fun `onLoginSuccess stores wrapped DEK and salt`() {
        sessionManager.onLoginSuccess("testuser", "wrappedDek", "saltEncB64", "saltAuthB64", testDEK)
        val loaded = sessionManager.loadWrappedDEK()
        assertNotNull(loaded)
        assertEquals("wrappedDek", loaded!!.first)
        assertEquals("saltEncB64", loaded.second)
    }

    @Test
    fun `getActiveDEK returns DEK after login`() {
        sessionManager.onLoginSuccess("testuser", "wrappedDek", "saltEncB64", "saltAuthB64", testDEK)
        val dek = sessionManager.getActiveDEK()
        assertNotNull(dek)
        assertArrayEquals(testDEK.encoded, dek!!.encoded)
    }

    @Test
    fun `hasValidDEK returns false when no DEK stored`() {
        assertFalse(sessionManager.hasValidDEK())
    }

    @Test
    fun `hasValidDEK returns true after login`() {
        sessionManager.onLoginSuccess("testuser", "w", "s", "saltAuthB64", testDEK)
        assertTrue(sessionManager.hasValidDEK())
    }

    @Test
    fun `clearDEK removes stored data`() {
        sessionManager.onLoginSuccess("testuser", "w", "s", "saltAuthB64", testDEK)
        sessionManager.clearDEK()
        assertNull(sessionManager.loadWrappedDEK())
        assertFalse(sessionManager.hasValidDEK())
    }

    @Test
    fun `clearAll removes all data`() {
        sessionManager.onLoginSuccess("testuser", "w", "s", "saltAuthB64", testDEK)
        sessionManager.clearAll()
        assertNull(sessionManager.loadWrappedDEK())
        assertFalse(sessionManager.hasValidDEK())
    }

    @Test
    fun `getActiveDEK returns null after TTL expiration`() {
        sessionManager.onLoginSuccess("testuser", "w", "s", "saltAuthB64", testDEK)
        val pastTime = System.currentTimeMillis() - (4 * 24 * 60 * 60 * 1000L) // 4 天前，超过 3 天 TTL
        prefsMap["dek_stored_at"] = pastTime
        assertNull(sessionManager.getActiveDEK())
    }

    @Test
    fun `getActiveDEK returns DEK within TTL`() {
        val recentTime = System.currentTimeMillis() - (2 * 24 * 60 * 60 * 1000L) // 2 天前，仍在 3 天 TTL 内
        sessionManager.onLoginSuccess("testuser", "w", "s", "saltAuthB64", testDEK)
        prefsMap["dek_stored_at"] = recentTime
        assertNotNull(sessionManager.getActiveDEK())
    }

    @Test
    fun `loadWrappedDEK returns null after TTL`() {
        sessionManager.onLoginSuccess("testuser", "w", "s", "saltAuthB64", testDEK)
        val pastTime = System.currentTimeMillis() - (4 * 24 * 60 * 60 * 1000L) // 4 天前，超过 3 天 TTL
        prefsMap["dek_stored_at"] = pastTime
        assertNull(sessionManager.loadWrappedDEK())
    }
}
