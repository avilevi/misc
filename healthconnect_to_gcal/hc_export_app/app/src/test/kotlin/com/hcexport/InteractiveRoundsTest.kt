package com.hcexport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractiveRoundsTest {

    private val roundsPlan = """
        {
          "title": "Snatch triplet",
          "sections": [
            {
              "title": "Metcon",
              "kind": "metcon",
              "metrics": [
                {"key": "total_time", "label": "Total time", "kind": "time", "unit": ""}
              ],
              "rounds": [
                {
                  "label": "Round 1",
                  "movements": [
                    {"name": "Power Snatch", "target": "15 reps", "metrics": [
                      {"key": "weight", "label": "Weight", "kind": "weight", "unit": "kg"},
                      {"key": "reps", "label": "Reps", "kind": "reps", "unit": ""}
                    ]},
                    {"name": "Air Squats", "target": "15 reps", "metrics": [
                      {"key": "reps", "label": "Reps", "kind": "reps", "unit": ""}
                    ]},
                    {"name": "Push-Ups", "target": "15 reps", "metrics": [
                      {"key": "reps", "label": "Reps", "kind": "reps", "unit": ""}
                    ]}
                  ]
                },
                {
                  "label": "Round 2",
                  "movements": [
                    {"name": "Power Snatch", "target": "12 reps", "metrics": [
                      {"key": "weight", "label": "Weight", "kind": "weight", "unit": "kg"},
                      {"key": "reps", "label": "Reps", "kind": "reps", "unit": ""}
                    ]},
                    {"name": "Air Squats", "target": "15 reps", "metrics": [
                      {"key": "reps", "label": "Reps", "kind": "reps", "unit": ""}
                    ]},
                    {"name": "Push-Ups", "target": "15 reps", "metrics": [
                      {"key": "reps", "label": "Reps", "kind": "reps", "unit": ""}
                    ]}
                  ]
                }
              ]
            }
          ]
        }
    """.trimIndent()

    private fun legacyCircuit(rounds: Int = 2): String {
        fun sets(targets: List<String>) = targets.mapIndexed { index, target ->
            """{"label":"Round ${index + 1}","target":"$target"}"""
        }.joinToString(",")

        return """
        {
          "title": "Triplet",
          "sections": [
            {
              "title": "Metcon",
              "kind": "metcon",
              "exercises": [
                {"name":"Power Snatch","metrics":[
                  {"key":"weight","label":"Weight","kind":"weight","unit":"kg"},
                  {"key":"reps","label":"Reps","kind":"reps","unit":""}
                ],"sets":[${sets((1..rounds).map { "${16 - it} reps" })}]},
                {"name":"Air Squats","metrics":[
                  {"key":"reps","label":"Reps","kind":"reps","unit":""}
                ],"sets":[${sets((1..rounds).map { "15 reps" })}]},
                {"name":"Push-Ups","metrics":[
                  {"key":"reps","label":"Reps","kind":"reps","unit":""}
                ],"sets":[${sets((1..rounds).map { "15 reps" })}]}
              ]
            }
          ]
        }
        """.trimIndent()
    }

    @Test
    fun `parses rounds, movements and their metrics`() {
        val plan = InteractivePlan.fromJson(roundsPlan)
        assertNotNull(plan)
        val section = plan!!.sections.single()
        assertEquals(2, section.rounds.size)
        val first = section.rounds.first()
        assertEquals("Round 1", first.label)
        assertEquals(3, first.movements.size)
        assertEquals("Power Snatch", first.movements.first().name)
        assertEquals("15 reps", first.movements.first().target)
        assertEquals(MetricKind.WEIGHT, first.movements.first().metrics.first().kind)
        assertEquals("12 reps", section.rounds[1].movements.first().target)
        assertTrue(section.exercises.isEmpty())
    }

    @Test
    fun `rounds survive a json round trip`() {
        val plan = InteractivePlan.fromJson(roundsPlan)!!
        val again = InteractivePlan.fromJson(plan.toJson().toString())!!
        assertEquals(plan, again)
    }

    @Test
    fun `a plan with only rounds is not empty`() {
        val plan = InteractivePlan.fromJson(
            """
            {"title":"X","sections":[{"title":"Metcon","rounds":[
              {"label":"Round 1","movements":[{"name":"Run","target":"400m"}]}
            ]}]}
            """.trimIndent()
        )!!
        assertTrue(!plan.isEmpty)
        val movement = plan.sections.single().rounds.single().movements.single()
        // No metrics means the movement still gets a text result to fill in.
        assertEquals(MetricKind.TEXT, movement.metrics.single().kind)
    }

    @Test
    fun `round keys are positional per round and movement`() {
        assertEquals("r:1:2:3", InteractiveKeys.round(section = 1, round = 2, movement = 3))
    }

    @Test
    fun `a legacy circuit stored as exercises is regrouped into rounds`() {
        val plan = InteractivePlan.fromJson(legacyCircuit(rounds = 5))!!
        val section = plan.sections.single()
        val rounds = InteractiveRounds.derive(section)
        assertNotNull(rounds)
        assertEquals(5, rounds!!.size)
        assertEquals("Round 3", rounds[2].label)
        assertEquals(3, rounds[2].movements.size)
        assertEquals(
            listOf("Power Snatch", "Air Squats", "Push-Ups"),
            rounds[2].movements.map { it.exercise.name },
        )
        // Keys stay on the original exercise/set positions, so no data moves.
        assertEquals("e:0:0:2", InteractiveKeys.set(0, rounds[2].movements[0].exerciseIndex, rounds[2].movements[0].setIndex))
        assertEquals("e:0:2:2", InteractiveKeys.set(0, rounds[2].movements[2].exerciseIndex, rounds[2].movements[2].setIndex))
    }

    @Test
    fun `plain sets are not treated as rounds`() {
        val plan = InteractivePlan.fromJson(
            """
            {"title":"X","sections":[{"title":"Strength","exercises":[
              {"name":"Squat","sets":[{"label":"Set 1"},{"label":"Set 2"}]},
              {"name":"Press","sets":[{"label":"Set 1"},{"label":"Set 2"}]}
            ]}]}
            """.trimIndent()
        )!!
        assertNull(InteractiveRounds.derive(plan.sections.single()))
    }

    @Test
    fun `exercises with different round counts are not grouped`() {
        val plan = InteractivePlan.fromJson(
            """
            {"title":"X","sections":[{"title":"Metcon","exercises":[
              {"name":"A","sets":[{"label":"Round 1"},{"label":"Round 2"}]},
              {"name":"B","sets":[{"label":"Round 1"}]}
            ]}]}
            """.trimIndent()
        )!!
        assertNull(InteractiveRounds.derive(plan.sections.single()))
    }

    // ── Journal ─────────────────────────────────────────────────────────────

    @Test
    fun `journal renders rounds with values, defaults and volume`() {
        val plan = InteractivePlan.fromJson(roundsPlan)!!
        val results = InteractiveResults.fromJson(
            """
            {"r:0:0:0":{"weight":"30","reps":"15"},
             "r:0:0:1":{"reps":"15"},
             "r:0:0:2":{"reps":"15"},
             "r:0:1:0":{"weight":"30","reps":"12"}}
            """.trimIndent()
        )
        val journal = JournalFeed.aiPlan(plan, results, model = "test")
        val section = journal.sections.single()
        assertEquals(6, journal.totalSets)
        assertEquals(4, journal.loggedSets)
        assertEquals(30.0 * 15 + 30 * 12, journal.totalVolumeKg!!, 0.001)

        assertEquals(2, section.rounds.size)
        assertTrue(section.exercises.isEmpty())
        val second = section.rounds[1]
        assertEquals("Round 2", second.label)
        assertEquals(
            listOf("Power Snatch", "Air Squats", "Push-Ups"),
            second.movements.map { it.name },
        )
        assertEquals(
            listOf(JournalAiValue("Weight", "30", "kg"), JournalAiValue("Reps", "12", "")),
            second.movements.first().values,
        )
        // Air squats round 2 has no stored reps, so the prescription shows.
        assertEquals(
            listOf(JournalAiValue("Reps", "15", "")),
            second.movements[1].values,
        )
    }

    // ── Completion-only work ────────────────────────────────────────────────

    private val emomPlan = """
        {
          "title": "Core EMOM",
          "sections": [
            {
              "title": "Killer Core",
              "kind": "metcon",
              "notes": "9:00 EMOM, 40s work / 20s rest",
              "rounds": [
                {
                  "label": "Round 1",
                  "movements": [
                    {"name": "Tall Plank", "target": "40s", "metrics": [
                      {"key": "done", "label": "Done", "kind": "check", "unit": ""}
                    ]},
                    {"name": "MedBall Russian Twist", "target": "40s", "metrics": [
                      {"key": "done", "label": "Done", "kind": "check", "unit": ""}
                    ]},
                    {"name": "Dual KB Front Rack Hold", "target": "40s", "metrics": [
                      {"key": "done", "label": "Done", "kind": "check", "unit": ""}
                    ]}
                  ]
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `check metrics parse and default to nothing`() {
        assertEquals(MetricKind.CHECK, MetricKind.fromKey("check"))
        assertEquals(MetricKind.CHECK, MetricKind.fromKey("done"))
        assertEquals(MetricKind.CHECK, MetricKind.fromKey("completed"))

        val metric = MetricDef("done", "Done", MetricKind.CHECK)
        assertEquals(null, InteractiveDefaults.forTarget("40s", metric))
    }

    @Test
    fun `a completed check shows as a tick in the journal`() {
        val plan = InteractivePlan.fromJson(emomPlan)!!
        val results = InteractiveResults.fromJson("""{"r:0:0:0":{"done":"1"}}""")
        val journal = JournalFeed.aiPlan(plan, results, model = "test")
        val movement = journal.sections.single().rounds.single().movements.first()

        assertEquals(
            listOf(JournalAiValue("Done", "", "", checked = true)),
            movement.values,
        )
        assertEquals(3, journal.totalSets)
        assertEquals(1, journal.loggedSets)
    }

    @Test
    fun `an unchecked completion shows no value`() {
        val plan = InteractivePlan.fromJson(emomPlan)!!
        val journal = JournalFeed.aiPlan(plan, emptyMap(), model = "test")
        val movement = journal.sections.single().rounds.single().movements.first()
        assertTrue(movement.values.isEmpty())
        assertEquals(0, journal.loggedSets)
    }

    @Test
    fun `journal regroups a legacy circuit without double counting`() {
        val plan = InteractivePlan.fromJson(legacyCircuit(rounds = 2))!!
        val journal = JournalFeed.aiPlan(plan, emptyMap(), model = "test")
        val section = journal.sections.single()

        assertEquals(6, journal.totalSets)
        assertEquals(0, journal.loggedSets)
        assertEquals(2, section.rounds.size)
        assertTrue(section.exercises.isEmpty())
        assertEquals(3, section.rounds.first().movements.size)
        assertEquals("Power Snatch", section.rounds.first().movements.first().name)
        // Target-derived defaults still show for an unlogged circuit.
        assertEquals(
            listOf(JournalAiValue("Reps", "15", "")),
            section.rounds.first().movements[1].values,
        )
    }
}
