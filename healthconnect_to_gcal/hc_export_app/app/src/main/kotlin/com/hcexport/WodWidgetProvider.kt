package com.hcexport

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews

class WodWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "WodWidgetProvider"

        /** Tell any active widgets to reload their WOD list data. */
        fun notifyDataChanged(context: Context) {
            try {
                val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
                    ComponentName(context, WodWidgetProvider::class.java)
                )
                if (ids.isNotEmpty())
                    AppWidgetManager.getInstance(context).notifyAppWidgetViewDataChanged(ids, R.id.wod_list)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to notify widget data changed", e)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            try {
                val views = RemoteViews(context.packageName, R.layout.wod_widget)

                val adapterIntent = Intent(context, WodWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    data = android.net.Uri.parse(this.toUri(Intent.URI_INTENT_SCHEME))
                }
                views.setRemoteAdapter(R.id.wod_list, adapterIntent)
                views.setEmptyView(R.id.wod_list, R.id.wod_empty)

                // Template intent for list item clicks opens the app
                val clickIntent = Intent(context, MainActivity::class.java)
                val templateIntent = PendingIntent.getActivity(
                    context, widgetId, clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setPendingIntentTemplate(R.id.wod_list, templateIntent)

                appWidgetManager.updateAppWidget(widgetId, views)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update widget $widgetId", e)
            }
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
