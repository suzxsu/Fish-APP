package com.guanyji.app.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.StatFs
import androidx.sqlite.db.SupportSQLiteDatabase
import com.guanyji.app.data.local.CareNotificationScheduler
import com.guanyji.app.data.local.DatabaseFactory
import com.guanyji.app.data.local.FishDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupSummary(
    val createdAt: Long,
    val tanks: Int,
    val fish: Int,
    val growthRecords: Int,
    val feedingRecords: Int,
    val waterQualityRecords: Int,
    val waterChangeRecords: Int,
    val careRecords: Int,
    val photos: Int,
) {
    val records: Int get() = growthRecords + feedingRecords + waterQualityRecords + waterChangeRecords + careRecords
}

class LocalBackupManager(
    private val context: Context,
    private val databaseName: String = DATABASE_NAME,
    private val photoRoot: File = File(context.filesDir, "photos"),
) {
    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val database = if (databaseName == DATABASE_NAME) DatabaseFactory.create(context) else databaseForRestore()
        try {
            checkpoint(database)
        } finally {
            if (databaseName != DATABASE_NAME) database.close()
        }
        val dbFile = context.getDatabasePath(databaseName)
        check(dbFile.isFile) { "本地数据库尚未生成" }
        val (summary, photos) = readSnapshot(dbFile, photoRoot, System.currentTimeMillis())
        val inventory = linkedMapOf("db/$databaseName" to sha256(dbFile))
        photos.forEach { photo ->
            photo.paths.forEach { relative ->
                val file = safePhotoFile(photoRoot, relative)
                check(file.isFile) { "照片文件缺失，无法创建完整备份" }
                inventory[relative] = sha256(file)
            }
        }
        val manifest = JSONObject()
            .put("formatVersion", FORMAT_VERSION)
            .put("databaseVersion", FishDatabase.VERSION)
            .put("sqliteUserVersion", readUserVersion(dbFile))
            .put("createdAt", summary.createdAt)
            .put("summary", summary.toJson())
            .put("files", JSONObject().also { hashes -> inventory.forEach(hashes::put) })
            .put("photoReferences", JSONArray().also { refs -> photos.forEach { refs.put(it.toJson()) } })

        context.contentResolver.openOutputStream(uri, "w")?.use { output ->
            ZipOutputStream(output).use { zip ->
                zip.addFile("db/$databaseName", dbFile)
                photos.flatMap { it.paths }.distinct().forEach { relative -> zip.addFile(relative, safePhotoFile(photoRoot, relative)) }
                zip.putNextEntry(ZipEntry(MANIFEST_NAME))
                zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        } ?: error("无法写入所选文件")
        summary
    }

    suspend fun inspect(uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
        val (summary, staged) = extractAndValidate(uri)
        staged.deleteRecursively()
        summary
    }

    suspend fun restore(uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
        val (summary, staged) = extractAndValidate(uri)
        val liveDb = context.getDatabasePath(databaseName)
        val livePhotos = photoRoot
        val safety = recoveryDirectory(context, databaseName)
        try {
            safety.deleteRecursively()
            check(safety.mkdirs()) { "无法创建恢复安全副本" }
            val currentDatabase = if (databaseName == DATABASE_NAME) DatabaseFactory.create(context) else databaseForRestore()
            try {
                checkpoint(currentDatabase)
            } finally {
                if (databaseName != DATABASE_NAME) currentDatabase.close()
            }
            val livePhotoBytes = if (livePhotos.isDirectory) livePhotos.walkTopDown().filter(File::isFile).sumOf(File::length) else 0L
            val archiveBytes = staged.walkTopDown().filter(File::isFile).sumOf(File::length)
            val freeBytes = StatFs(context.filesDir.path).availableBytes
            check(hasEnoughStorage(freeBytes, liveDb.length() + livePhotoBytes + archiveBytes)) { "存储空间不足，无法安全创建恢复副本" }
            if (liveDb.isFile) liveDb.copyTo(File(safety, databaseName), overwrite = true)
            if (livePhotos.isDirectory) livePhotos.copyRecursively(File(safety, "photos"), overwrite = true)
            File(safety, RECOVERY_READY).writeText("ready", Charsets.UTF_8)

            DatabaseFactory.closeForRestore()
            deleteDatabaseSidecars(liveDb)
            replaceDirectory(File(staged, "photos"), livePhotos)
            replaceFile(File(staged, "db/$databaseName"), liveDb)
            val restored = if (databaseName == DATABASE_NAME) DatabaseFactory.create(context) else databaseForRestore()
            try {
                validateLivePhotoReferences(restored.openHelper.writableDatabase, livePhotos)
            } finally {
                if (databaseName != DATABASE_NAME) restored.close()
            }
            if (databaseName == DATABASE_NAME) CareNotificationScheduler.sync(context)
            File(livePhotos.parentFile, "${livePhotos.name}.previous").deleteRecursively()
            safety.deleteRecursively()
            summary
        } catch (failure: Throwable) {
            DatabaseFactory.closeForRestore()
            if (File(safety, RECOVERY_READY).isFile) restoreSafetyCopy(safety, liveDb, livePhotos, databaseName)
            if (databaseName == DATABASE_NAME) runCatching { DatabaseFactory.create(context) }
            throw failure
        } finally {
            staged.deleteRecursively()
        }
    }

    companion object {
        const val DATABASE_NAME = "guanyji.db"
        const val FORMAT_VERSION = 1
        private const val MANIFEST_NAME = "manifest.json"
        private const val RECOVERY_DIRECTORY = "restore-recovery"
        private const val RECOVERY_READY = "ready"
        private const val MAX_MANIFEST_BYTES = 1_000_000
        private const val MAX_BACKUP_BYTES = 2L * 1024 * 1024 * 1024
        private val REQUIRED_TABLES = setOf(
            "photos", "tanks", "fish", "fish_events", "growth_records", "feeding_events", "feeding_targets",
            "common_foods", "water_tests", "water_test_values", "water_changes", "care_tasks", "care_rules",
            "care_records", "app_settings",
        )

        fun recoverInterruptedRestore(context: Context, databaseName: String = DATABASE_NAME, photoRoot: File = File(context.filesDir, "photos")) {
            val safety = recoveryDirectory(context, databaseName)
            if (!needsInterruptedRestoreRecovery(File(safety, RECOVERY_READY).isFile)) return
            restoreSafetyCopy(safety, context.getDatabasePath(databaseName), photoRoot, databaseName)
        }

        private fun recoveryDirectory(context: Context, databaseName: String = DATABASE_NAME) = File(context.filesDir, "$RECOVERY_DIRECTORY-${databaseName.hashCode()}")

        private fun restoreSafetyCopy(safety: File, liveDb: File, livePhotos: File, databaseName: String) {
            val safeDb = File(safety, databaseName)
            deleteDatabaseSidecars(liveDb)
            if (safeDb.isFile) replaceFile(safeDb, liveDb) else liveDb.delete()
            val safePhotos = File(safety, "photos")
            val previous = File(livePhotos.parentFile, "${livePhotos.name}.previous")
            livePhotos.deleteRecursively()
            if (safePhotos.isDirectory) {
                if (!safePhotos.renameTo(livePhotos)) safePhotos.copyRecursively(livePhotos, overwrite = true)
            } else if (previous.isDirectory) {
                previous.renameTo(livePhotos)
            }
            previous.deleteRecursively()
            safety.deleteRecursively()
        }

        private fun replaceFile(source: File, target: File) {
            target.parentFile?.let { if (!it.isDirectory && !it.mkdirs()) throw IOException("无法准备数据库目录") }
            val swap = File(target.parentFile, "${target.name}.restore")
            source.copyTo(swap, overwrite = true)
            if (!swap.renameTo(target)) {
                target.delete()
                if (!swap.renameTo(target)) throw IOException("无法安装恢复数据库")
            }
        }

        private fun replaceDirectory(source: File, target: File) {
            val swap = File(target.parentFile, "${target.name}.restore")
            swap.deleteRecursively()
            if (source.isDirectory) source.copyRecursively(swap, overwrite = true)
            val previous = File(target.parentFile, "${target.name}.previous")
            previous.deleteRecursively()
            if (target.exists() && !target.renameTo(previous)) throw IOException("无法暂存现有照片")
            if (swap.exists() && !swap.renameTo(target)) {
                if (previous.exists()) previous.renameTo(target)
                throw IOException("无法安装备份照片")
            }
        }

        private fun deleteDatabaseSidecars(db: File) {
            File(db.path + "-wal").delete()
            File(db.path + "-shm").delete()
            File(db.path + "-journal").delete()
        }

        private fun resolvePhotoFile(root: File, relative: String): File {
            require(safeArchivePath(relative) && relative.startsWith("photos/")) { "照片路径无效" }
            val canonicalRoot = root.canonicalFile
            val file = File(canonicalRoot, relative.removePrefix("photos/")).canonicalFile
            require(file.toPath().startsWith(canonicalRoot.toPath())) { "照片路径超出备份目录" }
            return file
        }

        private fun safeArchivePath(name: String): Boolean = name.isNotBlank() && !name.startsWith('/') && !name.contains('\\') &&
            name.split('/').none { it.isBlank() || it == "." || it == ".." } && !File(name).isAbsolute

        private fun validateLivePhotoReferences(database: SupportSQLiteDatabase, photoRoot: File) {
            val rows = database.query("SELECT localPath, thumbnailPath FROM photos")
            rows.use {
                while (it.moveToNext()) {
                    for (index in 0..1) {
                        if (index == 1 && it.isNull(index)) continue
                        val relative = it.getString(index)
                        check(resolvePhotoFile(photoRoot, relative).isFile) { "恢复后发现照片引用缺失" }
                    }
                }
            }
        }
    }

    private data class PhotoReference(val id: String, val paths: List<String>, val mimeType: String) {
        fun toJson() = JSONObject().put("id", id).put("files", JSONArray(paths)).put("mimeType", mimeType)
    }

    private fun databaseForRestore(): FishDatabase = androidx.room.Room.databaseBuilder(
        context.applicationContext,
        FishDatabase::class.java,
        databaseName,
    ).addMigrations(
        FishDatabase.MIGRATION_1_2, FishDatabase.MIGRATION_2_3, FishDatabase.MIGRATION_3_4,
        FishDatabase.MIGRATION_4_5, FishDatabase.MIGRATION_5_6, FishDatabase.MIGRATION_6_7, FishDatabase.MIGRATION_7_8,
    ).build()

    private fun extractAndValidate(uri: Uri): Pair<BackupSummary, File> {
        val staged = File(context.cacheDir, "restore-stage-${System.nanoTime()}").apply { check(mkdirs()) }
        try {
            var manifest: JSONObject? = null
            val extracted = linkedMapOf<String, String>()
            var totalBytes = 0L
            val seenNames = mutableSetOf<String>()
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = entry.name
                        require(!entry.isDirectory && safeArchiveName(name) && seenNames.add(name)) { "備份包含重复或不支持的文件" }
                        if (name == MANIFEST_NAME) {
                            val bytes = ByteArrayOutputStream()
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                require(bytes.size() + count <= MAX_MANIFEST_BYTES) { "备份清单超过允许大小" }
                                bytes.write(buffer, 0, count)
                            }
                            manifest = JSONObject(bytes.toString(Charsets.UTF_8.name()))
                        } else {
                            require(name == "db/$databaseName" || name.startsWith("photos/")) { "备份包含不支持的文件" }
                            val target = File(staged, name)
                            target.parentFile?.let { require(it.isDirectory || it.mkdirs()) { "无法准备临时恢复目录" } }
                            target.outputStream().use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    val count = zip.read(buffer)
                                    if (count < 0) break
                                    totalBytes += count
                                    require(totalBytes <= MAX_BACKUP_BYTES) { "备份文件超过允许大小" }
                                    output.write(buffer, 0, count)
                                }
                            }
                            extracted[name] = sha256(target)
                        }
                        zip.closeEntry()
                    }
                }
            } ?: error("无法读取所选备份")

            val info = requireNotNull(manifest) { "备份缺少版本信息" }
            val expected = info.optJSONObject("files") ?: error("备份缺少校验清单")
            val expectedHashes = expected.keys().asSequence().associateWith(expected::getString)
            val archiveHashes = extracted + ("db/$databaseName" to stagedDbHash(staged))
            val validation = validateBackupEnvelope(
                formatVersion = info.optInt("formatVersion", -1),
                supportedFormatVersion = FORMAT_VERSION,
                databaseVersion = info.optInt("databaseVersion", -1),
                supportedDatabaseVersion = FishDatabase.VERSION,
                expectedHashes = expectedHashes,
                actualHashes = archiveHashes,
                requiredFiles = setOf("db/$databaseName"),
                photoReferences = parsePhotoReferences(info.optJSONArray("photoReferences")).flatMap { it.paths }.toSet(),
            )
            require(validation == BackupValidationResult.Valid) {
                when ((validation as? BackupValidationResult.Invalid)?.reason) {
                    "future-version" -> "备份来自更高版本的观鱼记或数据库，当前版本不支持恢复"
                    "incompatible-version" -> "备份格式或数据库版本不兼容"
                    "missing-required-file" -> "备份缺少必需文件"
                    "missing-photo" -> "备份缺少照片文件"
                    "checksum-mismatch" -> "备份文件校验失败"
                    else -> "备份文件清单不匹配"
                }
            }
            val db = File(staged, "db/$databaseName")
            require(info.optInt("sqliteUserVersion", -1) == FishDatabase.VERSION) { "备份数据库版本不兼容" }
            validateDatabase(db)
            val photoReferences = parsePhotoReferences(info.optJSONArray("photoReferences"))
            validateStagedPhotos(photoReferences, staged)
            val summary = info.getJSONObject("summary").let { data ->
                BackupSummary(
                    createdAt = info.getLong("createdAt"),
                    tanks = data.getInt("tanks"), fish = data.getInt("fish"), growthRecords = data.getInt("growthRecords"),
                    feedingRecords = data.getInt("feedingRecords"), waterQualityRecords = data.getInt("waterQualityRecords"),
                    waterChangeRecords = data.getInt("waterChangeRecords"), careRecords = data.getInt("careRecords"), photos = data.getInt("photos"),
                )
            }
            val actual = readSnapshot(db, File(staged, "photos"), summary.createdAt).first
            require(actual.tanks == summary.tanks && actual.fish == summary.fish && actual.growthRecords == summary.growthRecords &&
                actual.feedingRecords == summary.feedingRecords && actual.waterQualityRecords == summary.waterQualityRecords &&
                actual.waterChangeRecords == summary.waterChangeRecords && actual.careRecords == summary.careRecords && actual.photos == summary.photos) {
                "备份摘要与实际数据不一致"
            }
            return summary to staged
        } catch (failure: Throwable) {
            staged.deleteRecursively()
            throw failure
        }
    }

    private fun validateDatabase(file: File) {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { sql ->
            sql.rawQuery("PRAGMA integrity_check", null).use { require(it.moveToFirst() && it.getString(0) == "ok") { "备份数据库完整性校验失败" } }
            sql.rawQuery("PRAGMA foreign_key_check", null).use { require(!it.moveToFirst()) { "备份数据引用校验失败" } }
            val tables = mutableSetOf<String>()
            sql.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { while (it.moveToNext()) tables += it.getString(0) }
            require(tables.containsAll(REQUIRED_TABLES)) { "备份缺少必需数据表" }
        }
    }

    private fun validateStagedPhotos(references: List<PhotoReference>, staged: File) {
        val paths = mutableSetOf<String>()
        references.forEach { ref ->
            require(ref.id.isNotBlank() && ref.paths.isNotEmpty() && ref.paths.all(paths::add)) { "照片引用清单无效" }
            ref.paths.forEach { path -> require(safeArchiveName(path) && path.startsWith("photos/")) { "照片引用路径无效" } }
        }
        val db = SQLiteDatabase.openDatabase(File(staged, "db/$databaseName").path, null, SQLiteDatabase.OPEN_READONLY)
        db.use { sql ->
            val rows = sql.rawQuery("SELECT id, localPath, thumbnailPath, mimeType FROM photos", null)
            rows.use {
                val seen = mutableSetOf<String>()
                while (it.moveToNext()) {
                    val id = it.getString(0)
                    val originals = listOf(it.getString(1)) + if (it.isNull(2)) emptyList() else listOf(it.getString(2))
                    val ref = references.singleOrNull { candidate -> candidate.id == id } ?: error("备份缺少照片引用记录")
                    require(ref.paths == originals && ref.mimeType == it.getString(3)) { "照片引用与数据库不匹配" }
                    originals.forEach { path ->
                        require(seen.add(path)) { "照片路径重复" }
                        require(File(staged, path).canonicalFile.isFile) { "备份缺少照片文件" }
                    }
                }
                require(seen.size == paths.size && references.size == seenPhotoCount(sql)) { "备份包含无效照片引用" }
            }
        }
    }

    private fun seenPhotoCount(database: SQLiteDatabase): Int = database.rawQuery("SELECT COUNT(*) FROM photos", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun readSnapshot(dbFile: File, photoRoot: File, createdAt: Long): Pair<BackupSummary, List<PhotoReference>> {
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { sql ->
            val photos = mutableListOf<PhotoReference>()
            sql.rawQuery("SELECT id, localPath, thumbnailPath, mimeType FROM photos ORDER BY id", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val paths = listOf(cursor.getString(1)) + if (cursor.isNull(2)) emptyList() else listOf(cursor.getString(2))
                    paths.forEach { path -> require(safePhotoFile(photoRoot, path).isFile) { "照片文件缺失，无法创建完整备份" } }
                    photos += PhotoReference(cursor.getString(0), paths, cursor.getString(3))
                }
            }
            val summary = BackupSummary(
                createdAt = createdAt,
                tanks = sql.count("tanks"), fish = sql.count("fish"), growthRecords = sql.count("growth_records"),
                feedingRecords = sql.count("feeding_events"), waterQualityRecords = sql.count("water_tests"),
                waterChangeRecords = sql.count("water_changes"), careRecords = sql.count("care_records"), photos = photos.size,
            )
            return summary to photos
        }
    }

    private fun checkpoint(database: FishDatabase) {
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
            check(cursor.moveToFirst() && cursor.getInt(0) == 0) { "数据库正在写入，请稍后重试备份" }
        }
    }

    private fun SQLiteDatabase.count(table: String): Int {
        require(table in REQUIRED_TABLES)
        return rawQuery("SELECT COUNT(*) FROM $table", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    private fun ZipOutputStream.addFile(name: String, file: File) {
        putNextEntry(ZipEntry(name)); file.inputStream().buffered().use { it.copyTo(this) }; closeEntry()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun stagedDbHash(staged: File): String {
        val db = File(staged, "db/$databaseName")
        require(db.isFile) { "备份数据库文件缺失" }
        return sha256(db)
    }

    private fun readUserVersion(dbFile: File): Int = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery("PRAGMA user_version", null).use { if (it.moveToFirst()) it.getInt(0) else -1 }
    }

    private fun safeArchiveName(name: String): Boolean = name.isNotBlank() && !name.startsWith('/') && !name.contains('\\') &&
        name.split('/').none { it.isBlank() || it == "." || it == ".." } && !File(name).isAbsolute

    private fun safePhotoFile(root: File, relative: String): File {
        return resolvePhotoFile(root, relative)
    }

    private fun BackupSummary.toJson() = JSONObject()
        .put("tanks", tanks).put("fish", fish).put("growthRecords", growthRecords)
        .put("feedingRecords", feedingRecords).put("waterQualityRecords", waterQualityRecords)
        .put("waterChangeRecords", waterChangeRecords).put("careRecords", careRecords).put("photos", photos)

    private fun parsePhotoReferences(array: JSONArray?): List<PhotoReference> {
        requireNotNull(array) { "备份缺少照片引用清单" }
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val files = item.getJSONArray("files")
            PhotoReference(item.getString("id"), (0 until files.length()).map(files::getString), item.getString("mimeType"))
        }
    }
}
