package com.hcexport.ui.workouts

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hcexport.Prefs
import com.hcexport.R
import com.hcexport.InteractiveStore
import com.hcexport.RecordStore
import com.hcexport.RecordLogic
import com.hcexport.Workout
import com.hcexport.WorkoutBlock
import com.hcexport.WorkoutStore
import com.hcexport.WorkoutText
import com.hcexport.ui.CardGap
import com.hcexport.ui.DataRefresh
import com.hcexport.ui.EmptyState
import com.hcexport.ui.HcBackground
import com.hcexport.ui.HcCard
import com.hcexport.ui.IconAction
import com.hcexport.ui.NumberText
import com.hcexport.ui.PageHeader
import com.hcexport.ui.ScreenPadding
import com.hcexport.ui.SecondaryButton
import com.hcexport.ui.SectionLabel
import com.hcexport.ui.SegmentedControl
import com.hcexport.ui.Tag
import com.hcexport.ui.theme.Amber
import com.hcexport.ui.theme.CardHigh
import com.hcexport.ui.theme.Hairline
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
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private data class WorkoutsLoad(
    val loaded: Boolean = false,
    val workouts: List<Workout> = emptyList(),
    val total: Int = 0,
)

private fun visibleRange(date: LocalDate, view: CalendarView): Pair<LocalDate, LocalDate> = when (view) {
    CalendarView.MONTH -> date.withDayOfMonth(1) to date.withDayOfMonth(date.lengthOfMonth())
    CalendarView.WEEK, CalendarView.DAY -> {
        val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        start to start.plusDays(6)
    }
}

enum class CalendarView(val key: String, val label: String) {
    WEEK("week", "Week"),
    MONTH("month", "Month"),
    DAY("day", "Day");

    companion object {
        fun fromKey(key: String?): CalendarView =
            entries.firstOrNull { it.key == key } ?: WEEK
    }
}

private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

private fun LocalDate.iso(): String = format(ISO)

