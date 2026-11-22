package com.hcexport.wear.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Text
import com.hcexport.InteractivePlan
import com.hcexport.InteractiveResults
import com.hcexport.LiveItem
import com.hcexport.LivePlan
import com.hcexport.LiveSection
import com.hcexport.LiveWorkout
import com.hcexport.MetricDef
import com.hcexport.MetricKind
import com.hcexport.TimeValue
import com.hcexport.formatLiveClock
import com.hcexport.formatLiveElapsed
import com.hcexport.metricHint
import com.hcexport.shared.DEVICE_WATCH
import com.hcexport.shared.LiveSettingsDto
import com.hcexport.shared.Snapshot
import com.hcexport.wear.WatchStore
import com.hcexport.wear.live.LiveCues
import com.hcexport.wear.live.LivePhase
import com.hcexport.wear.live.LiveSession
import com.hcexport.wear.live.WatchCues
import com.hcexport.wear.live.WatchLiveSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/** Survives the screen: closing the phone's copy of the session must still be
 *  written while this screen is being torn down. */
private val liveCloseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Composable
fun LiveScreen(
    snapshot: Snapshot?,
    date: String,
    typeKey: String,
    join: Boolean,
    onExit: () -> Unit,
    onRefresh: () -> Unit,
) {
    val planDto = snapshot?.plans?.firstOrNull { it.date == date && it.typeKey == typeKey }
    val plan = planDto?.let { InteractivePlan.fromJson(it.planJson) }
    if (plan == null || planDto == null) {
        MissingPlan(onExit, onRefresh)
        return
    }
    LiveRunner(
        plan = plan,
        planUpdatedAt = planDto.updatedAt,
        initialResultsJson = planDto.resultsJson,
        settings = snapshot?.settings ?: LiveSettingsDto(),
        date = date,
        typeKey = typeKey,
        join = join,
        onExit = onExit,
        onRefresh = onRefresh,
    )
}

