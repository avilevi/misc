package com.hcexport

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity

class SettingsActivity : ComponentActivity() {

    private lateinit var sleepLabel: TextView
    private lateinit var exerciseLabel: TextView
    private lateinit var schedulesList: LinearLayout

    private var calendars = mutableListOf<Triple<Long, String, String>>()
    private lateinit var calSpinner: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(Ui.dp(this@SettingsActivity, 20), Ui.dp(this@SettingsActivity, 60), Ui.dp(this@SettingsActivity, 20), Ui.dp(this@SettingsActivity, 24))
        }

        // ── Header ───────────────────────────────────────────────────────────

        headerBar(root)

        // ── Calendar card ────────────────────────────────────────────────────

        Ui.sectionHeader(this, "Calendar").also { root.addView(it) }

        calSpinner = Spinner(this).apply {
            setPadding(Ui.dp(this@SettingsActivity, 12), Ui.dp(this@SettingsActivity, 12), Ui.dp(this@SettingsActivity, 12), Ui.dp(this@SettingsActivity, 12))
            background = Ui.cardBg(Ui.dpf(this@SettingsActivity, 12))
        }
        root.addView(calSpinner)
        root.addView(Ui.sectionSpacer(this, 8))

        val calBtnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        calBtnRow.addView(Ui.secondaryButton(this, "Refresh list") { refreshCalendarSpinner() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                it.setMargins(0, 0, Ui.dp(this@SettingsActivity, 6), 0)
            }
        })
        calBtnRow.addView(Ui.secondaryButton(this, "Create new…") { showCreateCalendarDialog() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                it.setMargins(Ui.dp(this@SettingsActivity, 6), 0, 0, 0)
            }
        })
        root.addView(calBtnRow)

        root.addView(Ui.sectionSpacer(this, 28))

        // ── Sync range card ──────────────────────────────────────────────────

        Ui.sectionHeader(this, "Sync range (days back)").also { root.addView(it) }

        val rangeDays   = listOf(7L, 14L, 30L, 60L, 90L, 180L, 365L)
        val rangeLabels = listOf("7 days", "14 days", "30 days", "60 days", "90 days", "180 days", "1 year")
        val rangeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SettingsActivity,
                android.R.layout.simple_spinner_item,
                rangeLabels,
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setPadding(Ui.dp(this@SettingsActivity, 12), Ui.dp(this@SettingsActivity, 12), Ui.dp(this@SettingsActivity, 12), Ui.dp(this@SettingsActivity, 12))
            background = Ui.cardBg(Ui.dpf(this@SettingsActivity, 12))
        }
        val savedDays = Prefs.getSyncDaysBack(this)
        rangeSpinner.setSelection(rangeDays.indexOf(savedDays).takeIf { it >= 0 } ?: 4)
        rangeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                Prefs.setSyncDaysBack(this@SettingsActivity, rangeDays[pos])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        root.addView(rangeSpinner)

        root.addView(Ui.sectionSpacer(this, 28))

        // ── Auto-sync card ───────────────────────────────────────────────────

        Ui.sectionHeader(this, "Auto-sync schedules").also { root.addView(it) }

        schedulesList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(schedulesList)

        root.addView(Ui.sectionSpacer(this, 8))
        root.addView(Ui.secondaryButton(this, "+ Add schedule") { showAddScheduleDialog() })

        root.addView(Ui.sectionSpacer(this, 28))

        // ── Sleep sources card ───────────────────────────────────────────────

        Ui.sectionHeader(this, "Sleep — source priority").also { root.addView(it) }

        sleepLabel = TextView(this).apply {
            textSize = 13f
            setTextColor(Ui.TEXT_SECONDARY)
            setPadding(0, 0, 0, Ui.dp(this@SettingsActivity, 8))
        }
        root.addView(sleepLabel)

        root.addView(Ui.secondaryButton(this, "Reorder sleep sources…") {
            startActivity(Intent(this, ReorderActivity::class.java).apply {
                putExtra(ReorderActivity.EXTRA_TYPE, ReorderActivity.TYPE_SLEEP)
            })
        })

        root.addView(Ui.sectionSpacer(this, 28))

        // ── Exercise sources card ────────────────────────────────────────────

        Ui.sectionHeader(this, "Exercise — source priority").also { root.addView(it) }

        exerciseLabel = TextView(this).apply {
            textSize = 13f
            setTextColor(Ui.TEXT_SECONDARY)
            setPadding(0, 0, 0, Ui.dp(this@SettingsActivity, 8))
        }
        root.addView(exerciseLabel)

        root.addView(Ui.secondaryButton(this, "Reorder exercise sources…") {
            startActivity(Intent(this, ReorderActivity::class.java).apply {
                putExtra(ReorderActivity.EXTRA_TYPE, ReorderActivity.TYPE_EXERCISE)
            })
        })

        setContentView(ScrollView(this).apply {
            addView(root)
            setBackgroundColor(Ui.BG)
        })
    }

    override fun onResume() {
        super.onResume()
        refreshCalendarSpinner()
        refreshSourceLabels()
        refreshSchedulesList()
    }

    // ── Header ───────────────────────────────────────────────────────────────

    private fun headerBar(parent: LinearLayout) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, Ui.dp(this@SettingsActivity, 28))
        }
        TextView(this).apply {
            text = "Settings"
            textSize = 24f
            setTextColor(Ui.TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
        }.also { row.addView(it) }
        parent.addView(row)
    }

    // ── Schedule helpers ─────────────────────────────────────────────────────

    private fun refreshSchedulesList() {
        schedulesList.removeAllViews()
        val schedules = Prefs.getSyncSchedules(this)
        if (schedules.isEmpty()) {
            TextView(this).apply {
                text = "No schedules yet."
                textSize = 13f
                setTextColor(Ui.TEXT_MUTED)
                setPadding(0, Ui.dp(this@SettingsActivity, 4), 0, 0)
            }.also { schedulesList.addView(it) }
            return
        }
        for (schedule in schedules) {
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, Ui.dp(this@SettingsActivity, 6), 0, Ui.dp(this@SettingsActivity, 6))
                TextView(this@SettingsActivity).apply {
                    text = schedule.displayString()
                    textSize = 14f
                    setTextColor(Ui.TEXT_PRIMARY)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }.also { addView(it) }
                Button(this@SettingsActivity).apply {
                    text = "✕"
                    textSize = 13f
                    setTextColor(Ui.ERROR)
                    background = Ui.cardBg(Ui.dpf(this@SettingsActivity, 8), fillColor = Ui.SURFACE_ELEVATED, borderColor = Ui.BORDER_FAINT)
                    setPadding(Ui.dp(this@SettingsActivity, 12), Ui.dp(this@SettingsActivity, 6), Ui.dp(this@SettingsActivity, 12), Ui.dp(this@SettingsActivity, 6))
                    setOnClickListener {
                        val updated = Prefs.getSyncSchedules(this@SettingsActivity)
                            .filter { it.id != schedule.id }
                        Prefs.setSyncSchedules(this@SettingsActivity, updated)
                        SyncScheduler.applySchedules(this@SettingsActivity)
                        refreshSchedulesList()
                    }
                }.also { addView(it) }
            }.also { schedulesList.addView(it) }
        }
    }

    private fun showAddScheduleDialog() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val radioDaily  = RadioButton(this).apply { text = "Daily";  isChecked = true }
        val radioWeekly = RadioButton(this).apply { text = "Weekly" }
        RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            addView(radioDaily)
            addView(radioWeekly)
        }.also { dialogView.addView(it) }

        val hourPicker = NumberPicker(this).apply {
            minValue = 0; maxValue = 23
            setFormatter { "%02d".format(it) }
            value = 8
        }
        val minutePicker = NumberPicker(this).apply {
            minValue = 0; maxValue = 59
            setFormatter { "%02d".format(it) }
            value = 0
        }
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 16, 0, 8)
            TextView(this@SettingsActivity).apply { text = "Time:  " }.also { addView(it) }
            addView(hourPicker)
            TextView(this@SettingsActivity).apply { text = "  :  " }.also { addView(it) }
            addView(minutePicker)
        }.also { dialogView.addView(it) }

        val dayNames  = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val dayChecks = dayNames.mapIndexed { i, name ->
            CheckBox(this).apply { text = name; tag = i + 1 }
        }
        val daysRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
            visibility = View.GONE
            dayChecks.forEach { addView(it) }
        }
        dialogView.addView(TextView(this).apply {
            text = "Days:"
            textSize = 14f
            setPadding(0, 8, 0, 0)
            visibility = View.GONE
            tag = "days_label"
        })
        dialogView.addView(daysRow)

        val daysLabel = dialogView.findViewWithTag<TextView>("days_label")

        radioDaily.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                daysLabel.visibility = View.GONE
                daysRow.visibility = View.GONE
            }
        }
        radioWeekly.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                daysLabel.visibility = View.VISIBLE
                daysRow.visibility = View.VISIBLE
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Add sync schedule")
            .setView(dialogView)
            .setPositiveButton("Add", null)
            .setNegativeButton("Cancel", null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val type = if (radioDaily.isChecked) "daily" else "weekly"
            val selectedDays = dayChecks.filter { it.isChecked }.map { it.tag as Int }.toSet()

            if (type == "weekly" && selectedDays.isEmpty()) {
                Toast.makeText(this, "Select at least one day", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val schedule = SyncSchedule(
                type   = type,
                hour   = hourPicker.value,
                minute = minutePicker.value,
                days   = selectedDays,
            )
            Prefs.setSyncSchedules(this, Prefs.getSyncSchedules(this) + schedule)
            SyncScheduler.enqueue(this, schedule)
            refreshSchedulesList()
            dialog.dismiss()
        }
    }

    // ── Calendar helpers ─────────────────────────────────────────────────────

    private fun refreshCalendarSpinner() {
        calendars.clear()
        calendars.addAll(loadCalendars())

        calSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            calendars.map { it.second },
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        calSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (pos < calendars.size) Prefs.setCalendarId(this@SettingsActivity, calendars[pos].first)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val savedId = Prefs.getCalendarId(this)
        val idx = calendars.indexOfFirst { it.first == savedId }.takeIf { it >= 0 } ?: 0
        if (calendars.isNotEmpty()) {
            calSpinner.setSelection(idx)
            if (savedId == null) Prefs.setCalendarId(this, calendars[0].first)
        }
    }

    private fun showCreateCalendarDialog() {
        val input = EditText(this).apply {
            hint = "Calendar name"
            setText("Health Connect")
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("Create new calendar")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim().ifBlank { "Health Connect" }
                val selected = calendars.getOrNull(calSpinner.selectedItemPosition)
                val (accountName, accountType) = if (selected != null) {
                    val parts = selected.third.split("|")
                    (parts.getOrNull(0) ?: "") to (parts.getOrNull(1) ?: "com.google")
                } else "" to "com.google"

                val newId = CalendarHelper.createCalendar(this, name, accountName, accountType)
                if (newId != null) {
                    Prefs.setCalendarId(this, newId)
                    refreshCalendarSpinner()
                    Toast.makeText(this, "Calendar \"$name\" created", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to create calendar", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadCalendars(): List<Triple<Long, String, String>> {
        val projection = arrayOf(
            android.provider.CalendarContract.Calendars._ID,
            android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            android.provider.CalendarContract.Calendars.ACCOUNT_NAME,
            android.provider.CalendarContract.Calendars.ACCOUNT_TYPE,
        )
        val result = mutableListOf<Triple<Long, String, String>>()
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
                val id      = cursor.getLong(idCol)
                val calName = cursor.getString(nameCol) ?: ""
                val acct    = cursor.getString(acctCol) ?: ""
                result.add(Triple(id, "$calName ($acct)", "$acct|$type"))
            }
        }
        return result
    }

    // ── Source helpers ───────────────────────────────────────────────────────

    private fun refreshSourceLabels() {
        sleepLabel.text    = sourceSummary(Prefs.getSleepSourcePriority(this))
        exerciseLabel.text = sourceSummary(Prefs.getExerciseSourcePriority(this))
    }

    private fun sourceSummary(sources: List<String>): String =
        if (sources.isEmpty()) "Run a sync first to discover sources."
        else sources.mapIndexed { i, pkg -> "${i + 1}. ${friendlySource(pkg)}" }.joinToString("\n")

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
