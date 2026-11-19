package com.arjun.tasknest.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    /** Visible tasks: open ones first, newest edits on top. Filtered by [query] when not blank. */
    @Query(
        """
        SELECT * FROM tasks
        WHERE isDeleted = 0
          AND (:query = '' OR title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%')
        ORDER BY isCompleted ASC, updatedAt DESC
        """
    )
    fun pagingSource(query: String): PagingSource<Int, TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE syncState = 'PENDING' ORDER BY updatedAt ASC")
    suspend fun getPending(): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE syncState = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Marks a row synced only if it hasn't been edited since we read it for upload.
     * Returns the number of rows updated (0 = the user edited it mid-sync, keep it PENDING).
     */
    @Query(
        """
        UPDATE tasks SET syncState = 'SYNCED', serverVersion = :serverVersion
        WHERE id = :id AND updatedAt = :expectedUpdatedAt
        """
    )
    suspend fun markSyncedIfUnchanged(id: String, serverVersion: Long, expectedUpdatedAt: Long): Int

    @Query("UPDATE tasks SET serverVersion = :serverVersion WHERE id = :id")
    suspend fun updateServerVersion(id: String, serverVersion: Long)

    /** Removes a confirmed tombstone, unless it was changed again mid-sync. */
    @Query("DELETE FROM tasks WHERE id = :id AND updatedAt = :expectedUpdatedAt")
    suspend fun deleteIfUnchanged(id: String, expectedUpdatedAt: Long): Int
}
