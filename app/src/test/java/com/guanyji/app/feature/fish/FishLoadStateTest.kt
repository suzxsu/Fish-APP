package com.guanyji.app.feature.fish

import com.guanyji.app.data.local.Fish
import com.guanyji.app.data.local.FishSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FishLoadStateTest {
    @Test
    fun loadingIsDistinctFromMissingData() {
        val loading: FishLoadState<FishSummary> = FishLoadState.Loading
        assertEquals(FishLoadState.Loading, loading)
        assertEquals(FishLoadState.NotFound, fishLoadState<FishSummary>(null))
    }

    @Test
    fun readFailureIsNotReportedAsMissingData() {
        val state = fishLoadState<FishSummary>(null, IllegalStateException("disk read failed"))
        assertTrue(state is FishLoadState.Error)
        assertEquals("disk read failed", (state as FishLoadState.Error).message)
    }

    @Test
    fun successfulDataIsPreserved() {
        val summary = FishSummary(
            fish = fish(),
            lastMeasuredAt = null,
            lastLength = null,
            lastLengthUnit = null,
            todayFeedings = 0,
        )
        assertEquals(FishLoadState.Success(summary), fishLoadState(summary))
    }

    @Test
    fun emptyRecordsAreAValidSuccessState() {
        val state = FishRecordsState.Success(fish(), events = emptyList(), growth = emptyList(), feedings = emptyList())
        assertEquals(
            FishRecordsState.Success(fish(), events = emptyList(), growth = emptyList(), feedings = emptyList()),
            state,
        )
    }

    private fun fish() = Fish(
        id = "fish-1",
        tankId = "tank-1",
        name = "Test",
        species = null,
        coverPhotoId = null,
        addedAt = 1L,
        birthDate = null,
        status = com.guanyji.app.data.local.FishStatus.active,
        statusChangedAt = null,
        notes = null,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
