package com.hcexport

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.hcexport.shared.JournalDto
import com.hcexport.shared.MergedWodDto
import com.hcexport.shared.PATH_META
import com.hcexport.shared.SNAPSHOT_SCHEMA
import com.hcexport.shared.Snapshot
import com.hcexport.shared.SnapshotCodec
import com.hcexport.shared.WorkoutDto
import com.hcexport.shared.chunkPath
import com.hcexport.shared.generationPrefix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds the phone -> watch snapshot and publishes it as chunked DataItems.
 *
 * Chunks live under an immutable generation path and the meta item is written last,
 * so the watch never sees a half-written snapshot. The payload is gzipped because a
 * year of workouts and journal entries comfortably exceeds one DataItem.
 */
object WearSnapshotPublisher {

    private const val TAG = "WearSnapshotPublisher"
    private const val MAX_NARRATIVE = 6_000
    private const val MAX_CONTENT = 12_000
    private const val MAX_MERGED = 4_000

    private val pushMutex = Mutex()

    suspend fun publish(ctx: Context): Boolean = pushMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val snapshot = build(ctx)
                val bytes = SnapshotCodec.encode(snapshot)
                val chunks = SnapshotCodec.chunk(bytes)
                val generation = maxOf(
                    System.currentTimeMillis(),
                    Prefs.getWearGeneration(ctx) + 1,
                )

                val dataClient = Wearable.getDataClient(ctx)

                for ((index, chunk) in chunks.withIndex()) {
                    val request = PutDataRequest.create(chunkPath(generation, index))
                        .setData(chunk)
                    Tasks.await(dataClient.putDataItem(request))
                }

                val meta = JSONObject().apply {
                    put("schema", SNAPSHOT_SCHEMA)
                    put("generation", generation)
                    put("chunks", chunks.size)
                    put("sha256", SnapshotCodec.sha256Hex(bytes))
                    put("updatedAt", snapshot.updatedAt)
                }
                Tasks.await(
                    dataClient.putDataItem(
                        PutDataRequest.create(PATH_META)
                            .setData(meta.toString().toByteArray(Charsets.UTF_8))
                            .setUrgent()
                    )
                )

                // Keep the current and previous generation; drop anything older.
                val previous = Prefs.getWearGeneration(ctx)
                val older = Prefs.getWearPreviousGeneration(ctx)
                Prefs.setWearPreviousGeneration(ctx, previous)
                Prefs.setWearGeneration(ctx, generation)
                if (older > 0 && older != previous && older != generation) {
                    runCatching {
                        Tasks.await(
                            dataClient.deleteDataItems(
                                Uri.parse("wear://*${generationPrefix(older)}"),
                                DataClient.FILTER_PREFIX,
                            )
                        )
                    }
                }

                Prefs.setLastWatchPushMs(ctx, System.currentTimeMillis())
                SyncLogger.log(
                    ctx,
                    "Watch snapshot sent: ${snapshot.workouts.size} workouts, " +
                        "${snapshot.journal.size} journal entries, ${chunks.size} chunk(s)",
                )
                true
            } catch (e: Exception) {
                Log.e(TAG, "Watch push failed", e)
                SyncLogger.log(ctx, "ERROR: watch push failed: ${e.message}")
                false
            }
        }
    }

    fun build(ctx: Context): Snapshot {
        val workouts = WorkoutStore.getAll(ctx).map {
            WorkoutDto(
                date = it.date,
                typeKey = it.typeKey,
                typeLabel = it.typeLabel,
                content = it.content.take(MAX_CONTENT),
                sortOrder = it.sortOrder,
            )
        }

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val journal = JournalStorage.getAllVisibleWorkoutEntries(ctx, today).map { entry ->
            val j = try { JSONObject(entry.effectiveDataJson()) } catch (_: Exception) { JSONObject() }
            val startMs = j.optLong("sm", 0L)
            val endMs = j.optLong("em", 0L)
            JournalDto(
                id = entry.id,
                date = entry.date,
                entryType = entry.entryType,
                title = entry.customTitle ?: defaultTitle(entry.entryType, j),
                narrative = entry.narrativeText().take(MAX_NARRATIVE),
                startMs = startMs,
                endMs = endMs,
                typeCode = j.optInt("tc", -1),
                distanceM = if (j.has("dm")) j.getDouble("dm") else null,
                caloriesKcal = if (j.has("ck")) j.getDouble("ck") else null,
                avgHrBpm = if (j.has("ah")) j.getDouble("ah") else null,
                maxHrBpm = if (j.has("mh")) j.getDouble("mh") else null,
                steps = if (j.has("st")) j.getLong("st") else null,
                source = if (entry.entryType == "wod") "" else
                    SourceBrands.displayName(j.optString("sp", "")),
                mergedWods = readMerged(j),
                wodContent = if (entry.entryType == "wod") {
                    j.optString("wod", "").take(MAX_CONTENT)
                } else null,
            )
        }

        return Snapshot(
            schema = SNAPSHOT_SCHEMA,
            updatedAt = System.currentTimeMillis(),
            workouts = workouts,
            journal = journal,
        )
    }

    private fun defaultTitle(entryType: String, j: JSONObject): String = when (entryType) {
        "wod" -> "WOD"
        "exercise" -> {
            val typeLabel = CalendarHelper.EXERCISE_TYPES[j.optInt("tc", -1)]?.second ?: "Workout"
            j.optString("tt", "").trim().ifBlank { typeLabel }
        }
        else -> entryType
    }

    private fun readMerged(j: JSONObject): List<MergedWodDto> {
        val arr: JSONArray = j.optJSONArray("mw") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                MergedWodDto(
                    date = o.optString("d", ""),
                    title = o.optString("t", ""),
                    content = o.optString("w", "").take(MAX_MERGED),
                )
            }.getOrNull()
        }
    }
}
