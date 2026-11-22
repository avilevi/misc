package com.hcexport

/**
 * Whether an app window is on screen. A live workout started on the watch may
 * open the phone's live screen, but Android only lets an activity be launched
 * from the background through a notification; with the app already in front
 * the navigation is immediate.
 */
object AppVisibility {
    @Volatile
    var foreground: Boolean = false
        private set

    fun onStart() {
        foreground = true
    }

    fun onStop() {
        foreground = false
    }
}
