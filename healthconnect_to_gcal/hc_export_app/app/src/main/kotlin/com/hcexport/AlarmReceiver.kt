package com.hcexport

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_SYNC -> {
                // Re-arm first: if the process dies while the sync is running, the
                // next occurrence is already scheduled.
                AlarmScheduler.scheduleNextSync(ctx)
                SyncWorker.enqueue(ctx)
            }
            AlarmScheduler.ACTION_PULL -> {
                AlarmScheduler.scheduleNextPull(ctx)
                WorkoutFetchWorker.enqueue(ctx)
            }
        }
    }
}
