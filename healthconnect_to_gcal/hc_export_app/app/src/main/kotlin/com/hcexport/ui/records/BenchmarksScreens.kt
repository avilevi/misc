package com.hcexport.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hcexport.BenchmarkRecord
import com.hcexport.R
import com.hcexport.RecordLogic
import com.hcexport.RecordStore
import com.hcexport.ui.CardGap
import com.hcexport.ui.ConfirmDialog
import com.hcexport.ui.DataRefresh
import com.hcexport.ui.EmptyState
import com.hcexport.ui.HcBackground
import com.hcexport.ui.HcCard
import com.hcexport.ui.HighlightOnArrival
import com.hcexport.ui.IconAction
import com.hcexport.ui.NumberText
import com.hcexport.ui.PageHeader
import com.hcexport.ui.PrimaryButton
import com.hcexport.ui.ScreenPadding
import com.hcexport.ui.SectionLabel
import com.hcexport.ui.Tag
import com.hcexport.ui.theme.CardHigh
import com.hcexport.ui.theme.Danger
import com.hcexport.ui.theme.HairlineSoft
import com.hcexport.ui.theme.Mist
import com.hcexport.ui.theme.Slate
import com.hcexport.ui.theme.Snow
import com.hcexport.ui.theme.Teal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class BenchmarksLoad(
    val loaded: Boolean = false,
    val rows: List<Pair<String, List<BenchmarkRecord>>> = emptyList(),
)

