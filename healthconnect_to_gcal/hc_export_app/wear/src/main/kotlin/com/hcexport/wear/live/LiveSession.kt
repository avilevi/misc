package com.hcexport.wear.live

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hcexport.InteractiveDefaults
import com.hcexport.InteractiveKeys
import com.hcexport.InteractiveReached
import com.hcexport.InteractiveResults
import com.hcexport.LiveItem
import com.hcexport.LivePlan
import com.hcexport.LiveResults
import com.hcexport.LiveScoring
import com.hcexport.LiveSection
import com.hcexport.MetricDef
import com.hcexport.MetricKind
import com.hcexport.TimeValue
import com.hcexport.shared.DEVICE_WATCH
import com.hcexport.shared.LiveSessionDto
import java.util.UUID

enum class LivePhase { OVERVIEW, GET_READY, ACTIVE, DONE }

/** The watch's haptic voice: a tick for the last seconds, a longer go. */
interface LiveCues {
    fun tick()
    fun go()

    object None : LiveCues {
        override fun tick() = Unit
        override fun go() = Unit
    }
}

/**
 * The watch's running session: the same state machine the phone's live screen
 * uses, with the same timings, the same value scopes and the same "reached
 * here" boundary.
 *
 * It is a plain Compose-state class (no Android APIs), so the whole walk
 * through a workout is unit-testable. [adopt] is the two-way link: a record
 * from the phone replaces every field, and the caller records the resulting
 * fingerprint so the watch never echoes it back.
 */
