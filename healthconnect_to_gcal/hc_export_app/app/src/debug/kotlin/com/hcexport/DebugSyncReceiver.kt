package com.hcexport

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Debug builds only. Fires the work a developer needs from adb without
 * touching the phone's live journal:
 *
 *   adb shell am broadcast -a com.hcexport.DEBUG_SYNC \
 *       -n com.hcexport.debug/com.hcexport.DebugSyncReceiver --es mode snapshot
 */
class DebugSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getStringExtra("mode")) {
            "full" -> SyncWorker.enqueue(context)
            else -> WearPushWorker.enqueue(context)
        }
    }
}
