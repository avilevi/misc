package com.hcexport

import java.util.Locale

/**
 * The live workout: an [InteractivePlan] flattened into the order it is
 * performed, one set or movement at a time.
 *
 * The live screen walks this model; the values it collects are the same
 * values the plan editor writes, keyed by the same scopes, so a live session
 * and a hand-filled entry are one record of the workout.
 *
 * Timings are read from what the plan already says — "Every 1:30 x 6 sets",
 * "Time cap: 14:00", "12:00 AMRAP" — never invented. A strength section with
 * no stated interval uses the user's default window; every other section
 * without one is untimed (Done advances it).
 */
data class LiveItem(
    val sectionIndex: Int,
    /** Position among the section's rows, matching [InteractiveRows]. */
    val rowIndex: Int,
    val scope: String,
    val name: String,
    val target: String,
    val metrics: List<MetricDef>,
    /** "Set 2 of 6", "Round 3 of 5", or blank for a lone set. */
    val position: String = "",
    /** The round a movement belongs to; null for a set of an exercise. */
    val roundIndex: Int? = null,
    val roundLabel: String = "",
)

data class LiveSection(
    val index: Int,
    val title: String,
    val kind: String,
    val notes: String,
    val metrics: List<MetricDef>,
    val items: List<LiveItem>,
    /** How many rounds the plan prescribes; 0 for set-based work. */
    val roundCount: Int = 0,
    /** Per-set auto-advance window; null when the sets are not clocked. */
    val intervalSeconds: Int? = null,
    /** Each round of a round-timed piece runs for this long ("4 x 5:00"). */
    val roundSeconds: Int? = null,
    /** Rest between those rounds ("1:00 rest between rounds"). */
    val restSeconds: Int? = null,
    /** The section's own clock, when it is timed as a whole. */
    val timeCapSeconds: Int? = null,
    /** A single round that repeats until the cap. */
    val isAmrap: Boolean = false,
    /** True when the section runs one clock rather than per-set windows. */
    val clocked: Boolean = false,
    /** The repeating round's label, for an AMRAP. */
    val roundLabel: String = "",
) {
    /** Only scored work carries a result of its own; see [InteractiveSection]. */
    val showsSectionResult: Boolean
        get() = kind != "strength" && kind != "accessory" && kind != "rest"

    /**
     * A metcon of repeated rounds each with its own countdown, e.g.
     * "4 x 5:00 rounds with 1:00 rest". Unlike an AMRAP it is not one clock.
     */
    val roundTimed: Boolean
        get() = roundSeconds != null && roundCount > 0 && !isAmrap
}

data class LivePlan(
    val title: String,
    val summary: String,
    val sections: List<LiveSection>,
) {
    /** Whether the plan has anything to run. */
    val playable: Boolean
        get() = sections.any {
            it.items.isNotEmpty() || it.metrics.isNotEmpty() || it.notes.isNotBlank()
        }
}

object LiveWorkout {

    fun build(plan: InteractivePlan, defaultIntervalSeconds: Int = 90): LivePlan =
        LivePlan(
            title = plan.title,
            summary = plan.summary,
            sections = plan.sections.mapIndexed { index, section ->
                buildSection(section, index, defaultIntervalSeconds)
            },
        )

    /** One section in the live shape, for readers that hold a section alone. */
    fun section(
        section: InteractiveSection,
        index: Int,
        defaultIntervalSeconds: Int = 90,
    ): LiveSection = buildSection(section, index, defaultIntervalSeconds)

