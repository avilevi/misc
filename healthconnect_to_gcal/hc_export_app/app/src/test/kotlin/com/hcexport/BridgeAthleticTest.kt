package com.hcexport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class BridgeAthleticTest {

    private val rosterJson = """
        {
          "programAssignments":[{"id":702874,"programId":955233,"startDate":"2025-08-24T12:00:00.000Z"}],
          "linked":{
            "programHistories":{
              "2400736":{"programHistoryId":2400736,"programId":955233,"name":"Weightlifting Development"}
            },
            "phaseHistories":{
              "4884498":{"phaseHistoryId":4884498,"programHistoryId":2400736,"phaseId":2827547,"name":"Technical Build Up"}
            },
            "workouts":{
              "40411049":{"workoutId":40411049,"name":"Slow Snatch","weekNumber":6}
            },
            "workoutHistories":{
              "71191856":{"workoutHistoryId":71191856,"workoutId":40411050,"phaseHistoryId":4884498,"name":"Power Snatch","startDate":"2026-11-21T12:00:00.000Z","status":"scheduled"},
              "71191855":{"workoutHistoryId":71191855,"workoutId":40411049,"phaseHistoryId":4884498,"name":"Slow Snatch & Clean+Jerk + Core Focused","startDate":"2026-11-19T12:00:00.000Z","status":"started"}
            }
          }
        }
    """.trimIndent()

    /** Same shape, but with the linked sections as arrays. */
    private val rosterArrayJson = """
        {
          "linked":{
            "programHistories":[{"programHistoryId":1,"programId":42,"name":"Program"}],
            "phaseHistories":[{"phaseHistoryId":2,"programHistoryId":1,"name":"Phase"}],
            "workouts":[{"workoutId":3,"name":"W","weekNumber":2}],
            "workoutHistories":[{"workoutHistoryId":4,"workoutId":3,"phaseHistoryId":2,"name":"N","startDate":"2026-01-02T12:00:00.000Z","status":"completed"}]
          }
        }
    """.trimIndent()

    private val templateJson = """
        {
          "programs":{"programId":955233,"name":"Weightlifting Development","updatedAt":"2026-11-06T21:03:28.000Z"},
          "linked":{
            "exercises":{
              "365":{"exerciseId":365,"name":"Back Squat"},
              "366":{"exerciseId":366,"name":"Foam Roll"}
            },
            "workouts":{
              "40411049":{"workoutId":40411049,"name":"Slow Snatch","weekNumber":6}
            },
            "blocks":{
              "2":{"blockId":2,"workoutId":40411049,"name":"Warm Up - Massage","sequence":1,"isCircuit":false},
              "1":{"blockId":1,"workoutId":40411049,"name":"Olympic Lifting","sequence":2},
              "3":{"blockId":3,"workoutId":40411049,"name":"Core Focused","sequence":3,"isCircuit":true,"rounds":3}
            },
            "blockSets":{
              "10":{"blockSetId":10,"blockId":1,"exerciseId":365,"sequence":1,"roundNumber":1,
                    "prescribed":{"reps":{"type":"absolute","value":3},
                                  "weight":{"type":"1rme_perc","unit":"kg","value":60000000,"modifier":{"value":700000}}}},
              "11":{"blockSetId":11,"blockId":1,"exerciseId":365,"sequence":2,"roundNumber":1,
                    "prescribed":{"reps":{"type":"absolute","value":3},
                                  "weight":{"type":"1rme_perc","unit":"kg","value":75000000,"modifier":{"value":750000}}}},
              "12":{"blockSetId":12,"blockId":2,"exerciseId":366,"sequence":1,"roundNumber":1,
                    "prescribed":{"reps":{"type":"absolute","value":10}}},
              "13":{"blockSetId":13,"blockId":2,"exerciseId":366,"sequence":2,"roundNumber":1,
                    "prescribed":{"reps":{"type":"absolute","value":10}}},
              "14":{"blockSetId":14,"blockId":3,"exerciseId":366,"sequence":1,"roundNumber":1,
                    "prescribed":{"time":{"value":40000}},"restTime":10,"note":null},
              "15":{"blockSetId":15,"blockId":3,"exerciseId":365,"sequence":2,"roundNumber":2,
                    "prescribed":{"reps":{"type":"absolute","value":10},
                                  "weight":{"type":"manual_weight","unit":"kg","min":16000000,"max":32000000,"isRange":true}}}
            }
          }
        }
    """.trimIndent()

    @Test
    fun `roster rows are sorted, named and phase-tagged`() {
        val roster = BridgeAthletic.parseRoster(rosterJson)
        assertEquals(2, roster.rows.size)
        assertEquals(listOf("2026-11-19", "2026-11-21"), roster.rows.map { it.date })

        val first = roster.rows.first()
        assertEquals("Slow Snatch & Clean+Jerk + Core Focused", first.name)
        assertEquals("Technical Build Up", first.phaseName)
        assertEquals(6, first.weekNumber)
        assertEquals(955233, first.programId)
        assertEquals(setOf(955233), roster.programIds)

        val second = roster.rows[1]
        assertEquals("scheduled", second.status)
        assertNull(second.weekNumber)
    }

    @Test
    fun `linked sections sent as arrays parse too`() {
        val roster = BridgeAthletic.parseRoster(rosterArrayJson)
        assertEquals(1, roster.rows.size)
        val row = roster.rows.first()
        assertEquals(42, row.programId)
        assertEquals("Phase", row.phaseName)
        assertEquals(2, row.weekNumber)
        assertEquals("completed", row.status)
    }

    @Test
    fun `template blocks are ordered and prescriptions rendered`() {
        val template = BridgeAthletic.parseTemplate(templateJson)
        assertEquals(955233, template.programId)
        assertEquals("2026-11-06T21:03:28.000Z", template.updatedAt)

        val workout = template.workouts[40411049]!!
        assertEquals(3, workout.blocks.size)
        assertEquals(
            listOf("Warm Up - Massage", "Olympic Lifting", "Core Focused"),
            workout.blocks.map { it.name },
        )

        val text = BridgeAthletic.blocksText(workout)
        assertEquals(
            """
            Olympic Lifting:
            - Back Squat — 3 reps @ 70%
            - Back Squat — 3 reps @ 75%

            Core Focused (3 rounds):
            - Foam Roll — 40s · rest 10s
            - Back Squat — 10 reps @ 16–32 kg
            """.trimIndent(),
            text,
        )
        assertFalse("warm-up blocks are dropped", text.contains("Foam Roll — 10 reps"))
        assertTrue(BridgeAthletic.isWarmUp("Warm Up - Massage"))
        assertTrue(BridgeAthletic.isWarmUp("warm up - rotations"))
        assertTrue(BridgeAthletic.isWarmUp("Warm-Up"))
        assertFalse(BridgeAthletic.isWarmUp("Olympic Lifting"))
    }

    @Test
    fun `render joins heading and block text`() {
        val row = BridgeAthletic.RosterRow(
            workoutHistoryId = 71191855,
            workoutId = 40411049,
            programId = 955233,
            date = "2026-11-19",
            name = "Slow Snatch & Clean+Jerk + Core Focused",
            status = "started",
            phaseName = "Technical Build Up",
            weekNumber = 6,
        )
        val blocks = """
            Warm Up - Massage:
            - Foam Roll — 10 reps × 2
        """.trimIndent()
        assertEquals(
            "Slow Snatch & Clean+Jerk + Core Focused\nTechnical Build Up · Week 6\n\n$blocks",
            BridgeAthletic.render(row, blocks),
        )
        assertEquals(
            "Slow Snatch & Clean+Jerk + Core Focused\nTechnical Build Up · Week 6",
            BridgeAthletic.render(row, null),
        )
    }

    @Test
    fun `missing template renders the heading only`() {
        val rosters = BridgeAthletic.parseRoster(rosterJson)
        val template = BridgeAthletic.parseTemplate(templateJson)
        val unknown = rosters.rows[1].copy(workoutId = 999)
        val text = BridgeAthletic.render(
            unknown,
            BridgeAthletic.blocksText(template.workouts[unknown.workoutId]),
        )
        assertEquals("Power Snatch\nTechnical Build Up", text)
    }

    @Test
    fun `jwt claims carry the user id and expiry`() {
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"id":740482,"exp":1797706476}""".toByteArray())
        val claims = BridgeApi.jwtClaims("x.$payload.y")
        assertEquals(740482, claims.optInt("id"))
        assertEquals(1797706476L, claims.optLong("exp"))

        assertTrue(BridgeApi.jwtClaims("").length() == 0)
    }
}
