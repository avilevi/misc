package com.hcexport

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object SyncScheduler {

    fun applySchedules(ctx: Context) {
        // Enqueue all schedules stored in Prefs. ExistingWorkPolicy.REPLACE
        // handles updates (same schedule re-enqueued with a fresh delay).
        // Orphaned work from deleted schedules fires once, finds no matching
        // schedule_id in Prefs, and does not re-enqueue itself.
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

    fun nextScheduledTimeMs(ctx: Context): Long? {
        val schedules = Prefs.getSyncSchedules(ctx)
        if (schedules.isEmpty()) return null
        return schedules.minOf { it.nextTriggerMs() }
    }
}
