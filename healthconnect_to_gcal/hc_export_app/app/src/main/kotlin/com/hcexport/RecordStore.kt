package com.hcexport

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local store for weight and benchmark records. Records are user-entered and
 * survive re-syncs; the workout link is a (date, typeKey) pair pointing at the
 * workout database, not a foreign key, so changing the workout history never
 * deletes a personal best.
 */
object RecordStore {

    private const val DB_NAME = "records.db"
    private const val DB_VERSION = 1
    private const val WEIGHTS = "weight_records"
    private const val BENCHMARKS = "benchmark_records"

    @Volatile private var helper: Helper? = null

    private fun db(ctx: Context): SQLiteDatabase = synchronized(this) {
        val h = helper ?: Helper(ctx.applicationContext).also { helper = it }
        h.writableDatabase
    }

    data class WorkoutRecords(
        val weights: List<WeightRecord>,
        val benchmarks: List<BenchmarkRecord>,
    ) {
        val isEmpty: Boolean get() = weights.isEmpty() && benchmarks.isEmpty()
        val count: Int get() = weights.size + benchmarks.size
    }

    // ── Weights ────────────────────────────────────────────────────────────

    fun addWeight(ctx: Context, record: WeightRecord): Long {
        val values = ContentValues().apply {
            put("exercise_key", record.exerciseKey)
            put("exercise_name", record.exerciseName)
            put("reps", record.reps)
            put("weight_kg", record.weightKg)
            put("achieved_date", record.achievedDate)
            put("workout_date", record.workoutDate)
            put("workout_type_key", record.workoutTypeKey)
            put("note", record.note)
            put("created_at", if (record.createdAtMs > 0) record.createdAtMs else System.currentTimeMillis())
        }
        return db(ctx).insertOrThrow(WEIGHTS, null, values)
    }

    fun updateWeight(ctx: Context, record: WeightRecord) {
        val values = ContentValues().apply {
            put("exercise_key", record.exerciseKey)
            put("exercise_name", record.exerciseName)
            put("reps", record.reps)
            put("weight_kg", record.weightKg)
            put("achieved_date", record.achievedDate)
            put("workout_date", record.workoutDate)
            put("workout_type_key", record.workoutTypeKey)
            put("note", record.note)
        }
        db(ctx).update(WEIGHTS, values, "_id = ?", arrayOf(record.id.toString()))
    }

    fun deleteWeight(ctx: Context, id: Long) {
        db(ctx).delete(WEIGHTS, "_id = ?", arrayOf(id.toString()))
    }

    fun getWeights(ctx: Context): List<WeightRecord> =
        queryWeights(ctx, null, null)

    fun getWeightsFor(ctx: Context, exerciseKey: String): List<WeightRecord> =
        queryWeights(ctx, "exercise_key = ?", arrayOf(exerciseKey))

    fun getWeight(ctx: Context, id: Long): WeightRecord? =
        queryWeights(ctx, "_id = ?", arrayOf(id.toString())).firstOrNull()

    private fun queryWeights(ctx: Context, where: String?, args: Array<String>?): List<WeightRecord> {
        val out = mutableListOf<WeightRecord>()
        db(ctx).query(WEIGHTS, null, where, args, null, null, "achieved_date DESC, created_at DESC")
            .use { c -> while (c.moveToNext()) out += readWeight(c) }
        return out
    }

