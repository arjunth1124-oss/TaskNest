package com.arjun.tasknest.domain

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for tasks. Every read comes from the local database;
 * every write lands locally first and is pushed to the server in the background.
 */
interface TaskRepository {
    fun pagedTasks(query: String): Flow<PagingData<Task>>
    fun observePendingCount(): Flow<Int>
    fun observeLastSyncedAt(): Flow<Long?>
    fun observeIsSyncing(): Flow<Boolean>

    suspend fun getTask(id: String): Task?
    suspend fun addTask(title: String, description: String): String
    suspend fun updateTask(id: String, title: String, description: String)
    suspend fun setCompleted(id: String, completed: Boolean)
    suspend fun deleteTask(id: String)

    /** Ask WorkManager to sync as soon as the network allows. */
    fun requestSync()
}
