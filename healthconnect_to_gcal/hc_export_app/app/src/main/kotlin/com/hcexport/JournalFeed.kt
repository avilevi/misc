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

/** A workout's AI plan rendered with the values the user actually entered. */
data class JournalAiPlan(
    val title: String,
    val summary: String,
    val model: String,
    val totalSets: Int,
    val loggedSets: Int,
    val totalVolumeKg: Double?,
    val sections: List<JournalAiSection>,
)

data class JournalAiSection(
    val title: String,
    val kind: String,
    val notes: String,
    val values: List<JournalAiValue>,
    val exercises: List<JournalAiExercise>,
)

data class JournalAiExercise(
    val name: String,
    val notes: String,
    val sets: List<JournalAiSet>,
)

data class JournalAiSet(
    val label: String,
    val target: String,
    val values: List<JournalAiValue>,
)

data class JournalAiValue(val label: String, val value: String, val unit: String)

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
 * A workout earns a journal entry only when at least one of these is true:
 *  - an event on **any** calendar names it (matched by name),
 *  - a watch/strap session was recorded on the day, or
 *  - values were logged into its AI workout.
 * Everything that qualifies for the same workout is merged into one entry.
 */
object JournalFeed {

    fun buildDay(
        date: String,
        workouts: List<Workout>,
        calendarEvents: List<CalendarWorkoutEvent>,
        entries: List<JournalEntry>,
        sessions: Map<String, InteractiveSessionRecord>,
        notes: Map<String, String>,
    ): JournalDay {
        val ordered = orderedWorkouts(workouts)
        val eventMatches = calendarMatches(ordered, calendarEvents)
        val included = ordered.filter { workout ->
            eventMatches.containsKey(workout.typeKey) || sessions[workout.typeKey].hasLoggedValues()
        }
        val exercises = entries.filter { it.entryType == "exercise" }
            .sortedBy { startOf(it) }
        val wodEntries = entries.filter { it.entryType == "wod" }
        val sleepEntries = entries.filter { it.entryType == "sleep" }

        // The earliest matching event gives the entry its time; every matched
        // event participates in the watch-session merge below.
        val anchors = included.associate { workout ->
            workout.typeKey to eventMatches[workout.typeKey]?.minByOrNull { it.startMs }
        }

        // ── Match watch sessions to workouts ──────────────────────────────────
        // A session can belong to only one workout: the one whose calendar
        // window it overlaps the most.
        val attached = mutableMapOf<String, MutableList<JournalEntry>>()
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
            if (bestKey != null) {
                attached.getOrPut(bestKey) { mutableListOf() }.add(entry)
                remaining.remove(entry)
            }
        }
        // One workout on the day: a recorded session is that workout, even when
        // the calendar event and the watch disagree about the hour.
        if (included.size == 1 && remaining.isNotEmpty()) {
            attached.getOrPut(included.first().typeKey) { mutableListOf() }.addAll(remaining)
            remaining.clear()
        }

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
                        model = record.model,
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
                biometrics = attached[workout.typeKey].orEmpty()
                    .sortedBy { startOf(it) }
                    .mapNotNull { biometrics(it) },
                note = notes[JournalKeys.workout(date, workout.typeKey)].orEmpty(),
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
                val hits = if (start > 0 && end > start) {
                    remaining.filter { overlaps(it, start, end) }
                } else {
                    emptyList()
                }
                remaining.removeAll(hits)
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
                    biometrics = hits.mapNotNull { biometrics(it) },
                    note = notes[noteKey].orEmpty(),
                    canOpenAi = false,
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
        for (entry in sleepEntries.sortedBy { startOf(it) }) {
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
                note = notes[JournalKeys.sleep(entry.id)]
                    ?: entry.customNarrative?.takeIf { it.isNotBlank() }.orEmpty(),
            )
        }

        return JournalDay(date, items)
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
    ): List<String> {
        val ordered = orderedWorkouts(workouts)
        val matches = calendarMatches(ordered, calendarEvents)
        return ordered.filter { workout ->
            matches.containsKey(workout.typeKey) || sessions[workout.typeKey].hasLoggedValues()
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

    /** Day marks for a month of the calendar grid. */
    fun marks(
        workouts: List<Workout>,
        calendarEvents: List<CalendarWorkoutEvent>,
        entries: List<JournalEntry>,
        sessions: List<InteractiveSessionRecord>,
        notes: Map<String, String>,
    ): Map<String, JournalDayMark> {
        val workoutsByDate = workouts.groupBy { it.date }
        val eventsByDate = calendarEvents.groupBy { it.date }
        val sessionsByDate = sessions.groupBy { it.date }
        val wodDates = entries.filter { it.entryType == "wod" }.map { it.date }.toSet()
        val exerciseDates = entries.filter { it.entryType == "exercise" }.map { it.date }.toSet()
        val sleepDates = entries.filter { it.entryType == "sleep" }.map { it.date }.toSet()

        val entryDates = entries.associate { it.id to it.date }
        val noteDates = notes.keys.mapNotNull { key ->
            when {
                key.startsWith("w:") -> JournalKeys.dateOf(key)
                key.startsWith("e:") -> entryDates[key.removePrefix("e:")]
                key.startsWith("s:") -> entryDates[key.removePrefix("s:")]
                else -> null
            }
        }.toSet()

        val allDates = workoutsByDate.keys + eventsByDate.keys + wodDates +
            exerciseDates + sleepDates + noteDates
        return allDates.associateWith { date ->
            val types = includedTypeKeys(
                workouts = workoutsByDate[date].orEmpty(),
                calendarEvents = eventsByDate[date].orEmpty(),
                sessions = sessionsByDate[date].orEmpty().associateBy { it.typeKey },
            ).ifEmpty {
                if (date in wodDates) listOf(WorkoutTypes.WOD_KEY) else emptyList()
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

    // ── AI plan ──────────────────────────────────────────────────────────────

    fun aiPlan(
        plan: InteractivePlan,
        results: Map<String, Map<String, String>>,
        model: String,
    ): JournalAiPlan {
        var totalSets = 0
        var loggedSets = 0
        var volume = 0.0
        var volumeKnown = false

        val sections = plan.sections.mapIndexed { sectionIndex, section ->
            val sectionValues = valuesFor(
                section.metrics,
                results[InteractiveKeys.section(sectionIndex)],
            )
            val exercises = section.exercises.mapIndexed { exerciseIndex, exercise ->
                val sets = exercise.sets.mapIndexed { setIndex, set ->
                    totalSets++
                    val metrics = set.metrics.ifEmpty { exercise.metrics }
                    val scope = InteractiveKeys.set(sectionIndex, exerciseIndex, setIndex)
                    val values = valuesFor(metrics, results[scope])
                    if (values.isNotEmpty()) loggedSets++

                    val weight = numericValue(metrics, results[scope], MetricKind.WEIGHT)
                    val reps = numericValue(metrics, results[scope], MetricKind.REPS)
                    if (weight != null && reps != null && weight > 0 && reps > 0) {
                        volume += weight * reps
                        volumeKnown = true
                    }
                    JournalAiSet(label = set.label, target = set.target, values = values)
                }
                JournalAiExercise(name = exercise.name, notes = exercise.notes, sets = sets)
            }
            JournalAiSection(
                title = section.title,
                kind = section.kind,
                notes = section.notes,
                values = sectionValues,
                exercises = exercises,
            )
        }

        return JournalAiPlan(
            title = plan.title,
            summary = plan.summary,
            model = model,
            totalSets = totalSets,
            loggedSets = loggedSets,
            totalVolumeKg = if (volumeKnown) volume else null,
            sections = sections,
        )
    }

    private fun valuesFor(
        metrics: List<MetricDef>,
        values: Map<String, String>?,
    ): List<JournalAiValue> = metrics.mapNotNull { metric ->
        val raw = values?.get(metric.key)?.trim().orEmpty()
        if (raw.isEmpty()) null
        else JournalAiValue(label = metric.label, value = raw, unit = metric.unit)
    }

    private fun numericValue(
        metrics: List<MetricDef>,
        values: Map<String, String>?,
        kind: MetricKind,
    ): Double? {
        val metric = metrics.firstOrNull { it.kind == kind } ?: return null
        return values?.get(metric.key)?.trim()?.toDoubleOrNull()
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

    private fun overlaps(entry: JournalEntry, startMs: Long, endMs: Long): Boolean =
        startOf(entry) < endMs && endOf(entry) > startMs
}

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key) else null

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (has(key) && !isNull(key)) optLong(key) else null

/** A session only counts as "logged" when the user entered something. */
private fun InteractiveSessionRecord?.hasLoggedValues(): Boolean =
    this != null && InteractiveResults.hasAnyValue(InteractiveResults.fromJson(resultsJson))
