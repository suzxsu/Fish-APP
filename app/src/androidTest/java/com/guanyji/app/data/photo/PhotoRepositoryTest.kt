package com.guanyji.app.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.guanyji.app.data.local.FishDatabase
import com.guanyji.app.data.local.Fish
import com.guanyji.app.data.local.FishStatus
import com.guanyji.app.data.local.GrowthRecord
import com.guanyji.app.data.local.Photo
import com.guanyji.app.data.local.Tank
import com.guanyji.app.data.local.TankStatus
import com.guanyji.app.data.local.TankType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class PhotoRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: FishDatabase
    private lateinit var repository: PhotoRepository
    private val source = File(context.cacheDir, "photo-repository-test.png")
    private val imported = mutableListOf<Photo>()

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, FishDatabase::class.java).build()
        repository = PhotoRepository(database, ManagedPhotoFileStore(context))
        Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xff000000.toInt() or (System.nanoTime().toInt() and 0x00ffffff))
            FileOutputStream(source).use { compress(Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }
    }

    @After fun tearDown() {
        database.close()
        val files = ManagedPhotoFileStore(context)
        imported.distinctBy { it.id }.forEach(files::delete)
        source.delete()
    }

    @Test fun importCopiesPrivateFilesAndDeduplicatesByContent() = runBlocking {
        val uri = Uri.fromFile(source)
        val first = repository.import(uri)
        val second = repository.import(uri)
        imported += first
        assertEquals(first.id, second.id)
        assertEquals(first.localPath, second.localPath)
        assertTrue(repository.fileFor(first, thumbnail = false)?.isFile == true)
        assertTrue(repository.fileFor(first, thumbnail = true)?.isFile == true)
        assertFalse(first.localPath.contains(uri.toString()))
        assertEquals(1, database.photos().countByPath(first.localPath))
        assertEquals(0, database.photos().countReferences(first.id).total)
        assertTrue(repository.deleteIfUnreferenced(first.id) is PhotoDeleteResult.Deleted)
        assertNull(database.photos().get(first.id))
    }

    @Test fun referencedPhotoCannotBeRemovedAndMissingFilesReturnNoDisplayFile() = runBlocking {
        val photo = repository.import(Uri.fromFile(source))
        imported += photo
        val tank = Tank(
            name = "My tank", type = TankType.freshwater, photoId = photo.id,
            capacityValue = null, capacityUnit = null, startedAt = null, status = TankStatus.active,
            notes = null, createdAt = 1L, updatedAt = 1L,
        )
        database.tanks().insert(tank)
        val fish = Fish(
            tankId = tank.id, name = "Fish", species = null, coverPhotoId = photo.id,
            addedAt = 1L, birthDate = null, status = FishStatus.active, statusChangedAt = null,
            notes = null, createdAt = 1L, updatedAt = 1L,
        )
        database.fish().insert(fish)
        database.growthRecords().insert(
            GrowthRecord(fishId = fish.id, measuredAt = 1L, lengthValue = null, lengthUnit = null, weightValue = null, weightUnit = null, method = null, photoId = photo.id, notes = null),
        )
        val references = database.photos().countReferences(photo.id)
        assertEquals(1, references.tankCovers)
        assertEquals(1, references.fishCovers)
        assertEquals(1, references.growthRecords)
        assertTrue(repository.deleteIfUnreferenced(photo.id) is PhotoDeleteResult.InUse)
        File(context.filesDir, photo.localPath).delete()
        assertNull(repository.fileFor(photo, thumbnail = false))
        assertNotNull(database.photos().get(photo.id))
    }
}
