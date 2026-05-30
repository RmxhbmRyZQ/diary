package com.secretdiary.app.ui.components

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.DrawableResult
import coil.request.Options
import com.secretdiary.app.data.local.dao.AttachmentIvDao
import com.secretdiary.app.data.local.entity.AttachmentIvEntity
import com.secretdiary.app.security.CryptoManager
import com.secretdiary.app.util.AppConfig
import com.secretdiary.app.security.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

class AttachmentFetcher(
    private val attachmentId: String,
    private val baseUrl: String,
    private val attachmentIvDao: AttachmentIvDao,
    private val cryptoManager: CryptoManager,
    private val sessionManager: SessionManager,
    private val okHttpClient: OkHttpClient
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val url = "${baseUrl}attachments/$attachmentId"
        val resp = okHttpClient.newCall(
            okhttp3.Request.Builder().url(url).build()
        ).execute()

        if (!resp.isSuccessful) throw Exception("Attachment download failed: ${resp.code}")

        val encryptedBytes = resp.body?.bytes() ?: throw Exception("Empty attachment body")

        val expectedSha256 = resp.header("X-Content-SHA256")
        if (expectedSha256 != null) {
            if (cryptoManager.sha256Hex(encryptedBytes) != expectedSha256)
                throw Exception("附件完整性校验失败")
        }

        // Get IV: local DB first, fall back to response header
        var ivEntity = attachmentIvDao.getById(attachmentId)
        if (ivEntity == null) {
            val headerIv = resp.header("X-Content-IV")
            if (headerIv != null && headerIv.isNotEmpty()) {
                ivEntity = AttachmentIvEntity(attachmentId, headerIv)
                withContext(Dispatchers.IO) {
                    attachmentIvDao.upsert(ivEntity!!)
                }
            }
        }
        if (ivEntity == null) throw Exception("Attachment IV not found for $attachmentId")

        val dek = sessionManager.getActiveDEK() ?: throw Exception("DEK not available")
        val plaintext = cryptoManager.decrypt(
            android.util.Base64.encodeToString(encryptedBytes, android.util.Base64.NO_WRAP),
            ivEntity.ivB64, dek
        )

        val bitmap = BitmapFactory.decodeByteArray(plaintext, 0, plaintext.size)
            ?: throw Exception("Failed to decode attachment image")

        return DrawableResult(
            drawable = BitmapDrawable(null, bitmap),
            isSampled = false,
            dataSource = DataSource.NETWORK
        )
    }

    @Singleton
    class Factory @Inject constructor(
        private val attachmentIvDao: AttachmentIvDao,
        private val cryptoManager: CryptoManager,
        private val sessionManager: SessionManager,
        private val okHttpClient: OkHttpClient
    ) : Fetcher.Factory<android.net.Uri> {

        override fun create(data: android.net.Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != "attachment") return null
            return AttachmentFetcher(
                data.schemeSpecificPart,
                AppConfig.BASE_URL,
                attachmentIvDao,
                cryptoManager,
                sessionManager,
                okHttpClient
            )
        }
    }
}
