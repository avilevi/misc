package com.hcexport.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.hcexport.shared.JournalItemDto
import com.hcexport.shared.StatDto
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The system clock, kept live for the length of a screen. */
@Composable
fun WatchClock(modifier: Modifier = Modifier, color: Color = Slate) {
    val time by produceState(initialValue = clockText()) {
        while (true) {
            delay(15_000)
            value = clockText()
        }
    }
    Text(
        time,
        modifier = modifier,
        style = LabelStyle,
        color = color,
        textAlign = TextAlign.Center,
    )
}

private fun clockText(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

/** A layered card with an optional type accent. */
@Composable
fun WatchCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 11.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val background = if (accent != null) {
        Brush.linearGradient(listOf(accent.copy(alpha = 0.16f), CardHigh, Card))
    } else {
        Brush.linearGradient(listOf(CardHigh, Card))
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(1.dp, accent?.copy(alpha = 0.30f) ?: Hairline, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = contentPadding, vertical = 9.dp),
        content = content,
    )
}

@Composable
fun TagPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.13f))
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text.uppercase(),
            style = TinyLabelStyle,
            color = color,
            maxLines = 1,
        )
    }
}

/** One journal entry as the list shows it: a hint of the type, the name and
 *  the one number that matters. */
@Composable
fun JournalRow(
    item: JournalItemDto,
    onClick: () -> Unit,
    onPlay: (() -> Unit)? = null,
) {
    val accent = when (item.kind) {
        "sleep" -> Periwinkle
        "activity" -> Teal
        else -> workoutAccent(item.typeKey)
    }
    WatchCard(accent = accent, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent)
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.title,
                        modifier = Modifier.weight(1f),
                        style = TitleSmallStyle,
                        color = Snow,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.startMs > 0) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            clockOf(item.startMs),
                            style = TinyLabelStyle,
                            color = Slate,
                        )
                    }
                }
                val line = item.summary.ifBlank {
                    item.stats.joinToString(" · ") { it.value }
                }
                if (line.isNotBlank()) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        line,
                        style = CaptionStyle,
                        color = Mist,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val tags = buildList {
                    if (item.hasPlan) add("AI" to Teal)
                    if (item.scheduled) add("SCHEDULED" to Periwinkle)
                    if (item.planned) add("SOON" to Amber)
                    if (item.hasWatchData) add("WATCH" to Teal)
                    if (item.planLoggedSets > 0) {
                        val label = if (item.planLoggedSets == 1) {
                            "1 SET"
                        } else {
                            "${item.planLoggedSets} SETS"
                        }
                        add(label to Success)
                    }
                }
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        tags.take(2).forEach { (text, color) ->
                            TagPill(text, color)
                        }
                    }
                }
            }
            if (onPlay != null) {
                Spacer(Modifier.width(6.dp))
                PlayGlyph(accent, onPlay)
            }
        }
    }
}

@Composable
fun PlayGlyph(accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.16f))
            .border(1.dp, accent.copy(alpha = 0.42f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TriangleGlyph(accent)
    }
}

@Composable
private fun TriangleGlyph(color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(11.dp)) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(0f, size.height)
            close()
        }
        drawPath(path, color)
    }
}

/** A compact stat: the number big, the label tiny. */
@Composable
fun StatTile(stat: StatDto, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            stat.value,
            style = TitleSmallStyle.copy(fontWeight = FontWeight.SemiBold),
            color = Snow,
            maxLines = 1,
        )
        Text(stat.label, style = TinyLabelStyle, color = accent, maxLines = 1)
    }
}

fun clockOf(ms: Long): String =
    if (ms <= 0) "" else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

fun prettyDate(iso: String): String = runCatching {
    val date = java.time.LocalDate.parse(iso)
    val today = java.time.LocalDate.now()
    when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(
            java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
        )
    }
}.getOrDefault(iso)

@Composable
fun EmptyMessage(title: String, message: String) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = TitleStyle, color = Snow, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(message, style = CaptionStyle, color = Slate, textAlign = TextAlign.Center)
    }
}
