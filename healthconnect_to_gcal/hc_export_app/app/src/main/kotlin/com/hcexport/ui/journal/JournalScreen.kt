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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.hcexport.JournalDeleter
import com.hcexport.JournalFeed
import com.hcexport.JournalHiddenStore
import com.hcexport.JournalItem
import com.hcexport.JournalNotesStore
import com.hcexport.JournalStorage
import com.hcexport.Prefs
import com.hcexport.R
import com.hcexport.ScheduledSession
import com.hcexport.ScheduledSessionStore
import com.hcexport.WeekStart
import com.hcexport.Workout
import com.hcexport.WorkoutStore
import com.hcexport.ui.ConfirmDialog
import com.hcexport.ui.workouts.CalendarView
import com.hcexport.ui.CardGap
import com.hcexport.ui.DataRefresh
import com.hcexport.ui.EmptyState
import com.hcexport.ui.HcBackground
import com.hcexport.ui.HcCard
import com.hcexport.ui.IconAction
import com.hcexport.ui.LoadingLine
import com.hcexport.ui.LocalMinimalDesign
import com.hcexport.ui.SegmentedControl
import com.hcexport.ui.SwipeCarousel
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
import kotlinx.coroutines.launch
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
    val scheduled: List<ScheduledSession> = emptyList(),
    val notes: Map<String, String> = emptyMap(),
    val hiddenKeys: Set<String> = emptySet(),
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
    onCreateAi: (date: String, typeKey: String) -> Unit,
    onStartLive: (date: String, typeKey: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
    var showSchedule by rememberSaveable { mutableStateOf(false) }
    var menuKey by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<JournalItem?>(null) }
    val accent = Teal

    var viewKey by rememberSaveable { mutableStateOf(Prefs.getJournalView(context)) }
    val journalView = CalendarView.fromKey(viewKey)
    val selected = LocalDate.parse(selectedIso)
    val month = YearMonth.from(LocalDate.parse(monthIso))
    // Re-read on resume so a change in Settings lands without a restart.
    val firstDay = remember(resumeTick) { Prefs.getFirstDayOfWeek(context) }
    val weekStart = WeekStart.startOfWeek(selected, firstDay)

    // The window covers the displayed period plus one on each side, so both
    // neighbours are loaded before a swipe can reveal them.
    val rangeStartIso: String
    val rangeEndIso: String
    when (journalView) {
        CalendarView.MONTH -> {
            rangeStartIso = month.minusMonths(1).atDay(1).toString()
            rangeEndIso = month.plusMonths(1).atEndOfMonth().toString()
        }
        CalendarView.WEEK -> {
            rangeStartIso = weekStart.minusWeeks(1).toString()
            rangeEndIso = weekStart.plusWeeks(1).plusDays(6).toString()
        }
        CalendarView.DAY -> {
            rangeStartIso = selected.minusDays(1).toString()
            rangeEndIso = selected.plusDays(1).toString()
        }
    }
    val rangeKey = "$rangeStartIso:$rangeEndIso"

    // The previous window's data stays on screen while the new window loads;
    // a committed swipe never lands on a blank page.
    var monthLoad by remember { mutableStateOf(JournalMonthLoad()) }
    LaunchedEffect(rangeKey, refresh, resumeTick) {
        monthLoad = withContext(Dispatchers.IO) {
            val startDate = LocalDate.parse(rangeStartIso)
            val endDate = LocalDate.parse(rangeEndIso)
            val zone = java.time.ZoneId.systemDefault()
            val rangeStartMs = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val rangeEndMs = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val workouts = WorkoutStore.getBetween(context, rangeStartIso, rangeEndIso)
            val events = runCatching {
                CalendarHelper.findWorkoutEvents(context, rangeStartMs, rangeEndMs)
            }.getOrDefault(emptyList())
            val sessions = runCatching {
                InteractiveStore.getBetween(context, rangeStartIso, rangeEndIso)
            }.getOrDefault(emptyList())
            val entries = runCatching {
                JournalStorage.getEntries(context)
                    .filter { it.date >= rangeStartIso && it.date <= rangeEndIso }
            }.getOrDefault(emptyList())
            val scheduled = runCatching {
                ScheduledSessionStore.getAll(context)
                    .filter { it.date >= rangeStartIso && it.date <= rangeEndIso }
            }.getOrDefault(emptyList())
            val notes = JournalNotesStore.getAll(context)
            val hidden = runCatching { JournalHiddenStore.getAll(context) }
                .getOrDefault(emptySet())
            val allEntries = runCatching { JournalStorage.getEntries(context) }
                .getOrDefault(emptyList())
            JournalMonthLoad(
                loaded = true,
                entries = entries.groupBy { it.date },
                workouts = workouts.groupBy { it.date },
                events = events.groupBy { it.date },
                sessions = sessions.groupBy { it.date },
                scheduled = scheduled,
                notes = notes,
                hiddenKeys = hidden,
                marks = JournalFeed.marks(
                    workouts = workouts,
                    calendarEvents = events,
                    entries = entries,
                    sessions = sessions,
                    notes = notes,
                    scheduledTypeKeysByDate = scheduled.groupBy(
                        { it.date },
                        { it.typeKey },
                    ).mapValues { (_, types) -> types.toSet() },
                    hiddenKeys = hidden,
                ),
                hasAnything = entries.isNotEmpty() || workouts.isNotEmpty() ||
                    allEntries.isNotEmpty(),
            )
        }
    }

    // The day at -1, 0 and +1, built from the range already in memory so each
    // page — including the one sliding in — is real content.
    val dayLoads = remember(monthLoad, selectedIso, refresh, resumeTick) {
        val data = monthLoad
        (-1..1).associateWith { step ->
            val iso = selected.plusDays(step.toLong()).toString()
            if (!data.loaded) {
                JournalDayLoad()
            } else {
                JournalDayLoad(
                    loaded = true,
                    items = JournalFeed.buildDay(
                        date = iso,
                        workouts = data.workouts[iso].orEmpty(),
                        calendarEvents = data.events[iso].orEmpty(),
                        entries = data.entries[iso].orEmpty(),
                        sessions = data.sessions[iso].orEmpty()
                            .associateBy { it.typeKey },
                        notes = data.notes,
                        sleepSourcePriority = Prefs.getSleepSourcePriority(context),
                        scheduledSessions = data.scheduled.filter { it.date == iso },
                        hiddenKeys = data.hiddenKeys,
                    ).items,
                )
            }
        }
    }

    fun setSelected(date: LocalDate) {
        selectedIso = date.toString()
        monthIso = date.withDayOfMonth(1).toString()
    }

    fun shiftDay(delta: Long) = setSelected(selected.plusDays(delta))

    fun shiftPeriod(delta: Long) {
        when (journalView) {
            CalendarView.MONTH -> {
                val next = month.plusMonths(delta)
                val day = selected.dayOfMonth.coerceAtMost(next.lengthOfMonth())
                monthIso = next.atDay(1).toString()
                selectedIso = next.atDay(day).toString()
            }
            CalendarView.WEEK -> shiftDay(7 * delta)
            CalendarView.DAY -> shiftDay(delta)
        }
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
                SegmentedControl(
                    options = CalendarView.entries,
                    selected = journalView,
                    label = { it.label },
                    onSelect = { viewKey = it.key },
                    accent = accent,
                )
                Spacer(Modifier.height(18.dp))

                SwipeCarousel(
                    modifier = Modifier.fillMaxWidth(),
                    onSwitch = { direction ->
                        menuKey = null
                        shiftPeriod(direction.toLong())
                    },
                ) { step ->
                    when (journalView) {
                        CalendarView.MONTH -> {
                            val pageMonth = month.plusMonths(step.toLong())
                            val pageSelected = pageMonth.atDay(
                                selected.dayOfMonth.coerceAtMost(pageMonth.lengthOfMonth())
                            )
                            Column {
                                MonthNavigator(
                                    label = pageMonth.format(
                                        DateTimeFormatter.ofPattern(
                                            "MMMM yyyy",
                                            Locale.getDefault(),
                                        )
                                    ),
                                    onPrevious = { shiftPeriod(-1) },
                                    onNext = { shiftPeriod(1) },
                                )
                                Spacer(Modifier.height(12.dp))
                                HcCard(
                                    contentPadding = PaddingValues(
                                        horizontal = 12.dp,
                                        vertical = 12.dp,
                                    )
                                ) {
                                    WeekdayHeader(firstDay)
                                    Spacer(Modifier.height(6.dp))
                                    MonthGrid(
                                        month = pageMonth,
                                        selected = pageSelected,
                                        today = today,
                                        marks = monthLoad.marks,
                                        accent = accent,
                                        firstDay = firstDay,
                                        onSelect = ::setSelected,
                                    )
                                }
                            }
                        }

                        CalendarView.WEEK -> {
                            val pageWeekStart = weekStart.plusWeeks(step.toLong())
                            val pageSelected = selected.plusWeeks(step.toLong())
                            Column {
                                MonthNavigator(
                                    label = weekLabel(pageWeekStart),
                                    onPrevious = { shiftPeriod(-1) },
                                    onNext = { shiftPeriod(1) },
                                )
                                Spacer(Modifier.height(12.dp))
                                HcCard(
                                    contentPadding = PaddingValues(
                                        horizontal = 12.dp,
                                        vertical = 12.dp,
                                    )
                                ) {
                                    WeekdayHeader(firstDay)
                                    Spacer(Modifier.height(6.dp))
                                    WeekStrip(
                                        weekStart = pageWeekStart,
                                        selected = pageSelected,
                                        today = today,
                                        marks = monthLoad.marks,
                                        accent = accent,
                                        onSelect = ::setSelected,
                                    )
                                }
                            }
                        }

                        CalendarView.DAY -> {
                            val pageDate = selected.plusDays(step.toLong())
                            MonthNavigator(
                                label = pageDate.format(
                                    DateTimeFormatter.ofPattern(
                                        "EEE, d MMMM yyyy",
                                        Locale.getDefault(),
                                    )
                                ),
                                onPrevious = { shiftDay(-1) },
                                onNext = { shiftDay(1) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(26.dp))

                SwipeCarousel(
                    modifier = Modifier.fillMaxWidth(),
                    onSwitch = { direction ->
                        menuKey = null
                        shiftDay(direction.toLong())
                    },
                ) { step ->
                    JournalDayPage(
                        date = selected.plusDays(step.toLong()),
                        load = dayLoads.getValue(step),
                        monthLoaded = monthLoad.loaded,
                        hasAnything = monthLoad.hasAnything,
                        todayIso = todayIso,
                        accent = accent,
                        menuKey = menuKey,
                        onMenuClick = { key ->
                            menuKey = if (menuKey == key) null else key
                        },
                        onLongClick = { key -> menuKey = key },
                        onDismissMenu = { menuKey = null },
                        onCreateAi = onCreateAi,
                        onStartLive = onStartLive,
                        onDelete = { deleteTarget = it },
                        onOpenItem = onOpenItem,
                        onSchedule = { showSchedule = true },
                        onSync = onSync,
                    )
                }
            }
        }

        if (showSchedule) {
            val dayEvents = monthLoad.events[selectedIso].orEmpty()
            val suggestedStartMs = dayEvents.minByOrNull { it.startMs }?.startMs
                ?: LocalDate.parse(selectedIso)
                    .atTime(7, 0)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            ScheduleSessionDialog(
                date = selectedIso,
                workouts = monthLoad.workouts[selectedIso].orEmpty(),
                existingTypeKeys = (
                    dayLoads.getValue(0).items
                        .filterIsInstance<JournalItem.Workout>()
                        .map { it.typeKey } +
                        monthLoad.scheduled
                            .filter { it.date == selectedIso }
                            .map { it.typeKey }
                    ).toSet(),
                suggestedStartMs = suggestedStartMs,
                loading = !monthLoad.loaded || !dayLoads.getValue(0).loaded,
                onDismiss = { showSchedule = false },
                onScheduled = { DataRefresh.bump() },
            )
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Delete this entry?",
            message = JournalDeleter.confirmMessage(target),
            confirmText = "Delete",
            destructive = true,
            onConfirm = {
                deleteTarget = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        JournalDeleter.delete(context, target)
                    }
                    DataRefresh.bump()
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

/** One day as a carousel page; its neighbours are fully rendered too. */
@Composable
private fun JournalDayPage(
    date: LocalDate,
    load: JournalDayLoad,
    monthLoaded: Boolean,
    hasAnything: Boolean,
    todayIso: String,
    accent: Color,
    menuKey: String?,
    onMenuClick: (String) -> Unit,
    onLongClick: (String) -> Unit,
    onDismissMenu: () -> Unit,
    onCreateAi: (date: String, typeKey: String) -> Unit,
    onStartLive: (date: String, typeKey: String) -> Unit,
    onDelete: (JournalItem) -> Unit,
    onOpenItem: (date: String, key: String) -> Unit,
    onSchedule: () -> Unit,
    onSync: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        DayHeading(date = date, count = load.items.size, accent = accent)
        Spacer(Modifier.height(12.dp))
        ScheduleSessionButton(enabled = monthLoaded, onClick = onSchedule)
        Spacer(Modifier.height(14.dp))

        when {
            !monthLoaded || !load.loaded -> {
                LoadingLine("Reading your journal…", accent)
            }

            load.items.isEmpty() -> {
                val emptyJournal = !hasAnything
                EmptyState(
                    iconRes = R.drawable.ic_journal,
                    title = if (emptyJournal) {
                        "The journal is empty"
                    } else {
                        "Nothing on this day"
                    },
                    message = if (emptyJournal) {
                        "Sync to pull your workouts, watch sessions and sleep into the journal."
                    } else {
                        "No calendar workout, watch session or logged AI workout for this day. Sync for the latest."
                    },
                    accent = accent,
                    actionText = "Sync now",
                    onAction = onSync,
                )
            }

            else -> {
                load.items.forEach { item ->
                    JournalItemCard(
                        item = item,
                        planned = item.date > todayIso,
                        actions = JournalCardActions(
                            menuOpen = menuKey == item.key,
                            onMenuClick = { onMenuClick(item.key) },
                            onDismissMenu = onDismissMenu,
                            onCreateAi = {
                                (item as? JournalItem.Workout)?.let {
                                    onCreateAi(it.date, it.typeKey)
                                }
                            },
                            onStartLive = {
                                (item as? JournalItem.Workout)?.let {
                                    onStartLive(it.date, it.typeKey)
                                }
                            },
                            onDelete = { onDelete(item) },
                            onLongClick = { onLongClick(item.key) },
                        ),
                        onClick = { onOpenItem(item.date, item.key) },
                    )
                    Spacer(Modifier.height(CardGap))
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        if (!LocalMinimalDesign.current) {
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
private fun WeekdayHeader(firstDay: DayOfWeek) {
    Row(Modifier.fillMaxWidth()) {
        WeekStart.daysFrom(firstDay).forEach { day ->
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
private fun WeekStrip(
    weekStart: LocalDate,
    selected: LocalDate,
    today: LocalDate,
    marks: Map<String, JournalDayMark>,
    accent: Color,
    onSelect: (LocalDate) -> Unit,
) {
    Row(Modifier.fillMaxWidth()) {
        for (offset in 0L..6L) {
            val date = weekStart.plusDays(offset)
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

private fun weekLabel(weekStart: LocalDate): String {
    val locale = Locale.getDefault()
    val end = weekStart.plusDays(6)
    val sameMonth = weekStart.month == end.month
    val startFmt = DateTimeFormatter.ofPattern(if (sameMonth) "d" else "d MMM", locale)
    val endFmt = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    return "${weekStart.format(startFmt)} – ${end.format(endFmt)}"
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    marks: Map<String, JournalDayMark>,
    accent: Color,
    firstDay: DayOfWeek,
    onSelect: (LocalDate) -> Unit,
) {
    val leading = WeekStart.leadingDays(month.atDay(1), firstDay)
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
        if (!LocalMinimalDesign.current) {
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
}

/**
 * Under the day's title: the way to turn one of the day's programmed workouts
 * into an entry when the calendar has not named it yet.
 */
@Composable
private fun ScheduleSessionButton(enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Teal.copy(alpha = if (enabled) 0.10f else 0.04f))
            .border(1.dp, Teal.copy(alpha = if (enabled) 0.28f else 0.10f), shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Teal.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = if (enabled) Teal else Slate,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Schedule session",
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) Snow else Slate,
            )
            if (!LocalMinimalDesign.current) {
                Text(
                    "Add one of this day's workouts to the journal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = Slate,
            modifier = Modifier.size(20.dp),
        )
    }
}
