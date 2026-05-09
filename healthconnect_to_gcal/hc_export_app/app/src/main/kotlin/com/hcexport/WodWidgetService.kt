package com.hcexport

import android.content.Intent
import android.text.Html
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class WodWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        WodViewsFactory(applicationContext)

    private class WodViewsFactory(private val context: android.content.Context) : RemoteViewsFactory {

        private var wods: List<CalendarHelper.WodEventInfo> = emptyList()

        override fun onCreate() {}

        override fun onDataSetChanged() {
            wods = try {
                CalendarHelper.findWodEvents(context, 7)
            } catch (e: Exception) {
                Log.w("WodWidget", "Failed to query WOD events", e)
                emptyList()
            }
        }

        override fun getCount(): Int = wods.size

        override fun getViewAt(position: Int): RemoteViews =
            try {
                buildItemView(position)
            } catch (e: Exception) {
                Log.w("WodWidget", "Failed to build item view at $position", e)
                RemoteViews(context.packageName, R.layout.wod_widget_item).apply {
                    setTextViewText(R.id.item_date, "Error")
                    setTextViewText(R.id.item_time, "")
                    setTextViewText(R.id.item_desc, "")
                }
            }

        private fun buildItemView(position: Int): RemoteViews {
            val wod = wods[position]
            val views = RemoteViews(context.packageName, R.layout.wod_widget_item)

            // Date
            val instant = Instant.ofEpochMilli(wod.startMs)
            val zdt = instant.atZone(ZoneId.systemDefault())
            val dateStr = zdt.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
            views.setTextViewText(R.id.item_date, dateStr)

            // Time
            val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
            val startTime = timeFmt.format(Date(wod.startMs))
            val endTime = timeFmt.format(Date(wod.endMs))
            views.setTextViewText(R.id.item_time, "$startTime – $endTime")

            // Description preview (strip HTML + sync marker, truncate)
            val desc = wod.description?.let { html ->
                val stripped = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                } else {
                    @Suppress("DEPRECATION")
                    Html.fromHtml(html).toString().trim()
                }
                // Remove the sync marker line
                val cleaned = stripped.replace("# populated by wod_sync", "").trim()
                if (cleaned.isNotEmpty()) cleaned.take(250) else null
            }
            if (desc != null) {
                views.setTextViewText(R.id.item_desc, desc)
            } else {
                views.setTextViewText(R.id.item_desc, "")
            }

            // Fill-in intent for item click
            val fillInIntent = Intent().apply {
                putExtra("wod_date", wod.dateStr)
            }
            views.setOnClickFillInIntent(R.id.item_root, fillInIntent)

            return views
        }

        override fun getLoadingView(): RemoteViews =
            RemoteViews(context.packageName, R.layout.wod_widget_item).apply {
                setTextViewText(R.id.item_date, "Loading…")
                setTextViewText(R.id.item_time, "")
                setTextViewText(R.id.item_desc, "")
            }

        override fun getViewTypeCount(): Int = 1
        override fun getItemId(position: Int): Long = wods[position].id
        override fun hasStableIds(): Boolean = true
        override fun onDestroy() {}
    }
}
