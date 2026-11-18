package com.hcexport.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hcexport.BuildConfig
import com.hcexport.JournalStorage
import com.hcexport.R
import com.hcexport.RecordStore
import com.hcexport.WorkoutStore
import com.hcexport.ui.theme.Abyss
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class HomeStats(
    val journalEntries: Int = 0,
    val workouts: Int = 0,
    val records: Int = 0,
)

@Composable
fun HomeScreen(
    statusTitle: String,
    statusSubtitle: String,
    statusAccent: androidx.compose.ui.graphics.Color,
    syncing: Boolean,
    onSync: () -> Unit,
    onSettings: () -> Unit,
    onJournal: () -> Unit,
    onWorkouts: () -> Unit,
    onRecords: () -> Unit,
) {
    val context = LocalContext.current
    val refresh by DataRefresh.tick.collectAsState()
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick += 1
        onPauseOrDispose { }
    }

    val stats by produceState<HomeStats?>(null, refresh, resumeTick) {
        value = withContext(Dispatchers.IO) {
            HomeStats(
                journalEntries = runCatching { JournalStorage.getEntries(context).size }.getOrDefault(0),
                workouts = runCatching { WorkoutStore.count(context) }.getOrDefault(0),
                records = runCatching {
                    RecordStore.countWeightRecords(context) + RecordStore.countBenchmarkResults(context)
                }.getOrDefault(0),
            )
        }
    }

    HcBackground(accent = statusAccent) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
        ) {
            Spacer(Modifier.height(10.dp))
            HomeHeader(onSettings)
            Spacer(Modifier.height(26.dp))

            Text(
                "HC Sync",
                style = MaterialTheme.typography.displayLarge,
                color = Snow,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Health Connect  →  Calendar",
                style = MaterialTheme.typography.bodyMedium,
                color = Slate,
            )
            Spacer(Modifier.height(22.dp))

            SyncBar(
                title = statusTitle,
                subtitle = statusSubtitle,
                accent = statusAccent,
                syncing = syncing,
                onSync = onSync,
            )

            Spacer(Modifier.height(30.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel("Your training", modifier = Modifier.weight(1f))
                Text(
                    LocalDate.now().format(
                        DateTimeFormatter.ofPattern("EEE · d MMM", Locale.getDefault())
                    ).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate,
                )
            }
            Spacer(Modifier.height(12.dp))

            HubCard(
                iconRes = R.drawable.ic_journal,
                title = "Journal",
                subtitle = "Sessions, watch data and notes",
                meta = stats?.let { journalMeta(it.journalEntries) },
                accent = Teal,
                onClick = onJournal,
            )
            Spacer(Modifier.height(CardGap))
            HubCard(
                iconRes = R.drawable.ic_calendar,
                title = "Workouts",
                subtitle = "Your training calendar",
                meta = stats?.let { workoutMeta(it.workouts) },
                accent = Periwinkle,
                onClick = onWorkouts,
            )
            Spacer(Modifier.height(CardGap))
            HubCard(
                iconRes = R.drawable.ic_trophy,
                title = "Records",
                subtitle = "Personal bests and benchmarks",
                meta = stats?.let { recordMeta(it.records) },
                accent = Amber,
                onClick = onRecords,
            )

            Spacer(Modifier.height(30.dp))
            Text(
                "v${BuildConfig.VERSION_NAME}  ·  health data stays on this device",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = Slate,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HomeHeader(onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(Teal, Teal.copy(alpha = 0.55f)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "HC",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = Abyss,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "PERSONAL TRAINING LOG",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = Snow,
        )
        IconAction(
            icon = {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = Mist,
                    modifier = Modifier.size(19.dp),
                )
            },
            onClick = onSettings,
            background = CardHigh,
            border = true,
        )
    }
}

private fun journalMeta(count: Int): String =
    if (count == 0) "NO ENTRIES YET" else "$count ENTRIES"

private fun workoutMeta(count: Int): String =
    if (count == 0) "SYNC TO FILL THE CALENDAR" else "$count WORKOUTS STORED"

private fun recordMeta(count: Int): String =
    if (count == 0) "ADD YOUR FIRST RECORD" else "$count RECORDS TRACKED"

