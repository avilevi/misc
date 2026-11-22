package com.hcexport

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalFeedTest {

    private val date = "2026-11-17"
    private val base = 1_000_000_000_000L
    private val hour = 3_600_000L
    private val minute = 60_000L
    private var eventId = 0L

    // ── Fixtures ────────────────────────────────────────────────────────────

    private fun workout(
        typeKey: String,
        label: String,
        content: String,
        order: Int = 0,
        day: String = date,
    ) = Workout(
        date = day,
        typeKey = typeKey,
        typeLabel = label,
        content = content,
        sortOrder = order,
    )

    private fun event(
        title: String,
        startMs: Long,
        endMs: Long,
        day: String = date,
    ) = CalendarWorkoutEvent(
        id = ++eventId,
        title = title,
        startMs = startMs,
        endMs = endMs,
        date = day,
        description = null,
    )

    private fun exerciseEntry(
        id: String,
        startMs: Long,
        endMs: Long,
        typeCode: Int = 61,
        title: String = "",
        distanceM: Double? = null,
        avgHr: Double? = null,
        sourcePkg: String = "com.sec.android.app.shealth",
    ): JournalEntry {
        val json = JSONObject().apply {
            put("sm", startMs)
            put("em", endMs)
            put("tc", typeCode)
            put("tt", title)
            put("sp", sourcePkg)
            distanceM?.let { put("dm", it) }
            avgHr?.let { put("ah", it) }
        }
        return JournalEntry(
            id = id,
            date = date,
            entryType = "exercise",
            originalDataJson = json.toString(),
            customDataJson = null,
            customNarrative = null,
            createdAtMs = 0L,
            updatedAtMs = 0L,
        )
    }

    private fun wodEntry(
        id: String = "wod-$date",
        startMs: Long,
        endMs: Long,
        content: String,
        narrative: String? = null,
    ): JournalEntry {
        val json = JSONObject().apply {
            put("sm", startMs)
            put("em", endMs)
            put("date", date)
            put("wod", content)
            put("sp", "wod")
        }
        return JournalEntry(
            id = id,
            date = date,
            entryType = "wod",
            originalDataJson = json.toString(),
            customDataJson = null,
            customNarrative = narrative,
            createdAtMs = 0L,
            updatedAtMs = 0L,
        )
    }

    private fun sleepEntry(
        id: String = "sleep-$date",
        startMs: Long,
        endMs: Long,
        stages: List<Pair<Int, Long>>,
        source: String = "com.sec.android.app.shealth",
    ): JournalEntry {
        val json = JSONObject().apply {
            put("sm", startMs)
            put("em", endMs)
            put("sp", source)
            put("sg", JSONArray(stages.map { (code, lengthMs) ->
                JSONObject().put("sc", code).put("sm", startMs).put("em", startMs + lengthMs)
            }))
        }
        return JournalEntry(
            id = id,
            date = date,
            entryType = "sleep",
            originalDataJson = json.toString(),
            customDataJson = null,
            customNarrative = null,
            createdAtMs = 0L,
            updatedAtMs = 0L,
        )
    }

    private fun session(
        typeKey: String,
        planJson: String,
        resultsJson: String = "{}",
        model: String = "deepseek-flash",
    ) = InteractiveSessionRecord(
        date = date,
        typeKey = typeKey,
        planJson = planJson,
        resultsJson = resultsJson,
        model = model,
        createdAtMs = 0L,
        updatedAtMs = 0L,
    )

    private fun day(
        workouts: List<Workout> = emptyList(),
        events: List<CalendarWorkoutEvent> = emptyList(),
        entries: List<JournalEntry> = emptyList(),
        sessions: Map<String, InteractiveSessionRecord> = emptyMap(),
        notes: Map<String, String> = emptyMap(),
        sleepSourcePriority: List<String> = emptyList(),
        scheduled: List<ScheduledSession> = emptyList(),
        hiddenKeys: Set<String> = emptySet(),
    ) = JournalFeed.buildDay(
        date = date,
        workouts = workouts,
        calendarEvents = events,
        entries = entries,
        sessions = sessions,
        notes = notes,
        sleepSourcePriority = sleepSourcePriority,
        scheduledSessions = scheduled,
        hiddenKeys = hiddenKeys,
    )

    private val plannerJson = """
        {
          "title": "Heavy Squat",
          "summary": "Five by five",
          "sections": [
            {
              "title": "Strength",
              "kind": "strength",
              "metrics": [],
              "exercises": [
                {
                  "name": "Back Squat",
                  "metrics": [
                    {"key": "weight", "label": "Weight", "kind": "weight", "unit": "kg"},
                    {"key": "reps", "label": "Reps", "kind": "reps"}
                  ],
                  "sets": [
                    {"label": "Set 1", "target": "5 reps"},
                    {"label": "Set 2", "target": "5 reps"}
                  ]
                }
              ]
            }
          ]
        }
    """.trimIndent()

    // ── What belongs in the journal ─────────────────────────────────────────

    @Test
    fun `a workout with no calendar, watch or AI evidence is omitted`() {
        val day = day(workouts = listOf(workout("hyrox", "🫁HYROX", "HYROX text")))
        assertTrue(day.items.isEmpty())
    }

    @Test
    fun `an AI plan without values is not enough to enter the journal`() {
        val day = day(
            workouts = listOf(workout("pumping", "PUMP", "Five by five")),
            sessions = mapOf("pumping" to session("pumping", plannerJson)),
        )
        assertTrue(day.items.isEmpty())
    }

    @Test
    fun `an AI session with values includes the workout without a calendar event`() {
        val results = """{"e:0:0:0":{"weight":"100","reps":"5"}}"""
        val item = day(
            workouts = listOf(workout("pumping", "PUMP", "Five by five")),
            sessions = mapOf("pumping" to session("pumping", plannerJson, results)),
        ).items.single() as JournalItem.Workout

        assertNotNull(item.plan)
        // One value entered and no "reached here" marker: the whole plan counts.
        assertEquals(2, item.plan!!.loggedSets)
        assertNull(item.startMs)
    }

    @Test
    fun `a calendar event names the workout and gives it its time`() {
        val start = base + 7 * hour
        val end = base + 8 * hour
        val item = day(
            workouts = listOf(workout("hyrox", "🫁HYROX", "HYROX text")),
            events = listOf(event("HYROX", start, end)),
        ).items.single() as JournalItem.Workout

        assertEquals("hyrox", item.typeKey)
        assertEquals(start, item.startMs)
        assertEquals(end, item.endMs)
        assertNull(item.plan)
        assertEquals("HYROX text", item.content)
    }

    @Test
    fun `a scheduled workout enters the journal without a calendar event`() {
        val start = base + 7 * hour
        val end = start + hour
        val item = day(
            workouts = listOf(workout("pump", "PUMP", "Five by five")),
            scheduled = listOf(
                ScheduledSession(date, "pump", start, end, createdAtMs = 1L),
            ),
        ).items.single() as JournalItem.Workout

        assertTrue(item.scheduled)
        assertEquals("pump", item.typeKey)
        assertEquals(start, item.startMs)
        assertEquals(end, item.endMs)
    }

    @Test
    fun `a scheduled workout does not absorb a watch session without a calendar event`() {
        val start = base + 7 * hour
        val day = day(
            workouts = listOf(workout("pump", "PUMP", "Five by five")),
            scheduled = listOf(
                ScheduledSession(date, "pump", start, start + hour, createdAtMs = 1L),
            ),
            entries = listOf(
                exerciseEntry("watch-1", start + 10 * minute, start + 50 * minute),
            ),
        )

        val workout = day.items.filterIsInstance<JournalItem.Workout>().single()
        val activity = day.items.filterIsInstance<JournalItem.Activity>().single()
        assertTrue(workout.scheduled)
        assertTrue(workout.biometrics.isEmpty())
        assertEquals("watch-1", activity.biometrics.entryId)
    }

    @Test
    fun `a scheduled workout with a calendar event merges the session as usual`() {
        val start = base + 7 * hour
        val day = day(
            workouts = listOf(workout("pump", "PUMP", "Five by five")),
            events = listOf(event("PUMP", start, start + hour)),
            scheduled = listOf(
                ScheduledSession(date, "pump", start, start + hour, createdAtMs = 1L),
            ),
            entries = listOf(
                exerciseEntry("watch-1", start + 10 * minute, start + 50 * minute),
            ),
        )

        val workout = day.items.filterIsInstance<JournalItem.Workout>().single()
        assertEquals(1, workout.biometrics.size)
        assertEquals("watch-1", workout.biometrics.single().entryId)
    }

    @Test
    fun `a hidden workout stays out of the day and the month marks`() {
        val start = base + 7 * hour
        val hidden = setOf(JournalKeys.workout(date, "pump"))
        val theDay = day(
            workouts = listOf(workout("pump", "PUMP", "Five by five")),
            events = listOf(event("PUMP", start, start + hour)),
            hiddenKeys = hidden,
        )
        assertTrue(theDay.items.isEmpty())

        val marks = JournalFeed.marks(
            workouts = listOf(workout("pump", "PUMP", "Five by five")),
            calendarEvents = listOf(event("PUMP", start, start + hour)),
            entries = emptyList(),
            sessions = emptyList(),
            notes = emptyMap(),
            hiddenKeys = hidden,
        )
        assertTrue(marks[date]?.workoutTypes.isNullOrEmpty())
    }

    @Test
    fun `a hidden watch session leaves no mark`() {
        val entry = exerciseEntry("watch-hidden", base, base + hour)
        val marks = JournalFeed.marks(
            workouts = emptyList(),
            calendarEvents = emptyList(),
            entries = listOf(entry),
            sessions = emptyList(),
            notes = emptyMap(),
            hiddenKeys = setOf(JournalKeys.activity(entry.id)),
        )
        assertTrue(marks[date]?.hasWatchData != true)
    }

    @Test
    fun `the month marks a scheduled day with no calendar event`() {
        val marks = JournalFeed.marks(
            workouts = listOf(workout("pump", "PUMP", "Five by five")),
            calendarEvents = emptyList(),
            entries = emptyList(),
            sessions = emptyList(),
            notes = emptyMap(),
            scheduledTypeKeysByDate = mapOf(date to setOf("pump")),
        )

        assertEquals(listOf("pump"), marks.getValue(date).workoutTypes)
    }

    @Test
    fun `only the workouts with evidence survive on a multi-workout day`() {
        val day = day(
            workouts = listOf(
                workout("wod", "🔥WOD", "WOD text", order = 0),
                workout("hyrox", "🫁HYROX", "HYROX text", order = 1),
                workout("pump", "PUMP", "Pump text", order = 2),
            ),
            events = listOf(event("WOD", base + 18 * hour, base + 19 * hour)),
            sessions = mapOf(
                "pump" to session(
                    "pump",
                    plannerJson,
                    """{"e:0:0:0":{"weight":"60","reps":"8"}}""",
                ),
            ),
        )

        val types = day.items.filterIsInstance<JournalItem.Workout>().map { it.typeKey }
        assertEquals(listOf("wod", "pump"), types)
    }

    // ── Name matching ───────────────────────────────────────────────────────

    @Test
    fun `event titles match workout names on any calendar`() {
        val workouts = listOf(
            workout("wod", "🔥WOD", "A", order = 0),
            workout("hyrox", "🫁HYROX", "B", order = 1),
            workout("pump", "Pump", "C", order = 2),
            workout("pump_blend", "Pump Blend", "D", order = 3),
            workout("weightlifting", "Weightlifting", "E", order = 4),
        )
        val events = listOf(
            event("WOD", base, base + hour),
            event("CrossFit WOD 8:00", base + 2 * hour, base + 3 * hour),
            event("HYROX", base + 4 * hour, base + 5 * hour),
            event("Pump Blend", base + 6 * hour, base + 7 * hour),
            event("Weightlifting Class", base + 8 * hour, base + 9 * hour),
        )

        val matches = JournalFeed.calendarMatches(workouts, events)
        assertEquals(2, matches.getValue("wod").size)
        assertEquals(1, matches.getValue("hyrox").size)
        // "Pump Blend" must land on the blend, not on plain Pump.
        assertFalse(matches.containsKey("pump"))
        assertEquals(1, matches.getValue("pump_blend").size)
        assertEquals("Weightlifting Class", matches.getValue("weightlifting").single().title)
    }

    @Test
    fun `plain pump events still match plain pump`() {
        val workouts = listOf(
            workout("pump", "Pump", "C", order = 0),
            workout("pump_blend", "Pump Blend", "D", order = 1),
        )
        val matches = JournalFeed.calendarMatches(
            workouts,
            listOf(event("Morning Pump", base, base + hour)),
        )
        assertEquals(1, matches.getValue("pump").size)
        assertFalse(matches.containsKey("pump_blend"))
    }

    @Test
    fun `a calendar event for which there is no workout is ignored`() {
        val matches = JournalFeed.calendarMatches(
            listOf(workout("wod", "🔥WOD", "A")),
            listOf(event("Dentist", base, base + hour), event("Lunch with Dana", base, base + hour)),
        )
        assertTrue(matches.isEmpty())
    }

    // ── AI plan ─────────────────────────────────────────────────────────────

    @Test
    fun `ai plan counts the values that were entered`() {
        val results = """
            {"e:0:0:0":{"weight":"100","reps":"5"},"e:0:0:1":{"weight":"105","reps":"5"}}
        """.trimIndent()
        val item = day(
            workouts = listOf(workout("pumping", "PUMP", "Five by five")),
            sessions = mapOf("pumping" to session("pumping", plannerJson, results)),
        ).items.single() as JournalItem.Workout

        val plan = item.plan
        assertNotNull(plan)
        assertEquals("Heavy Squat", plan!!.title)
        assertEquals(2, plan.totalSets)
        assertEquals(2, plan.loggedSets)
        assertEquals(1025.0, plan.totalVolumeKg!!, 0.001)
    }

    @Test
    fun `a calendar workout with an unlogged plan is shown`() {
        val item = day(
            workouts = listOf(workout("pumping", "PUMP", "Five by five")),
            events = listOf(event("PUMP", base, base + hour)),
            sessions = mapOf("pumping" to session("pumping", plannerJson)),
        ).items.single() as JournalItem.Workout

        val plan = item.plan
        assertNotNull(plan)
        assertEquals(2, plan!!.totalSets)
        assertEquals(0, plan.loggedSets)
        assertNull(plan.totalVolumeKg)
    }

    @Test
    fun `a calendar workout without a plan keeps its original text`() {
        val item = day(
            workouts = listOf(workout("wod", "🔥WOD", "21-15-9 Thrusters")),
            events = listOf(event("WOD", base + 18 * hour, base + 19 * hour)),
        ).items.single() as JournalItem.Workout

        assertNull(item.plan)
        assertEquals("21-15-9 Thrusters", item.content)
        assertTrue(item.canOpenAi)
    }

    // ── Merging watch data ──────────────────────────────────────────────────

    @Test
    fun `calendar plus watch plus AI become one entry`() {
        val anchorStart = base + 18 * hour
        val anchorEnd = base + 19 * hour
        val day = day(
            workouts = listOf(workout("wod", "🔥WOD", "21-15-9 Thrusters")),
            events = listOf(event("WOD", anchorStart, anchorEnd)),
            entries = listOf(
                wodEntry(startMs = anchorStart, endMs = anchorEnd, content = "21-15-9 Thrusters"),
                exerciseEntry("e1", anchorStart + 5 * minute, anchorEnd - 5 * minute),
            ),
            sessions = mapOf(
                "wod" to session(
                    "wod",
                    plannerJson,
                    """{"e:0:0:0":{"weight":"80","reps":"5"}}""",
                ),
            ),
        )

        val item = day.items.single() as JournalItem.Workout
        assertEquals("w:$date:wod", item.key)
        assertEquals(anchorStart, item.startMs)
        assertEquals(anchorEnd, item.endMs)
        assertEquals(1, item.biometrics.size)
        assertEquals(50, item.biometrics.single().durationMin)
        assertEquals(50L, item.durationMin)
        assertEquals(2, item.plan!!.loggedSets)
        assertEquals(400.0, item.plan!!.totalVolumeKg!!, 0.001)
    }

    @Test
    fun `a session merges into the workout it overlaps the most`() {
        val day = day(
            workouts = listOf(
                workout("hyrox", "🫁HYROX", "HYROX text", order = 0),
                workout("wod", "🔥WOD", "WOD text", order = 1),
            ),
            events = listOf(
                event("HYROX", base + 6 * hour, base + 7 * hour),
                event("WOD", base + 18 * hour, base + 19 * hour),
            ),
            entries = listOf(
                exerciseEntry("morning", base + 6 * hour + 10 * minute, base + 6 * hour + 50 * minute),
                exerciseEntry("evening", base + 18 * hour + 20 * minute, base + 19 * hour + 20 * minute),
            ),
        )

        val workouts = day.items.filterIsInstance<JournalItem.Workout>()
        val hyrox = workouts.first { it.typeKey == "hyrox" }
        val wod = workouts.first { it.typeKey == "wod" }
        assertEquals("morning", hyrox.biometrics.single().entryId)
        assertEquals("evening", wod.biometrics.single().entryId)
    }

    @Test
    fun `a session outside the only workout's window stays its own activity`() {
        val day = day(
            workouts = listOf(workout("wod", "🔥WOD", "21-15-9 Thrusters")),
            events = listOf(event("WOD", base + 12 * hour, base + 13 * hour)),
            entries = listOf(exerciseEntry("e1", base, base + hour)),
        )

        // The watch recorded something else that day: it is not the WOD, even
        // though the WOD is the only workout.
        val wod = day.items.filterIsInstance<JournalItem.Workout>().single()
        assertTrue(wod.biometrics.isEmpty())
        assertEquals("e1", day.items.filterIsInstance<JournalItem.Activity>().single().entryId)

        // A session that does overlap the window is the one that merges.
        val withWod = day(
            workouts = listOf(workout("wod", "🔥WOD", "21-15-9 Thrusters")),
            events = listOf(event("WOD", base + 12 * hour, base + 13 * hour)),
            entries = listOf(
                exerciseEntry("e1", base, base + hour),
                exerciseEntry("e2", base + 12 * hour + 5 * minute, base + 12 * hour + 55 * minute),
            ),
        )
        val merged = withWod.items.filterIsInstance<JournalItem.Workout>().single()
        assertEquals(listOf("e2"), merged.biometrics.map { it.entryId })
        assertEquals("e1", withWod.items.filterIsInstance<JournalItem.Activity>().single().entryId)
        assertEquals(50L, merged.durationMin)
    }

    @Test
    fun `a workout entry takes a single watch session`() {
        val day = day(
            workouts = listOf(workout("wod", "🔥WOD", "21-15-9 Thrusters")),
            events = listOf(event("WOD", base + 12 * hour, base + 13 * hour)),
            entries = listOf(
                exerciseEntry("strength", base + 12 * hour + 5 * minute, base + 12 * hour + 45 * minute),
                exerciseEntry("kickboxing", base + 12 * hour + 50 * minute, base + 14 * hour, typeCode = 34),
            ),
        )

        val wod = day.items.filterIsInstance<JournalItem.Workout>().single()
        // The bigger overlap books the entry; the other session is not bundled in.
        assertEquals(listOf("strength"), wod.biometrics.map { it.entryId })
        assertEquals(40L, wod.durationMin)
        assertEquals("kickboxing", day.items.filterIsInstance<JournalItem.Activity>().single().entryId)
    }

    @Test
    fun `only an event naming a workout merges with a watch session`() {
        val start = base + 19 * hour
        val end = start + 45 * minute
        val merged = JournalFeed.journalEventsForSession(
            workouts = listOf(workout("wod", "🔥WOD", "Text")),
            events = listOf(
                event("WOD", start, end),
                event("זום עם מירי", start + 10 * minute, end - 5 * minute),
                event("Dentist", start - hour, end),
                event("Lunch with Dana", start, end),
            ),
            startMs = start,
            endMs = end,
        )

        assertEquals(listOf("WOD"), merged.map { it.title })
    }

    @Test
    fun `an unmatched session on a multi-workout day stays its own entry`() {
        val day = day(
            workouts = listOf(
                workout("wod", "🔥WOD", "WOD text", order = 0),
                workout("hyrox", "🫁HYROX", "HYROX text", order = 1),
            ),
            events = listOf(
                event("WOD", base + 12 * hour, base + 13 * hour),
                event("HYROX", base + 10 * hour, base + 11 * hour),
            ),
            entries = listOf(exerciseEntry("e1", base, base + hour)),
        )

        val wod = day.items.filterIsInstance<JournalItem.Workout>()
            .first { it.typeKey == "wod" }
        assertTrue(wod.biometrics.isEmpty())
        assertEquals("e1", day.items.filterIsInstance<JournalItem.Activity>().single().entryId)
    }

    @Test
    fun `a session with no workout is an activity with its metrics`() {
        val item = day(
            entries = listOf(
                exerciseEntry(
                    "run-1",
                    base,
                    base + 45 * minute,
                    typeCode = 48,
                    title = "Morning Run",
                    distanceM = 8240.0,
                    avgHr = 148.0,
                ),
            ),
        ).items.single() as JournalItem.Activity

        assertEquals("e:run-1", item.key)
        assertEquals("Morning Run", item.biometrics.title)
        assertEquals(45, item.biometrics.durationMin)
        assertEquals(8240.0, item.biometrics.distanceM!!, 0.001)
        assertEquals(148.0, item.biometrics.avgHrBpm!!, 0.001)
    }

    // ── Legacy WOD entries ──────────────────────────────────────────────────

    @Test
    fun `a WOD entry without an archive row is still shown`() {
        val item = day(
            entries = listOf(
                wodEntry(startMs = base, endMs = base + hour, content = "Original", narrative = "Edited text"),
            ),
        ).items.single() as JournalItem.Workout

        assertEquals("w:$date:wod", item.key)
        assertEquals("Edited text", item.content)
        assertFalse(item.canOpenAi)
    }

    @Test
    fun `a legacy WOD merges the watch session that overlaps it`() {
        val day = day(
            entries = listOf(
                wodEntry(startMs = base, endMs = base + hour, content = "Original"),
                exerciseEntry("e1", base + 10 * minute, base + 50 * minute),
            ),
        )

        val item = day.items.single() as JournalItem.Workout
        assertEquals(1, item.biometrics.size)
        assertTrue(day.items.none { it is JournalItem.Activity })
    }

    @Test
    fun `a legacy WOD merges only the session it overlaps the most`() {
        val day = day(
            entries = listOf(
                wodEntry(startMs = base, endMs = base + hour, content = "Original"),
                exerciseEntry("small", base, base + 10 * minute),
                exerciseEntry("big", base + 15 * minute, base + 55 * minute),
                exerciseEntry("outside", base + 3 * hour, base + 4 * hour),
            ),
        )

        val item = day.items.filterIsInstance<JournalItem.Workout>().single()
        assertEquals("big", item.biometrics.single().entryId)
        assertEquals(
            setOf("small", "outside"),
            day.items.filterIsInstance<JournalItem.Activity>().map { it.entryId }.toSet(),
        )
    }

    @Test
    fun `two legacy WOD entries on one day keep separate keys`() {
        val day = day(
            entries = listOf(
                wodEntry(id = "wod-a", startMs = base, endMs = base + hour, content = "First"),
                wodEntry(id = "wod-b", startMs = base + 2 * hour, endMs = base + 3 * hour, content = "Second"),
            ),
        )

        val keys = day.items.filterIsInstance<JournalItem.Workout>().map { it.key }
        assertEquals(2, keys.size)
        assertEquals(2, keys.distinct().size)
        assertEquals("w:$date:wod", keys.first())
    }

    @Test
    fun `a WOD entry is not duplicated when the archive has the workout`() {
        val day = day(
            workouts = listOf(workout("wod", "🔥WOD", "Same text")),
            events = listOf(event("WOD", base, base + hour)),
            entries = listOf(
                wodEntry(startMs = base, endMs = base + hour, content = "Same text"),
                exerciseEntry("e1", base + 10 * minute, base + 50 * minute),
            ),
        )

        assertEquals(1, day.items.size)
        assertTrue(day.items.single() is JournalItem.Workout)
        assertEquals(1, (day.items.single() as JournalItem.Workout).biometrics.size)
    }

    // ── Sleep and notes ─────────────────────────────────────────────────────

    @Test
    fun `sleep stages are summed and ordered`() {
        val item = day(
            entries = listOf(
                sleepEntry(
                    startMs = base,
                    endMs = base + 8 * hour,
                    stages = listOf(
                        4 to 120 * minute, // Light
                        5 to 60 * minute,  // Deep
                        6 to 90 * minute,  // REM
                        5 to 30 * minute,  // Deep again
                    ),
                ),
            ),
        ).items.single() as JournalItem.Sleep

        assertEquals(480, item.durationMin)
        assertEquals(listOf("Deep Sleep", "REM", "Light Sleep"), item.stages.map { it.name })
        assertEquals(listOf(90L, 90L, 120L), item.stages.map { it.minutes })
    }

    @Test
    fun `one night recorded by two sources is shown once`() {
        val item = day(
            entries = listOf(
                sleepEntry(
                    id = "samsung",
                    startMs = base,
                    endMs = base + 8 * hour,
                    stages = listOf(4 to 8 * hour),
                ),
                sleepEntry(
                    id = "fitbit",
                    startMs = base + 3 * minute,
                    endMs = base + 8 * hour + 3 * minute,
                    stages = listOf(5 to 8 * hour),
                    source = "com.fitbit.FitbitMobile",
                ),
            ),
            sleepSourcePriority = listOf(
                "com.fitbit.FitbitMobile",
                "com.sec.android.app.shealth",
            ),
        ).items.single() as JournalItem.Sleep

        assertEquals("fitbit", item.entryId)
        assertEquals("com.fitbit.FitbitMobile", item.sourcePkg)
        assertEquals(480, item.durationMin)
    }

    @Test
    fun `without a priority list the earliest recording is shown`() {
        val item = day(
            entries = listOf(
                sleepEntry(
                    id = "fitbit",
                    startMs = base + 3 * minute,
                    endMs = base + 8 * hour + 3 * minute,
                    stages = emptyList(),
                    source = "com.fitbit.FitbitMobile",
                ),
                sleepEntry(
                    id = "samsung",
                    startMs = base,
                    endMs = base + 8 * hour,
                    stages = emptyList(),
                ),
            ),
        ).items.single() as JournalItem.Sleep

        assertEquals("samsung", item.entryId)
    }

    @Test
    fun `a comment on either recording follows the night`() {
        val item = day(
            entries = listOf(
                sleepEntry(
                    id = "samsung",
                    startMs = base,
                    endMs = base + 8 * hour,
                    stages = emptyList(),
                ),
                sleepEntry(
                    id = "fitbit",
                    startMs = base + 3 * minute,
                    endMs = base + 8 * hour + 3 * minute,
                    stages = emptyList(),
                    source = "com.fitbit.FitbitMobile",
                ),
            ),
            notes = mapOf(JournalKeys.sleep("samsung") to "Woke up twice"),
            sleepSourcePriority = listOf("com.fitbit.FitbitMobile"),
        ).items.single() as JournalItem.Sleep

        assertEquals("fitbit", item.entryId)
        assertEquals("Woke up twice", item.note)
    }

    @Test
    fun `separate sleep sessions on one day stay separate`() {
        val items = day(
            entries = listOf(
                sleepEntry(
                    id = "night",
                    startMs = base,
                    endMs = base + 8 * hour,
                    stages = emptyList(),
                ),
                sleepEntry(
                    id = "nap",
                    startMs = base + 10 * hour,
                    endMs = base + 11 * hour,
                    stages = emptyList(),
                ),
            ),
        ).items.filterIsInstance<JournalItem.Sleep>()

        assertEquals(listOf("night", "nap"), items.map { it.entryId })
    }

    // ── Comments and notes ──────────────────────────────────────────────────

    @Test
    fun `comments are attached to the entry key`() {
        val key = JournalKeys.workout(date, "wod")
        val item = day(
            workouts = listOf(workout("wod", "🔥WOD", "Text")),
            events = listOf(event("WOD", base, base + hour)),
            notes = mapOf(key to "Felt strong."),
        ).items.single() as JournalItem.Workout

        assertEquals("Felt strong.", item.note)
        assertEquals(date, JournalKeys.dateOf(key))
        assertEquals("e:42", JournalKeys.activity("42"))
        assertEquals("s:42", JournalKeys.sleep("42"))
    }

    @Test
    fun `an old exercise note becomes the activity comment`() {
        val entry = exerciseEntry("e1", base, base + 30 * minute).copy(
            customNarrative = "Legs were heavy.",
        )
        val item = day(entries = listOf(entry)).items.single() as JournalItem.Activity

        assertEquals("Legs were heavy.", item.note)
    }

    // ── Calendar marks ─────────────────────────────────────────────────────

    @Test
    fun `month marks report workouts watch data sleep and notes`() {
        val nextDay = "2026-11-18"
        val marks = JournalFeed.marks(
            workouts = listOf(
                workout("wod", "🔥WOD", "A"),
                workout("hyrox", "🫁HYROX", "B", day = nextDay),
                // No event, no watch, no values: not in the journal, not marked.
                workout("pump", "PUMP", "C", day = "2026-11-19"),
            ),
            calendarEvents = listOf(
                event("WOD", base, base + hour),
                event("HYROX", base, base + hour, day = nextDay),
            ),
            entries = listOf(
                exerciseEntry("e1", base, base + hour),
                sleepEntry("s1", base, base + 8 * hour, stages = listOf(5 to hour)),
            ),
            sessions = emptyList(),
            notes = mapOf(JournalKeys.workout(date, "wod") to "Good one"),
        )

        val day = marks.getValue(date)
        assertEquals(listOf("wod"), day.workoutTypes)
        assertTrue(day.hasWatchData)
        assertTrue(day.hasSleep)
        assertTrue(day.hasNote)

        val other = marks.getValue(nextDay)
        assertEquals(listOf("hyrox"), other.workoutTypes)
        assertFalse(other.hasWatchData)
        assertFalse(other.hasNote)

        assertFalse(marks.containsKey("2026-11-19"))
    }

    @Test
    fun `an AI session with values marks its day`() {
        val marks = JournalFeed.marks(
            workouts = listOf(workout("pump", "PUMP", "C")),
            calendarEvents = emptyList(),
            entries = emptyList(),
            sessions = listOf(
                session("pump", plannerJson, """{"e:0:0:0":{"weight":"60","reps":"8"}}"""),
            ),
            notes = emptyMap(),
        )
        assertEquals(listOf("pump"), marks.getValue(date).workoutTypes)
    }

    @Test
    fun `a legacy WOD still marks its day as a workout`() {
        val marks = JournalFeed.marks(
            workouts = emptyList(),
            calendarEvents = emptyList(),
            entries = listOf(wodEntry(startMs = base, endMs = base + hour, content = "Text")),
            sessions = emptyList(),
            notes = emptyMap(),
        )
        assertEquals(listOf("wod"), marks.getValue(date).workoutTypes)
    }

    // ── Formatting ──────────────────────────────────────────────────────────

    @Test
    fun `formatting matches the rest of the app`() {
        assertEquals("45m", JournalFeed.formatDuration(45))
        assertEquals("1h", JournalFeed.formatDuration(60))
        assertEquals("1h 30m", JournalFeed.formatDuration(90))
        assertEquals("5:30", JournalFeed.formatPace(330.0))
        assertEquals("8.24", JournalFeed.formatDistance(8240.0))
        assertEquals("1,025", JournalFeed.formatVolume(1025.0))
    }
}
