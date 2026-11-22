package com.hcexport

import org.json.JSONObject
import java.util.Locale

/**
 * Stable identity for a journal entry's free comment.
 *
 * Workouts are keyed by date + type because that pair is the workout archive's
 * identity. Watch sessions and nights are keyed by the journal entry id, which
 * survives re-syncs (entries are matched by start time and source).
 */
object JournalKeys {
    fun workout(date: String, typeKey: String): String = "w:$date:$typeKey"
    fun activity(entryId: String): String = "e:$entryId"
    fun sleep(entryId: String): String = "s:$entryId"

    /** The ISO date a workout key belongs to, or null for an entry id key. */
    fun dateOf(key: String): String? = when {
        key.startsWith("w:") ->
            key.removePrefix("w:").substringBefore(':').takeIf { it.length == 10 }
        else -> null
    }
}

/** One rendered entry in the journal: a workout, a loose watch session, or a night. */
sealed interface JournalItem {
    val key: String
    val date: String
    val note: String

    /**
     * A programmed workout. [plan] carries the AI breakdown with the values the
     * user entered; without one the original text in [content] is the session.
     * [biometrics] are the watch sessions merged into it.
     */
    data class Workout(
        override val date: String,
        val typeKey: String,
        val typeLabel: String,
        val content: String,
        val plan: JournalAiPlan?,
        val startMs: Long?,
        val endMs: Long?,
        val biometrics: List<JournalBiometrics>,
        override val note: String,
        /** False for a WOD that only the journal remembers (no archive row to open). */
        val canOpenAi: Boolean = true,
        /** Put there by a live "Schedule session", not by the calendar or the watch. */
        val scheduled: Boolean = false,
        /** The journal.db WOD row behind a legacy workout, when there is one. */
        val sourceEntryId: String? = null,
        /** Keeps two WOD entries on one date from sharing a comment key. */
        private val keySuffix: String? = null,
    ) : JournalItem {
        override val key: String
            get() = JournalKeys.workout(date, typeKey) + (keySuffix?.let { ":$it" } ?: "")

        /** Actual recorded time when a watch merged in; otherwise the calendar window. */
        val durationMin: Long?
            get() {
                val watchMinutes = biometrics.sumOf { it.durationMin }
                if (watchMinutes > 0) return watchMinutes
                val start = startMs ?: return null
                val end = endMs ?: return null
                return ((end - start) / 60_000).takeIf { it > 0 }
            }
    }

    /** A watch/strap session with no workout behind it (a run, a ride, a game). */
    data class Activity(
        override val date: String,
        val entryId: String,
        val biometrics: JournalBiometrics,
        override val note: String,
    ) : JournalItem {
        override val key: String get() = JournalKeys.activity(entryId)
    }

    /** One night's sleep, on the day it ended. */
    data class Sleep(
        override val date: String,
        val entryId: String,
        val startMs: Long,
        val endMs: Long,
        val stages: List<SleepStageTotal>,
        val sourcePkg: String,
        override val note: String,
    ) : JournalItem {
        override val key: String get() = JournalKeys.sleep(entryId)
        val durationMin: Long get() = ((endMs - startMs) / 60_000).coerceAtLeast(0)
    }
}

/** Metrics captured by a watch or strap for one session. */
data class JournalBiometrics(
    val entryId: String,
    val startMs: Long,
    val endMs: Long,
    val typeCode: Int,
    val title: String,
    val distanceM: Double?,
    val paceSecPerKm: Double?,
    val caloriesKcal: Double?,
    val avgHrBpm: Double?,
    val maxHrBpm: Double?,
    val steps: Long?,
    val hrSamples: List<HrSample>,
    val sourcePkg: String,
) {
    val durationMin: Long get() = ((endMs - startMs) / 60_000).coerceAtLeast(0)
}

data class SleepStageTotal(val name: String, val minutes: Long)

/**
 * The headline numbers of a workout's AI plan. The plan itself is rendered by
 * the one editor in the journal entry; this is what the list cards, the entry
 * hero and the workout picker need to know.
 */
data class JournalAiPlan(
    val title: String,
    val summary: String,
    val totalSets: Int,
    val loggedSets: Int,
    val totalVolumeKg: Double?,
    /** Rows past a section's "reached here" marker: never started, not counted. */
    val notReachedSets: Int = 0,
)

