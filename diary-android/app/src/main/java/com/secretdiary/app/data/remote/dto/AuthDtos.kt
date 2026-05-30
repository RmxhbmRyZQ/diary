package com.secretdiary.app.data.remote.dto

import com.google.gson.annotations.SerializedName

// ---------- 注册 ----------

data class RegisterRequest(
    val username: String,
    val authKey: String,
    val saltAuth: String,
    val encryptedDek: String,
    val saltEnc: String,
    val kdfVersion: Int = 1,
    val kdfParams: KdfParams
)

// ---------- 登录 ----------

data class LoginRequest(
    val username: String,
    val authKey: String
)

data class LoginResponse(
    val userId: String,
    val encryptedDek: String,
    val saltEnc: String,
    val kdfVersion: Int,
    val kdfParams: KdfParams,
    val hasRecovery: Boolean
)

// ---------- 修改密码 ----------

data class ChangePasswordRequest(
    val oldAuthKey: String,
    val newAuthKeyHash: String,
    val newEncryptedDek: String,
    val newSaltEnc: String,
    @SerializedName("newKdfParams")
    val `newKdfParams`: KdfParams
)

// ---------- 恢复口令托管 ----------

data class SetRecoveryRequest(
    val recoveryData: String,
    val recoverySalt: String,
    val challenge: String,
    val encryptedChallenge: String
)

data class RecoveryInfoResponse(
    @SerializedName("recovery_data") val recoveryData: String?,
    @SerializedName("recovery_salt") val recoverySalt: String?,
    @SerializedName("salt_enc") val saltEnc: String?,
    val challenge: String?,
    @SerializedName("challenge_iv") val challengeIv: String?
)

data class RecoveryResetRequest(
    val username: String,
    val newAuthKeyHash: String,
    val newEncryptedDek: String,
    val newSaltEnc: String,
    @SerializedName("newKdfParams")
    val `newKdfParams`: KdfParams,
    val encryptedChallenge: String?
)

// ---------- 删除托管 / 注销 ----------

data class DeleteRecoveryRequest(val authKey: String)
data class DeleteAccountRequest(val authKey: String)

// ---------- KDF ----------

data class KdfParams(
    val algorithm: String,
    val iterations: Int
)

data class KdfInfoResponse(
    val current: KdfVersioned,
    val recommended: KdfVersioned
)

data class KdfVersioned(
    val kdfVersion: Int,
    val kdfParams: KdfParams
)

// ---------- 服务端配置 ----------

data class ConfigResponse(
    val kdf: KdfParams,
    val limits: LimitsConfig
)

data class LimitsConfig(
    @SerializedName("max_attachment_size_mb") val maxAttachmentSizeMb: Int,
    @SerializedName("max_attachments_per_entry") val maxAttachmentsPerEntry: Int
)
