package com.hcexport.ui.journal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hcexport.AutoRecords
import com.hcexport.CalendarHelper
import com.hcexport.CalendarWorkoutEvent
import com.hcexport.DesignStyle
import com.hcexport.InteractiveStore
import com.hcexport.JournalAiPlan
import com.hcexport.JournalDeleter
import com.hcexport.JournalFeed
import com.hcexport.JournalHiddenStore
import com.hcexport.JournalItem
import com.hcexport.JournalNotesStore
import com.hcexport.JournalStorage
import com.hcexport.Prefs
import com.hcexport.R
import com.hcexport.RecordLogic
import com.hcexport.RecordStore
import com.hcexport.ScheduledSessionStore
import com.hcexport.Workout
import com.hcexport.WorkoutStore
import com.hcexport.WorkoutVideo
import com.hcexport.ui.AiWorkoutPanel
import com.hcexport.ui.ConfirmDialog
import com.hcexport.ui.DataRefresh
import com.hcexport.ui.EmptyState
import com.hcexport.ui.HcBackground
import com.hcexport.ui.HcCard
import com.hcexport.ui.HighlightOnArrival
import com.hcexport.ui.LoadingLine
import com.hcexport.ui.NewPrBadge
import com.hcexport.ui.NewPrCelebration
import com.hcexport.ui.PageHeader
import com.hcexport.ui.ScreenPadding
import com.hcexport.ui.SecondaryButton
import com.hcexport.ui.rememberAiWorkoutState
import com.hcexport.ui.theme.Amber
import com.hcexport.ui.theme.Abyss
import com.hcexport.ui.theme.Danger
import com.hcexport.ui.theme.Mist
import com.hcexport.ui.theme.Periwinkle
import com.hcexport.ui.theme.Slate
import com.hcexport.ui.theme.Snow
import com.hcexport.ui.theme.Success
import com.hcexport.ui.theme.Teal
import com.hcexport.ui.theme.workoutAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class JournalEntryLoad(
    val loaded: Boolean = false,
    val item: JournalItem? = null,
    val workouts: List<Workout> = emptyList(),
)

