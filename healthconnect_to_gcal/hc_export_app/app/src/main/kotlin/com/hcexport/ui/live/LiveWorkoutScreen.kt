package com.hcexport.ui.live

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hcexport.LiveSessionHub
import com.hcexport.shared.DEVICE_PHONE
import com.hcexport.shared.LiveSessionDto
import com.hcexport.ui.AiWorkoutState
import com.hcexport.InteractiveDefaults
import com.hcexport.InteractiveKeys
import com.hcexport.InteractiveReached
import com.hcexport.InteractiveResults
import com.hcexport.LiveRemoteAction
import com.hcexport.LiveRemoteHost
import com.hcexport.LiveRemoteHub
import com.hcexport.LiveRemoteSnapshot
import com.hcexport.LiveScoring
import com.hcexport.JournalFeed
import com.hcexport.LiveItem
import com.hcexport.LivePlan
import com.hcexport.LiveResults
import com.hcexport.LiveSection
import com.hcexport.LiveWorkout
import com.hcexport.MetricDef
import com.hcexport.MetricKind
import com.hcexport.Prefs
import com.hcexport.R
import com.hcexport.RecordLogic
import com.hcexport.TimeValue
import com.hcexport.formatLiveClock
import com.hcexport.formatLiveElapsed
import com.hcexport.metricHint
import com.hcexport.ui.ConfirmDialog
import com.hcexport.ui.DataRefresh
import com.hcexport.ui.EmptyState
import com.hcexport.ui.HcBackground
import com.hcexport.ui.HcCard
import com.hcexport.ui.IconAction
import com.hcexport.ui.LoadingLine
import com.hcexport.ui.LocalMinimalDesign
import com.hcexport.ui.NewPrCelebration
import com.hcexport.ui.PrLine
import com.hcexport.ui.PrimaryButton
import com.hcexport.ui.SecondaryButton
import com.hcexport.ui.SectionLabel
import com.hcexport.ui.SwipeCarousel
import com.hcexport.ui.Tag
import com.hcexport.ui.sanitiseMetricInput
import com.hcexport.ui.sectionAccent
import com.hcexport.ui.theme.Abyss
import com.hcexport.ui.theme.Amber
import com.hcexport.ui.theme.Card
import com.hcexport.ui.theme.CardHigh
import com.hcexport.ui.theme.Danger
import com.hcexport.ui.theme.Hairline
import com.hcexport.ui.theme.HairlineSoft
import com.hcexport.ui.theme.Mist
import com.hcexport.ui.theme.Obsidian
import com.hcexport.ui.theme.Periwinkle
import com.hcexport.ui.theme.Slate
import com.hcexport.ui.theme.Snow
import com.hcexport.ui.theme.Success
import com.hcexport.ui.theme.Teal
import com.hcexport.ui.theme.Warning
import com.hcexport.ui.rememberAiWorkoutState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

/** Outlives the screen: telling the watch the workout is over still runs
 *  while the screen is being torn down. */
private val liveSyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * The live workout, full screen: one set or movement at a time, with the
 * timings the plan prescribes and nothing else on the screen.
 *
 * Everything it records goes through the same [AiWorkoutState] the journal
 * entry uses, so the values, the "reached here" boundary and the records are
 * one record of the workout rather than a parallel one.
 */
@Composable
fun LiveWorkoutScreen(
    date: String,
    typeKey: String,
    onBack: () -> Unit,
    /** Opened from a "workout started on the watch" notification or link. */
    join: Boolean = false,
    /** Opened from the Start live workout action: choose where to begin. */
    setup: Boolean = false,
) {
    val ai = rememberAiWorkoutState(date = date, typeKey = typeKey)
    var started by rememberSaveable { mutableStateOf(false) }
    var startKey by rememberSaveable { mutableStateOf("") }
    val chosenStart = remember(startKey) { decodeLiveStart(startKey) }

    HcBackground(accent = Teal) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            when {
                ai.loading -> LoadingLine("Reading the plan…", Teal)
                ai.plan == null -> NoLivePlan(onBack)
                setup && !join && !started -> LiveStartSetup(
                    ai = ai,
                    onBack = onBack,
                    onStart = { start ->
                        startKey = start?.encode() ?: ""
                        started = true
                    },
                )
                else -> LiveWorkoutBody(ai, onBack, join, chosenStart)
            }
        }
    }
}

@Composable
private fun NoLivePlan(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
            IconAction(
                icon = {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Snow)
                },
                onClick = onBack,
                border = false,
            )
        }
        EmptyState(
            iconRes = R.drawable.ic_ai,
            title = "No live plan",
            message = "Build its AI plan first — the live session runs that plan's sets, " +
                "one at a time.",
            accent = Teal,
            actionText = "Go back",
            onAction = onBack,
        )
    }
}

// ── Where to start ──────────────────────────────────────────────────────────

/**
 * The screen behind "Start live workout": start at the beginning, at a whole
 * section, or resume at a round/set with the clocks set. It never starts
 * anything by itself; [onStart] carries the chosen point (null = the normal
 * first-section start).
 */
