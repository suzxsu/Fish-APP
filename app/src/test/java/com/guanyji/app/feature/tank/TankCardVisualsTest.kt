package com.guanyji.app.feature.tank

import com.guanyji.app.data.local.Tank
import com.guanyji.app.data.local.TankStatus
import com.guanyji.app.data.local.TankSummary
import com.guanyji.app.data.local.TankType
import com.guanyji.app.data.local.WaterTest
import com.guanyji.app.data.local.WaterTestValue
import com.guanyji.app.data.local.WaterTestWithValues
import org.junit.Assert.assertEquals
import org.junit.Test

class TankCardVisualsTest {
    @Test
    fun freshWaterCardShowsOnlyApplicableMetricsAndMissingValues() {
        val model = tankCardModel(
            type = TankType.freshwater,
            values = listOf(
                value("temperature", 26.0, "°C"),
                value("ph", null, "pH"),
                value("salinity", 1.023, "ppt"),
            ),
            todayFeedings = 0,
        )

        assertEquals(listOf("温度", "pH"), model.metrics.map { it.label })
        assertEquals("26 °C", model.metrics[0].value)
        assertEquals("--", model.metrics[1].value)
        assertEquals("暂无记录", model.feedingLabel)
    }

    @Test
    fun saltWaterCardShowsAllFourRequestedMetrics() {
        val model = tankCardModel(
            type = TankType.saltwater,
            values = listOf(
                value("temperature", 78.8, "°F"),
                value("ph", 8.1, "pH"),
                value("salinity", 34.0, "ppt"),
                value("specific_gravity", 1.024, "SG"),
            ),
            todayFeedings = 2,
        )

        assertEquals(listOf("温度", "pH", "盐度", "比重"), model.metrics.map { it.label })
        assertEquals("26 °C", model.metrics[0].value)
        assertEquals("8.1", model.metrics[1].value)
        assertEquals("34 ppt", model.metrics[2].value)
        assertEquals("1.024 SG", model.metrics[3].value)
        assertEquals("2 次", model.feedingLabel)
    }

    @Test
    fun unknownCapacityAndInvalidWaterValuesDoNotBecomeZero() {
        val model = tankCardModel(
            type = TankType.saltwater,
            values = listOf(
                value("temperature", Double.NaN, "°C"),
                value("ph", Double.POSITIVE_INFINITY, "pH"),
                value("salinity", null, "ppt"),
                value("specific_gravity", 0.0, "SG"),
            ),
            todayFeedings = 0,
        )

        assertEquals("容量 --", model.capacityLabel)
        // 0 是数据库中真实测量值时仍应显示；只有缺失或非法值才显示 --。
        assertEquals(listOf("--", "--", "--", "0 SG"), model.metrics.map { it.value })
    }

    private fun tankCardModel(
        type: TankType,
        values: List<WaterTestValue>,
        todayFeedings: Int,
    ): TankCardUiModel {
        val tank = Tank(
            id = "tank-1",
            name = "小苏的海缸",
            type = type,
            photoId = null,
            capacityValue = null,
            capacityUnit = null,
            capacityIsEstimated = false,
            startedAt = null,
            status = TankStatus.active,
            notes = null,
            createdAt = 0L,
            updatedAt = 0L,
        )
        val summary = TankSummary(
            tank = tank,
            activeFishCount = 2,
            lastWaterChangeAt = 100L,
            lastWaterTestAt = 200L,
        )
        val test = WaterTest(tankId = tank.id, measuredAt = 200L, method = null, notes = null)
        return tankCardModel(
            summary = summary,
            latestTest = WaterTestWithValues(test, values),
            temperatureUnit = "°C",
            todayFeedings = todayFeedings,
        )
    }

    private fun value(code: String, value: Double?, unit: String) = WaterTestValue(
        waterTestId = "test-1",
        parameterCode = code,
        value = value,
        unit = unit,
        targetMin = null,
        targetMax = null,
    )
}
