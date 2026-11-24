package com.hcexport

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Pulls HYPR workouts from Fitr into the local workout database.
 *
 * The schedule list gives a day's sections and titles; the day-details call
 * gives every section's description in the same request, so a pull costs one
 * request per programmed day. The rolling window keeps recent and upcoming
 * workouts current; older rows already in the database are never touched.
 *
 * When Fitr is connected it is the only workout source: the HTML page scraper
 * is skipped.
 */
object FitrPull {

    private const val TAG = "FitrPull"
    private const val DAYS_BACK = 14L
    private const val DAYS_FORWARD = 28L

    data class Result(
        val ok: Boolean,
        val skipped: Boolean = false,
        val created: Int = 0,
        val updated: Int = 0,
        val same: Int = 0,
        val workouts: Int = 0,
        val error: String? = null,
    )

    data class Status(
        val connected: Boolean,
        val email: String?,
        val profileName: String?,
        val lastPullMs: Long?,
        val lastSummary: String?,
        val lastError: String?,
    )

    fun status(ctx: Context): Status = Status(
        connected = FitrSecretStore.hasCredentials(ctx),
        email = FitrSecretStore.getEmail(ctx),
        profileName = Prefs.getFitrProfileName(ctx),
        lastPullMs = Prefs.getLastFitrPullMs(ctx),
        lastSummary = Prefs.getFitrLastSummary(ctx),
        lastError = Prefs.getFitrLastError(ctx),
    )

