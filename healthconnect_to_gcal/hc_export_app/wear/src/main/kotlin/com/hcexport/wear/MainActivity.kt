package com.hcexport.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.Wearable
import com.hcexport.shared.JournalDto
import com.hcexport.shared.Snapshot
import com.hcexport.shared.WorkoutDto
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private val BG = Color(0xFF0B1121)
private val TEXT = Color(0xFFECF1F8)
private val MUTED = Color(0xFF8B9EC0)
private val ACCENT = Color(0xFF00D4C3)

private val DATE_FMT = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())

class MainActivity : ComponentActivity() {

    private val dataListener = DataClient.OnDataChangedListener {
        lifecycleScope.launch { WatchStore.refresh(this@MainActivity) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WatchStore.loadCache(this)
        lifecycleScope.launch { WatchStore.refresh(this@MainActivity) }
        setContent { HcWearApp() }
    }

    override fun onStart() {
        super.onStart()
        Wearable.getDataClient(this).addListener(dataListener)
    }

    override fun onStop() {
        Wearable.getDataClient(this).removeListener(dataListener)
        super.onStop()
    }
}

@Composable
private fun HcWearApp() {
    val snapshot by WatchStore.snapshot.collectAsState()
    val nav = rememberSwipeDismissableNavController()

    Box(Modifier.fillMaxSize().background(BG)) {
        SwipeDismissableNavHost(navController = nav, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    snapshot,
                    onWorkouts = { nav.navigate("workouts") },
                    onJournal = { nav.navigate("journal") },
                )
            }
            composable("workouts") {
                WorkoutDatesScreen(snapshot) { date -> nav.navigate("workout/$date") }
            }
            composable("workout/{date}") { entry ->
                val date = entry.arguments?.getString("date").orEmpty()
                WorkoutTypesScreen(snapshot, date) { typeKey ->
                    nav.navigate("workout/$date/$typeKey")
                }
            }
            composable("workout/{date}/{typeKey}") { entry ->
                WorkoutDetailScreen(
                    snapshot,
                    entry.arguments?.getString("date").orEmpty(),
                    entry.arguments?.getString("typeKey").orEmpty(),
                )
            }
            composable("journal") {
                JournalDatesScreen(snapshot) { date -> nav.navigate("journal/$date") }
            }
            composable("journal/{date}") { entry ->
                val date = entry.arguments?.getString("date").orEmpty()
                JournalEntriesScreen(snapshot, date) { id -> nav.navigate("journal/entry/$id") }
            }
            composable("journal/entry/{id}") { entry ->
                JournalDetailScreen(snapshot, entry.arguments?.getString("id").orEmpty())
            }
        }
    }
}

// ── Screens ────────────────────────────────────────────────────────────────

