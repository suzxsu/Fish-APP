package com.guanyji.app.core.time

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceDayBoundsTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun boundsUseProvidedZoneAndEndAtNextMidnight() {
        val now = LocalDateTime.of(2026, 10, 16, 23, 59, 59).atZone(zone).toInstant().toEpochMilli()

        val (start, end) = deviceDayBounds(now, zone)

        assertEquals(LocalDateTime.of(2026, 10, 16, 0, 0).atZone(zone).toInstant().toEpochMilli(), start)
        assertEquals(LocalDateTime.of(2026, 10, 17, 0, 0).atZone(zone).toInstant().toEpochMilli(), end)
    }

    @Test
    fun midnightStartsTheNextNaturalDay() {
        val midnight = LocalDateTime.of(2026, 10, 17, 0, 0).atZone(zone).toInstant().toEpochMilli()

        val (start, end) = deviceDayBounds(midnight, zone)

        assertEquals(midnight, start)
        assertEquals(LocalDateTime.of(2026, 10, 18, 0, 0).atZone(zone).toInstant().toEpochMilli(), end)
    }
}
