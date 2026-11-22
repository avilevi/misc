package com.hcexport.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.hcexport.shared.PATH_LIVE_REQUEST
import com.hcexport.shared.PATH_LIVE_SESSION
import com.hcexport.shared.LiveSessionCodec
import com.hcexport.wear.live.WatchLiveSync
import org.json.JSONObject

/**
 * News from the phone while the watch app is not open: the live session
 * record (so a workout started on the phone can be joined) and sync requests.
 */
class WatchLiveService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_DELETED) continue
            val item = event.dataItem
            if (item.uri.path != PATH_LIVE_SESSION) continue
            val bytes = item.data ?: continue
            val session = LiveSessionCodec.decode(String(bytes, Charsets.UTF_8)) ?: continue
            WatchLiveSync.onRemote(this, session)
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != PATH_LIVE_REQUEST) return
        val payload = runCatching {
            JSONObject(String(messageEvent.data, Charsets.UTF_8))
        }.getOrNull() ?: return
        when (payload.optString("type")) {
            "sync.request" -> WatchLiveSync.respondToPhone(this, messageEvent.sourceNodeId)
            "sync.none" -> WatchLiveSync.onPhoneNoWorkout()
        }
    }
}
