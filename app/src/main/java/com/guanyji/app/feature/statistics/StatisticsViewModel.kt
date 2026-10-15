package com.guanyji.app.feature.statistics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.guanyji.app.data.local.DatabaseFactory
import com.guanyji.app.feature.statistics.StatisticsInput
import com.guanyji.app.data.repository.LocalRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

data class StatisticsFilter(val tankId: String? = null, val from: LocalDate, val through: LocalDate)
private data class FishHistory(val tanks: List<com.guanyji.app.data.local.Tank>, val fish: List<com.guanyji.app.data.local.Fish>, val events: List<com.guanyji.app.data.local.FishEvent>, val growth: List<com.guanyji.app.data.local.GrowthRecord>)
private data class TankHistory(val feedings: List<com.guanyji.app.data.local.FeedingEventWithTargets>, val tests: List<com.guanyji.app.data.local.WaterTestWithValues>, val changes: List<com.guanyji.app.data.local.WaterChange>)

class StatisticsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LocalRepository(DatabaseFactory.create(application))
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    val filter = kotlinx.coroutines.flow.MutableStateFlow(StatisticsFilter(from = today.minusDays(29), through = today))
    val tanks = repository.allTanks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val fishHistory = combine(repository.allTanks(), repository.statisticsFish(), repository.statisticsFishEvents(), repository.statisticsGrowth()) { tanks, fish, events, growth -> FishHistory(tanks, fish, events, growth) }
    private val tankHistory = combine(repository.statisticsFeedings(), repository.statisticsWaterTests(), repository.statisticsWaterChanges()) { feedings, tests, changes -> TankHistory(feedings, tests, changes) }
    private val careHistory = combine(repository.statisticsCareTasks(), repository.statisticsCareRecords()) { tasks, records -> tasks to records }
    private val input = combine(fishHistory, tankHistory, careHistory) { fishRows, tankRows, careRows ->
        StatisticsInput(fishRows.tanks, fishRows.fish, fishRows.events, fishRows.growth, tankRows.feedings, tankRows.tests, tankRows.changes, careRows.first, careRows.second)
    }
    val report = combine(input, filter) { rows, selected ->
        runCatching { calculateStatistics(rows, selected.tankId, selected.from, selected.through, zone) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setTank(id: String?) { filter.value = filter.value.copy(tankId = id) }
    fun setRange(days: Long) { filter.value = filter.value.copy(from = LocalDate.now(zone).minusDays(days - 1), through = LocalDate.now(zone)) }
    fun setDates(from: LocalDate, through: LocalDate) { if (!through.isBefore(from)) filter.value = filter.value.copy(from = from, through = through) }

    companion object {
        fun factory(application: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(StatisticsViewModel::class.java))
                return StatisticsViewModel(application) as T
            }
        }
    }
}
