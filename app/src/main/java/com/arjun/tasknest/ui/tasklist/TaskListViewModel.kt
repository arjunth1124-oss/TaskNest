package com.arjun.tasknest.ui.tasklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.arjun.tasknest.domain.Task
import com.arjun.tasknest.domain.TaskRepository
import com.arjun.tasknest.util.ConnectivityObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncUiState(
    val isOnline: Boolean = true,
    val isSyncing: Boolean = false,
    val pendingCount: Int = 0,
    val lastSyncedAt: Long? = null,
)

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val repository: TaskRepository,
    connectivity: ConnectivityObserver,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val tasks: Flow<PagingData<Task>> = _query
        .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .distinctUntilChanged()
        .flatMapLatest { repository.pagedTasks(it) }
        .cachedIn(viewModelScope)

    val syncState: StateFlow<SyncUiState> = combine(
        connectivity.observeIsOnline(),
        repository.observeIsSyncing(),
        repository.observePendingCount(),
        repository.observeLastSyncedAt(),
    ) { online, syncing, pending, lastSynced ->
        SyncUiState(online, syncing, pending, lastSynced)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState())

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onToggleCompleted(task: Task) = viewModelScope.launch {
        repository.setCompleted(task.id, !task.isCompleted)
    }

    fun onDelete(task: Task) = viewModelScope.launch {
        repository.deleteTask(task.id)
    }

    fun onRefresh() = repository.requestSync()

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
