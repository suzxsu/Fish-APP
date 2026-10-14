package com.guanyji.app.data.photo

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.StatFs
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.guanyji.app.data.local.Photo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

data class StagedPhoto(
    val originalTemp: File,
    val thumbnailTemp: File,
    val localPath: String,
    val thumbnailPath: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
)

enum class PhotoStorageFailure { unreadable, invalidImage, noSpace, accessDenied }

class PhotoStorageException(val failure: PhotoStorageFailure, cause: Throwable? = null) : IOException(failure.name, cause)

interface PhotoFileStore {
    fun stageImport(uri: Uri): StagedPhoto
    fun publish(staged: StagedPhoto)
    fun discard(staged: StagedPhoto)
    fun createCameraCaptureUri(): Uri
    fun removeCameraCapture(uri: Uri)
    fun fileFor(photo: Photo, thumbnail: Boolean): File?
    fun delete(photo: Photo)
}

class ManagedPhotoFileStore(context: Context) : PhotoFileStore {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val photosRoot = File(appContext.filesDir, "photos")
    private val pendingRoot = File(photosRoot, ".pending")
    private val captureRoot = File(appContext.cacheDir, "photo-capture")
    private val authority = "${appContext.packageName}.fileprovider"

    override fun stageImport(uri: Uri): StagedPhoto {
        val originalTemp = File(pendingRoot, "${UUID.randomUUID()}.source")
        var thumbnailTemp: File? = null
        try {
            ensureDirectory(pendingRoot)
            val digest = MessageDigest.getInstance("SHA-256")
            val input = try {
                resolver.openInputStream(uri) ?: throw PhotoStorageException(PhotoStorageFailure.unreadable)
            } catch (error: SecurityException) {
                throw PhotoStorageException(PhotoStorageFailure.accessDenied, error)
            }
            input.use { source ->
                FileOutputStream(originalTemp).use { target ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        target.write(buffer, 0, count)
                    }
                }
            }
            if (originalTemp.length() == 0L) throw PhotoStorageException(PhotoStorageFailure.invalidImage)
            val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val dimensions = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
            BitmapFactory.decodeFile(originalTemp.absolutePath, dimensions)
            if (dimensions.outWidth <= 0 || dimensions.outHeight <= 0) throw PhotoStorageException(PhotoStorageFailure.invalidImage)
            val extensionMime = MimeTypeMap.getFileExtensionFromUrl(uri.lastPathSegment.orEmpty())
                .takeIf { it.isNotBlank() }
                ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
            val mimeType = resolver.getType(uri)?.takeIf { it.startsWith("image/") }
                ?: dimensions.outMimeType?.takeIf { it.startsWith("image/") }
                ?: extensionMime?.takeIf { it.startsWith("image/") }
                ?: throw PhotoStorageException(PhotoStorageFailure.invalidImage)

            thumbnailTemp = File(pendingRoot, "${UUID.randomUUID()}.thumb")
            createThumbnail(originalTemp, thumbnailTemp, dimensions.outWidth, dimensions.outHeight)
            return StagedPhoto(
                originalTemp = originalTemp,
                thumbnailTemp = thumbnailTemp,
                localPath = "photos/original/$hash",
                thumbnailPath = "photos/thumbnails/$hash.webp",
                mimeType = mimeType,
                width = dimensions.outWidth,
                height = dimensions.outHeight,
            )
        } catch (error: PhotoStorageException) {
            originalTemp.delete()
            thumbnailTemp?.delete()
            throw error
        } catch (error: SecurityException) {
            originalTemp.delete()
            thumbnailTemp?.delete()
            throw PhotoStorageException(PhotoStorageFailure.accessDenied, error)
        } catch (error: IOException) {
            originalTemp.delete()
            thumbnailTemp?.delete()
            if (isOutOfSpace(error)) throw PhotoStorageException(PhotoStorageFailure.noSpace, error)
            throw PhotoStorageException(PhotoStorageFailure.unreadable, error)
        } catch (error: RuntimeException) {
            originalTemp.delete()
            thumbnailTemp?.delete()
            throw PhotoStorageException(PhotoStorageFailure.invalidImage, error)
        }
    }

    override fun publish(staged: StagedPhoto) {
        publishFile(staged.originalTemp, resolve(staged.localPath))
        publishFile(staged.thumbnailTemp, resolve(staged.thumbnailPath))
    }

    override fun discard(staged: StagedPhoto) {
        staged.originalTemp.delete()
        staged.thumbnailTemp.delete()
    }

    override fun createCameraCaptureUri(): Uri {
        ensureDirectory(captureRoot)
        val file = File(captureRoot, "${UUID.randomUUID()}.jpg")
        if (!file.createNewFile()) throw IOException("Could not prepare camera capture file")
        return FileProvider.getUriForFile(appContext, authority, file)
    }

    override fun removeCameraCapture(uri: Uri) {
        captureFileFrom(uri)?.delete()
    }

    override fun fileFor(photo: Photo, thumbnail: Boolean): File? {
        val path = if (thumbnail) photo.thumbnailPath ?: photo.localPath else photo.localPath
        return runCatching { resolve(path).takeIf(File::isFile) }.getOrNull()
    }

    override fun delete(photo: Photo) {
        resolve(photo.localPath).delete()
        photo.thumbnailPath?.let { resolve(it).delete() }
    }

    private fun createThumbnail(source: File, target: File, width: Int, height: Int) {
        var sampleSize = 1
        while (maxOf(width, height) / sampleSize > THUMBNAIL_SIZE * 2) sampleSize *= 2
        val bitmap = BitmapFactory.decodeFile(source.absolutePath, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            ?: throw PhotoStorageException(PhotoStorageFailure.invalidImage)
        val rotated = try {
            val degrees = ExifInterface(source).rotationDegrees
            if (degrees == 0) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true).also { bitmap.recycle() }
        } catch (error: IOException) {
            bitmap.recycle()
            throw PhotoStorageException(PhotoStorageFailure.invalidImage, error)
        }
        val scale = minOf(1f, THUMBNAIL_SIZE.toFloat() / maxOf(rotated.width, rotated.height))
        val thumbnail = if (scale < 1f) Bitmap.createScaledBitmap(rotated, (rotated.width * scale).toInt().coerceAtLeast(1), (rotated.height * scale).toInt().coerceAtLeast(1), true) else rotated
        try {
            FileOutputStream(target).use { output ->
                if (!thumbnail.compress(Bitmap.CompressFormat.WEBP, 82, output)) throw IOException("Could not encode thumbnail")
            }
        } finally {
            thumbnail.recycle()
            if (thumbnail !== rotated) rotated.recycle()
        }
    }

    private fun publishFile(source: File, target: File) {
        ensureDirectory(target.parentFile ?: photosRoot)
        if (target.isFile) {
            source.delete()
        } else if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = false)
            source.delete()
        }
    }

    private fun resolve(relativePath: String): File {
        require(!File(relativePath).isAbsolute) { "Photo path must be relative" }
        val root = photosRoot.canonicalFile
        val file = File(root, relativePath.removePrefix("photos/")).canonicalFile
        require(file.toPath().startsWith(root.toPath())) { "Photo path escapes private storage" }
        return file
    }

    private fun captureFileFrom(uri: Uri): File? {
        if (uri.authority != authority) return null
        val name = uri.lastPathSegment ?: return null
        if (!name.endsWith(".jpg") || name.contains('/') || name.contains('\\')) return null
        return File(captureRoot, name)
    }

    private fun ensureDirectory(directory: File) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Could not create private photo directory")
    }

    private fun isOutOfSpace(error: IOException): Boolean {
        val message = generateSequence(error as Throwable?) { it.cause }.joinToString(" ") { it.message.orEmpty() }.lowercase()
        return message.contains("no space") || message.contains("enospc") || runCatching { StatFs(photosRoot.path).availableBytes < MIN_FREE_BYTES }.getOrDefault(false)
    }

    private companion object {
        const val THUMBNAIL_SIZE = 512
        const val MIN_FREE_BYTES = 1024L
    }
}
