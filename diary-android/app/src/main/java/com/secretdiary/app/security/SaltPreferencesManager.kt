package com.secretdiary.app.security

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 管理 diary_salts SharedPreferences，隔离 ViewModel 对 Context 的直接依赖。
 */
@Singleton
class SaltPreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("diary_salts", Context.MODE_PRIVATE)

    fun putSaltAuth(username: String, saltB64: String) {
        prefs.edit().putString("saltAuth_$username", saltB64).apply()
    }

    fun putSaltEnc(username: String, saltB64: String) {
        prefs.edit().putString("saltEnc_$username", saltB64).apply()
    }

    fun getSaltAuth(username: String): String? =
        prefs.getString("saltAuth_$username", null)

    fun getSaltEnc(username: String): String? =
        prefs.getString("saltEnc_$username", null)

    fun clear() {
        prefs.edit().clear().apply()
    }
}
