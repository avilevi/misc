package com.hcexport.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hcexport.BenchmarkLibrary
import com.hcexport.BenchmarkRecord
import com.hcexport.BenchmarkScoreType
import com.hcexport.ExerciseLibrary
import com.hcexport.RecordLogic
import com.hcexport.RecordStore
import com.hcexport.WeightRecord
import com.hcexport.Workout
import com.hcexport.WorkoutStore
import com.hcexport.WorkoutText
import com.hcexport.ui.ConfirmDialog
import com.hcexport.ui.DataRefresh
import com.hcexport.ui.HcBackground
import com.hcexport.ui.IconAction
import com.hcexport.ui.ScreenPadding
import com.hcexport.ui.SectionLabel
import com.hcexport.ui.theme.Abyss
import com.hcexport.ui.theme.Amber
import com.hcexport.ui.theme.CardHigh
import com.hcexport.ui.theme.Danger
import com.hcexport.ui.theme.Hairline
import com.hcexport.ui.theme.Mist
import com.hcexport.ui.theme.Obsidian
import com.hcexport.ui.theme.Slate
import com.hcexport.ui.theme.Snow
import com.hcexport.ui.theme.Teal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// ── Shared editor chrome ───────────────────────────────────────────────────

@Composable
private fun EditorHeader(
    title: String,
    eyebrow: String,
    accent: androidx.compose.ui.graphics.Color,
    saveEnabled: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(
            icon = { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Snow) },
            onClick = onBack,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                eyebrow.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Slate,
            )
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Snow)
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (saveEnabled) accent.copy(alpha = 0.14f) else CardHigh)
                .then(
                    if (saveEnabled) Modifier.border(1.dp, accent.copy(alpha = 0.32f), RoundedCornerShape(50))
                    else Modifier
                )
                .clickable(enabled = saveEnabled, onClick = onSave)
                .padding(horizontal = 16.dp, vertical = 9.dp)
        ) {
            Text(
                "SAVE",
                style = MaterialTheme.typography.labelLarge,
                color = if (saveEnabled) accent else Slate,
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text.uppercase(),
        modifier = Modifier.padding(start = 4.dp, bottom = 7.dp),
        style = MaterialTheme.typography.labelSmall,
        color = Slate,
    )
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    suffix: String? = null,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Snow),
        label = { Text(label, style = MaterialTheme.typography.bodySmall, color = Slate) },
        trailingIcon = if (suffix != null) {
            { Text(suffix, style = MaterialTheme.typography.titleSmall, color = Slate) }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = Hairline,
            focusedContainerColor = CardHigh,
            unfocusedContainerColor = CardHigh,
            cursorColor = accent,
            focusedLabelColor = accent,
            unfocusedLabelColor = Slate,
            focusedTextColor = Snow,
            unfocusedTextColor = Snow,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionFlow(
    suggestions: List<String>,
    onPick: (String) -> Unit,
) {
    if (suggestions.isEmpty()) return
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        suggestions.forEach { name ->
            Text(
                name,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(CardHigh)
                    .border(1.dp, Hairline, RoundedCornerShape(50))
                    .clickable { onPick(name) }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Mist,
            )
        }
    }
}

