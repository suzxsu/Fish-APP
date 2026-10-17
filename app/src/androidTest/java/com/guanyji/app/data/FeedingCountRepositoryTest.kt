package com.guanyji.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.guanyji.app.data.local.*
import com.guanyji.app.data.repository.LocalRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedingCountRepositoryTest {
    private lateinit var db: FishDatabase
    private lateinit var repository: LocalRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FishDatabase::class.java).build()
        repository = LocalRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun countsTodayFeedingsByTankWithBoundariesAndEventDeduplication() = runBlocking {
        val dayStart = 1_000L
        val dayEnd = 2_000L
        val tankA = tank("tank-a")
        val tankB = tank("tank-b")
        repository.saveTank(tankA)
        repository.saveTank(tankB)
        val fishA1 = fish("fish-a1", tankA.id)
        val fishA2 = fish("fish-a2", tankA.id)
        val fishB = fish("fish-b", tankB.id)
        repository.saveFish(fishA1)
        repository.saveFish(fishA2)
        repository.saveFish(fishB)

        repository.saveFeeding(event("before", tankA.id, dayStart - 1), listOf(tankTarget("before", tankA.id)))
        repository.saveFeeding(event("start", tankA.id, dayStart), listOf(tankTarget("start", tankA.id)))
        repository.saveFeeding(event("tank-wide", tankA.id, 1_200L), listOf(tankTarget("tank-wide", tankA.id)))
        repository.saveFeeding(event("multi-fish", tankA.id, 1_300L), listOf(fishTarget("multi-fish", fishA1.id), fishTarget("multi-fish", fishA2.id)))
        repository.saveFeeding(event("other-tank", tankB.id, 1_400L), listOf(tankTarget("other-tank", tankB.id)))
        repository.saveFeeding(event("null-tank", null, 1_500L), listOf(fishTarget("null-tank", fishA1.id), fishTarget("null-tank", fishA2.id)))
        repository.saveFeeding(event("end", tankA.id, dayEnd), listOf(tankTarget("end", tankA.id)))

        val counts = repository.feedingCountsByTank(dayStart, dayEnd).first().associate { it.tankId to it.feedingCount }

        assertEquals(4, counts[tankA.id])
        assertEquals(1, counts[tankB.id])
        assertTrue(repository.feedingCountsByTank(3_000L, 4_000L).first().isEmpty())
    }

    private fun tank(id: String) = Tank(
        id = id,
        name = id,
        type = TankType.freshwater,
        photoId = null,
        capacityValue = null,
        capacityUnit = null,
        startedAt = null,
        notes = null,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun fish(id: String, tankId: String) = Fish(
        id = id,
        tankId = tankId,
        name = id,
        species = null,
        coverPhotoId = null,
        addedAt = 0L,
        birthDate = null,
        status = FishStatus.active,
        statusChangedAt = null,
        notes = null,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun event(id: String, tankId: String?, fedAt: Long) = FeedingEvent(
        id = id,
        tankId = tankId,
        fedAt = fedAt,
        foodType = null,
        amountValue = null,
        amountUnit = null,
        result = null,
        notes = null,
    )

    private fun tankTarget(eventId: String, tankId: String) = FeedingTarget(
        id = "target-$eventId",
        feedingEventId = eventId,
        targetType = FeedingTargetType.tank,
        tankId = tankId,
        fishId = null,
        result = null,
    )

    private fun fishTarget(eventId: String, fishId: String) = FeedingTarget(
        id = "target-$eventId-$fishId",
        feedingEventId = eventId,
        targetType = FeedingTargetType.fish,
        tankId = null,
        fishId = fishId,
        result = null,
    )
}
