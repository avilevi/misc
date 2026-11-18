package com.hcexport.ui.workouts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hcexport.AiClient
import com.hcexport.AiConfigStore
import com.hcexport.AiDisplay
import com.hcexport.AiKeyStore
import com.hcexport.InteractiveExercise
import com.hcexport.InteractiveKeys
import com.hcexport.InteractivePlan
import com.hcexport.InteractiveResults
import com.hcexport.InteractiveSection
import com.hcexport.InteractiveStore
import com.hcexport.MetricDef
import com.hcexport.MetricKind
import com.hcexport.R
import com.hcexport.Workout
import com.hcexport.WorkoutStore
import com.hcexport.WorkoutText
import com.hcexport.metricHint
import com.hcexport.ui.CardGap
import com.hcexport.ui.ConfirmDialog
import com.hcexport.ui.DataRefresh
import com.hcexport.ui.EmptyState
import com.hcexport.ui.HcBackground
import com.hcexport.ui.HcCard
import com.hcexport.ui.IconAction
import com.hcexport.ui.LoadingLine
import com.hcexport.ui.NumberText
import com.hcexport.ui.PageHeader
import com.hcexport.ui.PrimaryButton
import com.hcexport.ui.ScreenPadding
import com.hcexport.ui.SecondaryButton
import com.hcexport.ui.SectionLabel
import com.hcexport.ui.Tag
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SaveState { Saved, Saving }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractiveWorkoutScreen(
    date: String,
    typeKey: String,
    onBack: () -> Unit,
    onOpenAiSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var workout by remember { mutableStateOf<Workout?>(null) }
    var plan by remember { mutableStateOf<InteractivePlan?>(null) }
    var results by remember { mutableStateOf<Map<String, Map<String, String>>>(emptyMap()) }
    var modelId by remember { mutableStateOf(AiConfigStore.getActive(context).displayName) }
    var loading by remember { mutableStateOf(true) }
    var generating by remember { mutableStateOf(false) }
    var needsKey by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showOriginal by remember { mutableStateOf(false) }
    var showRegenerate by remember { mutableStateOf(false) }
    var saveState by remember { mutableStateOf(SaveState.Saved) }
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick += 1
        onPauseOrDispose { }
    }

    suspend fun generatePlan(current: Workout) {
        val activeProvider = AiConfigStore.activeProviderKey(context)
        if (!AiKeyStore.hasKey(context, activeProvider)) {
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
            }
            plan = generated.plan
            results = emptyMap()
            modelId = usedModel
            saveState = SaveState.Saved
            DataRefresh.bump()
        }.onFailure {
            error = it.message ?: "Could not generate the plan."
        }
    }

    LaunchedEffect(date, typeKey) {
        val found = withContext(Dispatchers.IO) {
            WorkoutStore.getByDate(context, date).firstOrNull { it.typeKey == typeKey }
        }
        workout = found
        val session = withContext(Dispatchers.IO) {
            InteractiveStore.get(context, date, typeKey)
        }
        if (session != null) {
            val parsed = InteractivePlan.fromJson(session.planJson)
            if (parsed != null) {
                plan = parsed
                results = InteractiveResults.fromJson(session.resultsJson)
                modelId = session.model.ifBlank { AiConfigStore.getActive(context).displayName }
            } else {
                withContext(Dispatchers.IO) { InteractiveStore.delete(context, date, typeKey) }
            }
        }
        loading = false
        if (plan == null && found != null) {
            if (AiKeyStore.hasKey(context, AiConfigStore.activeProviderKey(context))) {
                generatePlan(found)
            } else {
                needsKey = true
            }
        }
    }

    // If the user went to Settings to add a key, resume with a fresh attempt.
    LaunchedEffect(resumeTick) {
        if (resumeTick <= 1 || loading || generating || plan != null) return@LaunchedEffect
        val current = workout ?: return@LaunchedEffect
        if (AiKeyStore.hasKey(context, AiConfigStore.activeProviderKey(context))) {
            needsKey = false
            generatePlan(current)
        } else {
            needsKey = true
        }
    }

    LaunchedEffect(results, plan) {
        if (plan == null) return@LaunchedEffect
        saveState = SaveState.Saving
        delay(350)
        withContext(Dispatchers.IO) {
            InteractiveStore.saveResults(
                context, date, typeKey, InteractiveResults.toJson(results),
            )
        }
        saveState = SaveState.Saved
    }

    fun saveAndBack() {
        val current = plan
        if (current == null) {
            onBack()
            return
        }
        val payload = InteractiveResults.toJson(results)
        scope.launch {
            withContext(Dispatchers.IO) {
                InteractiveStore.saveResults(context, date, typeKey, payload)
            }
            onBack()
        }
    }

    BackHandler { saveAndBack() }

    if (showRegenerate) {
        ConfirmDialog(
            title = "Regenerate this plan?",
            message = "The AI will rebuild the exercises and sets. Your entered values will be cleared.",
            confirmText = "Regenerate",
            destructive = false,
            onConfirm = {
                showRegenerate = false
                workout?.let { scope.launch { generatePlan(it) } }
            },
            onDismiss = { showRegenerate = false },
        )
    }

    HcBackground(accent = Teal) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            PageHeader(
                title = plan?.title ?: "AI workout",
                eyebrow = "Interactive",
                onBack = { saveAndBack() },
                trailing = {
                    if (plan != null) {
                        Tag(
                            if (saveState == SaveState.Saving) "SAVING" else "SAVED",
                            if (saveState == SaveState.Saving) Periwinkle else Success,
                        )
                        IconAction(
                            icon = {
                                Icon(
                                    painterResource(R.drawable.ic_ai),
                                    contentDescription = "Regenerate with AI",
                                    tint = Periwinkle,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = { showRegenerate = true },
                        )
                    }
                },
            )

            when {
                loading -> LoadingLine("Reading this workout…", Teal)
                workout == null -> EmptyState(
                    iconRes = R.drawable.ic_calendar,
                    title = "Workout not found",
                    message = "This workout is not in the local database.",
                    accent = Teal,
                )
                generating -> GeneratingState(workout!!, plan != null)
                needsKey -> AiSetupState(
                    providerLabel = AiConfigStore.getActive(context).provider.label,
                    onOpenAiSettings = onOpenAiSettings,
                )
                plan == null -> AiErrorState(
                    message = error ?: "Could not build an interactive plan.",
                    onRetry = { workout?.let { scope.launch { generatePlan(it) } } },
                    onOpenSettings = onOpenAiSettings,
                )
                else -> InteractivePlanContent(
                    workout = workout!!,
                    plan = plan!!,
                    results = results,
                    modelId = modelId,
                    error = error,
                    showOriginal = showOriginal,
                    onToggleOriginal = { showOriginal = !showOriginal },
                    onValue = { scopeKey, metricKey, value ->
                        results = InteractiveResults.set(results, scopeKey, metricKey, value)
                    },
                )
            }
        }
    }
}

