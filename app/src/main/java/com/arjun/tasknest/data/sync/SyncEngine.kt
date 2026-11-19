package com.arjun.tasknest.data.sync

import com.arjun.tasknest.data.local.SyncCursorStore
import com.arjun.tasknest.data.local.SyncState
import com.arjun.tasknest.data.local.TaskDao
import com.arjun.tasknest.data.local.TaskEntity
import com.arjun.tasknest.data.remote.ConflictResponse
import com.arjun.tasknest.data.remote.PushRequest
import com.arjun.tasknest.data.remote.TaskApi
import com.arjun.tasknest.data.remote.TaskDto
import com.arjun.tasknest.data.toDto
import com.arjun.tasknest.data.toSyncedEntity
import com.arjun.tasknest.util.Clock
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

data class SyncResult(
    val pushed: Int,
    val pulled: Int,
    val conflicts: Int,
)

/**
 * Two-way sync:
 *  1. **Push**: upload every PENDING row with optimistic locking (baseVersion).
 *     A 409 means someone else changed the task first; the conflict is resolved
 *     with last-write-wins and, if the local edit wins, re-uploaded.
 *  2. **Pull**: read the server's change feed from the saved cursor and merge
 *     each change into Room.
 *  3. **Push again** if the pull rebased any local edits onto newer server versions.
 *
 * Has no Android dependencies, so it is fully unit-testable with fakes.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val dao: TaskDao,
    private val api: TaskApi,
    private val cursorStore: SyncCursorStore,
    private val json: Json,
    private val clock: Clock,
) {

    private class Stats {
        var pushed = 0
        var pulled = 0
        var conflicts = 0
    }

    suspend fun sync(): SyncResult {
        val stats = Stats()
        pushPending(stats)
        pullChanges(stats)
        if (dao.getPending().isNotEmpty()) pushPending(stats)
        return SyncResult(stats.pushed, stats.pulled, stats.conflicts)
    }

    // region Push

    private suspend fun pushPending(stats: Stats) {
        for (task in dao.getPending()) pushOne(task, stats)
    }

    private suspend fun pushOne(initial: TaskEntity, stats: Stats) {
        var task = initial
        repeat(MAX_PUSH_ATTEMPTS) {
            val response = api.pushTask(task.id, PushRequest(task.toDto(), baseVersion = task.serverVersion))
            when {
                response.isSuccessful -> {
                    val stored = response.body() ?: throw HttpException(response)
                    onPushAccepted(task, stored)
                    stats.pushed++
                    return
                }
                response.code() == HTTP_CONFLICT -> {
                    stats.conflicts++
                    mergeServerTask(parseConflict(response))
                    // If the server copy won, the row is no longer pending and we're done.
                    // If our edit won, it's now rebased on the server version: try again.
                    task = dao.getById(task.id)?.takeIf { it.syncState == SyncState.PENDING } ?: return
                }
                else -> throw HttpException(response)
            }
        }
    }

    private suspend fun onPushAccepted(sent: TaskEntity, stored: TaskDto) {
        val unchanged = if (sent.isDeleted) {
            dao.deleteIfUnchanged(sent.id, expectedUpdatedAt = sent.updatedAt) > 0
        } else {
            dao.markSyncedIfUnchanged(sent.id, stored.version, expectedUpdatedAt = sent.updatedAt) > 0
        }
        // The user edited the task while it was uploading: keep it PENDING,
        // but base the next upload on the version the server just stored.
        if (!unchanged) dao.updateServerVersion(sent.id, stored.version)
    }

    private fun parseConflict(response: Response<TaskDto>): TaskDto {
        val raw = response.errorBody()?.string() ?: throw HttpException(response)
        return json.decodeFromString(ConflictResponse.serializer(), raw).serverTask
    }

    // endregion

    // region Pull

    private suspend fun pullChanges(stats: Stats) {
        var cursor = cursorStore.getCursor()
        do {
            val page = api.getChanges(since = cursor)
            for (server in page.tasks) {
                if (mergeServerTask(server)) stats.conflicts++
                stats.pulled++
            }
            cursor = page.cursor
            cursorStore.saveCursor(cursor, syncedAt = clock.now())
        } while (page.hasMore)
    }

    // endregion

    /**
     * Merges one server copy into Room. Returns true if it collided with a pending local edit.
     */
    internal suspend fun mergeServerTask(server: TaskDto): Boolean {
        val local = dao.getById(server.id)
        return when {
            local == null -> {
                if (!server.isDeleted) dao.upsert(server.toSyncedEntity())
                false
            }
            local.syncState == SyncState.SYNCED -> {
                // Ignore stale pages; otherwise the server copy simply replaces ours.
                if (server.version >= local.serverVersion) applyServerCopy(server)
                false
            }
            else -> {
                when (ConflictResolver.resolve(local, server)) {
                    Resolution.KEEP_LOCAL -> dao.updateServerVersion(local.id, server.version)
                    Resolution.ACCEPT_SERVER -> applyServerCopy(server)
                }
                true
            }
        }
    }

    private suspend fun applyServerCopy(server: TaskDto) {
        if (server.isDeleted) dao.deleteById(server.id) else dao.upsert(server.toSyncedEntity())
    }

    private companion object {
        const val HTTP_CONFLICT = 409
        const val MAX_PUSH_ATTEMPTS = 3
    }
}