@Composable
private fun LiveStartSetup(
    ai: AiWorkoutState,
    onBack: () -> Unit,
    onStart: (LiveStart?) -> Unit,
) {
    val context = LocalContext.current
    val plan = ai.plan ?: return
    val defaultInterval = remember { Prefs.getLiveSetIntervalSeconds(context) }
    val live = remember(plan, defaultInterval) { LiveWorkout.build(plan, defaultInterval) }
    val choices = remember(live) { live.sections.filter { it.items.isNotEmpty() } }
    var selected by remember { mutableIntStateOf(0) }
    val section = choices.getOrNull(selected) ?: choices.firstOrNull()
    if (section == null) {
        NoLivePlan(onBack)
        return
    }
    var roundsDone by remember(selected) { mutableIntStateOf(0) }
    var movement by remember(selected) { mutableIntStateOf(0) }
    var timeText by remember(selected) { mutableStateOf(defaultStartTime(section)) }
    var showTime by remember { mutableStateOf(false) }

    val roundItems = remember(section, roundsDone) {
        when {
            section.isAmrap -> section.items
            section.roundCount > 0 ->
                section.items.filter { it.roundIndex == roundsDone }
                    .ifEmpty { section.items }
            else -> section.items
        }
    }
    LaunchedEffect(roundItems) {
        if (movement > roundItems.lastIndex) movement = 0
    }
    val maxRounds = if (section.isAmrap) 99 else (section.roundCount - 1).coerceAtLeast(0)
    val hasTime = section.roundTimed || section.clocked

    fun sectionFirstItem(): Int = if (section.roundCount > 0 && !section.isAmrap) {
        section.items.indexOfFirst { it.roundIndex == 0 }.coerceAtLeast(0)
    } else {
        0
    }

    fun startHere(): LiveStart {
        val chosen = roundItems.getOrNull(movement) ?: roundItems.firstOrNull()
        val itemIndex = if (chosen != null && !section.isAmrap) {
            section.items.indexOf(chosen).coerceAtLeast(0)
        } else {
            movement.coerceAtLeast(0)
        }
        val seconds = startClockSeconds(timeText)
        val cap = section.timeCapSeconds
        val elapsedMs = when {
            section.roundTimed -> 0L
            section.clocked && cap != null -> (cap - seconds).coerceAtLeast(0) * 1000L
            section.clocked -> seconds.coerceAtLeast(0) * 1000L
            else -> 0L
        }
        return LiveStart(
            sectionIndex = live.sections.indexOf(section).coerceAtLeast(0),
            itemIndex = itemIndex,
            roundsDone = if (section.isAmrap) roundsDone else 0,
            currentRound = if (section.roundCount > 0 && !section.isAmrap) roundsDone else 0,
            elapsedMs = elapsedMs,
            intervalRemainingMs = if (section.roundTimed) {
                seconds.coerceAtLeast(0) * 1000L
            } else {
                null
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconAction(
                icon = {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Snow)
                },
                onClick = onBack,
                border = false,
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "Start live workout",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Snow,
                )
                Text(
                    live.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = Mist,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Start from scratch", onClick = { onStart(null) }, accent = Teal)
        Spacer(Modifier.height(8.dp))
        if (!LocalMinimalDesign.current) {
            Text(
                "Or begin at a section, or resume from a round and set you already reached.",
                style = MaterialTheme.typography.bodySmall,
                color = Slate,
            )
            Spacer(Modifier.height(16.dp))
        }

        choices.forEachIndexed { index, option ->
            val isSelected = index == selected
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) Teal.copy(alpha = 0.12f) else CardHigh)
                    .border(
                        1.dp,
                        if (isSelected) Teal.copy(alpha = 0.55f) else Hairline,
                        RoundedCornerShape(16.dp),
                    )
                    .clickable {
                        selected = index
                        roundsDone = 0
                        movement = 0
                        timeText = defaultStartTime(option)
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        option.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = Snow,
                    )
                    Tag(sectionSummary(option), Teal)
                }
                if (option.notes.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        option.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate,
                        maxLines = 2,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        HcCard(contentPadding = PaddingValues(16.dp)) {
            SectionLabel(section.title, color = Teal)
            if (section.roundCount > 0) {
                Spacer(Modifier.height(12.dp))
                Text(
                    if (section.isAmrap) "Rounds already done" else "Rounds already done",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StartStepButton("−") {
                        roundsDone = (roundsDone - 1).coerceAtLeast(0)
                    }
                    Text(
                        "$roundsDone",
                        modifier = Modifier.width(42.dp),
                        style = MaterialTheme.typography.titleLarge,
                        color = Snow,
                        textAlign = TextAlign.Center,
                    )
                    StartStepButton("+") {
                        roundsDone = (roundsDone + 1).coerceAtMost(maxRounds)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (section.isAmrap) {
                            "Full rounds"
                        } else {
                            "of ${section.roundCount}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                if (section.roundCount > 0) "Start at" else "Start at set",
                style = MaterialTheme.typography.bodySmall,
                color = Slate,
            )
            Spacer(Modifier.height(6.dp))
            roundItems.forEachIndexed { index, item ->
                val isSelected = index == movement
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Teal.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { movement = index }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .border(
                                2.dp,
                                if (isSelected) Teal else Hairline,
                                CircleShape,
                            )
                            .background(if (isSelected) Teal else Color.Transparent)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        startItemLabel(item),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) Snow else Mist,
                    )
                }
            }
            if (hasTime) {
                Spacer(Modifier.height(12.dp))
                Text(
                    when {
                        section.roundTimed -> "Time left in the round"
                        section.clocked && section.timeCapSeconds != null -> "Time left"
                        else -> "Time already done"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardHigh)
                        .border(1.dp, Hairline, RoundedCornerShape(12.dp))
                        .clickable { showTime = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        timeText,
                        style = MaterialTheme.typography.titleMedium,
                        color = Snow,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "PICK",
                        style = MaterialTheme.typography.labelSmall,
                        color = Teal,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            SecondaryButton(
                text = "Start this section",
                onClick = {
                    onStart(
                        LiveStart(
                            sectionIndex = live.sections.indexOf(section),
                            itemIndex = sectionFirstItem(),
                        ),
                    )
                },
                accent = Teal,
            )
            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                "Start here",
                onClick = { onStart(startHere()) },
                accent = Teal,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (!LocalMinimalDesign.current) {
            Text(
                "Existing values are kept; the session overwrites the sets it runs.",
                style = MaterialTheme.typography.labelSmall,
                color = Slate,
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showTime) {
        com.hcexport.ui.TimeSpinnerDialog(
            title = "Time",
            value = timeText,
            accent = Teal,
            onConfirm = {
                timeText = it
                showTime = false
            },
            onClear = {
                timeText = defaultStartTime(section)
                showTime = false
            },
            onDismiss = { showTime = false },
        )
    }
}

@Composable
private fun StartStepButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Teal.copy(alpha = 0.12f))
            .border(1.dp, Teal.copy(alpha = 0.3f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleLarge,
            color = Teal,
        )
    }
}

private fun startItemLabel(item: LiveItem): String {
    val target = item.target.trim()
    return if (target.isBlank() || item.name.contains(target, ignoreCase = true)) {
        item.name
    } else {
        "${item.name} · $target"
    }
}

private fun sectionSummary(section: LiveSection): String = when {
    section.isAmrap -> "AMRAP"
    section.roundTimed -> "${section.roundCount} × ${formatLiveClock((section.roundSeconds ?: 0) * 1000L)}"
    section.roundCount > 0 -> "${section.roundCount} rounds"
    else -> "${section.items.size} sets"
}

private fun defaultStartTime(section: LiveSection): String {
    val cap = section.timeCapSeconds
    return when {
        section.roundTimed -> formatLiveClock((section.roundSeconds ?: 0) * 1000L)
        section.clocked && cap != null -> formatLiveClock(cap * 1000L)
        else -> "0:00"
    }
}

private fun startClockSeconds(text: String): Int {
    val parts = text.trim().split(":")
    return if (parts.size == 2) {
        ((parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0))
            .coerceAtLeast(0)
    } else {
        (text.trim().toIntOrNull() ?: 0).coerceAtLeast(0)
    }
}

// ── Session state ───────────────────────────────────────────────────────────

private enum class LivePhase { OVERVIEW, GET_READY, ACTIVE, DONE }

/** What the section that just ended earned: a For Time clock or an AMRAP score. */
private data class LiveResultNote(val label: String, val value: String)

/**
 * Where a live session begins when it is not the normal first-section start.
 * A null start on [LiveSessionState] means the usual get-ready at section 1;
 * anything here begins active at that point, with the clocks set.
 */
private data class LiveStart(
    val sectionIndex: Int,
    val itemIndex: Int,
    val roundsDone: Int = 0,
    val currentRound: Int = 0,
    val elapsedMs: Long = 0L,
    val intervalRemainingMs: Long? = null,
)

private fun LiveStart.encode(): String = listOf(
    sectionIndex,
    itemIndex,
    roundsDone,
    currentRound,
    elapsedMs,
    intervalRemainingMs ?: -1L,
).joinToString("|")

private fun decodeLiveStart(raw: String): LiveStart? {
    if (raw.isBlank()) return null
    val parts = raw.split('|')
    if (parts.size < 6) return null
    return runCatching {
        LiveStart(
            sectionIndex = parts[0].toInt(),
            itemIndex = parts[1].toInt(),
            roundsDone = parts[2].toInt(),
            currentRound = parts[3].toInt(),
            elapsedMs = parts[4].toLong(),
            intervalRemainingMs = parts[5].toLong().takeIf { it >= 0L },
        )
    }.getOrNull()
}

/**
 * The running session: where in the plan we are, the two clocks the screens
 * use, and the writes that keep the journal in step.
 *
 * Countdowns are accumulated from the ticker's real elapsed time rather than
 * read from a clock inside a composable, so pausing freezes them and a
 * recomposition never resets them.
 */
@Stable
private class LiveSessionState(
    val live: LivePlan,
    private val ai: AiWorkoutState,
    private val cues: LiveCues,
    private val leadInSeconds: Int,
    private val start: LiveStart? = null,
) {
    var sectionIndex by mutableIntStateOf(0)
    var itemIndex by mutableIntStateOf(0)
    var phase by mutableStateOf(LivePhase.OVERVIEW)
    var paused by mutableStateOf(false)
    var showMap by mutableStateOf(false)
    var showEndConfirm by mutableStateOf(false)

    var leadInRemainingMs by mutableLongStateOf(0L)
    var intervalRemainingMs by mutableLongStateOf(0L)
    var sectionElapsedMs by mutableLongStateOf(0L)
    var sessionElapsedMs by mutableLongStateOf(0L)
    var roundsDone by mutableIntStateOf(0)

    /** The item the workout is actually on, while a review looks back. */
    var liveItemIndex by mutableIntStateOf(0)
    var restRemainingMs by mutableLongStateOf(0L)
    /** Round-timed work: which round is running, 0-based. */
    var currentRound by mutableIntStateOf(0)
    /** An interval set marked done; its window keeps running as rest. */
    var itemDone by mutableStateOf(false)
    /** Round-timed work: the round's movements are all done. */
    var roundWorkDone by mutableStateOf(false)
    /** Round-timed work: the rest between rounds is counting. */
    var resting by mutableStateOf(false)
    /** Round time at the previous movement's Done, for the next split. */
    var lastSplitElapsedMs by mutableLongStateOf(0L)

    /** The result of the section that just ended, shown until the next begins. */
    var lastResult by mutableStateOf<LiveResultNote?>(null)

    /** The furthest row entered per section, the live "reached here" boundary. */
    private val visited = mutableMapOf<Int, Int>()
    private var lastCueSecond = -1

    /** False until a section actually begins; an untouched plan is never written. */
    var started = false

    // ── The watch link ──────────────────────────────────────────────────────
    /** The shared session identity; adopting a watch session adopts its id. */
    var syncSessionId by mutableStateOf(UUID.randomUUID().toString())
    var syncStartedAt by mutableStateOf(System.currentTimeMillis())
    /** The values this session started from; the watch applies only changes. */
    var syncBaseResults: Map<String, Map<String, String>> = ai.results
        private set
    /** The stage last published (or adopted); identical stages never re-publish. */
    var lastPublishedFingerprint: String? = null

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
        append(InteractiveResults.toJson(ai.results))
    }

    fun toSyncDto(date: String, typeKey: String): LiveSessionDto = LiveSessionDto(
        sessionId = syncSessionId,
        origin = DEVICE_PHONE,
        date = date,
        typeKey = typeKey,
        open = true,
        startedAt = syncStartedAt,
        updatedAt = System.currentTimeMillis(),
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
        resultsJson = InteractiveResults.toJson(ai.results),
        baseResultsJson = InteractiveResults.toJson(syncBaseResults),
    )

    /**
     * Take another device's stage. Adopting never republishes by itself: the
     * caller records the new fingerprint, so two devices cannot echo a stage
     * back and forth.
     *
     * A record written a while ago carries clocks that have moved on: elapsed
     * clocks gain the age, countdowns lose it. The stage is clamped to this
     * plan, so a stale plan on the other device cannot point past the end.
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
        // Three-way, like the watch's side: apply what the other device
        // actually changed, never its whole document over newer local values.
        val remoteBase = InteractiveResults.fromJson(remote.baseResultsJson)
        val remoteCurrent = InteractiveResults.fromJson(remote.resultsJson)
        ai.replaceResults(
            InteractiveResults.mergeChanges(ai.results, remoteBase, remoteCurrent)
        )
        syncBaseResults = remoteCurrent
        visited.clear()
        lastCueSecond = -1
        showMap = false
    }

    init {
        if (start != null) {
            // A chosen point (a section, a round, a set) starts active: the
            // athlete has already done the work before it.
            sectionIndex = start.sectionIndex.coerceIn(0, live.sections.lastIndex)
            val chosen = live.sections[sectionIndex]
            val chosenInterval = chosen.intervalSeconds
            roundsDone = start.roundsDone.coerceAtLeast(0)
            currentRound = start.currentRound.coerceAtLeast(0)
            sectionElapsedMs = start.elapsedMs.coerceAtLeast(0L)
            intervalRemainingMs = start.intervalRemainingMs ?: when {
                chosen.roundTimed -> (chosen.roundSeconds ?: 0) * 1000L
                chosenInterval != null -> chosenInterval * 1000L
                else -> 0L
            }
            started = true
            paused = false
            phase = LivePhase.ACTIVE
            itemIndex = start.itemIndex.coerceIn(
                0, chosen.items.lastIndex.coerceAtLeast(0),
            )
            liveItemIndex = itemIndex
            lastCueSecond = -1
            enterItem()
        } else {
            // Pressing "Start live workout" is the start: the first section
            // opens on its get-ready countdown, not on another start button.
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
    }

    val section: LiveSection
        get() = live.sections.getOrElse(sectionIndex) { live.sections.first() }

    val item: LiveItem?
        get() = section.items.getOrNull(itemIndex)

    /** Looking at an earlier item: the whole session is frozen until resumed. */
    val reviewing: Boolean
        get() = itemIndex < liveItemIndex

    /** Looking ahead at an item that has not started; the live clock keeps running. */
    val previewing: Boolean
        get() = itemIndex > liveItemIndex

    /** The view is on the item the workout is actually on. */
    val onLiveItem: Boolean
        get() = itemIndex == liveItemIndex

    val isLastSection: Boolean
        get() = sectionIndex >= live.sections.lastIndex

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
        // Starting the same AMRAP again must not inherit the previous finish's
        // rounds or its unfinished-round boundary.
        if (current.isAmrap && LiveScoring.hasFinishedAmrap(ai.results, current)) {
            ai.replaceResults(LiveScoring.resetAmrap(ai.results, current))
        }
        if (current.items.isEmpty() && current.metrics.isEmpty()) {
            // A notes-only section is a page to read, then move on.
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
        lastResult = null
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
        val statedInterval = section.intervalSeconds
        intervalRemainingMs = when {
            section.roundTimed -> (section.roundSeconds ?: 0) * 1000L
            statedInterval != null -> statedInterval * 1000L
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

    /** The CTA: mark the work done. Interval sets rest out their window. */
    fun completeAndAdvance() {
        if (phase != LivePhase.ACTIVE || !onLiveItem || resting || roundWorkDone) return
        val current = item
        if (current != null) {
            current.metrics.filter { it.kind == MetricKind.CHECK }.forEach { metric ->
                if (resolvedValue(current, metric) != "1") {
                    setValue(current, metric, "1")
                }
            }
        }
        when {
            section.roundTimed -> completeRoundMovement()

            // "Every 2:00" work: the set is done, but the next one starts on
            // the interval boundary, not now. The window keeps counting.
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

    /**
     * Swipe left / Next: view the next set, even before it starts. Navigation
     * never completes anything; only the live item carries a running clock.
     */
    fun viewNext() {
        if (phase != LivePhase.ACTIVE) return
        if (itemIndex < section.items.lastIndex) itemIndex += 1
    }

    /** Swipe right / Previous: view the previous set. */
    fun viewPrevious() {
        if (phase != LivePhase.ACTIVE) return
        if (itemIndex > 0) itemIndex -= 1
    }

    /**
     * Mark/unmark the live timed set. The set's window keeps running either
     * way; the completed banner is just the athlete's confirmation.
     */
    fun toggleItemDone() {
        if (phase != LivePhase.ACTIVE || !onLiveItem || resting || roundWorkDone) return
        if (section.roundTimed || section.intervalSeconds == null) return
        if (!itemDone) {
            itemDone = true
            val current = item
            if (current != null) {
                current.metrics.filter { it.kind == MetricKind.CHECK }.forEach { metric ->
                    if (resolvedValue(current, metric) != "1") {
                        setValue(current, metric, "1")
                    }
                }
            }
            commitCurrentValues()
        } else {
            itemDone = false
        }
    }

    /**
     * End the live step's timer now. A timed set moves to the next set; a
     * round-timed movement moves on without being marked done, and skipping
     * the round's last movement goes straight to its rest.
     */
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
        when {
            lastOfRound -> endRound()
            else -> {
                itemIndex += 1
                liveItemIndex = itemIndex
                enterItem()
            }
        }
    }

    /** Move on without recording a split or the prescription. */
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
                ai.replaceResults(LiveScoring.resetRoundValues(ai.results, current))
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
            // The round's work is done, but its clock is the round's, so it
            // keeps running; the completion screen shows until it ends.
            itemDone = true
            roundWorkDone = true
        } else {
            itemIndex += 1
            liveItemIndex = itemIndex
            enterItem()
        }
    }

    /** A movement or set is done and the section advances immediately. */
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
            // The reps and checks just entered belong to the round that ended;
            // the same scopes are about to be walked again.
            ai.replaceResults(LiveScoring.resetRoundValues(ai.results, current))
            intervalRemainingMs = (current.intervalSeconds ?: 0) * 1000L
            lastCueSecond = -1
            enterItem()
        } else {
            finishSection(completed = true)
        }
    }

    /** An interval window ended: the next set starts on the boundary. */
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

    /** The rest between rounds can be cut short; the next round starts now. */
    fun skipRest() {
        if (phase != LivePhase.ACTIVE || !onLiveItem || !resting) return
        cues.go()
        startNextRound()
    }

    /** Return the view to the live item; its clock resumes where it was. */
    fun resumeLive() {
        itemIndex = liveItemIndex
        paused = false
    }

    /**
     * A metcon movement with a time metric — the 400m run — records the time
     * it took when it is marked done, measured from the previous split. The
     * round clock keeps running; the split is just a lap.
     */
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
        if (InteractiveResults.contains(ai.results, current.scope, metric.key)) return
        ai.setValue(
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
            // Inside the running section the map only moves the view; the live
            // item and its clock are untouched.
            itemIndex = item.coerceIn(0, this.section.items.lastIndex.coerceAtLeast(0))
            showMap = false
            return
        }
        // Moving to another section is a deliberate start there.
        beginSection(target)
        if (this.section.isAmrap && LiveScoring.hasFinishedAmrap(ai.results, this.section)) {
            ai.replaceResults(LiveScoring.resetAmrap(ai.results, this.section))
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
        showMap = false
        phase = LivePhase.ACTIVE
        enterItem()
    }

    fun jumpToSection(index: Int) {
        val target = index.coerceIn(0, live.sections.lastIndex)
        if (target == sectionIndex &&
            (phase == LivePhase.ACTIVE || phase == LivePhase.GET_READY)
        ) {
            // Tapping the running section's header must not throw it away.
            itemIndex = liveItemIndex
            showMap = false
            return
        }
        beginSection(target)
        showMap = false
    }

    /**
     * Leave a section without recording anything in it — the gym has no
     * rower, the coach changed the plan. The section is marked as never
     * reached, so its sets stay out of the journal's totals.
     */
    fun skipCurrentSection() {
        lastResult = null
        if (section.isAmrap) {
            ai.replaceResults(LiveScoring.resetAmrap(ai.results, section))
        }
        if (section.items.isNotEmpty()) {
            ai.replaceResults(LiveResults.markNotReached(ai.results, section))
        }
        if (isLastSection) {
            phase = LivePhase.DONE
            paused = false
        } else {
            beginSection(sectionIndex + 1)
        }
    }

    fun finishCurrentSection() = finishSection(completed = false)

    /**
     * Leaving before a timed section ends still records what it earned — the
     * AMRAP rounds so far, the time on the clock — and keeps the reached
     * boundary. Sections the session never got to are marked never reached,
     * so the journal never counts work that did not happen.
     */
    fun finalizeBeforeExit() {
        if (!started) return
        // An AMRAP's current movement is the one the cap interrupted: writing
        // its prescription would invent reps the athlete never did.
        if (phase == LivePhase.ACTIVE && onLiveItem && !section.isAmrap) {
            commitCurrentValues()
        }
        val current = section
        when {
            phase == LivePhase.DONE -> Unit
            phase == LivePhase.ACTIVE && current.clocked -> {
                val partialIndex = if (current.isAmrap) liveItemIndex else null
                val next = LiveResults.finish(
                    results = ai.results,
                    section = current,
                    lastReachedRow = reachedRow(),
                    completed = false,
                    elapsedSeconds = (sectionElapsedMs / 1000L).toInt(),
                    roundsDone = if (current.isAmrap) roundsDone else null,
                    partialIndex = partialIndex,
                )
                ai.replaceResults(next)
                lastResult = resultNoteFor(current, completed = false, partialIndex)
                markUnreached(sectionIndex + 1)
            }
            phase == LivePhase.ACTIVE -> markUnreached(sectionIndex + 1)
            // Get-ready or the next-session preview: this section never began.
            else -> markUnreached(sectionIndex)
        }
    }

    /** Mark every section from [fromIndex] on that has no boundary yet. */
    private fun markUnreached(fromIndex: Int) {
        var next = ai.results
        for (index in fromIndex..live.sections.lastIndex) {
            val target = live.sections[index]
            if (target.items.isEmpty()) continue
            if (InteractiveReached.rowOf(next, index) != null) continue
            next = LiveResults.markNotReached(next, target)
        }
        if (next !== ai.results) ai.replaceResults(next)
    }

    private fun finishSection(completed: Boolean) {
        val current = section
        // The in-progress AMRAP movement is not committed: its prescription is
        // not what the athlete did when the cap stopped them.
        if (!current.isAmrap) commitCurrentValues()
        val measuredSeconds = (sectionElapsedMs / 1000L).toInt()
        val cap = current.timeCapSeconds
        val elapsed = if (current.clocked) {
            if (!completed && cap != null) cap else measuredSeconds
        } else {
            null
        }
        val rounds = if (current.isAmrap) roundsDone else null
        val partialIndex = if (current.isAmrap && !completed) liveItemIndex else null
        val next = LiveResults.finish(
            results = ai.results,
            section = current,
            lastReachedRow = reachedRow(),
            completed = completed,
            elapsedSeconds = elapsed,
            roundsDone = rounds,
            partialIndex = partialIndex,
        )
        ai.replaceResults(next)
        // A notes-only section (the workout's stimulus page) has no result of
        // its own; it must not wipe the score the previous section just earned.
        lastResult = resultNoteFor(current, completed, partialIndex) ?: lastResult

        if (isLastSection) {
            phase = LivePhase.DONE
            paused = false
        } else {
            beginSection(sectionIndex + 1)
        }
    }

    /**
     * What the notification says the section earned: the AMRAP score, or a
     * For Time piece's clock. Only the capped/clocked metcons have a result
     * of their own; strength work is scored set by set.
     */
    private fun resultNoteFor(
        finished: LiveSection,
        completed: Boolean,
        partialIndex: Int?,
    ): LiveResultNote? {
        if (finished.isAmrap) {
            val score = LiveScoring.compute(finished, roundsDone, partialIndex, ai.results)
            return LiveResultNote(finished.title, score.detail)
        }
        val measured = (sectionElapsedMs / 1000L).toInt()
        val cap = finished.timeCapSeconds
        val elapsed = when {
            !finished.clocked -> null
            !completed && cap != null -> cap
            else -> measured
        } ?: return null
        return LiveResultNote(finished.title, formatLiveElapsed(elapsed * 1000L))
    }

    private fun enterItem() {
        val current = item ?: return
        if (section.isAmrap) return
        val previous = reachedRow() ?: -1
        if (current.rowIndex <= previous) return
        visited[sectionIndex] = current.rowIndex
        ai.setValue(
            InteractiveKeys.marker(sectionIndex),
            InteractiveReached.KEY,
            current.scope,
        )
    }

    /**
     * The furthest row reached in the current section. The in-memory map is
     * the fast path; after a restore it is rebuilt from the marker already
     * written into the results.
     */
    private fun reachedRow(): Int? {
        visited[sectionIndex]?.let { return it }
        val rows = section.items.map { it.scope }
        if (rows.isEmpty()) return null
        val stored = InteractiveReached.indexOf(ai.results, sectionIndex, rows)
        if (stored != null) visited[sectionIndex] = stored
        return stored
    }

    // ── Values ──────────────────────────────────────────────────────────────

    fun setValue(item: LiveItem, metric: MetricDef, value: String) {
        ai.setValue(item.scope, metric.key, value)
    }

    /** A metric on the section itself, for work scored as a whole. */
    fun resolvedValueForSection(metric: MetricDef): String {
        val scope = InteractiveKeys.section(sectionIndex)
        return if (InteractiveResults.contains(ai.results, scope, metric.key)) {
            InteractiveResults.value(ai.results, scope, metric.key)
        } else {
            ""
        }
    }

    fun setSectionValue(metric: MetricDef, value: String) {
        ai.setValue(InteractiveKeys.section(sectionIndex), metric.key, value)
    }

    /**
     * What the field shows: the stored value, else the prescription, else the
     * weight carried from the previous set of the same movement. Carrying is a
     * display convenience; it is written only when the set is advanced past.
     */
    fun resolvedValue(item: LiveItem, metric: MetricDef): String {
        if (InteractiveResults.contains(ai.results, item.scope, metric.key)) {
            return InteractiveResults.value(ai.results, item.scope, metric.key)
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
            val value = InteractiveResults.value(ai.results, previous.scope, key)
            if (value.isNotBlank()) return value
        }
        return null
    }

    /**
     * Persist what the screen showed for this set before leaving it: the
     * prescription's reps/time, and the weight carried over from an earlier
     * set. The athlete saw those numbers and advanced; the record should say
     * what they did, not stay silent.
     */
    fun commitCurrentValues() {
        val current = item ?: return
        for (metric in current.metrics) {
            if (metric.kind == MetricKind.CHECK) continue
            if (InteractiveResults.contains(ai.results, current.scope, metric.key)) continue
            val value = if (metric.kind == MetricKind.WEIGHT) {
                carriedWeight(current)
            } else {
                InteractiveDefaults.forTarget(current.target, metric)
            } ?: continue
            if (value.isNotBlank()) {
                ai.setValue(current.scope, metric.key, value)
            }
        }
    }

    // ── The band's remote ────────────────────────────────────────────────────

    /** A timed set whose Done marks it; its window keeps running as rest. */
    private val intervalSet: Boolean
        get() = !section.roundTimed && section.intervalSeconds != null

    private val hasRounds: Boolean
        get() = section.isAmrap || section.roundCount > 0

    private fun liveItemNow(): LiveItem? = section.items.getOrNull(liveItemIndex)

    /**
     * The controls the live step offers, in the screen's order. This list is
     * the single source for both the notification buttons and the band's
     * transport controls — a new action here reaches both.
     *
     * Round work has three buttons; a plain set has two. The colours are
     * carried by the labels because Android notification actions are
     * monochrome.
     */
    fun remoteActions(): List<LiveRemoteAction> = when (phase) {
        LivePhase.OVERVIEW -> listOf(LiveRemoteAction(LiveRemoteHub.ACTION_START, "Start"))
        LivePhase.GET_READY -> listOf(LiveRemoteAction(LiveRemoteHub.ACTION_START, "Start now"))
        LivePhase.DONE -> emptyList()
        LivePhase.ACTIVE -> when {
            // The rest screen has one control; the work controls are gone.
            resting -> listOf(LiveRemoteAction(LiveRemoteHub.ACTION_SKIP_REST, "Skip rest"))
            roundWorkDone -> emptyList()
            hasRounds -> listOf(
                LiveRemoteAction(LiveRemoteHub.ACTION_ROUND_DONE, "🟢 Round done"),
                LiveRemoteAction(LiveRemoteHub.ACTION_SET_DONE, "🟠 Set done"),
                LiveRemoteAction(LiveRemoteHub.ACTION_SKIP, "🔴 Skip"),
            )
            else -> listOf(
                LiveRemoteAction(LiveRemoteHub.ACTION_SET_DONE, "🟠 Set done"),
                LiveRemoteAction(LiveRemoteHub.ACTION_SKIP, "🔴 Skip"),
            )
        }
    }

    /**
     * Run a remote action. The band is always looking at the live step, so an
     * action returns the phone to it first rather than acting on a set the
     * phone merely swiped to.
     */
    fun performRemote(actionId: String) {
        when (actionId) {
            LiveRemoteHub.ACTION_START -> when (phase) {
                LivePhase.OVERVIEW -> startCurrentSection()
                LivePhase.GET_READY -> skipLeadIn()
                else -> Unit
            }

            LiveRemoteHub.ACTION_ROUND_DONE -> completeRound()

            LiveRemoteHub.ACTION_SET_DONE -> completeSet()

            LiveRemoteHub.ACTION_SKIP -> {
                if (phase != LivePhase.ACTIVE) return
                paused = false
                if (!onLiveItem) resumeLive()
                skipLiveTimer()
            }

            LiveRemoteHub.ACTION_SKIP_REST -> {
                if (phase != LivePhase.ACTIVE) return
                paused = false
                if (!onLiveItem) resumeLive()
                skipRest()
            }
        }
    }

    /** A set is done; an interval set is marked once and its window runs on. */
    private fun completeSet() {
        if (phase != LivePhase.ACTIVE || resting || roundWorkDone) return
        paused = false
        if (!onLiveItem) resumeLive()
        if (intervalSet) {
            if (!itemDone) markItemDone()
        } else {
            completeAndAdvance()
        }
    }

    /** Mark the live interval set done, without the screen's undo toggle. */
    private fun markItemDone() {
        itemDone = true
        val current = item ?: return
        current.metrics.filter { it.kind == MetricKind.CHECK }.forEach { metric ->
            if (resolvedValue(current, metric) != "1") {
                setValue(current, metric, "1")
            }
        }
        commitCurrentValues()
    }

    /**
     * Mark the whole round done, from any movement in it. An AMRAP counts the
     * round and starts the next; a round-timed piece ends its work and lets
     * the round clock run out (then rest); a fixed-round piece moves to the
     * next round, or finishes the section.
     */
    private fun completeRound() {
        if (phase != LivePhase.ACTIVE || resting || roundWorkDone) return
        if (!hasRounds) return
        paused = false
        if (!onLiveItem) resumeLive()
        val current = section
        when {
            current.isAmrap -> {
                roundsDone += 1
                itemIndex = 0
                liveItemIndex = 0
                itemDone = false
                ai.replaceResults(LiveScoring.resetRoundValues(ai.results, current))
                intervalRemainingMs = (current.intervalSeconds ?: 0) * 1000L
                lastCueSecond = -1
                enterItem()
            }

            current.roundTimed -> {
                recordSplit()
                commitCurrentValues()
                itemDone = true
                roundWorkDone = true
            }

            else -> {
                val round = current.items.getOrNull(liveItemIndex)?.roundIndex ?: currentRound
                recordSplit()
                commitCurrentValues()
                val nextFirst = current.items.indexOfFirst {
                    (it.roundIndex ?: 0) > round
                }
                if (nextFirst >= 0) {
                    itemIndex = nextFirst
                    liveItemIndex = nextFirst
                    intervalRemainingMs = (current.intervalSeconds ?: 0) * 1000L
                    lastCueSecond = -1
                    enterItem()
                } else {
                    finishSection(completed = true)
                }
            }
        }
    }

    private fun remoteTitle(): String {
        if (phase == LivePhase.DONE) return "Workout complete"
        // Between sections the result just earned is the headline: a For Time
        // clock or an AMRAP score must be readable on the band.
        if (phase == LivePhase.OVERVIEW) {
            return lastResult?.let { "${it.label} · ${it.value}" } ?: section.title
        }
        if (resting) return "Rest"
        if (roundWorkDone) return "Round done"
        val item = liveItemNow() ?: return section.title
        return when {
            section.roundTimed -> "Round ${currentRound + 1} of ${section.roundCount}"
            section.isAmrap -> "Round ${roundsDone + 1}"
            section.roundCount > 0 ->
                "Round ${(item.roundIndex ?: currentRound) + 1} of ${section.roundCount}"
            item.position.isNotBlank() -> item.position
            else -> section.title
        }
    }

    private fun remoteExercise(): String {
        if (phase == LivePhase.DONE) return remoteResult().orEmpty()
        if (phase == LivePhase.OVERVIEW) {
            return section.items.take(2).joinToString(" · ") { it.name }
                .ifBlank { section.notes }
        }
        if (resting) {
            return if (currentRound < section.roundCount - 1) {
                "Next: Round ${currentRound + 2} of ${section.roundCount}"
            } else {
                "Last round done"
            }
        }
        if (roundWorkDone) {
            return section.restSeconds?.let { "${formatLiveClock(it * 1000L)} rest" }.orEmpty()
        }
        val item = liveItemNow() ?: return section.title
        // A set is named by the lift and its prescription; a round movement
        // reads as the work itself ("15 Push-Ups", "200m Run").
        return if (section.kind == "strength" || section.kind == "accessory") {
            if (item.target.isBlank()) item.name else "${item.name} · ${item.target}"
        } else {
            displayExercise(item)
        }
    }

    /** "15 reps" + "Push-Ups" becomes "15 Push-Ups"; "200m" + "Run" stays "200m Run". */
    private fun displayExercise(item: LiveItem): String {
        val target = item.target.trim()
        val name = item.name.trim()
        if (target.isBlank()) return name
        val simple = Regex(
            """^\d{1,3}(?:\s*(?:reps?|m|km|cal|s|sec|secs|min|mins|minutes?))?$""",
            RegexOption.IGNORE_CASE,
        )
        if (simple.matches(target)) {
            val cleaned = target.replace(Regex("""\s*reps?$""", RegexOption.IGNORE_CASE), "")
                .trim()
            return if (cleaned.isBlank()) name else "$cleaned $name"
        }
        return if (name.contains(target, ignoreCase = true)) name else "$name · $target"
    }

    /**
     * A running timer is drawn by the notification's own chronometer, which
     * ticks without the app re-posting anything. Paused time is the exception:
     * it is written into the text, because a chronometer would keep moving.
     */
    private fun remoteChronometer(): Pair<Long?, Boolean> {
        if (paused) return null to false
        val now = System.currentTimeMillis()
        return when {
            phase == LivePhase.GET_READY ->
                (now + leadInRemainingMs.coerceAtLeast(0L)) to true
            phase == LivePhase.ACTIVE && resting ->
                (now + restRemainingMs.coerceAtLeast(0L)) to true
            phase == LivePhase.ACTIVE && section.roundTimed ->
                (now + intervalRemainingMs.coerceAtLeast(0L)) to true
            phase == LivePhase.ACTIVE && intervalSet ->
                (now + intervalRemainingMs.coerceAtLeast(0L)) to true
            phase == LivePhase.ACTIVE && section.isAmrap -> section.timeCapSeconds?.let {
                (now + (it * 1000L - sectionElapsedMs).coerceAtLeast(0L)) to true
            } ?: (null to false)
            phase == LivePhase.ACTIVE && section.clocked ->
                (now - sectionElapsedMs.coerceAtLeast(0L)) to false
            else -> null to false
        }
    }

    private fun remoteTimer(): String {
        val core = when {
            phase == LivePhase.GET_READY -> formatLiveClock(leadInRemainingMs)
            phase == LivePhase.ACTIVE && resting -> "Rest ${formatLiveClock(restRemainingMs)}"
            phase == LivePhase.ACTIVE && section.roundTimed ->
                "${formatLiveClock(intervalRemainingMs)} left in round"
            phase == LivePhase.ACTIVE && intervalSet ->
                if (itemDone) {
                    "Done · ${formatLiveClock(intervalRemainingMs)} left"
                } else {
                    "${formatLiveClock(intervalRemainingMs)} left"
                }
            phase == LivePhase.ACTIVE && section.isAmrap -> {
                val cap = section.timeCapSeconds
                if (cap != null) {
                    "${formatLiveClock(cap * 1000L - sectionElapsedMs)} left"
                } else {
                    ""
                }
            }
            phase == LivePhase.ACTIVE && section.clocked ->
                formatLiveElapsed(sectionElapsedMs)
            else -> ""
        }
        if (core.isBlank()) return ""
        return if (paused) "Paused · $core" else core
    }

    private fun remoteResult(): String? = lastResult?.let { "${it.label} · ${it.value}" }

    private fun remoteMediaDuration(): Long = when {
        phase == LivePhase.GET_READY -> leadInRemainingMs.coerceAtLeast(1L)
        phase == LivePhase.ACTIVE && resting ->
            ((section.restSeconds ?: 0) * 1000L).coerceAtLeast(1L)
        phase == LivePhase.ACTIVE && section.roundTimed ->
            ((section.roundSeconds ?: 0) * 1000L).coerceAtLeast(1L)
        phase == LivePhase.ACTIVE && intervalSet ->
            ((section.intervalSeconds ?: 0) * 1000L).coerceAtLeast(1L)
        phase == LivePhase.ACTIVE && section.isAmrap ->
            ((section.timeCapSeconds ?: 0) * 1000L).coerceAtLeast(1L)
        phase == LivePhase.ACTIVE && section.clocked ->
            ((section.timeCapSeconds?.times(1000L)) ?: sectionElapsedMs).coerceAtLeast(1L)
        else -> 1L
    }

    /** Countdowns fill the bar as they empty; clocks simply run. */
    private fun remoteMediaPosition(): Long = when {
        phase == LivePhase.GET_READY -> 0L
        phase == LivePhase.ACTIVE && resting ->
            (remoteMediaDuration() - restRemainingMs).coerceAtLeast(0L)
        phase == LivePhase.ACTIVE && section.roundTimed ->
            (remoteMediaDuration() - intervalRemainingMs).coerceAtLeast(0L)
        phase == LivePhase.ACTIVE && intervalSet ->
            (remoteMediaDuration() - intervalRemainingMs).coerceAtLeast(0L)
        phase == LivePhase.ACTIVE && section.isAmrap -> sectionElapsedMs
        phase == LivePhase.ACTIVE && section.clocked -> sectionElapsedMs
        else -> 0L
    }

    /** Changes only when the workout's stage moves, never on a timer tick. */
    private fun remoteStageKey(): String = buildString {
        append(phase.name).append('|')
        append(sectionIndex).append('|')
        append(liveItemIndex).append('|')
        append(resting).append('|')
        append(roundWorkDone).append('|')
        append(itemDone).append('|')
        append(roundsDone).append('|')
        append(lastResult?.label).append('|')
        append(lastResult?.value)
    }

    fun remoteSnapshot(): LiveRemoteSnapshot {
        val title = remoteTitle()
        val exercise = remoteExercise()
        val timer = remoteTimer()
        val result = remoteResult()
        val actions = remoteActions()
        val (chronometerBase, chronometerDown) = remoteChronometer()
        // While a timer runs it is the chronometer's job; only a paused timer
        // (which is not moving) belongs in the text.
        val staticTimer = timer.takeIf { chronometerBase == null }
        val subText = when {
            !staticTimer.isNullOrBlank() -> staticTimer
            phase == LivePhase.DONE -> ""
            phase == LivePhase.OVERVIEW && lastResult != null -> ""
            else -> result.orEmpty()
        }
        val bigText = buildString {
            append(title)
            if (exercise.isNotBlank() && exercise != title) {
                append('\n').append(exercise)
            }
            if (!staticTimer.isNullOrBlank()) append('\n').append(staticTimer)
            if (result != null && result != title && result != exercise) {
                append('\n').append(result)
            }
        }
        val mediaArtist = listOfNotNull(exercise, timer)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .ifBlank { result.orEmpty() }
        return LiveRemoteSnapshot(
            date = ai.date,
            typeKey = ai.typeKey,
            title = title,
            text = exercise.ifBlank { result.orEmpty() },
            subText = subText,
            bigText = bigText,
            result = result,
            stageKey = remoteStageKey(),
            actions = actions,
            done = phase == LivePhase.DONE,
            mediaTitle = title,
            mediaArtist = mediaArtist,
            mediaAlbum = "HC Sync",
            mediaDurationMs = remoteMediaDuration(),
            mediaPositionMs = remoteMediaPosition(),
            chronometerBaseMs = chronometerBase,
            chronometerCountDown = chronometerDown,
        )
    }
}

private fun liveSessionSaver(
    live: LivePlan,
    ai: AiWorkoutState,
    cues: LiveCues,
    leadInSeconds: Int,
) = listSaver<LiveSessionState, Any>(
    save = { state ->
        listOf(
            state.sectionIndex,
            state.itemIndex,
            state.phase.name,
            state.paused,
            state.roundsDone,
            state.leadInRemainingMs,
            state.intervalRemainingMs,
            state.sectionElapsedMs,
            state.sessionElapsedMs,
            state.showMap,
            state.started,
            state.liveItemIndex,
            state.restRemainingMs,
            state.currentRound,
            state.itemDone,
            state.roundWorkDone,
            state.resting,
            state.lastSplitElapsedMs,
            state.lastResult?.label.orEmpty(),
            state.lastResult?.value.orEmpty(),
        )
    },
    restore = { values ->
        LiveSessionState(live, ai, cues, leadInSeconds).apply {
            sectionIndex = (values[0] as Int).coerceIn(0, live.sections.lastIndex)
            itemIndex = values[1] as Int
            phase = runCatching { LivePhase.valueOf(values[2] as String) }
                .getOrDefault(LivePhase.OVERVIEW)
            paused = values[3] as Boolean
            roundsDone = values[4] as Int
            leadInRemainingMs = values[5] as Long
            intervalRemainingMs = values[6] as Long
            sectionElapsedMs = values[7] as Long
            sessionElapsedMs = values[8] as Long
            showMap = values[9] as Boolean
            started = values[10] as Boolean
            liveItemIndex = values[11] as Int
            restRemainingMs = values[12] as Long
            currentRound = values[13] as Int
            itemDone = values[14] as Boolean
            roundWorkDone = values[15] as Boolean
            resting = values[16] as Boolean
            lastSplitElapsedMs = values[17] as Long
            val resultLabel = values.getOrNull(18) as? String ?: ""
            val resultValue = values.getOrNull(19) as? String ?: ""
            lastResult = if (resultLabel.isBlank() && resultValue.isBlank()) {
                null
            } else {
                LiveResultNote(resultLabel, resultValue)
            }
        }
    },
)

// ── Body ────────────────────────────────────────────────────────────────────

@Composable
private fun LiveWorkoutBody(
    ai: AiWorkoutState,
    onBack: () -> Unit,
    join: Boolean,
    start: LiveStart?,
) {
    val context = LocalContext.current
    val plan = ai.plan ?: return
    val defaultInterval = remember { Prefs.getLiveSetIntervalSeconds(context) }
    val leadIn = remember { Prefs.getLiveLeadInSeconds(context) }
    val keepScreenOn = remember { Prefs.getLiveKeepScreenOn(context) }
    val live = remember(plan, defaultInterval) { LiveWorkout.build(plan, defaultInterval) }
    val cues = remember {
        LiveCues(
            context = context,
            soundEnabled = Prefs.getLiveSound(context),
            vibrationEnabled = Prefs.getLiveVibration(context),
        )
    }
    DisposableEffect(cues) {
        onDispose { cues.release() }
    }

    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        if (keepScreenOn) view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val scope = rememberCoroutineScope()
    val session = rememberSaveable(
        live,
        start,
        saver = remember(live) { liveSessionSaver(live, ai, cues, leadIn) },
    ) {
        LiveSessionState(live, ai, cues, leadIn, start)
    }

    var leaving by remember { mutableStateOf(false) }
    var prCelebration by remember { mutableStateOf<List<PrLine>>(emptyList()) }
    LaunchedEffect(ai.newRecordRevision) {
        if (ai.newRecordRevision > 0) {
            prCelebration = ai.newRecords.map { record ->
                PrLine(
                    exercise = record.exerciseName,
                    detail = "${record.reps}RM · " +
                        "${RecordLogic.formatWeight(record.weightKg)} kg",
                )
            }
        }
    }
    val leave: () -> Unit = {
        if (!leaving) {
            leaving = true
            session.finalizeBeforeExit()
            val dto = session.toSyncDto(ai.date, ai.typeKey)
            liveSyncScope.launch { LiveSessionHub.closeSession(context, dto) }
            LiveRemoteHub.stop(context)
            scope.launch {
                runCatching { ai.save() }
                DataRefresh.bump()
                onBack()
            }
        }
    }

    // ── The band's remote ───────────────────────────────────────────────────
    // The session stays here; the service only reads its snapshot and relays
    // button presses back through the hub. This is what makes the Fit3 and
    // Fitbit Music Controller drive the workout.
    DisposableEffect(session) {
        val remoteHost = object : LiveRemoteHost {
            override fun remoteSnapshot(): LiveRemoteSnapshot = session.remoteSnapshot()

            override fun performRemoteAction(id: String) {
                session.performRemote(id)
            }
        }
        LiveRemoteHub.attach(remoteHost)
        onDispose { LiveRemoteHub.detach(remoteHost) }
    }

    // Start with the screen, not the first set: the band can start the
    // workout too, and the phone may already be in a pocket.
    LaunchedEffect(Unit) {
        LiveRemoteHub.start(context, ai.date, ai.typeKey)
    }

    // ── The watch link ──────────────────────────────────────────────────────
    val remote by LiveSessionHub.remote.collectAsState()
    val requestResult by LiveSessionHub.requestResult.collectAsState()
    var syncNote by remember { mutableStateOf<String?>(null) }
    var lastRemoteAdopted by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        LiveSessionHub.localActive = true
        onDispose {
            // Any way off this screen without leave() still ends the workout
            // on the watch; a remote end sets leaving first.
            if (!leaving) {
                leaving = true
                session.finalizeBeforeExit()
                val dto = session.toSyncDto(ai.date, ai.typeKey)
                liveSyncScope.launch {
                    runCatching { ai.save() }
                    LiveSessionHub.closeSession(context, dto)
                }
                LiveRemoteHub.stop(context)
            }
        }
    }

    // The other device moved: take its stage and values. A close is terminal.
    LaunchedEffect(remote?.sessionId, remote?.updatedAt, remote?.open, join) {
        val r = remote ?: return@LaunchedEffect
        if (r.date != ai.date || r.typeKey != ai.typeKey) return@LaunchedEffect
        if (r.origin == DEVICE_PHONE) return@LaunchedEffect
        // A record from before this session began is history; the previous
        // session's close must not kill a fresh one.
        if (!join && r.updatedAt <= session.syncStartedAt) return@LaunchedEffect
        if (!r.open) {
            if (leaving) return@LaunchedEffect
            leaving = true
            session.finalizeBeforeExit()
            runCatching { ai.save() }
            DataRefresh.bump()
            onBack()
            return@LaunchedEffect
        }
        if (r.updatedAt <= lastRemoteAdopted) return@LaunchedEffect
        val stale = System.currentTimeMillis() - r.updatedAt > 10 * 60_000L
        if (stale && !join) return@LaunchedEffect
        lastRemoteAdopted = r.updatedAt
        session.adopt(r)
        session.lastPublishedFingerprint = session.syncFingerprint()
        // The phone is in a workout even while it is following the watch; a
        // "sync from phone" request must be answered with this stage.
        LiveSessionHub.markFollowing(r)
        syncNote = null
        runCatching { ai.save() }
    }

    // This session moved: publish it. A stage that was just adopted carries
    // the adopted fingerprint, so adoption never echoes back.
    val syncFingerprint = session.syncFingerprint()
    LaunchedEffect(syncFingerprint) {
        if (leaving) return@LaunchedEffect
        if (syncFingerprint == session.lastPublishedFingerprint) return@LaunchedEffect
        delay(250)
        // The end may have landed during the debounce; never publish an open
        // stage after the close.
        if (leaving) return@LaunchedEffect
        session.lastPublishedFingerprint = syncFingerprint
        withContext(Dispatchers.IO) {
            LiveSessionHub.publish(context, session.toSyncDto(ai.date, ai.typeKey))
        }
    }

    fun syncFromWatch() {
        syncNote = "Asking the watch…"
        liveSyncScope.launch {
            if (!LiveSessionHub.requestWatchState(context)) {
                syncNote = "Watch is not connected"
                return@launch
            }
            delay(5000)
            if (syncNote == "Asking the watch…") syncNote = "No answer from the watch"
        }
    }

    LaunchedEffect(requestResult) {
        when (requestResult) {
            "none" -> {
                syncNote = "The watch isn't in a workout"
                LiveSessionHub.clearRequestResult()
            }
        }
    }

    // A section's values and score are written when it ends; the whole
    // session's write lands once the last one does.
    LaunchedEffect(session.phase) {
        if (session.phase == LivePhase.DONE) {
            session.commitCurrentValues()
            runCatching { ai.save() }
            DataRefresh.bump()
        }
    }

    // The one clock: every phase reads its remaining time from accumulated
    // milliseconds, so pausing and recomposing never move it.
    LaunchedEffect(
        session.phase,
        session.paused,
        session.reviewing,
        session.sectionIndex,
        session.itemIndex,
        session.resting,
    ) {
        if (session.paused || session.reviewing) return@LaunchedEffect
        if (session.phase != LivePhase.GET_READY && session.phase != LivePhase.ACTIVE) {
            return@LaunchedEffect
        }
        var last = System.currentTimeMillis()
        while (true) {
            delay(100)
            val now = System.currentTimeMillis()
            val delta = (now - last).coerceAtLeast(0L)
            last = now
            session.tick(delta)
        }
    }

    val accent = sectionAccent(session.section.kind)

    BackHandler {
        when {
            session.showMap -> session.showMap = false
            session.showEndConfirm -> session.showEndConfirm = false
            session.phase == LivePhase.DONE -> leave()
            else -> session.showEndConfirm = true
        }
    }

    if (session.showEndConfirm) {
        ConfirmDialog(
            title = "End live workout?",
            message = "Everything you entered is saved, and the journal marks the last set " +
                "you reached.",
            confirmText = "End workout",
            destructive = true,
            onConfirm = {
                session.showEndConfirm = false
                leave()
            },
            onDismiss = { session.showEndConfirm = false },
        )
    }

    Box(Modifier.fillMaxSize().imePadding()) {
        when {
            session.showMap -> WorkoutMap(
                live = live,
                session = session,
                onClose = { session.showMap = false },
            )

            session.phase == LivePhase.DONE -> DoneScreen(
                live = live,
                ai = ai,
                session = session,
                onDone = leave,
            )

            else -> Column(Modifier.fillMaxSize()) {
                LiveTopBar(
                    eyebrow = if (session.phase == LivePhase.OVERVIEW) {
                        "LIVE WORKOUT"
                    } else {
                        "LIVE · ${formatLiveElapsed(session.sessionElapsedMs)}"
                    },
                    title = if (session.phase == LivePhase.OVERVIEW) {
                        session.section.title
                    } else {
                        live.title
                    },
                    accent = accent,
                    paused = session.paused || session.reviewing,
                    onClose = { session.showEndConfirm = true },
                    onSync = if (!session.reviewing) ({ syncFromWatch() }) else null,
                    onMap = { session.showMap = true },
                    onPause = if (
                        !session.reviewing &&
                        (session.phase == LivePhase.GET_READY ||
                            session.phase == LivePhase.ACTIVE)
                    ) {
                        { session.paused = !session.paused }
                    } else {
                        null
                    },
                )
                syncNote?.let { note ->
                    Text(
                        note,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Periwinkle,
                        textAlign = TextAlign.Center,
                    )
                }
                when (session.phase) {
                    LivePhase.OVERVIEW -> SectionOverview(
                        live = live,
                        session = session,
                        accent = accent,
                        onStart = session::startCurrentSection,
                    )

                    LivePhase.GET_READY -> GetReadyScreen(
                        section = session.section,
                        item = session.item,
                        remainingMs = session.leadInRemainingMs,
                        totalMs = leadIn * 1000L,
                        accent = accent,
                        onSkip = session::skipLeadIn,
                    )

                    LivePhase.ACTIVE -> ActiveScreen(
                        live = live,
                        session = session,
                        accent = accent,
                    )

                    LivePhase.DONE -> Unit
                }
            }
        }
        if (prCelebration.isNotEmpty()) {
            NewPrCelebration(
                records = prCelebration,
                onFinished = { prCelebration = emptyList() },
            )
        }
    }
}

// ── Top bar ─────────────────────────────────────────────────────────────────

@Composable
private fun LiveTopBar(
    eyebrow: String,
    title: String,
    accent: Color,
    paused: Boolean,
    onClose: () -> Unit,
    onSync: (() -> Unit)?,
    onMap: (() -> Unit)?,
    onPause: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(
            icon = { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Snow) },
            onClick = onClose,
            border = false,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (paused) Warning else accent)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (paused) "PAUSED" else eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (paused) Warning else accent,
                    maxLines = 1,
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = Snow,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onSync != null) {
            IconAction(
                icon = {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Sync from watch",
                        tint = if (paused) Slate else Periwinkle,
                        modifier = Modifier.size(20.dp),
                    )
                },
                onClick = onSync,
                border = false,
            )
        }
        if (onMap != null) {
            IconAction(
                icon = {
                    Icon(
                        Icons.AutoMirrored.Outlined.List,
                        contentDescription = "Workout map",
                        tint = Mist,
                        modifier = Modifier.size(20.dp),
                    )
                },
                onClick = onMap,
                border = false,
            )
        }
        if (onPause != null) {
            Spacer(Modifier.width(6.dp))
            IconAction(
                icon = {
                    if (paused) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Resume",
                            tint = accent,
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        PauseGlyph(Mist)
                    }
                },
                onClick = onPause,
                border = false,
            )
        }
    }
}