/** A day's journal, ready to render. */
data class JournalDay(val date: String, val items: List<JournalItem>)

/** What the month grid needs to know about one day. */
data class JournalDayMark(
    val workoutTypes: List<String> = emptyList(),
    val hasWatchData: Boolean = false,
    val hasSleep: Boolean = false,
    val hasNote: Boolean = false,
)

/**
 * Builds journal days from the stores that feed them: the append-only workout
 * archive, the calendars, the Health Connect journal entries and the AI
 * sessions.
 *
 * A workout earns a journal entry when an event on **any** calendar names it
 * (matched by name) or values were logged into its AI workout. A watch session
 * never adds a workout of its own; it merges into the workout whose calendar
 * event it overlaps, and each entry carries at most **one** recorded session.
 * A session the calendar does not account for stays its own activity.
 */
object JournalFeed {

    fun buildDay(
        date: String,
        workouts: List<Workout>,
        calendarEvents: List<CalendarWorkoutEvent>,
        entries: List<JournalEntry>,
        sessions: Map<String, InteractiveSessionRecord>,
        notes: Map<String, String>,
        sleepSourcePriority: List<String> = emptyList(),
        /** Sessions the athlete scheduled in the app for this day. */
        scheduledSessions: List<ScheduledSession> = emptyList(),
        /** Entry keys the athlete deleted; they stay out until scheduled again. */
        hiddenKeys: Set<String> = emptySet(),
    ): JournalDay {
        val ordered = orderedWorkouts(workouts)
        val eventMatches = calendarMatches(ordered, calendarEvents)
        val scheduledByType = scheduledSessions
            .filter { it.date == date }
            .associateBy { it.typeKey }
        val included = ordered.filter { workout ->
            eventMatches.containsKey(workout.typeKey) ||
                scheduledByType.containsKey(workout.typeKey) ||
                sessions[workout.typeKey].hasLoggedValues()
        }
        val exercises = entries.filter { it.entryType == "exercise" }
            .sortedBy { startOf(it) }
        val wodEntries = entries.filter { it.entryType == "wod" }
        val sleepEntries = entries.filter { it.entryType == "sleep" }

        // A scheduled session's window stands in for a calendar event for the
        // entry's planned time. It deliberately does **not** create a merge
        // window: only evidence on a calendar accounts for a recorded session.
        fun scheduledEvent(workout: Workout): CalendarWorkoutEvent? {
            val schedule = scheduledByType[workout.typeKey] ?: return null
            val start = schedule.startMs ?: return null
            val end = schedule.endMs ?: (start + 60 * 60_000L)
            if (end <= start) return null
            return CalendarWorkoutEvent(
                id = -(schedule.createdAtMs.coerceAtLeast(1L)),
                title = WorkoutText.displayType(workout.typeLabel),
                startMs = start,
                endMs = end,
                date = workout.date,
                description = null,
            )
        }

        // The earliest matching event gives the entry its time; every matched
        // event participates in the watch-session merge below.
        val anchors = included.associate { workout ->
            workout.typeKey to (
                eventMatches[workout.typeKey]?.minByOrNull { it.startMs }
                    ?: scheduledEvent(workout)
                )
        }

        // ── Match watch sessions to workouts ──────────────────────────────────
        // A session belongs to a workout only when it overlaps that workout's
        // calendar window, and a workout entry carries a single recorded
        // session — the one it overlaps the most. A session the calendar does
        // not account for stays its own activity: it is never bundled into a
        // workout it does not overlap, and two different sessions never share
        // one entry.
        val attached = mutableMapOf<String, JournalEntry>()
        val attachedOverlap = mutableMapOf<String, Long>()
        val remaining = exercises.toMutableList()
        for (entry in exercises) {
            var bestKey: String? = null
            var bestOverlap = 0L
            for (workout in included) {
                for (event in eventMatches[workout.typeKey].orEmpty()) {
                    val overlap = overlapDuration(entry, event)
                    if (overlap > bestOverlap) {
                        bestOverlap = overlap
                        bestKey = workout.typeKey
                    }
                }
            }
            val key = bestKey ?: continue
            val current = attached[key]
            if (current == null) {
                attached[key] = entry
                attachedOverlap[entry.id] = bestOverlap
                remaining.remove(entry)
            } else if (bestOverlap > (attachedOverlap[current.id] ?: 0L)) {
                attached[key] = entry
                attachedOverlap[entry.id] = bestOverlap
                remaining.remove(entry)
                remaining.add(current)
            }
        }
        remaining.sortBy { startOf(it) }

        val items = mutableListOf<JournalItem>()

        // ── Programmed workouts with evidence ─────────────────────────────────
        for (workout in included) {
            val anchor = anchors[workout.typeKey]
            val session = sessions[workout.typeKey]
            val plan = session?.let { record ->
                InteractivePlan.fromJson(record.planJson)?.let { parsed ->
                    aiPlan(
                        plan = parsed,
                        results = InteractiveResults.fromJson(record.resultsJson),
                    )
                }
            }
            items += JournalItem.Workout(
                date = date,
                typeKey = workout.typeKey,
                typeLabel = workout.typeLabel,
                content = workout.content,
                plan = plan,
                startMs = anchor?.startMs,
                endMs = anchor?.endMs,
                biometrics = attached[workout.typeKey]
                    ?.let { listOfNotNull(biometrics(it)) }
                    .orEmpty(),
                note = notes[JournalKeys.workout(date, workout.typeKey)].orEmpty(),
                scheduled = scheduledByType.containsKey(workout.typeKey),
            )
        }

        // ── WOD entries the archive does not have (older journal history) ─────
        if (included.none { it.typeKey == WorkoutTypes.WOD_KEY }) {
            var legacyIndex = 0
            for (entry in wodEntries) {
                val content = entry.customNarrative?.takeIf { it.isNotBlank() }
                    ?: wodContentOf(entry)
                if (content.isBlank()) continue
                val start = startOf(entry)
                val end = endOf(entry)
                // One recorded session per entry, the one it overlaps the most;
                // any other session stays its own activity.
                val hit = if (start > 0 && end > start) {
                    remaining
                        .filter { overlapLength(it, start, end) > 0 }
                        .maxByOrNull { overlapLength(it, start, end) }
                } else null
                hit?.let { remaining.remove(it) }
                val suffix = if (legacyIndex == 0) null else entry.id
                val noteKey = JournalKeys.workout(date, WorkoutTypes.WOD_KEY) +
                    (suffix?.let { ":$it" } ?: "")
                items += JournalItem.Workout(
                    date = date,
                    typeKey = WorkoutTypes.WOD_KEY,
                    typeLabel = entry.customTitle ?: "WOD",
                    content = content,
                    plan = null,
                    startMs = start.takeIf { it > 0 },
                    endMs = end.takeIf { it > 0 },
                    biometrics = listOfNotNull(hit?.let { biometrics(it) }),
                    note = notes[noteKey].orEmpty(),
                    canOpenAi = false,
                    sourceEntryId = entry.id,
                    keySuffix = suffix,
                )
                legacyIndex++
            }
        }

        // ── Everything the watch recorded on its own ─────────────────────────
        for (entry in remaining) {
            val data = biometrics(entry) ?: continue
            items += JournalItem.Activity(
                date = date,
                entryId = entry.id,
                biometrics = data,
                note = notes[JournalKeys.activity(entry.id)]
                    ?: entry.customNarrative?.takeIf { it.isNotBlank() }.orEmpty(),
            )
        }

        // ── Sleep, placed on the day it ended ────────────────────────────────
        // A night recorded by two sources is one night: the highest-priority
        // source is shown once, and its comment follows the night.
        for (cluster in SleepSessions.clusters(sleepEntries)) {
            val entry = SleepSessions.preferred(cluster, sleepSourcePriority)
            val start = startOf(entry)
            val end = endOf(entry)
            if (start <= 0 || end <= start) continue
            items += JournalItem.Sleep(
                date = date,
                entryId = entry.id,
                startMs = start,
                endMs = end,
                stages = sleepStages(entry),
                sourcePkg = sourceOf(entry),
                note = SleepSessions.note(cluster, entry, notes),
            )
        }

        return JournalDay(date, items.filterNot { it.key in hiddenKeys })
    }

