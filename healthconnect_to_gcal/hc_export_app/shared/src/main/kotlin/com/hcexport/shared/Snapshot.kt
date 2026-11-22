package com.hcexport.shared

/**
 * The phone -> watch payload. Everything the watch renders comes from here.
 * Lives in :shared so the phone builder and the watch parser cannot drift.
 *
 * Schema 2 carries the phone's rendered journal (the same days and items the
 * phone's Journal tab shows) plus the AI plans the watch can run live. The
 * plan JSON is the identical document the phone stores in interactive.db, so
 * a live session on the watch writes the same scopes and results as typing
 * the values on the phone.
 */

const val SNAPSHOT_SCHEMA = 2

/** Data Layer paths. Chunks live under an immutable generation so a write is
 *  never visible half-done: the meta item is written last. */
const val PATH_PREFIX = "/hcexport/sync"
const val PATH_META = "$PATH_PREFIX/meta"

/** Watch -> phone live results. One DataItem per workout, rewritten as the
 *  session commits values; the Data Layer queues it while Bluetooth is away. */
const val RESULTS_PREFIX = "/hcexport/results"

fun chunkPath(generation: Long, index: Int): String = "$PATH_PREFIX/generation/$generation/chunk/$index"
fun generationPrefix(generation: Long): String = "$PATH_PREFIX/generation/$generation"

fun resultsPath(date: String, typeKey: String): String = "$RESULTS_PREFIX/$date/$typeKey"
fun isResultsPath(path: String): Boolean = path.startsWith("$RESULTS_PREFIX/")

/** How the watch should run a live session, copied from the phone's settings. */
data class LiveSettingsDto(
    val leadInSeconds: Int = 10,
    val intervalSeconds: Int = 90,
    val sound: Boolean = true,
    val vibration: Boolean = true,
)

/** A headline number on a journal card: "45m" / "DURATION". */
data class StatDto(
    val label: String,
    val value: String,
)

/** One rendered journal entry. [kind] is "workout", "activity" or "sleep". */
data class JournalItemDto(
    val key: String,
    val date: String,
    val kind: String,
    val title: String,
    /** Two-line card summary. */
    val summary: String,
    /** Detail text (the diary narrative), blank for programmed workouts. */
    val body: String,
    /** Programmed workouts only; blank for activities and nights. */
    val typeKey: String,
    val typeLabel: String,
    val startMs: Long,
    val endMs: Long,
    val stats: List<StatDto>,
    val note: String,
    val scheduled: Boolean,
    /** The entry is dated after today: it is still ahead of the athlete. */
    val planned: Boolean,
    val hasPlan: Boolean,
    val planSummary: String,
    val planTotalSets: Int,
    val planLoggedSets: Int,
    val hasWatchData: Boolean,
    /** The stored workout text, for a workout with no AI plan. */
    val content: String,
)

data class JournalDayDto(
    val date: String,
    val items: List<JournalItemDto>,
)

/** An AI plan and the values logged into it, keyed by workout date + type. */
data class PlanDto(
    val date: String,
    val typeKey: String,
    val planJson: String,
    val resultsJson: String,
    val model: String,
    val updatedAt: Long,
)

data class Snapshot(
    val schema: Int = SNAPSHOT_SCHEMA,
    val updatedAt: Long,
    val days: List<JournalDayDto>,
    val plans: List<PlanDto>,
    val settings: LiveSettingsDto = LiveSettingsDto(),
)
