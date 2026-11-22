package com.hcexport.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Text
import com.hcexport.InteractivePlan
import com.hcexport.shared.JournalItemDto
import com.hcexport.shared.Snapshot
import com.hcexport.wear.live.WatchLiveSync

/**
 * The wrist's workout page: what it is and when, then two round actions.
 * Everything longer — stats, plan sections, the original text, the note —
 * lives one tap away in [EntryDetailsScreen], because a round 226 dp screen
 * cannot carry a phone layout without turning into a wall of small text.
 */
@Composable
fun EntryScreen(
    snapshot: Snapshot?,
    date: String,
    entryKey: String,
    onBack: () -> Unit,
    onDetails: (date: String, key: String) -> Unit,
    onStartLive: (date: String, typeKey: String) -> Unit,
) {
    val context = LocalContext.current
    val item = snapshot?.days
        ?.firstOrNull { it.date == date }
        ?.items
        ?.firstOrNull { it.key == entryKey }
    val planDto = item?.let { entry ->
        snapshot?.plans?.firstOrNull { it.date == entry.date && it.typeKey == entry.typeKey }
    }
    val plan = planDto?.let { InteractivePlan.fromJson(it.planJson) }
    val accent = when (item?.kind) {
        "sleep" -> Periwinkle
        "activity" -> Teal
        else -> workoutAccent(item?.typeKey.orEmpty())
    }
    val canStart = item?.kind == "workout" && plan != null && planDto != null
    val pendingRun = remember(item?.key, planDto?.updatedAt) {
        if (item == null || planDto == null) {
            null
        } else {
            WatchLiveSync.loadRun(context, date, item.typeKey, planDto.updatedAt)
        }
    }

    WatchBackground(accent = accent) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                BackGlyph(onBack)
            }
            Spacer(Modifier.height(4.dp))

            if (item == null) {
                EmptyMessage("Entry not found", "The phone's next snapshot will bring it back.")
                return@Column
            }

            Text(
                item.title,
                style = TitleStyle,
                color = Snow,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))

            val timeRange = if (item.startMs > 0 && item.endMs > item.startMs) {
                "${clockOf(item.startMs)} – ${clockOf(item.endMs)}"
            } else {
                ""
            }
            Text(
                listOf(prettyDate(item.date).uppercase(), timeRange)
                    .filter { it.isNotBlank() }
                    .joinToString("  ·  "),
                style = TinyLabelStyle,
                color = accent,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            val summary = item.summary.ifBlank {
                item.stats.take(3).joinToString(" · ") { "${it.value} ${it.label.lowercase()}" }
            }
            Text(
                summary,
                style = CaptionStyle,
                color = Mist,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))

            if (canStart) {
                CircleAction(
                    label = if (pendingRun != null) "RESUME" else "START",
                    accent = accent,
                    filled = true,
                    glyph = { PlayTriangle(Abyss) },
                ) {
                    onStartLive(item.date, item.typeKey)
                }
                Spacer(Modifier.height(7.dp))
            }
            CircleAction(
                label = "DETAILS",
                accent = accent,
                filled = false,
                glyph = { ListGlyph(accent) },
            ) {
                onDetails(item.date, item.key)
            }
            if (item.kind == "workout" && plan == null) {
                Spacer(Modifier.height(7.dp))
                Text(
                    "Build an AI plan on the phone to run this live.",
                    style = TinyLabelStyle,
                    color = Slate,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun CircleAction(
    label: String,
    accent: Color,
    filled: Boolean,
    glyph: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val shape = CircleShape
    Column(
        Modifier
            .size(56.dp)
            .clip(shape)
            .background(if (filled) accent else CardHigh.copy(alpha = 0.85f))
            .border(
                1.dp,
                if (filled) Color.Transparent else accent.copy(alpha = 0.45f),
                shape,
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        glyph()
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = TinyLabelStyle,
            color = if (filled) Abyss else accent,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlayTriangle(color: Color) {
    Canvas(Modifier.size(13.dp)) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(1f, 1f)
            lineTo(size.width - 1f, size.height / 2f)
            lineTo(1f, size.height - 1f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
private fun ListGlyph(color: Color) {
    Column(
        Modifier.size(13.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(3) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(color)
            )
        }
    }
}
