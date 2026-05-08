package com.hcexport

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

class ReorderActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TYPE  = "type"
        const val TYPE_SLEEP    = "sleep"
        const val TYPE_EXERCISE = "exercise"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val type    = intent.getStringExtra(EXTRA_TYPE) ?: TYPE_SLEEP
        val isSleep = type == TYPE_SLEEP

        val sources = (if (isSleep) Prefs.getSleepSourcePriority(this)
                       else         Prefs.getExerciseSourcePriority(this)).toMutableList()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            )
        }

        // Header
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(this@ReorderActivity, 20), Ui.dp(this@ReorderActivity, 60), Ui.dp(this@ReorderActivity, 20), 0)
            TextView(this@ReorderActivity).apply {
                text = if (isSleep) "Sleep sources" else "Exercise sources"
                textSize = 24f
                setTextColor(Ui.TEXT_PRIMARY)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, Ui.dp(this@ReorderActivity, 6))
            }.also { addView(it) }
            TextView(this@ReorderActivity).apply {
                text = "Drag  ≡  to reorder. Top = highest priority."
                textSize = 13f
                setTextColor(Ui.TEXT_SECONDARY)
                setPadding(0, 0, 0, Ui.dp(this@ReorderActivity, 20))
            }.also { addView(it) }
        }.also { root.addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)) }

        if (sources.isEmpty()) {
            TextView(this).apply {
                text = "No sources discovered yet.\nRun a sync first to populate this list."
                textSize = 14f
                setTextColor(Ui.TEXT_MUTED)
                setPadding(Ui.dp(this@ReorderActivity, 20), Ui.dp(this@ReorderActivity, 16), Ui.dp(this@ReorderActivity, 20), 0)
            }.also { root.addView(it) }
            setContentView(root)
            return
        }

        val adapter = SourceAdapter(sources) { updated ->
            if (isSleep) Prefs.setSleepSourcePriority(this, updated)
            else         Prefs.setExerciseSourcePriority(this, updated)
        }

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
                adapter.move(from.absoluteAdapterPosition, to.absoluteAdapterPosition)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}
        })

        adapter.touchHelper = touchHelper

        RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@ReorderActivity)
            this.adapter  = adapter
            setPadding(Ui.dp(this@ReorderActivity, 12), 0, Ui.dp(this@ReorderActivity, 12), 0)
            setBackgroundColor(Ui.BG)
            touchHelper.attachToRecyclerView(this)
        }.also {
            root.addView(it, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ))
        }

        setContentView(root)
    }
}

class SourceAdapter(
    private val sources: MutableList<String>,
    private val onChanged: (List<String>) -> Unit,
) : RecyclerView.Adapter<SourceAdapter.VH>() {

    var touchHelper: ItemTouchHelper? = null

    inner class VH(row: View, val label: TextView, val handle: TextView) : RecyclerView.ViewHolder(row)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val row = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            val p = Ui.dp(parent.context, 16)
            setPadding(p, p, p, p)
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT,
            )
            val margin = Ui.dp(parent.context, 6)
            (layoutParams as RecyclerView.LayoutParams).setMargins(0, margin, 0, margin)
            background = Ui.cardBg(Ui.dpf(parent.context, 12))
        }
        val label = TextView(parent.context).apply {
            textSize = 14f
            setTextColor(Ui.TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val handle = TextView(parent.context).apply {
            text = "≡"
            textSize = 22f
            setTextColor(Ui.TEXT_MUTED)
            setPadding(Ui.dp(parent.context, 12), 0, 0, 0)
        }
        row.addView(label)
        row.addView(handle)
        return VH(row, label, handle)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.label.text = friendlyName(sources[position])
        holder.handle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) touchHelper?.startDrag(holder)
            false
        }
    }

    override fun getItemCount() = sources.size

    fun move(from: Int, to: Int) {
        Collections.swap(sources, from, to)
        notifyItemMoved(from, to)
        onChanged(sources.toList())
    }

    private fun friendlyName(pkg: String): String = when {
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
