package com.hcexport

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Pushes the snapshot after journal edits, without running a whole sync. */
class WearPushWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        private const val WORK_NAME = "wear_push"

        /**
         * A delay lets a burst of edits (typing values, a live session's
         * writes) collapse into one push: REPLACE moves the pending work.
         */
        fun enqueue(ctx: Context, delayMs: Long = 0L) {
            val request = OneTimeWorkRequestBuilder<WearPushWorker>().apply {
                if (delayMs > 0) setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            }.build()
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
