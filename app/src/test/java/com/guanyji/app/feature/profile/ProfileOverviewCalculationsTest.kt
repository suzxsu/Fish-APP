package com.guanyji.app.feature.profile

import com.guanyji.app.data.local.WaterTest
import com.guanyji.app.data.local.WaterTestValue
import com.guanyji.app.data.local.WaterTestWithValues
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileOverviewCalculationsTest {
    @Test
    fun latestMeasurementReplacesOlderOutOfRangeMeasurement() {
        val rows = listOf(
            test("tank-a", 100, value("test-old", "ph", 9.0, 200)),
            test("tank-a", 300, value("test-new", "ph", 7.0, 300)),
        )

        assertEquals(0, countLatestOutOfRangeValues(rows))
    }

    @Test
    fun missingValuesAndValuesWithoutTargetsAreNotAlerts() {
        val rows = listOf(
            test("tank-a", 300, value("test-a", "ph", null, 300, min = 6.5, max = 8.0)),
            test("tank-a", 300, value("test-b", "temperature", 40.0, 300)),
        )

        assertEquals(0, countLatestOutOfRangeValues(rows))
    }

    @Test
    fun countsEachLatestOutOfRangeTankParameterOnce() {
        val rows = listOf(
            test("tank-a", 100, value("test-old", "ph", 9.0, null, min = 6.5, max = 8.0)),
            test("tank-a", 300, value("test-new", "ph", 9.0, null, min = 6.5, max = 8.0)),
            test("tank-b", 250, value("test-b", "ph", 5.0, null, min = 6.5, max = 8.0)),
        )

        assertEquals(2, countLatestOutOfRangeValues(rows))
    }

    private fun test(tankId: String, at: Long, value: WaterTestValue) = WaterTestWithValues(
        WaterTest(id = value.waterTestId, tankId = tankId, measuredAt = at, method = null, notes = null),
        listOf(value),
    )

    private fun value(id: String, parameter: String, actual: Double?, at: Long?, min: Double? = null, max: Double? = null) =
        WaterTestValue(id = "value-$id", waterTestId = id, parameterCode = parameter, value = actual, unit = "", targetMin = min, targetMax = max, measuredAt = at)
}
