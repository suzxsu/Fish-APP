package com.guanyji.app.feature.tank

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.guanyji.app.data.local.*
import com.guanyji.app.data.repository.LocalRepository
import com.guanyji.app.data.photo.ManagedPhotoFileStore
import com.guanyji.app.data.photo.PhotoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TankViewModel(application: Application) : AndroidViewModel(application) {
    private val database = DatabaseFactory.create(application)
    private val repository = LocalRepository(database)
    val photoRepository = PhotoRepository(database, ManagedPhotoFileStore(application))
    val tanks = repository.tanks().map<List<TankSummary>, List<TankSummary>?> { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(tank: Tank, onSaved: () -> Unit, onError: (String) -> Unit) = viewModelScope.launch {
        try { repository.saveTank(tank); CareNotificationScheduler.sync(getApplication()); onSaved() } catch (error: CancellationException) { throw error } catch (error: Exception) { onError(error.message ?: "保存失败") }
    }

    fun archive(id: String, confirmedWithFish: Boolean, onSaved: () -> Unit, onError: (String) -> Unit) = viewModelScope.launch {
        try { repository.archiveTank(id, System.currentTimeMillis(), confirmedWithFish); CareNotificationScheduler.sync(getApplication()); onSaved() } catch (error: CancellationException) { throw error } catch (error: Exception) { onError(error.message ?: "归档失败") }
    }

    sealed interface SummaryState {
        data object Loading : SummaryState
        data class Found(val summary: TankSummary) : SummaryState
        data object Missing : SummaryState
        data class Failed(val message: String) : SummaryState
    }

    fun summary(id: String?): kotlinx.coroutines.flow.StateFlow<SummaryState> =
        (id?.takeIf(String::isNotBlank)?.let(repository::tankSummary) ?: flowOf(null))
            .map<TankSummary?, SummaryState> { it?.let(SummaryState::Found) ?: SummaryState.Missing }
            .catch { emit(SummaryState.Failed(it.message ?: "鱼缸加载失败")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryState.Loading)
    fun fish(id: String) = repository.fishInTank(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun tests(id: String) = repository.waterTestsForTank(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun changes(id: String) = repository.waterChangesForTank(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun tanks() = repository.allTanks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun tasks() = repository.careTasks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun tasks(tankId: String) = repository.careTasksForTank(tankId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun photo(id: String?) = (id?.let(repository::photo) ?: flowOf(null)).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addFish(value: Fish, onSaved: () -> Unit, onError: (String) -> Unit) = viewModelScope.launch {
        try { repository.saveFish(value); onSaved() } catch (error: CancellationException) { throw error } catch (error: Exception) { onError(error.message ?: "保存失败") }
    }

    fun addWaterTest(test: WaterTest, values: List<WaterTestValue>, onSaved: () -> Unit, onError: (String) -> Unit) = viewModelScope.launch {
        try { repository.saveWaterTest(test, values); onSaved() } catch (error: CancellationException) { throw error } catch (error: Exception) { onError(error.message ?: "保存失败") }
    }

    fun addWaterChange(value: WaterChange, onSaved: () -> Unit, onError: (String) -> Unit) = viewModelScope.launch {
        try { repository.saveWaterChange(value); onSaved() } catch (error: CancellationException) { throw error } catch (error: Exception) { onError(error.message ?: "保存失败") }
    }

    suspend fun waterChange(id: String) = repository.waterChange(id)

    fun addTask(value: CareTask, onSaved: () -> Unit, onError: (String) -> Unit) = viewModelScope.launch {
        try { repository.saveCareTask(value); onSaved() } catch (error: CancellationException) { throw error } catch (error: Exception) { onError(error.message ?: "保存失败") }
    }

    companion object {
        fun factory(application: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(TankViewModel::class.java))
                return TankViewModel(application) as T
            }
        }
    }
}
