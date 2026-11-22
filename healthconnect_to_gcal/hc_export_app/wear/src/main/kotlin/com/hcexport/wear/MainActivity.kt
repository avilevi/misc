package com.hcexport.wear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.Wearable
import com.hcexport.shared.PATH_META
import com.hcexport.wear.live.WatchLiveSync
import com.hcexport.wear.ui.WatchApp
import com.hcexport.wear.ui.WatchJoinRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_LIVE_DATE = "live_date"
        const val EXTRA_LIVE_TYPE = "live_type"
    }

    private var refreshing by mutableStateOf(false)
    private var joinRequest by mutableStateOf<WatchJoinRequest?>(null)

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val dataListener = DataClient.OnDataChangedListener { events ->
        val snapshotChanged = events.any {
            it.dataItem.uri.path == PATH_META
        }
        if (snapshotChanged) refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WatchLiveSync.init(this)
        WatchStore.loadCache(this)
        setContent {
            val snapshot by WatchStore.snapshot.collectAsState()
            WatchApp(
                snapshot = snapshot,
                refreshing = refreshing,
                joinRequest = joinRequest,
                onJoinHandled = { joinRequest = null },
                onRefresh = ::refresh,
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        handleLiveIntent(intent)
        refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLiveIntent(intent)
    }

    private fun handleLiveIntent(intent: Intent?) {
        val date = intent?.getStringExtra(EXTRA_LIVE_DATE) ?: return
        val type = intent.getStringExtra(EXTRA_LIVE_TYPE) ?: return
        if (date.length != 10 || type.isBlank()) return
        joinRequest = WatchJoinRequest(date = date, typeKey = type, nonce = System.nanoTime())
        intent.removeExtra(EXTRA_LIVE_DATE)
        intent.removeExtra(EXTRA_LIVE_TYPE)
    }

    private fun refresh() {
        if (refreshing) return
        refreshing = true
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { WatchStore.refresh(this@MainActivity) }
            refreshing = false
        }
    }

    override fun onStart() {
        super.onStart()
        WatchAppVisibility.onStart()
        Wearable.getDataClient(this).addListener(dataListener)
    }

    override fun onResume() {
        super.onResume()
        // A workout started on the phone while this app was closed leaves its
        // record in the Data Layer; pick it up before anything else.
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { WatchLiveSync.loadLatest(this@MainActivity) }
        }
    }

    override fun onStop() {
        Wearable.getDataClient(this).removeListener(dataListener)
        WatchAppVisibility.onStop()
        super.onStop()
    }
}
