package com.hcexport

/** Display-ready blocks for a stored workout description. Pure Kotlin, unit-tested. */
sealed interface WorkoutBlock {
    data class Header(val text: String) : WorkoutBlock
    data class Bullet(val text: String) : WorkoutBlock
    data class Body(val text: String) : WorkoutBlock
}

object WorkoutText {

    private val HEADER_COLON = Regex("^[^:]{2,44}:$")
    private val LETTER_HEADER = Regex("^[A-Z]\\)$")

    fun parse(content: String): List<WorkoutBlock> {
        val blocks = mutableListOf<WorkoutBlock>()
        val paragraph = mutableListOf<String>()

        fun flush() {
            if (paragraph.isEmpty()) return
            blocks += WorkoutBlock.Body(paragraph.joinToString("\n"))
            paragraph.clear()
        }

        for (raw in content.replace("\r\n", "\n").lines()) {
            val line = raw.trimEnd()
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                flush()
                continue
            }

            val bullet = bulletText(trimmed)
            if (bullet != null) {
                flush()
                blocks += WorkoutBlock.Bullet(bullet)
                continue
            }

            if (HEADER_COLON.matches(trimmed) || LETTER_HEADER.matches(trimmed)) {
                flush()
                val text = if (LETTER_HEADER.matches(trimmed)) trimmed.removeSuffix(")")
                else trimmed.removeSuffix(":")
                blocks += WorkoutBlock.Header(text.trim())
                continue
            }

            paragraph += trimmed
        }
        flush()
        return blocks
    }

    /** The first useful line, for a card subtitle. */
    fun summary(content: String): String {
        for (block in parse(content)) {
            when (block) {
                is WorkoutBlock.Body -> return block.text.lineSequence()
                    .firstOrNull { it.isNotBlank() }?.trim()?.trimEnd(':')?.trim().orEmpty()
                is WorkoutBlock.Bullet -> return block.text
                is WorkoutBlock.Header -> Unit
            }
        }
        return content.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.trimEnd(':')?.trim().orEmpty()
    }

    /** "🔥WOD" -> "WOD", "🫁 HYROX" -> "HYROX". */
    fun displayType(label: String): String =
        label.replace(Regex("^[^\\p{L}\\p{N}]+"), "").trim().ifBlank { label.trim() }

    private fun bulletText(trimmed: String): String? {
        val marker = trimmed.firstOrNull() ?: return null
        if (marker !in charArrayOf('-', '•', '*', '–', '—')) return null
        val rest = trimmed.drop(1).trim()
        return rest.ifBlank { null }
    }
}
