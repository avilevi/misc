package com.hcexport

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private var hcPermissionsLaunched = false

    private val hcPermissions = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
    )

    private val calendarPermissions = arrayOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    )

    private val requestHcPermissions = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        hcPermissionsLaunched = false
        if (granted.containsAll(hcPermissions))
            checkAndSetup()
        else
            status("⚠ Some Health Connect permissions denied.\n\nOpen the Health Connect app → App permissions → HC Sync, and allow all permissions. Then tap Refresh.")
    }

    private val requestCalendarPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it })
            checkAndSetup()
        else
            status("⚠ Calendar permission denied. HC Sync needs it to create events.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 80, 64, 64)
        }

        TextView(this).apply {
            text = "HC Sync"
            textSize = 24f
            setPadding(0, 0, 0, 12)
        }.also { root.addView(it) }

        statusText = TextView(this).apply {
            text = "Checking…"
            textSize = 14f
            setPadding(0, 0, 0, 32)
        }.also { root.addView(it) }

        Button(this).apply {
            text = "Sync Now"
            setOnClickListener { triggerSync() }
        }.also { root.addView(it) }

        Button(this).apply {
            text = "Refresh / Re-check Permissions"
            setOnClickListener {
                hcPermissionsLaunched = false
                checkAndSetup()
            }
        }.also { root.addView(it) }

        setContentView(ScrollView(this).also { it.addView(root) })
        checkAndSetup()
    }

    override fun onResume() {
        super.onResume()
        if (!hcPermissionsLaunched) checkAndSetup()
    }

    private fun checkAndSetup() {
        // 1. Calendar permission
        if (calendarPermissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            status("Step 1 of 2: Requesting calendar permission…")
            requestCalendarPermissions.launch(calendarPermissions)
            return
        }

        // 2. Health Connect availability
        if (HealthConnectClient.getSdkStatus(this) != HealthConnectClient.SDK_AVAILABLE) {
            status("⚠ Health Connect is not available on this device.\nRequires Android 9+ with Health Connect installed.")
            return
        }

        // 3. Health Connect permissions
        lifecycleScope.launch {
            try {
                val client  = HealthConnectClient.getOrCreate(this@MainActivity)
                val granted = client.permissionController.getGrantedPermissions()
                if (!granted.containsAll(hcPermissions)) {
                    if (!hcPermissionsLaunched) {
                        hcPermissionsLaunched = true
                        status("Step 2 of 2: Requesting Health Connect permissions…")
                        try {
                            requestHcPermissions.launch(hcPermissions)
                        } catch (e: Exception) {
                            hcPermissionsLaunched = false
                            status("⚠ Could not open Health Connect permissions.\n\nOpen the Health Connect app → App permissions → HC Sync → allow all. Then tap Refresh.")
                        }
                    }
                    return@launch
                }
            } catch (e: Exception) {
                status("⚠ Health Connect error: ${e.message}\n\nMake sure Health Connect is installed and up to date, then tap Refresh.")
                return@launch
            }

            // 4. All good — show which calendar will be used
            val calId   = CalendarHelper.findCalendarId(this@MainActivity)
            val calName = calId?.let { resolveCalendarName(it) } ?: "none found"
            if (calId == null) {
                status("⚠ No Google calendar found on this device.\nMake sure a Google account is set up.")
            } else {
                status("✓ Ready\n\nCalendar: $calName\nSyncs every 6 hours automatically.\nLast run: check Android logcat (tag HcSyncWorker)")
                schedulePeriodicSync()
            }
        }
    }

    private fun triggerSync() {
        status("Syncing…")
        val req = OneTimeWorkRequestBuilder<HcSyncWorker>().build()
        WorkManager.getInstance(this).enqueue(req)
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(req.id).observe(this) { info ->
            when (info?.state) {
                WorkInfo.State.SUCCEEDED -> {
                    lifecycleScope.launch {
                        val calId   = CalendarHelper.findCalendarId(this@MainActivity)
                        val calName = calId?.let { resolveCalendarName(it) } ?: "?"
                        status("✓ Sync complete.\nEvents written to: $calName")
                    }
                }
                WorkInfo.State.FAILED -> status("✗ Sync failed. Check Android logcat (tag HcSyncWorker) for details.")
                else -> {}
            }
        }
    }

    private fun schedulePeriodicSync() {
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "hc_periodic_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<HcSyncWorker>(6, TimeUnit.HOURS).build(),
        )
    }

    private fun resolveCalendarName(calendarId: Long): String {
        val projection = arrayOf(
            android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            android.provider.CalendarContract.Calendars.ACCOUNT_NAME,
        )
        val selection = "${android.provider.CalendarContract.Calendars._ID} = ?"
        contentResolver.query(
            android.provider.CalendarContract.Calendars.CONTENT_URI,
            projection, selection, arrayOf(calendarId.toString()), null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name    = cursor.getString(0) ?: ""
                val account = cursor.getString(1) ?: ""
                return "$name ($account)"
            }
        }
        return calendarId.toString()
    }

    private fun status(msg: String) { statusText.text = msg }
}
