package com.guanyji.app.feature.tank

internal fun estimateTankCapacityLiters(lengthCm: Double, widthCm: Double, heightCm: Double): Double? =
    (lengthCm * widthCm * heightCm / 1000.0).takeIf { it.isFinite() && it > 0 }