@Composable
fun WorkoutsScreen(
    onBack: () -> Unit,
    onOpenWorkout: (date: String, typeKey: String) -> Unit,
    onSync: () -> Unit,
    onOpenHistory: () -> Unit,
    initialFocus: String? = null,
    initialView: String? = null,
) {
    val context = LocalContext.current
    val refresh by DataRefresh.tick.collectAsState()
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick += 1
        onPauseOrDispose { }
    }

    val startView = remember {
        initialView?.let { CalendarView.fromKey(it) }
            ?: CalendarView.fromKey(Prefs.getDefaultWorkoutView(context))
    }
    var viewKey by rememberSaveable { mutableStateOf(startView.key) }
    val view = CalendarView.fromKey(viewKey)
    val startFocus = remember {
        initialFocus?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()
    }
    var focusIso by rememberSaveable { mutableStateOf(startFocus.toString()) }
    val focusDate = LocalDate.parse(focusIso)
    val today = LocalDate.now()

    val range = visibleRange(focusDate, view)
    val rangeStart = range.first.iso()
    val rangeEnd = range.second.iso()
    val load by produceState(WorkoutsLoad(), refresh, resumeTick, rangeStart, rangeEnd) {
        value = WorkoutsLoad(loaded = false, workouts = emptyList(), total = value.total)
        val total = withContext(Dispatchers.IO) { WorkoutStore.count(context) }
        val workouts = withContext(Dispatchers.IO) {
            WorkoutStore.getBetween(context, rangeStart, rangeEnd)
        }
        value = WorkoutsLoad(loaded = true, workouts = workouts, total = total)
    }
    val workouts = load.workouts
    val byDate = remember(workouts) { workouts.groupBy { it.date } }
    val accent = Periwinkle

    val dayWorkouts = byDate[focusDate.iso()].orEmpty().sortedBy { it.sortOrder }

    HcBackground(accent = accent) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            PageHeader(
                title = "Workouts",
                eyebrow = "Training calendar",
                onBack = onBack,
                trailing = {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(accent.copy(alpha = 0.13f))
                            .border(1.dp, accent.copy(alpha = 0.26f), RoundedCornerShape(50))
                            .clickable(onClick = onOpenHistory)
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_archive),
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "HISTORY",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                        )
                    }
                    if (focusDate != today) {
                        Text(
                            "TODAY",
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(accent.copy(alpha = 0.13f))
                                .border(1.dp, accent.copy(alpha = 0.26f), RoundedCornerShape(50))
                                .clickable { focusIso = today.toString() }
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
                    selected = view,
                    label = { it.label },
                    onSelect = { viewKey = it.key },
                    accent = accent,
                )
                Spacer(Modifier.height(18.dp))

                if (view == CalendarView.DAY) {
                    DayHero(
                        date = focusDate,
                        count = dayWorkouts.size,
                        accent = accent,
                        onPrevious = { focusIso = shift(focusDate, view, -1).toString() },
                        onNext = { focusIso = shift(focusDate, view, 1).toString() },
                    )
                    Spacer(Modifier.height(22.dp))
                } else {
                    PeriodNavigator(
                        label = periodLabel(focusDate, view),
                        onPrevious = { focusIso = shift(focusDate, view, -1).toString() },
                        onNext = { focusIso = shift(focusDate, view, 1).toString() },
                    )
                    Spacer(Modifier.height(14.dp))

                    when (view) {
                        CalendarView.WEEK -> WeekStrip(
                            focusDate = focusDate,
                            today = today,
                            byDate = byDate,
                            accent = accent,
                            onSelect = { focusIso = it.toString() },
                        )
                        CalendarView.MONTH -> MonthGrid(
                            month = YearMonth.from(focusDate),
                            focusDate = focusDate,
                            today = today,
                            byDate = byDate,
                            accent = accent,
                            onSelect = { focusIso = it.toString() },
                        )
                        CalendarView.DAY -> Unit
                    }

                    Spacer(Modifier.height(24.dp))
                    DayHeading(date = focusDate, count = dayWorkouts.size, accent = accent)
                    Spacer(Modifier.height(12.dp))
                }

                if (!load.loaded) {
                    com.hcexport.ui.LoadingLine("Loading your calendar…", accent)
                } else if (dayWorkouts.isEmpty()) {
                    EmptyState(
                        iconRes = R.drawable.ic_flame,
                        title = if (load.total == 0) "No workouts yet" else "Rest day",
                        message = if (load.total == 0)
                            "Sync to pull the latest programming into the calendar."
                        else
                            "Nothing programmed for ${RecordLogic.formatDate(focusDate.iso())}.",
                        accent = accent,
                        actionText = if (load.total == 0) "Sync now" else null,
                        onAction = if (load.total == 0) onSync else null,
                    )
                } else {
                    dayWorkouts.forEach { workout ->
                        WorkoutCard(
                            workout = workout,
                            onClick = { onOpenWorkout(workout.date, workout.typeKey) },
                        )
                        Spacer(Modifier.height(CardGap))
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

private fun shift(date: LocalDate, view: CalendarView, direction: Long): LocalDate = when (view) {
    CalendarView.WEEK -> date.plusWeeks(direction)
    CalendarView.MONTH -> date.plusMonths(direction)
    CalendarView.DAY -> date.plusDays(direction)
}

private fun periodLabel(date: LocalDate, view: CalendarView): String {
    val locale = Locale.getDefault()
    return when (view) {
        CalendarView.WEEK -> {
            val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val end = start.plusDays(6)
            val sameMonth = start.month == end.month
            val startFmt = DateTimeFormatter.ofPattern(if (sameMonth) "d" else "d MMM", locale)
            val endFmt = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
            "${start.format(startFmt)} – ${end.format(endFmt)}"
        }
        CalendarView.MONTH -> date.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
        CalendarView.DAY -> date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale))
    }
}

@Composable
private fun PeriodNavigator(
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
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous", tint = Mist)
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
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconAction(
            icon = {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next", tint = Mist)
            },
            onClick = onNext,
            border = false,
            background = Color.Transparent,
        )
    }
}

