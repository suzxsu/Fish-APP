package com.guanyji.app.feature.tank

import java.util.Locale

internal fun estimateTankCapacityLiters(lengthCm: Double, widthCm: Double, heightCm: Double): Double? =
    (lengthCm * widthCm * heightCm / 1000.0).takeIf { it.isFinite() && it > 0 }

fun calculateWaterChangeRatioPercent(volume: Double, volumeUnit: String, capacity: Double?, capacityUnit: String?): Double? {
    if (!volume.isFinite() || volume <= 0 || capacity == null || !capacity.isFinite() || capacity <= 0) return null
    val normalizedVolume = volume.toLitersOrNull(volumeUnit) ?: return null
    val normalizedCapacity = capacityUnit?.let { capacity.toLitersOrNull(it) } ?: return null
    return (normalizedVolume / normalizedCapacity * 100.0).takeIf { it.isFinite() && it in 0.0..100.0 }
}

private fun Double.toLitersOrNull(unit: String): Double? = when (unit.lowercase(Locale.ROOT)) {
    "l", "liter", "liters" -> this
    "gal", "gallon", "gallons" -> this * 3.785411784
    else -> null
}
