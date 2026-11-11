package com.hcexport

/**
 * Parses the HYPR workouts page.
 *
 * The page wraps every date in a <details name="YYYY-MM-DD"> block; inside it each
 * workout type has its own <details name="YYYY-MM-DD"> block with the type in the
 * summary and the workout in <p class="workout-summary">.
 *
 * Pure Kotlin on purpose: no Android APIs, so it can be unit-tested on the JVM.
 */
object WorkoutParser {

    private val BLOCK = Regex(
        """<details[^>]+name="(\d{4}-\d{2}-\d{2})"[^>]*>\s*<summary[^>]*>.*?""" +
            """<div class="icon"></div>\s*<div>([^<]+)</div>\s*</summary>\s*""" +
            """<p class="workout-summary">(.*?)</p>""",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )

    private val ENTITY = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);")

    private val NAMED_ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "hellip" to "…", "mdash" to "—", "ndash" to "–",
        "rsquo" to "’", "lsquo" to "‘", "ldquo" to "“", "rdquo" to "”",
        "times" to "×", "divide" to "÷", "deg" to "°", "middot" to "·",
    )

    fun parse(html: String): List<Workout> {
        val byKey = LinkedHashMap<String, Workout>()
        val nextOrder = mutableMapOf<String, Int>()

        for (match in BLOCK.findAll(html)) {
            val date = match.groupValues[1]
            if (!WorkoutTypes.isValidDate(date)) continue

            val label = unescape(match.groupValues[2]).trim()
            if (label.isEmpty()) continue
            val key = WorkoutTypes.keyFor(label)
            val content = normalise(unescape(match.groupValues[3]))
            if (content.isEmpty()) continue

            val order = nextOrder.getOrDefault(date, 0)
            val id = "$date|$key"
            if (id in byKey) continue // keep the first occurrence and its order
            nextOrder[date] = order + 1
            byKey[id] = Workout(
                date = date,
                typeKey = key,
                typeLabel = label,
                content = content,
                sortOrder = order,
            )
        }
        return byKey.values.toList()
    }

    fun unescape(text: String): String = ENTITY.replace(text) { match ->
        val entity = match.groupValues[1]
        when {
            entity.startsWith("#x", ignoreCase = true) ->
                entity.substring(2).toIntOrNull(16)
                    ?.let { String(Character.toChars(it)) } ?: match.value
            entity.startsWith("#") ->
                entity.substring(1).toIntOrNull()
                    ?.let { String(Character.toChars(it)) } ?: match.value
            else -> NAMED_ENTITIES[entity] ?: match.value
        }
    }

    private fun normalise(raw: String): String =
        raw.replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
}