    private fun buildSection(
        section: InteractiveSection,
        index: Int,
        defaultIntervalSeconds: Int,
    ): LiveSection {
        val items = itemsOf(section, index)
        val derived = if (section.rounds.isEmpty()) InteractiveRounds.derive(section) else null
        val roundsBased = section.rounds.isNotEmpty() || derived != null
        val roundCount = when {
            section.rounds.isNotEmpty() -> section.rounds.size
            derived != null -> derived.size
            else -> 0
        }
        val labels = listOf(section.title, section.notes)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        val statedInterval = LiveTiming.intervalSeconds(labels) ?: minuteLabels(section)
        val interval = when {
            roundsBased -> null
            statedInterval != null -> statedInterval
            section.kind == "strength" -> defaultIntervalSeconds.coerceIn(10, 900)
            else -> null
        }
        val clocked = roundsBased || (section.kind == "metcon" && interval == null)
        val roundLabel = section.rounds.firstOrNull()?.label
            ?: derived?.firstOrNull()?.label
            ?: ""
        val amrap = roundsBased && roundCount == 1 &&
            (LiveTiming.isAmrap(labels) || roundLabel.contains("each round", true))
        // "4 x 5:00 rounds with 1:00 rest" is a round clock, not a section cap.
        val roundSeconds =
            if (roundsBased) LiveTiming.roundSeconds(labels) else null
        val restSeconds =
            if (roundSeconds != null) LiveTiming.restSeconds(labels) else null
        val roundTimed = roundSeconds != null && roundCount > 0 && !amrap

        return LiveSection(
            index = index,
            title = section.title,
            kind = section.kind,
            notes = section.notes,
            metrics = section.metrics,
            items = items,
            roundCount = roundCount,
            intervalSeconds = interval,
            roundSeconds = roundSeconds,
            restSeconds = restSeconds,
            timeCapSeconds = if (clocked && !roundTimed) {
                LiveTiming.timeCapSeconds(labels)
            } else {
                null
            },
            isAmrap = amrap,
            clocked = clocked,
            roundLabel = if (amrap) roundLabel else "",
        )
    }

    /**
     * The section's rows in display order — the same walk as [InteractiveRows],
     * so a live step and a "reached here" row always mean the same thing.
     */
    private fun itemsOf(section: InteractiveSection, sectionIndex: Int): List<LiveItem> {
        val items = mutableListOf<LiveItem>()
        var row = 0

        if (section.rounds.isNotEmpty()) {
            val count = section.rounds.size
            section.rounds.forEachIndexed { roundIndex, round ->
                round.movements.forEachIndexed { movementIndex, movement ->
                    items += LiveItem(
                        sectionIndex = sectionIndex,
                        rowIndex = row++,
                        scope = InteractiveKeys.round(sectionIndex, roundIndex, movementIndex),
                        name = movement.name,
                        target = movement.target,
                        metrics = movement.metrics,
                        position = roundPosition(roundIndex, count, round.label),
                        roundIndex = roundIndex,
                        roundLabel = round.label,
                    )
                }
            }
            return items
        }

        val derived = InteractiveRounds.derive(section)
        if (derived != null) {
            val count = derived.size
            derived.forEachIndexed { roundIndex, round ->
                round.movements.forEach { movement ->
                    items += LiveItem(
                        sectionIndex = sectionIndex,
                        rowIndex = row++,
                        scope = InteractiveKeys.set(
                            sectionIndex, movement.exerciseIndex, movement.setIndex,
                        ),
                        name = movement.exercise.name,
                        target = movement.set.target,
                        metrics = movement.set.metrics
                            .ifEmpty { movement.exercise.metrics },
                        position = roundPosition(roundIndex, count, round.label),
                        roundIndex = roundIndex,
                        roundLabel = round.label,
                    )
                }
            }
            return items
        }

        section.exercises.forEachIndexed { exerciseIndex, exercise ->
            exercise.sets.forEachIndexed { setIndex, set ->
                items += LiveItem(
                    sectionIndex = sectionIndex,
                    rowIndex = row++,
                    scope = InteractiveKeys.set(sectionIndex, exerciseIndex, setIndex),
                    name = exercise.name,
                    target = set.target,
                    metrics = set.metrics.ifEmpty { exercise.metrics },
                    position = setPosition(setIndex, exercise.sets.size),
                )
            }
        }
        return items
    }

    private fun setPosition(index: Int, count: Int): String =
        if (count > 1) "Set ${index + 1} of $count" else ""

    private fun roundPosition(index: Int, count: Int, label: String): String =
        if (count > 1) {
            "Round ${index + 1} of $count"
        } else {
            label.trim().ifBlank { "Round 1" }
        }