@Composable
private fun PauseGlyph(color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(2) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

/**
 * The at-a-glance strip: one segment per section, the current one carrying
 * the work done inside it.
 */
@Composable
private fun ProgressStrip(live: LivePlan, session: LiveSessionState) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        live.sections.forEachIndexed { index, section ->
            val done = index < session.sectionIndex || session.phase == LivePhase.DONE
            val current = index == session.sectionIndex && !done
            val inner = when {
                done -> 1f
                current && session.phase != LivePhase.OVERVIEW -> {
                    val count = section.items.size
                    if (count > 0) {
                        ((session.liveItemIndex + 1f) / count).coerceIn(0.12f, 1f)
                    } else {
                        0.5f
                    }
                }
                current -> 0.12f
                else -> 0f
            }
            val color = sectionAccent(section.kind)
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Snow.copy(alpha = 0.07f))
            ) {
                if (inner > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth(inner)
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(color.copy(alpha = if (current) 1f else 0.55f))
                    )
                }
            }
        }
    }
}

// ── Section overview ────────────────────────────────────────────────────────

@Composable
private fun SectionOverview(
    live: LivePlan,
    session: LiveSessionState,
    accent: Color,
    onStart: () -> Unit,
) {
    val section = session.section
    val first = session.sectionIndex == 0
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(6.dp))
            Text(
                if (first) {
                    "SECTION 1 OF ${live.sections.size}"
                } else {
                    "UP NEXT · SECTION ${session.sectionIndex + 1} OF ${live.sections.size}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = Slate,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                section.title,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp),
                color = Snow,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag(section.kind.uppercase(), accent)
                Spacer(Modifier.width(8.dp))
                TimingTag(section, accent)
            }
            if (section.notes.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Card)
                        .border(1.dp, Hairline, RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        section.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mist,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            SectionPreview(section, accent)
            if (!LocalMinimalDesign.current) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Your values are saved as you go. You can pause, step back a set, or open the " +
                        "map at any time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                )
            }
            Spacer(Modifier.height(18.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            PrimaryButton(
                text = when {
                    section.items.isEmpty() && section.metrics.isEmpty() -> "Continue"
                    first -> "Start workout"
                    else -> when (section.kind) {
                        "strength" -> "Start strength"
                        "metcon" -> "Start metcon"
                        "accessory" -> "Start accessory"
                        "rest" -> "Start rest"
                        else -> "Start section"
                    }
                },
                onClick = onStart,
                accent = accent,
            )
            Spacer(Modifier.height(2.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = session::skipCurrentSection) {
                    Text(
                        "Skip this section",
                        style = MaterialTheme.typography.labelLarge,
                        color = Slate,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimingTag(section: LiveSection, accent: Color) {
    val cap = section.timeCapSeconds
    val interval = section.intervalSeconds
    val text = when {
        section.roundTimed -> buildString {
            append("${section.roundCount} × ")
            append(formatLiveClock((section.roundSeconds ?: 0) * 1000L))
            section.restSeconds?.let { append(" · ${formatLiveClock(it * 1000L)} rest") }
        }
        section.isAmrap && cap != null ->
            "${formatLiveClock(cap * 1000L)} AMRAP"
        section.isAmrap -> "AMRAP"
        cap != null ->
            "Cap ${formatLiveClock(cap * 1000L)}"
        interval != null ->
            "Every ${formatLiveClock(interval * 1000L)}"
        section.clocked -> "For time"
        else -> return
    }
    Tag(text, accent.copy(alpha = 0.85f))
}

@Composable
private fun SectionPreview(section: LiveSection, accent: Color) {
    if (section.items.isEmpty()) {
        if (section.metrics.isNotEmpty()) {
            Text(
                "Recorded on this section: " +
                    section.metrics.joinToString(", ") { it.label.lowercase() },
                style = MaterialTheme.typography.bodyMedium,
                color = Mist,
            )
        }
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Card)
            .border(1.dp, Hairline, RoundedCornerShape(20.dp))
            .padding(vertical = 6.dp)
    ) {
        if (section.roundCount > 0) {
            // A round-based piece is shown as its shape — "4 rounds of" the
            // movements — not as every round's rows one after another.
            Text(
                if (section.isAmrap) "EACH ROUND UNTIL THE CAP"
                else "${section.roundCount} ROUNDS OF",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = accent,
            )
            PreviewDivider()
            val firstRound = section.items
                .filter { it.roundIndex == 0 }
                .ifEmpty { section.items.take(4) }
            firstRound.forEachIndexed { index, item ->
                if (index > 0) PreviewDivider()
                PreviewItemRow(item, accent, showPosition = false)
            }
            val timing = buildList {
                section.roundSeconds?.let {
                    add("${formatLiveClock(it * 1000L)} per round")
                }
                section.restSeconds?.let {
                    add("${formatLiveClock(it * 1000L)} rest between rounds")
                }
            }
            if (timing.isNotEmpty()) {
                Text(
                    timing.joinToString(" · "),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                )
            }
        } else {
            val shown = section.items.take(6)
            shown.forEachIndexed { index, item ->
                if (index > 0) PreviewDivider()
                PreviewItemRow(item, accent, showPosition = true)
            }
            val remaining = section.items.size - shown.size
            if (remaining > 0) {
                Text(
                    "+$remaining more",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Slate,
                )
            }
        }
    }
}

@Composable
private fun PreviewDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(1.dp)
            .background(HairlineSoft)
    )
}

@Composable
private fun PreviewItemRow(
    item: LiveItem,
    accent: Color,
    showPosition: Boolean,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (showPosition && item.position.isNotBlank()) {
                Text(
                    item.position,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent.copy(alpha = 0.85f),
                )
            }
            Text(
                item.name,
                style = MaterialTheme.typography.titleSmall,
                color = Snow,
            )
        }
        if (item.target.isNotBlank()) {
            Text(
                item.target,
                modifier = Modifier.padding(start = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Mist,
                maxLines = 2,
                textAlign = TextAlign.End,
            )
        }
    }
}

// ── Get ready ───────────────────────────────────────────────────────────────

@Composable
private fun GetReadyScreen(
    section: LiveSection,
    item: LiveItem?,
    remainingMs: Long,
    totalMs: Long,
    accent: Color,
    onSkip: () -> Unit,
) {
    val seconds = ((remainingMs.coerceAtLeast(0L) + 999L) / 1000L).toInt()
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "GET READY",
            style = MaterialTheme.typography.labelLarge,
            color = accent,
        )
        Spacer(Modifier.height(18.dp))
        TimerRing(
            fraction = if (totalMs > 0) {
                (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            },
            color = accent,
            diameter = 192.dp,
        ) {
            Text(
                seconds.toString(),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 76.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum",
                ),
                color = Snow,
            )
        }
        Spacer(Modifier.height(26.dp))
        if (item != null) {
            if (item.position.isNotBlank()) {
                Text(
                    item.position.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Slate,
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                item.name,
                style = MaterialTheme.typography.headlineLarge,
                color = Snow,
                textAlign = TextAlign.Center,
            )
            if (item.target.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    item.target,
                    style = MaterialTheme.typography.titleMedium,
                    color = Mist,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Text(
                section.title,
                style = MaterialTheme.typography.headlineLarge,
                color = Snow,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(30.dp))
        TextButton(onClick = onSkip) {
            Text(
                "Start now",
                style = MaterialTheme.typography.labelLarge,
                color = accent,
            )
        }
    }
}

// ── Active item ─────────────────────────────────────────────────────────────

@Composable
private fun ActiveScreen(
    live: LivePlan,
    session: LiveSessionState,
    accent: Color,
) {
    val section = session.section
    val past = session.reviewing
    val future = session.previewing

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ProgressStrip(live, session)
            if (past || future) {
                Spacer(Modifier.height(8.dp))
                ViewBanner(session = session, accent = accent, onResume = session::resumeLive)
            }
            Spacer(Modifier.height(10.dp))
            SwipeCarousel(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                canSwipeNext = session.itemIndex < section.items.lastIndex,
                canSwipePrevious = session.itemIndex > 0,
                onSwitch = { direction ->
                    if (direction > 0) session.viewNext() else session.viewPrevious()
                },
            ) { step ->
                StepPage(
                    live = live,
                    session = session,
                    pageIndex = session.itemIndex + step,
                    accent = accent,
                )
            }
        }
        if (session.paused && session.onLiveItem) {
            PausedOverlay(
                accent = accent,
                onResume = { session.paused = false },
                onEnd = { session.showEndConfirm = true },
            )
        }
    }
}

/**
 * One step as a carousel page. The page that is the live item shows the
 * running timer and the live actions; the neighbouring pages show the
 * completed or upcoming presentation, so both sides of a swipe are real
 * content rather than a blank edge.
 */
@Composable
private fun StepPage(
    live: LivePlan,
    session: LiveSessionState,
    pageIndex: Int,
    accent: Color,
) {
    val section = session.section
    val item = section.items.getOrNull(pageIndex)
    val focus = LocalFocusManager.current
    val onLive = pageIndex == session.liveItemIndex
    val past = pageIndex < session.liveItemIndex
    val future = pageIndex > session.liveItemIndex
    val roundFinished = section.roundTimed &&
        (session.roundWorkDone || session.resting) &&
        onLive
    val intervalSet = !section.roundTimed && section.intervalSeconds != null
    val title = item?.name ?: section.title
    val position = if (section.isAmrap) {
        "Round ${session.roundsDone + 1}"
    } else {
        item?.position.orEmpty()
    }
    val target = item?.target.orEmpty()
    val metrics = item?.metrics ?: section.metrics
    val showTarget = target.isNotBlank() &&
        !targetIsRedundant(target, metrics, item)

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(6.dp))
            if (roundFinished) {
                RoundCompletedBlock(session, accent)
            } else {
                if (position.isNotBlank()) {
                    Text(
                        position.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = when {
                            title.length <= 16 -> 38.sp
                            title.length <= 24 -> 32.sp
                            else -> 27.sp
                        },
                        lineHeight = 40.sp,
                    ),
                    color = Snow,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showTarget) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        target,
                        style = MaterialTheme.typography.titleMedium,
                        color = Mist,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
                Spacer(Modifier.height(12.dp))
                when {
                    past -> CompletedItemBlock(
                        detail = "Editing a completed set",
                        accent = accent,
                    )

                    future -> UpcomingBlock(paused = session.paused)

                    else -> {
                        if (intervalSet && session.itemDone) {
                            CompletedBanner()
                            Spacer(Modifier.height(10.dp))
                        }
                        TimerBlock(session)
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (item != null) {
                    ItemFields(
                        item = item,
                        session = session,
                        accent = accent,
                        onDoneEditing = { focus.clearFocus() },
                    )
                } else {
                    MetricFieldsRow(
                        metrics = metrics,
                        valueOf = { session.resolvedValueForSection(it) },
                        onChange = { metric, value -> session.setSectionValue(metric, value) },
                        accent = accent,
                        onDoneEditing = { focus.clearFocus() },
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            if (roundFinished) {
                if (session.resting) {
                    SecondaryButton(
                        text = "Skip rest",
                        onClick = session::skipRest,
                        accent = Periwinkle,
                    )
                    Spacer(Modifier.height(4.dp))
                }
            } else {
                LiveActionsRow(
                    session = session,
                    intervalSet = intervalSet,
                    enabled = onLive,
                    accent = accent,
                )
                Spacer(Modifier.height(4.dp))
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = session::viewPrevious) {
                    Text(
                        "← Previous",
                        style = MaterialTheme.typography.labelLarge,
                        color = Slate,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (onLive && section.clocked && !roundFinished) {
                    TextButton(onClick = session::finishCurrentSection) {
                        Text(
                            "Finish section",
                            style = MaterialTheme.typography.labelLarge,
                            color = accent,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = session::viewNext) {
                    Text(
                        "Next →",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                    )
                }
            }
            UpNextCaption(live, session)
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * The two live controls: mark complete (toggles to not completed) and skip
 * the timer. They only act on the active set; a past or future view shows
 * them greyed and inert.
 */
@Composable
private fun LiveActionsRow(
    session: LiveSessionState,
    intervalSet: Boolean,
    enabled: Boolean,
    accent: Color,
) {
    val marked = intervalSet && session.itemDone
    val markLabel = when {
        !enabled && session.reviewing -> "Completed"
        marked -> "Not completed"
        else -> "Mark as complete"
    }
    val markBackground = when {
        !enabled -> CardHigh
        marked -> Danger
        else -> Success
    }
    val markText = when {
        !enabled -> Slate
        marked -> Snow
        else -> Abyss
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LiveActionButton(
            text = markLabel,
            background = markBackground,
            textColor = markText,
            enabled = enabled,
            onClick = {
                if (intervalSet) session.toggleItemDone() else session.completeAndAdvance()
            },
            modifier = Modifier.weight(1f),
        )
        LiveActionButton(
            text = if (intervalSet) "Skip timer" else "Skip",
            background = CardHigh,
            textColor = if (enabled) Snow else Slate,
            enabled = enabled,
            border = true,
            onClick = session::skipLiveTimer,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LiveActionButton(
    text: String,
    background: Color,
    textColor: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    border: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .height(50.dp)
            .clip(shape)
            .background(background)
            .then(
                if (border) Modifier.border(1.dp, Hairline, shape) else Modifier
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Above a timer whose set is marked done but whose window still runs. */
@Composable
private fun CompletedBanner() {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Success.copy(alpha = 0.16f))
            .border(1.dp, Success.copy(alpha = 0.40f), RoundedCornerShape(50))
            .padding(horizontal = 13.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Success,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                "COMPLETED",
                style = MaterialTheme.typography.labelSmall,
                color = Success,
            )
        }
    }
}

/**
 * A past or upcoming set with no live timer. Completed sets keep their
 * fields editable; upcoming ones are a preview of what is next.
 */
@Composable
private fun CompletedItemBlock(
    detail: String,
    accent: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CompletedCircle(112.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            "Completed",
            style = MaterialTheme.typography.headlineSmall,
            color = Snow,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = Slate,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun UpcomingBlock(paused: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(CardHigh)
                .border(1.dp, Hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Slate,
                modifier = Modifier.size(38.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Not started",
            style = MaterialTheme.typography.titleLarge,
            color = Snow,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            if (paused) "The live clock is paused." else "The live clock is still running.",
            style = MaterialTheme.typography.bodySmall,
            color = Slate,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RoundCompletedBlock(session: LiveSessionState, accent: Color) {
    val section = session.section
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompletedCircle(136.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Round ${session.currentRound + 1} completed",
            style = MaterialTheme.typography.headlineMedium,
            color = Snow,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        if (session.resting) {
            Text(
                "REST",
                style = MaterialTheme.typography.labelLarge,
                color = Periwinkle,
            )
            Text(
                formatLiveClock(session.restRemainingMs),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 62.sp,
                    lineHeight = 66.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum",
                ),
                color = Periwinkle,
                maxLines = 1,
            )
        } else {
            Text(
                "Round time left",
                style = MaterialTheme.typography.labelSmall,
                color = Slate,
            )
            Text(
                formatLiveClock(session.intervalRemainingMs),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 44.sp,
                    lineHeight = 48.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum",
                ),
                color = accent,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            roundNextText(session, section),
            style = MaterialTheme.typography.bodySmall,
            color = Slate,
            textAlign = TextAlign.Center,
        )
    }
}

private fun roundNextText(session: LiveSessionState, section: LiveSection): String {
    val hasNextRound = session.currentRound < section.roundCount - 1
    val nextRound = if (hasNextRound) {
        "Round ${session.currentRound + 2} of ${section.roundCount}"
    } else {
        "Last round"
    }
    val rest = section.restSeconds
    return when {
        // The work is done early: the rest is what actually comes next.
        !session.resting && rest != null ->
            "Next · ${formatLiveClock(rest * 1000L)} min rest"
        else -> "Next · $nextRound"
    }
}

@Composable
private fun CompletedCircle(size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(Success, Success.copy(alpha = 0.78f))
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = Snow,
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

@Composable
private fun ViewBanner(
    session: LiveSessionState,
    accent: Color,
    onResume: () -> Unit,
) {
    val past = session.reviewing
    val color = if (past) Periwinkle else Teal
    val text = when {
        past -> "REVIEWING · CLOCK PAUSED"
        session.paused -> "PREVIEW · CLOCK PAUSED"
        else -> "PREVIEW · LIVE CLOCK RUNNING"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
        Text(
            "Back to active set",
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onResume)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelLarge,
            color = accent,
        )
    }
}

@Composable
private fun TimerBlock(session: LiveSessionState) {
    val section = session.section
    val interval = section.intervalSeconds
    val cap = section.timeCapSeconds
    val accent = sectionAccent(section.kind)

    val (label, text, fraction) = when {
        section.roundTimed -> {
            val remaining = session.intervalRemainingMs.coerceAtLeast(0L)
            Triple(
                "round time left",
                formatLiveClock(remaining),
                (remaining / 1000f / (section.roundSeconds ?: 1)).coerceIn(0f, 1f),
            )
        }

        interval != null && section.items.isNotEmpty() -> {
            val remaining = session.intervalRemainingMs.coerceAtLeast(0L)
            Triple(
                "until the next set",
                formatLiveClock(remaining),
                (remaining / 1000f / interval).coerceIn(0f, 1f),
            )
        }

        section.clocked && cap != null -> {
            val remaining = (cap * 1000L - session.sectionElapsedMs).coerceAtLeast(0L)
            Triple(
                "time cap left",
                formatLiveClock(remaining),
                (remaining / 1000f / cap).coerceIn(0f, 1f),
            )
        }

        section.clocked -> Triple(
            "elapsed",
            formatLiveElapsed(session.sectionElapsedMs),
            0f,
        )

        else -> Triple("", "", 0f)
    }

    if (text.isBlank()) return

    val warning = when {
        section.roundTimed -> session.intervalRemainingMs in 1..5_000
        interval != null -> session.intervalRemainingMs in 1..5_000
        section.clocked && cap != null ->
            cap * 1000L - session.sectionElapsedMs in 1..5_000
        else -> false
    }
    val color by animateColorAsState(
        if (warning) Danger else accent,
        tween(180),
        label = "timerColor",
    )
    TimerRing(fraction = fraction, color = color, diameter = 176.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 58.sp,
                    lineHeight = 62.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum",
                ),
                color = if (warning) Danger else Snow,
                maxLines = 1,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = Slate,
                maxLines = 1,
            )
        }
    }
}

private fun targetIsRedundant(
    target: String,
    metrics: List<MetricDef>,
    item: LiveItem?,
): Boolean {
    if (item == null) return false
    if (metrics.none { it.kind == MetricKind.REPS }) return false
    val plain = target.trim().lowercase()
    return Regex("""^\d+\s*(?:reps?|repetitions?)?$""").matches(plain)
}

@Composable
private fun TimerRing(
    fraction: Float,
    color: Color,
    diameter: Dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(260, easing = LinearEasing),
        label = "ring",
    )
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Snow.copy(alpha = 0.07f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (animated > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

// ── Value editing ───────────────────────────────────────────────────────────

@Composable
private fun ItemFields(
    item: LiveItem,
    session: LiveSessionState,
    accent: Color,
    onDoneEditing: () -> Unit,
) {
    val metrics = item.metrics
    val weight = metrics.firstOrNull { it.kind == MetricKind.WEIGHT }
    val reps = metrics.firstOrNull { it.kind == MetricKind.REPS }
    val check = metrics.firstOrNull { it.kind == MetricKind.CHECK }
    val others = metrics.filter { metric ->
        metric !== weight && metric !== reps && metric !== check
    }

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (check != null) {
            CheckField(
                metric = check,
                checked = session.resolvedValue(item, check) == "1",
                accent = accent,
                onToggle = {
                    session.setValue(
                        item,
                        check,
                        if (session.resolvedValue(item, check) == "1") "" else "1",
                    )
                },
            )
            if (weight != null || reps != null || others.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
            }
        }
        if (weight != null || reps != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (weight != null) {
                    StepperField(
                        metric = weight,
                        value = session.resolvedValue(item, weight),
                        accent = accent,
                        onValue = { session.setValue(item, weight, it) },
                        onDoneEditing = onDoneEditing,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (reps != null) {
                    StepperField(
                        metric = reps,
                        value = session.resolvedValue(item, reps),
                        accent = accent,
                        onValue = { session.setValue(item, reps, it) },
                        onDoneEditing = onDoneEditing,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (others.isNotEmpty()) {
            if (weight != null || reps != null || check != null) {
                Spacer(Modifier.height(10.dp))
            }
            MetricFieldsRow(
                metrics = others,
                valueOf = { session.resolvedValue(item, it) },
                onChange = { metric, value -> session.setValue(item, metric, value) },
                accent = accent,
                onDoneEditing = onDoneEditing,
            )
        }
    }
}

@Composable
private fun StepperField(
    metric: MetricDef,
    value: String,
    accent: Color,
    onValue: (String) -> Unit,
    onDoneEditing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val step = when (metric.kind) {
        MetricKind.WEIGHT ->
            if (metric.unit.equals("lb", true) || metric.unit.equals("lbs", true)) 5.0 else 2.5
        MetricKind.REPS -> 1.0
        MetricKind.CALORIES -> 5.0
        MetricKind.DISTANCE -> 50.0
        else -> 1.0
    }

    fun nudge(direction: Int) {
        val current = value.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
        onValue(stepValue(current + step * direction))
    }

    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CardHigh)
            .border(1.dp, Hairline, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            metric.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Slate,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                value = value,
                onValueChange = { raw ->
                    val clean = sanitiseMetricInput(raw, metric.kind)
                    if (clean != value) onValue(clean)
                },
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 30.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Snow,
                    textAlign = TextAlign.Center,
                    fontFeatureSettings = "tnum",
                ),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (metric.kind == MetricKind.WEIGHT) {
                        KeyboardType.Decimal
                    } else {
                        KeyboardType.Number
                    },
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onDoneEditing() },
                ),
                modifier = Modifier.widthIn(min = 36.dp),
            )
            if (metric.unit.isNotBlank()) {
                Spacer(Modifier.width(3.dp))
                Text(
                    metric.unit,
                    modifier = Modifier.padding(bottom = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate,
                )
            }
            Spacer(Modifier.width(5.dp))
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f))
                    .clickable { onDoneEditing() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Submit",
                    tint = accent,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        if (metric.hint.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                metric.hint,
                style = MaterialTheme.typography.labelSmall,
                color = accent.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(7.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton("−", accent = accent, onClick = { nudge(-1) })
            StepButton("+", accent = accent, onClick = { nudge(1) })
        }
    }
}

@Composable
private fun StepButton(text: String, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 34.dp)
            .width(46.dp)
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
            ),
            color = accent,
        )
    }
}

@Composable
private fun CheckField(
    metric: MetricDef,
    checked: Boolean,
    accent: Color,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (checked) accent.copy(alpha = 0.16f) else CardHigh)
            .border(
                1.dp,
                if (checked) accent.copy(alpha = 0.55f) else Hairline,
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (checked) accent else Color.Transparent)
                .border(1.5.dp, if (checked) accent else Slate.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Abyss,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            if (checked) "${metric.label} — done" else "Mark ${metric.label.lowercase()}",
            style = MaterialTheme.typography.titleMedium,
            color = if (checked) Snow else Mist,
        )
    }
}

@Composable
private fun MetricFieldsRow(
    metrics: List<MetricDef>,
    valueOf: (MetricDef) -> String,
    onChange: (MetricDef, String) -> Unit,
    accent: Color,
    onDoneEditing: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        metrics.forEach { metric ->
            when (metric.kind) {
                MetricKind.TIME -> TimeField(
                    metric = metric,
                    value = valueOf(metric),
                    accent = accent,
                    onChange = { onChange(metric, it) },
                )

                MetricKind.TEXT -> TextFieldCard(
                    metric = metric,
                    value = valueOf(metric),
                    accent = accent,
                    onChange = { onChange(metric, it) },
                    onDoneEditing = onDoneEditing,
                )

                MetricKind.CHECK -> CheckField(
                    metric = metric,
                    checked = valueOf(metric) == "1",
                    accent = accent,
                    onToggle = {
                        onChange(metric, if (valueOf(metric) == "1") "" else "1")
                    },
                )

                else -> StepperField(
                    metric = metric,
                    value = valueOf(metric),
                    accent = accent,
                    onValue = { onChange(metric, it) },
                    onDoneEditing = onDoneEditing,
                )
            }
        }
    }
}

@Composable
private fun TimeField(
    metric: MetricDef,
    value: String,
    accent: Color,
    onChange: (String) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardHigh)
            .border(1.dp, Hairline, RoundedCornerShape(18.dp))
            .clickable { picking = true }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            metric.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = Mist,
        )
        Text(
            if (value.isBlank()) metricHint(metric) else TimeValue.normalise(value),
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            color = if (value.isBlank()) Slate else Snow,
        )
        Spacer(Modifier.width(8.dp))
        Text("▾", style = MaterialTheme.typography.bodySmall, color = Slate)
    }
    if (picking) {
        TimeSpinner(
            title = metric.label,
            value = value,
            accent = accent,
            onConfirm = {
                onChange(it)
                picking = false
            },
            onClear = {
                onChange("")
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun TextFieldCard(
    metric: MetricDef,
    value: String,
    accent: Color,
    onChange: (String) -> Unit,
    onDoneEditing: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardHigh)
            .border(1.dp, Hairline, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            metric.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Slate,
        )
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = Snow),
            cursorBrush = SolidColor(accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDoneEditing() }),
        )
    }
}

@Composable
private fun TimeSpinner(
    title: String,
    value: String,
    accent: Color,
    onConfirm: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    // The picker is the plan editor's own minutes/seconds wheel, so a live
    // result and a hand-entered one are picked the same way.
    com.hcexport.ui.TimeSpinnerDialog(
        title = title,
        value = value,
        accent = accent,
        onConfirm = onConfirm,
        onClear = onClear,
        onDismiss = onDismiss,
    )
}

@Composable
private fun UpNextCaption(live: LivePlan, session: LiveSessionState) {
    val section = session.section
    val label: String
    val next: String?
    when {
        !session.onLiveItem -> {
            label = "Active"
            next = section.items.getOrNull(session.liveItemIndex)?.name
        }
        session.resting -> {
            label = "Up next"
            next = if (session.currentRound < section.roundCount - 1) {
                "Round ${session.currentRound + 2} of ${section.roundCount}"
            } else {
                null
            }
        }
        section.roundTimed && session.roundWorkDone -> {
            // The work is done early: the rest is what actually comes next.
            label = "Up next"
            next = section.restSeconds?.let {
                "${formatLiveClock(it * 1000L)} min rest"
            } ?: if (session.currentRound < section.roundCount - 1) {
                "Round ${session.currentRound + 2} of ${section.roundCount}"
            } else {
                null
            }
        }
        else -> {
            label = "Up next"
            val current = section.items.getOrNull(session.liveItemIndex)
            val nextItem = section.items.getOrNull(session.liveItemIndex + 1)
            next = when {
                section.roundTimed && nextItem != null &&
                    nextItem.roundIndex != current?.roundIndex ->
                    "Round ${(nextItem.roundIndex ?: 0) + 1} of ${section.roundCount}"
                nextItem != null -> nextItem.name
                section.isAmrap -> "Next round"
                else -> live.sections.drop(session.sectionIndex + 1)
                    .firstOrNull { it.items.isNotEmpty() || it.metrics.isNotEmpty() }
                    ?.title
            }
        }
    }
    if (next.isNullOrBlank()) return
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            "$label · $next",
            style = MaterialTheme.typography.bodySmall,
            color = Slate,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PausedOverlay(
    accent: Color,
    onResume: () -> Unit,
    onEnd: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Obsidian.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(74.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f))
                    .border(1.dp, accent.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PauseGlyph(accent)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "PAUSED",
                style = MaterialTheme.typography.labelLarge,
                color = accent,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "The clock is stopped.",
                style = MaterialTheme.typography.bodyMedium,
                color = Slate,
            )
            Spacer(Modifier.height(28.dp))
            PrimaryButton("Resume", onClick = onResume, accent = accent)
            Spacer(Modifier.height(10.dp))
            SecondaryButton("End workout", onClick = onEnd, accent = Danger)
        }
    }
}

// ── Workout map ─────────────────────────────────────────────────────────────

@Composable
private fun WorkoutMap(
    live: LivePlan,
    session: LiveSessionState,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LiveTopBar(
            eyebrow = "THE WHOLE WORKOUT",
            title = live.title,
            accent = Teal,
            paused = false,
            onClose = onClose,
            onSync = null,
            onMap = null,
            onPause = null,
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 30.dp),
        ) {
            live.sections.forEachIndexed { sectionIndex, section ->
                val sectionDone = sectionIndex < session.sectionIndex ||
                    session.phase == LivePhase.DONE
                item(key = "s$sectionIndex") {
                    MapSectionHeader(
                        section = section,
                        done = sectionDone,
                        current = sectionIndex == session.sectionIndex,
                        onClick = { session.jumpToSection(sectionIndex) },
                    )
                }
                if (section.items.isEmpty()) {
                    item(key = "e$sectionIndex") {
                        Text(
                            "Notes only — no sets to run.",
                            modifier = Modifier.padding(start = 14.dp, bottom = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate,
                        )
                    }
                }
                section.items.forEachIndexed { itemIndex, item ->
                    val current = sectionIndex == session.sectionIndex &&
                        itemIndex == session.liveItemIndex &&
                        (session.phase == LivePhase.ACTIVE ||
                            session.phase == LivePhase.GET_READY)
                    val viewed = sectionIndex == session.sectionIndex &&
                        itemIndex == session.itemIndex &&
                        !session.onLiveItem
                    item(key = "s${sectionIndex}i$itemIndex") {
                        MapItemRow(
                            item = item,
                            accent = sectionAccent(section.kind),
                            current = current,
                            viewed = viewed,
                            done = sectionDone ||
                                (sectionIndex == session.sectionIndex &&
                                    itemIndex < session.liveItemIndex),
                            onClick = { session.jumpTo(sectionIndex, itemIndex) },
                        )
                    }
                }
                if (section.isAmrap) {
                    item(key = "a$sectionIndex") {
                        Text(
                            "Repeats until the cap or until you finish.",
                            modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate,
                        )
                    }
                }
                item(key = "g$sectionIndex") { Spacer(Modifier.height(14.dp)) }
            }
        }
    }
}

