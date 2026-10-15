package com.guanyji.app.feature.tank

import org.junit.Assert.assertEquals
import org.junit.Test

class TankFishStateTest {
    @Test
    fun missingInitialValueRemainsLoading() {
        assertEquals(TankFishState.Loading, tankFishState())
    }

    @Test
    fun successfullyLoadedEmptyListIsDistinctFromLoading() {
        assertEquals(TankFishState.Success(emptyList()), tankFishState(emptyList()))
    }

    @Test
    fun readFailureRemainsAnErrorInsteadOfBecomingEmptySuccess() {
        assertEquals(TankFishState.Error("database unavailable"), tankFishState(error = IllegalStateException("database unavailable")))
        assertEquals(TankFishState.Error("鱼只加载失败"), tankFishState(error = IllegalStateException()))
    }
}