@Composable
private fun MissingPlan(onExit: () -> Unit, onRefresh: () -> Unit) {
    WatchBackground(accent = Amber) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("No plan here", style = TitleStyle, color = Snow, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(
                "The watch hasn't received this workout's AI plan yet. Sync on the phone, then pull it here.",
                style = CaptionStyle,
                color = Mist,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            StartButton("Pull from phone", Amber, onRefresh)
            Spacer(Modifier.height(6.dp))
            SmallAction("Back", Mist, Modifier.fillMaxWidth(), onExit)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LiveRunner(
    plan: InteractivePlan,
    planUpdatedAt: Long,
    initialResultsJson: String,
    settings: LiveSettingsDto,
    date: String,
    typeKey: String,
    join: Boolean,
    onExit: () -> Unit,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val cues = remember(settings.vibration) {
        if (settings.vibration) WatchCues(context) else LiveCues.None
    }
    DisposableEffect(cues) {
        onDispose { (cues as? WatchCues)?.release() }
    }
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val live = remember(plan, settings.intervalSeconds) {
        LiveWorkout.build(plan, settings.intervalSeconds)
    }
    val pendingRun = remember(date, typeKey, planUpdatedAt) {
        WatchLiveSync.loadRun(context, date, typeKey, planUpdatedAt)
    }
    val session = remember(live) {
        val results = pendingRun
            ?.let { InteractiveResults.fromJson(it.resultsJson) }
            ?: InteractiveResults.fromJson(initialResultsJson)
        LiveSession(live, settings.leadInSeconds, cues, initialResults = results).apply {
            // A resumed run publishes itself again, so a phone that thought
            // the workout was over can rejoin it at this stage.
            pendingRun?.let {
                adopt(it)
                restoreBase(it.baseResultsJson)
            }
        }
    }
    var lastAdopted by remember { mutableLongStateOf(0L) }
    var showMap by remember { mutableStateOf(false) }
    var showEndConfirm by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var syncNote by remember { mutableStateOf<String?>(null) }

    fun finishValues() {
        WatchLiveSync.publishResults(
            context, date, typeKey,
            InteractiveResults.toJson(session.results),
            planUpdatedAt,
            InteractiveResults.toJson(session.baseResults),
        )
        WatchLiveSync.clearRun(context)
    }

    /** The athlete ended it here: tell the phone and stop. */
    fun leave() {
        if (leaving) return
        leaving = true
        session.finalizeBeforeExit()
        val dto = session.toDto(date, typeKey, planUpdatedAt)
        liveCloseScope.launch {
            finishValues()
            WatchLiveSync.closeSession(context, dto)
        }
        onExit()
    }

    // Any way off this screen — the pause menu, a swipe back, the system
    // back — ends the workout on the phone too. A remote end sets `leaving`
    // first, so this does not publish a second close.
    DisposableEffect(Unit) {
        onDispose {
            if (!leaving) {
                leaving = true
                session.finalizeBeforeExit()
                val dto = session.toDto(date, typeKey, planUpdatedAt)
                liveCloseScope.launch {
                    finishValues()
                    WatchLiveSync.closeSession(context, dto)
                }
            }
        }
    }

    // The one clock.
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
            session.tick((now - last).coerceAtLeast(0L))
            last = now
        }
    }

    // The phone moved: take its stage and values. A close is terminal.
    val remote by WatchLiveSync.remote.collectAsState()
    LaunchedEffect(remote?.sessionId, remote?.updatedAt, remote?.open, join) {
        val r = remote ?: return@LaunchedEffect
        if (r.date != date || r.typeKey != typeKey) return@LaunchedEffect
        if (r.origin == DEVICE_WATCH) return@LaunchedEffect
        // A record from before this session began is history — in particular
        // the close of the previous session must not kill a fresh one.
        if (!join && r.updatedAt <= session.syncStartedAt) return@LaunchedEffect
        if (!r.open) {
            if (leaving) return@LaunchedEffect
            leaving = true
            session.finalizeBeforeExit()
            liveCloseScope.launch { finishValues() }
            onExit()
            return@LaunchedEffect
        }
        if (r.updatedAt <= lastAdopted) return@LaunchedEffect
        val stale = System.currentTimeMillis() - r.updatedAt > 10 * 60_000L
        if (stale && !join) return@LaunchedEffect
        lastAdopted = r.updatedAt
        session.adopt(r)
        session.lastSyncFingerprint = session.syncFingerprint()
        // Following the phone is still being in the workout; answer a
        // "sync from watch" request with this stage.
        WatchLiveSync.local = r.copy(origin = DEVICE_WATCH)
        WatchLiveSync.localActive = true
        syncNote = null
        withContext(Dispatchers.IO) {
            WatchStore.applyResults(date, typeKey, r.resultsJson, r.updatedAt)
            WatchLiveSync.saveRun(context, session, date, typeKey, planUpdatedAt)
        }
    }

    // This session moved: tell the phone and keep a resumable copy.
    val fingerprint = session.syncFingerprint()
    LaunchedEffect(fingerprint) {
        if (leaving) return@LaunchedEffect
        if (fingerprint == session.lastSyncFingerprint) return@LaunchedEffect
        delay(250)
        // The end may have landed during the debounce; never publish an open
        // stage after the close.
        if (leaving) return@LaunchedEffect
        session.lastSyncFingerprint = fingerprint
        val resultsJson = InteractiveResults.toJson(session.results)
        withContext(Dispatchers.IO) {
            WatchLiveSync.publish(context, session.toDto(date, typeKey, planUpdatedAt))
            WatchLiveSync.publishResults(
                context, date, typeKey, resultsJson, planUpdatedAt,
                InteractiveResults.toJson(session.baseResults),
            )
            WatchStore.applyResults(date, typeKey, resultsJson, System.currentTimeMillis())
            WatchLiveSync.saveRun(context, session, date, typeKey, planUpdatedAt)
        }
    }

    // The clocks are not part of the fingerprint; keep them on disk too.
    LaunchedEffect(Unit) {
        while (!leaving) {
            delay(3_000)
            if (leaving) break
            withContext(Dispatchers.IO) {
                WatchLiveSync.saveRun(context, session, date, typeKey, planUpdatedAt)
            }
        }
    }

    val requestResult by WatchLiveSync.requestResult.collectAsState()
    LaunchedEffect(requestResult) {
        if (requestResult == "none") {
            syncNote = "The phone isn't in a workout"
            WatchLiveSync.clearRequestResult()
        }
    }

    fun syncToPhone() {
        syncNote = "Asking the phone…"
        scope.launch {
            if (!WatchLiveSync.requestPhoneState(context)) {
                syncNote = "Phone not connected"
                return@launch
            }
            delay(5_000)
            if (syncNote == "Asking the phone…") syncNote = "No answer from the phone"
        }
    }

    BackHandler {
        when {
            showMap -> showMap = false
            showEndConfirm -> showEndConfirm = false
            session.phase == LivePhase.DONE -> leave()
            else -> showEndConfirm = true
        }
    }

    val accent = sectionAccent(session.section.kind)
    WatchBackground(accent = accent) {
        when {
            showMap -> LiveMap(
                live = live,
                session = session,
                accent = accent,
                onClose = { showMap = false },
                onFinishSection = {
                    session.finishCurrentSection()
                    showMap = false
                },
            )

            showEndConfirm -> EndConfirm(
                onKeepGoing = { showEndConfirm = false },
                onEnd = {
                    showEndConfirm = false
                    leave()
                },
            )

            session.phase == LivePhase.DONE -> DoneScreen(
                live = live,
                session = session,
                onDone = ::leave,
            )

            else -> Column(Modifier.fillMaxSize()) {
                TopRow(
                    elapsedMs = session.sessionElapsedMs,
                    paused = session.paused,
                    accent = accent,
                    onEnd = { showEndConfirm = true },
                    onPause = {
                        if (session.phase == LivePhase.ACTIVE ||
                            session.phase == LivePhase.GET_READY
                        ) {
                            session.paused = !session.paused
                        }
                    },
                )
                syncNote?.let {
                    Text(
                        it,
                        modifier = Modifier.fillMaxWidth().padding(top = 1.dp),
                        style = TinyLabelStyle,
                        color = Periwinkle,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (session.phase) {
                        LivePhase.OVERVIEW -> SectionOverview(
                            session = session,
                            accent = accent,
                            onStart = session::startCurrentSection,
                            onSkip = session::skipCurrentSection,
                        )

                        LivePhase.GET_READY -> GetReady(
                            session = session,
                            leadInSeconds = settings.leadInSeconds.coerceAtLeast(1),
                            accent = accent,
                            onSkip = session::skipLeadIn,
                        )

                        LivePhase.ACTIVE -> ActiveStep(
                            session = session,
                            accent = accent,
                        )

                        LivePhase.DONE -> Unit
                    }
                }
                if (session.phase == LivePhase.ACTIVE) {
                    BottomActions(
                        session = session,
                        accent = accent,
                    )
                }
            }
        }

        if (session.paused && !showMap && !showEndConfirm &&
            session.phase != LivePhase.DONE
        ) {
            PausedOverlay(
                accent = accent,
                note = syncNote,
                onResume = { session.paused = false },
                onMap = { showMap = true },
                onSync = ::syncToPhone,
                onSkipTimer = {
                    session.paused = false
                    session.skipLiveTimer()
                },
                onEnd = { showEndConfirm = true },
            )
        }
    }
}

// ── Top and bottom bars ─────────────────────────────────────────────────────

@Composable
private fun TopRow(
    elapsedMs: Long,
    paused: Boolean,
    accent: Color,
    onEnd: () -> Unit,
    onPause: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (paused) Warning else accent)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            if (paused) "PAUSED" else formatLiveElapsed(elapsedMs),
            style = TinyLabelStyle,
            color = if (paused) Warning else Slate,
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .height(24.dp)
                .clip(RoundedCornerShape(50))
                .background(Danger.copy(alpha = 0.14f))
                .border(1.dp, Danger.copy(alpha = 0.42f), RoundedCornerShape(50))
                .clickable(onClick = onEnd)
                .padding(horizontal = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("END", style = TinyLabelStyle, color = Danger, maxLines = 1)
        }
        Spacer(Modifier.width(3.dp))
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable(onClick = onPause),
            contentAlignment = Alignment.Center,
        ) {
            if (paused) {
                Text("▶", style = TitleSmallStyle, color = accent)
            } else {
                PauseGlyph()
            }
        }
    }
}

