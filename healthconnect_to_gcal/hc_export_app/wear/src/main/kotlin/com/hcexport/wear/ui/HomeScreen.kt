package com.hcexport.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Text
import com.hcexport.shared.JournalDayDto
import com.hcexport.shared.Snapshot
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    snapshot: Snapshot?,
    refreshing: Boolean,
    onJournal: () -> Unit,
    onEntry: (date: String, key: String) -> Unit,
    onStartLive: (date: String, typeKey: String) -> Unit,
    onRefresh: () -> Unit,
) {
    val today = LocalDate.now().toString()
    val todayItems = snapshot?.days?.firstOrNull { it.date == today }?.items.orEmpty()
    val recent = snapshot?.days?.filter { it.date < today }?.take(3).orEmpty()
    val upcoming = snapshot?.days
        ?.filter { it.date > today }
        ?.sortedBy { it.date }
        ?.take(4)
        .orEmpty()
    val state = rememberScalingLazyListState()

    ScalingLazyColumn(
        state = state,
        modifier = Modifier.fillMaxWidth(),
        autoCentering = null,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        item { WatchClock(Modifier.fillMaxWidth()) }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(Teal, Teal.copy(alpha = 0.55f)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "HC",
                        style = LabelStyle,
                        color = Abyss,
                    )
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("HC SYNC", style = LabelStyle, color = Snow)
                    Text(
                        LocalDate.now().format(
                            DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
                        ).uppercase(),
                        style = TinyLabelStyle,
                        color = Slate,
                    )
                }
            }
        }

        if (snapshot == null) {
            item {
                EmptyMessage(
                    "Waiting for the phone",
                    if (refreshing) {
                        "Reading the latest snapshot…"
                    } else {
                        "Sync on the phone to send the journal and your AI plans."
                    },
                )
            }
            item { RoundAction("Try again", Teal, onRefresh) }
        } else {
            if (todayItems.isEmpty()) {
                item {
                    WatchCard(accent = null) {
                        Text("Rest day", style = TitleSmallStyle, color = Snow)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Nothing logged for today. The journal below has the week.",
                            style = CaptionStyle,
                            color = Mist,
                        )
                    }
                }
            } else {
                item { SectionLabel("TODAY") }
                for (item in todayItems) {
                    item(key = "t:${item.key}") {
                        JournalRow(
                            item = item,
                            onClick = { onEntry(item.date, item.key) },
                            onPlay = if (item.hasPlan && item.kind == "workout") {
                                { onStartLive(item.date, item.typeKey) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(2.dp))
                WatchCard(
                    accent = Teal,
                    onClick = onJournal,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Journal", style = TitleStyle, color = Snow)
                            Text(
                                "${snapshot.days.sumOf { it.items.size }} entries · " +
                                    "${snapshot.days.size} days",
                                style = CaptionStyle,
                                color = Mist,
                            )
                        }
                        Text("›", style = ClockSmallStyle, color = Teal)
                    }
                }
            }

            if (upcoming.isNotEmpty()) {
                item { SectionLabel("UPCOMING", Periwinkle) }
                for (day in upcoming) {
                    for (item in day.items.take(2)) {
                        item(key = "u:${item.key}") {
                            JournalRow(
                                item = item.copy(
                                    summary = listOf(prettyDate(item.date), item.summary)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                ),
                                onClick = { onEntry(item.date, item.key) },
                                onPlay = if (item.hasPlan && item.kind == "workout") {
                                    { onStartLive(item.date, item.typeKey) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }

            if (recent.isNotEmpty()) {
                item { SectionLabel("RECENT") }
                for (day in recent) {
                    item(key = "d:${day.date}") {
                        RecentDay(day, onEntry)
                    }
                }
            }

            item {
                Spacer(Modifier.height(2.dp))
                Text(
                    "SYNCED ${relativeTime(snapshot.updatedAt)}",
                    modifier = Modifier.fillMaxWidth(),
                    style = TinyLabelStyle,
                    color = Slate,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun RecentDay(day: JournalDayDto, onEntry: (String, String) -> Unit) {
    val first = day.items.firstOrNull() ?: return
    val extra = day.items.size - 1
    WatchCard(onClick = { onEntry(first.date, first.key) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    prettyDate(day.date).uppercase(),
                    style = TinyLabelStyle,
                    color = Slate,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    first.title + if (extra > 0) " +$extra" else "",
                    style = TitleSmallStyle,
                    color = Snow,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    day.items.joinToString(" · ") { it.title },
                    style = CaptionStyle,
                    color = Mist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("›", style = ClockSmallStyle, color = Slate)
        }
    }
}

@Composable
fun SectionLabel(text: String, color: androidx.compose.ui.graphics.Color = Slate) {
    Text(
        text,
        modifier = Modifier.padding(start = 3.dp, top = 4.dp),
        style = TinyLabelStyle,
        color = color,
    )
}

@Composable
fun RoundAction(text: String, accent: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.15f))
            .border(1.dp, accent.copy(alpha = 0.40f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TitleSmallStyle, color = accent, textAlign = TextAlign.Center)
    }
}

private fun relativeTime(ms: Long): String {
    val diff = System.currentTimeMillis() - ms
    return when {
        diff < 60_000L -> "JUST NOW"
        diff < 3_600_000L -> "${diff / 60_000L} MIN AGO"
        diff < 24 * 3_600_000L -> "${diff / 3_600_000L} H AGO"
        else -> "${diff / (24 * 3_600_000L)} D AGO"
    }
}
