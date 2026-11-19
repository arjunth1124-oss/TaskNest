package com.arjun.tasknest.data.sync

import com.arjun.tasknest.data.local.TaskEntity
import com.arjun.tasknest.data.remote.TaskDto

enum class Resolution {
    /** Local edit is newer: re-upload it on top of the server's version. */
    KEEP_LOCAL,

    /** Server copy is newer (or equally new): overwrite the local row. */
    ACCEPT_SERVER,
}

/**
 * Last-write-wins on the edit timestamp. Ties go to the server, so every device
 * converges on the same result. Deletes are treated like any other edit, so an
 * edit made after a delete brings the task back, and a delete made after an
 * edit wins.
 */
object ConflictResolver {
    fun resolve(local: TaskEntity, server: TaskDto): Resolution =
        if (local.updatedAt > server.updatedAt) Resolution.KEEP_LOCAL else Resolution.ACCEPT_SERVER
}