@Composable
private fun HomeScreen(
    snapshot: Snapshot?,
    onWorkouts: () -> Unit,
    onJournal: () -> Unit,
) {
    Screen("HC Sync") {
        item {
            Text(
                text = when (snapshot) {
                    null -> "Waiting for the phone…"
                    else -> "${snapshot.workouts.size} workouts · ${snapshot.journal.size} journal entries"
                },
                style = MaterialTheme.typography.body2,
                color = MUTED,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        item {
            Chip(
                onClick = onWorkouts,
                label = { Text("Workouts") },
                secondaryLabel = { Text(snapshot?.let { "${it.workouts.size} entries" } ?: "—") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Chip(
                onClick = onJournal,
                label = { Text("My workouts") },
                secondaryLabel = { Text(snapshot?.let { "${it.journal.size} entries" } ?: "—") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        snapshot?.let {
            item {
                Text(
                    text = "Updated ${prettyTime(it.updatedAt)}",
                    style = MaterialTheme.typography.caption1,
                    color = MUTED,
                )
            }
        }
    }
}

@Composable
private fun WorkoutDatesScreen(snapshot: Snapshot?, onDate: (String) -> Unit) {
    val grouped = snapshot?.workouts?.groupBy { it.date }.orEmpty().toSortedMap(reverseOrder())
    Screen("Workouts") {
        if (grouped.isEmpty()) {
            item { Text("No workouts yet. Sync on the phone.", style = MaterialTheme.typography.body2, color = MUTED) }
        }
        for ((date, items) in grouped) {
            item {
                Chip(
                    onClick = { onDate(date) },
                    label = { Text(prettyDate(date), color = TEXT) },
                    secondaryLabel = {
                        Text(
                            items.joinToString(", ") { it.typeLabel },
                            color = MUTED,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun WorkoutTypesScreen(snapshot: Snapshot?, date: String, onType: (String) -> Unit) {
    val items = snapshot?.workouts?.filter { it.date == date }?.sortedBy { it.sortOrder }.orEmpty()
    Screen(prettyDate(date)) {
        if (items.isEmpty()) {
            item { Text("No workouts on this date.", style = MaterialTheme.typography.body2, color = MUTED) }
        }
        for (workout in items) {
            item {
                Chip(
                    onClick = { onType(workout.typeKey) },
                    label = { Text(workout.typeLabel, color = TEXT) },
                    secondaryLabel = {
                        Text(
                            workout.content.lineSequence().firstOrNull().orEmpty(),
                            color = MUTED,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun WorkoutDetailScreen(snapshot: Snapshot?, date: String, typeKey: String) {
    val workout = snapshot?.workouts?.firstOrNull { it.date == date && it.typeKey == typeKey }
    Screen(workout?.typeLabel ?: "Workout") {
        item { Text(prettyDate(date), style = MaterialTheme.typography.body2, color = MUTED) }
        paragraphs(workout?.content ?: "Not found.")
    }
}

@Composable
private fun JournalDatesScreen(snapshot: Snapshot?, onDate: (String) -> Unit) {
    val grouped = snapshot?.journal?.groupBy { it.date }.orEmpty().toSortedMap(reverseOrder())
    Screen("My workouts") {
        if (grouped.isEmpty()) {
            item { Text("No journal workouts yet.", style = MaterialTheme.typography.body2, color = MUTED) }
        }
        for ((date, items) in grouped) {
            item {
                Chip(
                    onClick = { onDate(date) },
                    label = { Text(prettyDate(date), color = TEXT) },
                    secondaryLabel = {
                        Text(
                            items.joinToString(" · ") { it.title },
                            color = MUTED,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun JournalEntriesScreen(snapshot: Snapshot?, date: String, onEntry: (String) -> Unit) {
    val entries = snapshot?.journal?.filter { it.date == date }.orEmpty()
    Screen(prettyDate(date)) {
        if (entries.isEmpty()) {
            item { Text("Nothing logged.", style = MaterialTheme.typography.body2, color = MUTED) }
        }
        for (entry in entries) {
            item {
                Chip(
                    onClick = { onEntry(entry.id) },
                    label = { Text(entry.title, color = TEXT, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = {
                        Text(
                            entry.narrative.lineSequence().firstOrNull().orEmpty(),
                            color = MUTED,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun JournalDetailScreen(snapshot: Snapshot?, id: String) {
    val entry = snapshot?.journal?.firstOrNull { it.id == id }
    Screen(entry?.title ?: "Entry") {
        if (entry == null) {
            item { Text("Not found.", style = MaterialTheme.typography.body2, color = MUTED) }
            return@Screen
        }
        item { Text(prettyDate(entry.date), style = MaterialTheme.typography.body2, color = MUTED) }
        if (entry.narrative.isNotBlank()) {
            paragraphs(entry.narrative)
        }
        val stats = statsFor(entry)
        if (stats.isNotEmpty()) {
            item { Text(stats.joinToString("  ·  "), style = MaterialTheme.typography.caption1, color = ACCENT) }
        }
        val wod = entry.wodContent
        if (!wod.isNullOrBlank() && wod != entry.narrative) {
            item { Text("Workout", style = MaterialTheme.typography.title3, color = TEXT) }
            paragraphs(wod)
        }
        for (merged in entry.mergedWods) {
            item { Text("Merged: ${merged.title}", style = MaterialTheme.typography.title3, color = TEXT) }
            paragraphs(merged.content)
        }
    }
}

// ── Building blocks ────────────────────────────────────────────────────────

@Composable
private fun Screen(title: String, content: ScalingLazyListScope.() -> Unit) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
    ) {
        item {
            Text(
                text = title,
                style = MaterialTheme.typography.title2,
                color = ACCENT,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        content()
    }
}

private fun ScalingLazyListScope.paragraphs(text: String) {
    text.split("\n\n").filter { it.isNotBlank() }.forEach { paragraph ->
        item {
            Text(
                text = paragraph.trim(),
                style = MaterialTheme.typography.body2,
                color = TEXT,
            )
        }
    }
}

private fun statsFor(entry: JournalDto): List<String> = buildList {
    val minutes = (entry.endMs - entry.startMs) / 60_000
    if (minutes > 0) add(formatDuration(minutes))
    entry.distanceM?.let { add("%.2f km".format(it / 1000)) }
    entry.caloriesKcal?.let { add("${it.toInt()} kcal") }
    val avgHr = entry.avgHrBpm
    val maxHr = entry.maxHrBpm
    if (avgHr != null) {
        add(
            if (maxHr != null) "HR ${avgHr.toInt()}/${maxHr.toInt()}"
            else "HR ${avgHr.toInt()}"
        )
    }
    entry.steps?.let { add("$it steps") }
    if (entry.source.isNotBlank()) add(entry.source)
}

private fun formatDuration(totalMinutes: Long): String = when {
    totalMinutes >= 60 -> {
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        if (m > 0) "${h}h ${m}m" else "${h}h"
    }
    else -> "${totalMinutes}m"
}

private fun prettyDate(date: String): String =
    runCatching { LocalDate.parse(date).format(DATE_FMT) }.getOrDefault(date)

private fun prettyTime(ms: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(ms))
