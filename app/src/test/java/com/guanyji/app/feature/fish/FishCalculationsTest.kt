package com.guanyji.app.feature.fish

import com.guanyji.app.data.local.GrowthRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FishCalculationsTest {
    @Test fun durationUsesLocalCalendarDaysAndStopsOnKnownDeathDate() {
        val start = day(2025, Calendar.JANUARY, 10)
        val end = day(2025, Calendar.JANUARY, 15)
        val today = day(2025, Calendar.JANUARY, 20)
        assertEquals(5L, durationDays(start, end, deceased = true, today = today))
        assertEquals(10L, durationDays(start, null, deceased = false, today = today))
        assertEquals(0L, durationDays(today, null, deceased = false, today = today))
    }

    @Test fun growthPointsKeepNullsOutConvertUnitsAndHandleSameDayRecords() {
        val first = GrowthRecord(id = "a", fishId = "fish", measuredAt = 1_800_000_000_000L, lengthValue = 21.0, lengthUnit = "mm", weightValue = null, weightUnit = null, method = null, photoId = null, notes = null)
        val second = first.copy(id = "b", measuredAt = first.measuredAt + 1_000, lengthValue = 2.2, lengthUnit = "cm")
        val weightOnly = first.copy(id = "c", measuredAt = first.measuredAt + 2_000, lengthValue = null, lengthUnit = null, weightValue = 0.5, weightUnit = "kg")
        val points = growthPoints(listOf(first, second, weightOnly), length = true, range = GrowthRange.All, now = first.measuredAt + 3_000)
        assertEquals(listOf(2.1, 2.2), points.map { it.value })
        assertEquals("cm", points.first().unit)
        assertEquals(1, growthPoints(listOf(first, second, weightOnly), length = false, range = GrowthRange.All, now = first.measuredAt + 3_000).size)
    }

    @Test fun growthRangeFiltersOldRowsAndUnsupportedUnitsAreNotPlotted() {
        val current = GrowthRecord(id = "new", fishId = "fish", measuredAt = 1_800_000_000_000L, lengthValue = 2.0, lengthUnit = "cm", weightValue = null, weightUnit = null, method = null, photoId = null, notes = null)
        val old = current.copy(id = "old", measuredAt = current.measuredAt - 100L * 86_400_000L)
        val unsupported = current.copy(id = "odd", lengthUnit = "fathom")
        assertEquals(listOf(current.id), growthPoints(listOf(old, current), true, GrowthRange.Days30, current.measuredAt).map { it.record.id })
        assertTrue(growthPoints(listOf(unsupported), true, GrowthRange.All, current.measuredAt).isEmpty())
    }

    private fun day(year: Int, month: Int, day: Int) = Calendar.getInstance().apply {
        clear()
        set(year, month, day, 12, 0, 0)
    }.timeInMillis
}
