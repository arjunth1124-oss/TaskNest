package com.arjun.tasknest.data.sync

import androidx.paging.PagingSource
import com.arjun.tasknest.data.local.SyncCursorStore
import com.arjun.tasknest.data.local.SyncState
import com.arjun.tasknest.data.local.TaskDao
import com.arjun.tasknest.data.local.TaskEntity
import com.arjun.tasknest.data.remote.ChangesResponse
import com.arjun.tasknest.data.remote.ConflictResponse
import com.arjun.tasknest.data.remote.PushRequest
import com.arjun.tasknest.data.remote.TaskApi
import com.arjun.tasknest.data.remote.TaskDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

/** In-memory stand-in for the Room DAO, with the same semantics as the SQL queries. */
class FakeTaskDao : TaskDao {
    val rows = MutableStateFlow<Map<String, TaskEntity>>(emptyMap())

    /** Runs right before the next [markSyncedIfUnchanged]/[deleteIfUnchanged]: simulates a user edit mid-upload. */
    var beforeNextAck: (suspend () -> Unit)? = null

    override fun pagingSource(query: String): PagingSource<Int, TaskEntity> =
        throw UnsupportedOperationException("Not needed in sync tests")

    override suspend fun getById(id: String) = rows.value[id]

    override suspend fun getPending() =
        rows.value.values.filter { it.syncState == SyncState.PENDING }.sortedBy { it.updatedAt }

    override fun observePendingCount(): Flow<Int> =
        rows.map { all -> all.values.count { it.syncState == SyncState.PENDING } }

    override suspend fun upsert(task: TaskEntity) {
        rows.value = rows.value + (task.id to task)
    }

    override suspend fun deleteById(id: String) {
        rows.value = rows.value - id
    }

    override suspend fun markSyncedIfUnchanged(id: String, serverVersion: Long, expectedUpdatedAt: Long): Int {
        runBeforeAck()
        val row = rows.value[id]?.takeIf { it.updatedAt == expectedUpdatedAt } ?: return 0
        upsert(row.copy(syncState = SyncState.SYNCED, serverVersion = serverVersion))
        return 1
    }

    override suspend fun updateServerVersion(id: String, serverVersion: Long) {
        val row = rows.value[id] ?: return
        upsert(row.copy(serverVersion = serverVersion))
    }

    override suspend fun deleteIfUnchanged(id: String, expectedUpdatedAt: Long): Int {
        runBeforeAck()
        if (rows.value[id]?.updatedAt != expectedUpdatedAt) return 0
        deleteById(id)
        return 1
    }

    private suspend fun runBeforeAck() {
        val hook = beforeNextAck ?: return
        beforeNextAck = null
        hook()
    }
}

/** In-memory server with the same rules as server/server.js (optimistic locking + change feed). */
class FakeTaskApi(private val json: Json) : TaskApi {
    private data class Stored(val dto: TaskDto, val seq: Long)

    private val tasks = linkedMapOf<String, Stored>()
    private var seq = 0L
    var pushCalls = 0
        private set

    fun serverTask(id: String): TaskDto? = tasks[id]?.dto

    /** Simulates another device writing to the server. */
    fun editFromOtherDevice(dto: TaskDto) {
        val version = (tasks[dto.id]?.dto?.version ?: 0) + 1
        tasks[dto.id] = Stored(dto.copy(version = version), ++seq)
    }

    override suspend fun getChanges(since: Long, limit: Int): ChangesResponse {
        val changed = tasks.values.filter { it.seq > since }.sortedBy { it.seq }
        val page = changed.take(limit)
        return ChangesResponse(
            tasks = page.map { it.dto },
            cursor = page.lastOrNull()?.seq ?: since,
            hasMore = changed.size > limit,
        )
    }

    override suspend fun pushTask(id: String, body: PushRequest): Response<TaskDto> {
        pushCalls++
        val existing = tasks[id]
        if (existing != null && existing.dto.version != body.baseVersion) {
            val raw = json.encodeToString(ConflictResponse.serializer(), ConflictResponse(existing.dto))
            return Response.error(409, raw.toResponseBody("application/json".toMediaType()))
        }
        val stored = body.task.copy(version = (existing?.dto?.version ?: 0) + 1)
        tasks[id] = Stored(stored, ++seq)
        return Response.success(stored)
    }
}

class FakeCursorStore : SyncCursorStore {
    var cursor = 0L
    val lastSynced = MutableStateFlow<Long?>(null)

    override suspend fun getCursor() = cursor

    override suspend fun saveCursor(cursor: Long, syncedAt: Long) {
        this.cursor = cursor
        lastSynced.value = syncedAt
    }

    override fun observeLastSyncedAt(): Flow<Long?> = lastSynced
}
