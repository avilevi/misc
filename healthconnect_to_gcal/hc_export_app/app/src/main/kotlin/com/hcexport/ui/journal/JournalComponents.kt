package com.hcexport.ui.journal

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hcexport.CalendarHelper
import com.hcexport.HrSample
import com.hcexport.JournalAiExercise
import com.hcexport.JournalAiMovement
import com.hcexport.JournalAiPlan
import com.hcexport.JournalAiRound
import com.hcexport.JournalAiSection
import com.hcexport.JournalAiSet
import com.hcexport.JournalAiValue
import com.hcexport.JournalBiometrics
import com.hcexport.JournalFeed
import com.hcexport.JournalItem
import com.hcexport.R
import com.hcexport.SleepStageTotal
import com.hcexport.SourceBrands
import com.hcexport.WorkoutBlock
import com.hcexport.WorkoutText
import com.hcexport.ui.CardGap
import com.hcexport.ui.HcCard
import com.hcexport.ui.NumberText
import com.hcexport.ui.PrimaryButton
import com.hcexport.ui.SectionLabel
import com.hcexport.ui.Tag
import com.hcexport.ui.theme.Amber
import com.hcexport.ui.theme.Card
import com.hcexport.ui.theme.CardHigh
import com.hcexport.ui.theme.CardLift
import com.hcexport.ui.theme.Coral
import com.hcexport.ui.theme.Hairline
import com.hcexport.ui.theme.HairlineSoft
import com.hcexport.ui.theme.Mist
import com.hcexport.ui.theme.Periwinkle
import com.hcexport.ui.theme.PeriwinkleDeep
import com.hcexport.ui.theme.Slate
import com.hcexport.ui.theme.Snow
import com.hcexport.ui.theme.Success
import com.hcexport.ui.theme.Teal
import com.hcexport.ui.theme.workoutAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())

internal fun clockTime(ms: Long): String = timeFmt.format(Date(ms))

internal fun itemAccent(item: JournalItem): Color = when (item) {
    is JournalItem.Workout -> workoutAccent(item.typeKey)
    is JournalItem.Activity -> Teal
    is JournalItem.Sleep -> Periwinkle
}

internal fun sleepStageColor(name: String): Color = when (name) {
    "Deep Sleep" -> PeriwinkleDeep
    "REM" -> Periwinkle
    "Light Sleep" -> Color(0xFF4EA5D9)
    "Sleeping" -> Slate
    "Awake" -> Amber
    "Out of Bed" -> Coral
    else -> Slate.copy(alpha = 0.65f)
}

internal fun sectionAccent(kind: String): Color = when (kind) {
    "strength" -> Amber
    "metcon" -> Teal
    "accessory" -> Periwinkle
    "rest" -> Slate
    else -> Mist
}

// ── Section heading ─────────────────────────────────────────────────────────

@Composable
internal fun JournalSectionHeader(
    title: String,
    accent: Color,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(19.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent)
            )
            Spacer(Modifier.width(9.dp))
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = Snow,
            )
            trailing()
        }
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(
                subtitle,
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Slate,
            )
        }
    }
}

// ── Hero ────────────────────────────────────────────────────────────────────

internal data class HeroStat(val value: String, val label: String)

