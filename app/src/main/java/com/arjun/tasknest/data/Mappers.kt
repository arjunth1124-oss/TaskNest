package com.arjun.tasknest.data

import com.arjun.tasknest.data.local.SyncState
import com.arjun.tasknest.data.local.TaskEntity
import com.arjun.tasknest.data.remote.TaskDto
import com.arjun.tasknest.domain.Task

fun TaskEntity.toDomain() = Task(
    id = id,
    title = title,
    description = description,
    isCompleted = isCompleted,
    updatedAt = updatedAt,
    isPendingSync = syncState == SyncState.PENDING,
)

fun TaskEntity.toDto() = TaskDto(
    id = id,
    title = title,
    description = description,
    isCompleted = isCompleted,
    updatedAt = updatedAt,
    version = serverVersion,
    isDeleted = isDeleted,
)

/** A server copy is always stored as SYNCED, since it matches the server by definition. */
fun TaskDto.toSyncedEntity() = TaskEntity(
    id = id,
    title = title,
    description = description,
    isCompleted = isCompleted,
    updatedAt = updatedAt,
    serverVersion = version,
    syncState = SyncState.SYNCED,
    isDeleted = isDeleted,
)