    /** Sets labelled "Min 1", "Min 2" are an EMOM: a window a minute apart. */
    private fun minuteLabels(section: InteractiveSection): Int? {
        if (section.exercises.isEmpty()) return null
        val labels = section.exercises.flatMap { exercise ->
            exercise.sets.map { set -> set.label.trim() }
        }
        if (labels.isEmpty() || labels.any { !MINUTE_LABEL.matches(it) }) return null
        return 60
    }

    private val MINUTE_LABEL =
        Regex("""^(?:min|minute)\s*\d{1,3}$""", RegexOption.IGNORE_CASE)
}

/** Reads the clocks a workout's own words prescribe. */
object LiveTiming {

    /**
     * The window between sets, e.g. "Every 1:30 x 6 sets", "on the 2:00",
     * "every 90 seconds", "E2MOM", or an EMOM's minute.
     */
    fun intervalSeconds(text: String): Int? {
        if (text.isBlank()) return null
        EVERY_CLOCK.find(text)?.let { return clock(it) }
        SETS_CLOCK.find(text)?.let { return clock(it) }
        INTERVAL_CLOCK.find(text)?.let { return clock(it) }
        EVERY_SECONDS.find(text)?.let { match ->
            return positive(match.groupValues[1].toIntOrNull())
        }
        EVERY_MINUTES.find(text)?.let { match ->
            return minutes(match.groupValues[1])
        }
        E_CLOCK.find(text)?.let { return clock(it) }
        E_SECONDS.find(text)?.let { match ->
            return positive(match.groupValues[1].toIntOrNull())
        }
        E_MINUTES_LONG.find(text)?.let { match ->
            return minuteCount(match.groupValues[1])
        }
        E_MINUTES.find(text)?.let { match ->
            return minuteCount(match.groupValues[1])
        }
        if (EMOM.containsMatchIn(text)) return 60
        return null
    }

    /** A time cap the section states, in seconds. */
    fun timeCapSeconds(text: String): Int? {
        if (text.isBlank()) return null
        CAP_CLOCK.find(text)?.let { return clock(it) }
        CAP_MINUTES.find(text)?.let { match ->
            return minutes(match.groupValues[1])
        }
        TIME_CAP_CLOCK.find(text)?.let { return clock(it) }
        TIME_CAP_MINUTES.find(text)?.let { match ->
            return minutes(match.groupValues[1])
        }
        TIME_CAP_BARE.find(text)?.let { match ->
            return minutes(match.groupValues[1])
        }
        AMRAP_CLOCK.find(text)?.let { return clock(it) }
        AMRAP_MINUTES.find(text)?.let { match ->
            return minutes(match.groupValues[1])
        }
        FOR_TIME_CLOCK.find(text)?.let { return clock(it) }
        FOR_TIME_MINUTES.find(text)?.let { match ->
            return minutes(match.groupValues[1])
        }
        CAP_AFTER.find(text)?.let { match ->
            val mins = match.groupValues[1].toIntOrNull() ?: return@let
            val secs = match.groupValues[2].toIntOrNull() ?: 0
            return positive(mins * 60 + secs)
        }
        AMRAP_BEFORE.find(text)?.let { match ->
            val mins = match.groupValues[1].toIntOrNull() ?: return@let
            val secs = match.groupValues[2].toIntOrNull() ?: 0
            return positive(mins * 60 + secs)
        }
        AMRAP_BARE.find(text)?.let { match ->
            return minutes(match.groupValues[1])
        }
        EMOM_CLOCK.find(text)?.let { return clock(it) }
        EMOM_MINUTES.find(text)?.let { match ->
            return minutes(match.groupValues[1])
        }
        return null
    }

    fun isAmrap(text: String): Boolean =
        text.contains("amrap", ignoreCase = true) ||
            text.contains("as many rounds", ignoreCase = true) ||
            text.contains("as many reps", ignoreCase = true)

