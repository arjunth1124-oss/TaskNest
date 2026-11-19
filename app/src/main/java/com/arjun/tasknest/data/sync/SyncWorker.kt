package com.arjun.tasknest.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/**
 * Runs one sync pass in the background. WorkManager guarantees it runs once the
 * network is available, survives process death, and retries with exponential backoff.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncEngine: SyncEngine,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        val result = syncEngine.sync()
        Log.d(TAG, "Sync done: $result")
        Result.success()
    } catch (e: IOException) {
        // Network dropped mid-sync, server unreachable, etc.
        Log.w(TAG, "Sync failed (network), attempt $runAttemptCount", e)
        retryOrFail()
    } catch (e: HttpException) {
        Log.w(TAG, "Sync failed (HTTP ${e.code()})", e)
        if (e.code() >= 500 || e.code() == 429) retryOrFail() else Result.failure()
    } catch (e: SerializationException) {
        Log.e(TAG, "Sync failed (bad server payload)", e)
        Result.failure()
    }

    private fun retryOrFail(): Result =
        if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()

    private companion object {
        const val TAG = "SyncWorker"
        const val MAX_RETRIES = 5
    }
}
