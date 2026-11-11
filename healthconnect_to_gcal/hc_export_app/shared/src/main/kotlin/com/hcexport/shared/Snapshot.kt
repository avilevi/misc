package com.hcexport.shared

/**
 * The phone -> watch payload. Everything the watch renders comes from here.
 * Lives in :shared so the phone builder and the watch parser cannot drift.
 */

const val SNAPSHOT_SCHEMA = 1

/** Data Layer paths. Chunks live under an immutable generation so a write is
 *  never visible half-done: the meta item is written last. */
const val PATH_PREFIX = "/hcexport/sync"
const val PATH_META = "$PATH_PREFIX/meta"

fun chunkPath(generation: Long, index: Int): String = "$PATH_PREFIX/generation/$generation/chunk/$index"
fun generationPrefix(generation: Long): String = "$PATH_PREFIX/generation/$generation"

data class WorkoutDto(
    val date: String,
    val typeKey: String,
    val typeLabel: String,
    val content: String,
    val sortOrder: Int,
)

data class MergedWodDto(
    val date: String,
    val title: String,
    val content: String,
)

data class JournalDto(
    val id: String,
    val date: String,
    val entryType: String,
    val title: String,
    val narrative: String,
    val startMs: Long,
    val endMs: Long,
    val typeCode: Int,
    val distanceM: Double?,
    val caloriesKcal: Double?,
    val avgHrBpm: Double?,
    val maxHrBpm: Double?,
    val steps: Long?,
    val source: String,
    val mergedWods: List<MergedWodDto>,
    val wodContent: String?,
)

data class Snapshot(
    val schema: Int = SNAPSHOT_SCHEMA,
    val updatedAt: Long,
    val workouts: List<WorkoutDto>,
    val journal: List<JournalDto>,
)
