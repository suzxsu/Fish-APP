package com.guanyji.app.feature.profile

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.guanyji.app.data.backup.BackupSummary
import com.guanyji.app.data.backup.LocalBackupManager
import com.guanyji.app.data.local.AppSetting
import com.guanyji.app.data.local.CareTaskStatus
import com.guanyji.app.data.local.DatabaseFactory
import com.guanyji.app.data.repository.LocalRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppPreferences(
    val temperature: String = "°C",
    val length: String = "cm",
    val volume: String = "L",
    val weight: String = "g",
    val dateFormat: String = "yyyy-MM-dd",
    val theme: String = "system",
    val notifications: Boolean = true,
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LocalRepository(DatabaseFactory.create(application))
    private val backup = LocalBackupManager(application)
    val preferences = repository.settings().map { rows -> rows.toPreferences() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences())
    val operation = kotlinx.coroutines.flow.MutableStateFlow<ProfileOperation>(ProfileOperation.Idle)
    val storage = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val databaseStorage = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val photoStorage = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val overview = combine(
        repository.allTanks(),
        repository.statisticsFish(),
        repository.careTasks(),
        repository.statisticsWaterTests(),
    ) { tanks, fish, tasks, tests ->
        ProfileOverviewState.Ready(
            tanks = tanks.count { it.status.name == "active" },
            fish = fish.count { it.status.name == "active" || it.status.name == "quarantine" },
            tasks = tasks.count { it.status == CareTaskStatus.pending || it.status == CareTaskStatus.overdue },
            waterAlerts = countLatestOutOfRangeValues(tests),
        ) as ProfileOverviewState
    }.catch { failure -> emit(ProfileOverviewState.Failed(failure.message ?: "本地数据读取失败")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileOverviewState.Loading)

    init { refreshStorage() }

    fun setPreference(key: String, value: String) = viewModelScope.launch {
        try { repository.saveSetting(AppSetting(key, value, System.currentTimeMillis())) }
        catch (failure: Exception) { operation.value = ProfileOperation.Failed(failure.message ?: "设置保存失败") }
    }

    fun setNotifications(enabled: Boolean) = viewModelScope.launch {
        try {
            repository.saveSetting(AppSetting("notifications.enabled", enabled.toString(), System.currentTimeMillis()))
            com.guanyji.app.data.local.CareNotificationScheduler.sync(getApplication())
        } catch (failure: Exception) { operation.value = ProfileOperation.Failed(failure.message ?: "提醒设置保存失败") }
    }

    fun export(uri: Uri) = viewModelScope.launch {
        if (operation.value is ProfileOperation.Working) return@launch
        operation.value = ProfileOperation.Working("正在打包本地数据…")
        try { val result = backup.export(uri); operation.value = ProfileOperation.Done("已导出：${result.tanks} 个鱼缸、${result.fish} 条鱼、${result.growthRecords} 条成长记录、喂食 ${result.feedingRecords} 条、水质 ${result.waterQualityRecords} 条、换水 ${result.waterChangeRecords} 条、养护 ${result.careRecords} 条、${result.photos} 张照片") }
        catch (failure: CancellationException) { throw failure }
        catch (failure: Exception) { operation.value = ProfileOperation.Failed(failure.message ?: "导出失败") }
    }

    fun inspect(uri: Uri) = viewModelScope.launch {
        if (operation.value is ProfileOperation.Working) return@launch
        operation.value = ProfileOperation.Working("正在校验备份…")
        try { operation.value = ProfileOperation.Ready(uri, backup.inspect(uri)) }
        catch (failure: CancellationException) { throw failure }
        catch (failure: Exception) { operation.value = ProfileOperation.Failed(failure.message ?: "备份无法读取") }
    }

    fun restore(uri: Uri) = viewModelScope.launch {
        if (operation.value is ProfileOperation.Working) return@launch
        operation.value = ProfileOperation.Working("正在恢复并验证本地数据…")
        try { val result = backup.restore(uri); refreshStorage(); operation.value = ProfileOperation.Restored("恢复完成：${result.tanks} 个鱼缸、${result.fish} 条鱼、${result.records} 条记录和 ${result.photos} 张照片") }
        catch (failure: CancellationException) { throw failure }
        catch (failure: Exception) { operation.value = ProfileOperation.Failed(failure.message ?: "恢复失败，现有数据已保留") }
    }

    fun clearOperation() { operation.value = ProfileOperation.Idle }

    private fun refreshStorage() = viewModelScope.launch(Dispatchers.IO) {
        val context = getApplication<Application>()
        val db = context.getDatabasePath("guanyji.db")
        val photos = java.io.File(context.filesDir, "photos")
        val dbBytes = if (db.isFile) db.length() else 0L
        val photoBytes = photos.walkTopDown().filter(java.io.File::isFile).sumOf { it.length() }
        databaseStorage.value = dbBytes
        photoStorage.value = photoBytes
        storage.value = dbBytes + photoBytes
    }

    private fun List<AppSetting>.toPreferences(): AppPreferences {
        val values = associate { it.key to it.value }
        return AppPreferences(
            temperature = values["unit.temperature"] ?: "°C",
            length = values["unit.length"] ?: "cm",
            volume = values["unit.volume"] ?: "L",
            weight = values["unit.weight"] ?: "g",
            dateFormat = values["date.format"] ?: "yyyy-MM-dd",
            theme = values["theme"] ?: "system",
            notifications = values["notifications.enabled"]?.toBooleanStrictOrNull() ?: true,
        )
    }

    companion object {
        fun factory(application: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(ProfileViewModel::class.java))
                return ProfileViewModel(application) as T
            }
        }
    }
}

sealed interface ProfileOverviewState {
    data object Loading : ProfileOverviewState
    data class Ready(val tanks: Int, val fish: Int, val tasks: Int, val waterAlerts: Int) : ProfileOverviewState
    data class Failed(val message: String) : ProfileOverviewState
}

sealed interface ProfileOperation {
    data object Idle : ProfileOperation
    data class Working(val message: String) : ProfileOperation
    data class Done(val message: String) : ProfileOperation
    data class Restored(val message: String) : ProfileOperation
    data class Failed(val message: String) : ProfileOperation
    data class Ready(val uri: Uri, val summary: BackupSummary) : ProfileOperation
}