@Composable
private fun PauseGlyph() {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(2) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Mist)
            )
        }
    }
}

@Composable
private fun BottomActions(session: LiveSession, accent: Color) {
    val section = session.section
    val intervalSet = !section.roundTimed && section.intervalSeconds != null
    val roundFinished = section.roundTimed && (session.roundWorkDone || session.resting)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 34.dp, end = 34.dp, top = 2.dp, bottom = 20.dp),
    ) {
        when {
            roundFinished && session.resting -> {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    StartButton("Skip rest", Periwinkle, session::skipRest)
                }
            }

            roundFinished -> {
                Text(
                    "ROUND ${session.currentRound + 1} DONE",
                    modifier = Modifier.fillMaxWidth(),
                    style = TinyLabelStyle,
                    color = Success,
                    textAlign = TextAlign.Center,
                )
            }

            session.resting -> {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    StartButton("Skip rest", Periwinkle, session::skipRest)
                }
            }

            else -> {
                // The round bottom is narrow: one centred action, no chevrons
                // under the bezel. The map (pause → Map) moves between sets.
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DoneButton(
                        session = session,
                        intervalSet = intervalSet,
                        accent = accent,
                        modifier = Modifier.width(120.dp),
                    )
                }
                val hint = when {
                    session.reviewing -> "VIEWING A PAST SET"
                    session.previewing -> "PREVIEWING"
                    intervalSet && session.itemDone -> "WINDOW STILL RUNNING"
                    else -> ""
                }
                if (hint.isNotBlank()) {
                    Text(
                        hint,
                        modifier = Modifier.fillMaxWidth(),
                        style = TinyLabelStyle,
                        color = Slate,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun DoneButton(
    session: LiveSession,
    intervalSet: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val enabled = session.onLiveItem && !session.resting && !session.roundWorkDone
    val completed = intervalSet && session.itemDone
    val background = when {
        !enabled -> CardHigh
        completed -> Success
        else -> Success
    }
    val label = when {
        !enabled -> "COMPLETED"
        completed -> "COMPLETED ✓"
        else -> "DONE"
    }
    Box(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(50))
            .background(background)
            .then(
                if (enabled) {
                    Modifier.clickable {
                        if (intervalSet) session.toggleItemDone() else session.completeAndAdvance()
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = TitleSmallStyle,
            color = if (enabled) Abyss else Slate,
            maxLines = 1,
        )
    }
}

@Composable
private fun Chevron(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = TitleStyle, color = if (enabled) Mist else Hairline)
    }
}

// ── Overview ────────────────────────────────────────────────────────────────

@Composable
private fun SectionOverview(
    session: LiveSession,
    accent: Color,
    onStart: () -> Unit,
    onSkip: () -> Unit,
) {
    val section = session.section
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(4.dp))
        Text(
            if (session.isLastSection) "LAST SESSION" else "NEXT SESSION",
            style = TinyLabelStyle,
            color = accent,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            section.title,
            style = TitleStyle,
            color = Snow,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            liveShape(section),
            style = CaptionStyle,
            color = Mist,
            textAlign = TextAlign.Center,
        )
        if (section.notes.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                section.notes,
                style = CaptionStyle,
                color = Slate,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
        StartButton("Start", accent, onStart)
        Spacer(Modifier.height(5.dp))
        Text(
            "Skip this section",
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onSkip)
                .padding(horizontal = 10.dp, vertical = 3.dp),
            style = TinyLabelStyle,
            color = Slate,
        )
    }
}

