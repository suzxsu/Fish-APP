package com.guanyji.app.data.repository

import java.time.Instant
import java.time.ZoneId
import com.guanyji.app.data.local.taskAlarmDataUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CareSchedulingTest {
    @Test fun occurrenceKeysAreStableForSameInstantAndZone() {
        val zone = ZoneId.of("Asia/Shanghai")
        val time = Instant.parse("2026-10-14T03:30:00Z").toEpochMilli()
        assertEquals(taskOccurrenceKey(time, zone), taskOccurrenceKey(time, zone))
        assertEquals("2026-10-14T11:30", taskOccurrenceKey(time, zone))
    }

    @Test fun dailyRecurrenceKeepsItsLocalTimeAcrossDaylightSavingChanges() {
        val zone = ZoneId.of("America/New_York")
        val before = Instant.parse("2026-03-07T14:30:00Z").toEpochMilli()
        val next = Instant.ofEpochMilli(nextRuleTime(before, 1, 9, 30, zone)).atZone(zone)
        assertEquals("2026-03-08T09:30", next.toLocalDateTime().toString())
        assertEquals("-04:00", next.offset.toString())
    }

    @Test fun alarmIdentityIncludesFullPersistentTaskId() {
        assertEquals("fishcare://task/task-1", taskAlarmDataUri("task-1"))
        assertNotEquals(taskAlarmDataUri("Aa"), taskAlarmDataUri("BB"))
    }
}
