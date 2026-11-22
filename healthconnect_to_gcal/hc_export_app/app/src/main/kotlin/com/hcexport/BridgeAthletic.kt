package com.hcexport

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * BridgeAthletic workout source.
 *
 * Parse-only: the roster (the athlete's assigned workout histories), the program
 * template (blocks/sets/exercises for every workout), and the rendering into the
 * app's workout-text convention. No Android APIs, so it is unit-tested on the JVM.
 *
 * The roster endpoint returns a window of the current program; the program endpoint
 * returns the whole plan including the workouts that have not been opened yet and
 * therefore have no athlete-specific block histories.
 */
object BridgeAthletic {

    /** Unique workout-database key for this source. */
    const val TYPE_KEY = "bridgeathletic"
    const val TYPE_LABEL = "BridgeAthletic"

    data class RosterRow(
        val workoutHistoryId: Int,
        val workoutId: Int,
        val programId: Int,
        val date: String,
        val name: String,
        val status: String,
        val phaseName: String?,
        val weekNumber: Int?,
    )

    data class Roster(val rows: List<RosterRow>, val programIds: Set<Int>, val programNames: Map<Int, String>)

    data class TemplateSet(
        val exercise: String,
        val reps: String?,
        val timeSec: Long?,
        val weight: String?,
        val restSec: Long?,
        val note: String?,
    )

    data class TemplateBlock(
        val name: String,
        val isCircuit: Boolean,
        val rounds: Int?,
        val sets: List<TemplateSet>,
    )

    data class TemplateWorkout(val name: String, val blocks: List<TemplateBlock>)

    data class Template(
        val programId: Int,
        val updatedAt: String?,
        val name: String?,
        val workouts: Map<Int, TemplateWorkout>,
    )

    // ── Roster ─────────────────────────────────────────────────────────────

    fun parseRoster(json: String): Roster {
        val root = JSONObject(json)
        val linked = root.optJSONObject("linked") ?: JSONObject()

        val phaseHistories = linked.objectsById("phaseHistories", "phaseHistoryId")
        val programHistories = linked.objectsById("programHistories", "programHistoryId")
        val workouts = linked.objectsById("workouts", "workoutId")

        val rows = linked.objects("workoutHistories").mapNotNull { wh ->
            val date = wh.str("startDate")?.take(10).orEmpty()
            if (!WorkoutTypes.isValidDate(date)) return@mapNotNull null

            val phaseHistory = phaseHistories[wh.optInt("phaseHistoryId")]
            val programHistory = programHistories[phaseHistory?.optInt("programHistoryId") ?: -1]
            val workout = workouts[wh.optInt("workoutId")]

            RosterRow(
                workoutHistoryId = wh.optInt("workoutHistoryId"),
                workoutId = wh.optInt("workoutId"),
                programId = programHistory?.optInt("programId") ?: 0,
                date = date,
                name = wh.str("name").orEmpty(),
                status = wh.str("status").orEmpty(),
                phaseName = phaseHistory?.str("name"),
                weekNumber = workout?.optInt("weekNumber", 0)?.takeIf { it > 0 },
            )
        }.sortedBy { it.date }

        val programs = programHistories.values
            .mapNotNull { ph -> ph.optInt("programId").takeIf { it > 0 }?.let { it to ph.str("name").orEmpty() } }
            .toMap()

        return Roster(
            rows = rows,
            programIds = programs.keys,
            programNames = programs,
        )
    }

    // ── Program template ───────────────────────────────────────────────────

    fun parseTemplate(json: String): Template {
        val root = JSONObject(json)
        val program = root.optJSONObject("programs") ?: JSONObject()
        val linked = root.optJSONObject("linked") ?: JSONObject()

        val exercises = linked.objects("exercises")
            .associate { it.optInt("exerciseId") to it.str("name").orEmpty() }
        val blocksByWorkout = linked.objects("blocks").groupBy { it.optInt("workoutId") }
        val setsByBlock = linked.objects("blockSets").groupBy { it.optInt("blockId") }

        val workouts = linked.objects("workouts").mapNotNull { w ->
            val workoutId = w.optInt("workoutId")
            if (workoutId <= 0) return@mapNotNull null
            val blocks = (blocksByWorkout[workoutId] ?: emptyList())
                .sortedBy { it.optInt("sequence") }
                .map { b ->
                    val sets = (setsByBlock[b.optInt("blockId")] ?: emptyList())
                        .sortedWith(compareBy({ it.optInt("roundNumber", 1) }, { it.optInt("sequence") }))
                        .map { s -> templateSet(s, exercises) }
                    TemplateBlock(
                        name = b.str("name").orEmpty(),
                        isCircuit = b.optBoolean("isCircuit"),
                        rounds = b.optInt("rounds", 0).takeIf { it > 0 },
                        sets = sets,
                    )
                }
            workoutId to TemplateWorkout(name = w.str("name").orEmpty(), blocks = blocks)
        }.toMap()

        return Template(
            programId = program.optInt("programId"),
            updatedAt = program.str("updatedAt"),
            name = program.str("name"),
            workouts = workouts,
        )
    }

    // ── Rendering ──────────────────────────────────────────────────────────

    /** Heading plus the cached block text for one athlete workout. */
    fun render(row: RosterRow, blocksText: String?): String {
        val heading = buildString {
            append(row.name.ifBlank { "BridgeAthletic workout" })
            val meta = listOfNotNull(
                row.phaseName?.takeIf { it.isNotBlank() },
                row.weekNumber?.let { "Week $it" },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) append('\n').append(meta)
        }
        val body = blocksText?.trim().orEmpty()
        return if (body.isEmpty()) heading else "$heading\n\n$body"
    }

    /** The block/set body of one template workout, without the heading. */
    fun blocksText(workout: TemplateWorkout?): String {
        val blocks = workout?.blocks.orEmpty()
        if (blocks.isEmpty()) return ""
        val out = StringBuilder()
        for (block in blocks) {
            if (block.name.isBlank() && block.sets.isEmpty()) continue
            if (isWarmUp(block.name)) continue
            if (out.isNotEmpty()) out.append("\n\n")
            out.append(blockHeader(block))
            for (line in collapse(block.sets)) {
                out.append("\n- ").append(line)
            }
        }
        return out.toString()
    }

    /** Warm-up blocks (massage, rotations) are noise in this app; they are dropped. */
    internal fun isWarmUp(name: String): Boolean {
        val n = name.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ")
        return n.startsWith("warm up") || n.startsWith("warmup") || n.startsWith("warm-up")
    }

    private fun blockHeader(block: TemplateBlock): String {
        val base = block.name.ifBlank { "Block" }
        val suffix = when {
            block.rounds != null -> " (${block.rounds} rounds)"
            block.isCircuit -> " (circuit)"
            else -> ""
        }
        val header = base + suffix
        return if (header.length <= 44 && ':' !in header) "$header:" else "$base:"
    }

    /** Consecutive identical set lines collapse into one line marked "× N". */
    private fun collapse(sets: List<TemplateSet>): List<String> {
        val lines = sets.map(::setLine).filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        var current = lines.first()
        var count = 1
        for (line in lines.drop(1)) {
            if (line == current) {
                count++
            } else {
                out += if (count > 1) "$current × $count" else current
                current = line
                count = 1
            }
        }
        out += if (count > 1) "$current × $count" else current
        return out
    }

    private fun setLine(set: TemplateSet): String {
        val main = listOfNotNull(
            set.reps,
            set.timeSec?.let { timeText(it * 1000) },
        ).joinToString(" · ")
        val head = listOfNotNull(main.ifBlank { null }, set.weight).joinToString(" ")
        val extras = listOfNotNull(
            set.restSec?.let { "rest ${timeText(it * 1000)}" },
            set.note?.takeIf { it.isNotBlank() }?.let { "($it)" },
        )
        val detail = listOfNotNull(head.ifBlank { null }, extras.joinToString(" · ").ifBlank { null })
            .joinToString(" · ")
        val exercise = set.exercise.ifBlank { "Set" }
        return if (detail.isEmpty()) exercise else "$exercise — $detail"
    }

    private fun templateSet(set: JSONObject, exercises: Map<Int, String>): TemplateSet {
        val prescribed = set.optJSONObject("prescribed")
        return TemplateSet(
            exercise = exercises[set.optInt("exerciseId")]?.trim().orEmpty(),
            reps = prescribed?.optJSONObject("reps")?.let(::repsText),
            timeSec = prescribed?.optJSONObject("time")?.let { t ->
                if (t.has("value")) (t.optLong("value") / 1000L).takeIf { it > 0 } else null
            },
            weight = prescribed?.optJSONObject("weight")?.let(::weightText),
            restSec = set.optLong("restTime", 0).takeIf { it > 0 },
            note = set.str("note"),
        )
    }

    private fun repsText(o: JSONObject): String? {
        if (o.optBoolean("isRange") && o.has("min") && o.has("max")) {
            return "${number(o.optDouble("min"))}–${number(o.optDouble("max"))} reps"
        }
        if (o.has("value")) {
            val v = o.optDouble("value")
            if (v.isNaN()) return null
            return "${number(v)} ${if (v == 1.0) "rep" else "reps"}"
        }
        return null
    }

    private fun weightText(o: JSONObject): String? {
        val unit = o.str("unit") ?: "kg"
        val percent = o.optJSONObject("modifier")?.optDouble("value", Double.NaN) ?: Double.NaN
        if (!percent.isNaN() && percent > 0) return "@ ${number(percent / 10_000.0)}%"
        if (o.optBoolean("isRange") && o.has("min") && o.has("max")) {
            return "@ ${number(o.optDouble("min") / 1e6)}–${number(o.optDouble("max") / 1e6)} $unit"
        }
        if (o.has("value")) return "@ ${number(o.optDouble("value") / 1e6)} $unit"
        if (o.has("min") && o.has("max")) {
            return "@ ${number(o.optDouble("min") / 1e6)}–${number(o.optDouble("max") / 1e6)} $unit"
        }
        return null
    }

    private fun timeText(ms: Long): String {
        if (ms < 60_000) return "${ms / 1000}s"
        val secs = ms / 1000
        return "${secs / 60}:${(secs % 60).toString().padStart(2, '0')}"
    }

    private fun number(value: Double): String {
        if (value.isNaN()) return ""
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == Math.floor(rounded)) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", rounded)
        }
    }

    // ── JSON helpers ───────────────────────────────────────────────────────

    /** org.json returns the string "null" for a JSON null; this keeps it null. */
    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key).trim().ifBlank { null }

    /** Values of a "linked" section, which the API sends either as an object keyed
     *  by id or as an array. */
    private fun JSONObject.objects(key: String): List<JSONObject> {
        optJSONObject(key)?.let { obj ->
            return obj.keys().asSequence()
                .mapNotNull { obj.optJSONObject(it) }
                .toList()
        }
        val array: JSONArray = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    private fun JSONObject.objectsById(key: String, idField: String): Map<Int, JSONObject> =
        objects(key).mapNotNull { o ->
            val id = o.optInt(idField)
            if (id <= 0) null else id to o
        }.toMap()
}
