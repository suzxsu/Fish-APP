package com.guanyji.app.feature.tank

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterQualityCalculationsTest {
    @Test fun validatesMeasuredValueAndOptionalTargetBounds() {
        assertTrue(isValidWaterMeasurement("7.4", "7.0", "8.0"))
        assertTrue(isValidWaterMeasurement("7.4", "", ""))
        assertFalse(isValidWaterMeasurement("", "7.0", "8.0"))
        assertFalse(isValidWaterMeasurement("NaN", "", ""))
        assertFalse(isValidWaterMeasurement("7.4", "invalid", "8.0"))
        assertFalse(isValidWaterMeasurement("7.4", "8.0", "7.0"))
    }

    @Test fun detectsOnlyValuesOutsideConfiguredBounds() {
        assertFalse(isOutsideWaterTarget(7.5, null, null))
        assertFalse(isOutsideWaterTarget(7.0, 7.0, 8.0))
        assertFalse(isOutsideWaterTarget(8.0, 7.0, 8.0))
        assertTrue(isOutsideWaterTarget(6.9, 7.0, 8.0))
        assertTrue(isOutsideWaterTarget(8.1, 7.0, 8.0))
    }
}
