package com.hcexport.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hcexport.R
import com.hcexport.RecordLogic
import com.hcexport.RecordStore
import com.hcexport.WeightRecord
import com.hcexport.ui.CardGap
import com.hcexport.ui.ConfirmDialog
import com.hcexport.ui.DataRefresh
import com.hcexport.ui.EmptyState
import com.hcexport.ui.HcBackground
import com.hcexport.ui.HcCard
import com.hcexport.ui.IconAction
import com.hcexport.ui.NumberText
import com.hcexport.ui.PageHeader
import com.hcexport.ui.PrimaryButton
import com.hcexport.ui.ScreenPadding
import com.hcexport.ui.SectionLabel
import com.hcexport.ui.Tag
import com.hcexport.ui.theme.Amber
import com.hcexport.ui.theme.CardHigh
import com.hcexport.ui.theme.Danger
import com.hcexport.ui.theme.Hairline
import com.hcexport.ui.theme.HairlineSoft
import com.hcexport.ui.theme.Mist
import com.hcexport.ui.theme.Slate
import com.hcexport.ui.theme.Snow
import com.hcexport.ui.theme.Teal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class WeightsLoad(
    val loaded: Boolean = false,
    val rows: List<com.hcexport.WeightTableRow> = emptyList(),
)

@Composable
fun WeightsScreen(
    onBack: () -> Unit,
    onOpenWeight: (exerciseKey: String) -> Unit,
    onAdd: () -> Unit,
) {
    val context = LocalContext.current
    val refresh by DataRefresh.tick.collectAsState()
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick += 1
        onPauseOrDispose { }
    }

    val load by produceState(WeightsLoad(), refresh, resumeTick) {
        value = withContext(Dispatchers.IO) {
            WeightsLoad(
                loaded = true,
                rows = RecordLogic.weightRows(RecordStore.getWeights(context)),
            )
        }
    }
    val rows = load.rows

    HcBackground(accent = Amber) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            PageHeader(
                title = "Weights",
                eyebrow = "Records",
                onBack = onBack,
                trailing = {
                    IconAction(
                        icon = {
                            Icon(Icons.Outlined.Add, contentDescription = "Add a lift", tint = Amber)
                        },
                        onClick = onAdd,
                    )
                },
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenPadding)
            ) {
                Text(
                    "Best successful attempt for each rep max.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(16.dp))

                if (!load.loaded) {
                    com.hcexport.ui.LoadingLine("Loading your lifts…", Amber)
                } else if (rows.isEmpty()) {
                    EmptyState(
                        iconRes = R.drawable.ic_dumbbell,
                        title = "No lifts yet",
                        message = "Add a squat, a deadlift or any lift you track. Your 1RM, 2RM and 3RM build the table.",
                        accent = Amber,
                        actionText = "Add a lift",
                        onAction = onAdd,
                    )
                } else {
                    HcCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                        TableHeader()
                        rows.forEachIndexed { index, row ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(if (index == 0) Hairline else HairlineSoft)
                            )
                            ExerciseRow(row, onClick = { onOpenWeight(row.exerciseKey) })
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "All weights in kilograms. Tap a lift for dates and source workouts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Spacer(Modifier.height(22.dp))
                    PrimaryButton("Add a lift", onClick = onAdd, accent = Amber)
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun TableHeader() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "EXERCISE",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = Slate,
        )
        listOf("1RM", "2RM", "3RM").forEach { label ->
            Text(
                label,
                modifier = Modifier.width(54.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Slate,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun ExerciseRow(row: com.hcexport.WeightTableRow, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                row.exerciseName,
                style = MaterialTheme.typography.titleSmall,
                color = Snow,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (row.totalRecords == 1) "1 attempt" else "${row.totalRecords} attempts",
                style = MaterialTheme.typography.bodySmall,
                color = Slate,
            )
        }
        listOf(1, 2, 3).forEach { reps ->
            val best = row.bestByReps[reps]
            Text(
                best?.let { RecordLogic.formatWeight(it.weightKg) } ?: "—",
                modifier = Modifier.width(54.dp),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFeatureSettings = "tnum",
                    fontWeight = if (best != null) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (best != null) Snow else Slate,
                textAlign = TextAlign.End,
            )
        }
    }
}

// ── Weight detail ──────────────────────────────────────────────────────────

private data class WeightDetailLoad(
    val loaded: Boolean = false,
    val records: List<WeightRecord> = emptyList(),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeightDetailScreen(
    exerciseKey: String,
    onBack: () -> Unit,
    onAdd: (exerciseKey: String, name: String) -> Unit,
    onEdit: (id: Long) -> Unit,
    onOpenWorkout: (date: String, typeKey: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val refresh by DataRefresh.tick.collectAsState()
    var pendingDelete by remember { mutableStateOf<WeightRecord?>(null) }

    val load by produceState(WeightDetailLoad(), exerciseKey, refresh) {
        value = withContext(Dispatchers.IO) {
            WeightDetailLoad(
                loaded = true,
                records = RecordStore.getWeightsFor(context, exerciseKey),
            )
        }
    }
    val records = load.records

    val name = records.firstOrNull()?.exerciseName
        ?: exerciseKey.replace('_', ' ').split(' ').joinToString(" ") { part ->
            part.replaceFirstChar { it.uppercase() }
        }
    val allReps = remember(records) { RecordLogic.allReps(records) }
    val bestByReps = remember(records, allReps) {
        allReps.associateWith { reps -> RecordLogic.bestWeight(records.filter { it.reps == reps }) }
    }
    val hero = bestByReps[1] ?: bestByReps.values.filterNotNull().minByOrNull { it.reps }

    if (pendingDelete != null) {
        ConfirmDialog(
            title = "Delete this record?",
            message = "The attempt will be removed. The workout itself stays in the calendar.",
            onConfirm = {
                val record = pendingDelete
                pendingDelete = null
                if (record != null) {
                    scope.launch {
                        withContext(Dispatchers.IO) { RecordStore.deleteWeight(context, record.id) }
                        DataRefresh.bump()
                    }
                }
            },
            onDismiss = { pendingDelete = null },
        )
    }

    HcBackground(accent = Amber) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            PageHeader(
                title = name,
                eyebrow = "Weight records",
                onBack = onBack,
                trailing = {
                    IconAction(
                        icon = {
                            Icon(Icons.Outlined.Add, contentDescription = "Add a record", tint = Amber)
                        },
                        onClick = { onAdd(exerciseKey, name) },
                    )
                },
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenPadding)
            ) {
                if (!load.loaded) {
                    com.hcexport.ui.LoadingLine("Loading records…", Amber)
                } else if (hero == null) {
                    EmptyState(
                        iconRes = R.drawable.ic_dumbbell,
                        title = "No records yet",
                        message = "Add the first attempt for $name.",
                        accent = Amber,
                        actionText = "Add a record",
                        onAction = { onAdd(exerciseKey, name) },
                    )
                } else {
                    BestHero(hero, onOpenWorkout)
                }

                if (load.loaded && records.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        allReps.forEach { reps ->
                            RepTile(
                                reps = reps,
                                record = bestByReps[reps],
                                modifier = Modifier.width(100.dp),
                            )
                        }
                        AddRepTile(onClick = { onAdd(exerciseKey, name) })
                    }
                    Spacer(Modifier.height(26.dp))
                    SectionLabel("History")
                    Spacer(Modifier.height(10.dp))
                    allReps.forEach { reps ->
                        val group = RecordLogic.weightHistory(records.filter { it.reps == reps })
                        RepHistoryCard(
                            reps = reps,
                            records = group,
                            onEdit = onEdit,
                            onDelete = { pendingDelete = it },
                            onOpenWorkout = onOpenWorkout,
                        )
                        Spacer(Modifier.height(CardGap))
                    }
                    Spacer(Modifier.height(6.dp))
                    PrimaryButton(
                        "Add another record",
                        onClick = { onAdd(exerciseKey, name) },
                        accent = Amber,
                    )
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun BestHero(record: WeightRecord, onOpenWorkout: (String, String) -> Unit) {
    HcCard(accent = Amber, contentPadding = PaddingValues(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Tag("${record.reps}RM", Amber)
            Spacer(Modifier.width(8.dp))
            Text(
                "PERSONAL BEST",
                style = MaterialTheme.typography.labelSmall,
                color = Slate,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painterResource(R.drawable.ic_trend),
                contentDescription = null,
                tint = Amber.copy(alpha = 0.65f),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            NumberText(
                RecordLogic.formatWeight(record.weightKg),
                color = Snow,
                size = 56,
                weight = FontWeight.Light,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                "kg",
                modifier = Modifier.padding(bottom = 7.dp),
                style = MaterialTheme.typography.titleMedium,
                color = Mist,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Achieved ${RecordLogic.formatDate(record.achievedDate)}",
            style = MaterialTheme.typography.bodySmall,
            color = Slate,
        )
        if (record.workoutDate != null && record.workoutTypeKey != null) {
            Spacer(Modifier.height(12.dp))
            WorkoutLinkChip(
                label = "View source workout",
                onClick = { onOpenWorkout(record.workoutDate, record.workoutTypeKey) },
            )
        }
        if (!record.note.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                record.note,
                style = MaterialTheme.typography.bodySmall,
                color = Mist,
            )
        }
    }
}

@Composable
private fun AddRepTile(onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .width(100.dp)
            .heightIn(min = 104.dp)
            .clip(shape)
            .background(CardHigh.copy(alpha = 0.55f))
            .border(1.dp, Hairline, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = null,
                tint = Amber,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "Add RM",
                style = MaterialTheme.typography.bodySmall,
                color = Mist,
            )
        }
    }
}

@Composable
private fun RepTile(reps: Int, record: WeightRecord?, modifier: Modifier = Modifier) {
    HcCard(modifier = modifier, contentPadding = PaddingValues(12.dp)) {
        Text(
            "${reps}RM",
            style = MaterialTheme.typography.labelSmall,
            color = if (record != null) Amber else Slate,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            record?.let { RecordLogic.formatWeight(it.weightKg) } ?: "—",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFeatureSettings = "tnum",
                fontWeight = FontWeight.SemiBold,
            ),
            color = if (record != null) Snow else Slate,
        )
        Text(
            if (record != null) RecordLogic.formatDateShort(record.achievedDate) + " · kg" else "Not set",
            style = MaterialTheme.typography.bodySmall,
            color = Slate,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RepHistoryCard(
    reps: Int,
    records: List<WeightRecord>,
    onEdit: (Long) -> Unit,
    onDelete: (WeightRecord) -> Unit,
    onOpenWorkout: (String, String) -> Unit,
) {
    HcCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${reps}RM",
                style = MaterialTheme.typography.titleMedium,
                color = Snow,
                modifier = Modifier.weight(1f),
            )
            val best = RecordLogic.bestWeight(records)
            if (best != null) {
                Tag("BEST ${RecordLogic.formatWeight(best.weightKg)} KG", Amber)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (records.isEmpty()) {
            Text(
                "No ${reps}RM recorded yet.",
                style = MaterialTheme.typography.bodySmall,
                color = Slate,
            )
        } else {
            records.forEachIndexed { index, record ->
                if (index > 0) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(HairlineSoft))
                }
                WeightHistoryRow(record, onEdit, onDelete, onOpenWorkout)
            }
        }
    }
}

@Composable
private fun WeightHistoryRow(
    record: WeightRecord,
    onEdit: (Long) -> Unit,
    onDelete: (WeightRecord) -> Unit,
    onOpenWorkout: (String, String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    RecordLogic.formatWeight(record.weightKg),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFeatureSettings = "tnum",
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = Snow,
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    "kg",
                    modifier = Modifier.padding(bottom = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                )
            }
            Text(
                RecordLogic.formatDate(record.achievedDate),
                style = MaterialTheme.typography.bodySmall,
                color = Slate,
            )
            if (!record.note.isNullOrBlank()) {
                Text(record.note, style = MaterialTheme.typography.bodySmall, color = Mist)
            }
            if (record.workoutDate != null && record.workoutTypeKey != null) {
                Spacer(Modifier.height(5.dp))
                WorkoutLinkChip(
                    label = "Workout · ${RecordLogic.formatDateShort(record.workoutDate)}",
                    onClick = { onOpenWorkout(record.workoutDate, record.workoutTypeKey) },
                    compact = true,
                )
            }
        }
        IconAction(
            icon = { Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = Slate, modifier = Modifier.size(17.dp)) },
            onClick = { onEdit(record.id) },
            border = false,
            background = CardHigh,
        )
        Spacer(Modifier.width(4.dp))
        IconAction(
            icon = { Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Danger, modifier = Modifier.size(17.dp)) },
            onClick = { onDelete(record) },
            border = false,
            background = CardHigh,
        )
    }
}

@Composable
fun WorkoutLinkChip(label: String, onClick: () -> Unit, compact: Boolean = false) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Teal.copy(alpha = 0.09f))
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (compact) 9.dp else 12.dp,
                vertical = if (compact) 4.dp else 7.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_calendar),
            contentDescription = null,
            tint = Teal,
            modifier = Modifier.size(if (compact) 12.dp else 15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleSmall,
            color = Teal,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = Teal,
            modifier = Modifier.size(if (compact) 12.dp else 15.dp),
        )
    }
}