@Composable
private fun SelectableChip(
    label: String,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent.copy(alpha = 0.16f) else CardHigh)
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.48f) else Hairline,
                RoundedCornerShape(50),
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = when {
            selected -> accent
            !enabled -> Slate
            else -> Mist
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    date: LocalDate,
    accent: androidx.compose.ui.graphics.Color,
    onChange: (LocalDate) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    FieldLabel("Date")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardHigh)
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            .clickable { showPicker = true }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = Snow,
        )
        Icon(
            Icons.Outlined.DateRange,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(19.dp),
        )
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let {
                            onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        showPicker = false
                    }
                ) { Text("Use date", color = accent) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel", color = Slate) }
            },
        ) {
            DatePicker(
                state = state,
                title = null,
                headline = null,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    containerColor = CardHigh,
                    titleContentColor = Snow,
                    headlineContentColor = Snow,
                    weekdayContentColor = Slate,
                    subheadContentColor = Slate,
                    navigationContentColor = Mist,
                    yearContentColor = Snow,
                    currentYearContentColor = accent,
                    selectedYearContentColor = Abyss,
                    selectedYearContainerColor = accent,
                    dayContentColor = Snow,
                    disabledDayContentColor = Slate,
                    selectedDayContentColor = Abyss,
                    disabledSelectedDayContentColor = Slate,
                    selectedDayContainerColor = accent,
                    disabledSelectedDayContainerColor = CardHigh,
                    todayContentColor = accent,
                    todayDateBorderColor = accent,
                    dayInSelectionRangeContentColor = Snow,
                    dayInSelectionRangeContainerColor = accent.copy(alpha = 0.22f),
                    dividerColor = Hairline,
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutLinkSelector(
    selectedTypeKey: String?,
    workouts: List<Workout>,
    accent: androidx.compose.ui.graphics.Color,
    onSelect: (String?) -> Unit,
) {
    FieldLabel("Source workout")
    if (workouts.isEmpty() && selectedTypeKey == null) {
        Text(
            "No workout stored for this date.",
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Slate,
        )
        return
    }
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        SelectableChip(
            label = "None",
            selected = selectedTypeKey == null,
            accent = accent,
            onClick = { onSelect(null) },
        )
        workouts.forEach { workout ->
            SelectableChip(
                label = WorkoutText.displayType(workout.typeLabel),
                selected = selectedTypeKey == workout.typeKey,
                accent = accent,
                onClick = { onSelect(workout.typeKey) },
            )
        }
        if (selectedTypeKey != null && workouts.none { it.typeKey == selectedTypeKey }) {
            SelectableChip(
                label = "Linked workout",
                selected = true,
                accent = accent,
                onClick = { onSelect(selectedTypeKey) },
            )
        }
    }
}

@Composable
private fun EditorLoading(onBack: () -> Unit, message: String = "Loading…") {
    HcBackground {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().padding(ScreenPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAction(
                    icon = { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Snow) },
                    onClick = onBack,
                )
            }
            Text(
                message,
                modifier = Modifier.padding(horizontal = ScreenPadding),
                style = MaterialTheme.typography.bodyMedium,
                color = Slate,
            )
        }
    }
}

@Composable
private fun RemoveRecordButton(onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("Delete this record", style = MaterialTheme.typography.titleSmall, color = Danger)
    }
}

// ── Weight editor ──────────────────────────────────────────────────────────

private data class WeightLoad(val loaded: Boolean, val record: WeightRecord?)

