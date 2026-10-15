package com.guanyji.app.feature.overview

import com.guanyji.app.data.local.WaterTestValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class DashboardFormattingTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    @Test
    fun relativeTimeLabelDistinguishesTodayYesterdayAndOlderRecords() {
        val now = at(2026, 10, 15, 21, 0)
        assertEquals("今天 09:00", relativeTimeLabel(at(2026, 10, 15, 9, 0), now, Locale.CHINA))
        assertEquals("昨天 20:15", relativeTimeLabel(at(2026, 10, 14, 20, 15), now, Locale.CHINA))
        assertEquals("前天 07:30", relativeTimeLabel(at(2026, 10, 13, 7, 30), now, Locale.CHINA))
        assertEquals("10月12日 12:00", relativeTimeLabel(at(2026, 10, 12, 12, 0), now, Locale.CHINA))
    }

    @Test
    fun relativeTimeLabelTreatsTheSameCalendarDayAsTodayEvenBeforeCurrentTime() {
        val now = at(2026, 10, 15, 6, 0)
        assertEquals("今天 23:50", relativeTimeLabel(at(2026, 10, 15, 23, 50), now, Locale.CHINA))
    }

    @Test
    fun durationLabelNeverReportsNegativeDaysAsZero() {
        assertEquals("日期待核对", durationLabel(at(2026, 10, 20, 8, 0), at(2026, 10, 15, 8, 0)))
        assertEquals("0 天", durationLabel(at(2026, 10, 15, 9, 0), at(2026, 10, 15, 8, 0)))
        assertEquals("5 天", durationLabel(at(2026, 10, 10, 8, 0), at(2026, 10, 15, 8, 0)))
    }

    @Test
    fun parameterTextUsesReadableUnitsAndKeepsIntegerValuesTrimmed() {
        assertEquals("26°C", dashboardParameterText("temperature", 26.0, "°C"))
        assertEquals("26.5°C", dashboardParameterText("temperature", 26.5, "°C"))
        assertEquals("pH 7.2", dashboardParameterText("ph", 7.2, "pH"))
        assertEquals("SG 1.023", dashboardParameterText("specific_gravity", 1.023, "SG"))
        assertEquals("16 ppt", dashboardParameterText("salinity", 16.0, "ppt"))
    }

    @Test
    fun aquariumSummariesOnlyUseRecordedValuesAndSkipUnknownOnes() {
        val values = listOf(
            WaterTestValue(waterTestId = "t", parameterCode = "temperature", value = 26.0, unit = "°C", targetMin = null, targetMax = null),
            WaterTestValue(waterTestId = "t", parameterCode = "ph", value = null, unit = "pH", targetMin = null, targetMax = null),
            WaterTestValue(waterTestId = "t", parameterCode = "salinity", value = null, unit = "ppt", targetMin = null, targetMax = null),
            WaterTestValue(waterTestId = "t", parameterCode = "specific_gravity", value = 1.023, unit = "SG", targetMin = null, targetMax = null),
        )
        assertEquals(listOf("水温 26°C", "比重 SG 1.023"), aquariumParameterSummaries(values))
    }

    @Test
    fun aquariumSummariesExcludeNonFiniteValues() {
        val values = listOf(
            WaterTestValue(waterTestId = "t", parameterCode = "temperature", value = Double.NaN, unit = "°C", targetMin = null, targetMax = null),
            WaterTestValue(waterTestId = "t", parameterCode = "ph", value = Double.POSITIVE_INFINITY, unit = "pH", targetMin = null, targetMax = null),
        )
        assertTrue(aquariumParameterSummaries(values).isEmpty())
    }

    @Test
    fun aquariumSummariesRespectTheRequestedLimit() {
        val values = listOf(
            WaterTestValue(waterTestId = "t", parameterCode = "temperature", value = 25.0, unit = "°C", targetMin = null, targetMax = null),
            WaterTestValue(waterTestId = "t", parameterCode = "ph", value = 8.1, unit = "pH", targetMin = null, targetMax = null),
            WaterTestValue(waterTestId = "t", parameterCode = "salinity", value = 34.0, unit = "ppt", targetMin = null, targetMax = null),
            WaterTestValue(waterTestId = "t", parameterCode = "specific_gravity", value = 1.025, unit = "SG", targetMin = null, targetMax = null),
        )
        assertEquals(3, aquariumParameterSummaries(values).size)
        assertEquals(listOf("水温 25°C", "pH 8.1", "盐度 34 ppt"), aquariumParameterSummaries(values))
    }

    @Test
    fun waterSummaryLabelDoesNotPretendUnrecordedDataIsZero() {
        assertEquals("尚未测量", waterSummaryLabel(0))
        assertEquals("今日 1 次", waterSummaryLabel(1))
        assertEquals("今日 3 次", waterSummaryLabel(3))
    }
}