    /** Validate and store a login. Returns the error message, or null on success. */
    suspend fun connect(ctx: Context, email: String, password: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val session = FitrApi.login(email.trim(), password)
                FitrSecretStore.setCredentials(ctx, email, password)
                FitrSecretStore.setAuth(ctx, session.auth)
                session.name?.let { Prefs.setFitrProfileName(ctx, it) }
                Prefs.setFitrLastError(ctx, null)
                null
            } catch (e: Exception) {
                Log.e(TAG, "connect failed", e)
                e.message ?: e::class.java.simpleName
            }
        }

    suspend fun refresh(
        ctx: Context,
        from: LocalDate = LocalDate.now().minusDays(DAYS_BACK),
        to: LocalDate = LocalDate.now().plusDays(DAYS_FORWARD),
    ): Result = withContext(Dispatchers.IO) {
        if (!FitrSecretStore.hasCredentials(ctx)) {
            return@withContext Result(ok = true, skipped = true)
        }
        try {
            val pulled = withAuth(ctx) { auth -> pullRange(auth, from, to.toString()) }
            val upsert = WorkoutStore.upsertAll(ctx, pulled.rows)
            pulled.videos.forEach { (key, videos) ->
                WorkoutStore.replaceVideos(ctx, key.first, key.second, videos)
            }

            val now = System.currentTimeMillis()
            val summary = buildString {
                append("Fitr: ${pulled.rows.size} workouts ")
                append("(${upsert.created} new, ${upsert.updated} updated)")
                if (pulled.errors.isNotEmpty()) {
                    append(", ${pulled.errors.size} day(s) failed")
                }
            }
            Prefs.setLastFitrPullMs(ctx, now)
            Prefs.setFitrLastSummary(ctx, summary)
            Prefs.setFitrLastError(ctx, pulled.errors.firstOrNull())
            SyncLogger.log(ctx, summary)
            pulled.errors.take(3).forEach { SyncLogger.log(ctx, "ERROR: Fitr: $it") }

            Result(
                ok = pulled.errors.isEmpty(),
                created = upsert.created,
                updated = upsert.updated,
                same = upsert.unchanged,
                workouts = pulled.rows.size,
                error = pulled.errors.firstOrNull(),
            )
        } catch (e: Exception) {
            Log.e(TAG, "pull failed", e)
            val message = e.message ?: e::class.java.simpleName
            Prefs.setFitrLastError(ctx, message)
            SyncLogger.log(ctx, "ERROR: Fitr pull failed: $message")
            Result(ok = false, error = message)
        }
    }

    /**
     * Pull every day since [since]. One details request per programmed day, in
     * monthly chunks so progress is visible and the token is minted once.
     */
    suspend fun backfill(
        ctx: Context,
        since: LocalDate,
        onProgress: ((String) -> Unit)? = null,
    ): Result = withContext(Dispatchers.IO) {
        if (!FitrSecretStore.hasCredentials(ctx)) {
            return@withContext Result(ok = true, skipped = true)
        }
        try {
            val end = LocalDate.now().plusDays(DAYS_FORWARD)
            var created = 0
            var updated = 0
            var same = 0
            var total = 0
            val errors = mutableListOf<String>()

            withAuth(ctx) { auth ->
                var cursor = since
                while (!cursor.isAfter(end)) {
                    val chunkEnd = minOf(cursor.plusMonths(1).minusDays(1), end)
                    onProgress?.invoke("Fitr backfill: $cursor → $chunkEnd")
                    val pulled = pullRange(auth, cursor, chunkEnd.toString())
                    errors += pulled.errors
                    val upsert = WorkoutStore.upsertAll(ctx, pulled.rows)
                    pulled.videos.forEach { (key, videos) ->
                        WorkoutStore.replaceVideos(ctx, key.first, key.second, videos)
                    }
                    created += upsert.created
                    updated += upsert.updated
                    same += upsert.unchanged
                    total += pulled.rows.size
                    cursor = chunkEnd.plusDays(1)
                }
            }

            val summary = "Fitr backfill: $total workouts ($created new, $updated updated)"
            Prefs.setLastFitrPullMs(ctx, System.currentTimeMillis())
            Prefs.setFitrLastSummary(ctx, summary)
            Prefs.setFitrLastError(ctx, errors.firstOrNull())
            SyncLogger.log(ctx, summary)

            Result(
                ok = errors.isEmpty(),
                created = created,
                updated = updated,
                same = same,
                workouts = total,
                error = errors.firstOrNull(),
            )
        } catch (e: Exception) {
            Log.e(TAG, "backfill failed", e)
            val message = e.message ?: e::class.java.simpleName
            Prefs.setFitrLastError(ctx, message)
            SyncLogger.log(ctx, "ERROR: Fitr backfill failed: $message")
            Result(ok = false, error = message)
        }
    }

    // ── Fetch ──────────────────────────────────────────────────────────────

    private data class Pulled(
        val rows: List<Workout>,
        /** date|type -> demo videos, including empty lists for types with none. */
        val videos: Map<Pair<String, String>, List<WorkoutVideo>>,
        val errors: List<String>,
    )

    private fun pullRange(auth: String, from: LocalDate, to: String): Pulled {
        val root = JSONObject(FitrApi.schedules(auth, from.toString(), to))
        val schedules = root.optJSONArray("schedules") ?: JSONArray()
        val rows = mutableListOf<Workout>()
        val videos = LinkedHashMap<Pair<String, String>, List<WorkoutVideo>>()
        val errors = mutableListOf<String>()

        for (i in 0 until schedules.length()) {
            val entry = schedules.optJSONObject(i) ?: continue
            val date = Instant.ofEpochSecond(entry.optLong("date"))
                .atZone(ZoneOffset.UTC).toLocalDate().toString()
            val plan = entry.optJSONObject("plan")
            val planId = plan?.optInt("id") ?: 0
            val trackId = plan?.optInt("default_track_id") ?: 0

            val details = try {
                if (planId <= 0) null else JSONObject(FitrApi.dayDetails(auth, date, planId, trackId))
            } catch (e: Exception) {
                errors += "$date: ${e.message ?: e::class.java.simpleName}"
                null
            }
            val sectionsJson = details?.optJSONArray("sections")
                ?: entry.optJSONArray("sections")
                ?: continue

            val sections = parseSections(sectionsJson)
            val dayRows = FitrWorkouts.rowsFor(date, sections)
            rows += dayRows
            val dayVideos = FitrWorkouts.videosFor(sections)
            for (row in dayRows) {
                videos[date to row.typeKey] = dayVideos[row.typeKey].orEmpty()
            }
        }
        return Pulled(rows, videos, errors)
    }

    private fun parseSections(arr: JSONArray): List<FitrSection> {
        val out = mutableListOf<FitrSection>()
        for (i in 0 until arr.length()) {
            val sec = arr.optJSONObject(i) ?: continue
            out += FitrSection(
                id = sec.optInt("id"),
                title = sec.optString("title"),
                description = sec.optString("description"),
                position = sec.optInt("position", i),
                attachments = parseAttachments(sec.optJSONArray("attachments")),
            )
        }
        return out
    }

    private fun parseAttachments(arr: JSONArray?): List<FitrAttachment> {
        if (arr == null) return emptyList()
        val out = mutableListOf<FitrAttachment>()
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: continue
            // org.json turns JSON null into the string "null"; treat it as absent.
            fun text(key: String): String? = a.optString(key)
                .takeIf { it.isNotBlank() && it != "null" }
            out += FitrAttachment(
                title = text("title") ?: "Movement demo",
                kind = text("type") ?: "video",
                videoId = text("video_id"),
                url = text("stream_src") ?: text("src"),
                thumbUrl = text("thumb"),
                position = a.optInt("position", i),
            )
        }
        return out
    }

    private fun <T> withAuth(ctx: Context, block: (String) -> T): T {
        val auth = FitrSecretStore.getAuth(ctx) ?: loginAndStore(ctx)
        return try {
            block(auth)
        } catch (e: FitrApi.AuthException) {
            Log.w(TAG, "token rejected, logging in again", e)
            FitrSecretStore.clearSession(ctx)
            block(loginAndStore(ctx))
        }
    }

    private fun loginAndStore(ctx: Context): String {
        val email = FitrSecretStore.getEmail(ctx)
        val password = FitrSecretStore.getPassword(ctx)
        if (email.isNullOrBlank() || password.isNullOrBlank()) {
            throw FitrApi.AuthException("Fitr credentials are missing")
        }
        val session = FitrApi.login(email, password)
        FitrSecretStore.setAuth(ctx, session.auth)
        session.name?.let { Prefs.setFitrProfileName(ctx, it) }
        return session.auth
    }
}
