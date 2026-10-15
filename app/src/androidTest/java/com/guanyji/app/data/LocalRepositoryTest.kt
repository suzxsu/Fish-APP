package com.guanyji.app.data

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.guanyji.app.data.local.*
import com.guanyji.app.data.repository.LocalRepository
import com.guanyji.app.data.repository.waterChangeTaskMarker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: FishDatabase
    private lateinit var repository: LocalRepository
    private val now = System.currentTimeMillis() - 86_400_000L

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FishDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, FishDatabase::class.java).build()
        repository = LocalRepository(db)
    }

    @After fun tearDown() = db.close()

    @Test fun generatedIdsAndUnknownMeasurementsStayNull() = runBlocking {
        val tank = tank()
        repository.saveTank(tank)
        val fish = fish(tank.id)
        repository.saveFish(fish)
        val record = GrowthRecord(fishId = fish.id, measuredAt = now, lengthValue = null, lengthUnit = null, weightValue = 1.0, weightUnit = "g", method = null, photoId = null, notes = null)
        repository.saveGrowthRecord(record)
        assertNotEquals(tank.id, fish.id)
        assertNull(db.growthRecords().observeForFish(fish.id).first().single().lengthValue)
        assertEquals(1.0, db.growthRecords().observeForFish(fish.id).first().single().weightValue!!, 0.0)
    }

    @Test fun initialGrowthIsAtomicAndStatusAndMoveEventsKeepTankHistory() = runBlocking {
        val oldTank = tank(); val newTank = tank()
        repository.saveTank(oldTank); repository.saveTank(newTank)
        val fish = fish(oldTank.id)
        val initial = GrowthRecord(fishId = fish.id, measuredAt = now, lengthValue = 4.5, lengthUnit = "cm", weightValue = null, weightUnit = null, method = null, photoId = null, notes = null)
        repository.saveFish(fish, initial)
        assertEquals(4.5, db.growthRecords().observeForFish(fish.id).first().single().lengthValue!!, 0.0)
        repository.moveFish(fish.id, newTank.id, now)
        val move = db.fish().observeEvents(fish.id).first().single { it.type == FishEventType.moved }
        assertEquals(oldTank.id, move.tankId); assertEquals(newTank.id, move.targetTankId)
        assertEquals(newTank.id, db.fish().get(fish.id)?.tankId)
    }

    @Test fun editingFishTankCancelsOldTankTasksAndRejectsArchivedTarget() = runBlocking {
        val oldTank = tank()
        val newTank = tank()
        repository.saveTank(oldTank)
        repository.saveTank(newTank)
        val fish = fish(oldTank.id)
        repository.saveFish(fish)
        val oldTask = CareTask(tankId = oldTank.id, fishId = fish.id, type = CareTaskType.feeding, plannedAt = now + 86_400_000, recurrenceRule = null, notes = null, createdAt = now, updatedAt = now)
        repository.saveCareTask(oldTask)

        repository.saveFish(fish.copy(tankId = newTank.id, updatedAt = now + 1))

        assertEquals(CareTaskStatus.cancelled, db.care().getTask(oldTask.id)?.status)
        val move = db.fish().observeEvents(fish.id).first().single { it.type == FishEventType.moved }
        assertEquals(oldTank.id, move.tankId)
        assertEquals(newTank.id, move.targetTankId)

        repository.saveTank(oldTank.copy(status = TankStatus.archived))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.saveFish(fish.copy(tankId = oldTank.id, updatedAt = now + 2)) } }
        assertEquals(newTank.id, db.fish().get(fish.id)?.tankId)
    }

    @Test fun newestIndependentGrowthValuesAreQueriedSeparately() = runBlocking {
        val tank = tank(); repository.saveTank(tank)
        val fish = fish(tank.id); repository.saveFish(fish)
        repository.saveGrowthRecord(GrowthRecord(id = "length-record", fishId = fish.id, measuredAt = now, lengthValue = 4.2, lengthUnit = "cm", weightValue = null, weightUnit = null, method = null, photoId = null, notes = null))
        repository.saveGrowthRecord(GrowthRecord(id = "weight-record", fishId = fish.id, measuredAt = now + 1, lengthValue = null, lengthUnit = null, weightValue = 8.0, weightUnit = "g", method = null, photoId = null, notes = null))
        assertEquals("length-record", repository.latestLength(fish.id).first()?.id)
        assertEquals("weight-record", repository.latestWeight(fish.id).first()?.id)
    }

    @Test fun growthRecordsRequireAValidMeasurementAndCanBeCorrectedOrDeleted() = runBlocking {
        val tank = tank(); repository.saveTank(tank)
        val fish = fish(tank.id); repository.saveFish(fish)
        val base = GrowthRecord(fishId = fish.id, measuredAt = now, lengthValue = 4.0, lengthUnit = "cm", weightValue = null, weightUnit = null, method = null, photoId = null, notes = null)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.saveGrowthRecord(base.copy(lengthValue = null, lengthUnit = null)) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.saveGrowthRecord(base.copy(lengthValue = Double.NaN)) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.saveGrowthRecord(base.copy(lengthValue = Double.POSITIVE_INFINITY)) } }
        repository.saveGrowthRecord(base)
        repository.saveGrowthRecord(base.copy(lengthValue = null, lengthUnit = null, weightValue = 2.5, weightUnit = "g", notes = "更正"))
        val corrected = db.growthRecords().observeForFish(fish.id).first().single()
        assertNull(corrected.lengthValue)
        assertEquals(2.5, corrected.weightValue!!, 0.0)
        assertEquals("更正", corrected.notes)
        repository.deleteGrowthRecord(corrected.id)
        assertTrue(db.growthRecords().observeForFish(fish.id).first().isEmpty())
    }

    @Test fun unknownDeathDateIsStoredExplicitlyAndCancelsIndividualFutureTask() = runBlocking {
        val tank = tank(); repository.saveTank(tank); val fish = fish(tank.id); repository.saveFish(fish)
        val task = CareTask(tankId = tank.id, fishId = fish.id, type = CareTaskType.feeding, plannedAt = now + 86_400_000, recurrenceRule = null, notes = null, createdAt = now, updatedAt = now)
        val tankTask = task.copy(id = "tank-wide-task", fishId = null, type = CareTaskType.water_test)
        repository.saveCareTask(task)
        repository.saveCareTask(tankTask)
        repository.changeFishStatus(fish.id, FishStatus.deceased, null, dateKnown = false)
        val saved = db.fish().get(fish.id)!!
        assertEquals(FishStatus.deceased, saved.status); assertFalse(saved.deathDateKnown); assertEquals(CareTaskStatus.cancelled, db.care().getTask(task.id)?.status)
        assertEquals(CareTaskStatus.pending, db.care().getTask(tankTask.id)?.status)
        assertFalse(db.fish().observeEvents(fish.id).first().first { it.status == FishStatus.deceased }.dateKnown)
    }

    @Test fun versionThreeMigrationPreservesFishAndAddsNullableProfileFields() {
        val name = "fish-migration-test"
        migrationHelper.createDatabase(name, 3).apply {
            execSQL("INSERT INTO tanks (id, name, type, photoId, capacityValue, capacityUnit, startedAt, status, notes, createdAt, updatedAt, lengthCm, widthCm, heightCm, capacityIsEstimated, equipment) VALUES ('tank-1', 'Old tank', 'freshwater', NULL, NULL, NULL, NULL, 'active', NULL, 1, 1, NULL, NULL, NULL, 0, NULL)")
            execSQL("INSERT INTO fish (id, tankId, name, species, coverPhotoId, addedAt, birthDate, status, statusChangedAt, notes, createdAt, updatedAt) VALUES ('fish-1', 'tank-1', 'Old fish', NULL, NULL, 1, NULL, 'active', NULL, NULL, 1, 1)")
            close()
        }
        val migrated = migrationHelper.runMigrationsAndValidate(name, 4, true, FishDatabase.MIGRATION_3_4)
        migrated.query("SELECT name, sex, source, deathDateKnown, deathDate FROM fish WHERE id = 'fish-1'").use { cursor ->
            assertTrue(cursor.moveToFirst()); assertEquals("Old fish", cursor.getString(0)); assertTrue(cursor.isNull(1)); assertTrue(cursor.isNull(2)); assertEquals(1, cursor.getInt(3)); assertTrue(cursor.isNull(4))
        }
        migrated.query("SELECT COUNT(*) FROM fish_events").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0)) }
        migrated.close()
    }

    @Test fun versionSevenCareMigrationPreservesOpenTaskAndAddsRuleAndSalinityFields() {
        val name = "care-migration-7-8"
        val oldDb = migrationHelper.createDatabase(name, 7)
        oldDb.execSQL("INSERT INTO tanks (id,name,type,photoId,capacityValue,capacityUnit,capacityIsEstimated,startedAt,equipment,status,notes,createdAt,updatedAt,lengthCm,widthCm,heightCm) VALUES ('legacy-tank','Legacy','saltwater',NULL,NULL,NULL,0,NULL,NULL,'active',NULL,1,1,NULL,NULL,NULL)")
        oldDb.execSQL("INSERT INTO care_tasks (id,tankId,fishId,type,plannedAt,recurrenceRule,status,notes,createdAt,updatedAt) VALUES ('legacy-task',NULL,NULL,'custom',1000,NULL,'pending','legacy',1,1)")
        FishDatabase.MIGRATION_7_8.migrate(oldDb)
        oldDb.query("SELECT status,ruleId,instanceKey,reminderEnabled FROM care_tasks WHERE id='legacy-task'").use { cursor ->
            assertTrue(cursor.moveToFirst()); assertEquals("pending", cursor.getString(0)); assertTrue(cursor.isNull(1)); assertTrue(cursor.isNull(2)); assertEquals(0, cursor.getInt(3))
        }
        oldDb.query("SELECT salinityManagementEnabled FROM tanks WHERE id='legacy-tank'").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0)) }
        oldDb.query("SELECT COUNT(*) FROM care_rules").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0)) }
        oldDb.close()
    }

    @Test fun foreignKeysRejectOrphansAndFishDeleteCascadesGrowth() = runBlocking {
        val tank = tank()
        repository.saveTank(tank)
        val fish = fish(tank.id)
        repository.saveFish(fish)
        repository.saveGrowthRecord(GrowthRecord(fishId = fish.id, measuredAt = now, lengthValue = 3.2, lengthUnit = "cm", weightValue = null, weightUnit = null, method = null, photoId = null, notes = null))
        assertThrows(Exception::class.java) { runBlocking { repository.saveFish(fish("missing-tank")) } }
        assertThrows(Exception::class.java) { runBlocking { db.tanks().delete(tank.id) } }
        db.fish().delete(fish.id)
        assertTrue(db.growthRecords().observeForFish(fish.id).first().isEmpty())
        db.tanks().delete(tank.id)
    }

    @Test fun oneMultiFishFeedingIsOneTankEventAndDuplicateTargetsAreRejected() = runBlocking {
        val tank = tank()
        repository.saveTank(tank)
        val firstFish = fish(tank.id)
        val secondFish = fish(tank.id)
        repository.saveFish(firstFish)
        repository.saveFish(secondFish)
        val event = FeedingEvent(tankId = tank.id, fedAt = now, foodType = "颗粒粮", amountValue = null, amountUnit = null, result = null, notes = null)
        val targets = listOf(firstFish, secondFish).map { FeedingTarget(feedingEventId = event.id, targetType = FeedingTargetType.fish, tankId = null, fishId = it.id, result = null) }
        repository.saveFeeding(event, targets)
        assertEquals(1, db.feeding().countTankEvents(tank.id))
        repository.saveFeeding(event.copy(notes = "更正"), listOf(targets.first().copy(id = "replacement", feedingEventId = event.id)), "颗粒粮")
        assertEquals(1, db.feeding().countTankEvents(tank.id))
        assertEquals("更正", db.feeding().getEvent(event.id)?.notes)
        assertEquals(1, db.feeding().getTargets(event.id).size)
        assertEquals("颗粒粮", db.commonFoods().observeAll().first().single().name)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.saveFeeding(event.copy(id = "duplicate"), listOf(targets.first().copy(id = "a", feedingEventId = "duplicate"), targets.first().copy(id = "b", feedingEventId = "duplicate"))) } }
        assertEquals(1, db.feeding().countTankEvents(tank.id))
    }

    @Test fun feedingMayHaveUnknownFoodAndTargetResultButRequiresARealTarget() = runBlocking {
        val tank = tank(); repository.saveTank(tank)
        val fish = fish(tank.id); repository.saveFish(fish)
        val event = FeedingEvent(tankId = tank.id, fedAt = now, foodType = null, amountValue = null, amountUnit = null, result = null, notes = null)
        repository.saveFeeding(event, listOf(FeedingTarget(feedingEventId = event.id, targetType = FeedingTargetType.fish, tankId = null, fishId = fish.id, result = null)))
        assertNull(db.feeding().getEvent(event.id)?.foodType)
        assertEquals(1, db.feeding().countTankEvents(tank.id))
    }

    @Test fun versionFourMigrationAddsCommonFoodsWithoutChangingFeedingHistory() {
        val name = "feeding-migration-test"
        migrationHelper.createDatabase(name, 4).apply {
            execSQL("INSERT INTO tanks (id, name, type, photoId, capacityValue, capacityUnit, startedAt, status, notes, createdAt, updatedAt, lengthCm, widthCm, heightCm, capacityIsEstimated, equipment) VALUES ('tank-1', 'Old tank', 'freshwater', NULL, NULL, NULL, NULL, 'active', NULL, 1, 1, NULL, NULL, NULL, 0, NULL)")
            execSQL("INSERT INTO feeding_events (id, tankId, fedAt, foodType, amountValue, amountUnit, result, notes) VALUES ('event-1', 'tank-1', 10, NULL, NULL, NULL, NULL, NULL)")
            close()
        }
        val migrated = migrationHelper.runMigrationsAndValidate(name, 5, true, FishDatabase.MIGRATION_4_5)
        migrated.query("SELECT id, foodType FROM feeding_events WHERE id = 'event-1'").use { cursor -> assertTrue(cursor.moveToFirst()); assertNull(cursor.getString(1)) }
        migrated.query("PRAGMA index_list(common_foods)").use { cursor ->
            var uniqueFound = false
            while (cursor.moveToNext()) if (cursor.getString(1) == "index_common_foods_name" && cursor.getInt(2) == 1) uniqueFound = true
            assertTrue(uniqueFound)
        }
        migrated.close()
    }

    @Test fun waterTestDaoLoadsValuesAndPreservesUnknownValueAsNull() = runBlocking {
        val tank = tank()
        repository.saveTank(tank)
        val test = WaterTest(tankId = tank.id, measuredAt = now, method = null, notes = null)
        val values = listOf(
            WaterTestValue(waterTestId = test.id, parameterCode = "ph", value = 7.2, unit = "pH", targetMin = 7.0, targetMax = 8.0, measuredAt = now - 10_000, method = "试剂", notes = "复测", targetSource = "user"),
            WaterTestValue(waterTestId = test.id, parameterCode = "ammonia", value = null, unit = "mg/L", targetMin = null, targetMax = null),
        )
        repository.saveWaterTest(test, values)
        val loaded = db.waterTests().observeForTank(tank.id).first().single()
        assertEquals(2, loaded.values.size)
        assertNull(loaded.values.single { it.parameterCode == "ammonia" }.value)
        val ph = loaded.values.single { it.parameterCode == "ph" }
        assertEquals(now - 10_000, ph.measuredAt)
        assertEquals("试剂", ph.method)
        assertEquals("复测", ph.notes)
        assertEquals("user", ph.targetSource)
        assertEquals(7.0, ph.targetMin!!, 0.0)
        db.openHelper.writableDatabase.execSQL("DELETE FROM water_tests WHERE id = ?", arrayOf(test.id))
        assertTrue(db.waterTests().observeForTank(tank.id).first().isEmpty())
    }

    @Test fun waterChangeComputesConvertedRatioEditsOneHistoryRowAndReschedulesOneTask() = runBlocking {
        val tank = tank().copy(capacityValue = 100.0, capacityUnit = "L", capacityIsEstimated = true)
        repository.saveTank(tank)
        val firstPlan = now + 86_400_000
        val change = WaterChange(tankId = tank.id, completedAt = now - 2_000, volumeValue = 6.604301, volumeUnit = "gal", ratio = null, nextPlannedAt = firstPlan, notes = "初次", supplies = "除氯剂")
        repository.saveWaterChange(change)
        assertEquals(25.0, db.waterChanges().observeForTank(tank.id).first().single().ratio!!, 0.01)
        val task = db.care().getTaskForWaterChangeMarker(waterChangeTaskMarker(change.id))!!
        assertEquals(firstPlan, task.plannedAt)
        val secondPlan = firstPlan + 86_400_000
        repository.saveWaterChange(change.copy(completedAt = now - 10_000, nextPlannedAt = secondPlan, notes = "更正"))
        assertEquals(1, db.waterChanges().observeForTank(tank.id).first().size)
        val corrected = db.waterChanges().observeForTank(tank.id).first().single()
        assertEquals(now - 10_000, corrected.completedAt)
        assertEquals("更正", corrected.notes)
        assertEquals(secondPlan, db.care().getTaskForWaterChangeMarker(waterChangeTaskMarker(change.id))?.plannedAt)
        assertEquals(task.id, db.care().getTaskForWaterChangeMarker(waterChangeTaskMarker(change.id))?.id)
    }

    @Test fun unknownCapacityDoesNotInventRatioAndRemovingPlanCancelsOpenTask() = runBlocking {
        val tank = tank()
        repository.saveTank(tank)
        val change = WaterChange(tankId = tank.id, completedAt = now, volumeValue = 5.0, volumeUnit = "L", ratio = null, nextPlannedAt = now + 1_000, notes = null)
        repository.saveWaterChange(change)
        assertNull(db.waterChanges().observeForTank(tank.id).first().single().ratio)
        repository.saveWaterChange(change.copy(nextPlannedAt = null))
        assertEquals(CareTaskStatus.cancelled, db.care().getTaskForWaterChangeMarker(waterChangeTaskMarker(change.id))?.status)
        assertEquals(1, db.waterChanges().observeForTank(tank.id).first().size)
    }

    @Test fun versionSixMigrationAddsOptionalWaterChangeFieldsAndPreservesHistory() {
        val name = "water-change-migration-test"
        migrationHelper.createDatabase(name, 6).apply {
            execSQL("INSERT INTO tanks (id, name, type, photoId, capacityValue, capacityUnit, startedAt, status, notes, createdAt, updatedAt, lengthCm, widthCm, heightCm, capacityIsEstimated, equipment) VALUES ('tank-1', 'Old tank', 'freshwater', NULL, NULL, NULL, NULL, 'active', NULL, 1, 1, NULL, NULL, NULL, 0, NULL)")
            execSQL("INSERT INTO water_changes (id, tankId, completedAt, volumeValue, volumeUnit, ratio, nextPlannedAt, notes) VALUES ('change-1', 'tank-1', 1234, 5, 'L', NULL, NULL, '历史')")
            close()
        }
        val migrated = migrationHelper.runMigrationsAndValidate(name, 7, true, FishDatabase.MIGRATION_6_7)
        migrated.query("SELECT completedAt, notes, supplies, beforeTemperature FROM water_changes WHERE id = 'change-1'").use { cursor ->
            assertTrue(cursor.moveToFirst()); assertEquals(1234L, cursor.getLong(0)); assertEquals("历史", cursor.getString(1)); assertTrue(cursor.isNull(2)); assertTrue(cursor.isNull(3))
        }
        migrated.close()
    }

    @Test fun versionFiveMigrationAddsNullableWaterValueMetadataAndKeepsExistingRows() {
        val name = "water-value-migration-test"
        migrationHelper.createDatabase(name, 5).apply {
            execSQL("INSERT INTO tanks (id, name, type, photoId, capacityValue, capacityUnit, startedAt, status, notes, createdAt, updatedAt, lengthCm, widthCm, heightCm, capacityIsEstimated, equipment) VALUES ('tank-1', 'Old tank', 'freshwater', NULL, NULL, NULL, NULL, 'active', NULL, 1, 1, NULL, NULL, NULL, 0, NULL)")
            execSQL("INSERT INTO water_tests (id, tankId, measuredAt, method, notes) VALUES ('test-1', 'tank-1', 1234, NULL, NULL)")
            execSQL("INSERT INTO water_test_values (id, waterTestId, parameterCode, value, unit, targetMin, targetMax) VALUES ('value-1', 'test-1', 'ph', 7.2, 'pH', NULL, NULL)")
            close()
        }
        val migrated = migrationHelper.runMigrationsAndValidate(name, 6, true, FishDatabase.MIGRATION_5_6)
        migrated.query("SELECT value, measuredAt, method, notes, targetSource FROM water_test_values WHERE id = 'value-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(7.2, cursor.getDouble(0), 0.0)
            assertTrue(cursor.isNull(1)); assertTrue(cursor.isNull(2)); assertTrue(cursor.isNull(3)); assertTrue(cursor.isNull(4))
        }
        migrated.close()
    }

    @Test fun deletingPhotoClearsTankCoverReference() = runBlocking {
        val photo = Photo(localPath = "photos/tank.jpg", thumbnailPath = "photos/tank-thumb.jpg", mimeType = "image/jpeg", width = 100, height = 100, createdAt = now)
        repository.savePhoto(photo)
        repository.saveTank(tank().copy(photoId = photo.id))
        db.photos().delete(photo.id)
        assertNull(db.tanks().observeAll().first().single().tank.photoId)
    }

    @Test fun completionIsAtomicAndCannotBeRepeated() = runBlocking {
        val tank = tank()
        repository.saveTank(tank)
        val task = CareTask(tankId = tank.id, fishId = null, type = CareTaskType.water_test, plannedAt = now, recurrenceRule = null, notes = null, createdAt = now, updatedAt = now)
        repository.saveCareTask(task)
        val invalidRecord = CareRecord(taskId = task.id, tankId = "missing-tank", fishId = null, type = CareRecordType.water_test, completedAt = now, payloadJson = null, notes = null)
        assertThrows(Exception::class.java) { runBlocking { repository.completeCareTask(task.id, invalidRecord.copy(id = "invalid-record"), now) } }
        assertEquals(CareTaskStatus.pending, db.care().getTask(task.id)?.status)
        val record = invalidRecord.copy(id = "valid-record", tankId = tank.id)
        repository.completeCareTask(task.id, record, now)
        assertEquals(CareTaskStatus.completed, db.care().getTask(task.id)?.status)
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.completeCareTask(task.id, record.copy(id = "second-record"), now) } }
        Unit
    }

    @Test fun versionOneMigrationPreservesPhotosAndAddsLookupIndex() {
        val name = "migration-test"
        migrationHelper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO photos (id, localPath, thumbnailPath, mimeType, width, height, createdAt) VALUES ('photo-1', 'photos/original/hash', NULL, 'image/jpeg', NULL, NULL, 1)")
            close()
        }
        val migrated = migrationHelper.runMigrationsAndValidate(name, 2, true, FishDatabase.MIGRATION_1_2)
        migrated.query("SELECT localPath FROM photos WHERE id = 'photo-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("photos/original/hash", cursor.getString(0))
        }
        migrated.query("PRAGMA index_list(photos)").use { cursor ->
            val indexes = buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
            assertTrue(indexes.contains("index_photos_localPath"))
        }
        migrated.close()
    }

    @Test fun versionOneFullUpgradePreservesTankFishGrowthFeedingAndCareHistory() {
        val name = "full-migration-v1-v8"
        migrationHelper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO tanks (id,name,type,photoId,capacityValue,capacityUnit,startedAt,status,notes,createdAt,updatedAt) VALUES ('legacy-tank','Legacy tank','freshwater',NULL,NULL,NULL,NULL,'active',NULL,1,1)")
            execSQL("INSERT INTO fish (id,tankId,name,species,coverPhotoId,addedAt,birthDate,status,statusChangedAt,notes,createdAt,updatedAt) VALUES ('legacy-fish','legacy-tank','Legacy fish',NULL,NULL,1,NULL,'active',NULL,NULL,1,1)")
            execSQL("INSERT INTO growth_records (id,fishId,measuredAt,lengthValue,lengthUnit,weightValue,weightUnit,method,photoId,notes) VALUES ('legacy-growth','legacy-fish',2,NULL,NULL,0.0,'g',NULL,NULL,NULL)")
            execSQL("INSERT INTO feeding_events (id,tankId,fedAt,foodType,amountValue,amountUnit,result,notes) VALUES ('legacy-feed','legacy-tank',3,NULL,NULL,NULL,NULL,NULL)")
            execSQL("INSERT INTO feeding_targets (id,feedingEventId,targetType,tankId,fishId,result) VALUES ('legacy-target','legacy-feed','fish',NULL,'legacy-fish',NULL)")
            execSQL("INSERT INTO care_tasks (id,tankId,fishId,type,plannedAt,recurrenceRule,status,notes,createdAt,updatedAt) VALUES ('legacy-task','legacy-tank','legacy-fish','feeding',4,NULL,'pending',NULL,1,1)")
            execSQL("INSERT INTO care_records (id,taskId,tankId,fishId,type,completedAt,payloadJson,notes) VALUES ('legacy-care','legacy-task','legacy-tank','legacy-fish','feeding',5,NULL,NULL)")
            close()
        }
        val migrated = migrationHelper.runMigrationsAndValidate(
            name,
            FishDatabase.VERSION,
            true,
            FishDatabase.MIGRATION_1_2,
            FishDatabase.MIGRATION_2_3,
            FishDatabase.MIGRATION_3_4,
            FishDatabase.MIGRATION_4_5,
            FishDatabase.MIGRATION_5_6,
            FishDatabase.MIGRATION_6_7,
            FishDatabase.MIGRATION_7_8,
        )
        try {
            migrated.query("SELECT id FROM tanks WHERE id='legacy-tank'").use { assertTrue(it.moveToFirst()) }
            migrated.query("SELECT id FROM fish WHERE id='legacy-fish' AND tankId='legacy-tank'").use { assertTrue(it.moveToFirst()) }
            migrated.query("SELECT id,lengthValue,weightValue FROM growth_records WHERE id='legacy-growth'").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertTrue(cursor.isNull(1)); assertEquals(0.0, cursor.getDouble(2), 0.0)
            }
            migrated.query("SELECT COUNT(*) FROM feeding_events JOIN feeding_targets ON feeding_targets.feedingEventId=feeding_events.id WHERE feeding_events.id='legacy-feed' AND feeding_targets.fishId='legacy-fish'").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals(1, cursor.getInt(0))
            }
            migrated.query("SELECT status,reminderEnabled FROM care_tasks WHERE id='legacy-task'").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals("pending", cursor.getString(0)); assertEquals(0, cursor.getInt(1))
            }
            migrated.query("SELECT taskId,fishId FROM care_records WHERE id='legacy-care'").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals("legacy-task", cursor.getString(0)); assertEquals("legacy-fish", cursor.getString(1))
            }
            migrated.query("PRAGMA foreign_key_check").use { assertFalse("Migration introduced foreign-key violations", it.moveToFirst()) }
        } finally {
            migrated.close()
        }
    }

    @Test fun failedMigrationRollsBackSchemaAndPreservesOriginalRows() {
        val name = "failed-migration-preserves-data"
        migrationHelper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO tanks (id,name,type,photoId,capacityValue,capacityUnit,startedAt,status,notes,createdAt,updatedAt) VALUES ('protected-tank','Protected tank','freshwater',NULL,NULL,NULL,NULL,'active',NULL,1,1)")
            close()
        }
        val failingMigration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tanks ADD COLUMN injectedFailure TEXT")
                throw SQLiteException("Injected migration failure")
            }
        }
        assertThrows(SQLiteException::class.java) {
            migrationHelper.runMigrationsAndValidate(name, 2, true, failingMigration)
        }

        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("PRAGMA user_version", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            db.rawQuery("SELECT name FROM tanks WHERE id='protected-tank'", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Protected tank", cursor.getString(0))
            }
            db.rawQuery("PRAGMA table_info(tanks)", null).use { cursor ->
                while (cursor.moveToNext()) assertNotEquals("injectedFailure", cursor.getString(1))
            }
        }
    }

    @Test fun versionTwoMigrationAddsTankFieldsWithoutReplacingExistingRows() {
        val name = "tank-migration-test"
        migrationHelper.createDatabase(name, 2).apply {
            execSQL("INSERT INTO tanks (id, name, type, photoId, capacityValue, capacityUnit, startedAt, status, notes, createdAt, updatedAt) VALUES ('tank-1', 'Old tank', 'freshwater', NULL, 20, 'L', NULL, 'active', NULL, 1, 1)")
            close()
        }
        val migrated = migrationHelper.runMigrationsAndValidate(name, 3, true, FishDatabase.MIGRATION_2_3)
        migrated.query("SELECT name, capacityValue, lengthCm, equipment FROM tanks WHERE id = 'tank-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Old tank", cursor.getString(0))
            assertEquals(20.0, cursor.getDouble(1), 0.0)
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
        }
        migrated.close()
    }

    @Test fun archiveKeepsFishAndHistoryButCancelsOpenTankTasks() = runBlocking {
        val tank = tank()
        repository.saveTank(tank)
        val fish = fish(tank.id)
        repository.saveFish(fish)
        val task = CareTask(tankId = tank.id, fishId = null, type = CareTaskType.water_test, plannedAt = now, recurrenceRule = null, notes = null, createdAt = now, updatedAt = now)
        repository.saveCareTask(task)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.archiveTank(tank.id, now, false) } }
        repository.archiveTank(tank.id, now, true)
        assertEquals(TankStatus.archived, db.tanks().get(tank.id)?.status)
        assertEquals(fish.id, db.fish().get(fish.id)?.id)
        assertEquals(CareTaskStatus.cancelled, db.care().getTask(task.id)?.status)
    }

    private fun tank(id: String = java.util.UUID.randomUUID().toString()) = Tank(id = id, name = "Tank", type = TankType.freshwater, photoId = null, capacityValue = null, capacityUnit = null, startedAt = null, status = TankStatus.active, notes = null, createdAt = now, updatedAt = now)
    private fun fish(tankId: String) = Fish(tankId = tankId, name = "Fish", species = null, coverPhotoId = null, addedAt = now, birthDate = null, status = FishStatus.active, statusChangedAt = null, notes = null, createdAt = now, updatedAt = now)
}
