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
import com.secretdiary.app.sync.SyncManager
import com.secretdiary.app.sync.SyncState
import com.secretdiary.app.util.Base64Util
import com.secretdiary.app.util.NetworkMonitor
import com.secretdiary.app.util.TimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    val pendingImages: List<Pair<String, Uri>> = emptyList(), // (tempId, uri)
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
    private val syncManager: SyncManager,
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
        _uiState.value = _uiState.value.copy(
            diaryDate = diaryDate, isNew = true,
            attachmentIds = emptyList(), removedAttachmentIds = emptyList(),
            pendingImages = emptyList(), title = "", content = "",
            mood = null, weather = null, favorite = false, tags = "",
            existingEntryId = null, serverVersion = null, isPreviewing = false
        )
        viewModelScope.launch {
            var existing = repository.getByDiaryDate(diaryDate)
            if (existing == null && networkMonitor.isOnline.value) {
                syncManager.performSync()
                syncManager.syncState.first { it is SyncState.Success || it is SyncState.Failed }
                existing = repository.getByDiaryDate(diaryDate)
            }
            if (existing != null) {
                _uiState.value = _uiState.value.copy(
                    title = existing.title, content = existing.content,
                    mood = existing.mood, weather = existing.weather,
                    favorite = existing.favorite, tags = existing.tags.joinToString(", "),
                    attachmentIds = existing.attachmentIds,
                    existingEntryId = existing.id, serverVersion = existing.serverVersion,
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

    /**
     * 添加图片：生成临时 ID，立即在光标处（或末尾）插入占位文本，对齐 Web 端行为。
     */
    fun addImage(uri: Uri, cursorPosition: Int = -1) {
        if (!_uiState.value.isOnline) {
            _uiState.value = _uiState.value.copy(error = "需联网后管理附件")
            return
        }
        val tempId = "pending_${UUID.randomUUID().toString().take(8)}"
        val state = _uiState.value
        val placeholder = "\n![图片](attachment:$tempId)\n"

        val newContent = if (cursorPosition >= 0 && cursorPosition <= state.content.length) {
            state.content.substring(0, cursorPosition) + placeholder + state.content.substring(cursorPosition)
        } else {
            state.content + placeholder
        }

        _uiState.value = state.copy(
            pendingImages = state.pendingImages + (tempId to uri),
            content = newContent
        )
    }

    /** 移除待上传的图片，同时清理内容中的占位文本 */
    fun removePendingImage(index: Int) {
        val state = _uiState.value
        val images = state.pendingImages.toMutableList()
        if (index in images.indices) {
            val (tempId, _) = images[index]
            images.removeAt(index)
            val newContent = state.content.replace(
                Regex("!\\[[^\\]]*\\]\\(attachment:$tempId\\)\\n?"), ""
            )
            _uiState.value = state.copy(pendingImages = images, content = newContent)
        }
    }

    /**
     * 移除已有附件：同时清理日记内容中对应的 Markdown 图片引用，对齐 Web 端行为。
     */
    fun removeExistingAttachment(attachmentId: String) {
        val state = _uiState.value
        val newContent = state.content.replace(
            Regex("!\\[[^\\]]*\\]\\(attachment:$attachmentId\\)\\n?"), ""
        )
        _uiState.value = state.copy(
            attachmentIds = state.attachmentIds - attachmentId,
            removedAttachmentIds = state.removedAttachmentIds + attachmentId,
            content = newContent
        )
    }

    /** 上传单张图片：压缩 → 加密 → 上传 → 返回 attachment ID */
    private suspend fun uploadImage(uri: Uri): String? {
        val dek = sessionManager.getActiveDEK() ?: return null
        return withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val originalBytes = inputStream.readBytes()
            inputStream.close()

            val (compressed, mimeType) = compressImage(originalBytes)

            val (ctB64, ivB64) = cryptoManager.encrypt(compressed, dek)
            val encryptedBytes = Base64Util.decode(ctB64)
            val sha256 = cryptoManager.sha256Hex(encryptedBytes)

            val fileBody = encryptedBytes.toRequestBody(mimeType.toMediaTypeOrNull())
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

    private fun compressImage(bytes: ByteArray): Pair<ByteArray, String> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

        if (options.outMimeType == "image/gif") return Pair(bytes, "image/gif")

        val isPng = options.outMimeType == "image/png"
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return Pair(bytes, "image/jpeg")

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
        val mimeType = if (isPng) "image/png" else "image/jpeg"
        var quality = if (isPng) 100 else 80
        bitmap.compress(format, quality, outputStream)

        if (outputStream.size() > 5 * 1024 * 1024 && !isPng) {
            outputStream.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
        }

        return Pair(outputStream.toByteArray(), mimeType)
    }

    fun saveDiary() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val state = _uiState.value

                // 1. 删除已移除的附件（从服务端）
                for (attId in state.removedAttachmentIds) {
                    try { apiService.deleteAttachment(attId) } catch (_: Exception) {}
                }

                // 2. 上传新图片，建立 tempId → realId 映射
                val tempToRealMap = mutableMapOf<String, String>()
                val newAttachmentIds = mutableListOf<String>()

                for ((tempId, uri) in state.pendingImages) {
                    val attId = uploadImage(uri)
                    if (attId != null) {
                        newAttachmentIds.add(attId)
                        tempToRealMap[tempId] = attId
                    }
                }

                // 3. 将内容中的临时 ID 替换为真实 ID，对齐 Web 端行为
                var finalContent = _uiState.value.content
                for ((tempId, realId) in tempToRealMap) {
                    finalContent = finalContent.replace("attachment:$tempId", "attachment:$realId")
                }
                _uiState.value = _uiState.value.copy(content = finalContent)

                val tags = state.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val id = state.existingEntryId ?: UUID.randomUUID().toString()
                val allAttachmentIds = state.attachmentIds + newAttachmentIds

                val result = if (state.existingEntryId != null) {
                    repository.updateDiary(
                        id = id, diaryDate = state.diaryDate, title = state.title,
                        content = finalContent, tags = tags, mood = state.mood,
                        weather = state.weather, favorite = state.favorite,
                        attachmentIds = allAttachmentIds
                    )
                } else {
                    repository.saveDiary(
                        id = id, diaryDate = state.diaryDate, title = state.title,
                        content = finalContent, tags = tags, mood = state.mood,
                        weather = state.weather, favorite = state.favorite,
                        attachmentIds = allAttachmentIds
                    )
                }
                result.fold(
                    onSuccess = { diary ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false, saved = true,
                            existingEntryId = diary.id,
                            serverVersion = diary.serverVersion
                        )
                    },
                    onFailure = { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }
}
