package com.secretdiary.app.data.remote.api

import com.secretdiary.app.data.remote.dto.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * 隐秘日记 REST API 接口定义。
 * Base URL 为 `/api/v1/`，Retrofit 实例需以完整服务器地址 + /api/v1/ 结尾。
 */
interface ApiService {

    // ==================== 公共 ====================

    /** 获取服务端 KDF 配置 */
    @GET("config")
    suspend fun getConfig(): Response<ApiResponse<ConfigResponse>>

    // ==================== 认证 ====================

    /** 注册 */
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<Any>>

    /** 登录 */
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<LoginResponse>>

    /** 登出 */
    @POST("auth/logout")
    suspend fun logout(): Response<ApiResponse<Any>>

    /** 修改密码 */
    @PUT("auth/password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<ApiResponse<Any>>

    /** 获取 KDF 信息 */
    @GET("auth/kdf-info")
    suspend fun getKdfInfo(): Response<ApiResponse<KdfInfoResponse>>

    /** 设置恢复口令托管 */
    @PUT("auth/recovery")
    suspend fun setRecovery(@Body request: SetRecoveryRequest): Response<ApiResponse<Any>>

    /** 查询托管信息（无需认证） */
    @GET("auth/recovery")
    suspend fun getRecoveryInfo(@Query("username") username: String): Response<ApiResponse<RecoveryInfoResponse>>

    /** 恢复模式重置密码 */
    @POST("auth/recovery/reset")
    suspend fun recoveryReset(@Body request: RecoveryResetRequest): Response<ApiResponse<Any>>

    /** 删除托管信息 */
    @HTTP(method = "DELETE", path = "auth/recovery", hasBody = true)
    suspend fun deleteRecovery(@Body request: DeleteRecoveryRequest): Response<ApiResponse<Any>>

    /** 注销账户 */
    @HTTP(method = "DELETE", path = "auth/account", hasBody = true)
    suspend fun deleteAccount(@Body request: DeleteAccountRequest): Response<ApiResponse<Any>>

    // ==================== 日记 ====================

    /** 同步摘要列表 */
    @GET("entries/sync")
    suspend fun getSyncEntries(
        @Query("clientTime") clientTime: String,
        @Query("since") since: String?
    ): Response<ApiResponse<SyncResponse>>

    /** 批量获取日记详情 */
    @GET("entries/batch")
    suspend fun getBatchEntries(@Query("ids") ids: String): Response<ApiResponse<BatchEntryResponse>>

    /** 创建日记 */
    @POST("entries")
    suspend fun createEntry(@Body request: CreateEntryRequest): Response<ApiResponse<EntryResponse>>

    /** 更新日记（全量） */
    @PUT("entries/{id}")
    suspend fun updateEntry(
        @Path("id") id: String,
        @Body request: UpdateEntryRequest
    ): Response<ApiResponse<EntryResponse>>

    /** 更新日记元数据（部分） */
    @PATCH("entries/{id}/meta")
    suspend fun updateEntryMeta(
        @Path("id") id: String,
        @Body request: UpdateMetaRequest
    ): Response<ApiResponse<EntryResponse>>

    /** 删除日记 */
    @DELETE("entries/{id}")
    suspend fun deleteEntry(@Path("id") id: String): Response<ApiResponse<Any>>

    // ==================== 附件 ====================

    /** 上传附件 */
    @Multipart
    @POST("attachments")
    suspend fun uploadAttachment(
        @Part("diary_id") diaryId: RequestBody,
        @Part file: MultipartBody.Part,
        @Part("iv") iv: RequestBody,
        @Part("sha256") sha256: RequestBody
    ): Response<ApiResponse<AttachmentUploadResponse>>

    /** 下载附件 */
    @GET("attachments/{id}")
    suspend fun downloadAttachment(@Path("id") id: String): Response<ResponseBody>

    /** 删除附件 */
    @DELETE("attachments/{id}")
    suspend fun deleteAttachment(@Path("id") id: String): Response<ApiResponse<Any>>
}
