package com.guanyji.app.feature.care

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.guanyji.app.data.local.*
import com.guanyji.app.data.repository.LocalRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CareViewModel(application: Application) : AndroidViewModel(application) {
    private val database = DatabaseFactory.create(application)
    private val repository = LocalRepository(database)
    val tasks = repository.careTasks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val records = repository.careRecords().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tanks = repository.allTanks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val fish = repository.allFish(0, Long.MAX_VALUE).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refreshOverdue() = viewModelScope.launch { repository.refreshOverdue() }
    fun save(task: CareTask, intervalDays: Int?, reminder: Boolean, done: () -> Unit, failed: (String) -> Unit) = viewModelScope.launch {
        try { repository.saveCareTask(task, intervalDays, reminder); CareNotificationScheduler.sync(getApplication()); done() }
        catch (error: CancellationException) { throw error } catch (error: Exception) { failed(error.message ?: "保存失败") }
    }
    fun finish(task: CareTask, note: String, payload: String?, done: () -> Unit, failed: (String) -> Unit) = viewModelScope.launch {
        try {
            repository.completeCareTask(task.id, CareRecord(taskId = task.id, tankId = task.tankId, fishId = task.fishId, type = CareRecordType.valueOf(task.type.name), completedAt = System.currentTimeMillis(), payloadJson = payload, notes = note.trim().ifBlank { null }), System.currentTimeMillis())
            CareNotificationScheduler.sync(getApplication()); done()
        } catch (error: CancellationException) { throw error } catch (error: Exception) { failed(error.message ?: "完成失败") }
    }
    fun status(id: String, status: CareTaskStatus, done: () -> Unit, failed: (String) -> Unit) = viewModelScope.launch {
        try { repository.changeTaskStatus(id, status); CareNotificationScheduler.sync(getApplication()); done() }
        catch (error: CancellationException) { throw error } catch (error: Exception) { failed(error.message ?: "操作失败") }
    }
    fun postpone(id: String, time: Long, done: () -> Unit, failed: (String) -> Unit) = viewModelScope.launch {
        try { repository.postponeTask(id, time); CareNotificationScheduler.sync(getApplication()); done() }
        catch (error: CancellationException) { throw error } catch (error: Exception) { failed(error.message ?: "延期失败") }
    }
    companion object {
        fun factory(application: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(CareViewModel::class.java)); return CareViewModel(application) as T
            }
        }
    }
}
