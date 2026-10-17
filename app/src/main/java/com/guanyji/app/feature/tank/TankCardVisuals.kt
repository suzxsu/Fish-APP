package com.guanyji.app.feature.tank

import com.guanyji.app.data.local.TankStatus
import com.guanyji.app.data.local.TankSummary
import com.guanyji.app.data.local.TankType
import com.guanyji.app.data.local.WaterTestValue
import com.guanyji.app.data.local.WaterTestWithValues
import com.guanyji.app.feature.overview.prettyNumber
import com.guanyji.app.feature.statistics.formatMeasurement
import com.guanyji.app.ui.theme.AquariumCardColors

internal data class TankWaterMetricUiModel(
    val code: String,
    val label: String,
    val value: String,
)

internal data class TankCardUiModel(
    val id: String,
    val photoId: String?,
    val name: String,
    val type: TankType,
    val typeLabel: String,
    val status: TankStatus,
    val statusLabel: String,
    val capacityLabel: String,
    val activeFishCount: Int,
    val metrics: List<TankWaterMetricUiModel>,
    val latestWaterTestAt: Long?,
    val lastWaterChangeAt: Long?,
    val feedingLabel: String,
)

internal fun TankType.tankCardAccent() = when (this) {
    TankType.freshwater -> AquariumCardColors.FreshwaterAccent
    TankType.saltwater -> AquariumCardColors.SaltwaterAccent
}

/** 徽标底色：需要用压暗色，白字才满足对比度要求（见 CardBadgeContrastTest）。 */
internal fun TankType.tankCardBadgeAccent() = when (this) {
    TankType.freshwater -> AquariumCardColors.FreshwaterBadge
    TankType.saltwater -> AquariumCardColors.SaltwaterBadge
}

internal fun TankStatus.tankCardStatusBadgeAccent() = when (this) {
    TankStatus.archived -> AquariumCardColors.StatusArchivedBadge
    else -> AquariumCardColors.StatusActiveBadge
}

internal fun tankCardModel(
    summary: TankSummary,
    latestTest: WaterTestWithValues?,
    temperatureUnit: String,
    todayFeedings: Int,
): TankCardUiModel {
    val tank = summary.tank
    return TankCardUiModel(
        id = tank.id,
        photoId = tank.photoId,
        name = tank.name.trim().ifBlank { "未命名鱼缸" },
        type = tank.type,
        typeLabel = tankWaterTypeLabel(tank.type),
        status = tank.status,
        statusLabel = if (tank.status == TankStatus.archived) "已归档" else "在养",
        capacityLabel = tankCapacityLabel(tank.capacityValue, tank.capacityUnit, tank.capacityIsEstimated),
        activeFishCount = summary.activeFishCount,
        metrics = tankWaterMetrics(tank.type, latestTest?.values.orEmpty(), temperatureUnit),
        latestWaterTestAt = summary.lastWaterTestAt,
        lastWaterChangeAt = summary.lastWaterChangeAt,
        feedingLabel = if (todayFeedings <= 0) "暂无记录" else "$todayFeedings 次",
    )
}

internal fun tankWaterMetrics(
    type: TankType,
    values: List<WaterTestValue>,
    temperatureUnit: String,
): List<TankWaterMetricUiModel> {
    val codes = listOf("temperature", "ph") + if (type == TankType.saltwater) {
        listOf("salinity", "specific_gravity")
    } else {
        emptyList()
    }
    return codes.map { code ->
        TankWaterMetricUiModel(
            code = code,
            label = tankMetricLabel(code),
            value = tankMetricValue(code, values.firstOrNull { it.parameterCode == code }, temperatureUnit),
        )
    }
}

internal fun tankCapacityLabel(
    value: Double?,
    unit: String?,
    isEstimated: Boolean,
): String {
    val number = value?.takeIf { it.isFinite() && it > 0 } ?: return "容量 --"
    val suffix = unit?.trim()?.takeIf(String::isNotEmpty)
    val text = if (suffix == null) number.prettyNumber() else "${number.prettyNumber()} $suffix"
    return if (isEstimated) "$text · 估算" else text
}

private fun tankMetricLabel(code: String): String = when (code) {
    "temperature" -> "温度"
    "ph" -> "pH"
    "salinity" -> "盐度"
    "specific_gravity" -> "比重"
    else -> code
}

private fun tankWaterTypeLabel(type: TankType): String =
    if (type == TankType.freshwater) "淡水缸" else "海水缸"

private fun tankMetricValue(
    code: String,
    row: WaterTestValue?,
    temperatureUnit: String,
): String {
    val number = row?.value?.takeIf(Double::isFinite) ?: return "--"
    return when (code) {
        "temperature" -> {
            val converted = formatMeasurement(number, row.unit, temperatureUnit)
            "${converted.first.prettyNumber()} ${converted.second}"
        }
        "ph" -> number.prettyNumber()
        else -> {
            val unit = row.unit.trim()
            if (unit.isEmpty()) number.prettyNumber() else "${number.prettyNumber()} $unit"
        }
    }
}
