package com.hcexport.ui.journal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hcexport.CalendarHelper
import com.hcexport.CalendarWorkoutEvent
import com.hcexport.InteractiveStore
import com.hcexport.JournalAiPlan
import com.hcexport.JournalFeed
import com.hcexport.JournalItem
import com.hcexport.JournalNotesStore
import com.hcexport.JournalStorage
import com.hcexport.R
import com.hcexport.RecordLogic
import com.hcexport.Workout
import com.hcexport.WorkoutStore
import com.hcexport.ui.AiWorkoutPanel
import com.hcexport.ui.DataRefresh
import com.hcexport.ui.EmptyState
import com.hcexport.ui.HcBackground
import com.hcexport.ui.LoadingLine
import com.hcexport.ui.PageHeader
import com.hcexport.ui.ScreenPadding
import com.hcexport.ui.rememberAiWorkoutState
import com.hcexport.ui.theme.Amber
import com.hcexport.ui.theme.Periwinkle
import com.hcexport.ui.theme.Slate
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
    onBack: () -> Unit,
    onOpenAiSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val refresh by DataRefresh.tick.collectAsState()
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick += 1
        onPauseOrDispose { }
    }

    val load by produceState(JournalEntryLoad(), date, entryKey, refresh, resumeTick) {
        value = withContext(Dispatchers.IO) {
            val workouts = WorkoutStore.getByDate(context, date)
            val entries = JournalStorage.getEntriesForDay(context, date)
            val sessions = runCatching {
                InteractiveStore.getBetween(context, date, date)
            }.getOrDefault(emptyList()).associateBy { it.typeKey }
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
    var note by remember(entryKey, journalItem?.note) {
        mutableStateOf(journalItem?.note.orEmpty())
    }

    fun saveAndBack() {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { JournalNotesStore.set(context, entryKey, note) }
                aiState?.save()
            }
            onBack()
        }
    }

    BackHandler { saveAndBack() }

    LaunchedEffect(note, journalItem?.note) {
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
                else -> LazyColumn(
                    Modifier.fillMaxSize().imePadding(),
                    contentPadding = PaddingValues(
                        start = ScreenPadding,
                        end = ScreenPadding,
                        top = 4.dp,
                        bottom = 30.dp,
                    ),
                ) {
                    item {
                        EntryHero(journalItem, planned, livePlan)
                        Spacer(Modifier.height(24.dp))
                    }

                    when (journalItem) {
                        is JournalItem.Workout -> {
                            item {
                                val panel = aiState
                                if (panel != null) {
                                    AiWorkoutPanel(
                                        state = panel,
                                        onOpenAiSettings = onOpenAiSettings,
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
                                Spacer(Modifier.height(26.dp))
                            }
                            item {
                                JournalSectionHeader(
                                    title = "Watch data",
                                    accent = Teal,
                                    subtitle = "Recorded by your watch or strap and merged into this entry.",
                                )
                                Spacer(Modifier.height(12.dp))
                                if (journalItem.biometrics.isEmpty()) {
                                    EmptyWatchDataCard(planned)
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
                                    subtitle = "Recorded by your watch or strap.",
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
                                    subtitle = "Stages as recorded by your watch or strap.",
                                )
                                Spacer(Modifier.height(12.dp))
                                SleepDetailCard(journalItem, Periwinkle)
                                Spacer(Modifier.height(26.dp))
                            }
                        }
                    }

                    item {
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
                        Spacer(Modifier.height(22.dp))
                    }

                    item {
                        Text(
                            "The journal merges your workout archive with Health Connect on every sync.",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

private fun workoutAccentFor(item: JournalItem.Workout) = workoutAccent(item.typeKey)

@Composable
private fun EntryHero(item: JournalItem, planned: Boolean, livePlan: JournalAiPlan?) {
    when (item) {
        is JournalItem.Workout -> {
            val accent = workoutAccentFor(item)
            val bio = item.biometrics.firstOrNull()
            val plan = livePlan ?: item.plan
            val timeRange = if (item.startMs != null && item.endMs != null) {
                " · ${clockTime(item.startMs)} – ${clockTime(item.endMs)}"
            } else ""
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
                title = com.hcexport.WorkoutText.displayType(item.typeLabel),
                subtitle = RecordLogic.formatDate(item.date) + timeRange,
                accent = accent,
                iconRes = R.drawable.ic_flame,
                tags = buildList {
                    add("WORKOUT" to accent)
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
                tags = listOf(
                    "ACTIVITY" to Teal,
                    typeLabel.uppercase() to Slate,
                ),
                stats = stats,
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
                tags = listOf("SLEEP" to Periwinkle),
                stats = stats,
            )
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
