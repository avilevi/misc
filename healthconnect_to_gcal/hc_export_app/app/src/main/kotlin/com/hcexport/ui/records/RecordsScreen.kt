package com.hcexport.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hcexport.R
import com.hcexport.RecordLogic
import com.hcexport.RecordStore
import com.hcexport.ui.CardGap
import com.hcexport.ui.DataRefresh
import com.hcexport.ui.HcBackground
import com.hcexport.ui.HcCard
import com.hcexport.ui.HubCard
import com.hcexport.ui.LocalMinimalDesign
import com.hcexport.ui.PageHeader
import com.hcexport.ui.ScreenPadding
import com.hcexport.ui.SectionLabel
import com.hcexport.ui.theme.Amber
import com.hcexport.ui.theme.CardHigh
import com.hcexport.ui.theme.Hairline
import com.hcexport.ui.theme.Mist
import com.hcexport.ui.theme.Periwinkle
import com.hcexport.ui.theme.Slate
import com.hcexport.ui.theme.Snow
import com.hcexport.ui.theme.Teal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class RecentRecord(
    val title: String,
    val value: String,
    val date: String,
    val typeLabel: String,
    val accent: Color,
    val iconRes: Int,
    val onClick: () -> Unit,
)

private data class Overview(
    val lifts: Int = 0,
    val weightRecords: Int = 0,
    val benchmarks: Int = 0,
    val benchmarkResults: Int = 0,
    val recent: List<RecentRecord> = emptyList(),
)

@Composable
fun RecordsScreen(
    onBack: () -> Unit,
    onWeights: () -> Unit,
    onBenchmarks: () -> Unit,
    onOpenWeight: (exerciseKey: String) -> Unit,
    onOpenBenchmark: (benchmarkKey: String) -> Unit,
) {
    val context = LocalContext.current
    val refresh by DataRefresh.tick.collectAsState()
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick += 1
        onPauseOrDispose { }
    }

    val overview by produceState<Overview?>(null, refresh, resumeTick) {
        value = withContext(Dispatchers.IO) {
            val weights = RecordStore.getWeights(context)
            val benchmarks = RecordStore.getBenchmarks(context)
            val recent = buildList {
                weights.sortedByDescending { it.createdAtMs }.take(3).forEach { record ->
                    add(
                        RecentRecord(
                            title = "${record.reps}RM · ${record.exerciseName}",
                            value = "${RecordLogic.formatWeight(record.weightKg)} kg",
                            date = record.achievedDate,
                            typeLabel = "WEIGHT",
                            accent = Amber,
                            iconRes = R.drawable.ic_dumbbell,
                            onClick = { onOpenWeight(record.exerciseKey) },
                        )
                    )
                }
                benchmarks.sortedByDescending { it.createdAtMs }.take(3).forEach { record ->
                    add(
                        RecentRecord(
                            title = record.benchmarkName,
                            value = RecordLogic.formatScore(record),
                            date = record.achievedDate,
                            typeLabel = record.scoreType.label.uppercase(),
                            accent = Teal,
                            iconRes = R.drawable.ic_stopwatch,
                            onClick = { onOpenBenchmark(record.benchmarkKey) },
                        )
                    )
                }
            }.sortedByDescending { it.date }.take(4)

            Overview(
                lifts = RecordStore.countWeightExercises(context),
                weightRecords = weights.size,
                benchmarks = RecordStore.countBenchmarks(context),
                benchmarkResults = benchmarks.size,
                recent = recent,
            )
        }
    }

    val data = overview

    HcBackground(accent = Amber) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            PageHeader(
                title = "Records",
                eyebrow = "Personal bests",
                onBack = onBack,
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenPadding)
            ) {
                if (!LocalMinimalDesign.current) {
                    Text(
                        "Every number has the session behind it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Spacer(Modifier.height(20.dp))
                } else {
                    Spacer(Modifier.height(6.dp))
                }

                HubCard(
                    iconRes = R.drawable.ic_dumbbell,
                    title = "Weights",
                    subtitle = "1RM · 2RM · 3RM",
                    meta = when {
                        data == null -> null
                        data.weightRecords == 0 -> "NO RECORDS YET"
                        else -> "${data.lifts} LIFTS · ${data.weightRecords} RECORDS"
                    },
                    accent = Amber,
                    onClick = onWeights,
                )
                Spacer(Modifier.height(CardGap))
                HubCard(
                    iconRes = R.drawable.ic_stopwatch,
                    title = "Benchmarks",
                    subtitle = "Fran, Murph and your own",
                    meta = when {
                        data == null -> null
                        data.benchmarkResults == 0 -> "NO RESULTS YET"
                        else -> "${data.benchmarks} BENCHMARKS · ${data.benchmarkResults} RESULTS"
                    },
                    accent = Teal,
                    onClick = onBenchmarks,
                )

                if (data != null && data.recent.isNotEmpty()) {
                    Spacer(Modifier.height(28.dp))
                    SectionLabel("Recent")
                    Spacer(Modifier.height(10.dp))
                    HcCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp)) {
                        data.recent.forEachIndexed { index, item ->
                            if (index > 0) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp)
                                        .height(1.dp)
                                        .background(com.hcexport.ui.theme.HairlineSoft)
                                )
                            }
                            RecentRow(item)
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun RecentRow(item: RecentRecord) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .clickable(onClick = item.onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(item.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(item.iconRes),
                contentDescription = null,
                tint = item.accent,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall,
                color = Snow,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${RecordLogic.formatDateShort(item.date)}  ·  ${item.typeLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = Slate,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            item.value,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = Snow,
        )
    }
}
