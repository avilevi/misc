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
        if (intent.action == ACTION_LIVE_DISMISSED) {
            // Swiped away by hand: the next reminder is a full minute from now.
            LiveRemoteHub.noteStepDismissed()
            return
        }
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        LiveRemoteHub.perform(action)
    }

    companion object {
        const val ACTION_LIVE_REMOTE = "com.hcexport.action.LIVE_REMOTE"
        const val ACTION_LIVE_DISMISSED = "com.hcexport.action.LIVE_DISMISSED"
        const val EXTRA_ACTION = "live_action"
    }
}
