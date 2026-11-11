package com.hcexport

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Samsung's battery manager can hold background work until the app is next opened.
 * An exemption is the honest mitigation; the rest is re-arming and catch-up.
 */
object BatteryOptimizationHelper {

    fun isIgnoringBatteryOptimizations(ctx: Context): Boolean {
        val pm = ctx.getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun isBackgroundRestricted(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val am = ctx.getSystemService(android.app.ActivityManager::class.java) ?: return false
        return am.isBackgroundRestricted
    }

    @SuppressLint("BatteryLife")
    fun requestExemptionIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${ctx.packageName}")
        }

    fun statusLine(ctx: Context): String = when {
        !isIgnoringBatteryOptimizations(ctx) -> "Background sync may be delayed — allow unrestricted battery use."
        isBackgroundRestricted(ctx) -> "Background restricted — sync may be delayed. Allow background activity in Settings."
        else -> "Background sync allowed."
    }
}
