package com.hcexport

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SyncScheduleTest {

    private lateinit var oldZone: TimeZone

    @Before
    fun setUp() {
        oldZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Jerusalem"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(oldZone)
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun dailyNextIsTodayWhenStillAhead() {
        val schedule = SyncSchedule(type = "daily", hour = 8, minute = 0)
        val now = at(2026, 11, 11, 7, 0)
        assertEquals(at(2026, 11, 11, 8, 0), schedule.nextTriggerMs(now))
        assertEquals(at(2026, 11, 10, 8, 0), schedule.mostRecentTriggerMs(now))
    }

    @Test
    fun dailyAtTheExactMinuteGoesToTomorrow() {
        val schedule = SyncSchedule(type = "daily", hour = 8, minute = 0)
        val now = at(2026, 11, 11, 8, 0)
        assertEquals(at(2026, 11, 12, 8, 0), schedule.nextTriggerMs(now))
        assertEquals(at(2026, 11, 11, 8, 0), schedule.mostRecentTriggerMs(now))
    }

    @Test
    fun dailyCrossesTheYearBoundary() {
        val schedule = SyncSchedule(type = "daily", hour = 0, minute = 30)
        val now = at(2026, 12, 31, 23, 0)
        assertEquals(at(2027, 1, 1, 0, 30), schedule.nextTriggerMs(now))
    }

    @Test
    fun weeklyFindsTheNextSelectedWeekday() {
        // 2026-11-11 is a Wednesday. Monday=1.
        val schedule = SyncSchedule(type = "weekly", hour = 8, minute = 0, days = setOf(1))
        val now = at(2026, 11, 11, 12, 0)
        assertEquals(at(2026, 11, 16, 8, 0), schedule.nextTriggerMs(now))
        assertEquals(at(2026, 11, 9, 8, 0), schedule.mostRecentTriggerMs(now))
    }

    @Test
    fun mostRecentForAWeeklyScheduleToday() {
        // Wednesday=3; now is after 08:00, so today's 08:00 is the most recent.
        val schedule = SyncSchedule(type = "weekly", hour = 8, minute = 0, days = setOf(3))
        val now = at(2026, 11, 11, 12, 0)
        assertEquals(at(2026, 11, 11, 8, 0), schedule.mostRecentTriggerMs(now))
        assertEquals(at(2026, 11, 18, 8, 0), schedule.nextTriggerMs(now))
    }
}
