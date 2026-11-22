package com.hcexport.ui

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hcexport.AiClient
import com.hcexport.AiConfigStore
import com.hcexport.AiDisplay
import com.hcexport.AiKeyStore
import com.hcexport.AutoRecords
import com.hcexport.InteractiveDefaults
import com.hcexport.InteractiveExercise
import com.hcexport.InteractiveKeys
import com.hcexport.InteractivePlan
import com.hcexport.InteractiveReached
import com.hcexport.InteractiveResults
import com.hcexport.InteractiveRounds
import com.hcexport.InteractiveRows
import com.hcexport.InteractiveSection
import com.hcexport.InteractiveStore
import com.hcexport.JournalAiPlan
import com.hcexport.JournalFeed
import com.hcexport.LiveScoring
import com.hcexport.LiveWorkout
import com.hcexport.MetricDef
import com.hcexport.MetricKind
import com.hcexport.R
import com.hcexport.RecordStore
import com.hcexport.TimeValue
import com.hcexport.WearPushWorker
import com.hcexport.Workout
import com.hcexport.WorkoutStore
import com.hcexport.WorkoutText
import com.hcexport.metricHint
import com.hcexport.ui.theme.Abyss
import com.hcexport.ui.theme.Amber
import com.hcexport.ui.theme.CardHigh
import com.hcexport.ui.theme.Danger
import com.hcexport.ui.theme.Hairline
import com.hcexport.ui.theme.Mist
import com.hcexport.ui.theme.Periwinkle
import com.hcexport.ui.theme.Slate
import com.hcexport.ui.theme.Snow
import com.hcexport.ui.theme.Success
import com.hcexport.ui.theme.Teal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SaveState { Saved, Saving }

/**
 * The live state behind an AI workout: the plan, the values entered into it,
 * and the loading/saving/generating around them.
 *
 * It is shared so the journal entry and the full-screen editor are one
 * presentation of a workout rather than two: whichever is on screen edits the
 * same stored plan, and the journal can read the numbers as they are typed.
 */
@Stable
/** The Workouts tab's AI view is a preview: it shows the plan, never edits it. */
internal val LocalAiPlanReadOnly = compositionLocalOf { false }

/** The plan row a record link asked to land on, if it is still in the plan. */
internal val LocalAiFocusScope = compositionLocalOf<String?> { null }

class AiWorkoutState internal constructor(
    private val context: Context,
    val date: String,
    val typeKey: String,
    /** Outlives the composition so the last keystrokes still reach the disk. */
    private val writeScope: CoroutineScope,
) {
    var workout by mutableStateOf<Workout?>(null)
        private set
    var plan by mutableStateOf<InteractivePlan?>(null)
        private set
    var results by mutableStateOf<Map<String, Map<String, String>>>(emptyMap())
        private set
    var modelId by mutableStateOf("")
        private set
    var loading by mutableStateOf(true)
        private set
    var generating by mutableStateOf(false)
        private set
    var needsKey by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var saveState by mutableStateOf(SaveState.Saved)
        internal set
    var showOriginal by mutableStateOf(false)
        private set

    /**
     * Bumped after a save reconciles the workout's auto records, so the
     * journal's Records section re-reads them while the values are typed.
     */
    var recordsRevision by mutableStateOf(0)
        private set

    /** True when the archive has this workout — generating a plan needs it. */
    val canGenerate: Boolean get() = workout != null

    /** Live headline numbers: sets logged and the volume they add up to. */
    val totals: JournalAiPlan? get() = plan?.let { JournalFeed.aiPlan(it, results) }

    fun toggleOriginal() {
        showOriginal = !showOriginal
    }

    fun setValue(scope: String, metricKey: String, value: String) {
        results = InteractiveResults.set(results, scope, metricKey, value)
        saveState = SaveState.Saving
    }

    /**
     * Replace the whole value map. A live session writes a section's boundary
     * and score as one batch rather than a metric at a time.
     */
    internal fun replaceResults(next: Map<String, Map<String, String>>) {
        results = next
        saveState = SaveState.Saving
    }

    suspend fun load(workout: Workout?, lookupWorkout: Boolean, autoGenerate: Boolean) {
        this.workout = workout ?: if (lookupWorkout) {
            withContext(Dispatchers.IO) {
                WorkoutStore.getByDate(context, date).firstOrNull { it.typeKey == typeKey }
            }
        } else {
            null
        }
        val session = withContext(Dispatchers.IO) { InteractiveStore.get(context, date, typeKey) }
        var parsed: InteractivePlan? = null
        if (session != null) {
            parsed = InteractivePlan.fromJson(session.planJson)
            if (parsed == null) {
                withContext(Dispatchers.IO) { InteractiveStore.delete(context, date, typeKey) }
            } else {
                results = InteractiveResults.fromJson(session.resultsJson)
                modelId = session.model.ifBlank { AiConfigStore.getActive(context).displayName }
            }
        }
        plan = parsed
        loading = false
        if (parsed == null && this.workout != null && autoGenerate) generate()
    }

    suspend fun generate() {
        val current = workout ?: return
        val provider = AiConfigStore.activeProviderKey(context)
        if (!AiKeyStore.hasKey(context, provider)) {
            needsKey = true
            return
        }
        generating = true
        error = null
        needsKey = false
        val result = AiClient.generatePlan(context, current)
        generating = false
        result.onSuccess { generated ->
            val usedModel = generated.displayName
            withContext(Dispatchers.IO) {
                InteractiveStore.savePlan(
                    context, date, typeKey, generated.plan.toJson().toString(), usedModel,
                )
                // The previous values are gone, so their auto records go with them.
                runCatching { RecordStore.syncAutoWeights(context, date, typeKey, emptyList()) }
            }
            plan = generated.plan
            results = emptyMap()
            modelId = usedModel
            saveState = SaveState.Saved
            WearPushWorker.enqueue(context, delayMs = 1_500L)
            DataRefresh.bump()
        }.onFailure {
            error = it.message ?: "Could not generate the plan."
        }
    }

    /** Back from Settings with a key added: retry the generation that asked. */
    suspend fun retryAfterResume() {
        if (loading || generating || plan != null || workout == null || !needsKey) return
        if (AiKeyStore.hasKey(context, AiConfigStore.activeProviderKey(context))) {
            needsKey = false
            generate()
        }
    }

    suspend fun save() {
        val currentPlan = plan ?: return
        val currentResults = results
        val payload = InteractiveResults.toJson(currentResults)
        saveState = SaveState.Saving
        withContext(Dispatchers.IO) {
            InteractiveStore.saveResults(context, date, typeKey, payload)
            // Strength work is a rep-max attempt: keep this workout's records
            // in step with the values just saved.
            runCatching {
                RecordStore.syncAutoWeights(
                    context, date, typeKey,
                    AutoRecords.candidates(currentPlan, currentResults),
                )
            }
        }
        recordsRevision += 1
        saveState = SaveState.Saved
        // The watch runs the same plan; give it the values just written.
        WearPushWorker.enqueue(context, delayMs = 1_500L)
    }

    /** Leave: finish the write the debounce may still be holding. */
    fun flush() {
        val currentPlan = plan ?: return
        val currentResults = results
        val payload = InteractiveResults.toJson(currentResults)
        writeScope.launch {
            InteractiveStore.saveResults(context, date, typeKey, payload)
            runCatching {
                RecordStore.syncAutoWeights(
                    context, date, typeKey,
                    AutoRecords.candidates(currentPlan, currentResults),
                )
            }
        }
    }
}

