package com.secretdiary.app.util

import org.junit.Assert.*
import org.junit.Test
import java.time.format.DateTimeFormatter

class TimeUtilsTest {

    @Test
    fun `nowBeijingIso returns valid ISO 8601 with plus08 offset`() {
        val result = TimeUtils.nowBeijingIso()
        assertTrue(result.contains("+08:00") || result.contains("+0800"))
        // 格式验证
        assertNotNull(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(result))
    }

    @Test
    fun `todayBeijing returns yyyy-MM-dd format`() {
        val result = TimeUtils.todayBeijing()
        assertTrue(result.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }

    @Test
    fun `millisToBeijingIso returns valid ISO 8601`() {
        val millis = System.currentTimeMillis()
        val result = TimeUtils.millisToBeijingIso(millis)
        assertNotNull(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(result))
    }

    @Test
    fun `nowBeijingIso is consistent when called twice in quick succession`() {
        val t1 = TimeUtils.nowBeijingIso()
        val t2 = TimeUtils.nowBeijingIso()
        // 两者应接近（同一时间戳的前半部分）
        assertEquals(t1.take(13), t2.take(13)) // 年份到小时
    }
}
