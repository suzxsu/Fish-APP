package com.guanyji.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.guanyji.app.data.local.FishDatabase
import com.guanyji.app.data.local.WaterTest
import com.guanyji.app.data.local.WaterTestValue
import com.guanyji.app.data.repository.LocalRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 鱼缸卡片依赖「每缸最新一次水质」：多缸、多测次、同一时间戳都不能取错行。
 */
@RunWith(AndroidJUnit4::class)
class LatestWaterTestDaoTest {
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
    fun returnsOnlyTheLatestTestOfEachTank() = runBlocking {
        saveTest("tank-a", measuredAt = 100L, temperature = 24.0)
        saveTest("tank-a", measuredAt = 300L, temperature = 26.0)
        saveTest("tank-a", measuredAt = 200L, temperature = 25.0)
        saveTest("tank-b", measuredAt = 150L, temperature = 22.0)

        val latest = repository.latestWaterTestsByTank().first()

        assertEquals(2, latest.size)
        val tankA = latest.single { it.test.tankId == "tank-a" }
        assertEquals(300L, tankA.test.measuredAt)
        assertEquals(26.0, tankA.values.single { it.parameterCode == "temperature" }.value!!, 0.0001)
        val tankB = latest.single { it.test.tankId == "tank-b" }
        assertEquals(150L, tankB.test.measuredAt)
    }

    @Test
    fun tankWithoutTestsHasNoRow() = runBlocking {
        saveTest("tank-a", measuredAt = 100L, temperature = 24.0)

        val latest = repository.latestWaterTestsByTank().first()

        assertEquals(listOf("tank-a"), latest.map { it.test.tankId })
    }

    @Test
    fun identicalTimestampsStillReturnExactlyOneRowPerTank() = runBlocking {
        saveTest("tank-a", measuredAt = 100L, temperature = 24.0)
        saveTest("tank-a", measuredAt = 100L, temperature = 28.0)

        val latest = repository.latestWaterTestsByTank().first()

        assertEquals(1, latest.count { it.test.tankId == "tank-a" })
        // 同一时间戳只要求稳定取一行，不要求具体取哪一行；这里验证仍有完整参数值。
        assertTrue(latest.single().values.any { it.parameterCode == "temperature" })
    }

    private suspend fun saveTest(tankId: String, measuredAt: Long, temperature: Double) {
        val test = WaterTest(tankId = tankId, measuredAt = measuredAt, method = null, notes = null)
        repository.saveWaterTest(
            test,
            listOf(
                WaterTestValue(
                    waterTestId = test.id,
                    parameterCode = "temperature",
                    value = temperature,
                    unit = "°C",
                    targetMin = null,
                    targetMax = null,
                ),
            ),
        )
    }
}