/**
 * Loads the stored plan for [date]/[typeKey] and persists edits as they are
 * typed. [workout] is the archive row that generation needs; pass
 * [lookupWorkout] when the caller does not already hold it.
 */
@Composable
fun rememberAiWorkoutState(
    date: String,
    typeKey: String,
    workout: Workout? = null,
    autoGenerate: Boolean = false,
    lookupWorkout: Boolean = false,
): AiWorkoutState {
    val context = LocalContext.current
    val writeScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    val state = remember(date, typeKey) {
        AiWorkoutState(context.applicationContext, date, typeKey, writeScope)
    }

    LaunchedEffect(date, typeKey, workout) {
        state.load(workout, lookupWorkout, autoGenerate)
    }

    // One write per pause in typing, not one per keystroke.
    LaunchedEffect(state.results, state.plan) {
        if (state.plan == null || state.saveState != SaveState.Saving) return@LaunchedEffect
        delay(350)
        state.save()
    }

    DisposableEffect(state) {
        onDispose { state.flush() }
    }

    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick += 1
        onPauseOrDispose { }
    }
    LaunchedEffect(resumeTick) {
        if (resumeTick > 1) state.retryAfterResume()
    }

    return state
}

/**
 * The AI workout: its header, its sections and the fields the values go into.
 * [original] is how the calling screen shows the workout's own text — the
 * journal has a styled version, the full-screen editor a plain card.
 */
@Composable
fun AiWorkoutPanel(
    state: AiWorkoutState,
    onOpenAiSettings: () -> Unit,
    accent: Color = Teal,
    /** Opens the full-screen live session; null hides the entry point. */
    onStartLive: (() -> Unit)? = null,
    /** A preview: the plan is shown but its values cannot be edited. */
    readOnly: Boolean = false,
    /** A plan row to scroll to and flash (a record's source set). */
    focusScope: String? = null,
    original: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAiPlanReadOnly provides readOnly,
        LocalAiFocusScope provides focusScope,
    ) {
        AiWorkoutPanelBody(state, onOpenAiSettings, accent, onStartLive, original)
    }
}

@Composable
private fun AiWorkoutPanelBody(
    state: AiWorkoutState,
    onOpenAiSettings: () -> Unit,
    accent: Color,
    onStartLive: (() -> Unit)?,
    original: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showRegenerate by remember { mutableStateOf(false) }

    if (showRegenerate) {
        ConfirmDialog(
            title = "Regenerate this plan?",
            message = "The AI will rebuild the exercises and sets. Your entered values will be cleared.",
            confirmText = "Regenerate",
            destructive = false,
            onConfirm = {
                showRegenerate = false
                scope.launch { state.generate() }
            },
            onDismiss = { showRegenerate = false },
        )
    }

    when {
        state.loading -> LoadingLine("Reading this workout…", accent)

        state.generating -> GeneratingState(state.workout, state.plan != null)

        state.needsKey -> AiSetupState(
            providerLabel = AiConfigStore.getActive(context).provider.label,
            onOpenAiSettings = onOpenAiSettings,
        )

        state.plan == null && state.error != null -> AiErrorState(
            message = state.error ?: "Could not build an interactive plan.",
            onRetry = { scope.launch { state.generate() } },
            onOpenSettings = onOpenAiSettings,
        )

        state.plan == null -> Column(Modifier.fillMaxWidth()) {
            original()
            if (state.canGenerate) {
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    text = "Turn into an AI workout",
                    onClick = { scope.launch { state.generate() } },
                    accent = Periwinkle,
                )
            }
        }

        else -> AiPlanContent(
            state = state,
            onRegenerate = { showRegenerate = true },
            onStartLive = onStartLive,
            original = original,
        )
    }
}