@Composable
private fun WeekStrip(
    focusDate: LocalDate,
    today: LocalDate,
    byDate: Map<String, List<Workout>>,
    accent: Color,
    onSelect: (LocalDate) -> Unit,
) {
    val start = focusDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val dates = (0L..6L).map { start.plusDays(it) }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        dates.forEach { date ->
            DayCell(
                date = date,
                selected = date == focusDate,
                isToday = date == today,
                workouts = byDate[date.iso()].orEmpty(),
                accent = accent,
                onClick = { onSelect(date) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    workouts: List<Workout>,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(15.dp)
    Column(
        modifier
            .heightIn(min = 76.dp)
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.16f) else CardHigh.copy(alpha = 0.55f))
            .border(
                1.dp,
                when {
                    selected -> accent.copy(alpha = 0.55f)
                    isToday -> accent.copy(alpha = 0.30f)
                    else -> Hairline
                },
                shape,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) accent else Slate,
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleLarge.copy(
                fontFeatureSettings = "tnum",
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = when {
                selected -> accent
                isToday -> Snow
                else -> Mist
            },
        )
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            workouts.take(3).forEach {
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(workoutAccent(it.typeKey))
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    focusDate: LocalDate,
    today: LocalDate,
    byDate: Map<String, List<Workout>>,
    accent: Color,
    onSelect: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value - DayOfWeek.MONDAY.value
    val days = month.lengthOfMonth()
    val rows = ((leading + days + 6) / 7)
    val dayHeaders = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

    Column {
        Row(Modifier.fillMaxWidth()) {
            dayHeaders.forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val index = row * 7 + col - leading
                    if (index !in 0 until days) {
                        Spacer(Modifier.weight(1f).heightIn(min = 46.dp))
                    } else {
                        val date = month.atDay(index + 1)
                        MonthDayCell(
                            date = date,
                            selected = date == focusDate,
                            isToday = date == today,
                            workouts = byDate[date.iso()].orEmpty(),
                            accent = accent,
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
        }
    }
}

@Composable
private fun MonthDayCell(
    date: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    workouts: List<Workout>,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(13.dp)
    Column(
        modifier
            .heightIn(min = 46.dp)
            .padding(2.dp)
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent)
            .then(
                if (selected) Modifier.border(1.dp, accent.copy(alpha = 0.45f), shape)
                else Modifier
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
        Row(horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
            workouts.take(3).forEach {
                Box(
                    Modifier
                        .size(3.5.dp)
                        .clip(CircleShape)
                        .background(workoutAccent(it.typeKey))
                )
            }
        }
    }
}

@Composable
private fun DayHero(
    date: LocalDate,
    count: Int,
    accent: Color,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(
            icon = {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous day", tint = Mist)
            },
            onClick = onPrevious,
            border = false,
            background = CardHigh.copy(alpha = 0.55f),
        )
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                date.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = accent,
            )
            Spacer(Modifier.height(4.dp))
            NumberText(
                date.dayOfMonth.toString(),
                color = Snow,
                size = 64,
                weight = FontWeight.Light,
            )
            Text(
                date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                style = MaterialTheme.typography.titleSmall,
                color = Mist,
            )
            Spacer(Modifier.height(10.dp))
            Tag(if (count == 0) "REST DAY" else if (count == 1) "1 WORKOUT" else "$count WORKOUTS", accent)
        }
        IconAction(
            icon = {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next day", tint = Mist)
            },
            onClick = onNext,
            border = false,
            background = CardHigh.copy(alpha = 0.55f),
        )
    }
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
                date.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())).uppercase(),
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
            if (count == 0) "REST DAY" else if (count == 1) "1 WORKOUT" else "$count WORKOUTS",
            accent,
        )
    }
}

