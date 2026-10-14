package com.guanyji.app.feature.photo

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.guanyji.app.data.local.Photo
import com.guanyji.app.data.photo.PhotoRepository
import com.guanyji.app.data.photo.ManagedPhotoFileStore
import com.guanyji.app.data.photo.PhotoStorageException
import com.guanyji.app.data.photo.PhotoStorageFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PhotoMessage { saved, selectionCancelled, cameraUnavailable, unreadable, invalidImage, noSpace, accessDenied, saveFailed }

data class PhotoUiState(
    val photo: Photo? = null,
    val isBusy: Boolean = false,
    val cameraCaptureUri: Uri? = null,
    val message: PhotoMessage? = null,
)

class PhotoViewModel(private val repository: PhotoRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(PhotoUiState())
    val state: StateFlow<PhotoUiState> = mutableState.asStateFlow()

    fun clearSelection() {
        if (!mutableState.value.isBusy && mutableState.value.cameraCaptureUri == null) mutableState.value = PhotoUiState()
    }

    fun load(photoId: String?) {
        if (photoId == null) {
            mutableState.value = PhotoUiState()
            return
        }
        if (mutableState.value.isBusy) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isBusy = true, message = null)
            mutableState.value = try {
                PhotoUiState(photo = repository.get(photoId))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                PhotoUiState(message = PhotoMessage.saveFailed)
            }
        }
    }

    fun import(uri: Uri) = viewModelScope.launch {
        if (mutableState.value.isBusy || mutableState.value.cameraCaptureUri != null) return@launch
        mutableState.value = mutableState.value.copy(isBusy = true, message = null)
        try {
            val photo = repository.import(uri)
            mutableState.value = PhotoUiState(photo = photo, message = PhotoMessage.saved)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(isBusy = false, message = error.toPhotoMessage())
        }
    }

    fun prepareCameraCapture() = viewModelScope.launch {
        if (mutableState.value.isBusy || mutableState.value.cameraCaptureUri != null) return@launch
        mutableState.value = mutableState.value.copy(isBusy = true, message = null)
        try {
            val uri = withContext(Dispatchers.IO) { repository.cameraCaptureUri() }
            mutableState.value = mutableState.value.copy(isBusy = false, cameraCaptureUri = uri)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(isBusy = false, message = PhotoMessage.cameraUnavailable)
        }
    }

    fun cameraResult(uri: Uri, captured: Boolean, launchFailed: Boolean = false) = viewModelScope.launch {
        if (mutableState.value.isBusy) return@launch
        mutableState.value = mutableState.value.copy(isBusy = true, cameraCaptureUri = null, message = null)
        try {
            val photo = repository.completeCameraCapture(uri, captured)
            mutableState.value = if (captured && photo != null) PhotoUiState(photo = photo, message = PhotoMessage.saved)
            else mutableState.value.copy(isBusy = false, message = if (launchFailed) PhotoMessage.cameraUnavailable else PhotoMessage.selectionCancelled)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(isBusy = false, message = error.toPhotoMessage())
        }
    }

    fun cameraUnavailable(uri: Uri) { cameraResult(uri, captured = false, launchFailed = true) }

    private fun Exception.toPhotoMessage(): PhotoMessage = when ((this as? PhotoStorageException)?.failure) {
        PhotoStorageFailure.unreadable -> PhotoMessage.unreadable
        PhotoStorageFailure.invalidImage -> PhotoMessage.invalidImage
        PhotoStorageFailure.noSpace -> PhotoMessage.noSpace
        PhotoStorageFailure.accessDenied -> PhotoMessage.accessDenied
        null -> PhotoMessage.saveFailed
    }
}

class PhotoViewModelFactory(private val repository: PhotoRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(PhotoViewModel::class.java))
        return PhotoViewModel(repository) as T
    }
}