@Composable
private fun AiPlanContent(
    state: AiWorkoutState,
    onRegenerate: () -> Unit,
    onStartLive: (() -> Unit)?,
    original: @Composable () -> Unit,
) {
    val plan = state.plan ?: return
    var confirmLive by remember { mutableStateOf(false) }
    if (confirmLive) {
        ConfirmDialog(
            title = "Start a live session?",
            message = "This workout already has values entered. The live session updates the " +
                "sets it runs and replaces a section's time or rounds when the section ends.",
            confirmText = "Start live",
            destructive = false,
            onConfirm = {
                confirmLive = false
                onStartLive?.invoke()
            },
            onDismiss = { confirmLive = false },
        )
    }
    Column(Modifier.fillMaxWidth()) {
        AiPlanHeader(state, onRegenerate = onRegenerate)
        state.error?.let { message ->
            Spacer(Modifier.height(12.dp))
            HcCard(accent = Danger, contentPadding = PaddingValues(16.dp)) {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = Mist)
            }
        }
        if (onStartLive != null && plan.sections.any {
                it.exercises.isNotEmpty() || it.rounds.isNotEmpty() || it.metrics.isNotEmpty()
            }
        ) {
            Spacer(Modifier.height(14.dp))
            LiveWorkoutCard(
                onClick = {
                    if (InteractiveResults.hasAnyValue(state.results)) {
                        confirmLive = true
                    } else {
                        onStartLive()
                    }
                },
            )
        }
        Spacer(Modifier.height(14.dp))
        if (state.showOriginal) {
            original()
            Spacer(Modifier.height(14.dp))
        }
        plan.sections.forEachIndexed { sectionIndex, section ->
            SectionBlock(
                section = section,
                sectionIndex = sectionIndex,
                results = state.results,
                onValue = state::setValue,
            )
            Spacer(Modifier.height(18.dp))
        }
        Text(
            "Generated by ${AiDisplay.pretty(state.modelId)}. Values are saved on this device.",
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Slate,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AiPlanHeader(state: AiWorkoutState, onRegenerate: () -> Unit) {
    val plan = state.plan ?: return
    val totals = state.totals ?: return
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Teal.copy(alpha = 0.20f), Periwinkle.copy(alpha = 0.17f), CardHigh)
                )
            )
            .border(1.dp, Teal.copy(alpha = 0.28f), shape)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Teal.copy(alpha = 0.45f), Periwinkle.copy(alpha = 0.45f))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_ai),
                    contentDescription = null,
                    tint = Snow,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Tag("AI PLAN", Teal)
            if (LocalAiPlanReadOnly.current) {
                Spacer(Modifier.width(6.dp))
                Tag("VIEW ONLY", Slate)
            }
            Spacer(Modifier.weight(1f))
            Tag(
                if (state.saveState == SaveState.Saving) "SAVING" else "SAVED",
                if (state.saveState == SaveState.Saving) Periwinkle else Success,
            )
            Spacer(Modifier.width(8.dp))
            IconAction(
                icon = {
                    Icon(
                        painterResource(R.drawable.ic_ai),
                        contentDescription = "Regenerate with AI",
                        tint = Periwinkle,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = onRegenerate,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(plan.title, style = MaterialTheme.typography.headlineMedium, color = Snow)
        if (plan.summary.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(plan.summary, style = MaterialTheme.typography.bodySmall, color = Mist)
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    totals.loggedSets == 0 -> "No values entered yet"
                    totals.notReachedSets > 0 ->
                        "${totals.loggedSets} of ${totals.totalSets} sets logged · " +
                            "${totals.notReachedSets} not reached"
                    else -> "${totals.loggedSets} of ${totals.totalSets} sets logged"
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = if (totals.loggedSets == 0) Slate else Mist,
            )
            totals.totalVolumeKg?.let { Tag("${JournalFeed.formatVolume(it)} KG", Success) }
        }
        val prescribedSets = totals.totalSets + totals.notReachedSets
        if (prescribedSets > 0) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Snow.copy(alpha = 0.08f))
            ) {
                val fraction = (totals.loggedSets.toFloat() / prescribedSets).coerceIn(0f, 1f)
                if (fraction > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(Teal, Periwinkle)))
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            if (state.showOriginal) "Hide original workout" else "Show original workout",
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = state::toggleOriginal)
                .padding(horizontal = 2.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = Periwinkle,
        )
    }
}

internal fun sectionAccent(kind: String): Color = when (kind) {
    "strength" -> Amber
    "metcon" -> Teal
    "accessory" -> Periwinkle
    "rest" -> Slate
    else -> Mist
}

/**
 * The way into the live session: a single, unmissable card under the plan
 * header. The details stay behind it — mid-workout the screen must not ask
 * the athlete to choose anything here.
 */
