package com.guanyji.app.feature.feeding

import com.guanyji.app.data.local.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedingCalculationsTest {
    @Test fun oneTankEventRemainsOneRegardlessOfTargetCount() {
        val event = FeedingEvent(id = "event", tankId = "tank", fedAt = 1, foodType = null, amountValue = null, amountUnit = null, result = null, notes = null)
        val row = FeedingEventWithTargets(event, listOf("a", "b", "c").map { FeedingTarget(feedingEventId = event.id, targetType = FeedingTargetType.fish, tankId = null, fishId = it, result = null) })
        assertEquals(1, tankFeedingEventCount(listOf(row)))
        assertTrue(isFeedingAssociatedWithFish(row, "b", "tank"))
        assertFalse(isFeedingAssociatedWithFish(row, "other", "another-tank"))
    }

    @Test fun tankTargetAssociatesEveryFishWithoutMultiplyingTankEvents() {
        val event = FeedingEvent(id = "event", tankId = "tank", fedAt = 1, foodType = null, amountValue = null, amountUnit = null, result = null, notes = null)
        val row = FeedingEventWithTargets(event, listOf(FeedingTarget(feedingEventId = event.id, targetType = FeedingTargetType.tank, tankId = "tank", fishId = null, result = null)))
        assertEquals(1, tankFeedingEventCount(listOf(row)))
        assertTrue(isFeedingAssociatedWithFish(row, "any-fish", "tank"))
    }
}