@Composable
fun WeightEditorScreen(
    recordId: Long?,
    initialExerciseKey: String?,
    initialName: String?,
    initialDate: String?,
    initialTypeKey: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val load by produceState(WeightLoad(false, null), recordId) {
        value = withContext(Dispatchers.IO) {
            if (recordId == null) WeightLoad(true, null)
            else WeightLoad(true, RecordStore.getWeight(context, recordId))
        }
    }

    when {
        !load.loaded -> EditorLoading(onBack)
        recordId != null && load.record == null ->
            EditorLoading(onBack, "That record no longer exists.")
        else -> WeightEditorForm(
            existing = load.record,
            initialExerciseKey = initialExerciseKey,
            initialName = initialName,
            initialDate = initialDate,
            initialTypeKey = initialTypeKey,
            onBack = onBack,
            onSaved = onSaved,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeightEditorForm(
    existing: WeightRecord?,
    initialExerciseKey: String?,
    initialName: String?,
    initialDate: String?,
    initialTypeKey: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = Amber
    var showDelete by remember { mutableStateOf(false) }

    var exerciseName by rememberSaveable {
        mutableStateOf(
            existing?.exerciseName
                ?: initialName
                ?: initialExerciseKey?.let { key ->
                    ExerciseLibrary.common.firstOrNull { it.key == key }?.name ?: prettifyKey(key)
                }.orEmpty()
        )
    }
    var repsText by rememberSaveable { mutableStateOf((existing?.reps ?: 1).toString()) }
    val reps = repsText.toIntOrNull() ?: 0
    var weightText by rememberSaveable {
        mutableStateOf(existing?.let { RecordLogic.formatWeight(it.weightKg) }.orEmpty())
    }
    val initialDateIso = existing?.achievedDate
        ?: initialDate
        ?: LocalDate.now().toString()
    var dateIso by rememberSaveable {
        mutableStateOf(
            runCatching { LocalDate.parse(initialDateIso) }
                .getOrDefault(LocalDate.now())
                .toString()
        )
    }
    val date = LocalDate.parse(dateIso)
    var workoutTypeKey by rememberSaveable {
        mutableStateOf(existing?.workoutTypeKey ?: initialTypeKey)
    }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    val dayWorkouts by produceState(emptyList<Workout>(), dateIso) {
        value = withContext(Dispatchers.IO) { WorkoutStore.getByDate(context, dateIso) }
    }
    val weight = weightText.toDoubleOrNull()
    val canSave = exerciseName.isNotBlank() && weight != null && weight > 0 && reps in 1..99

    if (showDelete) {
        ConfirmDialog(
            title = "Delete this record?",
            message = "The attempt will be removed from this lift's history.",
            onConfirm = {
                showDelete = false
                scope.launch {
                    withContext(Dispatchers.IO) { existing?.let { RecordStore.deleteWeight(context, it.id) } }
                    DataRefresh.bump()
                    onSaved()
                }
            },
            onDismiss = { showDelete = false },
        )
    }

    HcBackground(accent = accent) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
        ) {
            EditorHeader(
                title = if (existing == null) "New weight record" else "Edit weight record",
                eyebrow = "Weights",
                accent = accent,
                saveEnabled = canSave && !saving,
                onBack = onBack,
                onSave = {
                    if (!canSave || saving) return@EditorHeader
                    val kg = weight ?: return@EditorHeader
                    saving = true
                    val record = WeightRecord(
                        id = existing?.id ?: 0L,
                        exerciseKey = RecordLogic.exerciseKey(exerciseName.trim()),
                        exerciseName = exerciseName.trim(),
                        reps = reps,
                        weightKg = kg,
                        achievedDate = dateIso,
                        workoutDate = if (workoutTypeKey != null) dateIso else null,
                        workoutTypeKey = workoutTypeKey,
                        note = note.trim().ifBlank { null },
                        createdAtMs = existing?.createdAtMs ?: 0L,
                    )
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                if (existing == null) RecordStore.addWeight(context, record)
                                else RecordStore.updateWeight(context, record)
                            }
                            DataRefresh.bump()
                            onSaved()
                        } catch (_: Exception) {
                            saving = false
                        }
                    }
                },
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenPadding)
            ) {
                FieldLabel("Exercise")
                FormField(
                    value = exerciseName,
                    onValueChange = {
                        exerciseName = it
                    },
                    label = "Lift name",
                    accent = accent,
                )
                SuggestionFlow(
                    suggestions = ExerciseLibrary.common
                        .filter {
                            exerciseName.isBlank() ||
                                (it.name.contains(exerciseName, ignoreCase = true) &&
                                    !it.name.equals(exerciseName, ignoreCase = true))
                        }
                        .take(8)
                        .map { it.name },
                    onPick = {
                        exerciseName = it
                    },
                )

                Spacer(Modifier.height(20.dp))
                FieldLabel("Rep max")
                FormField(
                    value = repsText,
                    onValueChange = { repsText = it.filter(Char::isDigit).take(2) },
                    label = "Reps",
                    accent = accent,
                    keyboardType = KeyboardType.Number,
                    suffix = "RM",
                )
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    listOf(1, 2, 3, 5, 8, 10).forEach { quick ->
                        SelectableChip(
                            label = "${quick}RM",
                            selected = reps == quick,
                            accent = accent,
                            onClick = { repsText = quick.toString() },
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                FieldLabel("Weight")
                FormField(
                    value = weightText,
                    onValueChange = { text ->
                        if (text.count { it == '.' } <= 1 && text.all { it.isDigit() || it == '.' }) {
                            weightText = text
                        }
                    },
                    label = "Weight",
                    accent = accent,
                    keyboardType = KeyboardType.Decimal,
                    suffix = "kg",
                )

                Spacer(Modifier.height(20.dp))
                DateField(
                    date = date,
                    accent = accent,
                    onChange = {
                        dateIso = it.toString()
                        workoutTypeKey = null
                    },
                )

                Spacer(Modifier.height(20.dp))
                WorkoutLinkSelector(
                    selectedTypeKey = workoutTypeKey,
                    workouts = dayWorkouts,
                    accent = accent,
                    onSelect = { workoutTypeKey = it },
                )

                Spacer(Modifier.height(20.dp))
                FieldLabel("Note")
                FormField(
                    value = note,
                    onValueChange = { note = it },
                    label = "Optional",
                    accent = accent,
                )

                if (!canSave) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Add a lift, a rep max and a weight above zero to save.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate,
                    )
                }

                if (existing != null) {
                    Spacer(Modifier.height(22.dp))
                    RemoveRecordButton { showDelete = true }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

// ── Benchmark editor ───────────────────────────────────────────────────────

private data class BenchmarkLoad(val loaded: Boolean, val record: BenchmarkRecord?)

@Composable
fun BenchmarkEditorScreen(
    recordId: Long?,
    initialKey: String?,
    initialName: String?,
    initialDate: String?,
    initialTypeKey: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val load by produceState(BenchmarkLoad(false, null), recordId) {
        value = withContext(Dispatchers.IO) {
            if (recordId == null) BenchmarkLoad(true, null)
            else BenchmarkLoad(true, RecordStore.getBenchmark(context, recordId))
        }
    }

    when {
        !load.loaded -> EditorLoading(onBack)
        recordId != null && load.record == null ->
            EditorLoading(onBack, "That record no longer exists.")
        else -> BenchmarkEditorForm(
            existing = load.record,
            initialKey = initialKey,
            initialName = initialName,
            initialDate = initialDate,
            initialTypeKey = initialTypeKey,
            onBack = onBack,
            onSaved = onSaved,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BenchmarkEditorForm(
    existing: BenchmarkRecord?,
    initialKey: String?,
    initialName: String?,
    initialDate: String?,
    initialTypeKey: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = Teal
    var showDelete by remember { mutableStateOf(false) }

    val libraryEntry = initialKey?.let { key -> BenchmarkLibrary.common.firstOrNull { it.key == key } }
    var name by rememberSaveable {
        mutableStateOf(
            existing?.benchmarkName
                ?: initialName
                ?: libraryEntry?.name
                ?: initialKey?.let { prettifyKey(it) }
                .orEmpty()
        )
    }
    var selectedTypeKey by rememberSaveable {
        mutableStateOf((existing?.scoreType ?: libraryEntry?.scoreType ?: BenchmarkScoreType.TIME).key)
    }
    val selectedType = BenchmarkScoreType.fromKey(selectedTypeKey)
    val initialDateIso = existing?.achievedDate
        ?: initialDate
        ?: LocalDate.now().toString()
    var dateIso by rememberSaveable {
        mutableStateOf(
            runCatching { LocalDate.parse(initialDateIso) }
                .getOrDefault(LocalDate.now())
                .toString()
        )
    }
    val date = LocalDate.parse(dateIso)
    var workoutTypeKey by rememberSaveable { mutableStateOf(existing?.workoutTypeKey ?: initialTypeKey) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var saving by remember { mutableStateOf(false) }

    var minutesText by rememberSaveable {
        mutableIntStateOf(existing?.takeIf { it.scoreType == BenchmarkScoreType.TIME }?.let { (it.scoreValue / 60).toInt() } ?: 0)
    }
    var secondsText by rememberSaveable {
        mutableIntStateOf(existing?.takeIf { it.scoreType == BenchmarkScoreType.TIME }?.let { (it.scoreValue % 60).toInt() } ?: 0)
    }
    var valueText by rememberSaveable {
        mutableStateOf(
            existing?.takeIf {
                it.scoreType == BenchmarkScoreType.REPS ||
                    it.scoreType == BenchmarkScoreType.LOAD ||
                    it.scoreType == BenchmarkScoreType.POINTS
            }?.let { RecordLogic.formatWeight(it.scoreValue) }.orEmpty()
        )
    }
    var roundsText by rememberSaveable {
        mutableStateOf(
            existing?.takeIf { it.scoreType == BenchmarkScoreType.ROUNDS_REPS }
                ?.let { RecordLogic.formatWeight(it.scoreValue) }.orEmpty()
        )
    }
    var repsText by rememberSaveable {
        mutableStateOf(
            existing?.takeIf { it.scoreType == BenchmarkScoreType.ROUNDS_REPS }
                ?.let { RecordLogic.formatWeight(it.scoreValue2) }.orEmpty()
        )
    }
    var otherText by rememberSaveable {
        mutableStateOf(existing?.takeIf { it.scoreType == BenchmarkScoreType.OTHER }?.scoreText.orEmpty())
    }

    val benchmarkKey = RecordLogic.benchmarkKey(name)
    val knownType by produceState<BenchmarkScoreType?>(null, benchmarkKey, name.isBlank()) {
        value = if (name.isBlank() || existing != null) null
        else withContext(Dispatchers.IO) {
            RecordStore.getBenchmarksFor(context, benchmarkKey).firstOrNull()?.scoreType
        }
    }
    val effectiveType = existing?.scoreType ?: knownType ?: selectedType
    val typeLocked = existing != null || knownType != null

    val dayWorkouts by produceState(emptyList<Workout>(), dateIso) {
        value = withContext(Dispatchers.IO) { WorkoutStore.getByDate(context, dateIso) }
    }

    val canSave = name.isNotBlank() && when (effectiveType) {
        BenchmarkScoreType.TIME -> (minutesText * 60 + secondsText) > 0
        BenchmarkScoreType.REPS,
        BenchmarkScoreType.LOAD,
        BenchmarkScoreType.POINTS -> (valueText.toDoubleOrNull() ?: 0.0) > 0
        BenchmarkScoreType.ROUNDS_REPS ->
            (roundsText.toDoubleOrNull() ?: 0.0) + (repsText.toDoubleOrNull() ?: 0.0) > 0
        BenchmarkScoreType.OTHER -> otherText.isNotBlank()
    }

    if (showDelete) {
        ConfirmDialog(
            title = "Delete this result?",
            message = "It will be removed from this benchmark's history.",
            onConfirm = {
                showDelete = false
                scope.launch {
                    withContext(Dispatchers.IO) { existing?.let { RecordStore.deleteBenchmark(context, it.id) } }
                    DataRefresh.bump()
                    onSaved()
                }
            },
            onDismiss = { showDelete = false },
        )
    }

    HcBackground(accent = accent) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
        ) {
            EditorHeader(
                title = if (existing == null) "New result" else "Edit result",
                eyebrow = "Benchmarks",
                accent = accent,
                saveEnabled = canSave && !saving,
                onBack = onBack,
                onSave = {
                    if (!canSave || saving) return@EditorHeader
                    saving = true
                    val record = buildBenchmark(
                        existing = existing,
                        name = name.trim(),
                        type = effectiveType,
                        minutes = minutesText,
                        seconds = secondsText,
                        value = valueText.toDoubleOrNull() ?: 0.0,
                        rounds = roundsText.toDoubleOrNull() ?: 0.0,
                        reps = repsText.toDoubleOrNull() ?: 0.0,
                        other = otherText.trim(),
                        date = dateIso,
                        workoutTypeKey = workoutTypeKey,
                        note = note.trim().ifBlank { null },
                    )
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                if (existing == null) RecordStore.addBenchmark(context, record)
                                else RecordStore.updateBenchmark(context, record)
                            }
                            DataRefresh.bump()
                            onSaved()
                        } catch (_: Exception) {
                            saving = false
                        }
                    }
                },
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenPadding)
            ) {
                FieldLabel("Benchmark")
                FormField(
                    value = name,
                    onValueChange = {
                        name = it
                    },
                    label = "Name",
                    accent = accent,
                )
                SuggestionFlow(
                    suggestions = BenchmarkLibrary.common
                        .filter {
                            name.isBlank() ||
                                (it.name.contains(name, ignoreCase = true) &&
                                    !it.name.equals(name, ignoreCase = true))
                        }
                        .take(8)
                        .map { it.name },
                    onPick = {
                        name = it
                        selectedTypeKey = (BenchmarkLibrary.common.firstOrNull { b -> b.name == it }?.scoreType
                            ?: selectedType).key
                    },
                )

                Spacer(Modifier.height(20.dp))
                FieldLabel("Score type")
                if (typeLocked) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SelectableChip(
                            label = effectiveType.label,
                            selected = true,
                            accent = accent,
                            enabled = false,
                            onClick = {},
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Matches the existing history",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate,
                        )
                    }
                } else {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        BenchmarkScoreType.entries.forEach { type ->
                            SelectableChip(
                                label = type.label,
                                selected = selectedType == type,
                                accent = accent,
                                onClick = { selectedTypeKey = type.key },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                FieldLabel("Score")
                when (effectiveType) {
                    BenchmarkScoreType.TIME -> Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FormField(
                            value = if (minutesText == 0) "" else minutesText.toString(),
                            onValueChange = { minutesText = it.filter(Char::isDigit).toIntOrNull() ?: 0 },
                            label = "Minutes",
                            accent = accent,
                            modifier = Modifier.weight(1f),
                            keyboardType = KeyboardType.Number,
                        )
                        FormField(
                            value = if (secondsText == 0) "" else secondsText.toString(),
                            onValueChange = { secondsText = it.filter(Char::isDigit).toIntOrNull() ?: 0 },
                            label = "Seconds",
                            accent = accent,
                            modifier = Modifier.weight(1f),
                            keyboardType = KeyboardType.Number,
                        )
                    }
                    BenchmarkScoreType.REPS -> FormField(
                        value = valueText,
                        onValueChange = { valueText = decimalInput(it) },
                        label = "Reps",
                        accent = accent,
                        keyboardType = KeyboardType.Number,
                    )
                    BenchmarkScoreType.LOAD -> FormField(
                        value = valueText,
                        onValueChange = { valueText = decimalInput(it) },
                        label = "Weight",
                        accent = accent,
                        keyboardType = KeyboardType.Decimal,
                        suffix = "kg",
                    )
                    BenchmarkScoreType.POINTS -> FormField(
                        value = valueText,
                        onValueChange = { valueText = decimalInput(it) },
                        label = "Points",
                        accent = accent,
                        keyboardType = KeyboardType.Decimal,
                    )
                    BenchmarkScoreType.ROUNDS_REPS -> Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FormField(
                            value = roundsText,
                            onValueChange = { roundsText = decimalInput(it) },
                            label = "Rounds",
                            accent = accent,
                            modifier = Modifier.weight(1f),
                            keyboardType = KeyboardType.Number,
                        )
                        FormField(
                            value = repsText,
                            onValueChange = { repsText = decimalInput(it) },
                            label = "Extra reps",
                            accent = accent,
                            modifier = Modifier.weight(1f),
                            keyboardType = KeyboardType.Number,
                        )
                    }
                    BenchmarkScoreType.OTHER -> FormField(
                        value = otherText,
                        onValueChange = { otherText = it },
                        label = "Score",
                        accent = accent,
                    )
                }

                Spacer(Modifier.height(20.dp))
                DateField(
                    date = date,
                    accent = accent,
                    onChange = {
                        dateIso = it.toString()
                        workoutTypeKey = null
                    },
                )

                Spacer(Modifier.height(20.dp))
                WorkoutLinkSelector(
                    selectedTypeKey = workoutTypeKey,
                    workouts = dayWorkouts,
                    accent = accent,
                    onSelect = { workoutTypeKey = it },
                )

                Spacer(Modifier.height(20.dp))
                FieldLabel("Note")
                FormField(
                    value = note,
                    onValueChange = { note = it },
                    label = "Optional",
                    accent = accent,
                )

                if (!canSave) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Fill in the benchmark name and a valid score to save.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate,
                    )
                }

                if (existing != null) {
                    Spacer(Modifier.height(22.dp))
                    RemoveRecordButton { showDelete = true }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

private fun buildBenchmark(
    existing: BenchmarkRecord?,
    name: String,
    type: BenchmarkScoreType,
    minutes: Int,
    seconds: Int,
    value: Double,
    rounds: Double,
    reps: Double,
    other: String,
    date: String,
    workoutTypeKey: String?,
    note: String?,
): BenchmarkRecord {
    val scoreSeconds = (minutes * 60 + seconds).toDouble()
    val scoreValue = when (type) {
        BenchmarkScoreType.TIME -> scoreSeconds
        BenchmarkScoreType.ROUNDS_REPS -> rounds
        BenchmarkScoreType.OTHER -> 0.0
        else -> value
    }
    val scoreValue2 = if (type == BenchmarkScoreType.ROUNDS_REPS) reps else 0.0
    val scoreText = when (type) {
        BenchmarkScoreType.OTHER -> other
        else -> ""
    }
    return BenchmarkRecord(
        id = existing?.id ?: 0L,
        benchmarkKey = RecordLogic.benchmarkKey(name),
        benchmarkName = name,
        scoreType = type,
        scoreValue = scoreValue,
        scoreValue2 = scoreValue2,
        scoreText = scoreText,
        achievedDate = date,
        workoutDate = if (workoutTypeKey != null) date else null,
        workoutTypeKey = workoutTypeKey,
        note = note,
        createdAtMs = existing?.createdAtMs ?: 0L,
    )
}

private fun decimalInput(raw: String): String =
    if (raw.count { it == '.' } <= 1 && raw.all { it.isDigit() || it == '.' }) raw else raw.dropLast(1)

private fun prettifyKey(key: String): String =
    key.replace('_', ' ').split(' ').joinToString(" ") { part ->
        part.replaceFirstChar { it.uppercase() }
    }
