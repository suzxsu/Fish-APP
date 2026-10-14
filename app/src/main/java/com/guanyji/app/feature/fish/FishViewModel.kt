package com.guanyji.app.feature.fish

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.guanyji.app.data.local.*
import com.guanyji.app.data.photo.ManagedPhotoFileStore
import com.guanyji.app.data.photo.PhotoRepository
import com.guanyji.app.data.repository.LocalRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

class FishViewModel(application: Application) : AndroidViewModel(application) {
    private val database = DatabaseFactory.create(application)
    private val repository = LocalRepository(database)
    val photoRepository = PhotoRepository(database, ManagedPhotoFileStore(application))
    private fun dayBounds(): Pair<Long, Long> {
        val start = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return start to end
    }
    val fish = dayBounds().let { (start, end) -> repository.allFish(start, end) }.map<List<FishSummary>, List<FishSummary>?> { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val tanks = repository.allTanks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun summary(id: String) = dayBounds().let { (start, end) -> repository.fishSummary(id, start, end) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun events(id: String) = repository.fishEvents(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun growth(id: String) = repository.growthRecords(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun saveGrowth(value: GrowthRecord, onSaved: () -> Unit, onError: (String) -> Unit) = run(onSaved, onError) { repository.saveGrowthRecord(value) }
    fun deleteGrowth(id: String, onSaved: () -> Unit, onError: (String) -> Unit) = run(onSaved, onError) { repository.deleteGrowthRecord(id) }
    fun feedings(id: String, tankId: String) = repository.feedingsForFish(id, tankId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun photo(id: String?) = (id?.let(repository::photo) ?: flowOf(null)).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun save(value: Fish, length: Double?, weight: Double?, onSaved: () -> Unit, onError: (String) -> Unit) = run(onSaved, onError) {
        val growth = if (length != null || weight != null) GrowthRecord(fishId = value.id, measuredAt = value.addedAt, lengthValue = length, lengthUnit = length?.let { "cm" }, weightValue = weight, weightUnit = weight?.let { "g" }, method = null, photoId = null, notes = null) else null
        repository.saveFish(value, growth)
    }
    fun move(id: String, tankId: String, date: Long, onSaved: () -> Unit, onError: (String) -> Unit) = run(onSaved, onError) { repository.moveFish(id, tankId, date) }
    fun setStatus(id: String, status: FishStatus, date: Long?, dateKnown: Boolean = true, onSaved: () -> Unit = {}, onError: (String) -> Unit = {}) = run(onSaved, onError) { repository.changeFishStatus(id, status, date, dateKnown) }
    fun archive(id: String, onSaved: () -> Unit, onError: (String) -> Unit) = run(onSaved, onError) { repository.archiveFish(id) }
    private fun run(onSaved: () -> Unit, onError: (String) -> Unit, action: suspend () -> Unit) = viewModelScope.launch {
        try { action(); onSaved() } catch (error: CancellationException) { throw error } catch (error: Exception) { onError(error.message ?: "操作失败") }
    }
    override fun onCleared() { database.close() }
    companion object {
        fun factory(application: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T { require(modelClass.isAssignableFrom(FishViewModel::class.java)); return FishViewModel(application) as T }
        }
    }
}
