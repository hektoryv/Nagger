package com.example.nag.logic

import com.example.nag.data.DayStatus
import com.example.nag.data.Entry
import com.example.nag.data.Habit
import com.example.nag.data.MissPolicy
import com.example.nag.data.ScheduleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The schedule engine is plain Kotlin on purpose, so it can be exercised here without a
 * device or an emulator. If you change anything in Schedule.kt, these should tell you
 * whether you broke a rhythm.
 */
class ScheduleTest {

    private val monday: LocalDate = LocalDate.of(2026, 8, 31)

    private fun days(from: LocalDate, count: Int) = (0 until count).map { from.plusDays(it.toLong()) }

    @Test
    fun `every other day pair started a day apart stays interleaved`() {
        val pull = Habit(id = "pull", title = "pull-ups", intervalDays = 2, startDate = monday)
        val push = Habit(id = "push", title = "push-ups", intervalDays = 2, startDate = monday.plusDays(1))
        val log = mutableListOf<Entry>()

        val pattern = days(monday, 8).map { day ->
            val a = Schedule.isDueOn(pull, day, log)
            val b = Schedule.isDueOn(push, day, log)
            if (a) log.add(Entry("pull", day, 12))
            if (b) log.add(Entry("push", day, 20))
            when {
                a && b -> "both"
                a -> "pull"
                b -> "push"
                else -> "none"
            }
        }

        assertEquals(
            listOf("pull", "push", "pull", "push", "pull", "push", "pull", "push"),
            pattern
        )
    }

    @Test
    fun `doing it late does not shift the rhythm`() {
        val habit = Habit(id = "h", title = "h", intervalDays = 2, startDate = monday)
        // Scheduled Mon and Wed. Ticked Mon, then not until Thursday.
        val log = listOf(Entry("h", monday, null), Entry("h", monday.plusDays(3), null))

        // Friday is still an occurrence, counted from the start date rather than the late tick.
        assertTrue(Schedule.scheduledOn(habit, monday.plusDays(4), log))
        assertFalse(Schedule.scheduledOn(habit, monday.plusDays(3), log))
    }

    @Test
    fun `weekday habit only fires on its chosen days`() {
        val gym = Habit(
            id = "gym",
            title = "gym",
            kind = ScheduleKind.WEEKDAYS,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            startDate = monday
        )
        val log = mutableListOf<Entry>()
        val due = days(monday, 7).map { day ->
            val d = Schedule.isDueOn(gym, day, log)
            if (d) log.add(Entry("gym", day, null))
            d
        }
        assertEquals(
            listOf(true, false, true, false, true, false, false),
            due
        )
        assertEquals(3, Schedule.streak(gym, monday.plusDays(6), log))
    }

    @Test
    fun `weekly target with SKIP stays quiet until it has to speak up`() {
        val run = Habit(
            id = "run",
            title = "run",
            kind = ScheduleKind.TIMES_PER_WEEK,
            timesPerWeek = 3,
            missPolicy = MissPolicy.SKIP,
            startDate = monday
        )
        val log = emptyList<Entry>()
        // Nothing done yet: silent until Friday, when three days remain for three sessions.
        assertFalse(Schedule.isDueOn(run, monday, log))
        assertFalse(Schedule.isDueOn(run, monday.plusDays(3), log))
        assertTrue(Schedule.isDueOn(run, monday.plusDays(4), log))
    }

    @Test
    fun `weekly target with ROLL_OVER asks until the target is met`() {
        val run = Habit(
            id = "run",
            title = "run",
            kind = ScheduleKind.TIMES_PER_WEEK,
            timesPerWeek = 2,
            missPolicy = MissPolicy.ROLL_OVER,
            startDate = monday
        )
        assertTrue(Schedule.isDueOn(run, monday, emptyList()))

        val met = listOf(Entry("run", monday, null), Entry("run", monday.plusDays(1), null))
        assertFalse(Schedule.isDueOn(run, monday.plusDays(2), met))
    }

