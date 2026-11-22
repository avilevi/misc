package com.hcexport

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Pulls BridgeAthletic workouts into the local workout database.
 *
 * The roster (assignments) is small and fetched on every sync. The program template
 * — every workout's blocks and prescribed sets — is 16 MB of JSON (650 KB gzipped),
 * so it is fetched at most once a week and reduced to a small text cache; the sync
 * composes each day's row from that cache.
 */
object BridgePull {

    private const val TAG = "BridgePull"
    private const val TEMPLATE_TTL_MS = 7L * 24 * 3600 * 1000
    private const val MIN_SESSION_MS = 24L * 3600 * 1000
    private const val CACHE_DIR = "bridge"

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
        val workouts: Int,
    )

    fun status(ctx: Context): Status = Status(
        connected = BridgeSecretStore.hasCredentials(ctx),
        email = BridgeSecretStore.getEmail(ctx),
        profileName = Prefs.getBridgeProfileName(ctx),
        lastPullMs = Prefs.getLastBridgePullMs(ctx),
        lastSummary = Prefs.getBridgeLastSummary(ctx),
        lastError = Prefs.getBridgeLastError(ctx),
        workouts = WorkoutStore.countType(ctx, BridgeAthletic.TYPE_KEY),
    )

    /** Validate and store a login. Returns the error message, or null on success. */
    suspend fun connect(ctx: Context, email: String, password: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val session = BridgeApi.login(email.trim(), password)
                BridgeSecretStore.setCredentials(ctx, email, password)
                BridgeSecretStore.setSession(ctx, session.jwt, session.expiresAtMs)
                Prefs.setBridgeLastError(ctx, null)
                null
            } catch (e: Exception) {
                Log.e(TAG, "connect failed", e)
                e.message ?: e::class.java.simpleName
            }
        }

    suspend fun refresh(ctx: Context, forceTemplate: Boolean = false): Result =
        withContext(Dispatchers.IO) {
            if (!BridgeSecretStore.hasCredentials(ctx)) {
                return@withContext Result(ok = true, skipped = true)
            }
            try {
                val data = withAuth(ctx) { session -> fetch(ctx, session, forceTemplate) }

                val workouts = data.rows.map { row ->
                    val text = data.blocks[row.programId]?.get(row.workoutId)
                    Workout(
                        date = row.date,
                        typeKey = BridgeAthletic.TYPE_KEY,
                        typeLabel = BridgeAthletic.TYPE_LABEL,
                        content = BridgeAthletic.render(row, text),
                        sortOrder = 0,
                    )
                }
                val upsert = WorkoutStore.upsertAll(ctx, workouts)

                val now = System.currentTimeMillis()
                val summary = "BridgeAthletic: ${workouts.size} workouts " +
                    "(${upsert.created} new, ${upsert.updated} updated)"
                Prefs.setLastBridgePullMs(ctx, now)
                Prefs.setBridgeLastSummary(ctx, summary)
                data.profileName?.let { Prefs.setBridgeProfileName(ctx, it) }
                Prefs.setBridgeLastError(ctx, data.templateErrors.firstOrNull())
                SyncLogger.log(ctx, summary)
                data.templateErrors.forEach { SyncLogger.log(ctx, "ERROR: BridgeAthletic template: $it") }

                Result(
                    ok = data.templateErrors.isEmpty(),
                    created = upsert.created,
                    updated = upsert.updated,
                    same = upsert.unchanged,
                    workouts = workouts.size,
                    error = data.templateErrors.firstOrNull(),
                )
            } catch (e: Exception) {
                Log.e(TAG, "pull failed", e)
                val message = e.message ?: e::class.java.simpleName
                Prefs.setBridgeLastError(ctx, message)
                SyncLogger.log(ctx, "ERROR: BridgeAthletic pull failed: $message")
                Result(ok = false, error = message)
            }
        }

    // ── Fetch ──────────────────────────────────────────────────────────────

    private data class Pulled(
        val rows: List<BridgeAthletic.RosterRow>,
        val blocks: Map<Int, Map<Int, String>>,
        val profileName: String?,
        val templateErrors: List<String>,
    )

    private fun fetch(ctx: Context, session: BridgeApi.Session, forceTemplate: Boolean): Pulled {
        val profile = BridgeApi.profile(session.jwt, session.userId)

        val perTeam = profile.teams.map { team ->
            team to BridgeAthletic.parseRoster(
                BridgeApi.teamAssignments(session.jwt, team.organizationId, team.id, session.userId),
            )
        }
        val programOrg = mutableMapOf<Int, Int>()
        for ((team, roster) in perTeam) {
            roster.programIds.forEach { programOrg.putIfAbsent(it, team.organizationId) }
        }
        val rows = perTeam.flatMap { it.second.rows }
            .distinctBy { it.workoutHistoryId }
            .sortedBy { it.date }

        val blocks = mutableMapOf<Int, Map<Int, String>>()
        val templateErrors = mutableListOf<String>()
        for ((programId, orgId) in programOrg) {
            try {
                blocks[programId] = loadBlocks(ctx, session.jwt, orgId, programId, forceTemplate)
            } catch (e: Exception) {
                templateErrors += "program $programId: ${e.message ?: e::class.java.simpleName}"
            }
        }

        return Pulled(rows, blocks, profile.fullName, templateErrors)
    }

    private fun withAuth(ctx: Context, block: (BridgeApi.Session) -> Pulled): Pulled {
        val session = currentSession(ctx, forceLogin = false)
        return try {
            block(session)
        } catch (e: BridgeApi.AuthException) {
            Log.w(TAG, "session rejected, logging in again", e)
            BridgeSecretStore.clearSession(ctx)
            block(currentSession(ctx, forceLogin = true))
        }
    }

    private fun currentSession(ctx: Context, forceLogin: Boolean): BridgeApi.Session {
        val cached = if (forceLogin) null else BridgeSecretStore.getJwt(ctx)
        val expiry = BridgeSecretStore.getJwtExpiryMs(ctx)
        if (cached != null && expiry - System.currentTimeMillis() > MIN_SESSION_MS) {
            return BridgeApi.Session(cached, BridgeApi.jwtClaims(cached).optInt("id"), expiry)
        }
        val email = BridgeSecretStore.getEmail(ctx)
        val password = BridgeSecretStore.getPassword(ctx)
        if (email.isNullOrBlank() || password.isNullOrBlank()) {
            throw BridgeApi.AuthException("BridgeAthletic credentials are missing")
        }
        val session = BridgeApi.login(email, password)
        BridgeSecretStore.setSession(ctx, session.jwt, session.expiresAtMs)
        return session
    }

    // ── Template cache ─────────────────────────────────────────────────────

    /** workoutId -> rendered block text, cached on disk for a week. */
    private fun loadBlocks(
        ctx: Context,
        jwt: String,
        orgId: Int,
        programId: Int,
        force: Boolean,
    ): Map<Int, String> {
        val cache = File(cacheDir(ctx), "blocks_v2_$programId.json")
        // The v1 cache was written before warm-up blocks were filtered out.
        File(cacheDir(ctx), "blocks_$programId.json").delete()
        if (!force && cache.isFile) {
            val age = System.currentTimeMillis() - cache.lastModified()
            if (age in 0..TEMPLATE_TTL_MS) {
                readBlocksCache(cache)?.let { return it }
            }
        }

        val template = BridgeAthletic.parseTemplate(BridgeApi.program(jwt, orgId, programId))
        val rendered = template.workouts.mapValues { (_, workout) -> BridgeAthletic.blocksText(workout) }
        writeBlocksCache(cache, programId, template.updatedAt, rendered)
        return rendered
    }

    private fun readBlocksCache(file: File): Map<Int, String>? = try {
        val root = JSONObject(file.readText(Charsets.UTF_8))
        val blocks = root.optJSONObject("blocks") ?: JSONObject()
        blocks.keys().asSequence().mapNotNull { key ->
            val workoutId = key.toIntOrNull() ?: return@mapNotNull null
            workoutId to blocks.optString(key)
        }.toMap()
    } catch (e: Exception) {
        Log.w(TAG, "unreadable blocks cache, refetching", e)
        null
    }

    private fun writeBlocksCache(
        file: File,
        programId: Int,
        updatedAt: String?,
        blocks: Map<Int, String>,
    ) {
        try {
            val obj = JSONObject().apply {
                put("programId", programId)
                put("updatedAt", updatedAt ?: "")
                put("blocks", JSONObject().apply {
                    blocks.forEach { (id, text) -> put(id.toString(), text) }
                })
            }
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(obj.toString(), Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            Log.w(TAG, "could not cache program template", e)
        }
    }

    private fun cacheDir(ctx: Context): File = File(ctx.filesDir, CACHE_DIR)
}
