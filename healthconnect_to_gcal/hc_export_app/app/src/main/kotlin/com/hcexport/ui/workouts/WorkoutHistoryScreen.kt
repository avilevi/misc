package com.hcexport.ui.workouts

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hcexport.ArchiveSummary
import com.hcexport.R
import com.hcexport.RecordLogic
import com.hcexport.WorkoutExport
import com.hcexport.WorkoutMonth
import com.hcexport.WorkoutStore
import com.hcexport.ui.DataRefresh
import com.hcexport.ui.EmptyState
import com.hcexport.ui.HcBackground
import com.hcexport.ui.HcCard
import com.hcexport.ui.LoadingLine
import com.hcexport.ui.NumberText
import com.hcexport.ui.PageHeader
import com.hcexport.ui.ScreenPadding
import com.hcexport.ui.SectionLabel
import com.hcexport.ui.Tag
import com.hcexport.ui.theme.CardHigh
import com.hcexport.ui.theme.Hairline
import com.hcexport.ui.theme.HairlineSoft
import com.hcexport.ui.theme.Mist
import com.hcexport.ui.theme.Periwinkle
import com.hcexport.ui.theme.Slate
import com.hcexport.ui.theme.Snow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

private data class HistoryLoad(
    val loaded: Boolean = false,
    val months: List<WorkoutMonth> = emptyList(),
    val summary: ArchiveSummary = ArchiveSummary(0, 0, null, null),
)