    /**
     * The workouts that belong in the journal for a day: calendar-named, or
     * carried by an AI session with values. Watch sessions do not add a workout
     * (they can be standalone activities), but they decide the merge later.
     */
    fun includedTypeKeys(
        workouts: List<Workout>,
        calendarEvents: List<CalendarWorkoutEvent>,
        sessions: Map<String, InteractiveSessionRecord>,
        scheduledTypeKeys: Set<String> = emptySet(),
    ): List<String> {
        val ordered = orderedWorkouts(workouts)
        val matches = calendarMatches(ordered, calendarEvents)
        return ordered.filter { workout ->
            matches.containsKey(workout.typeKey) ||
                scheduledTypeKeys.contains(workout.typeKey) ||
                sessions[workout.typeKey].hasLoggedValues()
        }.map { it.typeKey }
    }

    /**
     * Calendar events grouped by the workout whose name they carry. Matching is
     * by name on **any** calendar: "WOD", "🔥 WOD", "CrossFit WOD", "WOD 8:00"
     * all name the WOD workout; "Pump Blend" prefers the blend over plain Pump.
     */
    fun calendarMatches(
        workouts: List<Workout>,
        events: List<CalendarWorkoutEvent>,
    ): Map<String, List<CalendarWorkoutEvent>> {
        if (workouts.isEmpty() || events.isEmpty()) return emptyMap()
        val out = mutableMapOf<String, MutableList<CalendarWorkoutEvent>>()
        for (event in events.sortedBy { it.startMs }) {
            val workout = workoutForTitle(event.title, workouts) ?: continue
            out.getOrPut(workout.typeKey) { mutableListOf() } += event
        }
        return out
    }

