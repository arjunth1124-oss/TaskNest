package com.arjun.tasknest.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_prefs")

@Singleton
class DataStoreSyncCursorStore @Inject constructor(
    @ApplicationContext context: Context,
) : SyncCursorStore {

    private val dataStore = context.syncDataStore

    override suspend fun getCursor(): Long =
        dataStore.data.map { it[KEY_CURSOR] ?: 0L }.first()

    override suspend fun saveCursor(cursor: Long, syncedAt: Long) {
        dataStore.edit {
            it[KEY_CURSOR] = cursor
            it[KEY_LAST_SYNCED_AT] = syncedAt
        }
    }

    override fun observeLastSyncedAt(): Flow<Long?> =
        dataStore.data.map { it[KEY_LAST_SYNCED_AT] }

    private companion object {
        val KEY_CURSOR = longPreferencesKey("change_cursor")
        val KEY_LAST_SYNCED_AT = longPreferencesKey("last_synced_at")
    }
}
