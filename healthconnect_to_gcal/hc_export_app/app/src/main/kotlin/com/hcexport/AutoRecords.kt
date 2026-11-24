package com.hcexport

/**
 * Records earned by the values entered into a strength workout.
 *
 * A strength set logged as weight × reps is an attempt at that rep max: three
 * reps at 100 kg is a 3RM attempt. For each exercise and rep max the workout
 * keeps only its best set; the journal stores a record for it when that set
 * beats the best already recorded, or when the lift has no record at all yet.
 * Metcon and accessory work is left alone — those numbers are conditioning, not
 * rep-max attempts. No estimated 1RM is derived from higher-rep sets.
 */
object AutoRecords {

    /** Rep maxes worth tracking this way; anything higher is conditioning volume. */
    val TRACKED_REPS = 1..10

    data class Candidate(
        val exerciseKey: String,
        val exerciseName: String,
        val reps: Int,
        val weightKg: Double,
    )

    /**
     * The best attempt at each rep max of the plan's strength sections, keyed by
     * exercise and reps. A set counts once its weight was entered; the reps
     * field may still hold the prescription's prefill. Rows past a section's
     * "reached here" marker were never performed and are left out.
     */
    fun candidates(
        plan: InteractivePlan,
        results: Map<String, Map<String, String>>,
    ): List<Candidate> {
        val best = LinkedHashMap<Pair<String, Int>, Candidate>()
        plan.sections.forEachIndexed sectionLoop@ { sectionIndex, section ->
            if (section.kind != "strength") return@sectionLoop
            val rows = InteractiveRows.scopes(section, sectionIndex)
            val reached = InteractiveReached.indexOf(results, sectionIndex, rows)
            section.exercises.forEachIndexed exerciseLoop@ { exerciseIndex, exercise ->
                val exerciseKey = RecordLogic.exerciseKey(exercise.name)
                // A name the key cannot represent (e.g. Hebrew) would share the
                // "other" bucket with every other such lift.
                if (exerciseKey == "other") return@exerciseLoop
                val exerciseName = exercise.name.trim()
                exercise.sets.forEachIndexed setLoop@ { setIndex, set ->
                    val scope = InteractiveKeys.set(sectionIndex, exerciseIndex, setIndex)
                    if (reached != null) {
                        val rowIndex = rows.indexOf(scope)
                        if (rowIndex < 0 || rowIndex > reached) return@setLoop
                    }
                    val metrics = set.metrics.ifEmpty { exercise.metrics }
                    val weightMetric = metrics.firstOrNull { it.kind == MetricKind.WEIGHT }
                        ?: return@setLoop
                    val repsMetric = metrics.firstOrNull { it.kind == MetricKind.REPS }
                        ?: return@setLoop
                    val weight = weightKg(
                        InteractiveResults.value(results, scope, weightMetric.key),
                        weightMetric,
                    ) ?: return@setLoop
                    val reps = reps(scope, results, repsMetric, set.target)
                        ?: return@setLoop
                    if (reps !in TRACKED_REPS) return@setLoop
                    val candidate = Candidate(exerciseKey, exerciseName, reps, weight)
                    val key = exerciseKey to reps
                    val current = best[key]
                    if (current == null || weight > current.weightKg) best[key] = candidate
                }
            }
        }
        return best.values.toList()
    }

    /**
     * The plan row a stored weight record came from, if it is still there: the
     * set whose lift, reps and entered weight match. A manual record, or one
     * whose set was edited since, has no row.
     */
    fun scopeFor(
        plan: InteractivePlan,
        results: Map<String, Map<String, String>>,
        record: WeightRecord,
    ): String? {
        var found: String? = null
        plan.sections.forEachIndexed sectionLoop@ { sectionIndex, section ->
            if (section.kind != "strength" || found != null) return@sectionLoop
            section.exercises.forEachIndexed exerciseLoop@ { exerciseIndex, exercise ->
                if (found != null) return@exerciseLoop
                if (RecordLogic.exerciseKey(exercise.name) != record.exerciseKey) {
                    return@exerciseLoop
                }
                exercise.sets.forEachIndexed setLoop@ { setIndex, set ->
                    if (found != null) return@setLoop
                    val scope = InteractiveKeys.set(sectionIndex, exerciseIndex, setIndex)
                    val metrics = set.metrics.ifEmpty { exercise.metrics }
                    val weightMetric = metrics.firstOrNull { it.kind == MetricKind.WEIGHT }
                        ?: return@setLoop
                    val repsMetric = metrics.firstOrNull { it.kind == MetricKind.REPS }
                        ?: return@setLoop
                    val weight = weightKg(
                        InteractiveResults.value(results, scope, weightMetric.key),
                        weightMetric,
                    ) ?: return@setLoop
                    val reps = reps(scope, results, repsMetric, set.target)
                        ?: return@setLoop
                    if (reps == record.reps &&
                        kotlin.math.abs(weight - record.weightKg) < 0.01
                    ) {
                        found = scope
                    }
                }
            }
        }
        return found
    }

    /**
     * The candidates worth storing: those that beat the best [existing] record
     * for the same lift and rep max, plus the first record for a lift that has
     * none. Equal weight is not a broken record.
     */
    fun beating(candidates: List<Candidate>, existing: List<WeightRecord>): List<Candidate> {
        if (candidates.isEmpty()) return emptyList()
        val bestByKey = existing
            .groupBy { it.exerciseKey to it.reps }
            .mapValues { (_, rows) -> rows.maxOf { it.weightKg } }
        return candidates.filter { candidate ->
            val best = bestByKey[candidate.exerciseKey to candidate.reps]
            best == null || candidate.weightKg > best
        }
    }

    /**
     * The rows to add: candidates that beat the standing record and that this
     * workout has not already recorded. Past records are never rewritten.
     */
    fun newRows(
        candidates: List<Candidate>,
        existing: List<WeightRecord>,
        alreadyRecorded: Set<Pair<String, Int>>,
    ): List<Candidate> = beating(candidates, existing)
        .filter { candidate -> (candidate.exerciseKey to candidate.reps) !in alreadyRecorded }

    private fun reps(
        scope: String,
        results: Map<String, Map<String, String>>,
        metric: MetricDef,
        target: String,
    ): Int? {
        val stored = InteractiveResults.value(results, scope, metric.key)
        val text = stored.ifBlank {
            InteractiveDefaults.forTarget(target, metric).orEmpty()
        }
        return text.trim().toIntOrNull()
            ?: Regex("""\d{1,3}""").find(text)?.value?.toIntOrNull()
    }

    private fun weightKg(raw: String, metric: MetricDef): Double? {
        val value = raw.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (value <= 0.0) return null
        return if (metric.unit.equals("lb", ignoreCase = true) ||
            metric.unit.equals("lbs", ignoreCase = true)
        ) {
            value * 0.45359237
        } else {
            value
        }
    }
}
