package com.arjun.tasknest.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface SyncScheduler {
    /** Sync soon (after any running sync), once the device is online. */
    fun requestSync()

    /** Background safety net: sync every 15 minutes while online. */
    fun schedulePeriodicSync()

    fun observeIsSyncing(): Flow<Boolean>
}

@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncScheduler {

    // Lazy: this class is injected into the Application before Hilt has set
    // workerFactory, and getInstance() would read the WorkManager configuration too early.
    private val workManager by lazy { WorkManager.getInstance(context) }

    private val onlineOnly = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    override fun requestSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(onlineOnly)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        // APPEND_OR_REPLACE: an edit made during a running sync still gets its own pass afterwards.
        workManager.enqueueUniqueWork(ONE_TIME_SYNC, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    override fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(onlineOnly)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_SYNC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun observeIsSyncing(): Flow<Boolean> =
        combine(
            workManager.getWorkInfosForUniqueWorkFlow(ONE_TIME_SYNC),
            workManager.getWorkInfosForUniqueWorkFlow(PERIODIC_SYNC),
        ) { oneTime, periodic ->
            (oneTime + periodic).any { it.state == WorkInfo.State.RUNNING }
        }.distinctUntilChanged()

    private companion object {
        const val ONE_TIME_SYNC = "sync_one_time"
        const val PERIODIC_SYNC = "sync_periodic"
    }
}