    /**
     * The calendar events a watch session may merge with: events on the
     * session's day ([workouts] are that day's stored workouts) that name one
     * of them — the same name matching that puts a workout in the journal — and
     * that overlap the session. A meeting that merely happens at the same time
     * names no workout and is never merged.
     *
     * [events] should already exclude the app's own Health Connect events
     * (`CalendarHelper.findWorkoutEvents` does that).
     */
    fun journalEventsForSession(
        workouts: List<Workout>,
        events: List<CalendarWorkoutEvent>,
        startMs: Long,
        endMs: Long,
    ): List<CalendarWorkoutEvent> =
        calendarMatches(orderedWorkouts(workouts), events)
            .values.flatten()
            .filter { it.startMs < endMs && it.endMs > startMs }
            .distinctBy { it.id to it.startMs }
            .sortedBy { it.startMs }

    /** Day marks for a month of the calendar grid. */
    fun marks(
        workouts: List<Workout>,
        calendarEvents: List<CalendarWorkoutEvent>,
        entries: List<JournalEntry>,
        sessions: List<InteractiveSessionRecord>,
        notes: Map<String, String>,
        scheduledTypeKeysByDate: Map<String, Set<String>> = emptyMap(),
        hiddenKeys: Set<String> = emptySet(),
    ): Map<String, JournalDayMark> {
        val workoutsByDate = workouts.groupBy { it.date }
        val eventsByDate = calendarEvents.groupBy { it.date }
        val sessionsByDate = sessions.groupBy { it.date }
        // A deleted watch session or night must not leave a mark behind.
        val visibleEntries = entries.filterNot { entry ->
            when (entry.entryType) {
                "exercise" -> JournalKeys.activity(entry.id) in hiddenKeys
                "sleep" -> JournalKeys.sleep(entry.id) in hiddenKeys
                else -> false
            }
        }
        val wodDates = visibleEntries.filter { it.entryType == "wod" }.map { it.date }.toSet()
        val exerciseDates = visibleEntries.filter { it.entryType == "exercise" }
            .map { it.date }.toSet()
        val sleepDates = visibleEntries.filter { it.entryType == "sleep" }
            .map { it.date }.toSet()

        val entryDates = visibleEntries.associate { it.id to it.date }

        // A hidden workout keeps its dot off the grid even when a calendar
        // event elsewhere still names it.
        val hiddenWorkoutTypes = hiddenKeys.mapNotNull { key ->
            if (!key.startsWith("w:")) return@mapNotNull null
            val rest = key.removePrefix("w:")
            val hiddenDate = rest.substringBefore(':')
            val type = rest.substringAfter(':', "")
            if (hiddenDate.length == 10 && type.isNotBlank() && ':' !in type) {
                hiddenDate to type
            } else {
                null
            }
        }.groupBy({ it.first }, { it.second })
        val noteDates = notes.keys.mapNotNull { key ->
            when {
                key.startsWith("w:") -> JournalKeys.dateOf(key)
                key.startsWith("e:") -> entryDates[key.removePrefix("e:")]
                key.startsWith("s:") -> entryDates[key.removePrefix("s:")]
                else -> null
            }
        }.toSet()

        val allDates = workoutsByDate.keys + eventsByDate.keys + wodDates +
            exerciseDates + sleepDates + noteDates + scheduledTypeKeysByDate.keys
        return allDates.associateWith { date ->
            val types = includedTypeKeys(
                workouts = workoutsByDate[date].orEmpty(),
                calendarEvents = eventsByDate[date].orEmpty(),
                sessions = sessionsByDate[date].orEmpty().associateBy { it.typeKey },
                scheduledTypeKeys = scheduledTypeKeysByDate[date].orEmpty(),
            )
                .filterNot { it in hiddenWorkoutTypes[date].orEmpty() }
                .ifEmpty {
                    if (date in wodDates &&
                        JournalKeys.workout(date, WorkoutTypes.WOD_KEY) !in hiddenKeys
                    ) {
                        listOf(WorkoutTypes.WOD_KEY)
                    } else {
                        emptyList()
                    }
                }
            JournalDayMark(
                workoutTypes = types,
                hasWatchData = date in exerciseDates,
                hasSleep = date in sleepDates,
                hasNote = date in noteDates,
            )
        }.filterValues {
            it.workoutTypes.isNotEmpty() || it.hasWatchData || it.hasSleep || it.hasNote
        }
    }