    fun countWeightRecords(ctx: Context): Int =
        db(ctx).rawQuery("SELECT COUNT(*) FROM $WEIGHTS", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    fun countWeightExercises(ctx: Context): Int =
        db(ctx).rawQuery("SELECT COUNT(DISTINCT exercise_key) FROM $WEIGHTS", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    // ── Benchmarks ─────────────────────────────────────────────────────────

    fun addBenchmark(ctx: Context, record: BenchmarkRecord): Long {
        val values = benchmarkValues(record).apply {
            put("created_at", if (record.createdAtMs > 0) record.createdAtMs else System.currentTimeMillis())
        }
        return db(ctx).insertOrThrow(BENCHMARKS, null, values)
    }

    fun updateBenchmark(ctx: Context, record: BenchmarkRecord) {
        db(ctx).update(
            BENCHMARKS, benchmarkValues(record), "_id = ?", arrayOf(record.id.toString()),
        )
    }

    fun deleteBenchmark(ctx: Context, id: Long) {
        db(ctx).delete(BENCHMARKS, "_id = ?", arrayOf(id.toString()))
    }

    private fun benchmarkValues(record: BenchmarkRecord) = ContentValues().apply {
        put("benchmark_key", record.benchmarkKey)
        put("benchmark_name", record.benchmarkName)
        put("score_type", record.scoreType.key)
        put("score_value", record.scoreValue)
        put("score_value2", record.scoreValue2)
        put("score_text", record.scoreText)
        put("achieved_date", record.achievedDate)
        put("workout_date", record.workoutDate)
        put("workout_type_key", record.workoutTypeKey)
        put("note", record.note)
    }

    fun getBenchmarks(ctx: Context): List<BenchmarkRecord> {
        val out = mutableListOf<BenchmarkRecord>()
        db(ctx).query(BENCHMARKS, null, null, null, null, null, "achieved_date DESC, created_at DESC")
            .use { c -> while (c.moveToNext()) out += readBenchmark(c) }
        return out
    }

    fun getBenchmarksFor(ctx: Context, benchmarkKey: String): List<BenchmarkRecord> {
        val out = mutableListOf<BenchmarkRecord>()
        db(ctx).query(
            BENCHMARKS, null, "benchmark_key = ?", arrayOf(benchmarkKey),
            null, null, "achieved_date DESC, created_at DESC",
        ).use { c -> while (c.moveToNext()) out += readBenchmark(c) }
        return out
    }

    fun getBenchmark(ctx: Context, id: Long): BenchmarkRecord? {
        db(ctx).query(BENCHMARKS, null, "_id = ?", arrayOf(id.toString()), null, null, null)
            .use { c -> if (c.moveToNext()) readBenchmark(c) else null }
            ?.let { return it }
        return null
    }

    fun countBenchmarkResults(ctx: Context): Int =
        db(ctx).rawQuery("SELECT COUNT(*) FROM $BENCHMARKS", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    fun countBenchmarks(ctx: Context): Int =
        db(ctx).rawQuery("SELECT COUNT(DISTINCT benchmark_key) FROM $BENCHMARKS", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    // ── Cross-links ────────────────────────────────────────────────────────

    fun recordsForWorkout(ctx: Context, date: String, typeKey: String): WorkoutRecords {
        val weights = mutableListOf<WeightRecord>()
        db(ctx).query(
            WEIGHTS, null, "workout_date = ? AND workout_type_key = ?", arrayOf(date, typeKey),
            null, null, "reps ASC, weight_kg DESC",
        ).use { c -> while (c.moveToNext()) weights += readWeight(c) }

        val benchmarks = mutableListOf<BenchmarkRecord>()
        db(ctx).query(
            BENCHMARKS, null, "workout_date = ? AND workout_type_key = ?", arrayOf(date, typeKey),
            null, null, "achieved_date DESC, created_at DESC",
        ).use { c -> while (c.moveToNext()) benchmarks += readBenchmark(c) }

        return WorkoutRecords(weights, benchmarks)
    }

    // ── Cursor mapping ─────────────────────────────────────────────────────

    private fun readWeight(c: Cursor) = WeightRecord(
        id = c.getLong(c.getColumnIndexOrThrow("_id")),
        exerciseKey = c.getString(c.getColumnIndexOrThrow("exercise_key")),
        exerciseName = c.getString(c.getColumnIndexOrThrow("exercise_name")),
        reps = c.getInt(c.getColumnIndexOrThrow("reps")),
        weightKg = c.getDouble(c.getColumnIndexOrThrow("weight_kg")),
        achievedDate = c.getString(c.getColumnIndexOrThrow("achieved_date")),
        workoutDate = c.getString(c.getColumnIndexOrThrow("workout_date")),
        workoutTypeKey = c.getString(c.getColumnIndexOrThrow("workout_type_key")),
        note = c.getString(c.getColumnIndexOrThrow("note")),
        createdAtMs = c.getLong(c.getColumnIndexOrThrow("created_at")),
    )

    private fun readBenchmark(c: Cursor) = BenchmarkRecord(
        id = c.getLong(c.getColumnIndexOrThrow("_id")),
        benchmarkKey = c.getString(c.getColumnIndexOrThrow("benchmark_key")),
        benchmarkName = c.getString(c.getColumnIndexOrThrow("benchmark_name")),
        scoreType = BenchmarkScoreType.fromKey(c.getString(c.getColumnIndexOrThrow("score_type"))),
        scoreValue = c.getDouble(c.getColumnIndexOrThrow("score_value")),
        scoreValue2 = c.getDouble(c.getColumnIndexOrThrow("score_value2")),
        scoreText = c.getString(c.getColumnIndexOrThrow("score_text")),
        achievedDate = c.getString(c.getColumnIndexOrThrow("achieved_date")),
        workoutDate = c.getString(c.getColumnIndexOrThrow("workout_date")),
        workoutTypeKey = c.getString(c.getColumnIndexOrThrow("workout_type_key")),
        note = c.getString(c.getColumnIndexOrThrow("note")),
        createdAtMs = c.getLong(c.getColumnIndexOrThrow("created_at")),
    )

    private class Helper(ctx: Context) : SQLiteOpenHelper(ctx, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $WEIGHTS(
                  _id INTEGER PRIMARY KEY AUTOINCREMENT,
                  exercise_key TEXT NOT NULL,
                  exercise_name TEXT NOT NULL,
                  reps INTEGER NOT NULL,
                  weight_kg REAL NOT NULL,
                  achieved_date TEXT NOT NULL,
                  workout_date TEXT,
                  workout_type_key TEXT,
                  note TEXT,
                  created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX idx_weight_exercise ON $WEIGHTS(exercise_key, reps)")
            db.execSQL("CREATE INDEX idx_weight_workout ON $WEIGHTS(workout_date, workout_type_key)")

            db.execSQL(
                """
                CREATE TABLE $BENCHMARKS(
                  _id INTEGER PRIMARY KEY AUTOINCREMENT,
                  benchmark_key TEXT NOT NULL,
                  benchmark_name TEXT NOT NULL,
                  score_type TEXT NOT NULL,
                  score_value REAL NOT NULL DEFAULT 0,
                  score_value2 REAL NOT NULL DEFAULT 0,
                  score_text TEXT NOT NULL DEFAULT '',
                  achieved_date TEXT NOT NULL,
                  workout_date TEXT,
                  workout_type_key TEXT,
                  note TEXT,
                  created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX idx_benchmark_key ON $BENCHMARKS(benchmark_key)")
            db.execSQL("CREATE INDEX idx_benchmark_workout ON $BENCHMARKS(workout_date, workout_type_key)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Version 1 is the first schema; future migrations go here.
        }
    }
}
