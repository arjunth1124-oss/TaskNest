package com.arjun.tasknest.data.local

import kotlinx.coroutines.flow.Flow

/** Remembers how far we've pulled from the server's change feed. */
interface SyncCursorStore {
    suspend fun getCursor(): Long
    suspend fun saveCursor(cursor: Long, syncedAt: Long)
    fun observeLastSyncedAt(): Flow<Long?>
}