    // ── Name matching ────────────────────────────────────────────────────────

    private val NON_NAME = Regex("[^A-Z0-9]+")

    private fun orderedWorkouts(workouts: List<Workout>): List<Workout> =
        workouts.sortedWith(
            compareBy<Workout> { it.sortOrder }.thenBy { it.typeLabel.lowercase(Locale.US) }
        )

    /**
     * The workout an event names, or null. Exact names win over containment and
     * longer names win over shorter ones, so "Pump Blend" is the blend and not
     * plain Pump.
     */
    private fun workoutForTitle(title: String, workouts: List<Workout>): Workout? {
        val normalized = normaliseName(title)
        if (normalized.isEmpty()) return null
        var best: Workout? = null
        var bestScore = 0
        for (workout in workouts) {
            for (name in workoutNames(workout)) {
                if (name.length < 3) continue
                val score = when {
                    normalized == name -> 1000 + name.length
                    containsWord(normalized, name) -> name.length
                    else -> 0
                }
                if (score > bestScore) {
                    best = workout
                    bestScore = score
                }
            }
        }
        return best
    }

    private fun workoutNames(workout: Workout): List<String> = listOf(
        WorkoutText.displayType(workout.typeLabel),
        workout.typeLabel,
        workout.typeKey.replace('_', ' '),
    ).map(::normaliseName).filter { it.isNotBlank() }.distinct()

    private fun normaliseName(raw: String): String =
        raw.uppercase(Locale.US).replace(NON_NAME, " ").trim()

    private fun containsWord(haystack: String, word: String): Boolean {
        var index = haystack.indexOf(word)
        while (index >= 0) {
            val beforeOk = index == 0 || haystack[index - 1] == ' '
            val end = index + word.length
            val afterOk = end >= haystack.length || haystack[end] == ' '
            if (beforeOk && afterOk) return true
            index = haystack.indexOf(word, index + 1)
        }
        return false
    }

    private fun overlapDuration(entry: JournalEntry, event: CalendarWorkoutEvent): Long {
        val start = maxOf(startOf(entry), event.startMs)
        val end = minOf(endOf(entry), event.endMs)
        return (end - start).coerceAtLeast(0L)
    }

