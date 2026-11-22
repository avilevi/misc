package com.hcexport.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hcexport.JournalKeys
import com.hcexport.LiveSessionHub
import com.hcexport.shared.DEVICE_WATCH
import com.hcexport.ui.records.BenchmarkDetailScreen
import com.hcexport.ui.records.BenchmarkEditorScreen
import com.hcexport.ui.records.BenchmarksScreen
import com.hcexport.ui.records.RecordsScreen
import com.hcexport.ui.records.WeightDetailScreen
import com.hcexport.ui.records.WeightEditorScreen
import com.hcexport.ui.records.WeightsScreen
import com.hcexport.ui.journal.JournalEntryScreen
import com.hcexport.ui.journal.JournalScreen
import com.hcexport.ui.live.LiveWorkoutScreen
import com.hcexport.ui.workouts.InteractiveWorkoutScreen
import com.hcexport.ui.workouts.WorkoutDetailScreen
import com.hcexport.ui.workouts.WorkoutHistoryScreen
import com.hcexport.ui.workouts.WorkoutsScreen

private object Routes {
    const val HOME = "home"
    const val JOURNAL = "journal"
    const val JOURNAL_ITEM = "journal/{date}/{key}?focus={focus}"
    const val WORKOUTS = "workouts?focus={focus}&view={view}"
    const val HISTORY = "history"
    const val WORKOUT = "workout/{date}/{typeKey}"
    const val INTERACTIVE = "interactive/{date}/{typeKey}"
    const val LIVE = "live/{date}/{typeKey}?join={join}"
    const val RECORDS = "records"
    const val WEIGHTS = "records/weights"
    const val WEIGHT = "records/weights/{exerciseKey}?recordId={recordId}&from={from}"
    const val BENCHMARKS = "records/benchmarks"
    const val BENCHMARK = "records/benchmarks/{benchmarkKey}?recordId={recordId}&from={from}"
    const val WEIGHT_NEW = "record/weight/new?exerciseKey={exerciseKey}&exerciseName={exerciseName}&date={date}&typeKey={typeKey}"
    const val WEIGHT_EDIT = "record/weight/edit/{id}"
    const val BENCHMARK_NEW = "record/benchmark/new?benchmarkKey={benchmarkKey}&benchmarkName={benchmarkName}&date={date}&typeKey={typeKey}"
    const val BENCHMARK_EDIT = "record/benchmark/edit/{id}"
}

private fun workout(date: String, typeKey: String) = "workout/$date/$typeKey"
private fun liveWorkout(date: String, typeKey: String) = "live/$date/$typeKey"
private fun interactive(date: String, typeKey: String) = "interactive/$date/$typeKey"

/**
 * A record's source workout is one that was performed, so it links to the
 * journal entry rather than the raw workout page in the Workouts calendar.
 */
private fun journalEntry(date: String, typeKey: String, focus: String = "") =
    "journal/$date/${Uri.encode(JournalKeys.workout(date, typeKey))}" +
        if (focus.isBlank()) "" else "?focus=${Uri.encode(focus)}"

/** from = "journal" or "workout" — what the record screen's back button returns to. */
private fun weightDetail(exerciseKey: String, recordId: Long = 0L, from: String = "") =
    "records/weights/${Uri.encode(exerciseKey)}?recordId=$recordId&from=${Uri.encode(from)}"

private fun benchmarkDetail(benchmarkKey: String, recordId: Long = 0L, from: String = "") =
    "records/benchmarks/${Uri.encode(benchmarkKey)}?recordId=$recordId&from=${Uri.encode(from)}"
