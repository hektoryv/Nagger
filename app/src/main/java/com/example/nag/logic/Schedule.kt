package com.example.nag.logic

import com.example.nag.data.DayStatus
import com.example.nag.data.Entry
import com.example.nag.data.Habit
import com.example.nag.data.MissPolicy
import com.example.nag.data.ScheduleKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * All the date arithmetic lives here.
 *
 * Fixed schedules are anchored to the habit's start date rather than to when you last
 * ticked it, so doing something late never shifts the rhythm. That's what keeps a pair
 * of every-other-day habits interleaved instead of slowly colliding.
 */
object Schedule {

    // ---------- basic lookups ----------

    fun entriesFor(habit: Habit, log: List<Entry>): List<Entry> =
        log.filter { it.habitId == habit.id }

    fun entryOn(habit: Habit, date: LocalDate, log: List<Entry>): Entry? =
        log.firstOrNull { it.habitId == habit.id && it.date == date }

    fun doneOn(habit: Habit, date: LocalDate, log: List<Entry>): Boolean =
        entryOn(habit, date, log) != null

    fun lastDone(habit: Habit, log: List<Entry>): LocalDate? =
        entriesFor(habit, log).maxOfOrNull { it.date }

    fun weekStart(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value - 1).toLong())

    // ---------- which days does this habit want ----------

    /** True when the habit has a standing obligation on this exact date. */
    fun scheduledOn(habit: Habit, date: LocalDate, log: List<Entry>): Boolean {
        if (habit.paused) return false
        if (date.isBefore(habit.startDate)) return false
        return when (habit.kind) {
            ScheduleKind.EVERY_N_DAYS ->
                ChronoUnit.DAYS.between(habit.startDate, date) % habit.intervalDays == 0L

            ScheduleKind.WEEKDAYS -> date.dayOfWeek in habit.weekdays

            // A weekly target has no fixed days, so no single day is "scheduled".
            ScheduleKind.TIMES_PER_WEEK -> false

            ScheduleKind.UNTIL_DONE -> {
                val finishedOn = lastDone(habit, log)
                finishedOn == null || !date.isAfter(finishedOn)
            }
        }
    }

    /** The most recent day this habit was supposed to happen, on or before [date]. */
    fun lastScheduledOnOrBefore(habit: Habit, date: LocalDate, log: List<Entry>): LocalDate? {
        if (date.isBefore(habit.startDate)) return null
        return when (habit.kind) {
            ScheduleKind.EVERY_N_DAYS -> {
                val elapsed = ChronoUnit.DAYS.between(habit.startDate, date)
                habit.startDate.plusDays(elapsed - elapsed % habit.intervalDays)
            }

            ScheduleKind.WEEKDAYS -> {
                if (habit.weekdays.isEmpty()) return null
                var day = date
                repeat(7) {
                    if (day.dayOfWeek in habit.weekdays && !day.isBefore(habit.startDate)) return day
                    day = day.minusDays(1)
                }
                null
            }

            ScheduleKind.TIMES_PER_WEEK -> null
            ScheduleKind.UNTIL_DONE -> date
        }
    }

    /** The next day this habit comes up, strictly after [date]. */
    fun nextScheduledAfter(habit: Habit, date: LocalDate, log: List<Entry>): LocalDate? {
        if (!habit.active) return null
        if (date.isBefore(habit.startDate)) return habit.startDate
        return when (habit.kind) {
            ScheduleKind.EVERY_N_DAYS ->
                lastScheduledOnOrBefore(habit, date, log)?.plusDays(habit.intervalDays.toLong())

            ScheduleKind.WEEKDAYS -> {
                if (habit.weekdays.isEmpty()) return null
                var day = date.plusDays(1)
                repeat(7) {
                    if (day.dayOfWeek in habit.weekdays) return day
                    day = day.plusDays(1)
                }
                null
            }

            ScheduleKind.TIMES_PER_WEEK -> weekStart(date).plusDays(7)
            ScheduleKind.UNTIL_DONE -> date.plusDays(1)
        }
    }

    // ---------- weekly targets ----------

    fun doneThisWeek(habit: Habit, today: LocalDate, log: List<Entry>): Int {
        val start = weekStart(today)
        return entriesFor(habit, log).count { !it.date.isBefore(start) && !it.date.isAfter(today) }
    }

    fun doneInWeekOf(habit: Habit, anyDay: LocalDate, log: List<Entry>): Int {
        val start = weekStart(anyDay)
        val end = start.plusDays(6)
        return entriesFor(habit, log).count { !it.date.isBefore(start) && !it.date.isAfter(end) }
    }

    private fun daysLeftInWeek(today: LocalDate): Int = 8 - today.dayOfWeek.value

    // ---------- is it due ----------

    fun isDueOn(habit: Habit, today: LocalDate, log: List<Entry>): Boolean {
        if (!habit.active) return false
        if (today.isBefore(habit.startDate)) return false
        if (doneOn(habit, today, log)) return false

        if (habit.kind == ScheduleKind.TIMES_PER_WEEK) {
            val remaining = habit.timesPerWeek - doneThisWeek(habit, today, log)
            if (remaining <= 0) return false
            return when (habit.missPolicy) {
                MissPolicy.ROLL_OVER -> true
                // Only speak up once you'd have to go every remaining day to make it.
                MissPolicy.SKIP -> remaining >= daysLeftInWeek(today)
            }
        }

        val lastScheduled = lastScheduledOnOrBefore(habit, today, log) ?: return false
        val done = lastDone(habit, log)
        if (done != null && !done.isBefore(lastScheduled)) return false

        return when (habit.missPolicy) {
            MissPolicy.ROLL_OVER -> true
            MissPolicy.SKIP -> lastScheduled == today
        }
    }

    /** How a habit should be drawn on a given calendar day. */
    fun statusOn(habit: Habit, date: LocalDate, today: LocalDate, log: List<Entry>): DayStatus {
        if (doneOn(habit, date, log)) return DayStatus.DONE
        if (date == today && isDueOn(habit, today, log)) return DayStatus.DUE
        if (!scheduledOn(habit, date, log)) return DayStatus.NONE
        return when {
            date.isAfter(today) -> DayStatus.UPCOMING
            date == today -> DayStatus.DUE
            habit.kind == ScheduleKind.UNTIL_DONE -> DayStatus.PENDING
            else -> DayStatus.MISSED
        }
    }

    /** Plain-English "when's the next one" for the Today screen. */
    fun nextUpLabel(habit: Habit, today: LocalDate, log: List<Entry>): String {
        if (habit.paused) return "Paused"
        if (habit.finished) return "Finished"
        if (habit.kind == ScheduleKind.TIMES_PER_WEEK) {
            val done = doneThisWeek(habit, today, log)
            return "$done of ${habit.timesPerWeek} this week"
        }
        val next = nextScheduledAfter(habit, today, log) ?: return "Not scheduled"
        return when (val days = ChronoUnit.DAYS.between(today, next)) {
            0L -> "Today"
            1L -> "Tomorrow"
            in 2..6 -> "In $days days"
            else -> next.toString()
        }
    }

    // ---------- streaks and stats ----------

    fun streak(habit: Habit, today: LocalDate, log: List<Entry>): Int {
        if (habit.kind == ScheduleKind.UNTIL_DONE) return if (habit.finished) 1 else 0

        if (habit.kind == ScheduleKind.TIMES_PER_WEEK) {
            var week = weekStart(today)
            var count = 0
            // The current week only counts once the target is actually met.
            if (doneInWeekOf(habit, week, log) < habit.timesPerWeek) week = week.minusDays(7)
            while (!week.plusDays(6).isBefore(habit.startDate)) {
                if (doneInWeekOf(habit, week, log) >= habit.timesPerWeek) {
                    count++
                    week = week.minusDays(7)
                } else break
            }
            return count
        }

        var day = lastScheduledOnOrBefore(habit, today, log) ?: return 0
        if (day == today && !doneOn(habit, today, log)) {
            day = previousScheduled(habit, day) ?: return 0
        }
        var count = 0
        while (!day.isBefore(habit.startDate) && doneOn(habit, day, log)) {
            count++
            day = previousScheduled(habit, day) ?: break
        }
        return count
    }

    private fun previousScheduled(habit: Habit, from: LocalDate): LocalDate? = when (habit.kind) {
        ScheduleKind.EVERY_N_DAYS -> from.minusDays(habit.intervalDays.toLong())
        ScheduleKind.WEEKDAYS -> {
            if (habit.weekdays.isEmpty()) null else {
                var day = from.minusDays(1)
                var found: LocalDate? = null
                repeat(7) {
                    if (found == null && day.dayOfWeek in habit.weekdays) found = day
                    if (found == null) day = day.minusDays(1)
                }
                found
            }
        }

        else -> null
    }

    data class HabitStats(
        val done: Int,
        val scheduled: Int,
        val streak: Int,
        val total: Int,
        val best: Int
    ) {
        val percent: Int get() = if (scheduled == 0) 0 else ((done * 100) / scheduled).coerceAtMost(100)
    }

    /** Counts over the last [windowDays] days. Today is excluded so a pending evening doesn't count against you. */
    fun statsFor(
        habit: Habit,
        today: LocalDate,
        log: List<Entry>,
        windowDays: Long = 30
    ): HabitStats {
        val from = today.minusDays(windowDays)
        val within = entriesFor(habit, log).filter { !it.date.isBefore(from) && it.date.isBefore(today) }
        val done = within.size
        val total = within.mapNotNull { it.amount }.sum()
        val best = within.mapNotNull { it.amount }.maxOrNull() ?: 0

        val scheduled = if (habit.kind == ScheduleKind.TIMES_PER_WEEK) {
            // Whole weeks in the window, times the target.
            ((windowDays / 7).toInt()) * habit.timesPerWeek
        } else {
            var count = 0
            var day = from
            while (day.isBefore(today)) {
                if (scheduledOn(habit, day, log)) count++
                day = day.plusDays(1)
            }
            count
        }

        return HabitStats(done, scheduled, streak(habit, today, log), total, best)
    }

    /** Consecutive days where everything with a fixed obligation got done. */
    fun perfectDayStreak(habits: List<Habit>, today: LocalDate, log: List<Entry>): Int {
        val fixed = habits.filter { it.active && it.kind != ScheduleKind.TIMES_PER_WEEK }
        if (fixed.isEmpty()) return 0
        val earliest = fixed.minOf { it.startDate }

        var day = today
        if (fixed.any { isDueOn(it, today, log) }) day = today.minusDays(1)

        var count = 0
        while (!day.isBefore(earliest)) {
            val obliged = fixed.filter { scheduledOn(it, day, log) }
            if (obliged.isEmpty()) {
                day = day.minusDays(1)
                continue
            }
            if (obliged.all { doneOn(it, day, log) }) {
                count++
                day = day.minusDays(1)
            } else break
        }
        return count
    }

    // ---------- chart data ----------

    data class Point(val date: LocalDate, val value: Int)

    /** Amounts logged in the window, oldest first. Used for the progress chart. */
    fun amountSeries(habit: Habit, today: LocalDate, log: List<Entry>, windowDays: Long): List<Point> {
        val from = if (windowDays <= 0) LocalDate.MIN else today.minusDays(windowDays)
        return entriesFor(habit, log)
            .filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
            .sortedBy { it.date }
            .map { Point(it.date, it.amount ?: 0) }
    }

    /** Completions per week, oldest first. Used for habits that don't count anything. */
    fun weeklyCounts(habit: Habit, today: LocalDate, log: List<Entry>, weeks: Int): List<Point> {
        val thisWeek = weekStart(today)
        return (weeks - 1 downTo 0).map { back ->
            val start = thisWeek.minusDays(back * 7L)
            Point(start, doneInWeekOf(habit, start, log))
        }
    }

    /** Simple trailing average, same length as the input, for the trend line. */
    fun movingAverage(points: List<Point>, window: Int = 5): List<Double> =
        points.indices.map { i ->
            val from = maxOf(0, i - window + 1)
            val slice = points.subList(from, i + 1)
            slice.sumOf { it.value }.toDouble() / slice.size
        }
}