    private fun overlapLength(entry: JournalEntry, startMs: Long, endMs: Long): Long {
        val start = maxOf(startOf(entry), startMs)
        val end = minOf(endOf(entry), endMs)
        return (end - start).coerceAtLeast(0L)
    }

    // ── AI plan ──────────────────────────────────────────────────────────────

    /**
     * The headline numbers of a stored plan: how many sets it prescribes, how
     * many the user has filled in, and the volume those values add up to.
     */
    fun aiPlan(
        plan: InteractivePlan,
        results: Map<String, Map<String, String>>,
    ): JournalAiPlan {
        var totalSets = 0
        var loggedSets = 0
        var notReachedSets = 0
        var volume = 0.0
        var volumeKnown = false
        // A plan nobody has touched is not "logged" just because its fields
        // carry the prescription. Once anything is entered, the plan is being
        // filled in, and with no "reached here" marker that means the whole
        // workout was done: every row counts, prefilled or not.
        val engaged = InteractiveResults.hasAnyValue(results)

        plan.sections.forEachIndexed { sectionIndex, section ->
            val rows = InteractiveRows.scopes(section, sectionIndex)
            val marker = InteractiveReached.indexOf(results, sectionIndex, rows)

            fun count(
                metrics: List<MetricDef>,
                scope: String,
                target: String,
                rowIndex: Int,
            ) {
                // Past the "reached here" marker nothing was started: the row
                // is shown greyed and stays out of both totals.
                if (marker != null && rowIndex > marker) {
                    notReachedSets++
                    return
                }
                totalSets++
                val stored = results[scope].orEmpty()
                if (engaged) loggedSets++
                val weight = numericValue(metrics, stored, MetricKind.WEIGHT, target)
                val reps = numericValue(metrics, stored, MetricKind.REPS, target)
                if (weight != null && reps != null && weight > 0 && reps > 0) {
                    volume += weight * reps
                    volumeKnown = true
                }
            }

            // Walk the same rows the editor shows: explicit rounds, a circuit
            // regrouped from Round-labelled sets, or plain sets.
            val derived =
                if (section.rounds.isEmpty()) InteractiveRounds.derive(section) else null
            when {
                section.rounds.isNotEmpty() -> {
                    var rowIndex = 0
                    section.rounds.forEachIndexed { roundIndex, round ->
                        round.movements.forEachIndexed { movementIndex, movement ->
                            count(
                                metrics = movement.metrics,
                                scope = InteractiveKeys.round(
                                    sectionIndex, roundIndex, movementIndex,
                                ),
                                target = movement.target,
                                rowIndex = rowIndex++,
                            )
                        }
                    }
                }
                derived != null -> {
                    var rowIndex = 0
                    derived.forEach { round ->
                        round.movements.forEach { movement ->
                            count(
                                metrics = movement.set.metrics
                                    .ifEmpty { movement.exercise.metrics },
                                scope = InteractiveKeys.set(
                                    sectionIndex,
                                    movement.exerciseIndex,
                                    movement.setIndex,
                                ),
                                target = movement.set.target,
                                rowIndex = rowIndex++,
                            )
                        }
                    }
                }
                else -> {
                    var rowIndex = 0
                    section.exercises.forEachIndexed { exerciseIndex, exercise ->
                        exercise.sets.forEachIndexed { setIndex, set ->
                            count(
                                metrics = set.metrics.ifEmpty { exercise.metrics },
                                scope = InteractiveKeys.set(
                                    sectionIndex, exerciseIndex, setIndex,
                                ),
                                target = set.target,
                                rowIndex = rowIndex++,
                            )
                        }
                    }
                }
            }
        }

        return JournalAiPlan(
            title = plan.title,
            summary = plan.summary,
            totalSets = totalSets,
            loggedSets = loggedSets,
            totalVolumeKg = if (volumeKnown) volume else null,
            notReachedSets = notReachedSets,
        )
    }

    private fun numericValue(
        metrics: List<MetricDef>,
        values: Map<String, String>?,
        kind: MetricKind,
        target: String? = null,
    ): Double? {
        val metric = metrics.firstOrNull { it.kind == kind } ?: return null
        val raw = values?.get(metric.key)?.trim().orEmpty().ifEmpty {
            target?.let { InteractiveDefaults.forTarget(it, metric) }.orEmpty()
        }
        return raw.toDoubleOrNull()
    }

