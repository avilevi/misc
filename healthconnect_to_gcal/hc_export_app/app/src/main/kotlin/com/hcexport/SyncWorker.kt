package com.hcexport

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        const val WORK_NAME = "hc_sync"
        const val KEY_FORCE_CALENDAR = "force_calendar"
        const val KEY_FORCE_JOURNAL = "force_journal"

        /**
         * One chain for both a full sync and the 02:00 pull, so they can never race on
         * the workout database or the journal file.
         */
        fun enqueue(
            ctx: Context,
            forceCalendar: Boolean = false,
            forceJournal: Boolean = false,
        ): java.util.UUID {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(
                    workDataOf(
                        KEY_FORCE_CALENDAR to forceCalendar,
                        KEY_FORCE_JOURNAL to forceJournal,
                    )
                )
                .addTag("hc_sync")
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request,
            )
            return request.id
        }
    }

    override suspend fun doWork(): Result {
        val forceCalendar = inputData.getBoolean(KEY_FORCE_CALENDAR, false)
        val forceJournal = inputData.getBoolean(KEY_FORCE_JOURNAL, false)
        Log.i("SyncWorker", "Starting unified sync")
        val report = SyncEngine.run(applicationContext, forceCalendar, forceJournal)
        val anyPhaseSucceeded =
            report.pull?.ok == true || report.calendar?.ok == true || report.journal?.ok == true
        return if (anyPhaseSucceeded) Result.success() else Result.failure()
    }
}
