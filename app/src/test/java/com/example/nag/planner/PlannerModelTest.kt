package com.example.nag.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class PlannerModelTest {

    private val monday: LocalDate = LocalDate.of(2026, 8, 31)

    @Test
    fun `event duration is computed from start and end`() {
        val event = PlannerEvent(
            id = "e1",
            title = "Lecture",
            start = LocalDateTime.of(monday, LocalTime.of(10, 0)),
            end = LocalDateTime.of(monday, LocalTime.of(11, 30))
        )

        assertEquals(90L, event.durationMinutes)
    }

    @Test
    fun `event ending before it starts is rejected`() {
        val start = LocalDateTime.of(monday, LocalTime.of(11, 0))
        val end = LocalDateTime.of(monday, LocalTime.of(10, 0))

        assertThrows(IllegalArgumentException::class.java) {
            PlannerEvent(id = "e1", title = "Bad", start = start, end = end)
        }
    }

    @Test
    fun `events are locked and tasks are flexible by default`() {
        assertEquals(LockState.LOCKED, defaultLockState(BlockKind.EVENT))
        assertEquals(LockState.FLEXIBLE, defaultLockState(BlockKind.TASK))
    }

    @Test
    fun `overrides only apply to the specific occurrence`() {
        val key = OccurrenceKey(sourceId = "class-1", date = monday)
        val otherDay = OccurrenceKey(sourceId = "class-1", date = monday.plusWeeks(1))

        val overrides = PlannerOverrides().with(key, LockState.FLEXIBLE)

        assertEquals(LockState.FLEXIBLE, overrides.lockStateFor(key, BlockKind.EVENT))
        // The same recurring class on a different date is untouched.
        assertEquals(LockState.LOCKED, overrides.lockStateFor(otherDay, BlockKind.EVENT))
    }

    @Test
    fun `clearing an override falls back to the default`() {
        val key = OccurrenceKey(sourceId = "class-1", date = monday)
        val overrides = PlannerOverrides().with(key, LockState.SKIPPED).cleared(key)

        assertEquals(LockState.LOCKED, overrides.lockStateFor(key, BlockKind.EVENT))
    }

    @Test
    fun `task with non-positive duration is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlannerTask(title = "Nothing", durationMinutes = 0)
        }
    }

    @Test
    fun `timesPerWeek recurrence requires a positive count`() {
        assertThrows(IllegalArgumentException::class.java) {
            TaskRecurrence.TimesPerWeek(0)
        }
    }

    @Test
    fun `day shape rejects a dinner window that swallows wind-down`() {
        assertThrows(IllegalArgumentException::class.java) {
            DayShape(
                dayStart = LocalTime.of(8, 0),
                dinnerStart = LocalTime.of(18, 0),
                dinnerEnd = LocalTime.of(23, 0),
                windDownStart = LocalTime.of(22, 0)
            )
        }
    }
}
