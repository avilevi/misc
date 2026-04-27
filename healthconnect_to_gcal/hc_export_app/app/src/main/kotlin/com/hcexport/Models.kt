package com.hcexport

data class ExerciseEvent(
    val startMs: Long,
    val endMs: Long,
    val typeCode: Int,
    val title: String,
    val distanceM: Double?,
    val caloriesKcal: Double?,
    val avgHrBpm: Double?,
    val maxHrBpm: Double?,
    val notes: String,
)

data class SleepEvent(
    val startMs: Long,
    val endMs: Long,
    val stages: List<SleepStage>,
)

data class SleepStage(
    val startMs: Long,
    val endMs: Long,
    val stageCode: Int,
)
