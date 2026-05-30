package com.secretdiary.app.ui.diaryedit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secretdiary.app.data.remote.api.ApiService
import com.secretdiary.app.data.repository.DiaryRepository
import com.secretdiary.app.security.CryptoManager
import com.secretdiary.app.security.SessionManager
import com.secretdiary.app.util.NetworkMonitor
import com.secretdiary.app.util.TimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject

data class DiaryEditUiState(
    val diaryDate: String = TimeUtils.todayBeijing(),
    val title: String = "",
    val content: String = "",
    val mood: String? = null,
    val weather: String? = null,
    val favorite: Boolean = false,
    val tags: String = "",
    val attachmentIds: List<String> = emptyList(),
    val removedAttachmentIds: List<String> = emptyList(),
    val pendingImages: List<Uri> = emptyList(),
    val existingEntryId: String? = null,
    val serverVersion: Int? = null,
    val isLoading: Boolean = false,
    val isNew: Boolean = true,
    val isOnline: Boolean = true,
    val isPreviewing: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
)

@HiltViewModel
class DiaryEditViewModel @Inject constructor(
    private val repository: DiaryRepository,
    private val apiService: ApiService,
    private val cryptoManager: CryptoManager,
    private val sessionManager: SessionManager,
    private val networkMonitor: NetworkMonitor,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DiaryEditUiState(isOnline = networkMonitor.isOnline.value)
    )
    val uiState: StateFlow<DiaryEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            networkMonitor.observe().collect { online ->
                _uiState.value = _uiState.value.copy(isOnline = online)
            }
        }
    }

    fun loadDiary(diaryDate: String) {
        // 切换日期时重置附件相关状态
        _uiState.value = _uiState.value.copy(
            diaryDate = diaryDate,
            isNew = true,
            attachmentIds = emptyList(),
            removedAttachmentIds = emptyList(),
            pendingImages = emptyList(),
            title = "",
            content = "",
            mood = null,
            weather = null,
            favorite = false,
            tags = "",
            existingEntryId = null,
            serverVersion = null,
            isPreviewing = false
        )
        viewModelScope.launch {
            val existing = repository.getByDiaryDate(diaryDate)
            if (existing != null) {
                _uiState.value = _uiState.value.copy(
                    title = existing.title,
                    content = existing.content,
                    mood = existing.mood,
                    weather = existing.weather,
                    favorite = existing.favorite,
                    tags = existing.tags.joinToString(", "),
                    attachmentIds = existing.attachmentIds,
                    existingEntryId = existing.id,
                    serverVersion = existing.serverVersion,
                    isNew = false
                )
            }
        }
    }

    fun onTitleChanged(v: String) { _uiState.value = _uiState.value.copy(title = v) }
    fun onContentChanged(v: String) { _uiState.value = _uiState.value.copy(content = v) }
    fun onMoodChanged(v: String?) { _uiState.value = _uiState.value.copy(mood = v) }
    fun onWeatherChanged(v: String?) { _uiState.value = _uiState.value.copy(weather = v) }
    fun onFavoriteToggled() { _uiState.value = _uiState.value.copy(favorite = !_uiState.value.favorite) }
    fun onDateChanged(v: String) { loadDiary(v) }
    fun onTagsChanged(v: String) { _uiState.value = _uiState.value.copy(tags = v) }
    fun togglePreview() { _uiState.value = _uiState.value.copy(isPreviewing = !_uiState.value.isPreviewing) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    fun addImage(uri: Uri) {
        if (!_uiState.value.isOnline) {
            _uiState.value = _uiState.value.copy(error = "需联网后管理附件")
            return
        }
        _uiState.value = _uiState.value.copy(
            pendingImages = _uiState.value.pendingImages + uri
        )
    }

    fun removePendingImage(index: Int) {
        val images = _uiState.value.pendingImages.toMutableList()
        if (index in images.indices) {
            images.removeAt(index)
            _uiState.value = _uiState.value.copy(pendingImages = images)
        }
    }

    fun removeExistingAttachment(attachmentId: String) {
        _uiState.value = _uiState.value.copy(
            attachmentIds = _uiState.value.attachmentIds - attachmentId,
            removedAttachmentIds = _uiState.value.removedAttachmentIds + attachmentId
        )
    }

    /** 上传单张图片：压缩 → 加密 → 上传 → 返回 attachment ID */
    private suspend fun uploadImage(uri: Uri): String? {
        val dek = sessionManager.getActiveDEK() ?: return null
        return withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val originalBytes = inputStream.readBytes()
            inputStream.close()

            // 图片压缩
            val compressed = compressImage(originalBytes)

            // 加密
            val (ctB64, ivB64) = cryptoManager.encrypt(compressed, dek)
            val encryptedBytes = android.util.Base64.decode(ctB64, android.util.Base64.NO_WRAP)
            val sha256 = cryptoManager.sha256Hex(encryptedBytes)

            // 上传（密文）
            val fileBody = encryptedBytes.toRequestBody("application/octet-stream".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", "encrypted_img", fileBody)
            val diaryIdBody = "00000000-0000-0000-0000-000000000000"
                .toRequestBody("text/plain".toMediaTypeOrNull())
            val ivBody = ivB64.toRequestBody("text/plain".toMediaTypeOrNull())
            val shaBody = sha256.toRequestBody("text/plain".toMediaTypeOrNull())

            val resp = apiService.uploadAttachment(diaryIdBody, filePart, ivBody, shaBody)
            if (resp.isSuccessful && resp.body()?.code == 0) {
                val attId = resp.body()!!.data!!.id
                repository.saveAttachmentIv(attId, ivB64)
                attId
            } else null
        }
    }

    private fun compressImage(bytes: ByteArray): ByteArray {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val isPng = options.outMimeType == "image/png"

        // GIF / 动图跳过压缩
        if (options.outMimeType == "image/gif") return bytes

        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes

        // 最大宽高 1920
        val maxSize = 1920
        val origW = bitmap.width
        val origH = bitmap.height
        if (origW > maxSize || origH > maxSize) {
            val ratio = minOf(maxSize.toFloat() / origW, maxSize.toFloat() / origH)
            val matrix = Matrix()
            matrix.postScale(ratio, ratio)
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, origW, origH, matrix, true)
        }

        val outputStream = ByteArrayOutputStream()
        val format = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        var quality = if (isPng) 100 else 80
        bitmap.compress(format, quality, outputStream)

        // 若压缩后仍 > 5MB，降质到 0.5
        if (outputStream.size() > 5 * 1024 * 1024 && !isPng) {
            outputStream.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
        }

        return outputStream.toByteArray()
    }

    fun saveDiary() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val state = _uiState.value

                // 1. 删除已移除的附件（从服务端及本地 IV 表）
                for (attId in state.removedAttachmentIds) {
                    try { apiService.deleteAttachment(attId) } catch (_: Exception) {}
                }

                // 2. 上传新图片
                val newAttachmentIds = mutableListOf<String>()
                for (uri in state.pendingImages) {
                    val attId = uploadImage(uri)
                    if (attId != null) {
                        newAttachmentIds.add(attId)
                        val placeholder = "\n![图片](attachment:$attId)\n"
                        _uiState.value = _uiState.value.copy(
                            content = _uiState.value.content + placeholder
                        )
                    }
                }

                val tags = state.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val id = state.existingEntryId ?: UUID.randomUUID().toString()

                // 合并保留的附件 ID 和新上传的附件 ID
                val allAttachmentIds = state.attachmentIds + newAttachmentIds

                val result = if (state.existingEntryId != null) {
                    repository.updateDiary(
                        id = id, diaryDate = state.diaryDate, title = state.title,
                        content = _uiState.value.content, tags = tags, mood = state.mood,
                        weather = state.weather, favorite = state.favorite,
                        attachmentIds = allAttachmentIds,
                        serverVersion = state.serverVersion
                    )
                } else {
                    repository.saveDiary(
                        id = id, diaryDate = state.diaryDate, title = state.title,
                        content = _uiState.value.content, tags = tags, mood = state.mood,
                        weather = state.weather, favorite = state.favorite,
                        attachmentIds = allAttachmentIds
                    )
                }
                result.fold(
                    onSuccess = { _uiState.value = _uiState.value.copy(isLoading = false, saved = true) },
                    onFailure = { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }
}
