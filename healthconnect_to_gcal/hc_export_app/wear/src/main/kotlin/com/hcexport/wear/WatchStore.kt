package com.hcexport.wear

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.hcexport.shared.PATH_META
import com.hcexport.shared.PATH_PREFIX
import com.hcexport.shared.Snapshot
import com.hcexport.shared.SnapshotCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * The watch's copy of the phone snapshot.
 *
 * DataItems are replicated to the watch by the Data Layer, so a launch can rebuild
 * the latest snapshot even with the phone absent. The decoded payload is also cached
 * on disk, which makes the app usable with no Bluetooth at all.
 */
object WatchStore {

    private const val CACHE_NAME = "snapshot.bin"

    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    val snapshot: StateFlow<Snapshot?> = _snapshot

    fun loadCache(ctx: Context) {
        val file = File(ctx.filesDir, CACHE_NAME)
        if (!file.exists()) return
        runCatching { SnapshotCodec.decode(file.readBytes()) }
            .onSuccess { _snapshot.value = it }
    }

    suspend fun refresh(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = Wearable.getDataClient(ctx)
            val buffer = Tasks.await(client.dataItems)
            var metaJson: JSONObject? = null
            val chunks = mutableMapOf<Pair<Long, Int>, ByteArray>()
            try {
                for (item in buffer) {
                    val path = item.uri.path ?: continue
                    val bytes = item.data ?: continue
                    if (path == PATH_META) {
                        metaJson = JSONObject(String(bytes, Charsets.UTF_8))
                        continue
                    }
                    val prefix = "$PATH_PREFIX/generation/"
                    if (!path.startsWith(prefix)) continue
                    val parts = path.removePrefix(prefix).split('/')
                    if (parts.size != 3 || parts[1] != "chunk") continue
                    val gen = parts[0].toLongOrNull() ?: continue
                    val index = parts[2].toIntOrNull() ?: continue
                    chunks[gen to index] = bytes
                }
            } finally {
                buffer.release()
            }

            val meta = metaJson ?: return@withContext false
            val generation = meta.getLong("generation")
            val count = meta.getInt("chunks")
            val hash = meta.getString("sha256")
            val ordered = (0 until count).map { chunks[generation to it] ?: return@withContext false }
            val bytes = SnapshotCodec.join(ordered)
            if (SnapshotCodec.sha256Hex(bytes) != hash) return@withContext false

            val snapshot = SnapshotCodec.decode(bytes)
            writeCache(ctx, bytes)
            _snapshot.value = snapshot
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun writeCache(ctx: Context, bytes: ByteArray) {
        val file = File(ctx.filesDir, CACHE_NAME)
        val tmp = File(ctx.filesDir, "$CACHE_NAME.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(file)) {
            file.writeBytes(bytes)
            tmp.delete()
        }
    }
}
