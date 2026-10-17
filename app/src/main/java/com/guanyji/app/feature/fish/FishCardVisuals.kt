package com.guanyji.app.feature.fish

import androidx.compose.ui.graphics.Color
import com.guanyji.app.data.local.FishStatus
import com.guanyji.app.data.local.FishSummary
import com.guanyji.app.data.local.Tank
import com.guanyji.app.data.local.TankType
import com.guanyji.app.feature.overview.prettyNumber
import com.guanyji.app.feature.statistics.formatMeasurement
import com.guanyji.app.ui.theme.AquariumCardColors
import com.guanyji.app.ui.theme.AquariumCardTokens

/**
 * 「我的鱼」游戏化鱼只卡片的视觉令牌。
 *
 * 实际颜色与尺寸集中在 ui/theme，保证鱼只卡片和鱼缸卡片共用同一套视觉语言。
 */
internal object FishCardTokens {
    val CardInk = AquariumCardColors.CardInk
    val CardSecondaryInk = AquariumCardColors.CardSecondaryInk
    val CardMetricInk = AquariumCardColors.CardMetricInk
    val Border = AquariumCardColors.Border
    val ControlSurface = AquariumCardColors.ControlSurface
    val ReefFallbackTop = AquariumCardColors.ReefFallbackTop
    val ReefFallbackBottom = AquariumCardColors.ReefFallbackBottom
    val CardShape = AquariumCardTokens.CardShape
    val ChipShape = AquariumCardTokens.ChipShape
    val FieldShape = AquariumCardTokens.FieldShape
}

/** 卡片状态标签配色。状态全部来自数据库中的 [FishStatus]，不使用任何虚构等级或稀有度配色。 */
internal data class FishCardStatusPalette(
    val accent: Color,
    val chipBackground: Color,
    val chipText: Color,
)

/** 卡片渲染所需的全部文案，字段均来自真实 UI State。 */
internal data class FishCardUiModel(
    val id: String,
    val name: String,
    val speciesLine: String,
    val tankLine: String,
    val tankType: TankType?,
    val tankTypeLabel: String?,
    val status: FishStatus,
    val statusLabel: String,
    val daysLabel: String,
    val lengthLabel: String,
    val feedingLabel: String,
)

/**
 * 由鱼只汇总数据与鱼缸列表构造卡片文案。
 * 所有取值都来自本地记录；数值缺失显示“--”，今日无喂食记录显示“暂无记录”。
 */
internal fun fishCardModel(
    row: FishSummary,
    tanks: List<Tank>,
    lengthTargetUnit: String? = null,
    today: Long = System.currentTimeMillis(),
): FishCardUiModel {
    val fish = row.fish
    val tank = tanks.firstOrNull { it.id == fish.tankId }
    val species = fish.species?.trim()?.takeIf { it.isNotEmpty() }
    return FishCardUiModel(
        id = fish.id,
        name = fish.name.trim().ifBlank { "未命名鱼只" },
        speciesLine = species ?: "鱼种未记录",
        tankLine = tank?.name?.trim()?.takeIf { it.isNotEmpty() } ?: "鱼缸未找到",
        tankType = tank?.type,
        tankTypeLabel = tank?.type?.let(::waterTypeBadgeLabel),
        status = fish.status,
        statusLabel = fish.status.label(),
        daysLabel = "${durationDays(fish.addedAt, fish.deathDate, fish.status == FishStatus.deceased, today)} 天",
        lengthLabel = cardLengthLabel(row.lastLength, row.lastLengthUnit, lengthTargetUnit),
        feedingLabel = if (row.todayFeedings <= 0) "暂无记录" else "${row.todayFeedings} 次",
    )
}

internal fun waterTypeLabel(type: TankType): String = if (type == TankType.freshwater) "淡水" else "海水"

internal fun waterTypeBadgeLabel(type: TankType): String = if (type == TankType.freshwater) "淡水缸" else "海水缸"

internal fun TankType.cardAccent(): Color = when (this) {
    TankType.freshwater -> AquariumCardColors.FreshwaterAccent
    TankType.saltwater -> AquariumCardColors.SaltwaterAccent
}

/** 徽标底色需要压暗版本：浅色强调色上直接放白字达不到对比度要求。 */
internal fun TankType.cardBadgeAccent(): Color = when (this) {
    TankType.freshwater -> AquariumCardColors.FreshwaterBadge
    TankType.saltwater -> AquariumCardColors.SaltwaterBadge
}

/**
 * 体长文案：没有记录时显示“--”，有记录时沿用项目已有的长度单位换算逻辑。
 * 原始单位未知时不能安全换算，只展示真实数值，不补默认单位。
 */
internal fun cardLengthLabel(value: Double?, unit: String?, targetUnit: String? = null): String {
    val number = value?.takeIf { it.isFinite() && it > 0 } ?: return "--"
    val sourceUnit = unit?.trim()?.takeIf { it.isNotEmpty() } ?: return number.prettyNumber()
    val formatted = formatMeasurement(number, sourceUnit, targetUnit)
    return "${formatted.first.prettyNumber()} ${formatted.second}"
}

internal fun FishStatus.cardPalette(): FishCardStatusPalette = when (this) {
    FishStatus.active -> FishCardStatusPalette(
        AquariumCardColors.StatusActiveAccent,
        AquariumCardColors.StatusActiveBackground,
        AquariumCardColors.StatusActiveText,
    )
    FishStatus.quarantine -> FishCardStatusPalette(
        AquariumCardColors.StatusQuarantineAccent,
        AquariumCardColors.StatusQuarantineBackground,
        AquariumCardColors.StatusQuarantineText,
    )
    FishStatus.transferred -> FishCardStatusPalette(
        AquariumCardColors.StatusTransferredAccent,
        AquariumCardColors.StatusTransferredBackground,
        AquariumCardColors.StatusTransferredText,
    )
    FishStatus.deceased -> FishCardStatusPalette(
        AquariumCardColors.StatusDeceasedAccent,
        AquariumCardColors.StatusDeceasedBackground,
        AquariumCardColors.StatusDeceasedText,
    )
    FishStatus.archived -> FishCardStatusPalette(
        AquariumCardColors.StatusArchivedAccent,
        AquariumCardColors.StatusArchivedBackground,
        AquariumCardColors.StatusArchivedText,
    )
}

internal fun FishStatus.label(): String = when (this) {
    FishStatus.active -> "在养"
    FishStatus.quarantine -> "隔离"
    FishStatus.transferred -> "已转出"
    FishStatus.deceased -> "已故"
    FishStatus.archived -> "已归档"
}