@Composable
private fun LiveWorkoutCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Teal.copy(alpha = 0.24f), Periwinkle.copy(alpha = 0.16f), CardHigh)
                )
            )
            .border(1.dp, Teal.copy(alpha = 0.32f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Teal.copy(alpha = 0.85f), Periwinkle.copy(alpha = 0.85f))
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Abyss,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Start live workout",
                style = MaterialTheme.typography.titleLarge,
                color = Snow,
            )
            Text(
                "One set at a time, with its timer, the whole plan in reach.",
                style = MaterialTheme.typography.bodySmall,
                color = Mist,
                maxLines = 2,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = Slate,
            modifier = Modifier.size(20.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionBlock(
    section: InteractiveSection,
    sectionIndex: Int,
    results: Map<String, Map<String, String>>,
    onValue: (String, String, String) -> Unit,
) {
    val accent = sectionAccent(section.kind)
    val liveSection = remember(section, sectionIndex) {
        LiveWorkout.section(section, sectionIndex)
    }
    val amrapScore = remember(liveSection, results) {
        if (liveSection.isAmrap) LiveScoring.amrapScore(liveSection, results) else null
    }
    // An AMRAP's clock is fixed by the workout; only its rounds and score are
    // results, so a stale "Total time" metric from an older plan stays hidden.
    val resultMetrics = if (liveSection.isAmrap) {
        section.metrics.filter { it.kind != MetricKind.TIME }
    } else {
        section.metrics
    }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent)
            )
            Spacer(Modifier.width(9.dp))
            Text(
                section.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = Snow,
            )
            Tag(section.kind.uppercase(), accent)
        }
        if (section.notes.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(
                section.notes,
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Slate,
            )
        }
        Spacer(Modifier.height(10.dp))

        val enteredResult = results[InteractiveKeys.section(sectionIndex)]
            ?.values?.any { it.isNotBlank() } == true
        if (amrapScore != null) {
            HcCard(
                accent = accent,
                contentPadding = PaddingValues(16.dp),
            ) {
                SectionLabel("Score", color = accent)
                Spacer(Modifier.height(6.dp))
                Text(
                    amrapScore.detail,
                    style = MaterialTheme.typography.titleLarge,
                    color = Snow,
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        if (resultMetrics.isNotEmpty() && section.showsSectionResult(enteredResult)) {
            HcCard(
                accent = accent,
                contentPadding = PaddingValues(16.dp),
            ) {
                SectionLabel("Section result", color = accent)
                Spacer(Modifier.height(8.dp))
                MetricFlow(
                    metrics = resultMetrics,
                    scopeKey = InteractiveKeys.section(sectionIndex),
                    results = results,
                    accent = accent,
                    onValue = onValue,
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        val explicitRounds = section.rounds
        val derivedRounds =
            if (explicitRounds.isEmpty()) InteractiveRounds.derive(section) else null

        // The section's rows in display order, and the row the "reached here"
        // marker sits on. Past it everything is greyed and left out of totals.
        val rowScopes = InteractiveRows.scopes(section, sectionIndex)
        val markedRow = InteractiveReached.indexOf(results, sectionIndex, rowScopes)
        // The movement the AMRAP cap stopped: its rep field must stay empty
        // (the prescription is not what the athlete did) so the score can be
        // completed by typing the reps actually reached.
        val partialScope = amrapScore?.partialIndex?.let { rowScopes.getOrNull(it) }
        val onReach: (String) -> Unit = { scope ->
            onValue(
                InteractiveKeys.marker(sectionIndex),
                InteractiveReached.KEY,
                if (markedRow != null && rowScopes.getOrNull(markedRow) == scope) "" else scope,
            )
        }
        val readOnly = LocalAiPlanReadOnly.current
        if (!readOnly && markedRow == null && rowScopes.size > 1 && section.kind == "metcon") {
            Text(
                "Tap the circle on the last set you reached.",
                modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Slate,
            )
        } else if (!readOnly && markedRow != null && markedRow < 0 && rowScopes.isNotEmpty()) {
            Text(
                "Not reached — tap the circle on any set to bring it back in.",
                modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Slate,
            )
        }

        when {
            explicitRounds.isNotEmpty() -> {
                RoundsCard(
                    rounds = explicitRounds.mapIndexed { roundIndex, round ->
                        val firstRow =
                            explicitRounds.take(roundIndex).sumOf { it.movements.size }
                        RoundView(
                            label = round.label,
                            notes = round.notes,
                            movements = round.movements.mapIndexed { movementIndex, movement ->
                                val scope = InteractiveKeys.round(
                                    sectionIndex, roundIndex, movementIndex,
                                )
                                RoundMovementView(
                                    name = movement.name,
                                    notes = movement.notes,
                                    target = movement.target,
                                    metrics = movement.metrics,
                                    scopeKey = scope,
                                    rowIndex = firstRow + movementIndex,
                                    suppressDefaults = scope == partialScope,
                                )
                            },
                        )
                    },
                    results = results,
                    accent = accent,
                    onValue = onValue,
                    markedRow = markedRow,
                    onReach = onReach,
                )
                Spacer(Modifier.height(CardGap))
            }
            derivedRounds != null -> {
                RoundsCard(
                    rounds = derivedRounds.mapIndexed { roundIndex, round ->
                        val firstRow =
                            derivedRounds.take(roundIndex).sumOf { it.movements.size }
                        RoundView(
                            label = round.label,
                            notes = "",
                            movements = round.movements.mapIndexed { movementIndex, movement ->
                                val scope = InteractiveKeys.set(
                                    sectionIndex,
                                    movement.exerciseIndex,
                                    movement.setIndex,
                                )
                                RoundMovementView(
                                    name = movement.exercise.name,
                                    notes = "",
                                    target = movement.set.target,
                                    metrics = movement.set.metrics
                                        .ifEmpty { movement.exercise.metrics },
                                    scopeKey = scope,
                                    rowIndex = firstRow + movementIndex,
                                    suppressDefaults = scope == partialScope,
                                )
                            },
                        )
                    },
                    results = results,
                    accent = accent,
                    onValue = onValue,
                    markedRow = markedRow,
                    onReach = onReach,
                )
                Spacer(Modifier.height(CardGap))
            }
            else -> {
                var firstRow = 0
                section.exercises.forEachIndexed { exerciseIndex, exercise ->
                    ExerciseCard(
                        exercise = exercise,
                        firstRow = firstRow,
                        markedRow = markedRow,
                        onReach = onReach,
                        scopePrefix = { setIndex ->
                            InteractiveKeys.set(sectionIndex, exerciseIndex, setIndex)
                        },
                        results = results,
                        accent = accent,
                        onValue = onValue,
                    )
                    firstRow += exercise.sets.size
                    Spacer(Modifier.height(CardGap))
                }
            }
        }
    }
}

@Composable
private fun GeneratingState(workout: Workout?, regenerating: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 46.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(74.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Teal.copy(alpha = 0.7f), Periwinkle.copy(alpha = 0.7f))
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_ai),
                contentDescription = null,
                tint = Abyss,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            if (regenerating) "The AI is rebuilding the plan…" else "The AI is reading this session…",
            style = MaterialTheme.typography.headlineSmall,
            color = Snow,
            textAlign = TextAlign.Center,
        )
        if (workout != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${WorkoutText.displayType(workout.typeLabel)} · ${workout.date}",
                style = MaterialTheme.typography.bodyMedium,
                color = Slate,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(20.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = Teal,
            strokeWidth = 2.dp,
        )
    }
}

@Composable
private fun AiSetupState(providerLabel: String, onOpenAiSettings: () -> Unit) {
    HcCard(accent = Periwinkle, contentPadding = PaddingValues(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Teal.copy(alpha = 0.35f), Periwinkle.copy(alpha = 0.35f))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_ai),
                    contentDescription = null,
                    tint = Snow,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("AI workout", style = MaterialTheme.typography.headlineSmall, color = Snow)
                Text("Add an API key for $providerLabel to use it.", style = MaterialTheme.typography.bodySmall, color = Mist)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "The AI turns the workout text into exercises, sets and metrics you can fill in. " +
                "Only the workout's date, type and text are sent — never your journal or health data.",
            style = MaterialTheme.typography.bodyMedium,
            color = Mist,
        )
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Open AI settings", onClick = onOpenAiSettings, accent = Periwinkle)
        Spacer(Modifier.height(10.dp))
        Text(
            "Your API key is stored encrypted on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = Slate,
        )
    }
}