    // ── Health Connect entries ───────────────────────────────────────────────

    fun biometrics(entry: JournalEntry): JournalBiometrics? {
        if (entry.entryType != "exercise") return null
        val json = parse(entry) ?: return null
        val start = json.optLong("sm", 0L)
        val end = json.optLong("em", 0L)
        if (start <= 0 || end < start) return null
        val samples = json.optJSONArray("hr")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                runCatching { HrSample.fromJson(array.getJSONObject(index)) }.getOrNull()
            }
        }.orEmpty()
        return JournalBiometrics(
            entryId = entry.id,
            startMs = start,
            endMs = end,
            typeCode = json.optInt("tc", -1),
            title = json.optString("tt", "").trim(),
            distanceM = json.optDoubleOrNull("dm"),
            paceSecPerKm = json.optDoubleOrNull("pp"),
            caloriesKcal = json.optDoubleOrNull("ck"),
            avgHrBpm = json.optDoubleOrNull("ah"),
            maxHrBpm = json.optDoubleOrNull("mh"),
            steps = json.optLongOrNull("st"),
            hrSamples = samples,
            sourcePkg = json.optString("sp", ""),
        )
    }

    fun sleepStages(entry: JournalEntry): List<SleepStageTotal> {
        val json = parse(entry) ?: return emptyList()
        val array = json.optJSONArray("sg") ?: return emptyList()
        val totals = linkedMapOf<String, Long>()
        for (index in 0 until array.length()) {
            val stage = array.optJSONObject(index) ?: continue
            val name = CalendarHelper.SLEEP_STAGES[stage.optInt("sc", 0)] ?: "Unknown"
            val minutes = ((stage.optLong("em", 0L) - stage.optLong("sm", 0L)) / 60_000)
                .coerceAtLeast(0)
            totals[name] = (totals[name] ?: 0L) + minutes
        }
        val order = listOf("Deep Sleep", "REM", "Light Sleep", "Sleeping", "Awake", "Out of Bed", "Unknown")
        return (order + totals.keys.filter { it !in order })
            .mapNotNull { name -> totals[name]?.let { SleepStageTotal(name, it) } }
    }

    // ── Formatting (pure, unit-tested) ───────────────────────────────────────

    fun formatDuration(minutes: Long): String = when {
        minutes >= 60 -> {
            val h = minutes / 60
            val m = minutes % 60
            if (m > 0) "${h}h ${m}m" else "${h}h"
        }
        else -> "${minutes}m"
    }

    fun formatDistance(meters: Double): String {
        val km = meters / 1000.0
        return when {
            km >= 100 -> String.format(Locale.US, "%.0f", km)
            km >= 10 -> String.format(Locale.US, "%.1f", km)
            else -> String.format(Locale.US, "%.2f", km)
        }
    }

    fun formatPace(secondsPerKm: Double): String {
        if (secondsPerKm <= 0) return "—"
        val total = secondsPerKm.toInt()
        return "%d:%02d".format(Locale.US, total / 60, total % 60)
    }

    fun formatVolume(kg: Double): String = String.format(Locale.US, "%,.0f", kg)

    // ── Internals ────────────────────────────────────────────────────────────

    private fun parse(entry: JournalEntry): JSONObject? =
        runCatching { JSONObject(entry.effectiveDataJson()) }.getOrNull()

    private fun startOf(entry: JournalEntry): Long =
        parse(entry)?.optLong("sm", 0L) ?: 0L

    private fun endOf(entry: JournalEntry): Long =
        parse(entry)?.optLong("em", 0L) ?: 0L

    private fun sourceOf(entry: JournalEntry): String =
        parse(entry)?.optString("sp", "").orEmpty()

    private fun wodContentOf(entry: JournalEntry): String =
        parse(entry)?.optString("wod", "").orEmpty()
}

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key) else null

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (has(key) && !isNull(key)) optLong(key) else null

/** A session only counts as "logged" when the user entered something. */
private fun InteractiveSessionRecord?.hasLoggedValues(): Boolean =
    this != null && InteractiveResults.hasAnyValue(InteractiveResults.fromJson(resultsJson))