    /**
     * The length of each round in a round-timed piece:
     * "4 x 5:00", "4 rounds of 5:00", "5:00 rounds", "4 rounds x 5 minutes".
     */
    fun roundSeconds(text: String): Int? {
        if (text.isBlank()) return null
        ROUND_WINDOW_CLOCK.find(text)?.let { match ->
            val mins = match.groupValues[2].toIntOrNull() ?: return@let
            val secs = match.groupValues[3].toIntOrNull() ?: return@let
            return positive(mins * 60 + secs)
        }
        ROUND_WINDOW_MINUTES.find(text)?.let { match ->
            return minutes(match.groupValues[2])
        }
        ROUND_WINDOW_CLOCK_AFTER.find(text)?.let { match ->
            val mins = match.groupValues[1].toIntOrNull() ?: return@let
            val secs = match.groupValues[2].toIntOrNull() ?: return@let
            return positive(mins * 60 + secs)
        }
        ROUND_WINDOW_MINUTES_AFTER.find(text)?.let { match ->
            return minutes(match.groupValues[1])
        }
        return null
    }

    /** The rest between rounds: "1:00 rest", "rest 1 min", "rest 45 seconds". */
    fun restSeconds(text: String): Int? {
        if (text.isBlank()) return null
        REST_CLOCK_BEFORE.find(text)?.let { match ->
            val mins = match.groupValues[1].toIntOrNull() ?: return@let
            val secs = match.groupValues[2].toIntOrNull() ?: return@let
            return positive(mins * 60 + secs)
        }
        REST_CLOCK_AFTER.find(text)?.let { match ->
            val mins = match.groupValues[1].toIntOrNull() ?: return@let
            val secs = match.groupValues[2].toIntOrNull() ?: return@let
            return positive(mins * 60 + secs)
        }
        REST_MINUTES_BEFORE.find(text)?.let { match ->
            return minutes(match.groupValues[1])
        }
        REST_MINUTES_AFTER.find(text)?.let { match ->
            return minutes(match.groupValues[1])
        }
        REST_SECONDS.find(text)?.let { match ->
            return positive(match.groupValues[1].toIntOrNull())
        }
        return null
    }

    private fun clock(match: MatchResult): Int? {
        val mins = match.groupValues[1].toIntOrNull() ?: return null
        val secs = match.groupValues[2].toIntOrNull() ?: return null
        return positive(mins * 60 + secs)
    }

    private fun minutes(raw: String): Int? {
        val value = raw.trim().replace(',', '.').toDoubleOrNull() ?: return null
        return positive(Math.round(value * 60).toInt())
    }

    /** A whole number of minutes, e.g. the "2" of "E2MOM". */
    private fun minuteCount(raw: String): Int? =
        raw.toIntOrNull()?.takeIf { it in 1..60 }?.times(60)

    private fun positive(seconds: Int?): Int? =
        seconds?.takeIf { it in 5..3600 }

