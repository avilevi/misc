package com.hcexport.wear.live

import com.hcexport.InteractiveExercise
import com.hcexport.InteractiveKeys
import com.hcexport.InteractiveMovement
import com.hcexport.InteractivePlan
import com.hcexport.InteractiveReached
import com.hcexport.InteractiveRound
import com.hcexport.InteractiveResults
import com.hcexport.InteractiveSection
import com.hcexport.InteractiveSet
import com.hcexport.LiveWorkout
import com.hcexport.MetricDef
import com.hcexport.MetricKind
import com.hcexport.shared.DEVICE_PHONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSessionTest {

    private val weight = MetricDef("weight", "Weight", MetricKind.WEIGHT, "kg")
    private val reps = MetricDef("reps", "Reps", MetricKind.REPS, "reps")
    private val time = MetricDef("time", "Time", MetricKind.TIME)
    private val rounds = MetricDef("rounds", "Rounds", MetricKind.ROUNDS)

    private fun strengthPlan(): InteractivePlan {
        val snatch = InteractiveExercise(
            name = "Snatch",
            metrics = listOf(weight, reps),
            sets = (1..3).map { InteractiveSet(label = "Set $it", target = "5 reps") },
        )
        return InteractivePlan(
            title = "Snatch day",
            sections = listOf(
                InteractiveSection(
                    title = "Snatch",
                    kind = "strength",
                    notes = "Every 1:30 x 3 sets",
                    exercises = listOf(snatch),
                ),
            ),
        )
    }

    private fun roundPlan(): InteractivePlan = InteractivePlan(
        title = "Round clock",
        sections = listOf(
            InteractiveSection(
                title = "Erg intervals",
                kind = "metcon",
                notes = "4 x 5:00 rounds with 1:00 rest",
                rounds = (1..4).map { round ->
                    InteractiveRound(
                        label = "Round $round",
                        movements = listOf(
                            InteractiveMovement(
                                name = "Row",
                                target = "500m",
                                metrics = listOf(time),
                            ),
                            InteractiveMovement(
                                name = "Squats",
                                target = "15 reps",
                                metrics = listOf(reps),
                            ),
                        ),
                    )
                },
            ),
        ),
    )

    private fun amrapPlan(): InteractivePlan = InteractivePlan(
        title = "AMRAP",
        sections = listOf(
            InteractiveSection(
                title = "AMRAP",
                kind = "metcon",
                notes = "8:00 AMRAP",
                metrics = listOf(time, rounds),
                rounds = listOf(
                    InteractiveRound(
                        label = "Each round",
                        movements = listOf(
                            InteractiveMovement("Pull-ups", "5", metrics = listOf(reps)),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun session(plan: InteractivePlan) = LiveSession(
        live = LiveWorkout.build(plan, defaultIntervalSeconds = 90),
        leadInSeconds = 0,
    )

    @Test
    fun strengthSetsAdvanceOnTheIntervalAndKeepThePrescription() {
        val session = session(strengthPlan())
        assertEquals(LivePhase.ACTIVE, session.phase)
        assertEquals(0, session.itemIndex)
        assertEquals("Set 1 of 3", session.item?.position)

        // The athlete types a weight; advancing writes what the row showed.
        val item = session.item!!
        session.setValue(item, weight, "60")
        assertEquals("60", session.resolvedValue(item, weight))

        session.tick(90_000)
        assertEquals(1, session.itemIndex)
        assertEquals("5", session.resolvedValue(session.item!!, reps))

        // The reached marker follows the live row.
        val marker = session.results[InteractiveKeys.marker(0)]?.get(InteractiveReached.KEY)
        assertEquals(InteractiveKeys.set(0, 0, 1), marker)
    }

    @Test
    fun anIntervalSetMarkedDoneKeepsItsWindowRunning() {
        val session = session(strengthPlan())
        session.completeAndAdvance()
        assertTrue(session.itemDone)
        assertEquals(0, session.itemIndex)
        session.tick(10_000)
        assertEquals(0, session.itemIndex)
        session.tick(80_000)
        assertEquals(1, session.itemIndex)
        assertTrue(!session.itemDone)
    }

    @Test
    fun roundTimedWorkRestsThenStartsTheNextRound() {
        val session = session(roundPlan())
        assertEquals(LivePhase.ACTIVE, session.phase)
        assertEquals(4, session.section.roundCount)
        assertEquals(300_000L, session.intervalRemainingMs)

        session.completeAndAdvance() // row 1 of round 1
        assertEquals(1, session.itemIndex)
        session.completeAndAdvance() // last row: the round work is done
        assertTrue(session.roundWorkDone)

        session.tick(300_000)
        assertTrue(session.resting)
        assertEquals(60_000L, session.restRemainingMs)

        session.skipRest()
        assertEquals(1, session.currentRound)
        assertEquals(2, session.itemIndex) // first row of round 2
        assertEquals(300_000L, session.intervalRemainingMs)
    }

    @Test
    fun amrapLoopsItsRoundAndWritesTheScoreAtTheCap() {
        val session = session(amrapPlan())
        val item = session.item!!
        session.completeAndAdvance()
        assertEquals(1, session.roundsDone)
        assertEquals(0, session.itemIndex)

        session.tick(480_000)
        assertEquals(LivePhase.DONE, session.phase)
        assertEquals("1", session.results[InteractiveKeys.section(0)]?.get("rounds"))
        // An AMRAP's clock is fixed, so it is not a result; the boundary of
        // the unfinished round is what the score needs.
        assertEquals(null, session.results[InteractiveKeys.section(0)]?.get("time"))
        assertEquals("0", session.results[InteractiveKeys.section(0)]?.get("partial"))
    }

    @Test
    fun leavingEarlyMarksUnreachedSectionsButKeepsWhatWasEntered() {
        val plan = InteractivePlan(
            title = "Two parts",
            sections = listOf(
                InteractiveSection(
                    title = "Part one",
                    kind = "strength",
                    exercises = listOf(
                        InteractiveExercise(
                            name = "Deadlift",
                            metrics = listOf(weight, reps),
                            sets = listOf(InteractiveSet(target = "5 reps")),
                        ),
                    ),
                ),
                InteractiveSection(
                    title = "Part two",
                    kind = "metcon",
                    notes = "10:00 AMRAP",
                    rounds = listOf(
                        InteractiveRound(
                            label = "Each round",
                            movements = listOf(
                                InteractiveMovement("Burpees", "10", metrics = listOf(reps)),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val session = session(plan)
        session.setValue(session.item!!, weight, "100")
        session.finalizeBeforeExit()

        assertEquals(
            "100",
            session.results[InteractiveKeys.set(0, 0, 0)]?.get("weight"),
        )
        assertEquals(
            InteractiveReached.NONE,
            session.results[InteractiveKeys.marker(1)]?.get(InteractiveReached.KEY),
        )
    }

    @Test
    fun adoptingDoesNotEraseAValueThePhoneNeverSaw() {
        val live = LiveWorkout.build(strengthPlan(), 90)
        val watch = LiveSession(live, 0)
        watch.setValue(watch.item!!, weight, "62.5")

        val phoneSession = LiveSession(live, 0)
        phoneSession.setValue(phoneSession.item!!, weight, "80")
        val phoneResults = phoneSession.toDto("2026-11-21", "weightlifting", 42L)
        // The phone's base and current agree: it never saw the watch's 62.5.
        val dto = phoneResults.copy(
            origin = DEVICE_PHONE,
            updatedAt = System.currentTimeMillis(),
            baseResultsJson = phoneResults.resultsJson,
        )

        watch.adopt(dto)
        assertEquals(
            "62.5",
            watch.results[InteractiveKeys.set(0, 0, 0)]?.get("weight"),
        )
    }

    @Test
    fun adoptingThePhonesRecordPutsTheWatchOnTheSameStage() {
        val plan = strengthPlan()
        val live = LiveWorkout.build(plan, 90)
        val phoneSession = LiveSession(live, 0)
        phoneSession.setValue(phoneSession.item!!, weight, "80")
        phoneSession.tick(90_000) // phone is on set 2
        val dto = phoneSession.toDto("2026-11-21", "weightlifting", 42L)
            .copy(origin = DEVICE_PHONE, updatedAt = System.currentTimeMillis())

        val watch = LiveSession(live, 0)
        watch.adopt(dto)

        assertEquals(1, watch.itemIndex)
        assertEquals(LivePhase.ACTIVE, watch.phase)
        assertEquals("80", watch.results[InteractiveKeys.set(0, 0, 0)]?.get("weight"))
        assertEquals(dto.sessionId, watch.syncSessionId)
        // Adoption must not look like a new local stage.
        watch.lastSyncFingerprint = watch.syncFingerprint()
        assertEquals(watch.lastSyncFingerprint, watch.syncFingerprint())
    }
}
