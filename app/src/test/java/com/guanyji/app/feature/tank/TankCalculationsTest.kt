package com.guanyji.app.feature.tank

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TankCalculationsTest {
    @Test fun estimatesLitersFromCentimetersAndRejectsInvalidDimensions() {
        assertEquals(120.0, estimateTankCapacityLiters(100.0, 40.0, 30.0)!!, 0.0)
        assertNull(estimateTankCapacityLiters(0.0, 40.0, 30.0))
        assertNull(estimateTankCapacityLiters(Double.POSITIVE_INFINITY, 40.0, 30.0))
    }
}
