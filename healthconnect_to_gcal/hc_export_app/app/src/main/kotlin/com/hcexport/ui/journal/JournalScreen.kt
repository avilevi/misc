package com.hcexport.ui.journal

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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hcexport.CalendarHelper
import com.hcexport.CalendarWorkoutEvent
import com.hcexport.InteractiveSessionRecord
import com.hcexport.InteractiveStore
import com.hcexport.JournalDayMark
import com.hcexport.JournalEntry
import com.hcexport.JournalFeed
import com.hcexport.JournalItem
import com.hcexport.JournalNotesStore
import com.hcexport.JournalStorage
import com.hcexport.R
import com.hcexport.Workout
import com.hcexport.WorkoutStore
import com.hcexport.ui.CardGap
import com.hcexport.ui.DataRefresh
import com.hcexport.ui.EmptyState
import com.hcexport.ui.HcBackground
import com.hcexport.ui.HcCard
import com.hcexport.ui.IconAction
import com.hcexport.ui.LoadingLine
import com.hcexport.ui.NumberText
import com.hcexport.ui.PageHeader
import com.hcexport.ui.ScreenPadding
import com.hcexport.ui.Tag
import com.hcexport.ui.theme.Amber
import com.hcexport.ui.theme.Mist
import com.hcexport.ui.theme.Periwinkle
import com.hcexport.ui.theme.Slate
import com.hcexport.ui.theme.Snow
import com.hcexport.ui.theme.Teal
import com.hcexport.ui.theme.workoutAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private data class JournalMonthLoad(
    val loaded: Boolean = false,
    val entries: Map<String, List<JournalEntry>> = emptyMap(),
    val workouts: Map<String, List<Workout>> = emptyMap(),
    val events: Map<String, List<CalendarWorkoutEvent>> = emptyMap(),
    val sessions: Map<String, List<InteractiveSessionRecord>> = emptyMap(),
    val notes: Map<String, String> = emptyMap(),
    val marks: Map<String, JournalDayMark> = emptyMap(),
    val hasAnything: Boolean = false,
)

private data class JournalDayLoad(
    val loaded: Boolean = false,
    val items: List<JournalItem> = emptyList(),
)

@Composable
fun JournalScreen(
    onBack: () -> Unit,
    onOpenItem: (date: String, key: String) -> Unit,
    onSync: () -> Unit,
) {
    val context = LocalContext.current
    val refresh by DataRefresh.tick.collectAsState()
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick += 1
        onPauseOrDispose { }
    }

    val today = LocalDate.now()
    val todayIso = today.toString()
    var selectedIso by rememberSaveable { mutableStateOf(today.toString()) }
    var monthIso by rememberSaveable { mutableStateOf(today.withDayOfMonth(1).toString()) }
    val selected = LocalDate.parse(selectedIso)
    val month = YearMonth.from(LocalDate.parse(monthIso))
    val accent = Teal

    val monthLoad by produceState(JournalMonthLoad(), month.toString(), refresh, resumeTick) {
        value = JournalMonthLoad()
        value = withContext(Dispatchers.IO) {
            val zone = java.time.ZoneId.systemDefault()
            val monthStartMs = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val monthEndMs = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val workouts = WorkoutStore.getBetween(
                context,
                month.atDay(1).toString(),
                month.atEndOfMonth().toString(),
            )
            val events = runCatching {
                CalendarHelper.findWorkoutEvents(context, monthStartMs, monthEndMs)
            }.getOrDefault(emptyList())
            val sessions = runCatching {
                InteractiveStore.getBetween(
                    context,
                    month.atDay(1).toString(),
                    month.atEndOfMonth().toString(),
                )
            }.getOrDefault(emptyList())
            val entries = runCatching {
                JournalStorage.getEntriesForMonth(context, month.year, month.monthValue - 1)
            }.getOrDefault(emptyList())
            val notes = JournalNotesStore.getAll(context)
            JournalMonthLoad(
                loaded = true,
                entries = entries.groupBy { it.date },
                workouts = workouts.groupBy { it.date },
                events = events.groupBy { it.date },
                sessions = sessions.groupBy { it.date },
                notes = notes,
                marks = JournalFeed.marks(workouts, events, entries, sessions, notes),
                hasAnything = entries.isNotEmpty() || workouts.isNotEmpty() ||
                    JournalStorage.getEntries(context).isNotEmpty(),
            )
        }
    }

    val dayLoad by produceState(JournalDayLoad(), monthLoad, selectedIso, refresh, resumeTick) {
        val monthData = monthLoad
        if (!monthData.loaded) return@produceState
        value = withContext(Dispatchers.IO) {
            JournalDayLoad(
                loaded = true,
                items = JournalFeed.buildDay(
                    date = selectedIso,
                    workouts = monthData.workouts[selectedIso].orEmpty(),
                    calendarEvents = monthData.events[selectedIso].orEmpty(),
                    entries = monthData.entries[selectedIso].orEmpty(),
                    sessions = monthData.sessions[selectedIso].orEmpty()
                        .associateBy { it.typeKey },
                    notes = monthData.notes,
                ).items,
            )
        }
    }

    fun shiftMonth(delta: Long) {
        val next = month.plusMonths(delta)
        monthIso = next.atDay(1).toString()
        val day = selected.dayOfMonth.coerceAtMost(next.lengthOfMonth())
        selectedIso = next.atDay(day).toString()
    }

    HcBackground(accent = accent) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            PageHeader(
                title = "Journal",
                eyebrow = "Training diary",
                onBack = onBack,
                trailing = {
                    if (selected != today) {
                        Text(
                            "TODAY",
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(accent.copy(alpha = 0.13f))
                                .border(1.dp, accent.copy(alpha = 0.26f), RoundedCornerShape(50))
                                .clickable {
                                    selectedIso = today.toString()
                                    monthIso = today.withDayOfMonth(1).toString()
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                        )
                    }
                },
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenPadding)
            ) {
                MonthNavigator(
                    label = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                    onPrevious = { shiftMonth(-1) },
                    onNext = { shiftMonth(1) },
                )
                Spacer(Modifier.height(12.dp))

                HcCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
                    WeekdayHeader()
                    Spacer(Modifier.height(6.dp))
                    MonthGrid(
                        month = month,
                        selected = selected,
                        today = today,
                        marks = monthLoad.marks,
                        accent = accent,
                        onSelect = { selectedIso = it.toString() },
                    )
                }

                Spacer(Modifier.height(26.dp))
                DayHeading(date = selected, count = dayLoad.items.size, accent = accent)
                Spacer(Modifier.height(14.dp))

                when {
                    !monthLoad.loaded || !dayLoad.loaded -> {
                        LoadingLine("Reading your journal…", accent)
                    }
                    dayLoad.items.isEmpty() -> {
                        val emptyJournal = !monthLoad.hasAnything
                        EmptyState(
                            iconRes = R.drawable.ic_journal,
                            title = if (emptyJournal) "The journal is empty" else "Nothing on this day",
                            message = if (emptyJournal)
                                "Sync to pull your workouts, watch sessions and sleep into the journal."
                            else
                                "No calendar workout, watch session or logged AI workout for this day. Sync for the latest.",
                            accent = accent,
                            actionText = "Sync now",
                            onAction = onSync,
                        )
                    }
                    else -> {
                        dayLoad.items.forEach { item ->
                            JournalItemCard(
                                item = item,
                                planned = item.date > todayIso,
                            ) { onOpenItem(item.date, item.key) }
                            Spacer(Modifier.height(CardGap))
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    "Workouts, watch data and notes merge into one entry on every sync.",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun MonthNavigator(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(
            icon = {
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = "Previous month",
                    tint = Mist,
                )
            },
            onClick = onPrevious,
            border = false,
            background = Color.Transparent,
        )
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = Snow,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconAction(
            icon = {
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "Next month",
                    tint = Mist,
                )
            },
            onClick = onNext,
            border = false,
            background = Color.Transparent,
        )
    }
}

