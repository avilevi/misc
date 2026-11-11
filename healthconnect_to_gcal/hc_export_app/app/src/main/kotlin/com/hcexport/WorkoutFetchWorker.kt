package com.hcexport

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/** The 02:00 job: refresh the workout database and update the watch, nothing else. */
class WorkoutFetchWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        fun enqueue(ctx: Context) {
            val request = OneTimeWorkRequestBuilder<WorkoutFetchWorker>()
                .addTag("hc_workout_pull")
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                SyncWorker.WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request,
            )
        }
    }

    override suspend fun doWork(): Result {
        val report = SyncEngine.run(applicationContext, pullOnly = true)
        return if (report.pull?.ok == true) {
            Result.success()
        } else if (runAttemptCount < 2) {
            Result.retry()
        } else {
            Result.failure()
        }
    }
}