@Composable
private fun MapSectionHeader(
    section: LiveSection,
    done: Boolean,
    current: Boolean,
    onClick: () -> Unit,
) {
    val accent = sectionAccent(section.kind)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    current -> accent.copy(alpha = 0.10f)
                    else -> Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(50))
                .background(accent)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                section.title,
                style = MaterialTheme.typography.titleSmall,
                color = Snow,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildList {
                    add(section.kind.uppercase())
                    if (section.items.isNotEmpty()) add("${section.items.size} steps")
                    TimingTagText(section)?.let(::add)
                }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = Slate,
                maxLines = 1,
            )
        }
        if (done) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Section complete",
                tint = Success,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun TimingTagText(section: LiveSection): String? {
    val cap = section.timeCapSeconds
    val interval = section.intervalSeconds
    return when {
        section.roundTimed -> buildString {
            append("${section.roundCount} × ")
            append(formatLiveClock((section.roundSeconds ?: 0) * 1000L))
            section.restSeconds?.let { append(" · ${formatLiveClock(it * 1000L)} rest") }
        }
        section.isAmrap && cap != null ->
            "${formatLiveClock(cap * 1000L)} AMRAP"
        section.isAmrap -> "AMRAP"
        cap != null ->
            "cap ${formatLiveClock(cap * 1000L)}"
        interval != null ->
            "every ${formatLiveClock(interval * 1000L)}"
        section.clocked -> "for time"
        else -> null
    }
}

