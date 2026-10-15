package com.guanyji.app.feature.fish

import com.guanyji.app.data.local.Fish
import com.guanyji.app.data.local.FishStatus
import com.guanyji.app.data.local.WaterTestValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 详情页只允许展示真实记录：水质指标与档案标签都不得凭空生成。 */
class FishDetailVisualsTest {
    @Test fun waterMetricsOnlyUseRecordedValuesInPriorityOrder() {
        val metrics = fishWaterMetrics(listOf(water("salinity", null, "ppt"), water("ph", 7.2, "pH"), water("temperature", 26.0, "°C")))
        assertEquals(listOf("水温", "pH"), metrics.map { it.label })
        assertEquals(listOf("26°C", "7.2"), metrics.map { it.value })
    }

    @Test fun waterMetricsStayEmptyWithoutRecordsOrFiniteValues() {
        assertTrue(fishWaterMetrics(emptyList()).isEmpty())
        assertTrue(fishWaterMetrics(listOf(water("temperature", Double.NaN, "°C"))).isEmpty())
    }

    @Test fun waterMetricsKeepRealUnitsAndRespectLimit() {
        val metrics = fishWaterMetrics(
            listOf(water("specific_gravity", 1.024, "SG"), water("temperature", 25.4, "°C"), water("ph", 6.8, "pH"), water("salinity", 30.0, "ppt")),
            limit = 2,
        )
        assertEquals(listOf("水温", "pH"), metrics.map { it.label })
        assertEquals(listOf("25.4°C", "6.8"), metrics.map { it.value })
        val gravity = fishWaterMetrics(listOf(water("specific_gravity", 1.024, "SG"))).first()
        assertEquals("比重", gravity.label)
        assertEquals("SG 1.024", gravity.value)
    }

    @Test fun detailChipsOnlyContainUserRecordedFields() {
        val blank = fish(birthDate = null, sex = null, source = null)
        assertTrue(fishDetailChips(blank) { "2025-10-01" }.isEmpty())
        assertTrue(fishDetailChips(fish(birthDate = null, sex = "   ", source = "")) { "2025-10-01" }.isEmpty())

        val filled = fish(birthDate = 1_759_276_800_000L, sex = "雄", source = "福袋")
        val chips = fishDetailChips(filled) { "2025-10-01" }
        assertEquals(listOf("性别", "来源", "出生"), chips.map { it.label })
        assertEquals(listOf("雄", "福袋", "2025-10-01"), chips.map { it.value })
    }

    private fun water(code: String, value: Double?, unit: String) = WaterTestValue(
        waterTestId = "test-1",
        parameterCode = code,
        value = value,
        unit = unit,
        targetMin = null,
        targetMax = null,
    )

    private fun fish(birthDate: Long?, sex: String?, source: String?) = Fish(
        id = "fish-1",
        tankId = "tank-1",
        name = "小丑",
        species = "小丑鱼",
        coverPhotoId = null,
        addedAt = 1_759_276_800_000L,
        birthDate = birthDate,
        status = FishStatus.active,
        statusChangedAt = null,
        notes = null,
        sex = sex,
        source = source,
        createdAt = 1_759_276_800_000L,
        updatedAt = 1_759_276_800_000L,
    )
}