package com.arjun.tasknest.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Whether a row still has local changes the server hasn't accepted yet. */
enum class SyncState { SYNCED, PENDING }

@Entity(
    tableName = "tasks",
    indices = [Index("syncState"), Index("updatedAt")],
)
data class TaskEntity(
    /** Client-generated UUID, so tasks can be created offline without a server round-trip. */
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val isCompleted: Boolean,
    /** Last local edit time (ms). Used for last-write-wins conflict resolution. */
    val updatedAt: Long,
    /** Server version this row is based on. 0 = never reached the server. */
    val serverVersion: Long,
    val syncState: SyncState,
    /** Soft-delete tombstone kept until the server confirms the delete. */
    val isDeleted: Boolean = false,
)
