package com.hcexport.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hcexport.ui.records.BenchmarkDetailScreen
import com.hcexport.ui.records.BenchmarkEditorScreen
import com.hcexport.ui.records.BenchmarksScreen
import com.hcexport.ui.records.RecordsScreen
import com.hcexport.ui.records.WeightDetailScreen
import com.hcexport.ui.records.WeightEditorScreen
import com.hcexport.ui.records.WeightsScreen
import com.hcexport.ui.journal.JournalEntryScreen
import com.hcexport.ui.journal.JournalScreen
import com.hcexport.ui.workouts.InteractiveWorkoutScreen
import com.hcexport.ui.workouts.WorkoutDetailScreen
import com.hcexport.ui.workouts.WorkoutHistoryScreen
import com.hcexport.ui.workouts.WorkoutsScreen

private object Routes {
    const val HOME = "home"
    const val JOURNAL = "journal"
    const val JOURNAL_ITEM = "journal/{date}/{key}"
    const val WORKOUTS = "workouts?focus={focus}&view={view}"
    const val HISTORY = "history"
    const val WORKOUT = "workout/{date}/{typeKey}"
    const val INTERACTIVE = "interactive/{date}/{typeKey}"
    const val RECORDS = "records"
    const val WEIGHTS = "records/weights"
    const val WEIGHT = "records/weights/{exerciseKey}"
    const val BENCHMARKS = "records/benchmarks"
    const val BENCHMARK = "records/benchmarks/{benchmarkKey}"
    const val WEIGHT_NEW = "record/weight/new?exerciseKey={exerciseKey}&exerciseName={exerciseName}&date={date}&typeKey={typeKey}"
    const val WEIGHT_EDIT = "record/weight/edit/{id}"
    const val BENCHMARK_NEW = "record/benchmark/new?benchmarkKey={benchmarkKey}&benchmarkName={benchmarkName}&date={date}&typeKey={typeKey}"
    const val BENCHMARK_EDIT = "record/benchmark/edit/{id}"
}

private fun workout(date: String, typeKey: String) = "workout/$date/$typeKey"
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
) {
    val nav = rememberNavController()

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
            )
        }

        composable(
            route = Routes.JOURNAL_ITEM,
            arguments = listOf(
                navArgument("date") { type = NavType.StringType },
                navArgument("key") { type = NavType.StringType },
            ),
        ) { entry ->
            JournalEntryScreen(
                date = entry.arguments?.getString("date").orEmpty(),
                entryKey = entry.arguments?.getString("key").orEmpty(),
                onBack = { nav.popBackStack() },
                onOpenInteractive = { date, typeKey ->
                    nav.navigate("interactive/$date/$typeKey")
                },
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
                onAddWeight = { d, t -> nav.navigate(weightNew(date = d, typeKey = t)) },
                onAddBenchmark = { d, t -> nav.navigate(benchmarkNew(date = d, typeKey = t)) },
                onOpenInteractive = { d, t -> nav.navigate("interactive/$d/$t") },
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
            arguments = listOf(navArgument("exerciseKey") { type = NavType.StringType }),
        ) { entry ->
            val key = entry.arguments?.getString("exerciseKey").orEmpty()
            WeightDetailScreen(
                exerciseKey = key,
                onBack = { nav.popBackStack() },
                onAdd = { exerciseKey, name ->
                    nav.navigate(weightNew(exerciseKey = exerciseKey, name = name))
                },
                onEdit = { id -> nav.navigate("record/weight/edit/$id") },
                onOpenWorkout = { date, typeKey -> nav.navigate(workout(date, typeKey)) },
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
            arguments = listOf(navArgument("benchmarkKey") { type = NavType.StringType }),
        ) { entry ->
            val key = entry.arguments?.getString("benchmarkKey").orEmpty()
            BenchmarkDetailScreen(
                benchmarkKey = key,
                onBack = { nav.popBackStack() },
                onAdd = { benchmarkKey, name ->
                    nav.navigate(benchmarkNew(benchmarkKey = benchmarkKey, name = name))
                },
                onEdit = { id -> nav.navigate("record/benchmark/edit/$id") },
                onOpenWorkout = { date, typeKey -> nav.navigate(workout(date, typeKey)) },
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
