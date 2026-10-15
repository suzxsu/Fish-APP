package com.guanyji.app.feature.overview

import com.guanyji.app.data.local.CareTask
import com.guanyji.app.data.local.CareTaskType
import com.guanyji.app.data.local.WaterTestValue
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 总览页中不依赖 Compose 的展示计算：时间文案、水质值文案、任务标题。
 * 抽成独立文件便于用单元测试覆盖（与 FeedingCalculations / StatisticsCalculations 的做法一致）。
 */

/** 把时刻显示为“今天 09:00 / 昨天 20:15 / 10月12日 12:00”。 */
internal fun relativeTimeLabel(value: Long, now: Long = System.currentTimeMillis(), locale: Locale = Locale.getDefault()): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = now }
    val daysAgo = daysBetween(calendar, value)
    val clock = SimpleDateFormat("HH:mm", locale).format(Date(value))
    return when (daysAgo) {
        0L -> "今天 " + clock
        1L -> "昨天 " + clock
        2L -> "前天 " + clock
        else -> SimpleDateFormat("M月d日 HH:mm", locale).format(Date(value))
    }
}

/** 入缸时长文案；未来日期不谎报为 0 天。 */
internal fun durationLabel(value: Long, now: Long = System.currentTimeMillis()): String {
    val days = daysBetween(Calendar.getInstance().apply { timeInMillis = now }, value)
    return if (days < 0) "日期待核对" else days.toString() + " 天"
}

private fun daysBetween(reference: Calendar, value: Long): Long {
    val target = Calendar.getInstance().apply { timeInMillis = value }
    val startOfReference = reference.clone() as Calendar
    startOfReference.set(Calendar.HOUR_OF_DAY, 0)
    startOfReference.set(Calendar.MINUTE, 0)
    startOfReference.set(Calendar.SECOND, 0)
    startOfReference.set(Calendar.MILLISECOND, 0)
    val startOfTarget = target.clone() as Calendar
    startOfTarget.set(Calendar.HOUR_OF_DAY, 0)
    startOfTarget.set(Calendar.MINUTE, 0)
    startOfTarget.set(Calendar.SECOND, 0)
    startOfTarget.set(Calendar.MILLISECOND, 0)
    return (startOfReference.timeInMillis - startOfTarget.timeInMillis) / 86_400_000L
}

/**
 * 数值展示：整数去掉小数位；小数按量级保留有效位（比重等 3 位小数不会被截断成 1.0）。
 * 使用 BigDecimal 转换，避免 1.023 被二进制浮点表示成 1.0229999… 后截断。
 */
internal fun Double.prettyNumber(): String {
    if (!isFinite()) return ""
    if (this % 1.0 == 0.0) return toLong().toString()
    val magnitude = kotlin.math.abs(this)
    val decimals = when {
        magnitude >= 100.0 -> 1
        magnitude >= 1.0 -> 3
        else -> 4
    }
    return java.math.BigDecimal.valueOf(this).setScale(decimals, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}

/** 水质参数的中文名。 */
internal fun dashboardParameterLabel(code: String): String = when (code) {
    "temperature" -> "水温"
    "ph" -> "pH"
    "ammonia" -> "氨/铵"
    "nitrite" -> "亚硝酸盐"
    "nitrate" -> "硝酸盐"
    "salinity" -> "盐度"
    "specific_gravity" -> "比重"
    "alkalinity" -> "碱度"
    else -> code
}

/** 参数值文案，未知值不会显示成 0。 */
internal fun dashboardParameterText(code: String, value: Double, unit: String): String = when (code) {
    "temperature" -> value.prettyNumber() + "°C"
    "ph" -> "pH " + value.prettyNumber()
    "specific_gravity" -> "SG " + value.prettyNumber()
    "ammonia" -> "氨 " + value.prettyNumber()
    "nitrite" -> "亚硝酸盐 " + value.prettyNumber()
    "nitrate" -> "硝酸盐 " + value.prettyNumber()
    "alkalinity" -> "碱度 " + value.prettyNumber()
    else -> value.prettyNumber() + " " + unit
}

/** 总览卡片优先展示的参数顺序：温度 → pH → 盐度/比重。 */
internal val dashboardMetricPriority = listOf("temperature", "ph", "salinity", "specific_gravity")

/**
 * 从一次水质检测的实测值中挑出最多 [limit] 条可展示摘要，
 * 只使用真实存在的测量值，未记录或非有限值的参数直接跳过。
 */
internal fun aquariumParameterSummaries(values: List<WaterTestValue>, limit: Int = 3): List<String> {
    val summaries = ArrayList<String>(limit)
    dashboardMetricPriority.forEach { code ->
        if (summaries.size >= limit) return@forEach
        val row = values.firstOrNull { it.parameterCode == code } ?: return@forEach
        val number = row.value?.takeIf { it.isFinite() } ?: return@forEach
        // pH / 比重 这类参数自带单位前缀，避免出现“pH pH 7.2”这样的重复文案
        val text = dashboardParameterText(code, number, row.unit)
        summaries += if (text.startsWith(dashboardParameterLabel(code))) text else dashboardParameterLabel(code) + " " + text
    }
    return summaries
}

internal fun waterSummaryLabel(todayWaterTestTanks: Int): String =
    if (todayWaterTestTanks > 0) "今日 " + todayWaterTestTanks + " 次" else "尚未测量"

internal fun CareTask.displayTitleForOverview(): String = when (type) {
    CareTaskType.feeding -> "喂食"
    CareTaskType.water_change -> "换水"
    CareTaskType.water_test -> "水质检测"
    CareTaskType.salinity_test -> "盐度检测"
    CareTaskType.maintenance -> "设备维护"
    CareTaskType.custom -> customTitle ?: "自定义任务"
}