private fun liveShape(section: LiveSection): String {
    val cap = section.timeCapSeconds
    val interval = section.intervalSeconds
    return when {
        section.isAmrap && cap != null ->
            "AMRAP · ${formatLiveClock(cap * 1000L)}"
        section.roundTimed ->
            "${section.roundCount} rounds of ${formatLiveClock((section.roundSeconds ?: 0) * 1000L)}"
        section.roundCount > 0 -> "${section.roundCount} rounds · ${section.items.size} movements"
        interval != null ->
            "${section.items.size} sets · every ${formatLiveClock(interval * 1000L)}"
        section.items.isNotEmpty() -> "${section.items.size} sets"
        else -> "A section to read"
    }
}

// ── Get ready / active ──────────────────────────────────────────────────────

@Composable
private fun GetReady(
    session: LiveSession,
    leadInSeconds: Int,
    accent: Color,
    onSkip: () -> Unit,
) {
    val item = session.item
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("GET READY", style = TinyLabelStyle, color = accent)
        Spacer(Modifier.height(4.dp))
        LiveRing(
            position = item?.position.orEmpty(),
            time = ((session.leadInRemainingMs + 999) / 1000).toString(),
            label = "seconds",
            fraction = (
                session.leadInRemainingMs / 1000f / leadInSeconds.toFloat()
                ).coerceIn(0f, 1f),
            warning = session.leadInRemainingMs in 1..3_000,
            accent = accent,
            diameter = 104.dp,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            item?.name.orEmpty(),
            style = TitleSmallStyle,
            color = Snow,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!item?.target.isNullOrBlank()) {
            Text(
                item?.target.orEmpty(),
                style = CaptionStyle,
                color = Mist,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Tap to start now",
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onSkip)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            style = TinyLabelStyle,
            color = Slate,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ActiveStep(
    session: LiveSession,
    accent: Color,
) {
    val section = session.section
    val item = session.item
    val roundFinished = section.roundTimed && (session.roundWorkDone || session.resting)
    val metrics = item?.metrics ?: section.metrics
    val selectable = metrics.filter { it.kind != MetricKind.CHECK && it.kind != MetricKind.TEXT }
    var activeKey by remember(item?.scope, section.index) {
        mutableStateOf(
            selectable.firstOrNull { it.kind == MetricKind.WEIGHT }?.key
                ?: selectable.firstOrNull()?.key
        )
    }
    val active = selectable.firstOrNull { it.key == activeKey } ?: selectable.firstOrNull()
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    val compact = screenHeight < 235
    val ringSize = if (compact) 60.dp else 96.dp

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val timer = timerState(session, section)
            if (timer != null) {
                LiveRing(
                    position = if (section.isAmrap) {
                        "Round ${session.roundsDone + 1}"
                    } else {
                        item?.position.orEmpty()
                    },
                    time = timer.time,
                    label = timer.label,
                    fraction = timer.fraction,
                    warning = timer.warning,
                    accent = accent,
                    diameter = ringSize,
                )
            } else {
                // Untimed work: the ring becomes a progress marker.
                LiveRing(
                    position = item?.position.orEmpty(),
                    time = if (section.items.isEmpty()) "—" else "${session.itemIndex + 1}/${section.items.size}",
                    label = when {
                        section.items.isEmpty() -> "section"
                        section.roundCount > 0 -> "rounds"
                        else -> "sets"
                    },
                    fraction = if (section.items.isEmpty()) 0f else {
                        (session.itemIndex + 1f) / section.items.size
                    },
                    warning = false,
                    accent = accent,
                    diameter = ringSize,
                )
            }

            if (roundFinished) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (session.resting) "REST" else "ROUND COMPLETE",
                    style = TitleStyle,
                    color = if (session.resting) Periwinkle else Success,
                )
                Text(
                    if (session.resting) {
                        "Round ${session.currentRound + 2} up next"
                    } else {
                        "The round clock is still running"
                    },
                    style = CaptionStyle,
                    color = Mist,
                )
            } else {
                Spacer(Modifier.height(3.dp))
                val target = item?.target.orEmpty()
                val showTarget = target.isNotBlank() &&
                    !(selectable.any { it.kind == MetricKind.REPS } &&
                        Regex("""^\d+\s*(?:reps?|repetitions?)?$""").matches(
                            target.trim().lowercase()
                        ))
                Text(
                    if (showTarget) "${item?.name ?: section.title} · $target"
                    else (item?.name ?: section.title),
                    style = TitleSmallStyle.copy(fontSize = if (compact) 15.sp else 16.sp),
                    color = Snow,
                    textAlign = TextAlign.Center,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selectable.isNotEmpty() && active != null) {
                    Spacer(Modifier.height(3.dp))
                    MetricStepper(
                        session = session,
                        item = item,
                        metrics = selectable,
                        active = active,
                        accent = accent,
                        onSelect = { activeKey = it.key },
                    )
                } else if (metrics.any { it.kind == MetricKind.CHECK }) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Check it off with Done",
                        style = TinyLabelStyle,
                        color = Slate,
                    )
                }
            }

            if (session.reviewing) {
                Text(
                    "A completed set · values still editable",
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable(onClick = session::resumeLive),
                    style = TinyLabelStyle,
                    color = Periwinkle,
                )
            }
            if (session.previewing) {
                Text(
                    "Upcoming set · the clock is running",
                    modifier = Modifier.padding(top = 4.dp),
                    style = TinyLabelStyle,
                    color = Slate,
                )
            }
        }

    }
}

