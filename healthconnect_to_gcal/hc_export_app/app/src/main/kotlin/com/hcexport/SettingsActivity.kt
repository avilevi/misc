package com.hcexport

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hcexport.ui.settings.SettingsApp
import com.hcexport.ui.theme.HcTheme

class SettingsActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PAGE = "settings_page"
        const val PAGE_CALENDAR = "calendar"
        const val PAGE_SYNC = "sync"
        const val PAGE_WORKOUTS = "workouts"
        const val PAGE_AI = "ai"
        const val PAGE_DIAGNOSTICS = "diagnostics"
        const val PAGE_ABOUT = "about"

        fun intent(context: Context, page: String? = null): Intent =
            Intent(context, SettingsActivity::class.java).apply {
                page?.let { putExtra(EXTRA_PAGE, it) }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val startPage = intent?.getStringExtra(EXTRA_PAGE)
        setContent {
            HcTheme {
                SettingsApp(startPage)
            }
        }
    }
}
