package com.hcexport

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/** Pushes the snapshot after journal edits, without running a whole sync. */
class WearPushWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        private const val WORK_NAME = "wear_push"

        fun enqueue(ctx: Context) {
            val request = OneTimeWorkRequestBuilder<WearPushWorker>().build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                WORK_NAME, ExistingWorkPolicy.REPLACE, request,
            )
        }
    }

    override suspend fun doWork(): Result =
        if (WearSnapshotPublisher.publish(applicationContext)) Result.success()
        else if (runAttemptCount < 2) Result.retry()
        else Result.failure()
}
