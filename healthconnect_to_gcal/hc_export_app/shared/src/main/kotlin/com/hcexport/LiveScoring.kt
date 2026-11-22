package com.hcexport

/**
 * The score of an AMRAP: the full rounds the athlete marked complete, plus
 * what was actually done of the round the cap interrupted.
 *
 * The score the workout asks for is the total number of reps — a movement
 * without a rep prescription (a 200m run, a 40s hold) counts as one rep, the
 * way those workouts are scored by hand.  The unfinished movement is read from
 * the values in the plan, so editing that last number later (the "5" of
 * "5 toes-to-bar") changes the score without re-running anything.
 */
data class AmrapScore(
    val rounds: Int,
    /** How many movements of the unfinished round were completed. */
    val partialIndex: Int?,
    /** The movement the cap stopped; null when the round was completed. */
    val currentItem: String?,
    /** Reps actually done of that movement, from the plan's values. */
    val currentReps: Int,
    /** Reps of the movements completed inside the unfinished round. */
    val completedReps: Int,
    /** The prescription of one whole round. */
    val perRound: Int,
    val total: Int,
) {
    /** "5 rounds + 5 reps of Toes-to-Bar = 151", the line the journal and notification show. */
    val detail: String
        get() {
            val roundPart = if (rounds > 0) {
                "$rounds ${if (rounds == 1) "round" else "rounds"}"
            } else {
                ""
            }
            val partialPart = when {
                currentItem != null && currentReps > 0 ->
                    "$currentReps ${if (currentReps == 1) "rep" else "reps"} of $currentItem"
                completedReps > 0 ->
                    "$completedReps ${if (completedReps == 1) "rep" else "reps"}"
                else -> ""
            }
            val parts = listOf(roundPart, partialPart).filter { it.isNotBlank() }
            return if (parts.isEmpty()) {
                "0 reps = 0"
            } else {
                "${parts.joinToString(" + ")} = $total"
            }
        }
}

object LiveScoring {

    /** The section-result key holding how far into the unfinished round we got. */
    const val PARTIAL_KEY = "partial"

    /** The rounds key used when the plan has no rounds metric of its own. */
    const val ROUNDS_KEY = "rounds"

    private val REPS_IN_TARGET =
        Regex("""(\d{1,3})\s*(?:reps?)\b""", RegexOption.IGNORE_CASE)
    private val NON_REP_TARGET = Regex(
        """(?:\d+(?:[.,]\d+)?\s*(?:m|km|mi|meter|meters|metre|metres|cal|kcal|calorie|calories|s|sec|secs|second|seconds|min|mins|minute|minutes)\b)|(?:\d{1,2}:\d{2})""",
        RegexOption.IGNORE_CASE,
    )
    private val LEADING_NUMBER = Regex("""^\s*(\d{1,3})\b""")
    private val ANY_NUMBER = Regex("""\b(\d{1,3})\b""")
    private val PERCENT = Regex("""\d{1,3}(?:[.,]\d+)?\s*%""")

    /** The key a section's rounds are stored under: its own metric, else the default. */
    fun roundsKey(section: LiveSection): String =
        section.metrics.firstOrNull { it.kind == MetricKind.ROUNDS }?.key ?: ROUNDS_KEY

    /**
     * The score a finished AMRAP left in the results, or null while the
     * section has not been ended (no rounds and no partial boundary).
     */
    fun amrapScore(
        section: LiveSection,
        results: Map<String, Map<String, String>>,
    ): AmrapScore? {
        if (!section.isAmrap) return null
        val scope = InteractiveKeys.section(section.index)
        val values = results[scope].orEmpty()
        val rounds = values[roundsKey(section)]?.trim()?.toIntOrNull()
        val hasPartial = values.containsKey(PARTIAL_KEY)
        if (rounds == null && !hasPartial) return null
        val partialIndex = values[PARTIAL_KEY]?.trim()?.toIntOrNull()?.takeIf { it >= 0 }
        return compute(section, rounds ?: 0, partialIndex, results)
    }

