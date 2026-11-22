package com.hcexport

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * A live-workout notification button. It carries only an action id; the
 * running session decides what that means in its current stage.
 */
class LiveActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        LiveRemoteHub.perform(action)
    }

    companion object {
        const val ACTION_LIVE_REMOTE = "com.hcexport.action.LIVE_REMOTE"
        const val EXTRA_ACTION = "live_action"
    }
}