@Composable
private fun GeneratingState(workout: Workout, regenerating: Boolean) {
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
        Spacer(Modifier.height(8.dp))
        Text(
            "${WorkoutText.displayType(workout.typeLabel)} · ${workout.date}",
            style = MaterialTheme.typography.bodyMedium,
            color = Slate,
            textAlign = TextAlign.Center,
        )
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
    Column(Modifier.fillMaxWidth().padding(ScreenPadding)) {
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
}

@Composable
private fun AiErrorState(
    message: String,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(ScreenPadding)) {
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
}

@Composable
private fun InteractivePlanContent(
    workout: Workout,
    plan: InteractivePlan,
    results: Map<String, Map<String, String>>,
    modelId: String,
    error: String?,
    showOriginal: Boolean,
    onToggleOriginal: () -> Unit,
    onValue: (scopeKey: String, metricKey: String, value: String) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ScreenPadding,
            end = ScreenPadding,
            top = 4.dp,
            bottom = 36.dp,
        ),
    ) {
        item {
            AiPlanHeader(
                plan = plan,
                modelId = modelId,
                showOriginal = showOriginal,
                onToggleOriginal = onToggleOriginal,
            )
            Spacer(Modifier.height(14.dp))
        }

        if (error != null) {
            item {
                HcCard(accent = Danger, contentPadding = PaddingValues(16.dp)) {
                    Text(error, style = MaterialTheme.typography.bodyMedium, color = Mist)
                }
                Spacer(Modifier.height(14.dp))
            }
        }

        if (showOriginal) {
            item {
                HcCard(contentPadding = PaddingValues(18.dp)) {
                    SectionLabel("Original workout", color = Slate)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        workout.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mist,
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
        }

        itemsIndexed(plan.sections) { sectionIndex, section ->
            SectionBlock(
                section = section,
                sectionIndex = sectionIndex,
                results = results,
                onValue = onValue,
            )
            Spacer(Modifier.height(18.dp))
        }

        item {
            Text(
                "Generated by ${AiDisplay.pretty(modelId)}. Values are saved on this device.",
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Slate,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AiPlanHeader(
    plan: InteractivePlan,
    modelId: String,
    showOriginal: Boolean,
    onToggleOriginal: () -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Teal.copy(alpha = 0.20f),
                        Periwinkle.copy(alpha = 0.17f),
                        CardHigh,
                    )
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
            Spacer(Modifier.weight(1f))
            Text(
                AiDisplay.pretty(modelId),
                style = MaterialTheme.typography.labelSmall,
                color = Slate,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            plan.title,
            style = MaterialTheme.typography.headlineMedium,
            color = Snow,
        )
        if (plan.summary.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(
                plan.summary,
                style = MaterialTheme.typography.bodySmall,
                color = Mist,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (showOriginal) "Hide original workout" else "Show original workout",
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onToggleOriginal)
                .padding(horizontal = 2.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = Periwinkle,
        )
    }
}

private fun sectionAccent(kind: String): Color = when (kind) {
    "strength" -> Amber
    "metcon" -> Teal
    "accessory" -> Periwinkle
    "rest" -> Slate
    else -> Mist
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

        if (section.metrics.isNotEmpty()) {
            HcCard(
                accent = accent,
                contentPadding = PaddingValues(16.dp),
            ) {
                SectionLabel("Section result", color = accent)
                Spacer(Modifier.height(8.dp))
                MetricFlow(
                    metrics = section.metrics,
                    scopeKey = InteractiveKeys.section(sectionIndex),
                    results = results,
                    accent = accent,
                    onValue = onValue,
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        section.exercises.forEachIndexed { exerciseIndex, exercise ->
            ExerciseCard(
                exercise = exercise,
                scopePrefix = { setIndex ->
                    InteractiveKeys.set(sectionIndex, exerciseIndex, setIndex)
                },
                results = results,
                accent = accent,
                onValue = onValue,
            )
            Spacer(Modifier.height(CardGap))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseCard(
    exercise: InteractiveExercise,
    scopePrefix: (setIndex: Int) -> String,
    results: Map<String, Map<String, String>>,
    accent: Color,
    onValue: (String, String, String) -> Unit,
) {
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
        Spacer(Modifier.height(6.dp))

        exercise.sets.forEachIndexed { setIndex, set ->
            if (setIndex > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .height(1.dp)
                        .background(Hairline)
                )
            }
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        set.label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        color = Snow,
                    )
                    if (set.target.isNotBlank()) {
                        Text(
                            set.target,
                            modifier = Modifier.weight(1.4f),
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate,
                            textAlign = TextAlign.End,
                        )
                    }
                }
                Spacer(Modifier.height(7.dp))
                val metrics = set.metrics.ifEmpty { exercise.metrics }
                MetricFlow(
                    metrics = metrics,
                    scopeKey = scopePrefix(setIndex),
                    results = results,
                    accent = accent,
                    onValue = onValue,
                )
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
) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        metrics.forEach { metric ->
            MetricInput(
                metric = metric,
                value = InteractiveResults.value(results, scopeKey, metric.key),
                accent = accent,
                onValue = { value -> onValue(scopeKey, metric.key, value) },
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
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hint = metricHint(metric)
    Column(modifier) {
        Text(
            if (metric.unit.isBlank()) metric.label else "${metric.label} · ${metric.unit}",
            style = MaterialTheme.typography.labelSmall,
            color = Slate,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(CardHigh)
                .border(
                    1.dp,
                    if (focused) accent.copy(alpha = 0.75f) else Hairline,
                    RoundedCornerShape(13.dp),
                )
                .padding(horizontal = 11.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty() && !focused) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                    maxLines = 1,
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
    }
}

private fun sanitiseMetricInput(raw: String, kind: MetricKind): String = when (kind) {
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
    MetricKind.TEXT -> raw
}
