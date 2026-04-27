package com.hcexport

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 80, 64, 64)
        }

        TextView(this).apply {
            text = "Settings"
            textSize = 22f
            setPadding(0, 0, 0, 32)
        }.also { root.addView(it) }

        // ── Calendar picker ────────────────────────────────────────────────

        sectionLabel("Calendar", root)

        val calendars = loadCalendars()
        if (calendars.isEmpty()) {
            TextView(this).apply {
                text = "No Google calendars found on this device."
                textSize = 14f
            }.also { root.addView(it) }
        } else {
            val spinner = Spinner(this).apply {
                adapter = ArrayAdapter(
                    this@SettingsActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    calendars.map { it.second },
                )
            }
            val savedId = Prefs.getCalendarId(this)
            val idx = calendars.indexOfFirst { it.first == savedId }.takeIf { it >= 0 } ?: 0
            spinner.setSelection(idx)
            if (savedId == null && calendars.isNotEmpty()) Prefs.setCalendarId(this, calendars[0].first)

            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                    Prefs.setCalendarId(this@SettingsActivity, calendars[pos].first)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            root.addView(spinner)
        }

        root.addView(spacer(32))

        // ── Sleep sources ──────────────────────────────────────────────────

        sectionLabel("Sleep — source priority", root)
        sourceSubLabel(Prefs.getSleepSourcePriority(this), root)

        Button(this).apply {
            text = "Reorder sleep sources…"
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, ReorderActivity::class.java).apply {
                    putExtra(ReorderActivity.EXTRA_TYPE, ReorderActivity.TYPE_SLEEP)
                })
            }
        }.also { root.addView(it) }

        root.addView(spacer(32))

        // ── Exercise sources ───────────────────────────────────────────────

        sectionLabel("Exercise — source priority", root)
        sourceSubLabel(Prefs.getExerciseSourcePriority(this), root)

        Button(this).apply {
            text = "Reorder exercise sources…"
            setOnClickListener {
                startActivity(Intent(this@SettingsActivity, ReorderActivity::class.java).apply {
                    putExtra(ReorderActivity.EXTRA_TYPE, ReorderActivity.TYPE_EXERCISE)
                })
            }
        }.also { root.addView(it) }

        setContentView(ScrollView(this).also { it.addView(root) })
    }

    override fun onResume() {
        super.onResume()
        // Recreate to refresh source labels after returning from ReorderActivity
        recreate()
    }

    private fun sectionLabel(text: String, parent: LinearLayout) {
        TextView(this).apply {
            this.text = text
            textSize = 16f
            setPadding(0, 0, 0, 8)
        }.also { parent.addView(it) }
    }

    private fun sourceSubLabel(sources: List<String>, parent: LinearLayout) {
        val text = if (sources.isEmpty()) "Run a sync first to discover sources."
                   else sources.mapIndexed { i, pkg -> "${i + 1}. ${friendlySource(pkg)}" }.joinToString("\n")
        TextView(this).apply {
            this.text = text
            textSize = 13f
            setPadding(0, 0, 0, 8)
        }.also { parent.addView(it) }
    }

    private fun spacer(dp: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp)
    }

    private fun loadCalendars(): List<Pair<Long, String>> {
        val projection = arrayOf(
            android.provider.CalendarContract.Calendars._ID,
            android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            android.provider.CalendarContract.Calendars.ACCOUNT_NAME,
            android.provider.CalendarContract.Calendars.ACCOUNT_TYPE,
        )
        val result = mutableListOf<Pair<Long, String>>()
        contentResolver.query(
            android.provider.CalendarContract.Calendars.CONTENT_URI,
            projection, null, null, null,
        )?.use { cursor ->
            val idCol   = cursor.getColumnIndex(android.provider.CalendarContract.Calendars._ID)
            val nameCol = cursor.getColumnIndex(android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val acctCol = cursor.getColumnIndex(android.provider.CalendarContract.Calendars.ACCOUNT_NAME)
            val typeCol = cursor.getColumnIndex(android.provider.CalendarContract.Calendars.ACCOUNT_TYPE)
            while (cursor.moveToNext()) {
                val type = cursor.getString(typeCol) ?: ""
                if (type != "com.google") continue
                val id   = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: ""
                val acct = cursor.getString(acctCol) ?: ""
                result.add(id to "$name ($acct)")
            }
        }
        return result
    }

    private fun friendlySource(pkg: String): String = when {
        pkg.contains("samsung") -> "Samsung Health"
        pkg.contains("fitbit")  -> "Fitbit"
        pkg.contains("garmin")  -> "Garmin Connect"
        pkg.contains("google.android.apps.fitness") -> "Google Fit"
        pkg.contains("huawei")  -> "Huawei Health"
        pkg.contains("polar")   -> "Polar Flow"
        pkg.contains("strava")  -> "Strava"
        pkg.contains("withings")-> "Withings"
        pkg.contains("whoop")   -> "WHOOP"
        else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}
