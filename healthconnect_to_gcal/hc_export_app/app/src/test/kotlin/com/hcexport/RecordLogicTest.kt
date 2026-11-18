package com.hcexport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordLogicTest {

    private fun weight(
        id: Long,
        name: String,
        reps: Int,
        kg: Double,
        date: String,
        created: Long = id,
    ) = WeightRecord(
        id = id,
        exerciseKey = RecordLogic.exerciseKey(name),
        exerciseName = name,
        reps = reps,
        weightKg = kg,
        achievedDate = date,
        createdAtMs = created,
    )

    private fun bench(
        id: Long,
        name: String,
        type: BenchmarkScoreType,
        value: Double,
        date: String,
        value2: Double = 0.0,
        text: String = "",
        created: Long = id,
    ) = BenchmarkRecord(
        id = id,
        benchmarkKey = RecordLogic.benchmarkKey(name),
        benchmarkName = name,
        scoreType = type,
        scoreValue = value,
        scoreValue2 = value2,
        scoreText = text,
        achievedDate = date,
        createdAtMs = created,
    )

    @Test
    fun `exercise keys normalise names`() {
        assertEquals("back_squat", RecordLogic.exerciseKey("Back Squat"))
        assertEquals("clean_jerk", RecordLogic.exerciseKey("Clean & Jerk"))
        assertEquals("clean_jerk", RecordLogic.exerciseKey("  Clean & Jerk! "))
        assertEquals("other", RecordLogic.exerciseKey("!!!"))
    }

    @Test
    fun `best weight is the heaviest and earliest tie wins`() {
        val records = listOf(
            weight(1, "Back Squat", 1, 120.0, "2026-01-10"),
            weight(2, "Back Squat", 1, 125.0, "2026-03-01", created = 4),
            weight(3, "Back Squat", 1, 125.0, "2026-02-01", created = 2),
        )
        assertEquals(3L, RecordLogic.bestWeight(records)?.id)
    }

    @Test
    fun `weight rows keep the latest spelling and best per rep max`() {
        val records = listOf(
            weight(1, "Back Squat", 1, 120.0, "2026-01-10"),
            weight(2, "Back Squat", 2, 110.0, "2026-02-10"),
            weight(3, "Back Squat", 2, 115.0, "2026-03-10"),
            weight(4, "Back squat", 3, 100.0, "2026-04-10"),
            weight(5, "Deadlift", 1, 180.0, "2026-01-01"),
        )
        val rows = RecordLogic.weightRows(records)
        assertEquals(listOf("Back squat", "Deadlift"), rows.map { it.exerciseName })
        val squat = rows.first()
        assertEquals(4, squat.totalRecords)
        assertEquals(120.0, squat.bestByReps[1]!!.weightKg, 0.0)
        assertEquals(115.0, squat.bestByReps[2]!!.weightKg, 0.0)
        assertEquals(100.0, squat.bestByReps[3]!!.weightKg, 0.0)
    }

    @Test
    fun `custom rep maxes stay out of the table but appear in all reps`() {
        val records = listOf(
            weight(1, "Back Squat", 1, 120.0, "2026-01-01"),
            weight(2, "Back Squat", 5, 100.0, "2026-01-02"),
        )
        val row = RecordLogic.weightRows(records).single()
        assertEquals(2, row.totalRecords)
        assertEquals(setOf(1), row.bestByReps.keys)
        assertEquals(listOf(1, 2, 3, 5), RecordLogic.allReps(records))
    }

    @Test
    fun `format weight drops the trailing zero`() {
        assertEquals("100", RecordLogic.formatWeight(100.0))
        assertEquals("102.5", RecordLogic.formatWeight(102.5))
        assertEquals("102.3", RecordLogic.formatWeight(102.34))
    }

    @Test
    fun `format duration handles minutes and hours`() {
        assertEquals("4:32", RecordLogic.formatDuration(272.0))
        assertEquals("0:09", RecordLogic.formatDuration(9.0))
        assertEquals("1:02:03", RecordLogic.formatDuration(3723.0))
    }

    @Test
    fun `for-time best is the lowest result`() {
        val records = listOf(
            bench(1, "Fran", BenchmarkScoreType.TIME, 300.0, "2026-01-01"),
            bench(2, "Fran", BenchmarkScoreType.TIME, 272.0, "2026-02-01"),
            bench(3, "Fran", BenchmarkScoreType.TIME, 290.0, "2026-03-01"),
        )
        assertEquals(2L, RecordLogic.bestBenchmark(records)?.id)
    }

    @Test
    fun `rounds and reps compare rounds first then reps`() {
        val records = listOf(
            bench(1, "Cindy", BenchmarkScoreType.ROUNDS_REPS, 20.0, "2026-01-01", value2 = 5.0),
            bench(2, "Cindy", BenchmarkScoreType.ROUNDS_REPS, 21.0, "2026-02-01", value2 = 0.0),
            bench(3, "Cindy", BenchmarkScoreType.ROUNDS_REPS, 20.0, "2026-03-01", value2 = 9.0),
        )
        assertEquals(2L, RecordLogic.bestBenchmark(records)?.id)
    }

    @Test
    fun `reps best is the highest result`() {
        val records = listOf(
            bench(1, "Max Pull-Ups", BenchmarkScoreType.REPS, 12.0, "2026-01-01"),
            bench(2, "Max Pull-Ups", BenchmarkScoreType.REPS, 18.0, "2026-02-01"),
        )
        assertEquals(2L, RecordLogic.bestBenchmark(records)?.id)
    }

    @Test
    fun `other benchmarks show the latest result`() {
        val records = listOf(
            bench(1, "Custom", BenchmarkScoreType.OTHER, 0.0, "2026-01-01", text = "first"),
            bench(2, "Custom", BenchmarkScoreType.OTHER, 0.0, "2026-02-01", text = "second"),
        )
        assertEquals("second", RecordLogic.bestBenchmark(records)?.scoreText)
    }

    @Test
    fun `score formatting follows the score type`() {
        assertEquals("4:32", RecordLogic.formatScore(bench(1, "Fran", BenchmarkScoreType.TIME, 272.0, "2026-01-01")))
        assertEquals("150 reps", RecordLogic.formatScore(bench(2, "Karen", BenchmarkScoreType.REPS, 150.0, "2026-01-01")))
        assertEquals("100 kg", RecordLogic.formatScore(bench(3, "Grace", BenchmarkScoreType.LOAD, 100.0, "2026-01-01")))
        assertEquals(
            "20 rounds + 5",
            RecordLogic.formatScore(
                bench(4, "Cindy", BenchmarkScoreType.ROUNDS_REPS, 20.0, "2026-01-01", value2 = 5.0)
            ),
        )
    }

    @Test
    fun `history is newest first`() {
        val records = listOf(
            bench(1, "Fran", BenchmarkScoreType.TIME, 300.0, "2026-01-01"),
            bench(2, "Fran", BenchmarkScoreType.TIME, 272.0, "2026-03-01"),
            bench(3, "Fran", BenchmarkScoreType.TIME, 290.0, "2026-02-01"),
        )
        assertEquals(listOf(2L, 3L, 1L), RecordLogic.benchmarkHistory(records).map { it.id })
        assertTrue(RecordLogic.benchmarkHistory(records).first().achievedDate > records.first().achievedDate)
    }
}
