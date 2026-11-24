package com.hcexport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRecordsTest {

    private val weightKg = MetricDef("weight", "Weight", MetricKind.WEIGHT, "kg")
    private val reps = MetricDef("reps", "Reps", MetricKind.REPS)

    private fun exercise(
        name: String,
        targets: List<String>,
        metrics: List<MetricDef> = listOf(weightKg, reps),
    ) = InteractiveExercise(
        name = name,
        metrics = metrics,
        sets = targets.mapIndexed { index, target ->
            InteractiveSet(label = "Set ${index + 1}", target = target)
        },
    )

    private fun section(kind: String, vararg exercises: InteractiveExercise) =
        InteractiveSection(title = "S", kind = kind, exercises = exercises.toList())

    private fun plan(vararg sections: InteractiveSection) =
        InteractivePlan(title = "T", sections = sections.toList())

    private fun results(vararg values: Triple<String, String, String>):
        Map<String, Map<String, String>> =
        values.fold(emptyMap()) { acc, (scope, key, value) ->
            InteractiveResults.set(acc, scope, key, value)
        }

    private fun set(section: Int, exercise: Int, set: Int, metric: String, value: String) =
        Triple(InteractiveKeys.set(section, exercise, set), metric, value)

    @Test
    fun `the best set at each rep max of a strength section is a candidate`() {
        val plan = plan(section("strength", exercise("Power Snatch", listOf("5 reps", "5 reps", "1 rep"))))
        val values = results(
            set(0, 0, 0, "weight", "80"),
            set(0, 0, 1, "weight", "85"),
            set(0, 0, 2, "weight", "100"),
        )

        val candidates = AutoRecords.candidates(plan, values)

        assertEquals(
            setOf("power_snatch" to 5 to 85.0, "power_snatch" to 1 to 100.0),
            candidates.map { it.exerciseKey to it.reps to it.weightKg }.toSet(),
        )
        assertEquals("Power Snatch", candidates.first().exerciseName)
    }

    @Test
    fun `the prescription's reps count when only the weight was entered`() {
        val plan = plan(section("strength", exercise("Back Squat", listOf("3 reps"))))
        val values = results(set(0, 0, 0, "weight", "120"))

        val candidate = AutoRecords.candidates(plan, values).single()

        assertEquals(3, candidate.reps)
        assertEquals(120.0, candidate.weightKg, 0.0)
    }

    @Test
    fun `entered reps override the prescription`() {
        val plan = plan(section("strength", exercise("Back Squat", listOf("5 reps"))))
        val values = results(
            set(0, 0, 0, "weight", "120"),
            set(0, 0, 0, "reps", "2"),
        )

        assertEquals(2, AutoRecords.candidates(plan, values).single().reps)
    }

    @Test
    fun `metcon and accessory sections are not rep max attempts`() {
        val powerClean = exercise("Power Clean", listOf("3 reps"))
        val plan = plan(section("metcon", powerClean), section("accessory", powerClean))
        val values = results(set(0, 0, 0, "weight", "60"), set(1, 0, 0, "weight", "60"))

        assertTrue(AutoRecords.candidates(plan, values).isEmpty())
    }

    @Test
    fun `a set with no weight is not an attempt`() {
        val plan = plan(section("strength", exercise("Deadlift", listOf("1 rep", "1 rep"))))
        val values = results(
            set(0, 0, 0, "reps", "1"),
            set(0, 0, 1, "weight", "0"),
        )

        assertTrue(AutoRecords.candidates(plan, values).isEmpty())
    }

    @Test
    fun `high rep conditioning is not tracked as a rep max`() {
        val plan = plan(section("strength", exercise("Thruster", listOf("20 reps"))))
        val values = results(set(0, 0, 0, "weight", "40"))

        assertTrue(AutoRecords.candidates(plan, values).isEmpty())
    }

    @Test
    fun `rows past the reached marker were not performed`() {
        val plan = plan(section("strength", exercise("Clean", listOf("1 rep", "1 rep", "1 rep"))))
        var values = results(
            set(0, 0, 0, "weight", "80"),
            set(0, 0, 1, "weight", "90"),
            set(0, 0, 2, "weight", "100"),
        )
        values = InteractiveReached.toggle(values, 0, InteractiveKeys.set(0, 0, 0))

        val candidates = AutoRecords.candidates(plan, values)

        assertEquals(listOf(80.0), candidates.map { it.weightKg })
    }

    @Test
    fun `only a weight that beats the standing record is stored`() {
        val plan = plan(section("strength", exercise("Bench Press", listOf("1 rep", "3 reps"))))
        val values = results(
            set(0, 0, 0, "weight", "100"),
            set(0, 0, 1, "weight", "95"),
        )
        val existing = listOf(
            record(reps = 1, weightKg = 100.0),   // equal — not broken
            record(reps = 3, weightKg = 90.0),    // beaten
        )

        val kept = AutoRecords.beating(AutoRecords.candidates(plan, values), existing)

        assertEquals(listOf(3), kept.map { it.reps })
        assertEquals(95.0, kept.single().weightKg, 0.0)
    }

    @Test
    fun `a lift with no record establishes one`() {
        val plan = plan(section("strength", exercise("Front Squat", listOf("2 reps"))))
        val values = results(set(0, 0, 0, "weight", "110"))

        val kept = AutoRecords.beating(AutoRecords.candidates(plan, values), emptyList())

        assertEquals(2, kept.single().reps)
    }

    @Test
    fun `a record already written by this workout is never rewritten`() {
        val plan = plan(section("strength", exercise("Bench Press", listOf("1 rep"))))
        val values = results(set(0, 0, 0, "weight", "110"))
        val existing = listOf(record(reps = 1, weightKg = 100.0))

        val rows = AutoRecords.newRows(
            AutoRecords.candidates(plan, values),
            existing,
            setOf("bench_press" to 1),
        )

        assertTrue(rows.isEmpty())
    }

    @Test
    fun `pounds are converted before they are compared`() {
        val pounds = MetricDef("weight", "Weight", MetricKind.WEIGHT, "lbs")
        val plan = plan(
            section("strength", exercise("Deadlift", listOf("1 rep"), listOf(pounds, reps)))
        )
        val values = results(set(0, 0, 0, "weight", "225"))

        val candidate = AutoRecords.candidates(plan, values).single()

        assertEquals(102.058, candidate.weightKg, 0.001)
    }

    private fun record(reps: Int, weightKg: Double) = WeightRecord(
        exerciseKey = "bench_press",
        exerciseName = "Bench Press",
        reps = reps,
        weightKg = weightKg,
        achievedDate = "2026-11-01",
    )
}
