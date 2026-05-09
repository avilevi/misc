package com.hcexport

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class WodWidgetProvider : AppWidgetProvider() {

    companion object {
        /** Tell any active widgets to reload their WOD list data. */
        fun notifyDataChanged(context: Context) {
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
                ComponentName(context, WodWidgetProvider::class.java)
            )
            if (ids.isNotEmpty())
                AppWidgetManager.getInstance(context).notifyAppWidgetViewDataChanged(ids, android.R.id.list)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.wod_widget)

            // Set up the scrollable list adapter
            val adapterIntent = Intent(context, WodWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = android.net.Uri.parse(this.toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(android.R.id.list, adapterIntent)
            views.setEmptyView(android.R.id.list, android.R.id.empty)

            // Template intent for list item clicks
            val clickIntent = Intent(context, MainActivity::class.java)
            val templateIntent = PendingIntent.getActivity(
                context, widgetId, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setPendingIntentTemplate(android.R.id.list, templateIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int,
        newOptions: android.os.Bundle?,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, widgetId, newOptions)
        onUpdate(context, appWidgetManager, intArrayOf(widgetId))
    }
}