@Composable
internal fun JournalHero(
    title: String,
    subtitle: String,
    accent: Color,
    iconRes: Int,
    tags: List<Pair<String, Color>>,
    stats: List<HeroStat>,
) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 196.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.26f), CardHigh, Card)))
            .border(1.dp, accent.copy(alpha = 0.30f), shape)
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = null,
            tint = accent.copy(alpha = 0.08f),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 18.dp)
                .size(168.dp),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            if (tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { (label, color) -> Tag(label, color) }
                }
                Spacer(Modifier.height(12.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.displaySmall,
                color = Snow,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Mist)
            if (stats.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                FlowRowAtLeastTwo(stats)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowAtLeastTwo(stats: List<HeroStat>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        stats.forEach { stat ->
            Column {
                NumberText(stat.value, color = Snow, size = 26, weight = FontWeight.Light)
                Text(stat.label, style = MaterialTheme.typography.labelSmall, color = Slate)
            }
        }
    }
}

// ── Day list cards ──────────────────────────────────────────────────────────

@Composable
internal fun JournalItemCard(item: JournalItem, planned: Boolean = false, onClick: () -> Unit) {
    when (item) {
        is JournalItem.Workout -> WorkoutJournalCard(item, planned, onClick)
        is JournalItem.Activity -> ActivityJournalCard(item, onClick)
        is JournalItem.Sleep -> SleepJournalCard(item, onClick)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutJournalCard(
    item: JournalItem.Workout,
    planned: Boolean,
    onClick: () -> Unit,
) {
    val accent = workoutAccent(item.typeKey)
    HcCard(accent = accent, contentPadding = PaddingValues(15.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent)
            )
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        WorkoutText.displayType(item.typeLabel),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        color = Snow,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.startMs?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            clockTime(it),
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    item.plan?.summary?.takeIf { it.isNotBlank() }
                        ?: WorkoutText.summary(item.content),
                    style = MaterialTheme.typography.bodySmall,
                    color = Mist,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (planned) Tag("PLANNED", Amber)
                    if (item.plan != null) Tag("AI", Periwinkle)
                    if (item.biometrics.isNotEmpty()) Tag("WATCH DATA", Teal)
                    if (item.plan != null && item.plan.loggedSets > 0) {
                        Tag(
                            if (item.plan.loggedSets == 1) "1 SET"
                            else "${item.plan.loggedSets} SETS",
                            Success,
                        )
                    }
                }
                val bio = item.biometrics.firstOrNull()
                if (bio != null) {
                    Spacer(Modifier.height(7.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(SourceBrands.iconResId(bio.sourcePkg)),
                            contentDescription = null,
                            tint = Snow,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            buildString {
                                append(SourceBrands.displayName(bio.sourcePkg))
                                append(" · ")
                                append(JournalFeed.formatDuration(bio.durationMin))
                                bio.avgHrBpm?.let { append(" · ${it.toInt()} bpm") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Teal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (item.note.isNotBlank()) {
                    Spacer(Modifier.height(7.dp))
                    NotePreview(item.note)
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = Slate,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ActivityJournalCard(item: JournalItem.Activity, onClick: () -> Unit) {
    val bio = item.biometrics
    val (emoji, typeLabel) = CalendarHelper.EXERCISE_TYPES[bio.typeCode] ?: ("🏃" to "Activity")
    HcCard(accent = Teal, contentPadding = PaddingValues(15.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Teal.copy(alpha = 0.12f))
                    .border(1.dp, Teal.copy(alpha = 0.22f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        bio.title.ifBlank { typeLabel },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        color = Snow,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        clockTime(bio.startMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    activitySummary(bio),
                    style = MaterialTheme.typography.bodySmall,
                    color = Mist,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(SourceBrands.iconResId(bio.sourcePkg)),
                        contentDescription = null,
                        tint = Snow,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        SourceBrands.displayName(bio.sourcePkg),
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate,
                    )
                }
                if (item.note.isNotBlank()) {
                    Spacer(Modifier.height(7.dp))
                    NotePreview(item.note)
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = Slate,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SleepJournalCard(item: JournalItem.Sleep, onClick: () -> Unit) {
    HcCard(accent = Periwinkle, contentPadding = PaddingValues(15.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Periwinkle.copy(alpha = 0.12f))
                    .border(1.dp, Periwinkle.copy(alpha = 0.22f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_moon),
                    contentDescription = null,
                    tint = Periwinkle,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Sleep",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        color = Snow,
                    )
                    Text(
                        "${clockTime(item.startMs)} – ${clockTime(item.endMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append(JournalFeed.formatDuration(item.durationMin))
                        item.stages.firstOrNull { it.name == "Deep Sleep" }?.let {
                            append(" · Deep ${JournalFeed.formatDuration(it.minutes)}")
                        }
                        item.stages.firstOrNull { it.name == "REM" }?.let {
                            append(" · REM ${JournalFeed.formatDuration(it.minutes)}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Mist,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.stages.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    SleepStageBar(item.stages, height = 5.dp)
                }
                if (item.note.isNotBlank()) {
                    Spacer(Modifier.height(7.dp))
                    NotePreview(item.note)
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = Slate,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun NotePreview(note: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(2.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(50))
                .background(Amber.copy(alpha = 0.75f))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            note,
            style = MaterialTheme.typography.bodySmall,
            color = Mist,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun activitySummary(bio: JournalBiometrics): String = buildString {
    val typeLabel = CalendarHelper.EXERCISE_TYPES[bio.typeCode]?.second ?: "Activity"
    append(JournalFeed.formatDuration(bio.durationMin))
    append(" of ")
    append(typeLabel.lowercase())
    bio.distanceM?.let { append(" · ${JournalFeed.formatDistance(it)} km") }
    bio.paceSecPerKm?.let { append(" · ${JournalFeed.formatPace(it)} /km") }
    bio.avgHrBpm?.let { append(" · ${it.toInt()} bpm avg") }
}

// ── AI plan ─────────────────────────────────────────────────────────────────

@Composable
internal fun AiPlanView(plan: JournalAiPlan, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        AiPlanHeader(plan, onOpen)
        Spacer(Modifier.height(18.dp))
        plan.sections.forEach { section ->
            AiSectionBlock(section)
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun AiPlanHeader(plan: JournalAiPlan, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Teal.copy(alpha = 0.20f), Periwinkle.copy(alpha = 0.17f), CardHigh)
                )
            )
            .border(1.dp, Teal.copy(alpha = 0.28f), shape)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Teal.copy(alpha = 0.45f), Periwinkle.copy(alpha = 0.45f))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_ai),
                    contentDescription = null,
                    tint = Snow,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Tag("AI PLAN", Teal)
            Spacer(Modifier.weight(1f))
            if (plan.model.isNotBlank()) {
                Text(
                    com.hcexport.AiDisplay.pretty(plan.model),
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(plan.title, style = MaterialTheme.typography.headlineMedium, color = Snow)
        if (plan.summary.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(plan.summary, style = MaterialTheme.typography.bodySmall, color = Mist)
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (plan.loggedSets == 0) {
                    "No values entered yet"
                } else {
                    "${plan.loggedSets} of ${plan.totalSets} sets logged"
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = if (plan.loggedSets == 0) Slate else Mist,
            )
            plan.totalVolumeKg?.let { Tag("${JournalFeed.formatVolume(it)} KG", Success) }
        }
        if (plan.totalSets > 0) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Snow.copy(alpha = 0.08f))
            ) {
                val fraction = (plan.loggedSets.toFloat() / plan.totalSets).coerceIn(0f, 1f)
                if (fraction > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(Teal, Periwinkle)))
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Open AI workout", onClick = onOpen, accent = Teal)
    }
}

@Composable
private fun AiSectionBlock(section: JournalAiSection) {
    val accent = sectionAccent(section.kind)
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent)
            )
            Spacer(Modifier.width(9.dp))
            Text(
                section.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = Snow,
            )
            if (section.kind != "other") Tag(section.kind.uppercase(), accent)
        }
        if (section.notes.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(
                section.notes,
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Slate,
            )
        }
        Spacer(Modifier.height(10.dp))

        if (section.values.isNotEmpty()) {
            HcCard(accent = accent, contentPadding = PaddingValues(16.dp)) {
                SectionLabel("Section result", color = accent)
                Spacer(Modifier.height(8.dp))
                ValueFlow(section.values, accent)
            }
            Spacer(Modifier.height(10.dp))
        }

        if (section.rounds.isNotEmpty()) {
            AiRoundsCard(section.rounds, accent)
            Spacer(Modifier.height(CardGap))
        }

        section.exercises.forEach { exercise ->
            AiExerciseCard(exercise, accent)
            Spacer(Modifier.height(CardGap))
        }
    }
}

@Composable
private fun AiRoundsCard(rounds: List<JournalAiRound>, accent: Color) {
    HcCard(contentPadding = PaddingValues(16.dp)) {
        rounds.forEachIndexed { roundIndex, round ->
            if (roundIndex > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .height(1.dp)
                        .background(HairlineSoft)
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.10f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(50))
                        .background(accent)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    round.label.uppercase(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (round.notes.isNotBlank()) {
                    Text(
                        round.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            round.movements.forEach { movement ->
                AiMovementRow(movement, accent)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiMovementRow(movement: JournalAiMovement, accent: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            movement.name,
            modifier = Modifier.weight(1f).padding(end = 6.dp),
            style = MaterialTheme.typography.titleSmall,
            color = Snow,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (movement.values.isNotEmpty()) {
            ValueFlow(movement.values, accent, Modifier.weight(1.6f))
        } else if (movement.target.isNotBlank()) {
            Text(
                movement.target,
                modifier = Modifier.weight(1.6f),
                style = MaterialTheme.typography.bodySmall,
                color = Slate,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiExerciseCard(exercise: JournalAiExercise, accent: Color) {
    HcCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                exercise.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = Snow,
            )
            if (exercise.sets.size > 1) Tag("${exercise.sets.size} SETS", accent)
            else if (exercise.sets.size == 1) Tag("1 SET", accent)
        }
        if (exercise.notes.isNotBlank()) {
            Text(
                exercise.notes,
                style = MaterialTheme.typography.bodySmall,
                color = Slate,
            )
        }
        Spacer(Modifier.height(6.dp))

        exercise.sets.forEachIndexed { index, set ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .height(1.dp)
                        .background(HairlineSoft)
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    set.label,
                    modifier = Modifier.width(54.dp).padding(end = 6.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = Snow,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (set.values.isNotEmpty()) {
                    ValueFlow(set.values, accent, Modifier.weight(1f))
                } else if (set.target.isNotBlank()) {
                    Text(
                        set.target,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ValueFlow(
    values: List<JournalAiValue>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        values.forEach { JournalValueChip(it, accent) }
    }
}

@Composable
private fun JournalValueChip(value: JournalAiValue, accent: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.24f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (value.checked) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                value.label,
                style = MaterialTheme.typography.labelSmall,
                color = Slate,
            )
        } else {
            Text(
                value.label,
                style = MaterialTheme.typography.labelSmall,
                color = Slate,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                value.value,
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                color = Snow,
            )
            if (value.unit.isNotBlank()) {
                Spacer(Modifier.width(2.dp))
                Text(
                    value.unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate,
                )
            }
        }
    }
}

// ── Watch data ──────────────────────────────────────────────────────────────

@Composable
internal fun WatchDataCard(bio: JournalBiometrics, accent: Color = Teal) {
    val brand = SourceBrands.forPackage(bio.sourcePkg)
    HcCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.10f))
                    .border(1.dp, accent.copy(alpha = 0.20f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(brand.iconResId),
                    contentDescription = null,
                    tint = Snow,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    brand.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = Snow,
                )
                Text(
                    "${clockTime(bio.startMs)} – ${clockTime(bio.endMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                )
            }
            Tag(JournalFeed.formatDuration(bio.durationMin), accent)
        }
        if (bio.title.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(bio.title, style = MaterialTheme.typography.bodyMedium, color = Mist)
        }

        val tiles = bioTiles(bio)
        if (tiles.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            MetricTileFlow(tiles, accent)
        }

        if (bio.hrSamples.size > 1) {
            Spacer(Modifier.height(16.dp))
            SectionLabel("Heart rate", color = Slate)
            Spacer(Modifier.height(6.dp))
            HrChart(bio.hrSamples, accent)
        }
    }
}

internal data class BioTile(val label: String, val value: String, val unit: String)

internal fun bioTiles(bio: JournalBiometrics): List<BioTile> = buildList {
    bio.distanceM?.let { add(BioTile("Distance", JournalFeed.formatDistance(it), "km")) }
    bio.paceSecPerKm?.let { add(BioTile("Pace", JournalFeed.formatPace(it), "/km")) }
    bio.caloriesKcal?.let { add(BioTile("Calories", it.toInt().toString(), "kcal")) }
    bio.avgHrBpm?.let { add(BioTile("Avg HR", it.toInt().toString(), "bpm")) }
    bio.maxHrBpm?.let { add(BioTile("Max HR", it.toInt().toString(), "bpm")) }
    bio.steps?.let { add(BioTile("Steps", "%,d".format(it), "")) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricTileFlow(tiles: List<BioTile>, accent: Color) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tiles.forEach { tile ->
            Column(
                Modifier
                    .width(100.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardHigh.copy(alpha = 0.45f))
                    .border(1.dp, Hairline, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Text(
                    tile.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        tile.value,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFeatureSettings = "tnum",
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Snow,
                        maxLines = 1,
                    )
                    if (tile.unit.isNotBlank()) {
                        Spacer(Modifier.width(3.dp))
                        Text(
                            tile.unit,
                            modifier = Modifier.padding(bottom = 2.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = accent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun EmptyWatchDataCard(planned: Boolean = false) {
    HcCard(contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Teal.copy(alpha = 0.08f))
                    .border(1.dp, Teal.copy(alpha = 0.18f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_watch),
                    contentDescription = null,
                    tint = Teal,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    if (planned) "Scheduled session" else "No watch data",
                    style = MaterialTheme.typography.titleSmall,
                    color = Snow,
                )
                Text(
                    if (planned) "This workout hasn't happened yet."
                    else "Nothing was recorded for this session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (planned) {
                "Its watch data merges here once the session is recorded."
            } else {
                "Record the session on your watch or strap. It merges into this entry on the next sync."
            },
            style = MaterialTheme.typography.bodySmall,
            color = Mist,
        )
    }
}

// ── Heart-rate chart ────────────────────────────────────────────────────────

@Composable
internal fun HrChart(samples: List<HrSample>, accent: Color) {
    if (samples.size < 2) return
    val minBpm = (samples.minOf { it.bpm } / 10 * 10).coerceAtLeast(0)
    val maxBpm = ((samples.maxOf { it.bpm } + 9) / 10 * 10).coerceAtLeast(minBpm + 10)
    val midBpm = (minBpm + maxBpm) / 2
    val maxMin = samples.maxOf { it.offsetMin }.coerceAtLeast(1)

    Box(
        Modifier
            .fillMaxWidth()
            .height(152.dp)
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(start = 38.dp, end = 6.dp, top = 8.dp, bottom = 22.dp)
        ) {
            if (size.width <= 0f || size.height <= 0f) return@Canvas
            val w = size.width
            val h = size.height

            for (i in 0..3) {
                val y = h * i / 3f
                drawLine(
                    HairlineSoft,
                    Offset(0f, y),
                    Offset(w, y),
                    1.dp.toPx(),
                )
            }

            val inset = 4.dp.toPx()
            val plotWidth = (w - inset * 2).coerceAtLeast(1f)
            val points = samples.map { sample ->
                val x = inset + plotWidth * (sample.offsetMin.toFloat() / maxMin.toFloat())
                val y = h - h * ((sample.bpm - minBpm).toFloat() / (maxBpm - minBpm).toFloat())
                Offset(x, y.coerceIn(0f, h))
            }
            val line = Path()
            val fill = Path()
            line.moveTo(points.first().x, points.first().y)
            fill.moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val previous = points[i - 1]
                val current = points[i]
                val midX = (previous.x + current.x) / 2f
                line.cubicTo(midX, previous.y, midX, current.y, current.x, current.y)
                fill.cubicTo(midX, previous.y, midX, current.y, current.x, current.y)
            }
            fill.lineTo(points.last().x, h)
            fill.lineTo(points.first().x, h)
            fill.close()
            drawPath(
                fill,
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.26f), Color.Transparent),
                    startY = 0f,
                    endY = h,
                ),
            )
            drawPath(
                line,
                accent,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            drawCircle(accent, radius = 3.5.dp.toPx(), center = points.last())
            drawCircle(Snow, radius = 1.4.dp.toPx(), center = points.last())
        }

        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(top = 8.dp)
                .height(122.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                maxBpm.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = Slate,
            )
            Text(
                midBpm.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = Slate,
            )
            Text(
                minBpm.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = Slate,
            )
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 38.dp, end = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("0m", style = MaterialTheme.typography.labelSmall, color = Slate)
            Text("${maxMin / 2}m", style = MaterialTheme.typography.labelSmall, color = Slate)
            Text("${maxMin}m", style = MaterialTheme.typography.labelSmall, color = Slate)
        }
    }
}

// ── Sleep ───────────────────────────────────────────────────────────────────

@Composable
internal fun SleepStageBar(
    stages: List<SleepStageTotal>,
    height: androidx.compose.ui.unit.Dp,
) {
    val total = stages.sumOf { it.minutes }.coerceAtLeast(1)
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(CardLift)
    ) {
        stages.forEach { stage ->
            val weight = (stage.minutes.toFloat() / total.toFloat()).coerceAtLeast(0.01f)
            Box(
                Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .background(sleepStageColor(stage.name))
            )
        }
    }
}

@Composable
internal fun SleepDetailCard(item: JournalItem.Sleep, accent: Color) {
    HcCard(contentPadding = PaddingValues(18.dp)) {
        SectionLabel("Sleep stages", color = accent)
        Spacer(Modifier.height(10.dp))
        if (item.stages.isEmpty()) {
            Text(
                "This session has no stage breakdown.",
                style = MaterialTheme.typography.bodySmall,
                color = Slate,
            )
        } else {
            SleepStageBar(item.stages, height = 10.dp)
            Spacer(Modifier.height(16.dp))
            val total = item.stages.sumOf { it.minutes }.coerceAtLeast(1)
            item.stages.forEachIndexed { index, stage ->
                if (index > 0) Spacer(Modifier.height(12.dp))
                val color = sleepStageColor(stage.name)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        stage.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mist,
                    )
                    Text(
                        JournalFeed.formatDuration(stage.minutes),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFeatureSettings = "tnum",
                        ),
                        color = Snow,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(HairlineSoft)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(stage.minutes.toFloat() / total.toFloat())
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(color)
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        HcDividerLine()
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(SourceBrands.iconResId(item.sourcePkg)),
                contentDescription = null,
                tint = Snow,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                SourceBrands.displayName(item.sourcePkg),
                style = MaterialTheme.typography.bodySmall,
                color = Slate,
            )
            Spacer(Modifier.weight(1f))
            Text(
                JournalFeed.formatDuration(item.durationMin),
                style = MaterialTheme.typography.titleSmall,
                color = Snow,
            )
        }
    }
}

@Composable
private fun HcDividerLine() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(HairlineSoft)
    )
}

// ── Workout text ────────────────────────────────────────────────────────────

@Composable
internal fun JournalWorkoutText(content: String, accent: Color) {
    HcCard(contentPadding = PaddingValues(20.dp)) {
        SectionLabel("The session", color = accent)
        Spacer(Modifier.height(14.dp))
        val blocks = WorkoutText.parse(content)
        if (blocks.isEmpty()) {
            Text(
                "No details for this workout.",
                style = MaterialTheme.typography.bodyMedium,
                color = Slate,
            )
        } else {
            blocks.forEachIndexed { index, block ->
                if (index > 0) Spacer(Modifier.height(14.dp))
                when (block) {
                    is WorkoutBlock.Header -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(50))
                                .background(accent)
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            block.text,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = Snow,
                        )
                    }
                    is WorkoutBlock.Bullet -> Row(verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier
                                .padding(top = 9.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(block.text, style = MaterialTheme.typography.bodyLarge, color = Mist)
                    }
                    is WorkoutBlock.Body -> Text(
                        block.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Snow,
                    )
                }
            }
        }
    }
}

// ── Comments ────────────────────────────────────────────────────────────────

@Composable
internal fun JournalCommentCard(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    accent: Color = Amber,
) {
    HcCard(accent = accent, contentPadding = PaddingValues(18.dp)) {
        SectionLabel("Free comments", color = accent)
        Spacer(Modifier.height(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Snow),
            cursorBrush = SolidColor(accent),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
            ),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 104.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardHigh.copy(alpha = 0.50f))
                        .border(1.dp, Hairline, RoundedCornerShape(14.dp))
                        .padding(13.dp)
                ) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate,
                        )
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.height(9.dp))
        Text(
            "Saved automatically on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = Slate,
        )
    }
}

// ── Source line (used by the entry screen) ─────────────────────────────────

@Composable
internal fun JournalTagRow(vararg tags: Pair<String, Color>) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tags.forEach { (text, color) -> Tag(text, color) }
    }
}
