package com.hcexport

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Week geometry for the two calendars. The first day is a user setting; every
 * strip, month grid and range must agree on it or a Sunday-first header would
 * sit over Monday-first cells.
 */
object WeekStart {

    /** The order the setting presents: Sunday first, the local default here. */
    val selectable: List<DayOfWeek> = listOf(
        DayOfWeek.SUNDAY,
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
    )

    fun startOfWeek(date: LocalDate, firstDay: DayOfWeek): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(firstDay))

    /** Empty cells before the 1st of a month in a grid that starts on [firstDay]. */
    fun leadingDays(firstOfMonth: LocalDate, firstDay: DayOfWeek): Int =
        (firstOfMonth.dayOfWeek.value - firstDay.value + 7) % 7

    /** The seven weekdays in display order, starting at [firstDay]. */
    fun daysFrom(firstDay: DayOfWeek): List<DayOfWeek> =
        (0L..6L).map { firstDay.plus(it) }
}
