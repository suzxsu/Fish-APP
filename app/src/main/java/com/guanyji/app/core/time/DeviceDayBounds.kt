package com.guanyji.app.core.time

import java.time.Instant
import java.time.ZoneId

/**
 * 设备当前时区下的自然日范围：[今天 0 点, 明天 0 点)。
 */
internal fun deviceDayBounds(
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): Pair<Long, Long> {
    val start = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    val end = Instant.ofEpochMilli(start).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return start to end
}