@Composable
fun BenchmarksScreen(
    onBack: () -> Unit,
    onOpenBenchmark: (benchmarkKey: String) -> Unit,
    onAdd: () -> Unit,
) {
    val context = LocalContext.current
    val refresh by DataRefresh.tick.collectAsState()
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick += 1
        onPauseOrDispose { }
    }

    val load by produceState(BenchmarksLoad(), refresh, resumeTick) {
        value = withContext(Dispatchers.IO) {
            BenchmarksLoad(
                loaded = true,
                rows = RecordStore.getBenchmarks(context)
                    .groupBy { it.benchmarkKey }
                    .toList()
                    .sortedBy { (_, records) -> records.first().benchmarkName.lowercase() },
            )
        }
    }
    val rows = load.rows

    HcBackground(accent = Teal) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            PageHeader(
                title = "Benchmarks",
                eyebrow = "Records",
                onBack = onBack,
                trailing = {
                    IconAction(
                        icon = { Icon(Icons.Outlined.Add, contentDescription = "Add a result", tint = Teal) },
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
                    "Best result and full history for every benchmark.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(16.dp))

                if (!load.loaded) {
                    com.hcexport.ui.LoadingLine("Loading benchmarks…", Teal)
                } else if (rows.isEmpty()) {
                    EmptyState(
                        iconRes = R.drawable.ic_stopwatch,
                        title = "No benchmarks yet",
                        message = "Record a time, reps, load or rounds for any named benchmark.",
                        accent = Teal,
                        actionText = "Add a result",
                        onAction = onAdd,
                    )
                } else {
                    rows.forEach { (key, records) ->
                        BenchmarkCard(
                            records = records,
                            onClick = { onOpenBenchmark(key) },
                        )
                        Spacer(Modifier.height(CardGap))
                    }
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("Add a result", onClick = onAdd, accent = Teal)
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun BenchmarkCard(records: List<BenchmarkRecord>, onClick: () -> Unit) {
    val best = RecordLogic.bestBenchmark(records)
    val latest = records.maxWithOrNull(
        compareBy<BenchmarkRecord> { it.achievedDate }.thenBy { it.createdAtMs }
    )
    HcCard(
        accent = Teal,
        contentPadding = PaddingValues(16.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Teal.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_stopwatch),
                    contentDescription = null,
                    tint = Teal,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    records.first().benchmarkName,
                    style = MaterialTheme.typography.titleLarge,
                    color = Snow,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Tag(
                    latest?.scoreType?.label?.uppercase() ?: "BENCHMARK",
                    Teal.copy(alpha = 0.9f),
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = Slate,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    "BEST",
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate,
                )
                Text(
                    best?.let { RecordLogic.formatScore(it) } ?: "—",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFeatureSettings = "tnum",
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = Teal,
                    maxLines = 1,
                )
            }
            Text(
                buildString {
                    append(if (records.size == 1) "1 result" else "${records.size} results")
                    latest?.let { append("\nlast ${RecordLogic.formatDateShort(it.achievedDate)}") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = Slate,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}

// ── Benchmark detail ───────────────────────────────────────────────────────

private data class BenchmarkDetailLoad(
    val loaded: Boolean = false,
    val records: List<BenchmarkRecord> = emptyList(),
)

@Composable
fun BenchmarkDetailScreen(
    benchmarkKey: String,
    focusRecordId: Long = 0L,
    origin: String = "",
    onBack: () -> Unit,
    onBackToOrigin: () -> Unit = onBack,
    onAdd: (benchmarkKey: String, name: String) -> Unit,
    onEdit: (id: Long) -> Unit,
    onOpenWorkout: (date: String, typeKey: String, recordId: Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val refresh by DataRefresh.tick.collectAsState()
    var pendingDelete by remember { mutableStateOf<BenchmarkRecord?>(null) }

    val load by produceState(BenchmarkDetailLoad(), benchmarkKey, refresh) {
        value = withContext(Dispatchers.IO) {
            BenchmarkDetailLoad(
                loaded = true,
                records = RecordStore.getBenchmarksFor(context, benchmarkKey),
            )
        }
    }
    val records = load.records

    val name = records.firstOrNull()?.benchmarkName
        ?: benchmarkKey.replace('_', ' ').split(' ').joinToString(" ") { part ->
            part.replaceFirstChar { it.uppercase() }
        }
    val best = RecordLogic.bestBenchmark(records)

    if (pendingDelete != null) {
        ConfirmDialog(
            title = "Delete this result?",
            message = "It will be removed from the history. The workout itself stays in the calendar.",
            onConfirm = {
                val record = pendingDelete
                pendingDelete = null
                if (record != null) {
                    scope.launch {
                        withContext(Dispatchers.IO) { RecordStore.deleteBenchmark(context, record.id) }
                        DataRefresh.bump()
                    }
                }
            },
            onDismiss = { pendingDelete = null },
        )
    }

    HcBackground(accent = Teal) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            PageHeader(
                title = name,
                eyebrow = "Benchmark",
                onBack = onBack,
                trailing = {
                    IconAction(
                        icon = { Icon(Icons.Outlined.Add, contentDescription = "Add a result", tint = Teal) },
                        onClick = { onAdd(benchmarkKey, name) },
                    )
                },
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenPadding)
            ) {
                if (origin.isNotBlank()) {
                    WorkoutLinkChip(
                        label = when (origin) {
                            "journal" -> "Back to journal entry"
                            "workout" -> "Back to workout"
                            else -> "Back"
                        },
                        onClick = onBackToOrigin,
                    )
                    Spacer(Modifier.height(14.dp))
                }
                if (!load.loaded) {
                    com.hcexport.ui.LoadingLine("Loading history…", Teal)
                } else if (best == null) {
                    EmptyState(
                        iconRes = R.drawable.ic_stopwatch,
                        title = "No results yet",
                        message = "Add the first result for $name.",
                        accent = Teal,
                        actionText = "Add a result",
                        onAction = { onAdd(benchmarkKey, name) },
                    )
                } else {
                    HcCard(accent = Teal, contentPadding = PaddingValues(20.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Tag(best.scoreType.label.uppercase(), Teal)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "PERSONAL BEST",
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                painterResource(R.drawable.ic_trophy),
                                contentDescription = null,
                                tint = Teal.copy(alpha = 0.7f),
                                modifier = Modifier.size(21.dp),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        val score = RecordLogic.formatScore(best)
                        NumberText(
                            score,
                            color = Snow,
                            size = if (score.length > 9) 32 else 48,
                            weight = FontWeight.Light,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Achieved ${RecordLogic.formatDate(best.achievedDate)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate,
                        )
                        if (best.workoutDate != null && best.workoutTypeKey != null) {
                            Spacer(Modifier.height(12.dp))
                            WorkoutLinkChip(
                                label = "View in journal",
                                onClick = {
                                    onOpenWorkout(best.workoutDate, best.workoutTypeKey, best.id)
                                },
                            )
                        }
                        if (!best.note.isNullOrBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(best.note, style = MaterialTheme.typography.bodySmall, color = Mist)
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    SectionLabel("History")
                    Spacer(Modifier.height(10.dp))
                    HcCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                        RecordLogic.benchmarkHistory(records).forEachIndexed { index, record ->
                            if (index > 0) {
                                Box(Modifier.fillMaxWidth().height(1.dp).background(HairlineSoft))
                            }
                            HighlightOnArrival(
                                active = record.id == focusRecordId,
                                color = Teal,
                            ) {
                                BenchmarkHistoryRow(
                                    record = record,
                                    isBest = record.id == best.id,
                                    onEdit = onEdit,
                                    onDelete = { pendingDelete = it },
                                    onOpenWorkout = onOpenWorkout,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(
                        "Add another result",
                        onClick = { onAdd(benchmarkKey, name) },
                        accent = Teal,
                    )
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun BenchmarkHistoryRow(
    record: BenchmarkRecord,
    isBest: Boolean,
    onEdit: (Long) -> Unit,
    onDelete: (BenchmarkRecord) -> Unit,
    onOpenWorkout: (String, String, Long) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    RecordLogic.formatScore(record),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFeatureSettings = "tnum",
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = Snow,
                )
                if (isBest) {
                    Spacer(Modifier.width(8.dp))
                    Tag("PB", Teal)
                }
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
                    label = "Journal · ${RecordLogic.formatDateShort(record.workoutDate)}",
                    onClick = {
                        onOpenWorkout(record.workoutDate, record.workoutTypeKey, record.id)
                    },
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
