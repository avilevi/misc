package com.hcexport

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
    private var currentMonth: Int = 0
    private var selectedDate: String = ""

    private lateinit var calendarContainer: LinearLayout
    private lateinit var monthLabel: TextView
    private lateinit var dayHeaderText: TextView
    private lateinit var dayEntriesContainer: LinearLayout

    private val dateFmt    = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val headerFmt  = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cal = Calendar.getInstance()
        currentYear  = cal.get(Calendar.YEAR)
        currentMonth = cal.get(Calendar.MONTH)
        selectedDate = dateFmt.format(cal.time)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            setPadding(Ui.dp(this@JournalActivity, 20), Ui.dp(this@JournalActivity, 48), Ui.dp(this@JournalActivity, 20), Ui.dp(this@JournalActivity, 24))
        }

        // Header
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

        // Calendar card
        root.addView(Ui.card(this) {
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

            val dowRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, Ui.dp(context, 4), 0, Ui.dp(context, 4))
            }
            for (d in listOf("M", "T", "W", "T", "F", "S", "S")) {
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

            calendarContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            addView(calendarContainer)

            addView(Ui.sectionSpacer(context, 8))
            addView(Ui.secondaryButton(context, "Today") {
                val c = Calendar.getInstance()
                currentYear  = c.get(Calendar.YEAR)
                currentMonth = c.get(Calendar.MONTH)
                selectedDate = dateFmt.format(c.time)
                refreshCalendar()
                refreshDayEntries()
            })
        })

        root.addView(Ui.sectionSpacer(this, 24))

        // Day header
        dayHeaderText = TextView(this).apply {
            textSize = 18f
            setTextColor(Ui.TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, Ui.dp(this@JournalActivity, 16))
        }
        root.addView(dayHeaderText)

        // Entries container
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
        val firstDowZeroMon = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7

        val todayCal = Calendar.getInstance()
        val todayY = todayCal.get(Calendar.YEAR)
        val todayM = todayCal.get(Calendar.MONTH)
        val todayD = todayCal.get(Calendar.DAY_OF_MONTH)

        val selDay = if (selectedDate.startsWith("%04d-%02d".format(currentYear, currentMonth + 1)))
            selectedDate.substringAfterLast('-').toIntOrNull() else null

        val entries = JournalStorage.getVisibleEntriesForMonth(this, currentYear, currentMonth)
        val dayMap  = JournalStorage.buildDayMap(entries)

        var cell = 0
        val totalCells = firstDowZeroMon + daysInMonth
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, Ui.dp(this@JournalActivity, 2), 0, Ui.dp(this@JournalActivity, 2))
            }
            for (col in 0 until 7) {
                val day = cell - firstDowZeroMon + 1
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
                            selectedDate = "%04d-%02d-%02d".format(currentYear, currentMonth + 1, day)
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

    // ── Day entries (narrative diary) ────────────────────────────────────────

    private fun refreshDayEntries() {
        val dayMs = dateToMs(selectedDate)
        dayHeaderText.text = headerFmt.format(Date(dayMs))
        dayEntriesContainer.removeAllViews()

        val entries = JournalStorage.getVisibleEntriesForDay(this, selectedDate)

        if (entries.isEmpty()) {
            dayEntriesContainer.addView(TextView(this).apply {
                text = "Nothing logged on this day."
                textSize = 13f
                setTextColor(Ui.TEXT_MUTED)
                setPadding(0, Ui.dp(this@JournalActivity, 8), 0, 0)
            })
            return
        }

        for ((i, entry) in entries.withIndex()) {
            if (i > 0) {
                dayEntriesContainer.addView(Ui.cardRule(this))
            }
            dayEntriesContainer.addView(JournalEntryView.build(this, entry) { updated ->
                JournalStorage.updateEntry(this, updated)
                refreshDayEntries()
                refreshCalendar()
            })
        }
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

    private fun dateToMs(dateStr: String): Long {
        try { return dateFmt.parse(dateStr)?.time ?: System.currentTimeMillis() }
        catch (_: Exception) { return System.currentTimeMillis() }
    }
}