    @Test
    fun `weekly target resets on monday`() {
        val run = Habit(
            id = "run",
            title = "run",
            kind = ScheduleKind.TIMES_PER_WEEK,
            timesPerWeek = 1,
            startDate = monday
        )
        val log = listOf(Entry("run", monday, null))
        assertFalse(Schedule.isDueOn(run, monday.plusDays(6), log))
        assertTrue(Schedule.isDueOn(run, monday.plusDays(7), log))
    }

    @Test
    fun `a missed day still reads as missed after you recover`() {
        val habit = Habit(id = "h", title = "h", intervalDays = 2, startDate = monday)
        val log = listOf(Entry("h", monday, null), Entry("h", monday.plusDays(4), null))
        val today = monday.plusDays(6)

        assertEquals(DayStatus.DONE, Schedule.statusOn(habit, monday, today, log))
        assertEquals(DayStatus.MISSED, Schedule.statusOn(habit, monday.plusDays(2), today, log))
        assertEquals(DayStatus.DONE, Schedule.statusOn(habit, monday.plusDays(4), today, log))
        assertEquals(DayStatus.DUE, Schedule.statusOn(habit, today, today, log))
        assertEquals(DayStatus.UPCOMING, Schedule.statusOn(habit, monday.plusDays(8), today, log))
        assertEquals(DayStatus.NONE, Schedule.statusOn(habit, monday.plusDays(1), today, log))
    }

    @Test
    fun `rollover keeps asking after a miss, skip waits for the next slot`() {
        val base = Habit(id = "h", title = "h", intervalDays = 2, startDate = monday)
        val log = listOf(Entry("h", monday, null))
        val tuesday = monday.plusDays(3) // a day with no scheduled occurrence, after a missed Wed

        assertTrue(Schedule.isDueOn(base.copy(missPolicy = MissPolicy.ROLL_OVER), tuesday, log))
        assertFalse(Schedule.isDueOn(base.copy(missPolicy = MissPolicy.SKIP), tuesday, log))
    }

    @Test
    fun `paused habits go quiet and drop off the calendar`() {
        val habit = Habit(id = "h", title = "h", intervalDays = 1, startDate = monday, paused = true)
        assertFalse(Schedule.isDueOn(habit, monday, emptyList()))
        assertFalse(Schedule.scheduledOn(habit, monday, emptyList()))
        assertEquals(DayStatus.NONE, Schedule.statusOn(habit, monday, monday, emptyList()))
    }

    @Test
    fun `until done asks every night and then finishes`() {
        val task = Habit(id = "t", title = "call the dentist", kind = ScheduleKind.UNTIL_DONE, startDate = monday)
        assertTrue(Schedule.isDueOn(task, monday, emptyList()))
        assertTrue(Schedule.isDueOn(task, monday.plusDays(5), emptyList()))

        val done = task.copy(finished = true)
        val log = listOf(Entry("t", monday.plusDays(5), null))
        assertFalse(Schedule.isDueOn(done, monday.plusDays(6), log))
        assertEquals(DayStatus.NONE, Schedule.statusOn(done, monday.plusDays(6), monday.plusDays(6), log))
    }

    @Test
    fun `nothing is due before the start date`() {
        val habit = Habit(id = "h", title = "h", intervalDays = 1, startDate = monday.plusDays(1))
        assertFalse(Schedule.isDueOn(habit, monday, emptyList()))
        assertTrue(Schedule.isDueOn(habit, monday.plusDays(1), emptyList()))
    }

    @Test
    fun `stats count amounts and the best session`() {
        val habit = Habit(id = "h", title = "h", intervalDays = 1, startDate = monday, tracksAmount = true)
        val log = listOf(
            Entry("h", monday, 10),
            Entry("h", monday.plusDays(1), 14),
            Entry("h", monday.plusDays(2), 12)
        )
        val stats = Schedule.statsFor(habit, monday.plusDays(3), log, 30)
        assertEquals(3, stats.done)
        assertEquals(36, stats.total)
        assertEquals(14, stats.best)
    }

    @Test
    fun `moving average smooths the series`() {
        val points = listOf(
            Schedule.Point(monday, 10),
            Schedule.Point(monday.plusDays(1), 20)
        )
        assertEquals(listOf(10.0, 15.0), Schedule.movingAverage(points, window = 5))
    }
}
