package com.arjun.tasknest.data.remote

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class TaskDto(
    val id: String,
    val title: String,
    val description: String,
    val isCompleted: Boolean,
    val updatedAt: Long,
    val version: Long,
    val isDeleted: Boolean,
)

/** Upload body. [baseVersion] is the server version the client edit was made on (optimistic locking). */
@Serializable
data class PushRequest(
    val task: TaskDto,
    val baseVersion: Long,
)

/** One page of the server's change feed. */
@Serializable
data class ChangesResponse(
    val tasks: List<TaskDto>,
    val cursor: Long,
    val hasMore: Boolean,
)

/** Body returned with HTTP 409 when [PushRequest.baseVersion] is stale. */
@Serializable
data class ConflictResponse(
    val serverTask: TaskDto,
)

interface TaskApi {

    /** Changes made after [since] (a server-side sequence number, not a timestamp, so clock skew can't lose data). */
    @GET("tasks/changes")
    suspend fun getChanges(
        @Query("since") since: Long,
        @Query("limit") limit: Int = 200,
    ): ChangesResponse

    /** Create, update or tombstone a task. Returns 200 with the stored task, or 409 with [ConflictResponse]. */
    @PUT("tasks/{id}")
    suspend fun pushTask(
        @Path("id") id: String,
        @Body body: PushRequest,
    ): Response<TaskDto>
}
