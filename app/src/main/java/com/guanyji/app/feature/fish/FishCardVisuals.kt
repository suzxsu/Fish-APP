package com.guanyji.app.feature.fish

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.guanyji.app.data.local.FishStatus
import com.guanyji.app.data.local.FishSummary
import com.guanyji.app.data.local.Tank
import com.guanyji.app.data.local.TankType
import java.util.Locale

/**
 * 「我的鱼」游戏化鱼只卡片的视觉令牌。
 *
 * 卡面自带深水蓝渐变与浅色文字，不依赖浅色 / 深色主题的卡面颜色，因此在两种主题下对比度一致；
 * 页面底色继续复用总览页的 DashboardTokens.PageBackground，保证全局视觉统一。
 */
internal object FishCardTokens {
    // 卡面背景
    val CardTop = Color(0xFF0D3355)
    val CardBottom = Color(0xFF0A1F35)
    val PanelTop = Color(0xFF14436B)
    val PanelBottom = Color(0xFF0A2440)

    // 卡面文字
    val CardInk = Color(0xFFFFFFFF)
    val CardSecondaryInk = Color(0xFFA8C5DE)
    val CardMetricInk = Color(0xFFD8E8F5)

    // 装饰色（含少量金色）与描边
    val Gold = Color(0xFFE4C27C)
    val Border = Color(0xFF9FD3F0)
    val Gloss = Color(0x26FFFFFF)
    val ShadowTint = Color(0xFF0A2440)
    val ControlSurface = Color(0x4D0C2E4E)
    val ReefFallbackTop = Color(0xFF0C3A63)
    val ReefFallbackBottom = Color(0xFF124E77)

    // 圆角与尺寸
    val CardShape = RoundedCornerShape(18.dp)
    val ImageShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    val ChipShape = RoundedCornerShape(999.dp)
    val FieldShape = RoundedCornerShape(14.dp)
    val ImageAspectRatio = 1.28f
    val CardElevation = 8.dp
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
    val status: FishStatus,
    val statusLabel: String,
    val daysLabel: String,
    val lengthLabel: String,
    val feedingLabel: String,
)

/**
 * 由鱼只汇总数据与鱼缸列表构造卡片文案。
 * 所有取值都来自本地记录；未知数据统一显示为“暂无记录 / 未记录”，不会转换成 0。
 */
internal fun fishCardModel(row: FishSummary, tanks: List<Tank>, today: Long = System.currentTimeMillis()): FishCardUiModel {
    val fish = row.fish
    val tank = tanks.firstOrNull { it.id == fish.tankId }
    val species = fish.species?.trim()?.takeIf { it.isNotEmpty() }
    return FishCardUiModel(
        id = fish.id,
        name = fish.name.trim().ifBlank { "未命名鱼只" },
        speciesLine = listOfNotNull(species ?: "鱼种未记录", tank?.type?.let(::waterTypeLabel)).joinToString(" · "),
        tankLine = tank?.name?.trim()?.takeIf { it.isNotEmpty() } ?: "鱼缸未找到",
        status = fish.status,
        statusLabel = fish.status.label(),
        daysLabel = "${durationDays(fish.addedAt, fish.deathDate, fish.status == FishStatus.deceased, today)} 天",
        lengthLabel = cardLengthLabel(row.lastLength, row.lastLengthUnit),
        feedingLabel = if (row.todayFeedings <= 0) "暂无记录" else "${row.todayFeedings} 次",
    )
}

internal fun waterTypeLabel(type: TankType): String = if (type == TankType.freshwater) "淡水" else "海水"

/** 体长文案：没有记录时显示“暂无记录”，单位缺失时只显示真实数值，不补默认单位。 */
internal fun cardLengthLabel(value: Double?, unit: String?): String {
    val number = value?.takeIf { it.isFinite() && it > 0 } ?: return "暂无记录"
    val suffix = unit?.trim()?.takeIf { it.isNotEmpty() }
    return if (suffix == null) number.cardNumber() else "${number.cardNumber()} $suffix"
}

internal fun FishStatus.cardPalette(): FishCardStatusPalette = when (this) {
    FishStatus.active -> FishCardStatusPalette(Color(0xFF3FD0B6), Color(0xD9148A76), Color(0xFFEAFBF6))
    FishStatus.quarantine -> FishCardStatusPalette(Color(0xFFE2A03F), Color(0xD9B8771E), Color(0xFFFFF4E2))
    FishStatus.transferred -> FishCardStatusPalette(Color(0xFF8C7BEC), Color(0xD95A55C4), Color(0xFFEDEAFF))
    FishStatus.deceased -> FishCardStatusPalette(Color(0xFF93A3B1), Color(0xD93F4A57), Color(0xFFE3EAF0))
    FishStatus.archived -> FishCardStatusPalette(Color(0xFF6D8BB0), Color(0xD93A4A63), Color(0xFFE0EAF6))
}

internal fun FishStatus.label(): String = when (this) {
    FishStatus.active -> "在养"
    FishStatus.quarantine -> "隔离"
    FishStatus.transferred -> "已转出"
    FishStatus.deceased -> "已故"
    FishStatus.archived -> "已归档"
}

private fun Double.cardNumber(): String = if (this % 1.0 == 0.0) toLong().toString() else "%.1f".format(Locale.getDefault(), this)