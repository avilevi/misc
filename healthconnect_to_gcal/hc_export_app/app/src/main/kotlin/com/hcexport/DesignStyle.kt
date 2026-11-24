package com.hcexport

/**
 * How much the journal explains itself.
 *
 * Minimal is the default: one summary card, the facts, and no commentary.
 * Verbose is the original, fully annotated design.
 */
enum class DesignStyle(val key: String, val label: String) {
    MINIMAL("minimal", "Minimal"),
    VERBOSE("verbose", "Verbose");

    companion object {
        fun fromKey(key: String?): DesignStyle =
            entries.firstOrNull { it.key == key } ?: MINIMAL
    }
}
