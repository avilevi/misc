package com.hcexport

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class JournalActivity : ComponentActivity() {

    private var currentYear: Int = 0
    private var currentMonth: Int = 0 // 0-based
    private var selectedDayMs: Long = 0L
    private val expandedCardIds = mutableSetOf<String>()

    private lateinit var calendarContainer: LinearLayout
    private lateinit var monthLabel: TextView
    private lateinit var dayHeaderText: TextView
    private lateinit var dayEntriesContainer: LinearLayout

    private val dayFmt  = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cal = Calendar.getInstance()
        currentYear  = cal.get(Calendar.YEAR)
        currentMonth = cal.get(Calendar.MONTH)
        selectedDayMs = dayStartMs(cal)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(Ui.dp(this@JournalActivity, 20), Ui.dp(this@JournalActivity, 48), Ui.dp(this@JournalActivity, 20), Ui.dp(this@JournalActivity, 24))
        }

        // ── Header ───────────────────────────────────────────────────────────

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, Ui.dp(this@JournalActivity, 20))
        }
        TextView(this).apply {
            text = "Journal"
            textSize = 24f
            setTextColor(Ui.TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
        }.also { header.addView(it) }
        header.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        })
        header.addView(Ui.secondaryButton(this, "Back") { finish() })
        root.addView(header)

        // ── Calendar card ────────────────────────────────────────────────────

        root.addView(Ui.card(this) {
            // Month nav
            val nav = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, Ui.dp(context, 8))
            }
            nav.addView(navButton("<") { shiftMonth(-1) })
            monthLabel = TextView(context).apply {
                textSize = 16f
                setTextColor(Ui.TEXT_PRIMARY)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            nav.addView(monthLabel)
            nav.addView(navButton(">") { shiftMonth(1) })
            addView(nav)

            addView(Ui.divider(context))

            // Day-of-week headers
            val dowRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 4))
            }
            val dowLabels = listOf("M", "T", "W", "T", "F", "S", "S")
            for (d in dowLabels) {
                dowRow.addView(TextView(context).apply {
                    text = d
                    textSize = 11f
                    setTextColor(Ui.TEXT_MUTED)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
            }
            addView(dowRow)

            // Grid container
            calendarContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            addView(calendarContainer)

            addView(Ui.sectionSpacer(context, 8))
            addView(Ui.secondaryButton(context, "Today") {
                val c = Calendar.getInstance()
                currentYear  = c.get(Calendar.YEAR)
                currentMonth = c.get(Calendar.MONTH)
                selectedDayMs = dayStartMs(c)
                refreshCalendar()
                refreshDayEntries()
            })
        })

        root.addView(Ui.sectionSpacer(this, 20))

        // ── Selected day section ─────────────────────────────────────────────

        dayHeaderText = TextView(this).apply {
            textSize = 15f
            setTextColor(Ui.TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, Ui.dp(this@JournalActivity, 8))
        }
        root.addView(dayHeaderText)

        dayEntriesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(dayEntriesContainer)

        setContentView(ScrollView(this).apply {
            addView(root)
            setBackgroundColor(Ui.BG)
        })

        refreshCalendar()
        refreshDayEntries()
    }

    override fun onResume() {
        super.onResume()
        refreshCalendar()
        refreshDayEntries()
    }

    // ── Calendar grid ────────────────────────────────────────────────────────

    private fun refreshCalendar() {
        monthLabel.text = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            .format(Date(dayStartMs(currentYear, currentMonth, 1)))

        calendarContainer.removeAllViews()

        val cal = Calendar.getInstance()
        cal.set(currentYear, currentMonth, 1)
        val daysInMonth   = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDayOfWeekZeroMonday = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // 0=Mon

        val todayCal = Calendar.getInstance()
        val todayY = todayCal.get(Calendar.YEAR)
        val todayM = todayCal.get(Calendar.MONTH)
        val todayD = todayCal.get(Calendar.DAY_OF_MONTH)

        val selCal = Calendar.getInstance().apply { timeInMillis = selectedDayMs }
        val selDay = if (selCal.get(Calendar.YEAR) == currentYear && selCal.get(Calendar.MONTH) == currentMonth)
            selCal.get(Calendar.DAY_OF_MONTH) else null

        val entries = JournalStorage.getEntriesForMonth(this, currentYear, currentMonth)
        val dayMap  = JournalStorage.buildDayMap(entries)

        var cell = 0
        val totalCells = firstDayOfWeekZeroMonday + daysInMonth
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, Ui.dp(this@JournalActivity, 2), 0, Ui.dp(this@JournalActivity, 2))
            }
            for (col in 0 until 7) {
                val day = cell - firstDayOfWeekZeroMonday + 1
                val inMonth = day in 1..daysInMonth

                val cellView = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    setPadding(Ui.dp(this@JournalActivity, 4), Ui.dp(this@JournalActivity, 6), Ui.dp(this@JournalActivity, 4), Ui.dp(this@JournalActivity, 6))

                    val bgColor = when {
                        inMonth && day == selDay -> Ui.SURFACE_ELEVATED
                        inMonth && currentYear == todayY && currentMonth == todayM && day == todayD -> Ui.SURFACE
                        else -> android.graphics.Color.TRANSPARENT
                    }
                    background = if (bgColor != android.graphics.Color.TRANSPARENT)
                        Ui.cardBg(Ui.dpf(this@JournalActivity, 8), fillColor = bgColor, borderColor = android.graphics.Color.TRANSPARENT)
                    else null

                    isClickable = inMonth
                    if (inMonth) {
                        setOnClickListener {
                            selectedDayMs = dayStartMs(currentYear, currentMonth, day)
                            refreshCalendar()
                            refreshDayEntries()
                        }
                    }
                }

                if (inMonth) {
                    cellView.addView(TextView(this).apply {
                        text = day.toString()
                        textSize = 14f
                        setTextColor(if (day == selDay) Ui.PRIMARY else Ui.TEXT_PRIMARY)
                        gravity = Gravity.CENTER
                    })
                    val hasEntries = dayMap.containsKey(day)
                    val hasCustom  = hasEntries && dayMap[day]!!.any { it.hasCustomizations() }
                    val dotColor = when {
                        hasCustom -> Ui.ACCENT
                        hasEntries -> Ui.PRIMARY
                        else -> android.graphics.Color.TRANSPARENT
                    }
                    if (hasEntries) {
                        val dotSize = Ui.dp(this, 5)
                        cellView.addView(View(this).apply {
                            layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).also {
                                it.setMargins(0, Ui.dp(this@JournalActivity, 2), 0, 0)
                            }
                            background = Ui.dotBg(dotColor, dotSize)
                        })
                    }
                }
                rowLayout.addView(cellView)
                cell++
            }
            calendarContainer.addView(rowLayout)
        }
    }

    // ── Day entries ──────────────────────────────────────────────────────────

    private fun refreshDayEntries() {
        dayHeaderText.text = dayFmt.format(Date(selectedDayMs))
        dayEntriesContainer.removeAllViews()

        val entries = JournalStorage.getEntriesForDay(this, selectedDayMs)

        if (entries.isEmpty()) {
            dayEntriesContainer.addView(TextView(this).apply {
                text = "No workouts on this day."
                textSize = 13f
                setTextColor(Ui.TEXT_MUTED)
                setPadding(0, Ui.dp(this@JournalActivity, 8), 0, 0)
            })
            return
        }

        for (entry in entries) {
            dayEntriesContainer.addView(buildWorkoutCard(entry))
            dayEntriesContainer.addView(Ui.sectionSpacer(this, 8))
        }
    }

    // ── Workout card ─────────────────────────────────────────────────────────

    private fun buildWorkoutCard(entry: JournalEntry): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@JournalActivity, 16), Ui.dp(this@JournalActivity, 16), Ui.dp(this@JournalActivity, 16), Ui.dp(this@JournalActivity, 16))
            background = Ui.cardBg(Ui.dpf(this@JournalActivity, 14))
            tag = entry.id
        }

        val isExpanded = expandedCardIds.contains(entry.id)

        // ── Header row (always visible, clickable) ──
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener {
                toggleExpanded(entry.id, card)
            }
        }

        val (emoji, _) = CalendarHelper.EXERCISE_TYPES[entry.originalTypeCode]
            ?: ("🏃" to "Exercise")

        header.addView(TextView(this).apply {
            text = emoji
            textSize = 22f
            setPadding(0, 0, Ui.dp(this@JournalActivity, 10), 0)
        })

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleCol.addView(TextView(this).apply {
            text = entry.effectiveTitle().ifBlank { "Workout" }
            textSize = 15f
            setTextColor(Ui.TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
        })
        titleCol.addView(TextView(this).apply {
            text = "${timeFmt.format(Date(entry.originalStartMs))} → ${timeFmt.format(Date(entry.originalEndMs))}"
            textSize = 12f
            setTextColor(Ui.TEXT_SECONDARY)
        })
        header.addView(titleCol)

        val arrow = TextView(this).apply {
            text = if (isExpanded) "▲" else "▼"
            textSize = 14f
            setTextColor(Ui.TEXT_MUTED)
            setPadding(Ui.dp(this@JournalActivity, 8), 0, 0, 0)
        }
        header.addView(arrow)
        card.addView(header)

        // ── Expanded details ──
        val detailsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (isExpanded) View.VISIBLE else View.GONE
            tag = "details"
        }
        detailsContainer.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this@JournalActivity, 1)).also {
                it.setMargins(0, Ui.dp(this@JournalActivity, 12), 0, Ui.dp(this@JournalActivity, 8))
            }
            setBackgroundColor(Ui.BORDER_FAINT)
        })

        // Metrics
        val durMin = (entry.originalEndMs - entry.originalStartMs) / 60_000
        detailsContainer.addView(Ui.statusLine(this, "Duration", fmtDur(durMin)))
        val label = CalendarHelper.EXERCISE_TYPES[entry.originalTypeCode]?.second ?: "Exercise"
        detailsContainer.addView(Ui.statusLine(this, "Type", label))
        entry.originalDistanceM?.let {
            detailsContainer.addView(Ui.statusLine(this, "Distance", "%.2f km".format(it / 1000.0)))
        }
        entry.originalCaloriesKcal?.let {
            detailsContainer.addView(Ui.statusLine(this, "Calories", "%.0f kcal".format(it)))
        }
        entry.originalAvgHrBpm?.let {
            detailsContainer.addView(Ui.statusLine(this, "Avg HR", "%.0f bpm".format(it)))
        }
        entry.originalMaxHrBpm?.let {
            detailsContainer.addView(Ui.statusLine(this, "Max HR", "%.0f bpm".format(it)))
        }
        entry.originalPaceSecPerKm?.let {
            detailsContainer.addView(Ui.statusLine(this, "Pace", fmtPace(it)))
        }
        entry.originalStepsCount?.let {
            detailsContainer.addView(Ui.statusLine(this, "Steps", "%,d".format(it)))
        }
        detailsContainer.addView(Ui.statusLine(this, "Source", friendlySource(entry.originalSourcePkg)))

        // Notes
        val notes = entry.effectiveNotes()
        if (notes.isNotBlank()) {
            detailsContainer.addView(TextView(this).apply {
                text = "NOTES"
                textSize = 11f
                setTextColor(Ui.TEXT_MUTED)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, Ui.dp(this@JournalActivity, 6), 0, Ui.dp(this@JournalActivity, 2))
            })
            detailsContainer.addView(TextView(this).apply {
                text = notes
                textSize = 13f
                setTextColor(Ui.TEXT_SECONDARY)
            })
        }

        if (entry.hasCustomizations()) {
            detailsContainer.addView(TextView(this).apply {
                text = "(edited)"
                textSize = 11f
                setTextColor(Ui.ACCENT)
                setPadding(0, Ui.dp(this@JournalActivity, 4), 0, 0)
            })
        }

        // Action buttons
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, Ui.dp(this@JournalActivity, 12), 0, 0)
        }
        btnRow.addView(Ui.secondaryButton(this, "Edit") { showEditDialog(entry) }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also {
                it.setMargins(0, 0, Ui.dp(this@JournalActivity, 6), 0)
            }
        })
        btnRow.addView(Ui.secondaryButton(this, "Revert") {
            if (entry.hasCustomizations()) showRevertDialog(entry)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also {
                it.setMargins(Ui.dp(this@JournalActivity, 6), 0, 0, 0)
            }
        })
        detailsContainer.addView(btnRow)

        card.addView(detailsContainer)
        return card
    }

    private fun toggleExpanded(id: String, card: View) {
        if (expandedCardIds.contains(id)) expandedCardIds.remove(id)
        else expandedCardIds.add(id)
        // Rebuild card
        val parent = card.parent as? ViewGroup ?: return
        val idx   = parent.indexOfChild(card)
        parent.removeView(card)
        parent.addView(buildWorkoutCard(requireEntry(id)), idx)
    }

    private fun requireEntry(id: String): JournalEntry =
        JournalStorage.getEntry(this, id)!!

    // ── Edit dialog ──────────────────────────────────────────────────────────

    private fun showEditDialog(entry: JournalEntry) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@JournalActivity, 20), Ui.dp(this@JournalActivity, 16), Ui.dp(this@JournalActivity, 20), Ui.dp(this@JournalActivity, 8))
        }

        container.addView(TextView(this).apply {
            text = "TITLE"
            textSize = 11f
            setTextColor(Ui.TEXT_MUTED)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, Ui.dp(this@JournalActivity, 4))
        })
        val titleInput = EditText(this).apply {
            setText(entry.effectiveTitle())
            textSize = 14f
            setTextColor(Ui.TEXT_PRIMARY)
            setPadding(Ui.dp(this@JournalActivity, 12), Ui.dp(this@JournalActivity, 10), Ui.dp(this@JournalActivity, 12), Ui.dp(this@JournalActivity, 10))
            background = Ui.cardBg(Ui.dpf(this@JournalActivity, 8), fillColor = Ui.SURFACE_ELEVATED, borderColor = Ui.BORDER_FAINT)
        }
        container.addView(titleInput)

        container.addView(TextView(this).apply {
            text = "NOTES"
            textSize = 11f
            setTextColor(Ui.TEXT_MUTED)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, Ui.dp(this@JournalActivity, 12), 0, Ui.dp(this@JournalActivity, 4))
        })
        val notesInput = EditText(this).apply {
            setText(entry.effectiveNotes())
            textSize = 14f
            setTextColor(Ui.TEXT_PRIMARY)
            minLines = 4
            gravity = Gravity.START or Gravity.TOP
            setPadding(Ui.dp(this@JournalActivity, 12), Ui.dp(this@JournalActivity, 10), Ui.dp(this@JournalActivity, 12), Ui.dp(this@JournalActivity, 10))
            background = Ui.cardBg(Ui.dpf(this@JournalActivity, 8), fillColor = Ui.SURFACE_ELEVATED, borderColor = Ui.BORDER_FAINT)
        }
        container.addView(notesInput)

        AlertDialog.Builder(this)
            .setTitle("Edit Workout")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newTitle = titleInput.text.toString().trim()
                val newNotes = notesInput.text.toString().trim()
                val updated = entry.copy(
                    customTitle  = newTitle.takeIf { it.isNotEmpty() && it != entry.originalTitle },
                    customNotes  = newNotes.takeIf { it.isNotEmpty() && it != entry.originalNotes },
                    updatedAtMs  = System.currentTimeMillis(),
                )
                JournalStorage.updateEntry(this, updated)
                refreshDayEntries()
                refreshCalendar()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRevertDialog(entry: JournalEntry) {
        AlertDialog.Builder(this)
            .setTitle("Revert to original?")
            .setMessage("This will discard your custom title and notes.")
            .setPositiveButton("Revert") { _, _ ->
                JournalStorage.updateEntry(this, entry.reverted())
                refreshDayEntries()
                refreshCalendar()
            }
            .setNegativeButton("Keep edits", null)
            .show()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun shiftMonth(delta: Int) {
        currentMonth += delta
        if (currentMonth < 0) { currentMonth = 11; currentYear-- }
        if (currentMonth > 11) { currentMonth = 0; currentYear++ }
        refreshCalendar()
    }

    private fun navButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 18f
            setTextColor(Ui.TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            background = Ui.cardBg(Ui.dpf(this@JournalActivity, 8), fillColor = Ui.SURFACE_ELEVATED, borderColor = Ui.BORDER_FAINT)
            setPadding(Ui.dp(this@JournalActivity, 14), Ui.dp(this@JournalActivity, 6), Ui.dp(this@JournalActivity, 14), Ui.dp(this@JournalActivity, 6))
            setOnClickListener { onClick() }
        }

    private fun dayStartMs(year: Int, month: Int, day: Int): Long {
        val c = Calendar.getInstance()
        c.set(year, month, day, 0, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun dayStartMs(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun fmtDur(totalMinutes: Long): String {
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    private fun fmtPace(secPerKm: Double): String {
        val min = (secPerKm / 60).toInt()
        val sec = (secPerKm % 60).toInt()
        return "%d:%02d /km".format(min, sec)
    }

    private fun friendlySource(pkg: String): String = when {
        pkg.contains("samsung") -> "Samsung Health"
        pkg.contains("fitbit")  -> "Fitbit"
        pkg.contains("garmin")  -> "Garmin Connect"
        pkg.contains("fitness") -> "Google Fit"
        pkg.contains("huawei")  -> "Huawei Health"
        pkg.contains("polar")   -> "Polar Flow"
        pkg.contains("strava")  -> "Strava"
        pkg.contains("withings")-> "Withings"
        pkg.contains("whoop")   -> "WHOOP"
        else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}
