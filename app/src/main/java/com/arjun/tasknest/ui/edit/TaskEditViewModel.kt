package com.arjun.tasknest.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arjun.tasknest.domain.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaskEditUiState(
    val isNew: Boolean = true,
    val isLoading: Boolean = false,
    val title: String = "",
    val description: String = "",
    val titleError: String? = null,
    val isSaved: Boolean = false,
)

@HiltViewModel
class TaskEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TaskRepository,
) : ViewModel() {

    private val taskId: String? = savedStateHandle[ARG_TASK_ID]

    private val _state = MutableStateFlow(TaskEditUiState(isNew = taskId == null, isLoading = taskId != null))
    val state: StateFlow<TaskEditUiState> = _state.asStateFlow()

    init {
        if (taskId != null) {
            viewModelScope.launch {
                val task = repository.getTask(taskId)
                _state.update {
                    if (task == null) it.copy(isLoading = false, isNew = true)
                    else it.copy(isLoading = false, title = task.title, description = task.description)
                }
            }
        }
    }

    fun onTitleChange(value: String) = _state.update { it.copy(title = value, titleError = null) }

    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value) }

    fun save() {
        val current = _state.value
        if (current.title.isBlank()) {
            _state.update { it.copy(titleError = "Title can't be empty") }
            return
        }
        viewModelScope.launch {
            if (taskId == null || current.isNew) {
                repository.addTask(current.title, current.description)
            } else {
                repository.updateTask(taskId, current.title, current.description)
            }
            _state.update { it.copy(isSaved = true) }
        }
    }

    companion object {
        const val ARG_TASK_ID = "taskId"
    }
}
