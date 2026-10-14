package com.guanyji.app.feature.tank

fun isValidWaterMeasurement(valueText: String, minimumText: String, maximumText: String): Boolean {
    val value = valueText.toDoubleOrNull() ?: return false
    if (!value.isFinite()) return false
    val minimum = minimumText.takeIf(String::isNotBlank)?.toDoubleOrNull()
    val maximum = maximumText.takeIf(String::isNotBlank)?.toDoubleOrNull()
    if (minimumText.isNotBlank() && minimum?.isFinite() != true) return false
    if (maximumText.isNotBlank() && maximum?.isFinite() != true) return false
    return minimum == null || maximum == null || minimum <= maximum
}

fun isOutsideWaterTarget(value: Double, minimum: Double?, maximum: Double?): Boolean =
    (minimum != null && value < minimum) || (maximum != null && value > maximum)
