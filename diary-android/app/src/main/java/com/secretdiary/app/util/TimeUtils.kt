package com.secretdiary.app.util

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 北京时间（UTC+8）ISO 8601 时间工具。
 */
object TimeUtils {

    private val BEIJING_ZONE = ZoneId.of("Asia/Shanghai")
    val ISO_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * 获取当前北京时间 ISO 8601 格式，如 2026-05-27T20:00:00+08:00
     */
    fun nowBeijingIso(): String = ZonedDateTime.now(BEIJING_ZONE).format(ISO_FORMATTER)

    /**
     * 获取当前日期 yyyy-MM-dd（北京时间）。
     */
    fun todayBeijing(): String = ZonedDateTime.now(BEIJING_ZONE).format(DATE_FORMATTER)

    /**
     * 将毫秒时间戳转为北京时间 ISO 8601。
     */
    fun millisToBeijingIso(millis: Long): String =
        ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), BEIJING_ZONE)
            .format(ISO_FORMATTER)
}