@Composable
private fun WorkoutCard(workout: Workout, onClick: () -> Unit) {
    val accent = workoutAccent(workout.typeKey)
    HcCard(
        accent = accent,
        contentPadding = PaddingValues(15.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent)
            )
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    WorkoutText.displayType(workout.typeLabel),
                    style = MaterialTheme.typography.titleLarge,
                    color = Snow,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    WorkoutText.summary(workout.content),
                    style = MaterialTheme.typography.bodySmall,
                    color = Mist,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = Slate,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ── Workout detail ─────────────────────────────────────────────────────────

private data class WorkoutDetailLoad(
    val loaded: Boolean = false,
    val workout: Workout? = null,
)

@Composable
fun WorkoutDetailScreen(
    date: String,
    typeKey: String,
    onBack: () -> Unit,
    onAddWeight: (date: String, typeKey: String) -> Unit,
    onAddBenchmark: (date: String, typeKey: String) -> Unit,
    onOpenInteractive: (date: String, typeKey: String) -> Unit,
) {
    val context = LocalContext.current
    val refresh by DataRefresh.tick.collectAsState()
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick += 1
        onPauseOrDispose { }
    }
    val hasInteractive by produceState(false, date, typeKey, refresh, resumeTick) {
        value = withContext(Dispatchers.IO) {
            InteractiveStore.get(context, date, typeKey) != null
        }
    }
    val detail by produceState(WorkoutDetailLoad(), date, typeKey, refresh) {
        value = withContext(Dispatchers.IO) {
            WorkoutDetailLoad(
                loaded = true,
                workout = WorkoutStore.getByDate(context, date).firstOrNull { it.typeKey == typeKey },
            )
        }
    }
    val linked by produceState(RecordStore.WorkoutRecords(emptyList(), emptyList()), date, typeKey, refresh) {
        value = withContext(Dispatchers.IO) { RecordStore.recordsForWorkout(context, date, typeKey) }
    }

    val display = detail.workout
    val accent = workoutAccent(typeKey)
    val label = display?.let { WorkoutText.displayType(it.typeLabel) } ?: typeKey.uppercase()

    HcBackground(accent = accent) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            PageHeader(
                title = "Workout",
                onBack = onBack,
            )
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 34.dp),
            ) {
                item {
                    HeroCard(
                        label = label,
                        date = date,
                        accent = accent,
                    )
                    Spacer(Modifier.height(16.dp))
                }

                if (!detail.loaded) {
                    item { com.hcexport.ui.LoadingLine("Loading workout…", accent) }
                } else if (display == null) {
                    item {
                        EmptyState(
                            iconRes = R.drawable.ic_calendar,
                            title = "Workout not found",
                            message = "It may have been removed when the workout database was refreshed.",
                            accent = accent,
                        )
                    }
                } else {
                    item {
                        AiWorkoutCard(
                            hasPlan = hasInteractive,
                            onClick = { onOpenInteractive(date, typeKey) },
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    item {
                        val blocks = remember(display.content) { WorkoutText.parse(display.content) }
                        HcCard(contentPadding = PaddingValues(20.dp)) {
                            SectionLabel("The session", color = accent)
                            Spacer(Modifier.height(14.dp))
                            if (blocks.isEmpty()) {
                                Text(
                                    "No details for this workout.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Slate,
                                )
                            } else {
                                blocks.forEachIndexed { index, block ->
                                    if (index > 0) Spacer(Modifier.height(14.dp))
                                    WorkoutBlockView(block, accent)
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    if (!linked.isEmpty) {
                        item {
                            HcCard(accent = Amber, contentPadding = PaddingValues(18.dp)) {
                                SectionLabel("Records set here", color = Amber)
                                Spacer(Modifier.height(10.dp))
                                linked.weights.forEach { record ->
                                    RecordLine(
                                        title = "${record.reps}RM · ${record.exerciseName}",
                                        value = "${RecordLogic.formatWeight(record.weightKg)} kg",
                                        accent = Amber,
                                    )
                                }
                                linked.benchmarks.forEach { record ->
                                    RecordLine(
                                        title = record.benchmarkName,
                                        value = RecordLogic.formatScore(record),
                                        accent = Teal,
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    item {
                        SecondaryButton(
                            text = "Add a weight record",
                            onClick = { onAddWeight(date, typeKey) },
                            accent = Amber,
                        )
                        Spacer(Modifier.height(9.dp))
                        SecondaryButton(
                            text = "Add a benchmark result",
                            onClick = { onAddBenchmark(date, typeKey) },
                            accent = Teal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiWorkoutCard(hasPlan: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(
                        Teal.copy(alpha = 0.18f),
                        Periwinkle.copy(alpha = 0.16f),
                        CardHigh,
                    )
                )
            )
            .border(1.dp, Teal.copy(alpha = 0.30f), shape)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(Teal, Periwinkle)
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_ai),
                    contentDescription = null,
                    tint = com.hcexport.ui.theme.Abyss,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "AI workout",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Snow,
                    )
                    Spacer(Modifier.width(8.dp))
                    Tag(
                        if (hasPlan) "READY" else "AI",
                        if (hasPlan) com.hcexport.ui.theme.Success else Teal,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    if (hasPlan) "Open your interactive session"
                    else "Turn this session into tracked sets",
                    style = MaterialTheme.typography.bodySmall,
                    color = Mist,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Your AI provider · sets, reps, time & load",
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate,
                )
            }
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
private fun HeroCard(label: String, date: String, accent: Color) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 178.dp)
            .clip(shape)
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.24f), CardHigh, com.hcexport.ui.theme.Card)
                )
            )
            .border(1.dp, accent.copy(alpha = 0.30f), shape)
    ) {
        Icon(
            androidx.compose.ui.res.painterResource(R.drawable.ic_flame),
            contentDescription = null,
            tint = accent.copy(alpha = 0.09f),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).size(150.dp),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Tag("WORKOUT", accent)
            Spacer(Modifier.height(10.dp))
            Text(
                label,
                style = MaterialTheme.typography.displaySmall,
                color = Snow,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                RecordLogic.formatDate(date),
                style = MaterialTheme.typography.bodyMedium,
                color = Mist,
            )
        }
    }
}

@Composable
private fun WorkoutBlockView(block: WorkoutBlock, accent: Color) {
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
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
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
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
            color = Snow,
        )
    }
}

@Composable
private fun RecordLine(title: String, value: String, accent: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = Mist,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
            color = Snow,
        )
    }
}
