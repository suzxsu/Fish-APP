package com.guanyji.app.data.photo

import android.net.Uri
import androidx.room.withTransaction
import com.guanyji.app.data.local.FishDatabase
import com.guanyji.app.data.local.Photo
import com.guanyji.app.data.local.PhotoReferenceCounts
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed interface PhotoDeleteResult {
    data object Missing : PhotoDeleteResult
    data class InUse(val references: PhotoReferenceCounts) : PhotoDeleteResult
    data class Deleted(val filesRemoved: Boolean) : PhotoDeleteResult
}

class PhotoRepository(
    private val database: FishDatabase,
    private val fileStore: PhotoFileStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun import(uri: Uri): Photo = withContext(ioDispatcher) {
        val staged = fileStore.stageImport(uri)
        val candidate = Photo(
            localPath = staged.localPath,
            thumbnailPath = staged.thumbnailPath,
            mimeType = staged.mimeType,
            width = staged.width,
            height = staged.height,
            createdAt = System.currentTimeMillis(),
        )
        var publishedNewFiles = false
        try {
            database.withTransaction {
                val existing = database.photos().getByPath(staged.localPath)
                if (existing != null) {
                    fileStore.publish(staged)
                    existing
                } else {
                    publishedNewFiles = true
                    fileStore.publish(staged)
                    database.photos().insert(candidate)
                    candidate
                }
            }
        } catch (error: Throwable) {
            if (publishedNewFiles) runCatching { fileStore.delete(candidate) }
            throw error
        } finally {
            fileStore.discard(staged)
        }
    }

    suspend fun deleteIfUnreferenced(photoId: String): PhotoDeleteResult = withContext(ioDispatcher) {
        val photo = database.withTransaction {
            val current = database.photos().get(photoId) ?: return@withTransaction null
            val references = database.photos().countReferences(photoId)
            if (references.total > 0) return@withTransaction references
            database.photos().delete(photoId)
            current
        }
        when (photo) {
            null -> PhotoDeleteResult.Missing
            is PhotoReferenceCounts -> PhotoDeleteResult.InUse(photo)
            is Photo -> {
                val removed = runCatching { fileStore.delete(photo); true }.getOrDefault(false)
                PhotoDeleteResult.Deleted(removed)
            }
            else -> error("Unexpected photo deletion result")
        }
    }

    fun cameraCaptureUri(): Uri = fileStore.createCameraCaptureUri()

    suspend fun completeCameraCapture(uri: Uri, captured: Boolean): Photo? = withContext(ioDispatcher) {
        try {
            if (captured) import(uri) else null
        } finally {
            fileStore.removeCameraCapture(uri)
        }
    }

    suspend fun get(photoId: String): Photo? = withContext(ioDispatcher) { database.photos().get(photoId) }

    fun fileFor(photo: Photo, thumbnail: Boolean): File? = fileStore.fileFor(photo, thumbnail)
}
