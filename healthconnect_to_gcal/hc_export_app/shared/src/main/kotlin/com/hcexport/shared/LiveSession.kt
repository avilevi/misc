package com.hcexport.shared

import org.json.JSONObject

/**
 * One running live workout, shared between the phone and the watch.
 *
 * Both devices run the same state machine ([:shared]'s LiveWorkout / the
 * watch's LiveSession). Whoever changes the stage publishes this record as a
 * single DataItem; the other device adopts it and its clock continues from
 * there. Values travel in the same record, so a set logged on one device is
 * the set the other one shows.
 *
 * The record is published on stage changes and pauses, never on every tick:
 * the receiving device ticks its own clocks. A reconnecting device therefore
 * gets the *current* record from the Data Layer and can also ask for a fresh
 * one with [PATH_LIVE_REQUEST].
 */
const val PATH_LIVE_SESSION = "/hcexport/live/session"
const val PATH_LIVE_REQUEST = "/hcexport/live/request"

const val DEVICE_PHONE = "phone"
const val DEVICE_WATCH = "watch"

data class LiveSessionDto(
    val sessionId: String,
    val origin: String,
    val date: String,
    val typeKey: String,
    val open: Boolean = true,
    val startedAt: Long,
    val updatedAt: Long,
    /** The plan revision this session runs; a regenerated plan invalidates it. */
    val planUpdatedAt: Long = 0L,
    /** overview | get_ready | active | done */
    val phase: String = "overview",
    val sectionIndex: Int = 0,
    val itemIndex: Int = 0,
    val liveItemIndex: Int = 0,
    val paused: Boolean = false,
    val started: Boolean = false,
    val currentRound: Int = 0,
    val resting: Boolean = false,
    val itemDone: Boolean = false,
    val roundWorkDone: Boolean = false,
    val leadInRemainingMs: Long = 0L,
    val intervalRemainingMs: Long = 0L,
    val sectionElapsedMs: Long = 0L,
    val sessionElapsedMs: Long = 0L,
    val restRemainingMs: Long = 0L,
    val roundsDone: Int = 0,
    val lastSplitElapsedMs: Long = 0L,
    val resultsJson: String = "{}",
    /** The values this session started from; edits are the difference. */
    val baseResultsJson: String = "{}",
)

object LiveSessionCodec {

    fun encode(session: LiveSessionDto): String = JSONObject().apply {
        put("schema", SNAPSHOT_SCHEMA)
        put("id", session.sessionId)
        put("origin", session.origin)
        put("date", session.date)
        put("typeKey", session.typeKey)
        put("open", session.open)
        put("startedAt", session.startedAt)
        put("updatedAt", session.updatedAt)
        put("planUpdatedAt", session.planUpdatedAt)
        put("phase", session.phase)
        put("section", session.sectionIndex)
        put("item", session.itemIndex)
        put("liveItem", session.liveItemIndex)
        put("paused", session.paused)
        put("started", session.started)
        put("round", session.currentRound)
        put("resting", session.resting)
        put("itemDone", session.itemDone)
        put("roundWorkDone", session.roundWorkDone)
        put("leadInMs", session.leadInRemainingMs)
        put("intervalMs", session.intervalRemainingMs)
        put("sectionMs", session.sectionElapsedMs)
        put("sessionMs", session.sessionElapsedMs)
        put("restMs", session.restRemainingMs)
        put("roundsDone", session.roundsDone)
        put("splitMs", session.lastSplitElapsedMs)
        put("results", session.resultsJson)
        put("baseResults", session.baseResultsJson)
    }.toString()

    fun decode(raw: String): LiveSessionDto? = runCatching {
        val obj = JSONObject(raw)
        val date = obj.optString("date")
        val typeKey = obj.optString("typeKey")
        val id = obj.optString("id")
        if (date.length != 10 || typeKey.isBlank() || id.isBlank()) return null
        LiveSessionDto(
            sessionId = id,
            origin = obj.optString("origin", DEVICE_PHONE),
            date = date,
            typeKey = typeKey,
            open = obj.optBoolean("open", true),
            startedAt = obj.optLong("startedAt", 0L),
            updatedAt = obj.optLong("updatedAt", 0L),
            planUpdatedAt = obj.optLong("planUpdatedAt", 0L),
            phase = obj.optString("phase", "overview"),
            sectionIndex = obj.optInt("section", 0),
            itemIndex = obj.optInt("item", 0),
            liveItemIndex = obj.optInt("liveItem", 0),
            paused = obj.optBoolean("paused", false),
            started = obj.optBoolean("started", false),
            currentRound = obj.optInt("round", 0),
            resting = obj.optBoolean("resting", false),
            itemDone = obj.optBoolean("itemDone", false),
            roundWorkDone = obj.optBoolean("roundWorkDone", false),
            leadInRemainingMs = obj.optLong("leadInMs", 0L),
            intervalRemainingMs = obj.optLong("intervalMs", 0L),
            sectionElapsedMs = obj.optLong("sectionMs", 0L),
            sessionElapsedMs = obj.optLong("sessionMs", 0L),
            restRemainingMs = obj.optLong("restMs", 0L),
            roundsDone = obj.optInt("roundsDone", 0),
            lastSplitElapsedMs = obj.optLong("splitMs", 0L),
            resultsJson = obj.optString("results", "{}"),
            baseResultsJson = obj.optString("baseResults", "{}"),
        )
    }.getOrNull()
}
