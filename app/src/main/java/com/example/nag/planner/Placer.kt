package com.example.nag.planner

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Greedy day-by-day scheduler — deliberately not a constraint solver. Locked blocks
 * (events; eventually pinned tasks) are placed exactly where they already are, and a
 * skipped occurrence is dropped entirely. Flexible tasks are then walked in over the
 * week, day by day, deadline-soonest first, and dropped into the first gap that fits —
 * skipping dinner and anything at or after wind-down. A task that recurs (daily, or N
 * times a week) needs one placement per occurrence and never two on the same day, so
 * it actually spreads across the week instead of clumping. A task that doesn't fit
 * anywhere by the end of the week is simply left unplaced rather than forced somewhere
 * that breaks the day's shape.
 *
 * Overrides are only consulted for events here — a task occurrence doesn't exist to be
 * locked or skipped until something has placed it once, which needs task-editing UI
 * this app doesn't have yet.
 *
 * This function is pure and stateless: called again with the same inputs it makes the
 * same decision. That's deliberate for a single week, but it means a one-off task has
 * no memory of which week it already landed in if this is called once per week by
 * several independent callers — see [PlannerScheduler], which wraps this with a
 * persisted record of where each one-off task has already been placed, and passes
 * [minDate] so a task being placed for the first time is never dropped onto a day
 * that's already passed.
 */
object Placer {

    fun place(
        weekStart: LocalDate,
        events: List<PlannerEvent>,
        tasks: List<PlannerTask>,
        overrides: PlannerOverrides,
        dayShape: DayShape,
        minDate: LocalDate = weekStart
    ): List<ScheduledBlock> {
        val weekDays = (0 until 7).map { weekStart.plusDays(it.toLong()) }

        val fixed = events.mapNotNull { event ->
            val date = event.start.toLocalDate()
            if (date !in weekDays) return@mapNotNull null
            val key = OccurrenceKey(event.id, date)
            val lockState = overrides.lockStateFor(key, BlockKind.EVENT)
            if (lockState == LockState.SKIPPED) return@mapNotNull null
            ScheduledBlock(key, BlockKind.EVENT, event.title, event.start, event.end, lockState, event.location)
        }

        val busyByDay: Map<LocalDate, MutableList<ScheduledBlock>> = weekDays.associateWith { date ->
            fixed.filter { it.start.toLocalDate() == date }.toMutableList()
        }

        val placedTasks = mutableListOf<ScheduledBlock>()
        val remaining = occurrencesNeeded(tasks).toMutableList()
        val usedDaysByTask = mutableMapOf<String, MutableSet<LocalDate>>()

        for (day in weekDays) {
            val busy = busyByDay.getValue(day)
            val iterator = remaining.iterator()
            while (iterator.hasNext()) {
                val task = iterator.next()
                // Only a one-off task's single occurrence needs to stay off days that
                // have already passed — a recurring task's occurrence on a past day of
                // the week being viewed is still meaningful history, not a decision to
                // avoid. See PlannerScheduler for why this matters.
                if (task.recurrence == TaskRecurrence.None && day < minDate) continue
                val usedDays = usedDaysByTask.getOrPut(task.id) { mutableSetOf() }
                if (day in usedDays) continue

                val slot = findSlot(day, task.durationMinutes, dayShape, busy) ?: continue
                val block = ScheduledBlock(
                    OccurrenceKey(task.id, day), BlockKind.TASK, task.title, slot.first, slot.second,
                    LockState.FLEXIBLE, deadline = task.deadline
                )
                placedTasks.add(block)
                busy.add(block)
                usedDays.add(day)
                iterator.remove()
            }
        }

        return (fixed + placedTasks).sortedBy { it.start }
    }

    /** Expands each task into however many separate occurrences it needs placed this week. */
    private fun occurrencesNeeded(tasks: List<PlannerTask>): List<PlannerTask> = tasks.flatMap { task ->
        val count = when (val recurrence = task.recurrence) {
            TaskRecurrence.None -> 1
            TaskRecurrence.Daily -> 7
            is TaskRecurrence.TimesPerWeek -> recurrence.count
        }
        List(count) { task }
    }.sortedWith(deadlineThenTitle)

    /** Earliest deadline first; no-deadline tasks are least urgent and sort last. */
    private val deadlineThenTitle = Comparator<PlannerTask> { a, b ->
        val deadlineA = a.deadline
        val deadlineB = b.deadline
        when {
            deadlineA == null && deadlineB == null -> a.title.compareTo(b.title)
            deadlineA == null -> 1
            deadlineB == null -> -1
            deadlineA != deadlineB -> deadlineA.compareTo(deadlineB)
            else -> a.title.compareTo(b.title)
        }
    }

    /** First gap on [day], at or after [DayShape.dayStart] and before wind-down, that fits [durationMinutes]. */
    private fun findSlot(
        day: LocalDate,
        durationMinutes: Int,
        dayShape: DayShape,
        busy: List<ScheduledBlock>
    ): Pair<LocalDateTime, LocalDateTime>? {
        val dayStart = LocalDateTime.of(day, dayShape.dayStart)
        val windDown = LocalDateTime.of(day, dayShape.windDownStart)
        val dinnerStart = LocalDateTime.of(day, dayShape.dinnerStart)
        val dinnerEnd = LocalDateTime.of(day, dayShape.dinnerEnd)

        val blockers = (busy.map { it.start to it.end } + (dinnerStart to dinnerEnd)).sortedBy { it.first }

        var cursor = dayStart
        for ((busyStart, busyEnd) in blockers) {
            if (busyStart > cursor) {
                val gapEnd = minOf(busyStart, windDown)
                if (Duration.between(cursor, gapEnd).toMinutes() >= durationMinutes) {
                    return cursor to cursor.plusMinutes(durationMinutes.toLong())
                }
            }
            if (busyEnd > cursor) cursor = busyEnd
        }
        if (Duration.between(cursor, windDown).toMinutes() >= durationMinutes) {
            return cursor to cursor.plusMinutes(durationMinutes.toLong())
        }
        return null
    }
}
