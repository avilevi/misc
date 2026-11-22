package com.hcexport.wear

/**
 * Whether the watch app is on screen. When the phone starts a workout while
 * this is false, a notification carries the join instead of an auto-navigate.
 */
object WatchAppVisibility {
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