@Composable
private fun AiErrorState(
    message: String,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    HcCard(accent = Danger, contentPadding = PaddingValues(22.dp)) {
        Text("Could not build the plan", style = MaterialTheme.typography.headlineSmall, color = Snow)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Mist)
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Try again", onClick = onRetry, accent = Teal)
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Open AI settings", onClick = onOpenSettings, accent = Periwinkle)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseCard(
    exercise: InteractiveExercise,
    firstRow: Int,
    markedRow: Int?,
    onReach: (String) -> Unit,
    scopePrefix: (setIndex: Int) -> String,
    results: Map<String, Map<String, String>>,
    accent: Color,
    onValue: (String, String, String) -> Unit,
) {
    val readOnly = LocalAiPlanReadOnly.current
    HcCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                exercise.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = Snow,
            )
            if (exercise.sets.size > 1) {
                Tag("${exercise.sets.size} SETS", accent)
            }
        }
        if (exercise.notes.isNotBlank()) {
            Text(
                exercise.notes,
                style = MaterialTheme.typography.bodySmall,
                color = Slate,
            )
        }
        Spacer(Modifier.height(4.dp))

        exercise.sets.forEachIndexed { setIndex, set ->
            if (setIndex > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .height(1.dp)
                        .background(Hairline)
                )
            }
            val metrics = set.metrics.ifEmpty { exercise.metrics }
            val defaultFor: (MetricDef) -> String? = { metric ->
                InteractiveDefaults.forTarget(set.target, metric)
            }
            val hasCheck = metrics.any { it.kind == MetricKind.CHECK }
            // For a tick-only row the prescription is the interval (e.g. "40s");
            // keep it beside the tick rather than on a line of its own.
            val inlineTarget = set.target.takeIf { hasCheck && it.isNotBlank() }
            // The target only needs its own line when no field already shows it.
            val showTarget = set.target.isNotBlank() && !hasCheck &&
                metrics.none { defaultFor(it) != null }
            val scopeKey = scopePrefix(setIndex)
            val rowIndex = firstRow + setIndex
            val isMarked = markedRow == rowIndex
            val notReached = markedRow != null && rowIndex > markedRow
            Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                HighlightOnArrival(
                    active = LocalAiFocusScope.current == scopeKey,
                    color = accent,
                ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!readOnly) {
                        ReachedCheck(marked = isMarked, accent = accent) { onReach(scopeKey) }
                    }
                    Column(Modifier.weight(1f).alpha(if (notReached) PlacedAlpha else 1f)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                set.label,
                                modifier = Modifier.width(54.dp).padding(end = 6.dp),
                                style = MaterialTheme.typography.titleSmall,
                                color = Snow,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (inlineTarget != null) {
                                Text(
                                    inlineTarget,
                                    modifier = Modifier.padding(end = 6.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Slate,
                                    maxLines = 1,
                                )
                            }
                            CompactMetrics(
                                metrics = metrics,
                                scopeKey = scopeKey,
                                results = results,
                                accent = accent,
                                onValue = onValue,
                                defaultFor = defaultFor,
                                modifier = Modifier.weight(1f),
                                enabled = !notReached,
                            )
                        }
                        if (showTarget) {
                            Text(
                                set.target,
                                modifier = Modifier.padding(start = 54.dp, top = 2.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                }
                if (isMarked) ReachedCaption(accent)
            }
        }
    }
}

