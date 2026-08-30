package com.example.nag.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class PlacerTest {

    private val monday: LocalDate = LocalDate.of(2026, 8, 31)
    private val dayShape = DayShape(
        dayStart = LocalTime.of(8, 0),
        dinnerStart = LocalTime.of(18, 0),
        dinnerEnd = LocalTime.of(19, 0),
        windDownStart = LocalTime.of(22, 0)
    )

    private fun at(day: LocalDate, hour: Int, minute: Int = 0) = LocalDateTime.of(day, LocalTime.of(hour, minute))

    @Test
    fun `a locked event is placed exactly where it already is`() {
        val event = PlannerEvent(id = "class-1", title = "Databases", start = at(monday, 10), end = at(monday, 11))

        val result = Placer.place(monday, listOf(event), emptyList(), PlannerOverrides(), dayShape)

        assertEquals(1, result.size)
        val block = result.single()
        assertEquals(at(monday, 10), block.start)
        assertEquals(at(monday, 11), block.end)
        assertEquals(LockState.LOCKED, block.lockState)
        assertEquals(BlockKind.EVENT, block.kind)
    }

    @Test
    fun `a skipped event occurrence is dropped entirely`() {
        val event = PlannerEvent(id = "class-1", title = "Databases", start = at(monday, 10), end = at(monday, 11))
        val overrides = PlannerOverrides().with(OccurrenceKey("class-1", monday), LockState.SKIPPED)

        val result = Placer.place(monday, listOf(event), emptyList(), overrides, dayShape)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `an event outside the requested week is excluded`() {
        val event = PlannerEvent(
            id = "class-1",
            title = "Next week",
            start = at(monday.plusWeeks(1), 10),
            end = at(monday.plusWeeks(1), 11)
        )

        val result = Placer.place(monday, listOf(event), emptyList(), PlannerOverrides(), dayShape)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `a flexible task is placed in the first free slot of the week`() {
        val task = PlannerTask(id = "t1", title = "Insurance", durationMinutes = 120)

        val result = Placer.place(monday, emptyList(), listOf(task), PlannerOverrides(), dayShape)

        assertEquals(1, result.size)
        val block = result.single()
        assertEquals(BlockKind.TASK, block.kind)
        assertEquals(LockState.FLEXIBLE, block.lockState)
        assertEquals(at(monday, 8), block.start)
        assertEquals(at(monday, 10), block.end)
    }

    @Test
    fun `a task is placed around a locked event on the same day`() {
        val event = PlannerEvent(id = "class-1", title = "Morning class", start = at(monday, 8), end = at(monday, 11))
        val task = PlannerTask(id = "t1", title = "Insurance", durationMinutes = 60)

        val result = Placer.place(monday, listOf(event), listOf(task), PlannerOverrides(), dayShape)

        val taskBlock = result.single { it.kind == BlockKind.TASK }
        assertEquals(at(monday, 11), taskBlock.start)
        assertEquals(at(monday, 12), taskBlock.end)
    }

    @Test
    fun `a task never gets scheduled over dinner`() {
        // Only a sliver is free before dinner (17:45-18:00) and the day is otherwise
        // packed until dinner ends, so a 30 minute task must land after dinner.
        val event = PlannerEvent(id = "class-1", title = "Long class", start = at(monday, 8), end = at(monday, 17, 45))
        val task = PlannerTask(id = "t1", title = "Reading", durationMinutes = 30)

        val result = Placer.place(monday, listOf(event), listOf(task), PlannerOverrides(), dayShape)

        val taskBlock = result.single { it.kind == BlockKind.TASK }
        assertEquals(at(monday, 19), taskBlock.start)
    }

    @Test
    fun `earlier deadline is placed on an earlier day when only one task fits per day`() {
        // Each day has exactly one hour free (21:00-22:00) — the rest is one long
        // locked class — so only one of the two 60 minute tasks can land on Monday.
        val busyDays = (0 until 7).map { offset ->
            val day = monday.plusDays(offset.toLong())
            PlannerEvent(id = "class-$offset", title = "All day", start = at(day, 8), end = at(day, 21))
        }
        val urgent = PlannerTask(id = "urgent", title = "Urgent", durationMinutes = 60, deadline = monday)
        val relaxed = PlannerTask(id = "relaxed", title = "Relaxed", durationMinutes = 60, deadline = monday.plusWeeks(4))

        val result = Placer.place(monday, busyDays, listOf(relaxed, urgent), PlannerOverrides(), dayShape)

        val urgentBlock = result.single { it.occurrenceKey.sourceId == "urgent" }
        assertEquals(monday, urgentBlock.start.toLocalDate())
    }

    @Test
    fun `a task that never fits anywhere in the week is left unplaced`() {
        val busyDays = (0 until 7).map { offset ->
            val day = monday.plusDays(offset.toLong())
            PlannerEvent(id = "class-$offset", title = "All day", start = at(day, 8), end = at(day, 22))
        }
        val task = PlannerTask(id = "t1", title = "No room", durationMinutes = 30)

        val result = Placer.place(monday, busyDays, listOf(task), PlannerOverrides(), dayShape)

        assertTrue(result.none { it.kind == BlockKind.TASK })
    }

    @Test
    fun `a daily task gets one occurrence on every day of the week`() {
        val task = PlannerTask(id = "t1", title = "Training", durationMinutes = 30, recurrence = TaskRecurrence.Daily)

        val result = Placer.place(monday, emptyList(), listOf(task), PlannerOverrides(), dayShape)

        assertEquals(7, result.size)
        val days = result.map { it.start.toLocalDate() }.toSet()
        assertEquals((0 until 7).map { monday.plusDays(it.toLong()) }.toSet(), days)
    }

    @Test
    fun `a placed task's deadline carries into its ScheduledBlock`() {
        val task = PlannerTask(id = "t1", title = "Insurance", durationMinutes = 60, deadline = monday.plusDays(3))

        val result = Placer.place(monday, emptyList(), listOf(task), PlannerOverrides(), dayShape)

        assertEquals(monday.plusDays(3), result.single().deadline)
    }

    @Test
    fun `a times-per-week task never gets placed twice on the same day`() {
        val task = PlannerTask(
            id = "t1",
            title = "Bouldering",
            durationMinutes = 90,
            recurrence = TaskRecurrence.TimesPerWeek(2)
        )

        val result = Placer.place(monday, emptyList(), listOf(task), PlannerOverrides(), dayShape)

        assertEquals(2, result.size)
        assertEquals(2, result.map { it.start.toLocalDate() }.distinct().size)
    }
}