private fun weightNew(
    exerciseKey: String? = null,
    name: String? = null,
    date: String? = null,
    typeKey: String? = null,
): String {
    val params = buildList {
        if (!exerciseKey.isNullOrBlank()) add("exerciseKey=$exerciseKey")
        if (!name.isNullOrBlank()) add("exerciseName=${Uri.encode(name)}")
        if (!date.isNullOrBlank()) add("date=$date")
        if (!typeKey.isNullOrBlank()) add("typeKey=$typeKey")
    }
    return "record/weight/new" + if (params.isEmpty()) "" else "?" + params.joinToString("&")
}
private fun benchmarkNew(
    benchmarkKey: String? = null,
    name: String? = null,
    date: String? = null,
    typeKey: String? = null,
): String {
    val params = buildList {
        if (!benchmarkKey.isNullOrBlank()) add("benchmarkKey=$benchmarkKey")
        if (!name.isNullOrBlank()) add("benchmarkName=${Uri.encode(name)}")
        if (!date.isNullOrBlank()) add("date=$date")
        if (!typeKey.isNullOrBlank()) add("typeKey=$typeKey")
    }
    return "record/benchmark/new" + if (params.isEmpty()) "" else "?" + params.joinToString("&")
}

@Composable
fun HcApp(
    statusTitle: String,
    statusSubtitle: String,
    statusAccent: Color,
    syncing: Boolean,
    onSync: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAiSettings: () -> Unit,
    joinRequest: LiveJoinRequest? = null,
    onJoinHandled: () -> Unit = {},
) {
    val nav = rememberNavController()

    // The watch started a workout while this phone was on screen: join it.
    // The live screen adopts the shared stage; this only gets us there.
    val remote by LiveSessionHub.remote.collectAsState()
    val backStack by nav.currentBackStackEntryAsState()
    var autoJoinedSession by remember { mutableStateOf<String?>(null) }

    // A notification (or an explicit link) asked for one workout's live screen.
    LaunchedEffect(joinRequest?.nonce) {
        val request = joinRequest ?: return@LaunchedEffect
        val current = remote
        if (current != null && current.date == request.date &&
            current.typeKey == request.typeKey && LiveSessionHub.isClosed(current)
        ) {
            onJoinHandled()
            return@LaunchedEffect
        }
        // The same record may also arrive through the flow; join once.
        autoJoinedSession = current?.sessionId
        nav.navigate("live/${request.date}/${request.typeKey}?join=true")
        onJoinHandled()
    }
    LaunchedEffect(remote?.sessionId, remote?.updatedAt, remote?.open) {
        val session = remote ?: return@LaunchedEffect
        if (!session.open || session.origin != DEVICE_WATCH) return@LaunchedEffect
        if (LiveSessionHub.isClosed(session)) return@LaunchedEffect
        if (LiveSessionHub.localActive) return@LaunchedEffect
        if (session.sessionId == autoJoinedSession) return@LaunchedEffect
        if (System.currentTimeMillis() - session.updatedAt > 10 * 60_000L) {
            return@LaunchedEffect
        }
        if (backStack?.destination?.route.orEmpty().startsWith("live/")) {
            return@LaunchedEffect
        }
        autoJoinedSession = session.sessionId
        nav.navigate("live/${session.date}/${session.typeKey}?join=true")
    }

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                statusTitle = statusTitle,
                statusSubtitle = statusSubtitle,
                statusAccent = statusAccent,
                syncing = syncing,
                onSync = onSync,
                onSettings = onOpenSettings,
                onJournal = { nav.navigate(Routes.JOURNAL) },
                onWorkouts = { nav.navigate("workouts") },
                onRecords = { nav.navigate(Routes.RECORDS) },
            )
        }

        composable(Routes.JOURNAL) {
            JournalScreen(
                onBack = { nav.popBackStack() },
                onOpenItem = { date, key ->
                    nav.navigate("journal/$date/${Uri.encode(key)}")
                },
                onSync = onSync,
                onCreateAi = { date, typeKey -> nav.navigate(interactive(date, typeKey)) },
                onStartLive = { date, typeKey -> nav.navigate(liveWorkout(date, typeKey)) },
            )
        }

        composable(
            route = Routes.JOURNAL_ITEM,
            arguments = listOf(
                navArgument("date") { type = NavType.StringType },
                navArgument("key") { type = NavType.StringType },
                navArgument("focus") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            JournalEntryScreen(
                date = entry.arguments?.getString("date").orEmpty(),
                entryKey = entry.arguments?.getString("key").orEmpty(),
                focus = entry.arguments?.getString("focus").orEmpty(),
                onBack = { nav.popBackStack() },
                onOpenAiSettings = onOpenAiSettings,
                onAddWeight = { d, t -> nav.navigate(weightNew(date = d, typeKey = t)) },
                onAddBenchmark = { d, t -> nav.navigate(benchmarkNew(date = d, typeKey = t)) },
                onStartLive = { d, t -> nav.navigate(liveWorkout(d, t)) },
                onOpenWeightRecord = { key, id -> nav.navigate(weightDetail(key, id, "journal")) },
                onOpenBenchmarkRecord = { key, id -> nav.navigate(benchmarkDetail(key, id, "journal")) },
            )
        }

        composable(
            route = Routes.WORKOUTS,
            arguments = listOf(
                navArgument("focus") { type = NavType.StringType; defaultValue = "" },
                navArgument("view") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            WorkoutsScreen(
                onBack = { nav.popBackStack() },
                onOpenWorkout = { date, typeKey -> nav.navigate(workout(date, typeKey)) },
                onSync = onSync,
                onOpenHistory = { nav.navigate(Routes.HISTORY) },
                initialFocus = entry.arguments?.getString("focus")?.ifBlank { null },
                initialView = entry.arguments?.getString("view")?.ifBlank { null },
            )
        }

        composable(Routes.HISTORY) {
            WorkoutHistoryScreen(
                onBack = { nav.popBackStack() },
                onOpenMonth = { firstDate ->
                    nav.navigate("workouts?focus=$firstDate&view=month")
                },
                onSync = onSync,
            )
        }

        composable(
            route = Routes.WORKOUT,
            arguments = listOf(
                navArgument("date") { type = NavType.StringType },
                navArgument("typeKey") { type = NavType.StringType },
            ),
        ) { entry ->
            val date = entry.arguments?.getString("date").orEmpty()
            val typeKey = entry.arguments?.getString("typeKey").orEmpty()
            WorkoutDetailScreen(
                date = date,
                typeKey = typeKey,
                onBack = { nav.popBackStack() },
                onOpenInteractive = { d, t -> nav.navigate("interactive/$d/$t") },
                onOpenWeightRecord = { key, id -> nav.navigate(weightDetail(key, id, "workout")) },
                onOpenBenchmarkRecord = { key, id -> nav.navigate(benchmarkDetail(key, id, "workout")) },
            )
        }

        composable(
            route = Routes.INTERACTIVE,
            arguments = listOf(
                navArgument("date") { type = NavType.StringType },
                navArgument("typeKey") { type = NavType.StringType },
            ),
        ) { entry ->
            InteractiveWorkoutScreen(
                date = entry.arguments?.getString("date").orEmpty(),
                typeKey = entry.arguments?.getString("typeKey").orEmpty(),
                onBack = { nav.popBackStack() },
                onOpenAiSettings = onOpenAiSettings,
            )
        }

        composable(
            route = Routes.LIVE,
            arguments = listOf(
                navArgument("date") { type = NavType.StringType },
                navArgument("typeKey") { type = NavType.StringType },
                navArgument("join") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            LiveWorkoutScreen(
                date = entry.arguments?.getString("date").orEmpty(),
                typeKey = entry.arguments?.getString("typeKey").orEmpty(),
                onBack = { nav.popBackStack() },
                join = entry.arguments?.getBoolean("join") ?: false,
            )
        }

        composable(Routes.RECORDS) {
            RecordsScreen(
                onBack = { nav.popBackStack() },
                onWeights = { nav.navigate(Routes.WEIGHTS) },
                onBenchmarks = { nav.navigate(Routes.BENCHMARKS) },
                onOpenWeight = { key -> nav.navigate("records/weights/$key") },
                onOpenBenchmark = { key -> nav.navigate("records/benchmarks/$key") },
            )
        }

        composable(Routes.WEIGHTS) {
            WeightsScreen(
                onBack = { nav.popBackStack() },
                onOpenWeight = { key -> nav.navigate("records/weights/$key") },
                onAdd = { nav.navigate(weightNew()) },
            )
        }

        composable(
            route = Routes.WEIGHT,
            arguments = listOf(
                navArgument("exerciseKey") { type = NavType.StringType },
                navArgument("recordId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("from") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val key = entry.arguments?.getString("exerciseKey").orEmpty()
            WeightDetailScreen(
                exerciseKey = key,
                focusRecordId = entry.arguments?.getLong("recordId") ?: 0L,
                origin = entry.arguments?.getString("from").orEmpty(),
                onBack = { nav.popBackStack() },
                onBackToOrigin = { nav.popBackStack() },
                onAdd = { exerciseKey, name ->
                    nav.navigate(weightNew(exerciseKey = exerciseKey, name = name))
                },
                onEdit = { id -> nav.navigate("record/weight/edit/$id") },
                onOpenWorkout = { date, typeKey, recordId ->
                    nav.navigate(journalEntry(date, typeKey, "w:$recordId"))
                },
            )
        }

        composable(Routes.BENCHMARKS) {
            BenchmarksScreen(
                onBack = { nav.popBackStack() },
                onOpenBenchmark = { key -> nav.navigate("records/benchmarks/$key") },
                onAdd = { nav.navigate(benchmarkNew()) },
            )
        }

        composable(
            route = Routes.BENCHMARK,
            arguments = listOf(
                navArgument("benchmarkKey") { type = NavType.StringType },
                navArgument("recordId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("from") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val key = entry.arguments?.getString("benchmarkKey").orEmpty()
            BenchmarkDetailScreen(
                benchmarkKey = key,
                focusRecordId = entry.arguments?.getLong("recordId") ?: 0L,
                origin = entry.arguments?.getString("from").orEmpty(),
                onBack = { nav.popBackStack() },
                onBackToOrigin = { nav.popBackStack() },
                onAdd = { benchmarkKey, name ->
                    nav.navigate(benchmarkNew(benchmarkKey = benchmarkKey, name = name))
                },
                onEdit = { id -> nav.navigate("record/benchmark/edit/$id") },
                onOpenWorkout = { date, typeKey, recordId ->
                    nav.navigate(journalEntry(date, typeKey, "b:$recordId"))
                },
            )
        }

        composable(
            route = Routes.WEIGHT_NEW,
            arguments = listOf(
                navArgument("exerciseKey") { type = NavType.StringType; defaultValue = "" },
                navArgument("exerciseName") { type = NavType.StringType; defaultValue = "" },
                navArgument("date") { type = NavType.StringType; defaultValue = "" },
                navArgument("typeKey") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            WeightEditorScreen(
                recordId = null,
                initialExerciseKey = entry.arguments?.getString("exerciseKey")?.ifBlank { null },
                initialName = entry.arguments?.getString("exerciseName")?.ifBlank { null },
                initialDate = entry.arguments?.getString("date")?.ifBlank { null },
                initialTypeKey = entry.arguments?.getString("typeKey")?.ifBlank { null },
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() },
            )
        }

        composable(
            route = Routes.WEIGHT_EDIT,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            WeightEditorScreen(
                recordId = entry.arguments?.getLong("id"),
                initialExerciseKey = null,
                initialName = null,
                initialDate = null,
                initialTypeKey = null,
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() },
            )
        }

        composable(
            route = Routes.BENCHMARK_NEW,
            arguments = listOf(
                navArgument("benchmarkKey") { type = NavType.StringType; defaultValue = "" },
                navArgument("benchmarkName") { type = NavType.StringType; defaultValue = "" },
                navArgument("date") { type = NavType.StringType; defaultValue = "" },
                navArgument("typeKey") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            BenchmarkEditorScreen(
                recordId = null,
                initialKey = entry.arguments?.getString("benchmarkKey")?.ifBlank { null },
                initialName = entry.arguments?.getString("benchmarkName")?.ifBlank { null },
                initialDate = entry.arguments?.getString("date")?.ifBlank { null },
                initialTypeKey = entry.arguments?.getString("typeKey")?.ifBlank { null },
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() },
            )
        }

        composable(
            route = Routes.BENCHMARK_EDIT,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            BenchmarkEditorScreen(
                recordId = entry.arguments?.getLong("id"),
                initialKey = null,
                initialName = null,
                initialDate = null,
                initialTypeKey = null,
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() },
            )
        }
    }
}