@Composable
fun JournalEntryScreen(
    date: String,
    entryKey: String,
    focus: String = "",
    onBack: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onAddWeight: (date: String, typeKey: String) -> Unit,
    onAddBenchmark: (date: String, typeKey: String) -> Unit,
    onStartLive: (date: String, typeKey: String) -> Unit,
    onOpenWeightRecord: (exerciseKey: String, recordId: Long) -> Unit,
    onOpenBenchmarkRecord: (benchmarkKey: String, recordId: Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val refresh by DataRefresh.tick.collectAsState()
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick += 1
        onPauseOrDispose { }
    }
    val minimal = remember(resumeTick) {
        Prefs.getDesignStyle(context) == DesignStyle.MINIMAL
    }

    val load by produceState(JournalEntryLoad(), date, entryKey, refresh, resumeTick) {
        value = withContext(Dispatchers.IO) {
            val workouts = WorkoutStore.getByDate(context, date)
            val entries = JournalStorage.getEntriesForDay(context, date)
            val sessions = runCatching {
                InteractiveStore.getBetween(context, date, date)
            }.getOrDefault(emptyList()).associateBy { it.typeKey }
            val scheduled = runCatching {
                ScheduledSessionStore.getForDay(context, date)
            }.getOrDefault(emptyList())
            val events = runCatching {
                val localDate = java.time.LocalDate.parse(date)
                val zone = java.time.ZoneId.systemDefault()
                val startMs = localDate.atStartOfDay(zone).toInstant().toEpochMilli()
                val endMs = localDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                CalendarHelper.findWorkoutEvents(context, startMs, endMs)
            }.getOrDefault(emptyList<CalendarWorkoutEvent>())
            JournalEntryLoad(
                loaded = true,
                item = JournalFeed.buildDay(
                    date = date,
                    workouts = workouts,
                    calendarEvents = events,
                    entries = entries,
                    sessions = sessions,
                    notes = JournalNotesStore.getAll(context),
                    sleepSourcePriority = Prefs.getSleepSourcePriority(context),
                    scheduledSessions = scheduled,
                    hiddenKeys = JournalHiddenStore.getAll(context),
                ).items.firstOrNull { it.key == entryKey },
                workouts = workouts,
            )
        }
    }

    val journalItem = load.item
    val todayIso = remember { java.time.LocalDate.now().toString() }
    val planned = journalItem is JournalItem.Workout && journalItem.date > todayIso

    // The AI workout is edited right here; this state is what the hero counts
    // and what the workout section renders.
    val workoutItem = journalItem as? JournalItem.Workout
    val aiState = workoutItem?.let { item ->
        rememberAiWorkoutState(
            date = date,
            typeKey = item.typeKey,
            // A legacy WOD entry has no archive row to build a plan from.
            workout = load.workouts
                .firstOrNull { it.typeKey == item.typeKey }
                ?.takeIf { item.canOpenAi },
            autoGenerate = false,
        )
    }
    val livePlan: JournalAiPlan? = workoutItem?.let { item ->
        aiState?.plan?.let { JournalFeed.aiPlan(it, aiState.results) } ?: item.plan
    }
    // Movement demos Fitr attaches to this workout's section(s).
    val videos by produceState(
        emptyList<WorkoutVideo>(),
        date, workoutItem?.typeKey, refresh, resumeTick,
    ) {
        val typeKey = workoutItem?.typeKey
        value = if (typeKey == null) emptyList() else withContext(Dispatchers.IO) {
            WorkoutStore.getVideos(context, date, typeKey)
        }
    }
    // The live session needs real work to run; notes-only sections do not count.
    val hasLiveContent = aiState?.plan?.sections?.any {
        it.exercises.isNotEmpty() || it.rounds.isNotEmpty() || it.metrics.isNotEmpty()
    } == true
    // Records are set from the session they were achieved in, so this entry owns
    // both the list and the two add actions.
    val recordsState by produceState(
        RecordStore.WorkoutRecords(emptyList(), emptyList()) to false,
        workoutItem?.typeKey, refresh, resumeTick, aiState?.recordsRevision,
    ) {
        val typeKey = workoutItem?.typeKey
        value = if (typeKey == null) {
            RecordStore.WorkoutRecords(emptyList(), emptyList()) to true
        } else {
            withContext(Dispatchers.IO) {
                RecordStore.recordsForWorkout(context, date, typeKey)
            } to true
        }
    }
    val workoutRecords = recordsState.first
    val recordsLoaded = recordsState.second
    var note by remember(entryKey, journalItem?.note) {
        mutableStateOf(journalItem?.note.orEmpty())
    }
    var seenRecordIds by remember { mutableStateOf<Set<String>?>(null) }
    var prCelebration by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(workoutRecords, recordsLoaded) {
        if (!recordsLoaded) return@LaunchedEffect
        val ids = workoutRecords.weights.map { "w:${it.id}" }.toSet() +
            workoutRecords.benchmarks.map { "b:${it.id}" }.toSet()
        val previous = seenRecordIds
        seenRecordIds = ids
        if (previous == null) return@LaunchedEffect
        val added = buildList {
            workoutRecords.weights.forEach { record ->
                if ("w:${record.id}" !in previous) {
                    add(
                        "${record.reps}RM ${record.exerciseName} · " +
                            "${RecordLogic.formatWeight(record.weightKg)} kg"
                    )
                }
            }
            workoutRecords.benchmarks.forEach { record ->
                if ("b:${record.id}" !in previous) {
                    add("${record.benchmarkName} · ${RecordLogic.formatScore(record)}")
                }
            }
        }
        if (added.isNotEmpty()) prCelebration = added
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    // A record link arrives as "w:<id>" or "b:<id>". A weight record that still
    // matches a strength set lands on that set's fields; anything else lands on
    // its row in this entry's records card.
    val focusRecord = remember(focus, workoutRecords) {
        if (focus.startsWith("w:")) {
            val id = focus.removePrefix("w:").toLongOrNull()
            workoutRecords.weights.firstOrNull { it.id == id }
        } else {
            null
        }
    }
    val focusBenchmark = remember(focus, workoutRecords) {
        if (focus.startsWith("b:")) {
            val id = focus.removePrefix("b:").toLongOrNull()
            workoutRecords.benchmarks.firstOrNull { it.id == id }
        } else {
            null
        }
    }
    val focusScope = remember(focusRecord, aiState?.plan, aiState?.results) {
        val plan = aiState?.plan
        val record = focusRecord
        if (plan != null && record != null) {
            AutoRecords.scopeFor(plan, aiState.results, record)
        } else {
            null
        }
    }

    fun saveAndBack() {
        if (deleting) {
            onBack()
            return
        }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { JournalNotesStore.set(context, entryKey, note) }
                aiState?.save()
            }
            onBack()
        }
    }

    BackHandler { saveAndBack() }

    LaunchedEffect(note, journalItem?.note, deleting) {
        if (deleting) return@LaunchedEffect
        if (journalItem == null || note == journalItem.note) return@LaunchedEffect
        delay(700)
        runCatching {
            withContext(Dispatchers.IO) { JournalNotesStore.set(context, entryKey, note) }
        }
    }

    val accent = journalItem?.let { itemAccent(it) } ?: Teal

    HcBackground(accent = accent) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            PageHeader(
                title = "Journal",
                eyebrow = RecordLogic.formatDate(date),
                onBack = { saveAndBack() },
            )

            when {
                !load.loaded -> LoadingLine("Reading this entry…", accent)
                journalItem == null -> EmptyState(
                    iconRes = R.drawable.ic_journal,
                    title = "Entry not found",
                    message = "This entry is no longer part of the journal. Sync to pull it back from your health data.",
                    accent = accent,
                )
                else -> Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.fillMaxSize().imePadding(),
                    contentPadding = PaddingValues(
                        start = ScreenPadding,
                        end = ScreenPadding,
                        top = 4.dp,
                        bottom = 30.dp,
                    ),
                ) {
                    item {
                        EntryHero(journalItem, planned, livePlan, minimal)
                        if (minimal && journalItem is JournalItem.Workout && hasLiveContent) {
                            Spacer(Modifier.height(16.dp))
                            LiveWorkoutCircle {
                                onStartLive(date, journalItem.typeKey)
                            }
                            Spacer(Modifier.height(22.dp))
                        } else {
                            Spacer(Modifier.height(24.dp))
                        }
                    }

                    when (journalItem) {
                        is JournalItem.Workout -> {
                            item {
                                val panel = aiState
                                if (panel != null) {
                                    AiWorkoutPanel(
                                        state = panel,
                                        onOpenAiSettings = onOpenAiSettings,
                                        minimal = minimal,
                                        onStartLive = if (minimal) null else {
                                            { onStartLive(date, journalItem.typeKey) }
                                        },
                                        focusScope = focusScope,
                                        original = {
                                            JournalWorkoutText(
                                                journalItem.content,
                                                workoutAccentFor(journalItem),
                                            )
                                        },
                                    )
                                } else {
                                    JournalWorkoutText(
                                        journalItem.content,
                                        workoutAccentFor(journalItem),
                                    )
                                }
                                if (videos.isNotEmpty()) {
                                    Spacer(Modifier.height(20.dp))
                                    WorkoutVideoStrip(
                                        videos = videos,
                                        accent = workoutAccentFor(journalItem),
                                    )
                                }
                                Spacer(Modifier.height(26.dp))
                            }
                            // A record can only be set in a workout that was done, so a
                            // planned entry offers neither the list nor the add actions.
                            if (!planned) {
                                item {
                                    JournalSectionHeader(
                                        title = "Records",
                                        accent = Amber,
                                        subtitle = if (minimal) null
                                        else "Lift PRs and benchmark results set in this session.",
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    if (!minimal || !workoutRecords.isEmpty) {
                                        HcCard(
                                            contentPadding = PaddingValues(
                                                horizontal = 16.dp,
                                                vertical = 8.dp,
                                            ),
                                        ) {
                                            if (workoutRecords.isEmpty && !minimal) {
                                                Text(
                                                    "No records set in this workout yet.",
                                                    modifier = Modifier.padding(vertical = 6.dp),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Slate,
                                                )
                                            }
                                            workoutRecords.weights.forEach { record ->
                                            HighlightOnArrival(
                                                active = focusRecord?.id == record.id &&
                                                    focusScope == null &&
                                                    aiState?.loading != true,
                                                color = Amber,
                                            ) {
                                                JournalRecordLine(
                                                    title = "${record.reps}RM · ${record.exerciseName}",
                                                    value = "${RecordLogic.formatWeight(record.weightKg)} kg",
                                                    accent = Amber,
                                                    pr = true,
                                                    onClick = {
                                                        onOpenWeightRecord(
                                                            record.exerciseKey,
                                                            record.id,
                                                        )
                                                    },
                                                )
                                            }
                                        }
                                        workoutRecords.benchmarks.forEach { record ->
                                            HighlightOnArrival(
                                                active = focusBenchmark?.id == record.id,
                                                color = Teal,
                                            ) {
                                                JournalRecordLine(
                                                    title = record.benchmarkName,
                                                    value = RecordLogic.formatScore(record),
                                                    accent = Teal,
                                                    pr = true,
                                                    onClick = {
                                                        onOpenBenchmarkRecord(
                                                            record.benchmarkKey,
                                                            record.id,
                                                        )
                                                    },
                                                )
                                            }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    SecondaryButton(
                                        text = "Add a weight record",
                                        onClick = { onAddWeight(date, journalItem.typeKey) },
                                        accent = Amber,
                                    )
                                    Spacer(Modifier.height(9.dp))
                                    SecondaryButton(
                                        text = "Add a benchmark result",
                                        onClick = { onAddBenchmark(date, journalItem.typeKey) },
                                        accent = Teal,
                                    )
                                    Spacer(Modifier.height(26.dp))
                                }
                            }
                            item {
                                JournalSectionHeader(
                                    title = "Watch data",
                                    accent = Teal,
                                    subtitle = if (minimal) null
                                    else "Recorded by your watch or strap and merged into this entry.",
                                )
                                Spacer(Modifier.height(12.dp))
                                if (journalItem.biometrics.isEmpty()) {
                                    EmptyWatchDataCard(planned, minimal)
                                } else {
                                    journalItem.biometrics.forEachIndexed { index, bio ->
                                        if (index > 0) Spacer(Modifier.height(10.dp))
                                        WatchDataCard(bio)
                                    }
                                }
                                Spacer(Modifier.height(26.dp))
                            }
                        }

                        is JournalItem.Activity -> {
                            item {
                                JournalSectionHeader(
                                    title = "Watch data",
                                    accent = Teal,
                                    subtitle = if (minimal) null
                                    else "Recorded by your watch or strap.",
                                )
                                Spacer(Modifier.height(12.dp))
                                WatchDataCard(journalItem.biometrics)
                                Spacer(Modifier.height(26.dp))
                            }
                        }

                        is JournalItem.Sleep -> {
                            item {
                                JournalSectionHeader(
                                    title = "Sleep",
                                    accent = Periwinkle,
                                    subtitle = if (minimal) null
                                    else "Stages as recorded by your watch or strap.",
                                )
                                Spacer(Modifier.height(12.dp))
                                SleepDetailCard(journalItem, Periwinkle)
                                Spacer(Modifier.height(26.dp))
                            }
                        }
                    }

                    item {
                        if (minimal) {
                            JournalCommentCard(
                                value = note,
                                onValueChange = { note = it },
                                placeholder = commentPlaceholder(journalItem),
                                accent = Amber,
                                minimal = true,
                            )
                        } else {
                            JournalSectionHeader(
                                title = "Comments",
                                accent = Amber,
                                subtitle = "Anything you want to remember about this entry.",
                            )
                            Spacer(Modifier.height(12.dp))
                            JournalCommentCard(
                                value = note,
                                onValueChange = { note = it },
                                placeholder = commentPlaceholder(journalItem),
                                accent = Amber,
                            )
                        }
                        Spacer(Modifier.height(22.dp))
                    }

                    item {
                        if (!minimal) {
                            Text(
                                "The journal merges your workout archive with Health Connect on every sync.",
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(18.dp))
                        }
                        SecondaryButton(
                            text = "Delete this entry",
                            onClick = { showDeleteConfirm = true },
                            accent = Danger,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
                if (prCelebration.isNotEmpty()) {
                    NewPrCelebration(
                        records = prCelebration,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = ScreenPadding, vertical = 8.dp),
                        onFinished = { prCelebration = emptyList() },
                    )
                }
                }
            }
        }
    }

    if (showDeleteConfirm && journalItem != null) {
        ConfirmDialog(
            title = "Delete this entry?",
            message = JournalDeleter.confirmMessage(journalItem),
            confirmText = "Delete",
            destructive = true,
            onConfirm = {
                showDeleteConfirm = false
                deleting = true
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            JournalDeleter.delete(context, journalItem)
                        }
                    }
                    DataRefresh.bump()
                    onBack()
                }
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

private fun workoutAccentFor(item: JournalItem.Workout) = workoutAccent(item.typeKey)

@Composable
private fun EntryHero(
    item: JournalItem,
    planned: Boolean,
    livePlan: JournalAiPlan?,
    minimal: Boolean,
) {
    when (item) {
        is JournalItem.Workout -> {
            val accent = workoutAccentFor(item)
            val bio = item.biometrics.firstOrNull()
            val plan = livePlan ?: item.plan
            val timeRange = if (item.startMs != null && item.endMs != null) {
                " · ${clockTime(item.startMs)} – ${clockTime(item.endMs)}"
            } else ""
            val typeLabel = com.hcexport.WorkoutText.displayType(item.typeLabel)
            if (minimal) {
                val future = planned ||
                    (item.scheduled && (item.endMs ?: 0L) > System.currentTimeMillis())
                val stats = buildList {
                    item.durationMin?.let {
                        add(HeroStat(JournalFeed.formatDuration(it), "DURATION"))
                    }
                    bio?.avgHrBpm?.let { add(HeroStat(it.toInt().toString(), "AVG BPM")) }
                }
                JournalHero(
                    title = plan?.title?.takeIf { it.isNotBlank() } ?: typeLabel,
                    subtitle = RecordLogic.formatDate(item.date) + timeRange,
                    accent = accent,
                    iconRes = R.drawable.ic_flame,
                    tags = listOf(
                        (if (future) "FUTURE" else "COMPLETED") to
                            (if (future) Amber else Success)
                    ),
                    stats = stats,
                    compact = true,
                )
                return
            }
            val stats = buildList {
                item.durationMin?.let { add(HeroStat(JournalFeed.formatDuration(it), "DURATION")) }
                plan?.let { ai ->
                    if (ai.loggedSets > 0) {
                        add(
                            HeroStat(
                                ai.loggedSets.toString(),
                                if (ai.loggedSets == 1) "SET LOGGED" else "SETS LOGGED",
                            )
                        )
                    }
                    ai.totalVolumeKg?.let {
                        add(HeroStat(JournalFeed.formatVolume(it), "VOLUME KG"))
                    }
                }
                bio?.avgHrBpm?.let { add(HeroStat(it.toInt().toString(), "AVG BPM")) }
            }
            JournalHero(
                title = typeLabel,
                subtitle = RecordLogic.formatDate(item.date) + timeRange,
                accent = accent,
                iconRes = R.drawable.ic_flame,
                tags = buildList {
                    add("WORKOUT" to accent)
                    if (item.scheduled) add("SCHEDULED" to Periwinkle)
                    if (planned) add("PLANNED" to Amber)
                    if (plan != null) add("AI PLAN" to Teal)
                    if (item.biometrics.isNotEmpty()) add("WATCH DATA" to Teal)
                },
                stats = stats,
            )
        }

        is JournalItem.Activity -> {
            val bio = item.biometrics
            val (_, typeLabel) = CalendarHelper.EXERCISE_TYPES[bio.typeCode] ?: ("🏃" to "Activity")
            val stats = buildList {
                add(HeroStat(JournalFeed.formatDuration(bio.durationMin), "DURATION"))
                bio.distanceM?.let { add(HeroStat(JournalFeed.formatDistance(it), "DISTANCE KM")) }
                bio.avgHrBpm?.let { add(HeroStat(it.toInt().toString(), "AVG BPM")) }
                bio.caloriesKcal?.let { add(HeroStat(it.toInt().toString(), "CALORIES")) }
            }
            JournalHero(
                title = bio.title.ifBlank { typeLabel },
                subtitle = "${RecordLogic.formatDate(item.date)} · " +
                    "${clockTime(bio.startMs)} – ${clockTime(bio.endMs)}",
                accent = Teal,
                iconRes = R.drawable.ic_stopwatch,
                tags = if (minimal) {
                    listOf(typeLabel.uppercase() to Slate)
                } else {
                    listOf(
                        "ACTIVITY" to Teal,
                        typeLabel.uppercase() to Slate,
                    )
                },
                stats = stats,
                compact = minimal,
            )
        }

        is JournalItem.Sleep -> {
            fun minutes(stage: String) =
                item.stages.firstOrNull { it.name == stage }?.minutes
            val stats = buildList {
                add(HeroStat(JournalFeed.formatDuration(item.durationMin), "ASLEEP"))
                minutes("Deep Sleep")?.let {
                    add(HeroStat(JournalFeed.formatDuration(it), "DEEP"))
                }
                minutes("REM")?.let { add(HeroStat(JournalFeed.formatDuration(it), "REM")) }
                minutes("Awake")?.let { add(HeroStat(JournalFeed.formatDuration(it), "AWAKE")) }
            }
            JournalHero(
                title = "Sleep",
                subtitle = "${RecordLogic.formatDate(item.date)} · " +
                    "${clockTime(item.startMs)} – ${clockTime(item.endMs)}",
                accent = Periwinkle,
                iconRes = R.drawable.ic_moon,
                tags = if (minimal) emptyList() else listOf("SLEEP" to Periwinkle),
                stats = stats,
                compact = minimal,
            )
        }
    }
}

/**
 * Minimal design: the way into the live session is one big circle under the
 * workout card, with no explanatory copy.
 */
@Composable
private fun LiveWorkoutCircle(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(122.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Teal, Periwinkle)))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Abyss,
                    modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Live workout",
                    style = MaterialTheme.typography.titleSmall,
                    color = Abyss,
                )
            }
        }
    }
}

private fun commentPlaceholder(item: JournalItem): String = when (item) {
    is JournalItem.Workout ->
        "How did it feel? Note the load, the pace, anything worth remembering."
    is JournalItem.Activity ->
        "Anything worth remembering about this session?"
    is JournalItem.Sleep ->
        "How did you sleep?"
}

@Composable
private fun JournalRecordLine(
    title: String,
    value: String,
    accent: Color,
    pr: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (pr) {
                    Modifier
                        .background(
                            Brush.horizontalGradient(
                                listOf(Amber.copy(alpha = 0.15f), Color.Transparent),
                            )
                        )
                        .border(1.dp, Amber.copy(alpha = 0.32f), shape)
                } else {
                    Modifier
                }
            )
            .then(
                if (onClick != null) Modifier
                    .clip(shape)
                    .clickable(onClick = onClick)
                else Modifier
            )
            .padding(vertical = 6.dp, horizontal = if (onClick != null) 6.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (pr) Amber else accent))
        Spacer(Modifier.width(10.dp))
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = Mist,
            )
            if (pr) {
                Spacer(Modifier.width(6.dp))
                NewPrBadge()
            }
        }
        Text(
            value,
            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
            color = Snow,
        )
    }
}