private data class RoundMovementView(
    val name: String,
    val notes: String,
    val target: String,
    val metrics: List<MetricDef>,
    val scopeKey: String,
    /** Position of this row among the section's rows, for the reached marker. */
    val rowIndex: Int,
    /** The AMRAP movement the cap stopped: its reps are the athlete's to fill. */
    val suppressDefaults: Boolean = false,
)

private data class RoundView(
    val label: String,
    val notes: String,
    val movements: List<RoundMovementView>,
)

@Composable
private fun RoundsCard(
    rounds: List<RoundView>,
    results: Map<String, Map<String, String>>,
    accent: Color,
    onValue: (String, String, String) -> Unit,
    markedRow: Int?,
    onReach: (String) -> Unit,
) {
    val readOnly = LocalAiPlanReadOnly.current
    HcCard(contentPadding = PaddingValues(16.dp)) {
        rounds.forEachIndexed { roundIndex, round ->
            if (roundIndex > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .height(1.dp)
                        .background(Hairline)
                )
            }
            // A round whose every row is past the marker is greyed as a whole.
            val roundNotReached =
                markedRow != null && round.movements.all { it.rowIndex > markedRow }
            Row(
                Modifier
                    .fillMaxWidth()
                    .alpha(if (roundNotReached) PlacedAlpha else 1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.10f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(50))
                        .background(accent)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    round.label.uppercase(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (round.notes.isNotBlank()) {
                    Text(
                        round.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            round.movements.forEach { movement ->
                val defaultFor: (MetricDef) -> String? = { metric ->
                    if (movement.suppressDefaults && metric.kind == MetricKind.REPS) {
                        null
                    } else {
                        InteractiveDefaults.forTarget(movement.target, metric)
                    }
                }
                val hasCheck = movement.metrics.any { it.kind == MetricKind.CHECK }
                val inlineTarget =
                    movement.target.takeIf { hasCheck && it.isNotBlank() }
                val showTarget = movement.target.isNotBlank() && !hasCheck &&
                    movement.metrics.none { defaultFor(it) != null }
                val isMarked = markedRow == movement.rowIndex
                val notReached = markedRow != null && movement.rowIndex > markedRow
                Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    HighlightOnArrival(
                        active = LocalAiFocusScope.current == movement.scopeKey,
                        color = accent,
                    ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!readOnly) {
                            ReachedCheck(marked = isMarked, accent = accent) {
                                onReach(movement.scopeKey)
                            }
                        }
                        Column(Modifier.weight(1f).alpha(if (notReached) PlacedAlpha else 1f)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f).padding(end = 6.dp)) {
                                    Text(
                                        movement.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Snow,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (movement.notes.isNotBlank()) {
                                        Text(
                                            movement.notes,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Slate,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (inlineTarget != null) {
                                    Text(
                                        inlineTarget,
                                        modifier = Modifier.padding(end = 6.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Slate,
                                        maxLines = 1,
                                    )
                                }
                                CompactMetrics(
                                    metrics = movement.metrics,
                                    scopeKey = movement.scopeKey,
                                    results = results,
                                    accent = accent,
                                    onValue = onValue,
                                    defaultFor = defaultFor,
                                    modifier = Modifier.width(compactWidth(movement.metrics)),
                                    enabled = !notReached,
                                )
                            }
                            if (showTarget) {
                                Text(
                                    movement.target,
                                    modifier = Modifier.padding(top = 2.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    }
                    if (isMarked) ReachedCaption(accent)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricFlow(
    metrics: List<MetricDef>,
    scopeKey: String,
    results: Map<String, Map<String, String>>,
    accent: Color,
    onValue: (String, String, String) -> Unit,
    defaultFor: (MetricDef) -> String? = { null },
) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        metrics.forEach { metric ->
            val value =
                if (InteractiveResults.contains(results, scopeKey, metric.key)) {
                    InteractiveResults.value(results, scopeKey, metric.key)
                } else {
                    // Start filled with the prescription; clearing the field
                    // stores an explicit empty and keeps it empty.
                    defaultFor(metric).orEmpty()
                }
            MetricInput(
                metric = metric,
                value = value,
                accent = accent,
                onValue = { entered -> onValue(scopeKey, metric.key, entered) },
                modifier = Modifier.width(118.dp),
            )
        }
    }
}

@Composable
private fun MetricInput(
    metric: MetricDef,
    value: String,
    accent: Color,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    enabled: Boolean = true,
) {
    val editable = enabled && !LocalAiPlanReadOnly.current
    if (metric.kind == MetricKind.TIME) {
        TimeMetricInput(
            metric = metric,
            value = value,
            accent = accent,
            onValue = onValue,
            modifier = modifier,
            compact = compact,
            enabled = editable,
        )
        return
    }
    if (metric.kind == MetricKind.CHECK) {
        CheckMetricInput(
            metric = metric,
            value = value,
            accent = accent,
            onValue = onValue,
            modifier = modifier,
            enabled = editable,
        )
        return
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // Compact rows put the label inside the field so a set stays one line.
    val hint = if (compact) metric.hint.ifBlank { metric.label } else metricHint(metric)
    Column(modifier) {
        if (!compact) {
            Text(
                if (metric.unit.isBlank()) metric.label else "${metric.label} · ${metric.unit}",
                style = MaterialTheme.typography.labelSmall,
                color = Slate,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) 40.dp else 44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(CardHigh)
                .border(
                    1.dp,
                    if (focused) accent.copy(alpha = 0.75f) else Hairline,
                    RoundedCornerShape(13.dp),
                )
                .padding(horizontal = 11.dp, vertical = if (compact) 8.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && !focused) {
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = { raw ->
                        val clean = sanitiseMetricInput(raw, metric.kind)
                        if (clean != value) onValue(clean)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = editable,
                    textStyle = MaterialTheme.typography.titleSmall.copy(
                        color = Snow,
                        fontFeatureSettings = "tnum",
                    ),
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (metric.kind) {
                            MetricKind.WEIGHT, MetricKind.DISTANCE -> KeyboardType.Decimal
                            MetricKind.REPS, MetricKind.CALORIES, MetricKind.ROUNDS -> KeyboardType.Number
                            else -> KeyboardType.Text
                        }
                    ),
                    interactionSource = interaction,
                )
            }
            if (compact && value.isNotBlank() && metric.unit.isNotBlank()) {
                Spacer(Modifier.width(3.dp))
                Text(
                    metric.unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate,
                    maxLines = 1,
                )
            }
        }
    }
}

/** One row of inputs for a set or a round movement, sized to stay on one line. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactMetrics(
    metrics: List<MetricDef>,
    scopeKey: String,
    results: Map<String, Map<String, String>>,
    accent: Color,
    onValue: (String, String, String) -> Unit,
    defaultFor: (MetricDef) -> String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        metrics.forEach { metric ->
            MetricInput(
                metric = metric,
                value = resolvedMetricValue(results, scopeKey, metric, defaultFor),
                accent = accent,
                onValue = { entered -> onValue(scopeKey, metric.key, entered) },
                modifier = Modifier.width(compactWidth(metric)),
                compact = true,
                enabled = enabled,
            )
        }
    }
}

private fun compactWidth(metric: MetricDef): Dp = when (metric.kind) {
    MetricKind.WEIGHT -> if (metric.hint.isNotBlank()) 100.dp else 88.dp
    MetricKind.REPS, MetricKind.CALORIES, MetricKind.ROUNDS -> 74.dp
    MetricKind.DISTANCE -> 80.dp
    MetricKind.TIME -> 84.dp
    MetricKind.CHECK -> 86.dp
    MetricKind.TEXT -> 120.dp
}

private fun compactWidth(metrics: List<MetricDef>): Dp {
    if (metrics.isEmpty()) return 0.dp
    return metrics.fold(0.dp) { total, metric -> total + compactWidth(metric) } +
        6.dp * (metrics.size - 1).coerceAtLeast(0)
}

/** How far a row past the reached marker is dimmed. */
private const val PlacedAlpha = 0.35f

/** Width of the reached circle's tap area; the caption aligns to its far edge. */
private val ReachCheckSize = 34.dp

/**
 * The "reached here" control: a small circle at the head of every set row.
 * Tapping it marks that row as the last one reached — everything before it
 * counts as done, everything after is greyed and not counted. Tapping the
 * marked circle again clears the marker.
 */
@Composable
private fun ReachedCheck(
    marked: Boolean,
    accent: Color,
    onToggle: () -> Unit,
) {
    Box(
        Modifier
            .size(ReachCheckSize)
            .clip(CircleShape)
            .clickable(
                onClickLabel = if (marked) "Clear reached marker" else "Reached up to here",
                onClick = onToggle,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(17.dp)
                .clip(CircleShape)
                .background(if (marked) accent else Color.Transparent)
                .border(
                    1.5.dp,
                    if (marked) accent else Slate.copy(alpha = 0.55f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (marked) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Abyss,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}

/** Under the marked row: a quiet line naming where the workout was stopped. */
@Composable
private fun ReachedCaption(accent: Color) {
    Text(
        "reached here",
        modifier = Modifier.padding(start = ReachCheckSize + 2.dp, top = 1.dp),
        style = MaterialTheme.typography.labelSmall,
        color = accent,
        maxLines = 1,
    )
}

private fun resolvedMetricValue(
    results: Map<String, Map<String, String>>,
    scopeKey: String,
    metric: MetricDef,
    defaultFor: (MetricDef) -> String?,
): String =
    if (InteractiveResults.contains(results, scopeKey, metric.key)) {
        InteractiveResults.value(results, scopeKey, metric.key)
    } else {
        // Start filled with the prescription; clearing the field stores an
        // explicit empty and keeps it empty.
        defaultFor(metric).orEmpty()
    }

/**
 * A completion-only metric: there is no number to record, just a tick that the
 * work was done (EMOM intervals, holds, drills).
 */
@Composable
private fun CheckMetricInput(
    metric: MetricDef,
    value: String,
    accent: Color,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val checked = value == "1"
    Row(
        modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (checked) accent.copy(alpha = 0.16f) else CardHigh)
            .border(
                1.dp,
                if (checked) accent.copy(alpha = 0.55f) else Hairline,
                RoundedCornerShape(13.dp),
            )
            .clickable(enabled = enabled) { onValue(if (checked) "" else "1") }
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = if (checked) accent else Slate,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            metric.label,
            style = MaterialTheme.typography.titleSmall,
            color = if (checked) Snow else Slate,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Time metrics ("For time", total time, holds) are picked on minute and
 * second wheels rather than typed as free text.
 */
@Composable
private fun TimeMetricInput(
    metric: MetricDef,
    value: String,
    accent: Color,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    enabled: Boolean = true,
) {
    var picking by remember { mutableStateOf(false) }
    Column(modifier) {
        if (!compact) {
            Text(
                if (metric.unit.isBlank()) metric.label else "${metric.label} · ${metric.unit}",
                style = MaterialTheme.typography.labelSmall,
                color = Slate,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) 40.dp else 44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(CardHigh)
                .border(1.dp, Hairline, RoundedCornerShape(13.dp))
                .clickable(enabled = enabled) { picking = true }
                .padding(horizontal = 11.dp, vertical = if (compact) 8.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (value.isBlank()) "mm:ss" else TimeValue.normalise(value),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall.copy(
                    color = if (value.isBlank()) Slate else Snow,
                    fontFeatureSettings = "tnum",
                ),
                maxLines = 1,
            )
            Text("▾", style = MaterialTheme.typography.bodySmall, color = Slate)
        }
    }
    if (picking) {
        TimeSpinnerDialog(
            title = metric.label,
            value = value,
            accent = accent,
            onConfirm = { picked ->
                onValue(picked)
                picking = false
            },
            onClear = {
                onValue("")
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

private val WheelItemHeight = 38.dp
private const val WheelRows = 5

@Composable
internal fun TimeSpinnerDialog(
    title: String,
    value: String,
    accent: Color,
    onConfirm: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val parsed = remember(value) { TimeValue.parse(value) }
    var minutes by remember { mutableIntStateOf(parsed?.first ?: 0) }
    var seconds by remember { mutableIntStateOf(parsed?.second ?: 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardHigh,
        titleContentColor = Snow,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    TimeValue.format(minutes, seconds),
                    style = MaterialTheme.typography.displaySmall.copy(
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum",
                    ),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WheelColumn(0..99, minutes, "min", accent) { minutes = it }
                    Box(Modifier.height(WheelItemHeight * WheelRows), contentAlignment = Alignment.Center) {
                        Text(":", style = MaterialTheme.typography.titleLarge, color = Slate)
                    }
                    WheelColumn(0..59, seconds, "sec", accent) { seconds = it }
                }
                TextButton(onClick = onClear) {
                    Text("Clear", color = Danger, style = MaterialTheme.typography.titleSmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(TimeValue.format(minutes, seconds)) }) {
                Text("Save", color = accent, style = MaterialTheme.typography.titleSmall)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Slate, style = MaterialTheme.typography.titleSmall)
            }
        },
    )
}

@Composable
private fun WheelColumn(
    range: IntRange,
    selected: Int,
    label: String,
    accent: Color,
    onSelected: (Int) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        NumberWheel(range = range, selected = selected, accent = accent, onSelected = onSelected)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Slate)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NumberWheel(
    range: IntRange,
    selected: Int,
    accent: Color,
    onSelected: (Int) -> Unit,
) {
    val values = remember(range) { range.toList() }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val half = WheelRows / 2
    val itemHeightPx = with(LocalDensity.current) { WheelItemHeight.toPx() }
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val initial = values.indexOf(selected).coerceAtLeast(0)
        state.scrollToItem(initial + half, -half * itemHeightPx.toInt())
        ready = true
    }

    val centerItem by remember {
        derivedStateOf {
            val info = state.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) return@derivedStateOf -1
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            info.visibleItemsInfo.minByOrNull { item ->
                kotlin.math.abs(item.offset + item.size / 2f - center)
            }?.index ?: -1
        }
    }

    LaunchedEffect(centerItem, ready) {
        if (!ready) return@LaunchedEffect
        values.getOrNull(centerItem - half)?.let(onSelected)
    }

    Box(
        Modifier
            .width(84.dp)
            .height(WheelItemHeight * WheelRows),
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(WheelItemHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.12f))
        )
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            flingBehavior = rememberSnapFlingBehavior(state),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(half) { Spacer(Modifier.height(WheelItemHeight)) }
            itemsIndexed(values) { index, number ->
                val centered = index + half == centerItem
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(WheelItemHeight)
                        .clickable {
                            scope.launch {
                                state.animateScrollToItem(
                                    index + half,
                                    -half * itemHeightPx.toInt(),
                                )
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        number.toString(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (centered) FontWeight.SemiBold else FontWeight.Normal,
                            fontFeatureSettings = "tnum",
                        ),
                        color = if (centered) Snow else Slate,
                    )
                }
            }
            items(half) { Spacer(Modifier.height(WheelItemHeight)) }
        }
    }
}

internal fun sanitiseMetricInput(raw: String, kind: MetricKind): String = when (kind) {
    MetricKind.WEIGHT, MetricKind.DISTANCE -> buildString {
        var dot = false
        for (c in raw) {
            if (c.isDigit()) append(c)
            else if (c == '.' && !dot) {
                append(c)
                dot = true
            }
        }
    }
    MetricKind.REPS, MetricKind.CALORIES, MetricKind.ROUNDS ->
        raw.filter(Char::isDigit)
    MetricKind.TIME -> buildString {
        var colon = false
        for (c in raw) {
            if (c.isDigit()) append(c)
            else if (c == ':' && !colon) {
                append(c)
                colon = true
            }
        }
    }
    MetricKind.TEXT, MetricKind.CHECK -> raw
}
