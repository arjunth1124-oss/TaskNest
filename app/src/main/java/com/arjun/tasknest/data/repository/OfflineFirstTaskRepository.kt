package com.arjun.tasknest.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.arjun.tasknest.data.local.SyncCursorStore
import com.arjun.tasknest.data.local.SyncState
import com.arjun.tasknest.data.local.TaskDao
import com.arjun.tasknest.data.local.TaskEntity
import com.arjun.tasknest.data.sync.SyncScheduler
import com.arjun.tasknest.data.toDomain
import com.arjun.tasknest.domain.Task
import com.arjun.tasknest.domain.TaskRepository
import com.arjun.tasknest.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes go to Room immediately (instant UI, works offline) and are marked PENDING.
 * A background sync is then requested; the UI never waits on the network.
 */
@Singleton
class OfflineFirstTaskRepository @Inject constructor(
    private val dao: TaskDao,
    private val cursorStore: SyncCursorStore,
    private val syncScheduler: SyncScheduler,
    private val clock: Clock,
) : TaskRepository {

    override fun pagedTasks(query: String): Flow<PagingData<Task>> =
        Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = { dao.pagingSource(query.trim()) },
        ).flow.map { page -> page.map(TaskEntity::toDomain) }

    override fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    override fun observeLastSyncedAt(): Flow<Long?> = cursorStore.observeLastSyncedAt()

    override fun observeIsSyncing(): Flow<Boolean> = syncScheduler.observeIsSyncing()

    override suspend fun getTask(id: String): Task? =
        dao.getById(id)?.takeUnless { it.isDeleted }?.toDomain()

    override suspend fun addTask(title: String, description: String): String {
        val id = UUID.randomUUID().toString()
        dao.upsert(
            TaskEntity(
                id = id,
                title = title.trim(),
                description = description.trim(),
                isCompleted = false,
                updatedAt = clock.now(),
                serverVersion = 0,
                syncState = SyncState.PENDING,
            )
        )
        syncScheduler.requestSync()
        return id
    }

    override suspend fun updateTask(id: String, title: String, description: String) =
        editLocally(id) { it.copy(title = title.trim(), description = description.trim()) }

    override suspend fun setCompleted(id: String, completed: Boolean) =
        editLocally(id) { it.copy(isCompleted = completed) }

    override suspend fun deleteTask(id: String) {
        val current = dao.getById(id) ?: return
        if (current.serverVersion == 0L) {
            // Never reached the server: nobody else knows about it, just drop it.
            dao.deleteById(id)
        } else {
            // Keep a tombstone so the delete can be synced to other devices.
            editLocally(id) { it.copy(isDeleted = true) }
        }
    }

    override fun requestSync() = syncScheduler.requestSync()

    private suspend inline fun editLocally(id: String, change: (TaskEntity) -> TaskEntity) {
        val current = dao.getById(id) ?: return
        dao.upsert(change(current).copy(updatedAt = clock.now(), syncState = SyncState.PENDING))
        syncScheduler.requestSync()
    }

    private companion object {
        const val PAGE_SIZE = 30
    }
}