@Composable
private fun MapItemRow(
    item: LiveItem,
    accent: Color,
    current: Boolean,
    viewed: Boolean,
    done: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 13.dp, end = 10.dp, top = 1.dp, bottom = 1.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    current -> accent.copy(alpha = 0.12f)
                    viewed -> Periwinkle.copy(alpha = 0.10f)
                    else -> Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (current) Snow else Mist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.position.isNotBlank() || item.target.isNotBlank()) {
                Text(
                    listOf(item.position, item.target)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            current -> Tag("NOW", accent)
            viewed -> Tag("VIEWING", Periwinkle)
            done -> Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Success.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ── Finish ──────────────────────────────────────────────────────────────────

@Composable
private fun DoneScreen(
    live: LivePlan,
    ai: AiWorkoutState,
    session: LiveSessionState,
    onDone: () -> Unit,
) {
    val plan = ai.plan
    val totals = plan?.let { JournalFeed.aiPlan(it, ai.results) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(30.dp))
        Box(
            Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Teal.copy(alpha = 0.75f), Periwinkle.copy(alpha = 0.75f))
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Abyss,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(
            "Workout complete",
            style = MaterialTheme.typography.displaySmall,
            color = Snow,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            live.title,
            style = MaterialTheme.typography.titleMedium,
            color = Mist,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DoneStat(
                label = "TIME",
                value = formatLiveElapsed(session.sessionElapsedMs),
                accent = Teal,
                modifier = Modifier.weight(1f),
            )
            DoneStat(
                label = "SETS",
                value = totals?.let { "${it.loggedSets}/${it.totalSets}" } ?: "—",
                accent = Periwinkle,
                modifier = Modifier.weight(1f),
            )
            DoneStat(
                label = "VOLUME",
                value = totals?.totalVolumeKg?.let { "${JournalFeed.formatVolume(it)} kg" } ?: "—",
                accent = Amber,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(18.dp))
        if (!LocalMinimalDesign.current) {
            Text(
                "Saved to the journal. Records are updated from the weights you logged.",
                style = MaterialTheme.typography.bodySmall,
                color = Slate,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(30.dp))
        PrimaryButton("Back to journal", onClick = onDone, accent = Teal)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DoneStat(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Card)
            .border(1.dp, Hairline, RoundedCornerShape(18.dp))
            .padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall.copy(
                color = Snow,
                fontFeatureSettings = "tnum",
            ),
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
        )
    }
}

// ── Small helpers ───────────────────────────────────────────────────────────

private fun stepValue(raw: Double): String {
    if (raw <= 0.0) return ""
    return if (raw == Math.floor(raw) && !raw.isInfinite()) {
        raw.roundToInt().toString()
    } else {
        String.format(java.util.Locale.US, "%.1f", raw)
    }
}
