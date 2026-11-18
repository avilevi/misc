package com.hcexport

import java.time.LocalDate

/** One achieved weight, for a specific rep maximum (1RM, 2RM or 3RM). */
data class WeightRecord(
    val id: Long = 0L,
    val exerciseKey: String,
    val exerciseName: String,
    val reps: Int,
    val weightKg: Double,
    val achievedDate: String,
    val workoutDate: String? = null,
    val workoutTypeKey: String? = null,
    val note: String? = null,
    val createdAtMs: Long = 0L,
)

enum class BenchmarkScoreType(val key: String, val label: String, val lowerIsBetter: Boolean) {
    TIME("time", "For time", true),
    REPS("reps", "For reps", false),
    ROUNDS_REPS("rounds_reps", "Rounds + reps", false),
    LOAD("load", "For load", false),
    POINTS("points", "For points", false),
    OTHER("other", "Other", false);

    companion object {
        fun fromKey(key: String?): BenchmarkScoreType =
            entries.firstOrNull { it.key == key } ?: OTHER
    }
}

/** One result for a named benchmark (Fran, Murph, Cindy, or anything custom). */
data class BenchmarkRecord(
    val id: Long = 0L,
    val benchmarkKey: String,
    val benchmarkName: String,
    val scoreType: BenchmarkScoreType,
    val scoreValue: Double = 0.0,   // seconds | reps | kg | points; rounds for ROUNDS_REPS
    val scoreValue2: Double = 0.0,  // reps for ROUNDS_REPS
    val scoreText: String = "",
    val achievedDate: String,
    val workoutDate: String? = null,
    val workoutTypeKey: String? = null,
    val note: String? = null,
    val createdAtMs: Long = 0L,
)

data class Exercise(val key: String, val name: String)

data class BenchmarkDefinition(
    val key: String,
    val name: String,
    val scoreType: BenchmarkScoreType,
)

data class WeightTableRow(
    val exerciseKey: String,
    val exerciseName: String,
    val bestByReps: Map<Int, WeightRecord>,
    val totalRecords: Int,
)

object ExerciseLibrary {
    val common: List<Exercise> = listOf(
        "Back Squat",
        "Front Squat",
        "Deadlift",
        "Sumo Deadlift",
        "Romanian Deadlift",
        "Bench Press",
        "Strict Press",
        "Push Press",
        "Push Jerk",
        "Split Jerk",
        "Clean",
        "Power Clean",
        "Clean & Jerk",
        "Snatch",
        "Power Snatch",
        "Overhead Squat",
        "Thruster",
        "Weighted Pull-Up",
    ).map { Exercise(RecordLogic.exerciseKey(it), it) }
}

object BenchmarkLibrary {
    val common: List<BenchmarkDefinition> = listOf(
        BenchmarkDefinition("fran", "Fran", BenchmarkScoreType.TIME),
        BenchmarkDefinition("murph", "Murph", BenchmarkScoreType.TIME),
        BenchmarkDefinition("grace", "Grace", BenchmarkScoreType.TIME),
        BenchmarkDefinition("isabel", "Isabel", BenchmarkScoreType.TIME),
        BenchmarkDefinition("diane", "Diane", BenchmarkScoreType.TIME),
        BenchmarkDefinition("helen", "Helen", BenchmarkScoreType.TIME),
        BenchmarkDefinition("karen", "Karen", BenchmarkScoreType.TIME),
        BenchmarkDefinition("annie", "Annie", BenchmarkScoreType.TIME),
        BenchmarkDefinition("angie", "Angie", BenchmarkScoreType.TIME),
        BenchmarkDefinition("elizabeth", "Elizabeth", BenchmarkScoreType.TIME),
        BenchmarkDefinition("jackie", "Jackie", BenchmarkScoreType.TIME),
        BenchmarkDefinition("amanda", "Amanda", BenchmarkScoreType.TIME),
        BenchmarkDefinition("nancy", "Nancy", BenchmarkScoreType.TIME),
        BenchmarkDefinition("eva", "Eva", BenchmarkScoreType.TIME),
        BenchmarkDefinition("kelly", "Kelly", BenchmarkScoreType.TIME),
        BenchmarkDefinition("cindy", "Cindy", BenchmarkScoreType.ROUNDS_REPS),
        BenchmarkDefinition("fight_gone_bad", "Fight Gone Bad", BenchmarkScoreType.POINTS),
        BenchmarkDefinition("max_pull_ups", "Max Pull-Ups", BenchmarkScoreType.REPS),
        BenchmarkDefinition("max_double_unders", "Max Double-Unders", BenchmarkScoreType.REPS),
    )
}

object RecordLogic {

