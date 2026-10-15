package com.guanyji.app.feature.fish

import com.guanyji.app.data.local.Fish
import com.guanyji.app.data.local.FishStatus
import com.guanyji.app.data.local.FishSummary
import com.guanyji.app.data.local.Tank
import com.guanyji.app.data.local.TankStatus
import com.guanyji.app.data.local.TankType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/** 「我的鱼」卡牌文案只允许复述本地真实记录，未知数据必须保持为空值。 */
class FishCardVisualsTest {
    @Test fun cardModelShowsRealRecordValues() {
        val addedAt = day(2024, Calendar.OCTOBER, 2)
        val model = fishCardModel(
            row = summary(fish = fish(addedAt = addedAt, name = "小丑", species = "小丑鱼"), lastLength = 2.0, lastLengthUnit = "cm", todayFeedings = 2),
            tanks = listOf(tank(TankType.saltwater, "我的海水缸")),
            today = day(2025, Calendar.OCTOBER, 15),
        )
        assertEquals("小丑", model.name)
        assertEquals("小丑鱼 · 海水", model.speciesLine)
        assertEquals("我的海水缸", model.tankLine)
        assertEquals("在养", model.statusLabel)
        assertEquals("378 天", model.daysLabel)
        assertEquals("2 cm", model.lengthLabel)
        assertEquals("2 次", model.feedingLabel)
    }

    @Test fun cardModelKeepsUnknownValuesEmptyInsteadOfZero() {
        val model = fishCardModel(
            row = summary(fish = fish(addedAt = day(2025, Calendar.JANUARY, 1), species = null), lastLength = null, lastLengthUnit = null, todayFeedings = 0),
            tanks = listOf(tank(TankType.freshwater, "水草缸")),
            today = day(2025, Calendar.JANUARY, 11),
        )
        assertEquals("鱼种未记录 · 淡水", model.speciesLine)
        assertEquals("10 天", model.daysLabel)
        assertEquals("暂无记录", model.lengthLabel)
        assertEquals("暂无记录", model.feedingLabel)
    }

    @Test fun cardModelHandlesDeletedTankAndDeceasedDuration() {
        val deathDate = day(2025, Calendar.MARCH, 20)
        val model = fishCardModel(
            row = summary(
                fish = fish(addedAt = day(2025, Calendar.JANUARY, 1), status = FishStatus.deceased, deathDate = deathDate, deathDateKnown = true),
                lastLength = 3.4,
                lastLengthUnit = null,
                todayFeedings = 0,
            ),
            tanks = emptyList(),
            today = day(2025, Calendar.OCTOBER, 15),
        )
        assertEquals("鱼缸未找到", model.tankLine)
        // 鱼缸不存在时不推测水体类型，只展示真实鱼种。
        assertEquals("小丑鱼", model.speciesLine)
        assertEquals("78 天", model.daysLabel)
        assertEquals("3.4", model.lengthLabel)
        assertEquals("已故", model.statusLabel)
    }

    @Test fun cardLengthLabelRejectsInvalidValuesAndMissingUnits() {
        assertEquals("暂无记录", cardLengthLabel(null, "cm"))
        assertEquals("暂无记录", cardLengthLabel(0.0, "cm"))
        assertEquals("暂无记录", cardLengthLabel(-1.0, "cm"))
        assertEquals("暂无记录", cardLengthLabel(Double.NaN, "cm"))
        assertEquals("7", cardLengthLabel(7.0, null))
        assertEquals("7 cm", cardLengthLabel(7.0, "cm"))
    }

    @Test fun statusLabelsAndPalettesCoverEveryRealStatus() {
        val expected = mapOf(
            FishStatus.active to "在养",
            FishStatus.quarantine to "隔离",
            FishStatus.transferred to "已转出",
            FishStatus.deceased to "已故",
            FishStatus.archived to "已归档",
        )
        assertEquals(expected.keys, FishStatus.entries.toSet())
        expected.forEach { (status, label) -> assertEquals(label, status.label()) }
        assertEquals(FishStatus.entries.size, FishStatus.entries.map { it.cardPalette().accent }.toSet().size)
    }

    private fun fish(
        addedAt: Long,
        name: String = "小丑",
        species: String? = "小丑鱼",
        status: FishStatus = FishStatus.active,
        deathDate: Long? = null,
        deathDateKnown: Boolean = true,
    ) = Fish(
        id = "fish-1",
        tankId = "tank-1",
        name = name,
        species = species,
        coverPhotoId = null,
        addedAt = addedAt,
        birthDate = null,
        status = status,
        statusChangedAt = null,
        notes = null,
        deathDateKnown = deathDateKnown,
        deathDate = deathDate,
        createdAt = addedAt,
        updatedAt = addedAt,
    )

    private fun summary(
        fish: Fish,
        lastLength: Double?,
        lastLengthUnit: String?,
        todayFeedings: Int,
    ) = FishSummary(
        fish = fish,
        lastMeasuredAt = lastLength?.let { fish.addedAt },
        lastLength = lastLength,
        lastLengthUnit = lastLengthUnit,
        todayFeedings = todayFeedings,
    )

    private fun tank(type: TankType, name: String) = Tank(
        id = "tank-1",
        name = name,
        type = type,
        photoId = null,
        capacityValue = null,
        capacityUnit = null,
        startedAt = null,
        notes = null,
        status = TankStatus.active,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun day(year: Int, month: Int, day: Int) = Calendar.getInstance().apply {
        clear()
        set(year, month, day, 12, 0, 0)
    }.timeInMillis
}
