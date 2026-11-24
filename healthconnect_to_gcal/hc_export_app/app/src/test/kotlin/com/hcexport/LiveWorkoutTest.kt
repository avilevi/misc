package com.hcexport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveWorkoutTest {

    private val plan = InteractivePlan.fromJson(
        """
        {
          "title": "Friday",
          "summary": "Snatch strength, then a mixed AMRAP",
          "sections": [
            {
              "title": "Power Snatch",
              "kind": "strength",
              "notes": "Every 1:30 x 6 sets. Build to a heavy single.",
              "exercises": [
                {
                  "name": "Power Snatch",
                  "metrics": [
                    {"key": "weight", "label": "Weight", "kind": "weight", "unit": "kg"},
                    {"key": "reps", "label": "Reps", "kind": "reps"}
                  ],
                  "sets": [
                    {"label": "Set 1", "target": "5 reps"},
                    {"label": "Set 2", "target": "5 reps"},
                    {"label": "Set 3", "target": "3 reps"},
                    {"label": "Set 4", "target": "3 reps"},
                    {"label": "Set 5", "target": "1 rep"},
                    {"label": "Set 6", "target": "1 rep"}
                  ]
                }
              ]
            },
            {
              "title": "AMRAP 12",
              "kind": "metcon",
              "notes": "12:00 AMRAP. Keep the bar moving.",
              "metrics": [
                {"key": "rounds", "label": "Rounds", "kind": "rounds"},
                {"key": "total_time", "label": "Total time", "kind": "time"}
              ],
              "rounds": [
                {
                  "label": "Each round",
                  "movements": [
                    {
                      "name": "Power Snatch",
                      "target": "15 reps",
                      "metrics": [
                        {"key": "weight", "label": "Weight", "kind": "weight", "unit": "kg"},
                        {"key": "reps", "label": "Reps", "kind": "reps"}
                      ]
                    },
                    {
                      "name": "Air Squats",
                      "target": "15 reps",
                      "metrics": [{"key": "reps", "label": "Reps", "kind": "reps"}]
                    },
                    {
                      "name": "Front Rack Hold",
                      "target": "40s",
                      "metrics": [{"key": "done", "label": "Done", "kind": "check"}]
                    }
                  ]
                }
              ]
            },
            {
              "title": "Accessory",
              "kind": "accessory",
              "exercises": [
                {
                  "name": "Ring Row",
                  "metrics": [{"key": "reps", "label": "Reps", "kind": "reps"}],
                  "sets": [
                    {"label": "Set 1", "target": "10 reps"},
                    {"label": "Set 2", "target": "10 reps"}
                  ]
                }
              ]
            }
          ]
        }
        """.trimIndent()
    )!!

    private val live = LiveWorkout.build(plan, defaultIntervalSeconds = 90)

    @Test
    fun `strength sets become one step each, in order`() {
        val section = live.sections[0]
        assertEquals(6, section.items.size)
        assertEquals(90, section.intervalSeconds)
        assertFalse(section.clocked)
        assertEquals("Power Snatch", section.items.first().name)
        assertEquals("Set 1 of 6", section.items.first().position)
        assertEquals("5 reps", section.items.first().target)
        assertEquals(InteractiveKeys.set(0, 0, 0), section.items.first().scope)
        assertEquals(
            InteractiveRows.scopes(plan.sections[0], 0),
            section.items.map { it.scope },
        )
    }

    @Test
    fun `an AMRAP keeps its one round and a cap`() {
        val section = live.sections[1]
        assertTrue(section.isAmrap)
        assertTrue(section.clocked)
        assertEquals(720, section.timeCapSeconds)
        assertEquals(3, section.items.size)
        assertEquals("Each round", section.items.first().position)
        assertEquals(InteractiveKeys.round(1, 0, 0), section.items.first().scope)
        assertEquals("Power Snatch", section.items.first().name)
        assertEquals(MetricKind.CHECK, section.items[2].metrics.first().kind)
    }

    @Test
    fun `accessory work without a stated interval is untimed`() {
        val section = live.sections[2]
        assertNull(section.intervalSeconds)
        assertFalse(section.clocked)
        assertEquals(2, section.items.size)
    }

    @Test
    fun `a strength section with no interval uses the default window`() {
        val plain = InteractivePlan.fromJson(
            """
            {"title":"X","sections":[{"title":"Squat","kind":"strength",
              "exercises":[{"name":"Back Squat","sets":[{"label":"Set 1","target":"5 reps"}]}]}]}
            """.trimIndent()
        )!!
        assertEquals(120, LiveWorkout.build(plain, 120).sections.single().intervalSeconds)
    }

    @Test
    fun `minute-labelled sets are an EMOM`() {
        val emom = InteractivePlan.fromJson(
            """
            {"title":"X","sections":[{"title":"Heavy Metcon","kind":"metcon","notes":"28:00 EMOM",
              "exercises":[{"name":"Push Press","sets":[
                {"label":"Min 1","target":"3 reps"},{"label":"Min 2","target":"3 reps"}
              ]}]}]}
            """.trimIndent()
        )!!
        val section = LiveWorkout.build(emom).sections.single()
        assertEquals(60, section.intervalSeconds)
        assertFalse(section.clocked)
    }

    @Test
    fun `explicit rounds store each movement as a step`() {
        val section = live.sections[1]
        assertEquals(
            listOf("Power Snatch", "Air Squats", "Front Rack Hold"),
            section.items.map { it.name },
        )
        assertEquals(listOf(0, 1, 2), section.items.map { it.rowIndex })
    }

    @Test
    fun `level-one timing read from the workout's words`() {
        assertEquals(90, LiveTiming.intervalSeconds("Every 1:30 x 6 sets"))
        assertEquals(90, LiveTiming.intervalSeconds("1:30 x 6 sets"))
        assertEquals(120, LiveTiming.intervalSeconds("on the 2:00"))
        assertEquals(90, LiveTiming.intervalSeconds("every 90 seconds"))
        assertEquals(120, LiveTiming.intervalSeconds("Every 2 minutes"))
        assertEquals(90, LiveTiming.intervalSeconds("E1M30"))
        assertEquals(120, LiveTiming.intervalSeconds("E2MOM"))
        assertEquals(120, LiveTiming.intervalSeconds("E2M0M"))
        assertEquals(60, LiveTiming.intervalSeconds("28:00 EMOM"))
        assertNull(LiveTiming.intervalSeconds("5 rounds for time"))
    }

    @Test
    fun `time caps are read from caps AMRAPs and for-time pieces`() {
        assertEquals(840, LiveTiming.timeCapSeconds("Time cap: 14:00"))
        assertEquals(840, LiveTiming.timeCapSeconds("14 min cap"))
        assertEquals(720, LiveTiming.timeCapSeconds("cap 12"))
        assertEquals(720, LiveTiming.timeCapSeconds("12:00 AMRAP"))
        assertEquals(600, LiveTiming.timeCapSeconds("AMRAP 10 min"))
        assertEquals(1200, LiveTiming.timeCapSeconds("20 minute AMRAP"))
        assertEquals(600, LiveTiming.timeCapSeconds("AMRAP 10"))
        assertEquals(840, LiveTiming.timeCapSeconds("14:00 for time"))
        assertEquals(1680, LiveTiming.timeCapSeconds("28:00 EMOM"))
        assertEquals(900, LiveTiming.timeCapSeconds("For Time: T.C 15:00"))
        assertEquals(900, LiveTiming.timeCapSeconds("TC: 15:00"))
        assertEquals(900, LiveTiming.timeCapSeconds("T.C. 15:00"))
        assertEquals(900, LiveTiming.timeCapSeconds("T.C 15 min"))
        assertNull(LiveTiming.timeCapSeconds("5 rounds for time"))
    }

    @Test
    fun `AMRAP detection ignores ordinary rounds`() {
        assertTrue(LiveTiming.isAmrap("12:00 AMRAP"))
        assertTrue(LiveTiming.isAmrap("as many rounds as possible"))
        assertFalse(LiveTiming.isAmrap("5 rounds for time"))
    }

    @Test
    fun `a circuit stored as Round-labelled sets is walked the way the editor walks it`() {
        val legacy = InteractivePlan.fromJson(
            """
            {"title":"X","sections":[{"title":"Circuit","kind":"metcon","notes":"12:00 AMRAP",
              "exercises":[
                {"name":"Power Snatch","metrics":[{"key":"reps","label":"Reps","kind":"reps"}],
                 "sets":[{"label":"Round 1","target":"15 reps"},{"label":"Round 2","target":"12 reps"},
                         {"label":"Round 3","target":"9 reps"}]},
                {"name":"Air Squats","metrics":[{"key":"reps","label":"Reps","kind":"reps"}],
                 "sets":[{"label":"Round 1","target":"15 reps"},{"label":"Round 2","target":"12 reps"},
                         {"label":"Round 3","target":"9 reps"}]}
              ]}]}
            """.trimIndent()
        )!!
        val section = LiveWorkout.build(legacy).sections.single()
        assertEquals(
            InteractiveRows.scopes(legacy.sections.single(), 0),
            section.items.map { it.scope },
        )
        assertEquals(6, section.items.size)
        assertEquals(
            listOf("Power Snatch", "Air Squats", "Power Snatch", "Air Squats", "Power Snatch", "Air Squats"),
            section.items.map { it.name },
        )
        assertEquals("Round 1 of 3", section.items.first().position)
        assertFalse(section.isAmrap)
    }

    @Test
    fun `a metcon stored as plain exercises runs on a cap clock`() {
        val forTime = InteractivePlan.fromJson(
            """
            {"title":"X","summary":"","sections":[{"title":"Burpee test","kind":"metcon",
              "notes":"For time. Time cap: 10:00",
              "exercises":[{"name":"Burpees","sets":[{"label":"Result","target":"100 reps"}]}]}]}
            """.trimIndent()
        )!!
        val section = LiveWorkout.build(forTime).sections.single()
        assertTrue(section.clocked)
        assertEquals(600, section.timeCapSeconds)
        assertNull(section.intervalSeconds)
        assertEquals(1, section.items.size)
    }

    @Test
    fun `a section nobody reached is marked and stays out of the totals`() {
        val single = InteractivePlan.fromJson(
            """
            {"title":"X","sections":[{"title":"Squat","kind":"strength","exercises":[
              {"name":"Back Squat","metrics":[{"key":"reps","label":"Reps","kind":"reps"}],
               "sets":[{"label":"Set 1","target":"5 reps"},{"label":"Set 2","target":"5 reps"},
                       {"label":"Set 3","target":"5 reps"},{"label":"Set 4","target":"5 reps"},
                       {"label":"Set 5","target":"5 reps"},{"label":"Set 6","target":"5 reps"}]}]}]}
            """.trimIndent()
        )!!
        val section = LiveWorkout.build(single).sections.single()
        val results = LiveResults.markNotReached(emptyMap(), section)
        assertEquals(
            InteractiveReached.NONE,
            InteractiveResults.value(results, InteractiveKeys.marker(0), InteractiveReached.KEY),
        )
        assertEquals(
            -1,
            InteractiveReached.indexOf(results, 0, section.items.map { it.scope }),
        )
        val totals = JournalFeed.aiPlan(single, results)
        assertEquals(0, totals.loggedSets)
        assertEquals(0, totals.totalSets)
        assertEquals(6, totals.notReachedSets)
    }

    @Test
    fun `a row marker keeps the sets after it out of the totals`() {
        val single = InteractivePlan.fromJson(
            """
            {"title":"X","sections":[{"title":"Squat","kind":"strength","exercises":[
              {"name":"Back Squat","metrics":[{"key":"reps","label":"Reps","kind":"reps"}],
               "sets":[{"label":"Set 1","target":"5 reps"},{"label":"Set 2","target":"5 reps"},
                       {"label":"Set 3","target":"5 reps"},{"label":"Set 4","target":"5 reps"}]}]}]}
            """.trimIndent()
        )!!
        val section = LiveWorkout.build(single).sections.single()
        val results = InteractiveResults.set(
            emptyMap(),
            InteractiveKeys.marker(0),
            InteractiveReached.KEY,
            section.items[1].scope,
        )
        val totals = JournalFeed.aiPlan(single, results)
        assertEquals(2, totals.totalSets)
        assertEquals(2, totals.loggedSets)
        assertEquals(2, totals.notReachedSets)
    }

    @Test
    fun `a round-timed metcon gets a window per round and a rest`() {
        val timed = InteractivePlan.fromJson(
            """
            {"title":"Round Metcon","sections":[
              {"title":"Strength","kind":"strength","notes":"Every 2:00 x 4; build to heavy 3.",
               "exercises":[{"name":"Pull-Up","metrics":[{"key":"weight","label":"Weight","kind":"weight","unit":"kg"}],
                 "sets":[{"label":"Set 1","target":"3 reps"},{"label":"Set 2","target":"3 reps"}]}]},
              {"title":"Metcon","kind":"metcon",
               "notes":"4 x 5:00 rounds with 1:00 rest between rounds. Score is total burpees.",
               "metrics":[{"key":"total_burpees","label":"Total burpees","kind":"reps"}],
               "rounds":[
                 {"label":"Round 1","movements":[
                   {"name":"400m run","target":"400m","metrics":[{"key":"time","label":"Time","kind":"time"}]},
                   {"name":"Burpees","target":"max","metrics":[{"key":"reps","label":"Reps","kind":"reps"}]}]},
                 {"label":"Round 2","movements":[
                   {"name":"400m run","target":"400m","metrics":[{"key":"time","label":"Time","kind":"time"}]},
                   {"name":"Burpees","target":"max","metrics":[{"key":"reps","label":"Reps","kind":"reps"}]}]}
               ]}
            ]}
            """.trimIndent()
        )!!
        val built = LiveWorkout.build(timed, 90)
        val strength = built.sections[0]
        val metcon = built.sections[1]

        // The strength window is not a round window.
        assertEquals(120, strength.intervalSeconds)
        assertNull(strength.roundSeconds)
        assertFalse(strength.roundTimed)

        assertTrue(metcon.roundTimed)
        assertEquals(2, metcon.roundCount)
        assertEquals(300, metcon.roundSeconds)
        assertEquals(60, metcon.restSeconds)
        assertNull(metcon.timeCapSeconds)
        assertEquals(4, metcon.items.size)
        assertEquals("Round 1 of 2", metcon.items.first().position)
        assertEquals(InteractiveKeys.round(1, 0, 0), metcon.items.first().scope)
        assertEquals(MetricKind.TIME, metcon.items.first().metrics.first().kind)
    }

    @Test
    fun `round windows and rests are read in their usual forms`() {
        assertEquals(300, LiveTiming.roundSeconds("4 x 5:00 rounds"))
        assertEquals(300, LiveTiming.roundSeconds("4 rounds of 5:00"))
        assertEquals(300, LiveTiming.roundSeconds("4 rounds x 5 minutes"))
        assertEquals(300, LiveTiming.roundSeconds("5:00 rounds"))
        assertEquals(300, LiveTiming.roundSeconds("5 min rounds"))
        assertEquals(240, LiveTiming.roundSeconds("Every 4:00 x 6 (24:00)"))
        assertEquals(240, LiveTiming.roundSeconds("Every 4:00 x 6, alternating A & B"))
        assertEquals(240, LiveTiming.roundSeconds("every 4 min x 6 rounds"))
        assertNull(LiveTiming.roundSeconds("Every 2:00 x 4 sets"))

        assertEquals(60, LiveTiming.restSeconds("1:00 rest between rounds"))
        assertEquals(60, LiveTiming.restSeconds("rest 1 min"))
        assertEquals(45, LiveTiming.restSeconds("rest 45 seconds"))
        assertNull(LiveTiming.restSeconds("4 x 5:00 rounds"))
    }

    @Test
    fun `every 4 00 x 6 is six four-minute windows`() {
        val every = InteractivePlan.fromJson(
            """
            {"title":"Every 4:00 x 6 (A/B)","sections":[
              {"title":"Metcon","kind":"metcon",
               "notes":"Every 4:00 x 6 (24:00), alternating A & B. Split reps as desired on B.",
               "rounds":[
                 {"label":"Interval 1 - A","movements":[
                   {"name":"800m Row / 1600m Bike","target":"800m Row / 1600m Bike",
                    "metrics":[{"key":"time","label":"Time","kind":"time"}]}]},
                 {"label":"Interval 2 - B","movements":[
                   {"name":"Dual DB Push Press","target":"30 reps",
                    "metrics":[{"key":"reps","label":"Reps","kind":"reps"}]},
                   {"name":"Sit-Ups","target":"30 reps",
                    "metrics":[{"key":"reps","label":"Reps","kind":"reps"}]}]},
                 {"label":"Interval 3 - A","movements":[
                   {"name":"800m Row / 1600m Bike","target":"800m Row / 1600m Bike",
                    "metrics":[{"key":"time","label":"Time","kind":"time"}]}]},
                 {"label":"Interval 4 - B","movements":[
                   {"name":"Dual DB Push Press","target":"30 reps",
                    "metrics":[{"key":"reps","label":"Reps","kind":"reps"}]},
                   {"name":"Sit-Ups","target":"30 reps",
                    "metrics":[{"key":"reps","label":"Reps","kind":"reps"}]}]},
                 {"label":"Interval 5 - A","movements":[
                   {"name":"800m Row / 1600m Bike","target":"800m Row / 1600m Bike",
                    "metrics":[{"key":"time","label":"Time","kind":"time"}]}]},
                 {"label":"Interval 6 - B","movements":[
                   {"name":"Dual DB Push Press","target":"30 reps",
                    "metrics":[{"key":"reps","label":"Reps","kind":"reps"}]},
                   {"name":"Sit-Ups","target":"30 reps",
                    "metrics":[{"key":"reps","label":"Reps","kind":"reps"}]}]}
               ]}
            ]}
            """.trimIndent()
        )!!
        val metcon = LiveWorkout.build(every).sections.single()

        // Six 4:00 windows, not one 24:00 cap and not a per-set interval.
        assertTrue(metcon.roundTimed)
        assertEquals(6, metcon.roundCount)
        assertEquals(240, metcon.roundSeconds)
        assertNull(metcon.restSeconds)
        assertNull(metcon.timeCapSeconds)
        assertEquals(9, metcon.items.size)
        assertEquals("Round 1 of 6", metcon.items.first().position)
        assertEquals("800m Row / 1600m Bike", metcon.items.first().name)
        assertEquals("Round 2 of 6", metcon.items[1].position)
        assertEquals("Dual DB Push Press", metcon.items[1].name)
    }

    @Test
    fun `finishing a section clears its reached marker`() {
        val section = live.sections[0]
        val withMarker = InteractiveResults.set(
            emptyMap(),
            InteractiveKeys.marker(0),
            InteractiveReached.KEY,
            section.items[2].scope,
        )
        val finished = LiveResults.finish(
            results = withMarker,
            section = section,
            lastReachedRow = 5,
            completed = true,
            elapsedSeconds = null,
            roundsDone = null,
        )
        assertEquals(
            "",
            InteractiveResults.value(finished, InteractiveKeys.marker(0), InteractiveReached.KEY),
        )
    }

    @Test
    fun `a capped metcon keeps the last reached row and records the cap`() {
        val section = live.sections[1].copy(isAmrap = false)
        val finished = LiveResults.finish(
            results = emptyMap(),
            section = section,
            lastReachedRow = 1,
            completed = false,
            elapsedSeconds = 720,
            roundsDone = 4,
        )
        assertEquals(
            section.items[1].scope,
            InteractiveResults.value(finished, InteractiveKeys.marker(1), InteractiveReached.KEY),
        )
        assertEquals(
            "12:00",
            InteractiveResults.value(finished, InteractiveKeys.section(1), "total_time"),
        )
        assertEquals(
            "4",
            InteractiveResults.value(finished, InteractiveKeys.section(1), "rounds"),
        )
    }

    @Test
    fun `an AMRAP never leaves a reached marker`() {
        val section = live.sections[1]
        val finished = LiveResults.finish(
            results = emptyMap(),
            section = section,
            lastReachedRow = 2,
            completed = false,
            elapsedSeconds = 720,
            roundsDone = 7,
        )
        assertEquals(
            "",
            InteractiveResults.value(finished, InteractiveKeys.marker(1), InteractiveReached.KEY),
        )
        assertEquals(
            "7",
            InteractiveResults.value(finished, InteractiveKeys.section(1), "rounds"),
        )
    }

    @Test
    fun `a strength section never writes a section score`() {
        val section = live.sections[0]
        val scored = section.copy(metrics = listOf(MetricDef("total_time", "Time", MetricKind.TIME)))
        val finished = LiveResults.finish(
            results = emptyMap(),
            section = scored,
            lastReachedRow = 0,
            completed = false,
            elapsedSeconds = 100,
            roundsDone = null,
        )
        assertFalse(finished.containsKey(InteractiveKeys.section(0)))
    }

    @Test
    fun `live clock formatting rounds a countdown up and elapsed down`() {
        assertEquals("1:30", formatLiveClock(90_000))
        assertEquals("1:30", formatLiveClock(89_100))
        assertEquals("0:01", formatLiveClock(100))
        assertEquals("0:00", formatLiveClock(0))
        assertEquals("1:30", formatLiveElapsed(90_000))
        assertEquals("1:29", formatLiveElapsed(89_900))
        assertEquals("1:00:00", formatLiveElapsed(3_600_000))
    }
}
