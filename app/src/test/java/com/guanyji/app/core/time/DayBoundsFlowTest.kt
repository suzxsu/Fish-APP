package com.guanyji.app.core.time

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「今日」边界必须能跟着自然日走。
 * 这里只钉住纯函数部分；定时器与去重由 [dayBoundsFlow] 组合保证。
 */
class DayBoundsFlowTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun oneMinuteBeforeMidnightWaitsOneMinute() {
        assertEquals(60_000L, nextDayBoundsDelay(at(2026, 10, 16, 23, 59), zone))
    }

    @Test
    fun midnightStartsAFreshFullDay() {
        assertEquals(86_400_000L, nextDayBoundsDelay(at(2026, 10, 17, 0, 0), zone))
    }

    @Test
    fun noonWaitsTheRemainingHalfDay() {
        assertEquals(43_200_000L, nextDayBoundsDelay(at(2026, 10, 16, 12, 0), zone))
    }

    @Test
    fun delayIsNeverNegativeSoTheTimerCannotBusyLoop() {
        listOf(at(2026, 10, 16, 0, 0), at(2026, 10, 16, 23, 59), at(2026, 10, 16, 12, 0)).forEach { now ->
            assertTrue(nextDayBoundsDelay(now, zone) >= MinDayBoundsDelayMillis)
        }
        // 复核上限必须小于一天，否则跨零点只能靠本次定时器命中。
        assertTrue(MaxDayBoundsDelayMillis < 86_400_000L)
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
}
