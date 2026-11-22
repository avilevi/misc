package com.hcexport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveScoringTest {

    private val amrapPlan = InteractivePlan.fromJson(
        """
        {
          "title": "Monday",
          "summary": "Strength, then a 15:00 AMRAP",
          "sections": [
            {
              "title": "Metcon",
              "kind": "metcon",
              "notes": "15:00 AMRAP",
              "metrics": [
                {"key": "rounds", "label": "Rounds", "kind": "rounds"},
                {"key": "total_time", "label": "Total time", "kind": "time"}
              ],
              "rounds": [
                {
                  "label": "Each round",
                  "movements": [
                    {
                      "name": "200m Run",
                      "target": "200m",
                      "metrics": [
                        {"key": "distance", "label": "Distance", "kind": "distance", "unit": "m"},
                        {"key": "time", "label": "Time", "kind": "time"}
                      ]
                    },
                    {
                      "name": "Push-ups",
                      "target": "15 reps",
                      "metrics": [{"key": "reps", "label": "Reps", "kind": "reps"}]
                    },
                    {
                      "name": "Toes-to-Bar",
                      "target": "10 reps",
                      "metrics": [{"key": "reps", "label": "Reps", "kind": "reps"}]
                    }
                  ]
                }
              ]
            }
          ]
        }
        """.trimIndent()
    )!!

    private val section = LiveWorkout.build(amrapPlan).sections.single()

    private fun scope(index: Int) = section.items[index].scope
    private fun sectionScope() = InteractiveKeys.section(0)

    private fun results(vararg values: Pair<Pair<String, String>, String>): Map<String, Map<String, String>> {
        var out: Map<String, Map<String, String>> = emptyMap()
        values.forEach { (target, value) -> out = InteractiveResults.set(out, target.first, target.second, value) }
        return out
    }

    @Test
    fun `the example WOD scores 5 rounds plus 5 toes-to-bar as 151`() {
        assertEquals(26, LiveScoring.perRoundReps(section))
        val values = results(
            (scope(0) to "distance") to "200",
            (scope(1) to "reps") to "15",
            (scope(2) to "reps") to "5",
            (sectionScope() to "rounds") to "5",
            (sectionScope() to LiveScoring.PARTIAL_KEY) to "2",
        )
        val score = LiveScoring.amrapScore(section, values)!!
        assertEquals(5, score.rounds)
        assertEquals(2, score.partialIndex)
        assertEquals("Toes-to-Bar", score.currentItem)
        assertEquals(5, score.currentReps)
        assertEquals(16, score.completedReps)
        assertEquals(151, score.total)
        assertEquals("5 rounds + 5 reps of Toes-to-Bar = 151", score.detail)
    }

    @Test
    fun `an unfinished movement with no reps entered counts only what was completed`() {
        val values = results(
            (scope(1) to "reps") to "15",
            (sectionScope() to "rounds") to "5",
            (sectionScope() to LiveScoring.PARTIAL_KEY) to "2",
        )
        val score = LiveScoring.amrapScore(section, values)!!
        assertEquals(0, score.currentReps)
        assertEquals(146, score.total)
        assertEquals("5 rounds + 16 reps = 146", score.detail)
    }

    @Test
    fun `a full-round finish scores only the completed rounds`() {
        val values = results(
            (sectionScope() to "rounds") to "5",
            (sectionScope() to LiveScoring.PARTIAL_KEY) to "-1",
        )
        val score = LiveScoring.amrapScore(section, values)!!
        assertNull(score.partialIndex)
        assertEquals(130, score.total)
        assertEquals("5 rounds = 130", score.detail)
    }

    @Test
    fun `an untouched section has no score to show`() {
        assertNull(LiveScoring.amrapScore(section, emptyMap()))
    }

    @Test
    fun `a non-rep movement counts as one rep and a rep target reads its number`() {
        val run = section.items[0]
        assertEquals(1, LiveScoring.prescribedReps(run))
        assertEquals(1, LiveScoring.prescribedReps(run.copy(target = "400m")))
        assertEquals(1, LiveScoring.prescribedReps(run.copy(target = "40s")))
        assertEquals(1, LiveScoring.prescribedReps(run.copy(target = "15 cal")))
        assertEquals(1, LiveScoring.prescribedReps(run.copy(target = "1:30")))
        assertEquals(1, LiveScoring.prescribedReps(run.copy(target = "max reps")))
        assertEquals(10, LiveScoring.prescribedReps(run.copy(target = "10 toes-to-bar")))
        assertEquals(15, LiveScoring.prescribedReps(run.copy(target = "15 reps")))
        assertEquals(12, LiveScoring.prescribedReps(run.copy(target = "12")))
    }

    @Test
    fun `a new AMRAP round drops its reps checks and splits but keeps the weight`() {
        var values = results(
            (scope(0) to "distance") to "200",
            (scope(0) to "time") to "1:30",
            (scope(1) to "reps") to "12",
            (scope(2) to "reps") to "4",
        )
        values = InteractiveResults.set(values, scope(1), "weight", "20")
        val reset = LiveScoring.resetRoundValues(values, section)
        assertFalse(InteractiveResults.contains(reset, scope(1), "reps"))
        assertFalse(InteractiveResults.contains(reset, scope(2), "reps"))
        assertFalse(InteractiveResults.contains(reset, scope(0), "time"))
        assertEquals("20", InteractiveResults.value(reset, scope(1), "weight"))
        // A distance is not a rep count; it is not part of the round's reps.
        assertTrue(InteractiveResults.contains(reset, scope(0), "distance"))
    }

    @Test
    fun `finishing an AMRAP stores rounds and the partial boundary, never its time`() {
        val finished = LiveResults.finish(
            results = emptyMap(),
            section = section,
            lastReachedRow = 2,
            completed = false,
            elapsedSeconds = 900,
            roundsDone = 5,
            partialIndex = 2,
        )
        assertEquals("5", InteractiveResults.value(finished, sectionScope(), "rounds"))
        assertEquals("2", InteractiveResults.value(finished, sectionScope(), LiveScoring.PARTIAL_KEY))
        assertFalse(InteractiveResults.contains(finished, sectionScope(), "total_time"))
        assertEquals(
            "",
            InteractiveResults.value(finished, InteractiveKeys.marker(0), InteractiveReached.KEY),
        )
    }

    @Test
    fun `a finished AMRAP is reset before the same section starts again`() {
        var values = results(
            (sectionScope() to "rounds") to "5",
            (sectionScope() to LiveScoring.PARTIAL_KEY) to "2",
            (sectionScope() to "total_time") to "15:00",
            (scope(2) to "reps") to "5",
        )
        values = LiveScoring.resetAmrap(values, section)
        assertFalse(LiveScoring.hasFinishedAmrap(values, section))
        assertNull(LiveScoring.amrapScore(section, values))
        assertFalse(InteractiveResults.contains(values, scope(2), "reps"))
        assertFalse(InteractiveResults.contains(values, sectionScope(), "total_time"))
    }

    @Test
    fun `removing a value makes the field show its prescription again`() {
        val values = InteractiveResults.set(emptyMap(), "e:0:0:0", "reps", "5")
        assertTrue(InteractiveResults.contains(values, "e:0:0:0", "reps"))
        val cleared = InteractiveResults.remove(values, "e:0:0:0", "reps")
        assertFalse(InteractiveResults.contains(cleared, "e:0:0:0", "reps"))
        assertTrue(cleared.isEmpty())
    }
}
