package com.hcexport.wear.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.hcexport.shared.DEVICE_PHONE
import com.hcexport.shared.Snapshot
import com.hcexport.wear.live.WatchLiveSync

data class WatchJoinRequest(
    val date: String,
    val typeKey: String,
    val nonce: Long,
)

@Composable
fun WatchApp(
    snapshot: Snapshot?,
    refreshing: Boolean,
    joinRequest: WatchJoinRequest?,
    onJoinHandled: () -> Unit,
    onRefresh: () -> Unit,
) {
    val nav = rememberSwipeDismissableNavController()

    // A workout started on the phone while the watch is on screen: join it.
    val remote by WatchLiveSync.remote.collectAsState()
    val backStack by nav.currentBackStackEntryAsState()
    var autoJoined by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(joinRequest?.nonce) {
        val request = joinRequest ?: return@LaunchedEffect
        val current = remote
        if (current != null && current.date == request.date &&
            current.typeKey == request.typeKey && WatchLiveSync.isClosed(current)
        ) {
            onJoinHandled()
            return@LaunchedEffect
        }
        // The same record may also arrive through the flow; join once.
        autoJoined = current?.sessionId
        nav.navigate("live/${request.date}/${request.typeKey}?join=true")
        onJoinHandled()
    }
    LaunchedEffect(remote?.sessionId, remote?.updatedAt, remote?.open) {
        val session = remote ?: return@LaunchedEffect
        if (!session.open || session.origin != DEVICE_PHONE) return@LaunchedEffect
        if (WatchLiveSync.isClosed(session)) return@LaunchedEffect
        if (WatchLiveSync.localActive) return@LaunchedEffect
        if (session.sessionId == autoJoined) return@LaunchedEffect
        if (System.currentTimeMillis() - session.updatedAt > 10 * 60_000L) {
            return@LaunchedEffect
        }
        if (backStack?.destination?.route.orEmpty().startsWith("live/")) {
            return@LaunchedEffect
        }
        autoJoined = session.sessionId
        nav.navigate("live/${session.date}/${session.typeKey}?join=true")
    }

    Box(Modifier.fillMaxSize().background(Obsidian)) {
        SwipeDismissableNavHost(navController = nav, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    snapshot = snapshot,
                    refreshing = refreshing,
                    onJournal = { nav.navigate("journal") },
                    onEntry = { date, key -> nav.navigate("entry/$date/${Uri.encode(key)}") },
                    onStartLive = { date, type -> nav.navigate("live/$date/$type") },
                    onRefresh = onRefresh,
                )
            }

            composable("journal") {
                JournalListScreen(
                    snapshot = snapshot,
                    onEntry = { date, key -> nav.navigate("entry/$date/${Uri.encode(key)}") },
                    onStartLive = { date, type -> nav.navigate("live/$date/$type") },
                )
            }

            composable(
                route = "entry/{date}/{key}",
                arguments = listOf(
                    navArgument("date") { type = NavType.StringType },
                    navArgument("key") { type = NavType.StringType },
                ),
            ) { entry ->
                EntryScreen(
                    snapshot = snapshot,
                    date = entry.arguments?.getString("date").orEmpty(),
                    entryKey = entry.arguments?.getString("key").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onDetails = { date, key ->
                        nav.navigate("details/$date/${Uri.encode(key)}")
                    },
                    onStartLive = { date, type -> nav.navigate("live/$date/$type") },
                )
            }

            composable(
                route = "details/{date}/{key}",
                arguments = listOf(
                    navArgument("date") { type = NavType.StringType },
                    navArgument("key") { type = NavType.StringType },
                ),
            ) { entry ->
                EntryDetailsScreen(
                    snapshot = snapshot,
                    date = entry.arguments?.getString("date").orEmpty(),
                    entryKey = entry.arguments?.getString("key").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onStartLive = { date, type -> nav.navigate("live/$date/$type") },
                )
            }

            composable(
                route = "live/{date}/{typeKey}?join={join}",
                arguments = listOf(
                    navArgument("date") { type = NavType.StringType },
                    navArgument("typeKey") { type = NavType.StringType },
                    navArgument("join") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                LiveScreen(
                    snapshot = snapshot,
                    date = entry.arguments?.getString("date").orEmpty(),
                    typeKey = entry.arguments?.getString("typeKey").orEmpty(),
                    join = entry.arguments?.getBoolean("join") ?: false,
                    onExit = { nav.popBackStack() },
                    onRefresh = onRefresh,
                )
            }
        }
    }
}
