package com.hcexport

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WeekStartTest {

    // 2026-11-22 is a Sunday; 2026-11-23 is a Monday.
    private val sunday = LocalDate.of(2026, 11, 22)
    private val monday = LocalDate.of(2026, 11, 23)

    @Test
    fun `a week starts on the configured day`() {
        assertEquals(
            LocalDate.of(2026, 11, 16),
            WeekStart.startOfWeek(sunday, DayOfWeek.MONDAY),
        )
        assertEquals(sunday, WeekStart.startOfWeek(sunday, DayOfWeek.SUNDAY))
        assertEquals(sunday, WeekStart.startOfWeek(monday, DayOfWeek.SUNDAY))
        assertEquals(monday, WeekStart.startOfWeek(monday, DayOfWeek.MONDAY))
    }

    @Test
    fun `month grids lead with the configured first day`() {
        val novemberFirst = LocalDate.of(2026, 11, 1) // Sunday
        assertEquals(0, WeekStart.leadingDays(novemberFirst, DayOfWeek.SUNDAY))
        assertEquals(6, WeekStart.leadingDays(novemberFirst, DayOfWeek.MONDAY))
        assertEquals(1, WeekStart.leadingDays(monday, DayOfWeek.SUNDAY))
        assertEquals(0, WeekStart.leadingDays(monday, DayOfWeek.MONDAY))
    }

    @Test
    fun `the weekday header follows the same order as the grid`() {
        assertEquals(
            listOf(
                DayOfWeek.SUNDAY,
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY,
            ),
            WeekStart.daysFrom(DayOfWeek.SUNDAY),
        )
        assertEquals(DayOfWeek.MONDAY, WeekStart.daysFrom(DayOfWeek.MONDAY).first())
        assertEquals(DayOfWeek.SUNDAY, WeekStart.daysFrom(DayOfWeek.MONDAY).last())
    }
}