@Composable
private fun WeekdayHeader() {
    Row(Modifier.fillMaxWidth()) {
        DayOfWeek.values().forEach { day ->
            Text(
                day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = Slate,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    marks: Map<String, JournalDayMark>,
    accent: Color,
    onSelect: (LocalDate) -> Unit,
) {
    val leading = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
    val days = month.lengthOfMonth()
    val rows = (leading + days + 6) / 7

    Column {
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val index = row * 7 + col - leading
                    if (index !in 0 until days) {
                        Spacer(
                            Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        )
                    } else {
                        val date = month.atDay(index + 1)
                        DayCell(
                            date = date,
                            selected = date == selected,
                            isToday = date == today,
                            mark = marks[date.toString()],
                            accent = accent,
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    mark: JournalDayMark?,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .heightIn(min = 48.dp)
            .padding(2.dp)
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.15f) else Color.Transparent)
            .then(
                when {
                    selected -> Modifier.border(1.dp, accent.copy(alpha = 0.45f), shape)
                    isToday -> Modifier.border(1.dp, accent.copy(alpha = 0.20f), shape)
                    else -> Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(top = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
            color = when {
                selected -> accent
                isToday -> accent
                else -> Mist
            },
        )
        Spacer(Modifier.height(4.dp))
        val dots = dayDots(mark)
        Row(horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
            if (dots.isEmpty()) {
                Spacer(Modifier.size(3.5.dp))
            } else {
                dots.take(3).forEach { color ->
                    Box(
                        Modifier
                            .size(3.5.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
        }
    }
}

/** Calendar dots: the day's workouts, then watch data, sleep and a note. */
private fun dayDots(mark: JournalDayMark?): List<Color> {
    if (mark == null) return emptyList()
    val dots = mark.workoutTypes.take(2).map { workoutAccent(it) }.toMutableList()
    if (mark.hasWatchData && dots.size < 3) dots += Teal
    if (mark.hasSleep && dots.size < 3) dots += Periwinkle
    if (mark.hasNote && dots.size < 3) dots += Amber
    return dots
}

@Composable
private fun DayHeading(date: LocalDate, count: Int, accent: Color) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        NumberText(
            date.dayOfMonth.toString(),
            color = Snow,
            size = 44,
            weight = FontWeight.Light,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(bottom = 5.dp)) {
            Text(
                date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = accent,
            )
            Text(
                date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                style = MaterialTheme.typography.titleSmall,
                color = Mist,
            )
        }
        Tag(
            when (count) {
                0 -> "NO ENTRIES"
                1 -> "1 ENTRY"
                else -> "$count ENTRIES"
            },
            accent,
        )
    }
}
