package com.hcexport

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object SyncScheduler {

    fun applySchedules(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        wm.cancelUniqueWork("hc_periodic_sync")
        wm.cancelAllWorkByTag("scheduled_sync")
        for (schedule in Prefs.getSyncSchedules(ctx)) enqueue(ctx, schedule)
    }

    fun enqueue(ctx: Context, schedule: SyncSchedule) {
        val delay = schedule.nextTriggerDelayMs().coerceAtLeast(5_000L)
        val req = OneTimeWorkRequestBuilder<HcSyncWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("schedule_id" to schedule.id))
            .addTag("scheduled_sync")
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "sync_${schedule.id}",
            ExistingWorkPolicy.REPLACE,
            req,
        )
    }
}
