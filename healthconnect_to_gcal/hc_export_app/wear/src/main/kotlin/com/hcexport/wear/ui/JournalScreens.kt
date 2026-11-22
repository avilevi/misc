package com.hcexport.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Text
import com.hcexport.InteractivePlan
import com.hcexport.InteractiveRows
import com.hcexport.InteractiveResults
import com.hcexport.LiveWorkout
import com.hcexport.shared.JournalItemDto
import com.hcexport.shared.Snapshot
import com.hcexport.wear.live.WatchLiveSync

@Composable
fun JournalListScreen(
    snapshot: Snapshot?,
    onEntry: (date: String, key: String) -> Unit,
    onStartLive: (date: String, typeKey: String) -> Unit,
) {
    val days = snapshot?.days.orEmpty()
    val state = rememberScalingLazyListState()

    ScalingLazyColumn(
        state = state,
        modifier = Modifier.fillMaxWidth(),
        autoCentering = null,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { WatchClock(Modifier.fillMaxWidth()) }
        item {
            Text(
                "Journal",
                modifier = Modifier.padding(start = 3.dp),
                style = TitleStyle,
                color = Snow,
            )
        }

        if (days.isEmpty()) {
            item {
                EmptyMessage(
                    "No entries yet",
                    "Sync on the phone to bring the journal to the wrist.",
                )
            }
        }

        var lastDate = ""
        for (day in days) {
            if (day.date != lastDate) {
                lastDate = day.date
                item(key = "h:${day.date}") {
                    Text(
                        prettyDate(day.date).uppercase(),
                        modifier = Modifier.padding(start = 3.dp, top = 5.dp),
                        style = TinyLabelStyle,
                        color = Slate,
                    )
                }
            }
            for (item in day.items) {
                item(key = item.key) {
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
    }
}

@Composable
fun EntryDetailsScreen(
    snapshot: Snapshot?,
    date: String,
    entryKey: String,
    onBack: () -> Unit,
    onStartLive: (date: String, typeKey: String) -> Unit,
) {
    val item = snapshot?.days
        ?.firstOrNull { it.date == date }
        ?.items
        ?.firstOrNull { it.key == entryKey }
    val planDto = item?.let { snapshot?.plans?.firstOrNull { p -> p.date == it.date && p.typeKey == it.typeKey } }
    val plan = planDto?.let { InteractivePlan.fromJson(it.planJson) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val pendingRun = item?.let { entry ->
        planDto?.let { dto -> WatchLiveSync.loadRun(context, entry.date, entry.typeKey, dto.updatedAt) }
    }
    val accent = when (item?.kind) {
        "sleep" -> Periwinkle
        "activity" -> Teal
        else -> workoutAccent(item?.typeKey.orEmpty())
    }
    val state = rememberScalingLazyListState()

    ScalingLazyColumn(
        state = state,
        modifier = Modifier.fillMaxWidth(),
        autoCentering = null,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { WatchClock(Modifier.fillMaxWidth()) }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackGlyph(onBack)
                Spacer(Modifier.width(5.dp))
                Text(
                    prettyDate(date).uppercase(),
                    style = TinyLabelStyle,
                    color = Slate,
                )
            }
        }

        if (item == null) {
            item {
                EmptyMessage(
                    "Entry not found",
                    "The phone's next snapshot will bring it back.",
                )
            }
            return@ScalingLazyColumn
        }

        item {
            Text(
                item.title,
                modifier = Modifier.padding(start = 3.dp),
                style = TitleStyle,
                color = Snow,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.hasPlan) TagPill("AI", Teal)
                if (item.scheduled) TagPill("Scheduled", Periwinkle)
                if (item.planned) TagPill("Planned", Amber)
                if (item.hasWatchData) TagPill("Watch", Teal)
            }
        }

        if (item.summary.isNotBlank()) {
            item {
                Text(
                    item.summary,
                    modifier = Modifier.padding(horizontal = 3.dp),
                    style = BodyStyle,
                    color = Mist,
                )
            }
        }

        val timeRange = if (item.startMs > 0 && item.endMs > item.startMs) {
            "${clockOf(item.startMs)} – ${clockOf(item.endMs)}"
        } else {
            ""
        }
        if (timeRange.isNotBlank() || item.stats.isNotEmpty()) {
            item {
                WatchCard(accent = accent) {
                    if (timeRange.isNotBlank()) {
                        Text(timeRange, style = CaptionStyle, color = Slate)
                        Spacer(Modifier.height(3.dp))
                    }
                    val stats = item.stats.take(4)
                    for (row in stats.chunked(2)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for (stat in row) {
                                StatTile(stat, accent, Modifier.weight(1f))
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }

        if (item.body.isNotBlank()) {
            item {
                Text(
                    item.body,
                    modifier = Modifier.padding(horizontal = 3.dp),
                    style = BodyStyle,
                    color = Mist,
                )
            }
        }

        if (item.kind == "workout" && plan != null && planDto != null) {
            item { PlanSummary(plan, planDto.resultsJson, accent) }
            item {
                StartButton(
                    label = if (pendingRun != null) "Resume workout" else "Start live workout",
                    accent = accent,
                ) {
                    onStartLive(item.date, item.typeKey)
                }
            }
        } else if (item.content.isNotBlank()) {
            item { SectionLabel("WORKOUT") }
            item {
                Text(
                    item.content,
                    modifier = Modifier.padding(horizontal = 3.dp),
                    style = BodyStyle,
                    color = Mist,
                )
            }
            item {
                Text(
                    "Build an AI plan on the phone to run this live on the watch.",
                    modifier = Modifier.padding(horizontal = 3.dp),
                    style = CaptionStyle,
                    color = Slate,
                )
            }
        }

        if (item.note.isNotBlank()) {
            item { SectionLabel("NOTE", Amber) }
            item {
                Row {
                    Box(
                        Modifier
                            .width(2.dp)
                            .height(28.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Amber.copy(alpha = 0.8f))
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(item.note, style = CaptionStyle, color = Mist)
                }
            }
        }
    }
}

@Composable
private fun PlanSummary(plan: InteractivePlan, resultsJson: String, accent: Color) {
    val results = InteractiveResults.fromJson(resultsJson)
    val live = LiveWorkout.build(plan)
    val logged = if (InteractiveResults.hasAnyValue(results)) {
        var count = 0
        plan.sections.forEachIndexed { index, section ->
            InteractiveRows.scopes(section, index).forEach { scope ->
                if (results[scope]?.values?.any { it.isNotBlank() } == true) count += 1
            }
        }
        count
    } else {
        0
    }
    WatchCard(accent = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AI PLAN", style = TinyLabelStyle, color = Teal, modifier = Modifier.weight(1f))
            if (live.sections.sumOf { it.items.size } > 0) {
                Text(
                    "${live.sections.sumOf { it.items.size }} ROWS",
                    style = TinyLabelStyle,
                    color = Slate,
                )
            }
        }
        if (plan.summary.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                plan.summary,
                style = CaptionStyle,
                color = Mist,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
        live.sections.forEachIndexed { index, section ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(sectionAccent(section.kind))
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    section.title,
                    modifier = Modifier.weight(1f),
                    style = CaptionStyle,
                    color = Snow,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        section.isAmrap -> "AMRAP"
                        section.roundCount > 0 -> "${section.roundCount} RDS"
                        section.items.isNotEmpty() -> "${section.items.size} SETS"
                        else -> ""
                    },
                    style = TinyLabelStyle,
                    color = Slate,
                )
            }
        }
        if (logged > 0) {
            Spacer(Modifier.height(3.dp))
            Text("$logged rows logged", style = TinyLabelStyle, color = Success)
        }
    }
}

@Composable
fun StartButton(label: String, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = TitleSmallStyle,
            color = Abyss,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun BackGlyph(onClick: () -> Unit) {
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(CardHigh)
            .border(1.dp, Hairline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("‹", style = TitleSmallStyle, color = Snow)
    }
}
