package com.hcexport.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Screens bump this after writing a record so lists re-read the database. */
object DataRefresh {
    private val _tick = MutableStateFlow(0L)
    val tick: StateFlow<Long> = _tick

    fun bump() {
        _tick.value += 1
    }
}
