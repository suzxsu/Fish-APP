package com.guanyji.app.feature.feeding

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

class FeedingViewModel(application: Application) : AndroidViewModel(application) {
    private val database = DatabaseFactory.create(application)
    private val repository = LocalRepository(database)
    val tanks = repository.allTanks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val foods = repository.commonFoods().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun fish(tankId: String) = repository.fishInTank(tankId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun history(tankId: String) = repository.feedingsForTank(tankId)
    fun save(event: FeedingEvent, targets: List<FeedingTarget>, onSaved: () -> Unit, onError: (String) -> Unit) = viewModelScope.launch {
        try { repository.saveFeeding(event, targets); onSaved() } catch (e: CancellationException) { throw e } catch (e: Exception) { onError(e.message ?: "喂食记录保存失败") }
    }
    companion object {
        fun factory(application: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(FeedingViewModel::class.java))
                return FeedingViewModel(application) as T
            }
        }
    }
}
