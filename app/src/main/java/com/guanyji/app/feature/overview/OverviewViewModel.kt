package com.guanyji.app.feature.overview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.guanyji.app.data.local.CareNotificationScheduler
import com.guanyji.app.data.local.*
import com.guanyji.app.data.repository.LocalRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

data class OverviewSnapshot(val tanks: List<TankSummary>, val fish: List<FishSummary>, val tasks: List<CareTask>)

class OverviewViewModel(application: Application) : AndroidViewModel(application) {
    private val database = DatabaseFactory.create(application)
    private val repository = LocalRepository(database)
    private val zone = ZoneId.systemDefault()
    private val today = Instant.now().atZone(zone).toLocalDate()
    private val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    private val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    val tanks = repository.tanks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val fish = repository.allFish(dayStart, dayEnd).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tasks = repository.careTasks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val waterFlows = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.flow.StateFlow<List<WaterTestWithValues>>>()
    private val photoFlows = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.flow.StateFlow<Photo?>>()
    fun waterTests(tankId: String) = waterFlows.getOrPut(tankId) { repository.waterTestsForTank(tankId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()) }
    fun photo(id: String?) = id?.let { key -> photoFlows.getOrPut(key) { repository.photo(key).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null) } }

    val state = combine(tanks, fish, tasks) { tankRows, fishRows, allTasks ->
        val tankSummaries = tankRows.filter { it.tank.status == TankStatus.active }
        val todayTasks = allTasks.filter { task ->
            (task.plannedAt in dayStart until dayEnd || task.status == CareTaskStatus.overdue || task.status == CareTaskStatus.pending && task.plannedAt < dayStart) &&
                task.status in setOf(CareTaskStatus.pending, CareTaskStatus.overdue)
        }.sortedBy { it.plannedAt }
        Triple(tankSummaries, fishRows, todayTasks)
    }.map { (tankRows, fishRows, todayTasks) -> OverviewSnapshot(tankRows, fishRows, todayTasks) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewSnapshot(emptyList(), emptyList(), emptyList()))

    fun completeTask(task: CareTask, done: () -> Unit, failed: (String) -> Unit) = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                repository.completeCareTask(
                    task.id,
                    CareRecord(taskId = task.id, tankId = task.tankId, fishId = task.fishId, type = CareRecordType.valueOf(task.type.name), completedAt = System.currentTimeMillis(), payloadJson = null, notes = null),
                    System.currentTimeMillis(),
                )
            }
            withContext(Dispatchers.Main) { CareNotificationScheduler.sync(getApplication()) }
            done()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            failed(error.message ?: "完成任务失败")
        }
    }

    companion object {
        fun factory(application: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(OverviewViewModel::class.java)); return OverviewViewModel(application) as T
            }
        }
    }
}
