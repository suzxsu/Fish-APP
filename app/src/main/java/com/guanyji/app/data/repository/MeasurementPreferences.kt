package com.guanyji.app.data.repository

import com.guanyji.app.data.local.AppSetting

internal const val DefaultTemperatureUnit = "°C"
internal const val DefaultLengthUnit = "cm"

internal fun preferredTemperatureUnit(settings: List<AppSetting>): String =
    settings.preferredUnit("unit.temperature", DefaultTemperatureUnit)

internal fun preferredLengthUnit(settings: List<AppSetting>): String =
    settings.preferredUnit("unit.length", DefaultLengthUnit)

/** 单位偏好的统一读取口径：缺失、空白或只有空格时回落到默认单位。 */
private fun List<AppSetting>.preferredUnit(key: String, fallback: String): String =
    firstOrNull { it.key == key }?.value?.trim()?.takeIf(String::isNotEmpty) ?: fallback