    /** Stable identity for an exercise name. Display keeps the user's spelling. */
    fun exerciseKey(name: String): String =
        name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "other" }

    fun benchmarkKey(name: String): String = exerciseKey(name)

    fun bestWeight(records: List<WeightRecord>): WeightRecord? =
        records.sortedWith(
            compareByDescending<WeightRecord> { it.weightKg }
                .thenBy { it.achievedDate }
                .thenBy { it.createdAtMs }
        ).firstOrNull()

    fun weightRows(records: List<WeightRecord>): List<WeightTableRow> =
        records.groupBy { it.exerciseKey }
            .map { (key, group) ->
                val latest = group.maxWithOrNull(
                    compareBy<WeightRecord> { it.achievedDate }.thenBy { it.createdAtMs }
                )
                WeightTableRow(
                    exerciseKey = key,
                    exerciseName = latest?.exerciseName ?: key,
                    bestByReps = (1..3).mapNotNull { reps ->
                        bestWeight(group.filter { it.reps == reps })?.let { reps to it }
                    }.toMap(),
                    totalRecords = group.size,
                )
            }
            .sortedBy { it.exerciseName.lowercase() }

    fun weightHistory(records: List<WeightRecord>): List<WeightRecord> =
        records.sortedWith(
            compareByDescending<WeightRecord> { it.achievedDate }
                .thenByDescending { it.createdAtMs }
        )

    /**
     * Every rep max the detail page should show: the canonical 1/2/3RM first,
     * then any custom rep max the user has recorded (5RM, 10RM, ...).
     */
    fun allReps(records: List<WeightRecord>): List<Int> =
        (listOf(1, 2, 3) + records.map { it.reps })
            .distinct()
            .filter { it >= 1 }
            .sorted()

    fun compareBenchmarks(
        a: BenchmarkRecord,
        b: BenchmarkRecord,
        type: BenchmarkScoreType,
    ): Int = when (type) {
        BenchmarkScoreType.TIME -> a.scoreValue.compareTo(b.scoreValue)       // lowest wins
        BenchmarkScoreType.ROUNDS_REPS ->
            compareValuesBy(b, a, { it.scoreValue }, { it.scoreValue2 })     // most rounds, then reps
        BenchmarkScoreType.REPS,
        BenchmarkScoreType.LOAD,
        BenchmarkScoreType.POINTS -> b.scoreValue.compareTo(a.scoreValue)     // highest wins
        BenchmarkScoreType.OTHER -> 0
    }

    fun bestBenchmark(records: List<BenchmarkRecord>): BenchmarkRecord? {
        if (records.isEmpty()) return null
        val type = benchmarkHistory(records).first().scoreType
        if (type == BenchmarkScoreType.OTHER) return benchmarkHistory(records).first()
        return records.filter { it.scoreType == type }
            .sortedWith { a, b ->
                val byScore = compareBenchmarks(a, b, type)
                if (byScore != 0) byScore
                else compareValuesBy(a, b, { it.achievedDate }, { it.createdAtMs })
            }
            .firstOrNull()
    }

    fun benchmarkHistory(records: List<BenchmarkRecord>): List<BenchmarkRecord> =
        records.sortedWith(
            compareByDescending<BenchmarkRecord> { it.achievedDate }
                .thenByDescending { it.createdAtMs }
        )

    /** One decimal at most, no trailing ".0". */
    fun formatWeight(kg: Double): String {
        val rounded = Math.round(kg * 10.0) / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
        else String.format(java.util.Locale.US, "%.1f", rounded)
    }

    fun formatDuration(totalSeconds: Double): String {
        val s = Math.round(totalSeconds).toInt().coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec)
        else "%d:%02d".format(m, sec)
    }

    fun formatScore(record: BenchmarkRecord): String = when (record.scoreType) {
        BenchmarkScoreType.TIME -> formatDuration(record.scoreValue)
        BenchmarkScoreType.REPS -> "${formatWeight(record.scoreValue)} reps"
        BenchmarkScoreType.ROUNDS_REPS -> {
            val rounds = formatWeight(record.scoreValue)
            val reps = formatWeight(record.scoreValue2)
            if (record.scoreValue2 > 0) "$rounds rounds + $reps"
            else "$rounds rounds"
        }
        BenchmarkScoreType.LOAD -> "${formatWeight(record.scoreValue)} kg"
        BenchmarkScoreType.POINTS -> "${formatWeight(record.scoreValue)} pts"
        BenchmarkScoreType.OTHER -> record.scoreText.ifBlank { "—" }
    }

    fun scoreLabel(record: BenchmarkRecord): String = record.scoreType.label

    fun todayIso(): String = LocalDate.now().toString()

    fun formatDate(iso: String): String =
        runCatching {
            LocalDate.parse(iso).format(
                java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM yyyy", java.util.Locale.getDefault())
            )
        }.getOrDefault(iso)

    fun formatDateShort(iso: String): String =
        runCatching {
            LocalDate.parse(iso).format(
                java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault())
            )
        }.getOrDefault(iso)
}
