package com.arjun.tasknest.data.sync

import com.arjun.tasknest.data.local.SyncState
import com.arjun.tasknest.data.local.TaskEntity
import com.arjun.tasknest.data.remote.TaskDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncEngineTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var dao: FakeTaskDao
    private lateinit var api: FakeTaskApi
    private lateinit var cursor: FakeCursorStore
    private lateinit var engine: SyncEngine

    @Before
    fun setUp() {
        dao = FakeTaskDao()
        api = FakeTaskApi(json)
        cursor = FakeCursorStore()
        engine = SyncEngine(dao, api, cursor, json, clock = { 1_000L })
    }

    private fun local(
        id: String,
        title: String = id,
        updatedAt: Long = 100,
        serverVersion: Long = 0,
        state: SyncState = SyncState.PENDING,
        deleted: Boolean = false,
    ) = TaskEntity(id, title, "", false, updatedAt, serverVersion, state, deleted)

    private fun remote(id: String, title: String = id, updatedAt: Long = 100, deleted: Boolean = false) =
        TaskDto(id, title, "", false, updatedAt, version = 0, isDeleted = deleted)

    @Test
    fun `offline-created task is uploaded and marked synced`() = runTest {
        dao.upsert(local("a", "Buy milk"))

        val result = engine.sync()

        assertEquals(1, result.pushed)
        assertEquals("Buy milk", api.serverTask("a")?.title)
        val row = dao.getById("a")!!
        assertEquals(SyncState.SYNCED, row.syncState)
        assertEquals(1L, row.serverVersion)
    }

    @Test
    fun `tasks created on another device are pulled into the local database`() = runTest {
        api.editFromOtherDevice(remote("b", "From laptop"))

        val result = engine.sync()

        assertEquals(1, result.pulled)
        val row = dao.getById("b")!!
        assertEquals("From laptop", row.title)
        assertEquals(SyncState.SYNCED, row.syncState)
        assertTrue(cursor.cursor > 0)
    }

    @Test
    fun `conflict - newer local edit wins and is re-uploaded on top of server version`() = runTest {
        api.editFromOtherDevice(remote("c", "server v1", updatedAt = 100))
        engine.sync()
        api.editFromOtherDevice(remote("c", "laptop edit", updatedAt = 200)) // server now v2
        dao.upsert(local("c", "phone edit", updatedAt = 300, serverVersion = 1)) // based on v1, but newer

        val result = engine.sync()

        assertTrue(result.conflicts >= 1)
        assertEquals("phone edit", api.serverTask("c")?.title)
        assertEquals(3L, api.serverTask("c")?.version)
        assertEquals(SyncState.SYNCED, dao.getById("c")?.syncState)
    }

    @Test
    fun `conflict - newer server edit wins and replaces the local edit`() = runTest {
        api.editFromOtherDevice(remote("d", "server v1", updatedAt = 100))
        engine.sync()
        dao.upsert(local("d", "old phone edit", updatedAt = 150, serverVersion = 1))
        api.editFromOtherDevice(remote("d", "newer laptop edit", updatedAt = 400))

        engine.sync()

        val row = dao.getById("d")!!
        assertEquals("newer laptop edit", row.title)
        assertEquals(SyncState.SYNCED, row.syncState)
        assertEquals("newer laptop edit", api.serverTask("d")?.title)
    }

    @Test
    fun `local delete is sent as tombstone and row is removed once confirmed`() = runTest {
        dao.upsert(local("e"))
        engine.sync()
        dao.upsert(dao.getById("e")!!.copy(isDeleted = true, updatedAt = 200, syncState = SyncState.PENDING))

        engine.sync()

        assertNull(dao.getById("e"))
        assertTrue(api.serverTask("e")!!.isDeleted)
    }

    @Test
    fun `delete from another device removes the synced local copy`() = runTest {
        api.editFromOtherDevice(remote("f"))
        engine.sync()
        api.editFromOtherDevice(remote("f", deleted = true, updatedAt = 500))

        engine.sync()

        assertNull(dao.getById("f"))
    }

    @Test
    fun `edit made while uploading stays pending and is not lost`() = runTest {
        dao.upsert(local("g", "first", updatedAt = 100))
        dao.beforeNextAck = {
            // User edits the task after the upload request left, before we mark it synced.
            dao.upsert(dao.getById("g")!!.copy(title = "second", updatedAt = 200))
        }

        engine.sync() // pushes "first", sees the edit, keeps it pending, then pushes "second"

        assertEquals("second", api.serverTask("g")?.title)
        assertEquals(SyncState.SYNCED, dao.getById("g")?.syncState)
        assertEquals("second", dao.getById("g")?.title)
    }

    @Test
    fun `change feed is read page by page until hasMore is false`() = runTest {
        repeat(450) { i -> api.editFromOtherDevice(remote("t$i")) }

        val result = engine.sync()

        assertEquals(450, result.pulled)
        assertEquals(450, dao.rows.value.size)
    }

    @Test
    fun `nothing to do means no uploads`() = runTest {
        val result = engine.sync()

        assertEquals(0, result.pushed)
        assertEquals(0, api.pushCalls)
        assertEquals(1_000L, cursor.lastSynced.value)
    }
}
