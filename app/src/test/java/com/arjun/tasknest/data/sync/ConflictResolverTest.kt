package com.arjun.tasknest.data.sync

import com.arjun.tasknest.data.local.SyncState
import com.arjun.tasknest.data.local.TaskEntity
import com.arjun.tasknest.data.remote.TaskDto
import org.junit.Assert.assertEquals
import org.junit.Test

class ConflictResolverTest {

    private fun local(updatedAt: Long, deleted: Boolean = false) =
        TaskEntity("id", "local", "", false, updatedAt, 1, SyncState.PENDING, deleted)

    private fun server(updatedAt: Long, deleted: Boolean = false) =
        TaskDto("id", "server", "", false, updatedAt, 2, deleted)

    @Test
    fun `newer local edit wins`() {
        assertEquals(Resolution.KEEP_LOCAL, ConflictResolver.resolve(local(200), server(100)))
    }

    @Test
    fun `newer server edit wins`() {
        assertEquals(Resolution.ACCEPT_SERVER, ConflictResolver.resolve(local(100), server(200)))
    }

    @Test
    fun `tie goes to server so all devices converge`() {
        assertEquals(Resolution.ACCEPT_SERVER, ConflictResolver.resolve(local(150), server(150)))
    }

    @Test
    fun `edit made after a remote delete restores the task`() {
        assertEquals(Resolution.KEEP_LOCAL, ConflictResolver.resolve(local(300), server(200, deleted = true)))
    }

    @Test
    fun `delete made after a remote edit wins`() {
        assertEquals(Resolution.KEEP_LOCAL, ConflictResolver.resolve(local(300, deleted = true), server(200)))
    }
}