@Composable
fun WorkoutHistoryScreen(
    onBack: () -> Unit,
    onOpenMonth: (firstDateIso: String) -> Unit,
    onSync: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val refresh by DataRefresh.tick.collectAsState()
    var resumeTick by remember { mutableIntStateOf(0) }
    var exporting by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        resumeTick += 1
        onPauseOrDispose { }
    }

    val load by produceState(HistoryLoad(), refresh, resumeTick) {
        value = withContext(Dispatchers.IO) {
            HistoryLoad(
                loaded = true,
                months = WorkoutStore.getMonthCounts(context),
                summary = WorkoutStore.archiveSummary(context),
            )
        }
    }

    val sections = remember(load.months) {
        load.months.groupBy { it.year }.toList()
    }
    val years = remember(sections) { sections.map { it.first } }
    val listState = rememberLazyListState()
    var selectedYear by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(years) {
        if (selectedYear == null || selectedYear !in years) {
            selectedYear = years.firstOrNull()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        exporting = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val csv = WorkoutExport.toCsv(WorkoutStore.getAll(context))
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(csv.toByteArray(Charsets.UTF_8))
                    } ?: error("Could not open the selected file")
                }.isSuccess
            }
            exporting = false
            Toast.makeText(
                context,
                if (ok) "Workout history exported" else "Export failed",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    HcBackground(accent = Periwinkle) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            PageHeader(
                title = "History",
                eyebrow = "Workout archive",
                onBack = onBack,
                trailing = {
                    if (load.loaded && load.summary.workouts > 0) {
                        ExportPill(exporting) {
                            if (!exporting) {
                                exportLauncher.launch("hc-sync-workouts-${LocalDate.now()}.csv")
                            }
                        }
                    }
                },
            )

            when {
                !load.loaded -> LoadingLine("Loading your archive…", Periwinkle)
                load.months.isEmpty() -> EmptyState(
                    iconRes = R.drawable.ic_archive,
                    title = "No history yet",
                    message = "Sync to pull workouts. Everything ever pulled stays here — nothing is deleted when HYPR drops a date.",
                    accent = Periwinkle,
                    actionText = "Sync now",
                    onAction = onSync,
                )
                else -> {
                    Column(Modifier.padding(horizontal = ScreenPadding)) {
                        ArchiveSummaryCard(load.summary)
                        Spacer(Modifier.height(14.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(end = 4.dp),
                        ) {
                            itemsIndexed(years) { index, year ->
                                YearChip(
                                    year = year,
                                    selected = year == selectedYear,
                                    onClick = {
                                        selectedYear = year
                                        scope.launch { listState.animateScrollToItem(index) }
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(
                            start = ScreenPadding,
                            end = ScreenPadding,
                            top = 8.dp,
                            bottom = 34.dp,
                        ),
                    ) {
                        sections.forEach { (year, months) ->
                            item(key = "year-$year") {
                                YearSection(
                                    year = year,
                                    months = months,
                                    onOpenMonth = onOpenMonth,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportPill(exporting: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Periwinkle.copy(alpha = 0.13f))
            .border(1.dp, Periwinkle.copy(alpha = 0.26f), RoundedCornerShape(50))
            .clickable(enabled = !exporting, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (exporting) {
            CircularProgressIndicator(
                modifier = Modifier.size(13.dp),
                color = Periwinkle,
                strokeWidth = 1.8.dp,
            )
        } else {
            Icon(
                painterResource(R.drawable.ic_export),
                contentDescription = null,
                tint = Periwinkle,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            if (exporting) "SAVING" else "EXPORT",
            style = MaterialTheme.typography.labelSmall,
            color = Periwinkle,
        )
    }
}

@Composable
private fun ArchiveSummaryCard(summary: ArchiveSummary) {
    HcCard(accent = Periwinkle, contentPadding = PaddingValues(20.dp)) {
        SectionLabel("Kept forever", color = Periwinkle)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            NumberText(
                summary.workouts.toString(),
                color = Snow,
                size = 44,
                weight = FontWeight.Light,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.padding(bottom = 5.dp)) {
                Text(
                    if (summary.workouts == 1) "workout" else "workouts",
                    style = MaterialTheme.typography.titleSmall,
                    color = Mist,
                )
                Text(
                    "on ${summary.days} training ${if (summary.days == 1) "day" else "days"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            summary.firstDate?.let { "Pulled since ${RecordLogic.formatDate(it)}." }
                ?: "No workouts stored yet.",
            style = MaterialTheme.typography.bodySmall,
            color = Mist,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            "Every pulled workout is kept here. HYPR dropping a date never deletes it.",
            style = MaterialTheme.typography.bodySmall,
            color = Slate,
        )
    }
}

@Composable
private fun YearChip(year: Int, selected: Boolean, onClick: () -> Unit) {
    Text(
        year.toString(),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Periwinkle.copy(alpha = 0.18f) else CardHigh)
            .border(
                1.dp,
                if (selected) Periwinkle.copy(alpha = 0.45f) else Hairline,
                RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Periwinkle else Mist,
    )
}

@Composable
private fun YearSection(
    year: Int,
    months: List<WorkoutMonth>,
    onOpenMonth: (String) -> Unit,
) {
    val total = months.sumOf { it.workouts }
    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                year.toString(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.displaySmall,
                color = Snow,
            )
            Tag(
                if (total == 1) "1 WORKOUT" else "$total WORKOUTS",
                Periwinkle,
            )
        }
        HcCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            months.forEachIndexed { index, month ->
                if (index > 0) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(HairlineSoft))
                }
                MonthRow(month, onOpenMonth)
            }
        }
    }
}

@Composable
private fun MonthRow(month: WorkoutMonth, onOpenMonth: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onOpenMonth(month.firstDate) }
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                Month.of(month.month).getDisplayName(TextStyle.FULL, Locale.getDefault()),
                style = MaterialTheme.typography.titleLarge,
                color = Snow,
            )
            Text(
                buildString {
                    append(if (month.workouts == 1) "1 workout" else "${month.workouts} workouts")
                    append("  ·  ")
                    append(if (month.days == 1) "1 day" else "${month.days} days")
                },
                style = MaterialTheme.typography.bodySmall,
                color = Slate,
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = Slate,
            modifier = Modifier.size(20.dp),
        )
    }
}
