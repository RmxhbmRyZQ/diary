package com.secretdiary.app.di

import android.content.Context
import com.secretdiary.app.data.remote.api.*
import com.secretdiary.app.security.SessionManager
import com.secretdiary.app.util.AppConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideCookieJar(@ApplicationContext context: Context): CookieJar =
        PersistentCookieJar(context)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        cookieJar: CookieJar,
        sessionManager: SessionManager,
        errorHandler: GlobalErrorHandler
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(logging)
            .addInterceptor(AuthInterceptor(sessionManager, errorHandler))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(AppConfig.BASE_URL).client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create()).build()

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService =
        retrofit.create(ApiService::class.java)
}

private class PersistentCookieJar(context: Context) : CookieJar {
    private val prefs = context.getSharedPreferences("diary_cookies", Context.MODE_PRIVATE)
    private val cookieCache = mutableMapOf<String, MutableList<Cookie>>()

    init {
        val saved = prefs.getStringSet("cookie_hosts", emptySet()) ?: emptySet()
        for (host in saved) {
            val encodedList = prefs.getStringSet("cookies_$host", emptySet()) ?: continue
            val decoded = encodedList.mapNotNull { decodeCookie(it) }.toMutableList()
            if (decoded.isNotEmpty()) cookieCache[host] = decoded
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookieCache[url.host] = cookies.toMutableList()
        val editor = prefs.edit()
        val hosts = (prefs.getStringSet("cookie_hosts", emptySet()) ?: emptySet()).toMutableSet()
        hosts.add(url.host)
        editor.putStringSet("cookie_hosts", hosts)
        val encoded = cookies.map { c ->
            "${c.name}=${c.value};${c.expiresAt};${c.domain};${c.path};${c.secure};${c.httpOnly}"
        }.toSet()
        editor.putStringSet("cookies_${url.host}", encoded)
        editor.apply()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        cookieCache[url.host]?.filter { it.expiresAt > System.currentTimeMillis() } ?: emptyList()

    private fun decodeCookie(encoded: String): Cookie? {
        return try {
            val parts = encoded.split(";")
            if (parts.size < 6) return null
            Cookie.Builder()
                .name(parts[0].substringBefore("="))
                .value(parts[0].substringAfter("="))
                .expiresAt(parts[1].toLong())
                .domain(parts[2])
                .path(parts[3])
                .apply { if (parts[4] == "true") secure() }
                .apply { if (parts[5] == "true") httpOnly() }
                .build()
        } catch (_: Exception) { null }
    }
}

class AuthInterceptor(
    private val sessionManager: SessionManager,
    private val errorHandler: GlobalErrorHandler
) : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val response = chain.proceed(request)
        when (response.code) {
            401 -> {
                // 登录接口的 401 是密码错误，不应清除已有会话
                val path = request.url.encodedPath
                if (!path.contains("/auth/login")) {
                    sessionManager.clearAll()
                    errorHandler.notifySessionExpired()
                }
            }
            409 -> { errorHandler.notifyConflict(null, null) }
            429 -> {
                val retryAfter = response.header("Retry-After")?.toIntOrNull() ?: 60
                errorHandler.notifyRateLimit(retryAfter)
            }
            400 -> {
                // 解析 JSON 响应体检测时间偏差，避免与参数校验 400 混淆
                val body = response.peekBody(Long.MAX_VALUE).string()
                val isTimeSkew = try {
                    val json = com.google.gson.JsonParser.parseString(body).asJsonObject
                    json.get("code")?.asInt == 400 &&
                        (json.get("message")?.asString?.contains("时间偏差") == true ||
                         json.get("message")?.asString?.contains("time") == true)
                } catch (_: Exception) {
                    body.contains("时间偏差")
                }
                if (isTimeSkew) errorHandler.notifyTimeSkew(body)
            }
        }
        return response
    }
}