    /**
     * The score from a live state: [roundsDone] full rounds and [partialIndex]
     * movements done inside the next one. A null [partialIndex] means the
     * round itself was completed, so only full rounds count.
     */
    fun compute(
        section: LiveSection,
        roundsDone: Int,
        partialIndex: Int?,
        results: Map<String, Map<String, String>>,
    ): AmrapScore {
        val items = section.items
        val perRound = items.sumOf { prescribedReps(it) }
        val completedCount = partialIndex?.coerceIn(0, items.size) ?: 0
        val completedReps = items.take(completedCount).sumOf { item ->
            repsOf(item, results) ?: prescribedReps(item)
        }
        val current = partialIndex?.let { items.getOrNull(completedCount) }
        val currentReps = current?.let { repsOf(it, results) ?: 0 } ?: 0
        return AmrapScore(
            rounds = roundsDone.coerceAtLeast(0),
            partialIndex = partialIndex?.coerceIn(0, items.size),
            currentItem = current?.name,
            currentReps = currentReps,
            completedReps = completedReps,
            perRound = perRound,
            total = roundsDone.coerceAtLeast(0) * perRound + completedReps + currentReps,
        )
    }

    /** A whole round's prescription, for showing what one round is worth. */
    fun perRoundReps(section: LiveSection): Int =
        section.items.sumOf { prescribedReps(it) }

    /**
     * One movement's rep prescription. "15 reps" and "10 toes-to-bar" are 15
     * and 10; a distance, a hold or a calorie row is one rep; "max" is one.
     */
    fun prescribedReps(item: LiveItem): Int {
        val target = item.target.trim()
        if (target.isBlank()) return 1
        REPS_IN_TARGET.find(target)?.let { match ->
            match.groupValues[1].toIntOrNull()?.let { return it.coerceAtLeast(0) }
        }
        if (NON_REP_TARGET.containsMatchIn(target)) return 1
        val cleaned = target.replace(PERCENT, " ")
        LEADING_NUMBER.find(cleaned)?.let { match ->
            match.groupValues[1].toIntOrNull()?.let { return it.coerceAtLeast(0) }
        }
        ANY_NUMBER.find(cleaned)?.let { match ->
            match.groupValues[1].toIntOrNull()?.let { return it.coerceAtLeast(0) }
        }
        return 1
    }

    /**
     * The reps recorded for a movement, if it has a rep field with a number.
     * Never reads a distance, time or calorie field as reps.
     */
    fun repsOf(
        item: LiveItem,
        results: Map<String, Map<String, String>>,
    ): Int? {
        val metric = item.metrics.firstOrNull { it.kind == MetricKind.REPS } ?: return null
        val raw = InteractiveResults.value(results, item.scope, metric.key).trim()
        if (raw.isBlank()) return null
        return raw.toIntOrNull()
            ?: raw.replace(',', '.').toDoubleOrNull()?.toInt()
            ?: Regex("""\d+""").find(raw)?.value?.toIntOrNull()
    }

    /**
     * Fresh values for a new round of an AMRAP: the reps, checks and splits
     * belong to the round that just ended, not to the repeat about to start.
     * Weights, distances and notes carry over — they are what the athlete
     * keeps using.
     */
    fun resetRoundValues(
        results: Map<String, Map<String, String>>,
        section: LiveSection,
    ): Map<String, Map<String, String>> {
        var next = results
        section.items.forEach { item ->
            item.metrics
                .filter {
                    it.kind == MetricKind.REPS ||
                        it.kind == MetricKind.CHECK ||
                        it.kind == MetricKind.TIME
                }
                .forEach { metric ->
                    next = InteractiveResults.remove(next, item.scope, metric.key)
                }
        }
        return next
    }

    /**
     * A previous AMRAP result (rounds, partial boundary, auto-recorded times)
     * must not leak into a session starting the same section again.
     */
    fun resetAmrap(
        results: Map<String, Map<String, String>>,
        section: LiveSection,
    ): Map<String, Map<String, String>> {
        var next = resetRoundValues(results, section)
        val scope = InteractiveKeys.section(section.index)
        next = InteractiveResults.remove(next, scope, PARTIAL_KEY)
        next = InteractiveResults.remove(next, scope, ROUNDS_KEY)
        section.metrics
            .filter { it.kind == MetricKind.ROUNDS || it.kind == MetricKind.TIME }
            .forEach { metric -> next = InteractiveResults.remove(next, scope, metric.key) }
        return next
    }

    /** Whether a finished AMRAP result is already stored for [section]. */
    fun hasFinishedAmrap(
        results: Map<String, Map<String, String>>,
        section: LiveSection,
    ): Boolean {
        if (!section.isAmrap) return false
        val values = results[InteractiveKeys.section(section.index)].orEmpty()
        return values.containsKey(roundsKey(section)) || values.containsKey(PARTIAL_KEY)
    }
}