private fun computeTimer(session: LiveSession, section: LiveSection): TimerState? {
    val cap = section.timeCapSeconds
    val interval = section.intervalSeconds
    if (session.resting) {
        val remaining = session.restRemainingMs.coerceAtLeast(0L)
        return TimerState(
            "rest",
            formatLiveClock(remaining),
            (remaining / 1000f / (section.restSeconds ?: 1)).coerceIn(0f, 1f),
            session.restRemainingMs in 1..5_000,
        )
    }
    return when {
        section.roundTimed -> {
            val remaining = session.intervalRemainingMs.coerceAtLeast(0L)
            TimerState(
                "round time",
                formatLiveClock(remaining),
                (remaining / 1000f / (section.roundSeconds ?: 1)).coerceIn(0f, 1f),
                session.intervalRemainingMs in 1..5_000,
            )
        }
        interval != null && section.items.isNotEmpty() -> {
            val remaining = session.intervalRemainingMs.coerceAtLeast(0L)
            TimerState(
                "next set",
                formatLiveClock(remaining),
                (remaining / 1000f / interval).coerceIn(0f, 1f),
                session.intervalRemainingMs in 1..5_000,
            )
        }
        section.clocked && cap != null -> {
            val remaining = (cap * 1000L - session.sectionElapsedMs).coerceAtLeast(0L)
            TimerState(
                "cap left",
                formatLiveClock(remaining),
                (remaining / 1000f / cap).coerceIn(0f, 1f),
                remaining in 1..5_000,
            )
        }
        section.clocked -> TimerState(
            "elapsed",
            formatLiveElapsed(session.sectionElapsedMs),
            0f,
            false,
        )
        else -> null
    }
}

