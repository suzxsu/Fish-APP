package com.guanyji.app.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.guanyji.app.data.local.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

@RunWith(AndroidJUnit4::class)
class LocalBackupManagerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: FishDatabase
    private lateinit var manager: LocalBackupManager
    private lateinit var work: File
    private lateinit var backupFile: File
    private lateinit var isolatedDatabaseName: String
    private lateinit var photoRoot: File
    private lateinit var tank: Tank
    private lateinit var fish: Fish

    @Before fun setUp() = runBlocking {
        work = File(context.cacheDir, "backup-test-${System.nanoTime()}").apply { mkdirs() }
        backupFile = File(work, "backup.zip")
        isolatedDatabaseName = "backup-test-${System.nanoTime()}.db"
        photoRoot = File(work, "photos")
        database = Room.databaseBuilder(context, FishDatabase::class.java, isolatedDatabaseName)
            .addMigrations(FishDatabase.MIGRATION_1_2, FishDatabase.MIGRATION_2_3, FishDatabase.MIGRATION_3_4, FishDatabase.MIGRATION_4_5, FishDatabase.MIGRATION_5_6, FishDatabase.MIGRATION_6_7, FishDatabase.MIGRATION_7_8)
            .build()
        manager = LocalBackupManager(context, isolatedDatabaseName, photoRoot)
        tank = Tank(id = "tank-backup", name = "测试缸", type = TankType.freshwater, photoId = null, capacityValue = null, capacityUnit = null, startedAt = null, notes = null, createdAt = 1, updatedAt = 1)
        fish = Fish(id = "fish-backup", tankId = tank.id, name = "测试鱼", species = null, coverPhotoId = null, addedAt = 1, birthDate = null, status = FishStatus.active, statusChangedAt = null, notes = null, createdAt = 1, updatedAt = 1)
        database.withTransaction {
            database.tanks().insert(tank)
            database.fish().insert(fish)
            database.care().insertRecord(CareRecord(id = "care-backup", taskId = null, tankId = tank.id, fishId = null, type = CareRecordType.custom, completedAt = 1, payloadJson = null, notes = null))
        }
    }

    @After fun tearDown() {
        runCatching { database.close() }
        context.deleteDatabase(isolatedDatabaseName)
        work.deleteRecursively()
    }

    @Test fun exportSummaryAndRestorePreserveLocalRecords() = runBlocking {
        manager.export(Uri.fromFile(backupFile))
        val summary = manager.inspect(Uri.fromFile(backupFile))
        assertEquals(1, summary.tanks)
        assertEquals(1, summary.fish)
        assertEquals(1, summary.careRecords)
        assertTrue(zipNames().containsAll(setOf("manifest.json", "db/$isolatedDatabaseName")))

        database.fish().delete(fish.id)
        database.tanks().delete(tank.id)
        restoreWithClosedTestConnection()
        assertEquals(tank.id, database.tanks().get(tank.id)?.id)
        assertEquals(fish.id, database.fish().get(fish.id)?.id)
        assertEquals(1, database.care().observeAllRecords().first().size)
    }

    @Test fun restoreCopiesPhotoFilesAndKeepsDatabasePhotoReferencesResolvable() = runBlocking {
        val photo = Photo(id = "photo-roundtrip", localPath = "photos/original/fish.jpg", thumbnailPath = "photos/thumb/fish.jpg", mimeType = "image/jpeg", width = 20, height = 10, createdAt = 1)
        database.photos().insert(photo)
        database.tanks().update(tank.copy(photoId = photo.id))
        database.fish().update(fish.copy(coverPhotoId = photo.id))
        assertEquals(photo.id, database.tanks().get(tank.id)?.photoId)
        assertEquals(photo.id, database.fish().get(fish.id)?.coverPhotoId)
        File(photoRoot, "original/fish.jpg").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1, 2, 3, 4)) }
        File(photoRoot, "thumb/fish.jpg").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(5, 6)) }
        manager.export(Uri.fromFile(backupFile))
        assertEquals(1, manager.inspect(Uri.fromFile(backupFile)).photos)
        assertEquals(photo.id, archivedPhotoId("tanks", "photoId", tank.id))

        database.tanks().update(tank.copy(photoId = null))
        database.fish().update(fish.copy(coverPhotoId = null))
        database.photos().delete(photo.id)
        photoRoot.deleteRecursively()
        restoreWithClosedTestConnection()

        assertEquals(photo.id, photoIdFromLiveDatabase("tanks", tank.id))
        assertEquals(photo.id, database.tanks().get(tank.id)?.photoId)
        assertEquals(photo.id, database.fish().get(fish.id)?.coverPhotoId)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), File(photoRoot, "original/fish.jpg").readBytes())
        assertArrayEquals(byteArrayOf(5, 6), File(photoRoot, "thumb/fish.jpg").readBytes())
    }

    @Test fun missingPhotoAndCorruptChecksumAreRejectedBeforeChangingCurrentData() = runBlocking {
        val photo = Photo(id = "photo-backup", localPath = "photos/original/hash", thumbnailPath = null, mimeType = "image/jpeg", width = 1, height = 1, createdAt = 1)
        database.photos().insert(photo)
        database.tanks().update(tank.copy(photoId = photo.id))
        val photoFile = File(photoRoot, "original/hash").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        manager.export(Uri.fromFile(backupFile))

        val brokenPhoto = File(work, "missing-photo.zip")
        rewriteZip(backupFile, brokenPhoto) { name, bytes -> if (name == "photos/original/hash") null else bytes }
        assertFailsWithMessage("备份缺少照片文件") { manager.inspect(Uri.fromFile(brokenPhoto)) }
        assertEquals(tank.id, database.tanks().get(tank.id)?.id)

        val corrupt = File(work, "corrupt.zip")
        rewriteZip(backupFile, corrupt) { name, bytes -> if (name == "photos/original/hash") bytes + byteArrayOf(9) else bytes }
        assertFailsWithMessage("校验失败") { manager.inspect(Uri.fromFile(corrupt)) }
        assertFailsWithMessage("校验失败") { manager.restore(Uri.fromFile(corrupt)) }
        assertEquals(tank.id, database.tanks().get(tank.id)?.id)
        assertTrue(photoFile.isFile)
    }

    @Test fun futureVersionAndDuplicateEntriesAreRejected() = runBlocking {
        manager.export(Uri.fromFile(backupFile))
        val future = File(work, "future.zip")
        rewriteZip(backupFile, future) { name, bytes -> if (name == "manifest.json") {
            JSONObject(bytes.toString(Charsets.UTF_8)).put("formatVersion", LocalBackupManager.FORMAT_VERSION + 1).toString().toByteArray(Charsets.UTF_8)
        } else bytes }
        assertFailsWithMessage("更高版本") { manager.inspect(Uri.fromFile(future)) }
        val first = manager.restore(Uri.fromFile(backupFile))
        val second = manager.restore(Uri.fromFile(backupFile))
        assertEquals(first, second)
        assertEquals(1, database.care().observeAllRecords().first().size)
    }

    private fun zipNames(): Set<String> = ZipInputStream(backupFile.inputStream()).use { zip ->
        buildSet { while (true) add(zip.nextEntry?.name ?: break) }
    }

    private suspend fun restoreWithClosedTestConnection() {
        database.close()
        manager.restore(Uri.fromFile(backupFile))
        database = Room.databaseBuilder(context, FishDatabase::class.java, isolatedDatabaseName)
            .addMigrations(FishDatabase.MIGRATION_1_2, FishDatabase.MIGRATION_2_3, FishDatabase.MIGRATION_3_4, FishDatabase.MIGRATION_4_5, FishDatabase.MIGRATION_5_6, FishDatabase.MIGRATION_6_7, FishDatabase.MIGRATION_7_8)
            .build()
    }

    private fun archivedPhotoId(table: String, column: String, id: String): String? {
        val bytes = ZipInputStream(backupFile.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: error("Database entry missing")
                if (entry.name == "db/$isolatedDatabaseName") return@use zip.readBytes()
            }
            error("Database entry missing")
        }
        val dbFile = File(work, "archive.db").apply { writeBytes(bytes) }
        return SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT $column FROM $table WHERE id=?", arrayOf(id)).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }
    }

    private fun photoIdFromLiveDatabase(table: String, id: String): String? = SQLiteDatabase.openDatabase(
        context.getDatabasePath(isolatedDatabaseName).path, null, SQLiteDatabase.OPEN_READONLY,
    ).use { db -> db.rawQuery("SELECT photoId FROM $table WHERE id=?", arrayOf(id)).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } }

    private fun rewriteZip(source: File, target: File, transform: (String, ByteArray) -> ByteArray?) {
        ZipInputStream(source.inputStream()).use { input ->
            ZipOutputStream(target.outputStream()).use { output ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val bytes = input.readBytes()
                    val transformed = transform(entry.name, bytes) ?: continue
                    output.putNextEntry(ZipEntry(entry.name)); output.write(transformed); output.closeEntry()
                }
            }
        }
    }

    private suspend fun assertFailsWithMessage(text: String, action: suspend () -> Unit) {
        val failure = runCatching { action() }.exceptionOrNull()
        assertNotNull("Expected failure containing $text", failure)
        assertTrue(failure.toString(), failure?.message.orEmpty().contains(text))
    }
}
