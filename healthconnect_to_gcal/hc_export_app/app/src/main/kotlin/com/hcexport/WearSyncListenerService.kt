package com.hcexport

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.hcexport.shared.DEVICE_WATCH
import com.hcexport.shared.LiveResultCodec
import com.hcexport.shared.LiveSessionCodec
import com.hcexport.shared.PATH_LIVE_REQUEST
import com.hcexport.shared.PATH_LIVE_SESSION
import com.hcexport.shared.isResultsPath
import com.hcexport.ui.DataRefresh
import org.json.JSONObject

/**
 * What the phone hears from the watch even when the app is not open.
 *
 * Two kinds of news arrive here:
 *  - live values, written as `/hcexport/results/<date>/<type>` while a watch
 *    session commits sets, and
 *  - the live session record itself, which tells the phone where the workout
 *    is so its own live screen can join at the same stage.
 *
 * Requests ("sync from watch") use messages because they are only meaningful
 * while both devices are connected.
 */
class WearSyncListenerService : WearableListenerService() {

    private companion object {
        const val TAG = "WearSyncListener"
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_DELETED) continue
            val item = event.dataItem
            val path = item.uri.path ?: continue
            val bytes = item.data ?: continue
            when {
                isResultsPath(path) -> handleResults(String(bytes, Charsets.UTF_8))
                path == PATH_LIVE_SESSION -> handleSession(String(bytes, Charsets.UTF_8))
            }
        }
    }

    private fun handleResults(raw: String) {
        val result = LiveResultCodec.decode(raw) ?: return
        val record = runCatching { InteractiveStore.get(this, result.date, result.typeKey) }
            .getOrNull() ?: return
        val plan = InteractivePlan.fromJson(record.planJson) ?: return
        // Values keyed to a plan that has since been regenerated are not the
        // same workout any more.
        if (result.planUpdatedAt > 0 && record.createdAtMs > result.planUpdatedAt) {
            SyncLogger.log(
                this,
                "Watch live values ignored: plan regenerated " +
                    "(${result.date} ${result.typeKey})",
            )
            return
        }
        // Apply only the fields the watch changed since its own starting
        // point. A watch carrying a stale snapshot therefore cannot erase a
        // value the phone typed after that snapshot was made.
        val stored = InteractiveResults.fromJson(record.resultsJson)
        val merged = InteractiveResults.mergeChanges(
            into = stored,
            base = InteractiveResults.fromJson(result.baseResultsJson),
            current = InteractiveResults.fromJson(result.resultsJson),
        )
        runCatching {
            InteractiveStore.saveResults(
                this, result.date, result.typeKey,
                InteractiveResults.toJson(merged),
            )
            RecordStore.syncAutoWeights(
                this, result.date, result.typeKey,
                AutoRecords.candidates(plan, merged),
            )
            DataRefresh.bump()
            SyncLogger.log(
                this,
                "Watch live values: ${result.date} ${result.typeKey} " +
                    "(${merged.values.sumOf { it.size }} fields)",
            )
        }.onFailure { Log.e(TAG, "Could not store watch live values", it) }
    }

    private fun handleSession(raw: String) {
        val session = LiveSessionCodec.decode(raw) ?: return
        if (session.origin != DEVICE_WATCH) return
        LiveSessionHub.onRemote(this, session)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != PATH_LIVE_REQUEST) return
        val payload = runCatching {
            JSONObject(String(messageEvent.data, Charsets.UTF_8))
        }.getOrNull() ?: return
        when (payload.optString("type")) {
            "sync.request" -> {
                // The watch asked where the phone's workout is. Answer on the
                // node the question came from; a DataItem would do, but a
                // message lets the watch report "phone has no workout".
                runCatching {
                    LiveSessionHub.respondToWatch(this, messageEvent.sourceNodeId)
                }
            }
            "sync.none" -> LiveSessionHub.onWatchHasNoWorkout()
        }
    }
}