private data class TimerState(
    val label: String,
    val time: String,
    val fraction: Float,
    val warning: Boolean,
)

@Composable
private fun timerState(session: LiveSession, section: LiveSection): TimerState? =
    computeTimer(session, section)

@Composable
private fun LiveRing(
    position: String,
    time: String,
    label: String,
    fraction: Float,
    warning: Boolean,
    accent: Color,
    diameter: Dp,
) {
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Snow.copy(alpha = 0.06f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (fraction > 0f) {
                drawArc(
                    color = accent.copy(alpha = 0.10f),
                    startAngle = -90f,
                    sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset - 5.dp.toPx(), inset - 5.dp.toPx()),
                    size = Size(
                        size.width - stroke + 10.dp.toPx(),
                        size.height - stroke + 10.dp.toPx(),
                    ),
                    style = Stroke(width = stroke + 10.dp.toPx(), cap = StrokeCap.Round),
                )
                drawArc(
                    color = if (warning) Danger else accent,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (position.isNotBlank()) {
                Text(
                    position.uppercase(),
                    style = TinyLabelStyle,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val timeSize = when {
                diameter < 90.dp -> if (time.length > 5) 26.sp else 30.sp
                time.length > 5 -> 32.sp
                else -> 38.sp
            }
            Text(
                time,
                style = ClockStyle.copy(fontSize = timeSize, lineHeight = timeSize),
                color = if (warning) Danger else Snow,
                maxLines = 1,
            )
            Text(label, style = TinyLabelStyle, color = Slate, maxLines = 1)
        }
    }
}

// ── Values ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MetricStepper(
    session: LiveSession,
    item: LiveItem?,
    metrics: List<MetricDef>,
    active: MetricDef,
    accent: Color,
    onSelect: (MetricDef) -> Unit,
) {
    val value = when {
        item != null -> session.resolvedValue(item, active)
        else -> session.resolvedValueForSection(active)
    }
    fun commit(raw: String) {
        if (item != null) session.setValue(item, active, raw) else session.setSectionValue(active, raw)
    }
    fun nudge(direction: Int) {
        val step = when (active.kind) {
            MetricKind.WEIGHT ->
                if (active.unit.equals("lb", true) || active.unit.equals("lbs", true)) 5.0 else 2.5
            MetricKind.REPS -> 1.0
            MetricKind.CALORIES -> 5.0
            MetricKind.DISTANCE -> 50.0
            MetricKind.TIME -> 5.0
            else -> 1.0
        }
        if (active.kind == MetricKind.TIME) {
            val (minutes, seconds) = TimeValue.parse(value) ?: (0 to 0)
            val total = (minutes * 60 + seconds + step * direction).coerceAtLeast(0.0)
            commit(TimeValue.format((total / 60).toInt(), (total % 60).toInt()))
            return
        }
        val current = value.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
        val next = current + step * direction
        commit(stepValue(next))
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(active.key) {
        runCatching { focusRequester.requestFocus() }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .onRotaryScrollEvent { event ->
                nudge(if (event.verticalScrollPixels > 0) 1 else -1)
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            NudgeButton("−", accent) { nudge(-1) }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        if (metrics.size > 1) {
                            val index = metrics.indexOfFirst { it.key == active.key }
                            onSelect(metrics[(index + 1) % metrics.size])
                        }
                    }
                    .padding(vertical = 1.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    active.label.uppercase() +
                        if (metrics.size > 1) "  ⇄" else "",
                    style = TinyLabelStyle,
                    color = accent.copy(alpha = 0.9f),
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        value.ifBlank {
                            if (active.kind == MetricKind.TIME) "0:00" else "—"
                        },
                        style = ClockSmallStyle.copy(fontSize = 22.sp, lineHeight = 24.sp),
                        color = Snow,
                        maxLines = 1,
                    )
                    if (active.unit.isNotBlank()) {
                        Spacer(Modifier.width(2.dp))
                        Text(
                            active.unit,
                            modifier = Modifier.padding(bottom = 3.dp),
                            style = TinyLabelStyle,
                            color = Slate,
                        )
                    }
                }
            }
            NudgeButton("+", accent) { nudge(1) }
        }
    }
}