    private val EVERY_CLOCK = Regex(
        """\b(?:every|on\s+the|at)\s+(?:the\s+)?(\d{1,2}):([0-5]\d)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val SETS_CLOCK = Regex(
        """(\d{1,2}):([0-5]\d)\s*[x×]\s*\d+\s*sets?\b""",
        RegexOption.IGNORE_CASE,
    )
    private val INTERVAL_CLOCK = Regex(
        """(\d{1,2}):([0-5]\d)\s*(?:intervals?|windows?)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val EVERY_SECONDS = Regex(
        """\bevery\s+(\d{1,4})\s*(?:s|sec|secs|seconds?)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val EVERY_MINUTES = Regex(
        """\bevery\s+(\d{1,2}(?:[.,]\d+)?)\s*(?:m|min|mins|minutes?)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val E_CLOCK = Regex("""\bE(\d{1,2})M(\d{1,2})\b""", RegexOption.IGNORE_CASE)
    private val E_SECONDS = Regex("""\bE(\d{1,3})S\b""", RegexOption.IGNORE_CASE)
    private val E_MINUTES_LONG =
        Regex("""\bE(\d{1,2})M(?:O|0)M\b""", RegexOption.IGNORE_CASE)
    private val E_MINUTES = Regex("""\bE(\d{1,2})M\b""", RegexOption.IGNORE_CASE)
    private val EMOM = Regex("""\bEMOM\b""", RegexOption.IGNORE_CASE)

    private val CAP_CLOCK = Regex(
        """(\d{1,2}):([0-5]\d)\s*(?:min(?:ute)?s?\s*)?(?:time\s*)?cap\b""",
        RegexOption.IGNORE_CASE,
    )
    private val CAP_MINUTES = Regex(
        """(\d{1,2}(?:[.,]\d+)?)\s*(?:m|min|mins|minutes?)\s*(?:time\s*)?cap\b""",
        RegexOption.IGNORE_CASE,
    )
    private val CAP_AFTER = Regex(
        """\bcap\s*:?\s*(?:of\s*)?(\d{1,2})(?::([0-5]\d))?(?:\s*(?:m|min|mins|minutes?))?""",
        RegexOption.IGNORE_CASE,
    )
    // "T.C 15:00" / "TC: 15:00" — the abbreviation the workouts themselves use.
    private val TIME_CAP_CLOCK = Regex(
        """\bT\.?\s?C\.?\s*:?\s*(\d{1,2}):([0-5]\d)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val TIME_CAP_MINUTES = Regex(
        """\bT\.?\s?C\.?\s*:?\s*(\d{1,2}(?:[.,]\d+)?)\s*(?:m|min|mins|minutes?)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val TIME_CAP_BARE = Regex(
        """\bT\.?\s?C\.?\s*:?\s*(\d{1,2})(?=\s*(?:$|[.,;)]))""",
        RegexOption.IGNORE_CASE,
    )
    private val AMRAP_CLOCK = Regex(
        """(\d{1,2}):([0-5]\d)\s*(?:min(?:ute)?s?\s*)?amrap\b""",
        RegexOption.IGNORE_CASE,
    )
    private val AMRAP_MINUTES = Regex(
        """(\d{1,2}(?:[.,]\d+)?)\s*(?:m|min|mins|minutes?)\s*amrap\b""",
        RegexOption.IGNORE_CASE,
    )
    private val AMRAP_BEFORE = Regex(
        """\bamrap\s*(?:in\s*)?(\d{1,2})(?::([0-5]\d)|\s*(?:m|min|mins|minutes?))\b""",
        RegexOption.IGNORE_CASE,
    )
    private val AMRAP_BARE = Regex(
        """\bamrap\s*(?:in\s*)?(\d{1,2})(?=\s*(?:$|[.,;)]))""",
        RegexOption.IGNORE_CASE,
    )
    private val FOR_TIME_CLOCK = Regex(
        """(\d{1,2}):([0-5]\d)\s*for\s*time\b""",
        RegexOption.IGNORE_CASE,
    )
    private val FOR_TIME_MINUTES = Regex(
        """(\d{1,2}(?:[.,]\d+)?)\s*(?:m|min|mins|minutes?)\s*for\s*time\b""",
        RegexOption.IGNORE_CASE,
    )
    private val EMOM_CLOCK = Regex(
        """(\d{1,2}):([0-5]\d)\s*(?:min(?:ute)?s?\s*)?emom\b""",
        RegexOption.IGNORE_CASE,
    )
    private val EMOM_MINUTES = Regex(
        """(\d{1,2}(?:[.,]\d+)?)\s*(?:m|min|mins|minutes?)\s*emom\b""",
        RegexOption.IGNORE_CASE,
    )

    private val ROUND_WINDOW_CLOCK = Regex(
        """\b(\d{1,2})\s*(?:rounds?\s*(?:of|x|×)|[x×])\s*(\d{1,2}):([0-5]\d)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val ROUND_WINDOW_MINUTES = Regex(
        """\b(\d{1,2})\s*(?:rounds?\s*(?:of|x|×)|[x×])\s*(\d{1,2}(?:[.,]\d+)?)\s*(?:m|min|mins|minutes?)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val ROUND_WINDOW_CLOCK_AFTER = Regex(
        """\b(\d{1,2}):([0-5]\d)\s*rounds?\b""",
        RegexOption.IGNORE_CASE,
    )
    private val ROUND_WINDOW_MINUTES_AFTER = Regex(
        """\b(\d{1,2}(?:[.,]\d+)?)\s*(?:m|min|mins|minutes?)\s*rounds?\b""",
        RegexOption.IGNORE_CASE,
    )

    private val REST_CLOCK_BEFORE = Regex(
        """(\d{1,2}):([0-5]\d)\s*(?:of\s*)?rest\b""",
        RegexOption.IGNORE_CASE,
    )
    private val REST_CLOCK_AFTER = Regex(
        """\brest\s*(?:of\s*)?(\d{1,2}):([0-5]\d)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val REST_MINUTES_BEFORE = Regex(
        """(\d{1,2}(?:[.,]\d+)?)\s*(?:m|min|mins|minutes?)\s*(?:of\s*)?rest\b""",
        RegexOption.IGNORE_CASE,
    )
    private val REST_MINUTES_AFTER = Regex(
        """\brest\s*(?:of\s*)?(\d{1,2}(?:[.,]\d+)?)\s*(?:m|min|mins|minutes?)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val REST_SECONDS = Regex(
        """\brest\s*(?:of\s*)?(\d{1,3})\s*(?:s|sec|secs|seconds?)\b""",
        RegexOption.IGNORE_CASE,
    )
}

/**
 * What a live section leaves in the results when it ends: the section's own
 * score (a metcon's time, an AMRAP's rounds) and the "reached here" boundary
 * that keeps rows the session never got to out of the totals.
 */
object LiveResults {

    /** Record that none of a section's rows were reached. */
    fun markNotReached(
        results: Map<String, Map<String, String>>,
        section: LiveSection,
    ): Map<String, Map<String, String>> =
        if (section.items.isEmpty()) {
            results
        } else {
            InteractiveResults.set(
                results,
                InteractiveKeys.marker(section.index),
                InteractiveReached.KEY,
                InteractiveReached.NONE,
            )
        }

    fun finish(
        results: Map<String, Map<String, String>>,
        section: LiveSection,
        lastReachedRow: Int?,
        completed: Boolean,
        elapsedSeconds: Int?,
        roundsDone: Int?,
        /** Movements of the unfinished AMRAP round that were completed. */
        partialIndex: Int? = null,
    ): Map<String, Map<String, String>> {
        var next = results
        if (section.items.isNotEmpty()) {
            // An AMRAP repeats its one round, so a row marker says nothing;
            // a completed section has no unreached rows left to mark either.
            val marker = when {
                section.isAmrap -> ""
                completed -> ""
                lastReachedRow != null ->
                    section.items.getOrNull(lastReachedRow)?.scope.orEmpty()
                else -> ""
            }
            next = InteractiveResults.set(
                next,
                InteractiveKeys.marker(section.index),
                InteractiveReached.KEY,
                marker,
            )
        }
        if (section.showsSectionResult) {
            val scope = InteractiveKeys.section(section.index)
            // An AMRAP's clock is fixed by the workout, so its time is not a
            // result; what it leaves is rounds plus the reps of the unfinished
            // round. A For Time piece is the opposite: the clock is the result.
            if (!section.isAmrap && elapsedSeconds != null) {
                section.metrics.firstOrNull { it.kind == MetricKind.TIME }?.let { metric ->
                    next = InteractiveResults.set(
                        next,
                        scope,
                        metric.key,
                        TimeValue.format(
                            elapsedSeconds.coerceAtLeast(0) / 60,
                            elapsedSeconds.coerceAtLeast(0) % 60,
                        ),
                    )
                }
            }
            if (roundsDone != null) {
                val key = LiveScoring.roundsKey(section)
                next = InteractiveResults.set(
                    next, scope, key, roundsDone.coerceAtLeast(0).toString(),
                )
            }
            if (section.isAmrap) {
                next = InteractiveResults.set(
                    next,
                    scope,
                    LiveScoring.PARTIAL_KEY,
                    (partialIndex?.coerceAtLeast(0) ?: -1).toString(),
                )
            }
        }
        return next
    }
}

/** "1:30", "0:45" — the same m:ss the metric fields use. */
fun formatLiveClock(millis: Long): String {
    val total = ((millis.coerceAtLeast(0L) + 999L) / 1000L).toInt()
    val minutes = total / 60
    val seconds = total % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

/** Elapsed time, floored: a clock that reads 14:00 only once 14:00 is done. */
fun formatLiveElapsed(millis: Long): String {
    val total = (millis.coerceAtLeast(0L) / 1000L).toInt()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