@Stable
class LiveSession(
    val live: LivePlan,
    private val leadInSeconds: Int,
    private val cues: LiveCues = LiveCues.None,
    initialResults: Map<String, Map<String, String>> = emptyMap(),
) {
    var sectionIndex by mutableIntStateOf(0)
    var itemIndex by mutableIntStateOf(0)
    var phase by mutableStateOf(LivePhase.OVERVIEW)
    var paused by mutableStateOf(false)

    var leadInRemainingMs by mutableLongStateOf(0L)
    var intervalRemainingMs by mutableLongStateOf(0L)
    var sectionElapsedMs by mutableLongStateOf(0L)
    var sessionElapsedMs by mutableLongStateOf(0L)
    var roundsDone by mutableIntStateOf(0)

    /** The item the workout is actually on, while a review looks back. */
    var liveItemIndex by mutableIntStateOf(0)
    var restRemainingMs by mutableLongStateOf(0L)
    var currentRound by mutableIntStateOf(0)
    var itemDone by mutableStateOf(false)
    var roundWorkDone by mutableStateOf(false)
    var resting by mutableStateOf(false)
    var lastSplitElapsedMs by mutableLongStateOf(0L)

    var started by mutableStateOf(false)
    var results by mutableStateOf(initialResults)
        private set

    /** The values the session started from; pushes carry only their changes. */
    var baseResults by mutableStateOf(initialResults)
        private set

    /** Bumped whenever a value changes; the screen persists on it. */
    var resultsRevision by mutableIntStateOf(0)
        private set

    // The shared session identity; adopting a phone session adopts its id.
    var syncSessionId by mutableStateOf(UUID.randomUUID().toString())
    var syncStartedAt by mutableStateOf(System.currentTimeMillis())
    var lastSyncFingerprint: String? = null

    private val visited = mutableMapOf<Int, Int>()
    private var lastCueSecond = -1

    init {
        val first = live.sections.firstOrNull()
        if (first != null && first.items.isNotEmpty()) {
            if (leadInSeconds <= 0) {
                beginActive()
            } else {
                leadInRemainingMs = leadInSeconds * 1000L
                phase = LivePhase.GET_READY
            }
        }
    }

    val section: LiveSection
        get() = live.sections.getOrElse(sectionIndex) { live.sections.first() }

    val item: LiveItem?
        get() = section.items.getOrNull(itemIndex)

    val reviewing: Boolean get() = itemIndex < liveItemIndex
    val previewing: Boolean get() = itemIndex > liveItemIndex
    val onLiveItem: Boolean get() = itemIndex == liveItemIndex
    val isLastSection: Boolean get() = sectionIndex >= live.sections.lastIndex

    // ── Moving through the plan ─────────────────────────────────────────────

    fun beginSection(index: Int) {
        sectionIndex = index.coerceIn(0, live.sections.lastIndex)
        itemIndex = 0
        liveItemIndex = 0
        roundsDone = 0
        currentRound = 0
        itemDone = false
        roundWorkDone = false
        resting = false
        restRemainingMs = 0
        sectionElapsedMs = 0
        intervalRemainingMs = 0
        leadInRemainingMs = 0
        paused = false
        phase = LivePhase.OVERVIEW
        lastCueSecond = -1
    }

    fun startCurrentSection() {
        val current = section
        if (current.isAmrap && LiveScoring.hasFinishedAmrap(results, current)) {
            replaceResults(LiveScoring.resetAmrap(results, current))
        }
        if (current.items.isEmpty() && current.metrics.isEmpty()) {
            finishSection(completed = true)
            return
        }
        if (leadInSeconds <= 0) {
            beginActive()
            return
        }
        leadInRemainingMs = leadInSeconds * 1000L
        lastCueSecond = -1
        phase = LivePhase.GET_READY
        paused = false
    }

    fun beginActive() {
        started = true
        phase = LivePhase.ACTIVE
        paused = false
        sectionElapsedMs = 0
        roundsDone = 0
        currentRound = 0
        itemDone = false
        roundWorkDone = false
        resting = false
        restRemainingMs = 0
        lastSplitElapsedMs = 0
        itemIndex = 0
        liveItemIndex = 0
        intervalRemainingMs = when {
            section.roundTimed -> (section.roundSeconds ?: 0) * 1000L
            section.intervalSeconds != null -> section.intervalSeconds!! * 1000L
            else -> 0L
        }
        lastCueSecond = -1
        enterItem()
    }

    fun skipLeadIn() {
        if (phase != LivePhase.GET_READY) return
        cues.go()
        beginActive()
    }

    fun tick(deltaMs: Long) {
        if (paused || reviewing) return
        when (phase) {
            LivePhase.GET_READY -> {
                sessionElapsedMs += deltaMs
                leadInRemainingMs -= deltaMs
                cueCountdown(leadInRemainingMs)
                if (leadInRemainingMs <= 0L) {
                    cues.go()
                    beginActive()
                }
            }

            LivePhase.ACTIVE -> {
                sessionElapsedMs += deltaMs
                sectionElapsedMs += deltaMs
                if (resting) {
                    restRemainingMs -= deltaMs
                    cueCountdown(restRemainingMs)
                    if (restRemainingMs <= 0L) {
                        cues.go()
                        startNextRound()
                    }
                    return
                }
                if (section.roundTimed) {
                    intervalRemainingMs -= deltaMs
                    cueCountdown(intervalRemainingMs)
                    if (intervalRemainingMs <= 0L) {
                        cues.go()
                        endRound()
                    }
                } else if (section.intervalSeconds != null && section.items.isNotEmpty()) {
                    intervalRemainingMs -= deltaMs
                    cueCountdown(intervalRemainingMs)
                    if (intervalRemainingMs <= 0L) {
                        cues.go()
                        advanceWindow()
                    }
                } else if (section.clocked) {
                    val cap = section.timeCapSeconds
                    if (cap != null && sectionElapsedMs >= cap * 1000L) {
                        cues.go()
                        finishSection(completed = false)
                    }
                }
            }

            else -> Unit
        }
    }

    private fun cueCountdown(remainingMs: Long) {
        val second = ((remainingMs + 999L) / 1000L).toInt()
        if (second in 1..3 && second != lastCueSecond) {
            lastCueSecond = second
            cues.tick()
        }
    }

    fun completeAndAdvance() {
        if (phase != LivePhase.ACTIVE || !onLiveItem || resting || roundWorkDone) return
        val current = item
        if (current != null) {
            current.metrics.filter { it.kind == MetricKind.CHECK }.forEach { metric ->
                if (resolvedValue(current, metric) != "1") setValue(current, metric, "1")
            }
        }
        when {
            section.roundTimed -> completeRoundMovement()
            section.intervalSeconds != null && section.items.isNotEmpty() -> {
                if (!itemDone) {
                    itemDone = true
                    commitCurrentValues()
                }
            }
            else -> {
                recordSplit()
                advanceLiveItem()
            }
        }
    }

    fun viewNext() {
        if (phase != LivePhase.ACTIVE) return
        if (itemIndex < section.items.lastIndex) itemIndex += 1
    }

    fun viewPrevious() {
        if (phase != LivePhase.ACTIVE) return
        if (itemIndex > 0) itemIndex -= 1
    }

    fun toggleItemDone() {
        if (phase != LivePhase.ACTIVE || !onLiveItem || resting || roundWorkDone) return
        if (section.roundTimed || section.intervalSeconds == null) return
        if (!itemDone) {
            itemDone = true
            val current = item
            if (current != null) {
                current.metrics.filter { it.kind == MetricKind.CHECK }.forEach { metric ->
                    if (resolvedValue(current, metric) != "1") setValue(current, metric, "1")
                }
            }
            commitCurrentValues()
        } else {
            itemDone = false
        }
    }

    fun skipLiveTimer() {
        if (phase != LivePhase.ACTIVE || !onLiveItem || resting || roundWorkDone) return
        when {
            section.roundTimed -> skipRoundMovement()
            section.intervalSeconds != null && section.items.isNotEmpty() -> {
                itemDone = false
                advanceWindow(commit = false)
            }
            else -> skipLiveItem()
        }
    }

    private fun skipRoundMovement() {
        val current = item ?: return
        val round = current.roundIndex ?: currentRound
        val lastOfRound = section.items.getOrNull(itemIndex + 1)?.roundIndex != round
        if (lastOfRound) {
            endRound()
        } else {
            itemIndex += 1
            liveItemIndex = itemIndex
            enterItem()
        }
    }

    private fun skipLiveItem() {
        val current = section
        when {
            current.items.isEmpty() -> finishSection(completed = false)
            itemIndex < current.items.lastIndex -> {
                itemIndex += 1
                liveItemIndex = itemIndex
                enterItem()
            }
            current.isAmrap -> {
                itemIndex = 0
                liveItemIndex = 0
                replaceResults(LiveScoring.resetRoundValues(results, current))
                enterItem()
            }
            else -> finishSection(completed = false)
        }
    }

    private fun completeRoundMovement() {
        val current = item ?: return
        recordSplit()
        val round = current.roundIndex ?: currentRound
        val lastOfRound = section.items.getOrNull(itemIndex + 1)?.roundIndex != round
        commitCurrentValues()
        if (lastOfRound) {
            itemDone = true
            roundWorkDone = true
        } else {
            itemIndex += 1
            liveItemIndex = itemIndex
            enterItem()
        }
    }

    private fun advanceLiveItem() {
        commitCurrentValues()
        val current = section
        if (current.items.isEmpty()) {
            finishSection(completed = true)
            return
        }
        if (itemIndex < current.items.lastIndex) {
            itemIndex += 1
            liveItemIndex = itemIndex
            intervalRemainingMs = (current.intervalSeconds ?: 0) * 1000L
            lastCueSecond = -1
            enterItem()
        } else if (current.isAmrap) {
            roundsDone += 1
            itemIndex = 0
            liveItemIndex = 0
            replaceResults(LiveScoring.resetRoundValues(results, current))
            intervalRemainingMs = (current.intervalSeconds ?: 0) * 1000L
            lastCueSecond = -1
            enterItem()
        } else {
            finishSection(completed = true)
        }
    }

    private fun advanceWindow(commit: Boolean = true) {
        if (commit) commitCurrentValues()
        val current = section
        itemDone = false
        if (itemIndex < current.items.lastIndex) {
            itemIndex += 1
            liveItemIndex = itemIndex
            intervalRemainingMs = (current.intervalSeconds ?: 0) * 1000L
            lastCueSecond = -1
            enterItem()
        } else {
            finishSection(completed = true)
        }
    }

    private fun endRound() {
        roundWorkDone = true
        itemDone = false
        if (currentRound >= section.roundCount - 1) {
            finishSection(completed = true)
            return
        }
        val rest = section.restSeconds ?: 0
        if (rest > 0) {
            resting = true
            restRemainingMs = rest * 1000L
            lastCueSecond = -1
        } else {
            startNextRound()
        }
    }

    private fun startNextRound() {
        currentRound += 1
        resting = false
        restRemainingMs = 0
        roundWorkDone = false
        itemDone = false
        lastSplitElapsedMs = 0
        val first = section.items.indexOfFirst { it.roundIndex == currentRound }
        if (first < 0) {
            finishSection(completed = true)
            return
        }
        itemIndex = first
        liveItemIndex = first
        intervalRemainingMs = (section.roundSeconds ?: 0) * 1000L
        lastCueSecond = -1
        enterItem()
    }

    fun skipRest() {
        if (phase != LivePhase.ACTIVE || !onLiveItem || !resting) return
        cues.go()
        startNextRound()
    }

    fun resumeLive() {
        itemIndex = liveItemIndex
        paused = false
    }

    private fun recordSplit() {
        val current = item ?: return
        val elapsed = when {
            section.roundTimed ->
                ((section.roundSeconds ?: 0) * 1000L - intervalRemainingMs).coerceAtLeast(0L)
            section.clocked -> sectionElapsedMs
            else -> return
        }
        val split = (elapsed - lastSplitElapsedMs).coerceAtLeast(0L)
        lastSplitElapsedMs = elapsed
        if (split <= 0L) return
        val metric = current.metrics.firstOrNull { it.kind == MetricKind.TIME } ?: return
        if (InteractiveResults.contains(results, current.scope, metric.key)) return
        setValue(
            current.scope,
            metric.key,
            TimeValue.format(
                (split / 60_000L).toInt(),
                ((split / 1000L) % 60L).toInt(),
            ),
        )
    }

    fun jumpTo(section: Int, item: Int) {
        val target = section.coerceIn(0, live.sections.lastIndex)
        if (target == sectionIndex) {
            itemIndex = item.coerceIn(0, this.section.items.lastIndex.coerceAtLeast(0))
            return
        }
        beginSection(target)
        if (this.section.isAmrap && LiveScoring.hasFinishedAmrap(results, this.section)) {
            replaceResults(LiveScoring.resetAmrap(results, this.section))
        }
        beginActive()
        val current = this.section
        val targetItem = item.coerceIn(0, current.items.lastIndex.coerceAtLeast(0))
        liveItemIndex = targetItem
        itemIndex = targetItem
        itemDone = false
        if (current.roundTimed) {
            currentRound = current.items.getOrNull(targetItem)?.roundIndex ?: 0
            roundWorkDone = false
            resting = false
            restRemainingMs = 0
            lastSplitElapsedMs = 0
            intervalRemainingMs = (current.roundSeconds ?: 0) * 1000L
        } else if (current.intervalSeconds != null) {
            intervalRemainingMs = (current.intervalSeconds ?: 0) * 1000L
        }
        paused = false
        phase = LivePhase.ACTIVE
        enterItem()
    }

    fun jumpToSection(index: Int) {
        val target = index.coerceIn(0, live.sections.lastIndex)
        if (target == sectionIndex &&
            (phase == LivePhase.ACTIVE || phase == LivePhase.GET_READY)
        ) {
            itemIndex = liveItemIndex
            return
        }
        beginSection(target)
    }

    fun skipCurrentSection() {
        if (section.isAmrap) {
            replaceResults(LiveScoring.resetAmrap(results, section))
        }
        if (section.items.isNotEmpty()) {
            replaceResults(LiveResults.markNotReached(results, section))
        }
        if (isLastSection) {
            phase = LivePhase.DONE
            paused = false
        } else {
            beginSection(sectionIndex + 1)
        }
    }

    fun finishCurrentSection() = finishSection(completed = false)

    /** Leaving early keeps every value and marks the sections not reached. */
    fun finalizeBeforeExit() {
        if (!started) return
        if (phase == LivePhase.ACTIVE && onLiveItem && !section.isAmrap) {
            commitCurrentValues()
        }
        val current = section
        when {
            phase == LivePhase.DONE -> Unit
            phase == LivePhase.ACTIVE && current.clocked -> {
                val next = LiveResults.finish(
                    results = results,
                    section = current,
                    lastReachedRow = reachedRow(),
                    completed = false,
                    elapsedSeconds = (sectionElapsedMs / 1000L).toInt(),
                    roundsDone = if (current.isAmrap) roundsDone else null,
                    partialIndex = if (current.isAmrap) liveItemIndex else null,
                )
                replaceResults(next)
                markUnreached(sectionIndex + 1)
            }
            phase == LivePhase.ACTIVE -> markUnreached(sectionIndex + 1)
            else -> markUnreached(sectionIndex)
        }
    }

    private fun markUnreached(fromIndex: Int) {
        var next = results
        for (index in fromIndex..live.sections.lastIndex) {
            val target = live.sections[index]
            if (target.items.isEmpty()) continue
            if (InteractiveReached.rowOf(next, index) != null) continue
            next = LiveResults.markNotReached(next, target)
        }
        if (next !== results) replaceResults(next)
    }

    private fun finishSection(completed: Boolean) {
        val current = section
        if (!current.isAmrap) commitCurrentValues()
        val measuredSeconds = (sectionElapsedMs / 1000L).toInt()
        val cap = current.timeCapSeconds
        val elapsed = if (current.clocked) {
            if (!completed && cap != null) cap else measuredSeconds
        } else {
            null
        }
        val next = LiveResults.finish(
            results = results,
            section = current,
            lastReachedRow = reachedRow(),
            completed = completed,
            elapsedSeconds = elapsed,
            roundsDone = if (current.isAmrap) roundsDone else null,
            partialIndex = if (current.isAmrap && !completed) liveItemIndex else null,
        )
        replaceResults(next)

        if (isLastSection) {
            phase = LivePhase.DONE
            paused = false
        } else {
            beginSection(sectionIndex + 1)
        }
    }

    private fun enterItem() {
        val current = item ?: return
        if (section.isAmrap) return
        val previous = reachedRow() ?: -1
        if (current.rowIndex <= previous) return
        visited[sectionIndex] = current.rowIndex
        setValue(
            InteractiveKeys.marker(sectionIndex),
            InteractiveReached.KEY,
            current.scope,
        )
    }

    private fun reachedRow(): Int? {
        visited[sectionIndex]?.let { return it }
        val rows = section.items.map { it.scope }
        if (rows.isEmpty()) return null
        val stored = InteractiveReached.indexOf(results, sectionIndex, rows)
        if (stored != null) visited[sectionIndex] = stored
        return stored
    }

    // ── Values ──────────────────────────────────────────────────────────────

    fun setValue(item: LiveItem, metric: MetricDef, value: String) =
        setValue(item.scope, metric.key, value)

    fun setValue(scope: String, metricKey: String, value: String) {
        results = InteractiveResults.set(results, scope, metricKey, value)
        resultsRevision += 1
    }

    fun replaceResults(next: Map<String, Map<String, String>>) {
        results = next
        resultsRevision += 1
    }

    fun resolvedValueForSection(metric: MetricDef): String {
        val scope = InteractiveKeys.section(sectionIndex)
        return if (InteractiveResults.contains(results, scope, metric.key)) {
            InteractiveResults.value(results, scope, metric.key)
        } else {
            ""
        }
    }

    fun setSectionValue(metric: MetricDef, value: String) =
        setValue(InteractiveKeys.section(sectionIndex), metric.key, value)

    fun resolvedValue(item: LiveItem, metric: MetricDef): String {
        if (InteractiveResults.contains(results, item.scope, metric.key)) {
            return InteractiveResults.value(results, item.scope, metric.key)
        }
        return InteractiveDefaults.forTarget(item.target, metric)
            ?: (if (metric.kind == MetricKind.WEIGHT) carriedWeight(item) else null)
            ?: ""
    }

    fun carriedWeight(item: LiveItem): String? {
        for (index in itemIndex - 1 downTo 0) {
            val previous = section.items[index]
            if (previous.name != item.name) continue
            val key = previous.metrics.firstOrNull { it.kind == MetricKind.WEIGHT }?.key
                ?: continue
            val value = InteractiveResults.value(results, previous.scope, key)
            if (value.isNotBlank()) return value
        }
        return null
    }

    fun commitCurrentValues() {
        val current = item ?: return
        for (metric in current.metrics) {
            if (metric.kind == MetricKind.CHECK) continue
            if (InteractiveResults.contains(results, current.scope, metric.key)) continue
            val value = if (metric.kind == MetricKind.WEIGHT) {
                carriedWeight(current)
            } else {
                InteractiveDefaults.forTarget(current.target, metric)
            } ?: continue
            if (value.isNotBlank()) setValue(current.scope, metric.key, value)
        }
    }

    // ── The phone link ──────────────────────────────────────────────────────

    /** Everything except the ticking clocks, for "has the stage changed?". */
    fun syncFingerprint(): String = buildString {
        append(syncSessionId).append('|')
        append(phase.name).append('|')
        append(sectionIndex).append('|')
        append(itemIndex).append('|')
        append(paused).append('|')
        append(started).append('|')
        append(resting).append('|')
        append(itemDone).append('|')
        append(roundWorkDone).append('|')
        append(currentRound).append('|')
        append(roundsDone).append('|')
        append(InteractiveResults.toJson(results))
    }

    fun toDto(date: String, typeKey: String, planUpdatedAt: Long): LiveSessionDto = LiveSessionDto(
        sessionId = syncSessionId,
        origin = DEVICE_WATCH,
        date = date,
        typeKey = typeKey,
        open = true,
        startedAt = syncStartedAt,
        updatedAt = System.currentTimeMillis(),
        planUpdatedAt = planUpdatedAt,
        phase = phase.name.lowercase(),
        sectionIndex = sectionIndex,
        itemIndex = itemIndex,
        liveItemIndex = liveItemIndex,
        paused = paused,
        started = started,
        currentRound = currentRound,
        resting = resting,
        itemDone = itemDone,
        roundWorkDone = roundWorkDone,
        leadInRemainingMs = leadInRemainingMs,
        intervalRemainingMs = intervalRemainingMs,
        sectionElapsedMs = sectionElapsedMs,
        sessionElapsedMs = sessionElapsedMs,
        restRemainingMs = restRemainingMs,
        roundsDone = roundsDone,
        lastSplitElapsedMs = lastSplitElapsedMs,
        resultsJson = InteractiveResults.toJson(results),
        baseResultsJson = InteractiveResults.toJson(baseResults),
    )

    /**
     * Take the phone's stage. Countdowns lose the record's age; elapsed clocks
     * gain it. The stage is clamped to this plan. Values are merged three-way:
     * only what the phone changed since its own base reaches this watch, so a
     * local value the phone has not seen is not wiped by an older document.
     */
    fun adopt(remote: LiveSessionDto) {
        val now = System.currentTimeMillis()
        val age = if (remote.paused) 0L else (now - remote.updatedAt).coerceAtLeast(0L)
        syncSessionId = remote.sessionId
        syncStartedAt = remote.startedAt
        phase = runCatching { LivePhase.valueOf(remote.phase.uppercase()) }
            .getOrDefault(LivePhase.ACTIVE)
        sectionIndex = remote.sectionIndex.coerceIn(0, live.sections.lastIndex)
        val items = section.items
        itemIndex = remote.itemIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))
        liveItemIndex = remote.liveItemIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))
        paused = remote.paused
        started = remote.started
        currentRound = remote.currentRound.coerceAtLeast(0)
        resting = remote.resting
        itemDone = remote.itemDone
        roundWorkDone = remote.roundWorkDone
        leadInRemainingMs = (remote.leadInRemainingMs - age).coerceAtLeast(0L)
        intervalRemainingMs = (remote.intervalRemainingMs - age).coerceAtLeast(0L)
        sectionElapsedMs = remote.sectionElapsedMs + age
        sessionElapsedMs = remote.sessionElapsedMs + age
        restRemainingMs = (remote.restRemainingMs - age).coerceAtLeast(0L)
        roundsDone = remote.roundsDone
        lastSplitElapsedMs = remote.lastSplitElapsedMs
        val remoteBase = InteractiveResults.fromJson(remote.baseResultsJson)
        val remoteCurrent = InteractiveResults.fromJson(remote.resultsJson)
        // Joining before this watch has done anything: take the phone's
        // values whole, so a stale cached plan cannot show older numbers.
        // Once engaged, only the phone's changes are applied.
        val engaged = started || resultsRevision > 0
        results = if (engaged) {
            InteractiveResults.mergeChanges(results, remoteBase, remoteCurrent)
        } else {
            remoteCurrent
        }
        baseResults = remoteCurrent
        resultsRevision += 1
        visited.clear()
        lastCueSecond = -1
    }

    /** A resumed run keeps the base it was originally written against. */
    fun restoreBase(raw: String) {
        if (raw.isNotBlank() && raw != "{}") {
            baseResults = InteractiveResults.fromJson(raw)
        }
    }
}