private fun stepValue(raw: Double): String {
    if (raw <= 0.0) return ""
    return if (raw == Math.floor(raw) && !raw.isInfinite()) {
        raw.roundToInt().toString()
    } else {
        String.format(Locale.US, "%.1f", raw)
    }
}

@Composable
private fun NudgeButton(label: String, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.13f))
            .border(1.dp, accent.copy(alpha = 0.30f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TitleStyle, color = accent)
    }
}

// ── Map, pause, end, done ───────────────────────────────────────────────────

@Composable
private fun PausedOverlay(
    accent: Color,
    note: String?,
    onResume: () -> Unit,
    onMap: () -> Unit,
    onSync: () -> Unit,
    onSkipTimer: () -> Unit,
    onEnd: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Abyss.copy(alpha = 0.93f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("PAUSED", style = LabelStyle, color = Warning)
            if (note != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    note,
                    style = TinyLabelStyle,
                    color = Periwinkle,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.height(6.dp))
            StartButton("Resume", accent, onResume)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                SmallAction("Sync to phone", Periwinkle, Modifier.weight(1f), onSync)
                SmallAction("Map", Mist, Modifier.weight(1f), onMap)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                SmallAction("Skip timer", Mist, Modifier.weight(1f), onSkipTimer)
                SmallAction("End workout", Danger, Modifier.weight(1f), onEnd)
            }
        }
    }
}

@Composable
private fun SmallAction(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.30f), RoundedCornerShape(50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TinyLabelStyle, color = color, maxLines = 1)
    }
}

@Composable
private fun TinyLink(label: String, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 2.dp),
        style = TinyLabelStyle,
        color = Slate,
    )
}

