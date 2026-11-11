package com.hcexport

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pulls the whole HYPR page into the local workout database.
 *
 * The page only keeps a few dates at a time, so every pull must add what is new and
 * never delete what has already been stored. A failed or malformed pull leaves the
 * database exactly as it was.
 */
object WorkoutPull {

    private const val TAG = "WorkoutPull"
    private const val URL_STR = "https://hypr-workouts.pages.dev/"

    data class Result(
        val ok: Boolean,
        val created: Int = 0,
        val updated: Int = 0,
        val same: Int = 0,
        val notModified: Boolean = false,
        val error: String? = null,
    )

    suspend fun refresh(ctx: Context): Result = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        try {
            val conn = URL(URL_STR).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "hc-sync-android/2.0")
            Prefs.getWorkoutEtag(ctx)?.let { conn.setRequestProperty("If-None-Match", it) }
            Prefs.getWorkoutLastModified(ctx)?.let { conn.setRequestProperty("If-Modified-Since", it) }

            try {
                val code = conn.responseCode
                if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    Prefs.setLastWorkoutPullMs(ctx, now)
                    Prefs.setLastWorkoutPullSummary(ctx, "Unchanged (HTTP 304)")
                    SyncLogger.log(ctx, "Workouts: unchanged (HTTP 304)")
                    return@withContext Result(ok = true, notModified = true)
                }
                if (code !in 200..299) {
                    throw IllegalStateException("HTTP $code")
                }

                val html = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
                val workouts = WorkoutParser.parse(html)
                if (workouts.isEmpty()) {
                    throw IllegalStateException("no workouts parsed — page format changed?")
                }

                val upsert = WorkoutStore.upsertAll(ctx, workouts, now)
                Prefs.setWorkoutEtag(ctx, conn.getHeaderField("ETag"))
                Prefs.setWorkoutLastModified(ctx, conn.getHeaderField("Last-Modified"))
                Prefs.setLastWorkoutPullMs(ctx, now)
                val total = WorkoutStore.count(ctx)
                val summary = "Workouts: ${upsert.created} new, ${upsert.updated} updated, " +
                    "${upsert.unchanged} unchanged ($total total)"
                Prefs.setLastWorkoutPullSummary(ctx, summary)
                SyncLogger.log(ctx, summary)

                Result(ok = true, created = upsert.created, updated = upsert.updated, same = upsert.unchanged)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Workout pull failed", e)
            val message = e.message ?: e::class.java.simpleName
            SyncLogger.log(ctx, "ERROR: workout pull failed: $message")
            Result(ok = false, error = message)
        }
    }

    /** Human-readable last pull, for the settings card. */
    fun lastPullLine(ctx: Context): String {
        val ms = Prefs.getLastWorkoutPullMs(ctx) ?: return "Never pulled"
        val fmt = SimpleDateFormat("EEE, dd MMM HH:mm", Locale.getDefault())
        return "Last pull: ${fmt.format(Date(ms))}"
    }
}
