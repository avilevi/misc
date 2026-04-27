package com.hcexport

object SourceFilter {
    fun <T> pickByPriority(
        records: List<T>,
        startMs: (T) -> Long,
        endMs: (T) -> Long,
        sourcePkg: (T) -> String,
        priority: List<String>,
    ): List<T> {
        if (records.isEmpty()) return emptyList()
        val sorted = records.sortedBy { startMs(it) }
        // Group overlapping sessions into clusters
        val groups = mutableListOf<MutableList<T>>()
        for (r in sorted) {
            val last = groups.lastOrNull()
            if (last != null && startMs(r) < last.maxOf { endMs(it) }) {
                last.add(r)
            } else {
                groups.add(mutableListOf(r))
            }
        }
        // From each cluster pick the highest-priority source
        return groups.map { group ->
            for (pkg in priority) {
                val match = group.firstOrNull { sourcePkg(it) == pkg }
                if (match != null) return@map match
            }
            group.first()
        }
    }
}