@Composable
private fun EndConfirm(onKeepGoing: () -> Unit, onEnd: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Obsidian),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("End live workout?", style = TitleStyle, color = Snow, textAlign = TextAlign.Center)
            Spacer(Modifier.height(3.dp))
            Text(
                "Everything entered is kept, and the journal marks the last set you reached.",
                style = CaptionStyle,
                color = Mist,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            StartButton("Keep going", Teal, onKeepGoing)
            Spacer(Modifier.height(4.dp))
            SmallAction("End workout", Danger, Modifier.fillMaxWidth(), onEnd)
        }
    }
}

@Composable
private fun DoneScreen(live: LivePlan, session: LiveSession, onDone: () -> Unit) {
    val itemCount = live.sections.sumOf { it.items.size }
    val touched = session.results.count { (scope, values) ->
        !scope.startsWith("m:") && values.values.any { it.isNotBlank() }
    }
    WatchBackground(accent = Success) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("WORKOUT COMPLETE", style = LabelStyle, color = Success)
            Spacer(Modifier.height(3.dp))
            Text(
                formatLiveElapsed(session.sessionElapsedMs),
                style = ClockStyle,
                color = Snow,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "$touched of $itemCount rows touched",
                style = CaptionStyle,
                color = Mist,
            )
            Spacer(Modifier.height(10.dp))
            StartButton("Done", Success, onDone)
        }
    }
}

@Composable
private fun LiveMap(
    live: LivePlan,
    session: LiveSession,
    accent: Color,
    onClose: () -> Unit,
    onFinishSection: () -> Unit,
) {
    val state = rememberScalingLazyListState()
    WatchBackground(accent = accent) {
        ScalingLazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            autoCentering = null,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BackGlyph(onClose)
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text("THE WHOLE WORKOUT", style = TinyLabelStyle, color = accent)
                        Text(
                            live.title,
                            style = TitleSmallStyle,
                            color = Snow,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (session.phase == LivePhase.ACTIVE && session.section.clocked) {
                item { SmallAction("Finish this section", accent, Modifier.fillMaxWidth(), onFinishSection) }
            }
            live.sections.forEachIndexed { sectionIndex, section ->
                item(key = "s$sectionIndex") {
                    val current = sectionIndex == session.sectionIndex
                    Column(Modifier.padding(top = 5.dp, start = 3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            sectionIndex < session.sectionIndex -> Success
                                            current -> sectionAccent(section.kind)
                                            else -> Hairline
                                        }
                                    )
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                section.title,
                                modifier = Modifier.weight(1f),
                                style = TinyLabelStyle,
                                color = if (current) Snow else Slate,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (section.roundCount > 0) "${section.roundCount} RDS" else "${section.items.size} SETS",
                                style = TinyLabelStyle,
                                color = Slate,
                            )
                        }
                    }
                }
                section.items.forEachIndexed { itemIndex, item ->
                    val done = sectionIndex < session.sectionIndex ||
                        (currentSection(session, sectionIndex) && itemIndex < session.liveItemIndex) ||
                        (currentSection(session, sectionIndex) && itemIndex == session.liveItemIndex && session.itemDone)
                    val liveItem = currentSection(session, sectionIndex) &&
                        itemIndex == session.liveItemIndex
                    item(key = "i$sectionIndex:$itemIndex") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    session.jumpTo(sectionIndex, itemIndex)
                                    onClose()
                                }
                                .padding(horizontal = 5.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            done -> Success
                                            liveItem -> sectionAccent(section.kind).copy(alpha = 0.30f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        when {
                                            done -> Success
                                            liveItem -> sectionAccent(section.kind)
                                            else -> Hairline
                                        },
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (done) Text("✓", style = TinyLabelStyle, color = Abyss)
                            }
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.name,
                                    style = CaptionStyle,
                                    color = if (liveItem) Snow else Mist,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (item.position.isNotBlank()) {
                                    Text(item.position, style = TinyLabelStyle, color = Slate)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                SmallAction("Close map", accent, Modifier.fillMaxWidth(), onClose)
            }
        }
    }
}

private fun currentSection(session: LiveSession, sectionIndex: Int): Boolean =
    session.sectionIndex == sectionIndex
