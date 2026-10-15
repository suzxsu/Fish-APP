package com.guanyji.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.guanyji.app.data.local.*
import com.guanyji.app.data.repository.LocalRepository
import com.guanyji.app.data.repository.taskOccurrenceKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CareTaskRepositoryTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: FishDatabase
    private lateinit var repo: LocalRepository
    private val now = System.currentTimeMillis() + 86_400_000L
    private val tank = Tank(id = "tank-care", name = "Care tank", type = TankType.saltwater, photoId = null, capacityValue = null, capacityUnit = null, startedAt = null, notes = null, createdAt = now, updatedAt = now, salinityManagementEnabled = true)

    @Before fun createDatabase() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FishDatabase::class.java).build()
        repo = LocalRepository(db)
    }
    @After fun closeDatabase() = db.close()

    @Test fun completionCreatesRecordAndOneStableNextOccurrence() = runBlocking {
        repo.saveTank(tank)
        val first = task("repeat", now)
        repo.saveCareTask(first, intervalDays = 2, reminderEnabled = true)
        repo.completeCareTask(first.id, record(first), now)
        val all = db.care().allTasks()
        assertEquals(2, all.size)
        assertEquals(CareTaskStatus.completed, db.care().getTask(first.id)?.status)
        assertEquals(1, repo.careRecords().first().size)
        val next = all.single { it.status == CareTaskStatus.pending }
        assertEquals(first.id, db.care().getRule(next.ruleId!!)!!.id)
        assertEquals(next.instanceKey, taskOccurrenceKey(next.plannedAt, java.time.ZoneId.of(db.care().getRule(next.ruleId!!)!!.timeZoneId)))
        assertFalse(next.id == first.id)
    }

    @Test fun customTaskTitleIsRequiredAndContinuesWithItsRule() = runBlocking {
        repo.saveTank(tank)
        val missingTitle = task("missing-title", now).copy(type = CareTaskType.custom)
        assertTrue(runCatching { runBlocking { repo.saveCareTask(missingTitle) } }.isFailure)
        val custom = missingTitle.copy(customTitle = "清洁过滤器")
        repo.saveCareTask(custom, intervalDays = 14)
        repo.completeCareTask(custom.id, CareRecord(taskId = custom.id, tankId = custom.tankId, fishId = null, type = CareRecordType.custom, completedAt = now, payloadJson = null, notes = null), now)
        assertEquals("清洁过滤器", db.care().allTasks().single { it.status == CareTaskStatus.pending }.customTitle)
    }

    @Test fun pendingIntentIdentityIsStableAndDistinctEvenWhenRequestCodesCollide() {
        val first = CareNotificationScheduler.alarmIntent(context, "Aa")
        val repeated = CareNotificationScheduler.alarmIntent(context, "Aa")
        val collision = CareNotificationScheduler.alarmIntent(context, "BB")
        assertEquals(first, repeated)
        assertNotEquals(first, collision)
        first.cancel(); collision.cancel()
    }

    @Test fun notificationPermissionDenialLeavesInAppTaskAvailable() = runBlocking {
        if (Build.VERSION.SDK_INT < 33) return@runBlocking
        repo.saveTank(tank)
        val pending = task("permission-denied-task", now + 86_400_000L)
        repo.saveCareTask(pending, reminderEnabled = true)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val wasGranted = target.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (wasGranted) instrumentation.uiAutomation.revokeRuntimePermission(target.packageName, Manifest.permission.POST_NOTIFICATIONS)
        try {
            assertEquals(PackageManager.PERMISSION_DENIED, target.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
            CareNotificationScheduler.notify(context, pending)
            assertEquals(CareTaskStatus.pending, db.care().getTask(pending.id)?.status)
            assertEquals(pending.id, db.care().allTasks().single().id)
        } finally {
            if (wasGranted) instrumentation.uiAutomation.grantRuntimePermission(target.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @Test fun repeatingRuleCreationIsIdempotentAndSurvivesDatabaseReopen() = runBlocking<Unit> {
        val name = "care-restart-${java.util.UUID.randomUUID()}"
        context.deleteDatabase(name)
        db.close()
        db = Room.databaseBuilder(context, FishDatabase::class.java, name).build()
        repo = LocalRepository(db)
        repo.saveTank(tank)
        val recurring = task("restart-task", now).copy(type = CareTaskType.feeding)
        repo.saveCareTask(recurring, intervalDays = 3, reminderEnabled = true)
        repo.saveCareTask(recurring, intervalDays = 3, reminderEnabled = true)
        val ruleId = db.care().allTasks().single().ruleId
        assertEquals(1, db.care().allTasks().size)
        assertTrue(db.care().allTasks().single().reminderEnabled)
        db.close()
        db = Room.databaseBuilder(context, FishDatabase::class.java, name).build()
        repo = LocalRepository(db)
        assertEquals(ruleId, db.care().allTasks().single().ruleId)
        assertEquals(recurring.id, db.care().getRule(ruleId!!)!!.id)
        db.close()
        context.deleteDatabase(name)
    }

    @Test fun postponeSkipAndCancelUpdateOnlyOpenTaskAndSkipDoesNotCreateFact() = runBlocking {
        repo.saveTank(tank)
        val postponed = task("postpone", now)
        repo.saveCareTask(postponed)
        repo.postponeTask(postponed.id, now + 2 * 86_400_000L, at = now - 1000)
        assertEquals(now + 2 * 86_400_000L, db.care().getTask(postponed.id)?.plannedAt)
        assertEquals(now, db.care().getTask(postponed.id)?.postponedFrom)
        assertEquals(now - 1000, db.care().getTask(postponed.id)?.lastPostponedAt)
        val skipped = task("skip", now + 1000)
        repo.saveCareTask(skipped)
        repo.changeTaskStatus(skipped.id, CareTaskStatus.skipped, now)
        assertEquals(CareTaskStatus.skipped, db.care().getTask(skipped.id)?.status)
        assertTrue(repo.careRecords().first().isEmpty())
        val cancelled = task("cancel", now + 2000)
        repo.saveCareTask(cancelled, reminderEnabled = true)
        repo.changeTaskStatus(cancelled.id, CareTaskStatus.cancelled, now)
        assertEquals(CareTaskStatus.cancelled, db.care().getTask(cancelled.id)?.status)
        assertTrue(runCatching { repo.changeTaskStatus(cancelled.id, CareTaskStatus.cancelled, now) }.isFailure)
    }

    @Test fun fishStatusAndTankArchiveCancelTheirFutureTasks() = runBlocking {
        repo.saveTank(tank)
        val today = System.currentTimeMillis()
        val fish = Fish(id = "fish-care", tankId = tank.id, name = "Clownfish", species = null, coverPhotoId = null, addedAt = today - 1, birthDate = null, status = FishStatus.active, statusChangedAt = null, notes = null, createdAt = today - 1, updatedAt = today - 1)
        repo.saveFish(fish)
        val individual = task("fish-task", now + 1000, fish.id)
        repo.saveCareTask(individual)
        repo.changeFishStatus(fish.id, FishStatus.transferred, today, dateKnown = true)
        assertEquals(CareTaskStatus.cancelled, db.care().getTask(individual.id)?.status)
        val tankTask = task("tank-task", now + 2000)
        repo.saveCareTask(tankTask)
        repo.archiveTank(tank.id, now, confirmedWithActiveFish = false)
        assertEquals(CareTaskStatus.cancelled, db.care().getTask(tankTask.id)?.status)
    }

    @Test fun disablingSaltinityManagementCancelsOnlySaltinityTasks() = runBlocking {
        repo.saveTank(tank)
        val salinity = task("salt-task", now + 1000)
        val feeding = salinity.copy(id = "feed-task", type = CareTaskType.feeding)
        repo.saveCareTask(salinity); repo.saveCareTask(feeding)
        repo.saveTank(tank.copy(salinityManagementEnabled = false, updatedAt = now + 1))
        assertEquals(CareTaskStatus.cancelled, db.care().getTask(salinity.id)?.status)
        assertEquals(CareTaskStatus.pending, db.care().getTask(feeding.id)?.status)
    }

    private fun task(id: String, at: Long, fishId: String? = null) = CareTask(id = id, tankId = tank.id, fishId = fishId, type = CareTaskType.salinity_test, plannedAt = at, recurrenceRule = null, notes = null, createdAt = now, updatedAt = now)
    private fun record(task: CareTask) = CareRecord(taskId = task.id, tankId = task.tankId, fishId = task.fishId, type = CareRecordType.salinity_test, completedAt = now, payloadJson = null, notes = null)
}
