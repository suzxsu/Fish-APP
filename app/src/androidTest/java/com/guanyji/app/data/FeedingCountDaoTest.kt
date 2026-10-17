package com.guanyji.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.guanyji.app.data.local.Fish
import com.guanyji.app.data.local.FishDatabase
import com.guanyji.app.data.local.FeedingEvent
import com.guanyji.app.data.local.FeedingTarget
import com.guanyji.app.data.local.FeedingTargetType
import com.guanyji.app.data.local.Tank
import com.guanyji.app.data.local.TankType
import com.guanyji.app.data.repository.LocalRepository
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedingCountDaoTest {
    private val zone = ZoneId.of("UTC")
    private val dayStart = LocalDateTime.of(2025, 10, 16, 0, 0).atZone(zone).toInstant().toEpochMilli()
    private val dayEnd = LocalDateTime.of(2025, 10, 17, 0, 0).atZone(zone).toInstant().toEpochMilli()
    private lateinit var db: FishDatabase
    private lateinit var repository: LocalRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            FishDatabase::class.java,
        ).build()
        repository = LocalRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun countsOnlyEventsInsideTheNaturalDayBoundary() = runBlocking {
        val tank = saveTank()
        saveTankFeeding(tank.id, dayStart - 1)
        saveTankFeeding(tank.id, dayStart)
        saveTankFeeding(tank.id, dayEnd - 1)
        saveTankFeeding(tank.id, dayEnd)

        val counts = repository.feedingCountsByTank(dayStart, dayEnd).first()

        assertEquals(2, counts.single { it.tankId == tank.id }.feedingCount)
    }

    @Test
    fun tankWideAndMultiFishFeedingsAreEachCountedOnce() = runBlocking {
        val tank = saveTank()
        val firstFish = saveFish(tank.id)
        val secondFish = saveFish(tank.id)
        saveTankFeeding(tank.id, dayStart)
        saveMultiFishFeeding(dayStart, firstFish.id, secondFish.id)

        val counts = repository.feedingCountsByTank(dayStart, dayEnd).first()

        assertEquals(2, counts.single { it.tankId == tank.id }.feedingCount)
    }

    @Test
    fun nullTankEventUsesCurrentFishTankAndCountsOneEvent() = runBlocking {
        val tank = saveTank()
        val firstFish = saveFish(tank.id)
        val secondFish = saveFish(tank.id)
        saveMultiFishFeeding(dayStart, firstFish.id, secondFish.id)

        val counts = repository.feedingCountsByTank(dayStart, dayEnd).first()

        assertEquals(1, counts.single { it.tankId == tank.id }.feedingCount)
    }

    @Test
    fun differentTanksDoNotAffectEachOther() = runBlocking {
        val firstTank = saveTank()
        val secondTank = saveTank()
        val secondFish = saveFish(secondTank.id)
        saveTankFeeding(firstTank.id, dayStart)
        saveMultiFishFeeding(dayStart, secondFish.id)

        val counts = repository.feedingCountsByTank(dayStart, dayEnd).first()

        assertEquals(1, counts.single { it.tankId == firstTank.id }.feedingCount)
        assertEquals(1, counts.single { it.tankId == secondTank.id }.feedingCount)
    }

    @Test
    fun tankWithoutFeedingsHasNoCountRow() = runBlocking {
        val tank = saveTank()

        val counts = repository.feedingCountsByTank(dayStart, dayEnd).first()

        assertEquals(null, counts.firstOrNull { it.tankId == tank.id })
    }

    private suspend fun saveTank(): Tank {
        val tank = Tank(
            id = UUID.randomUUID().toString(),
            name = "Tank",
            type = TankType.freshwater,
            photoId = null,
            capacityValue = null,
            capacityUnit = null,
            startedAt = null,
            notes = null,
            createdAt = dayStart,
            updatedAt = dayStart,
        )
        repository.saveTank(tank)
        return tank
    }

    private suspend fun saveFish(tankId: String): Fish {
        val fish = Fish(
            tankId = tankId,
            name = "Fish",
            species = null,
            coverPhotoId = null,
            addedAt = dayStart,
            birthDate = null,
            status = com.guanyji.app.data.local.FishStatus.active,
            statusChangedAt = null,
            notes = null,
            createdAt = dayStart,
            updatedAt = dayStart,
        )
        repository.saveFish(fish)
        return fish
    }

    private suspend fun saveTankFeeding(tankId: String, fedAt: Long) {
        val event = FeedingEvent(
            tankId = tankId,
            fedAt = fedAt,
            foodType = null,
            amountValue = null,
            amountUnit = null,
            result = null,
            notes = null,
        )
        repository.saveFeeding(
            event,
            listOf(
                FeedingTarget(
                    feedingEventId = event.id,
                    targetType = FeedingTargetType.tank,
                    tankId = tankId,
                    fishId = null,
                    result = null,
                ),
            ),
        )
    }

    private suspend fun saveMultiFishFeeding(fedAt: Long, vararg fishIds: String) {
        val event = FeedingEvent(
            tankId = null,
            fedAt = fedAt,
            foodType = null,
            amountValue = null,
            amountUnit = null,
            result = null,
            notes = null,
        )
        repository.saveFeeding(
            event,
            fishIds.map { fishId ->
                FeedingTarget(
                    feedingEventId = event.id,
                    targetType = FeedingTargetType.fish,
                    tankId = null,
                    fishId = fishId,
                    result = null,
                )
            },
        )
    }
}
