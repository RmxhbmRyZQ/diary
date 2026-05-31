package com.secretdiary.app.util

/**
 * Base64 编码工具，统一使用 java.util.Base64（带填充，对齐 Web 端 btoa()）。
 * 所有加解密、盐值、密钥相关的 Base64 操作必须通过此工具类。
 */
object Base64Util {
    fun encodeToString(bytes: ByteArray): String =
        java.util.Base64.getEncoder().encodeToString(bytes)

    fun decode(string: String): ByteArray =
        java.util.Base64.getDecoder().decode(string)
}
