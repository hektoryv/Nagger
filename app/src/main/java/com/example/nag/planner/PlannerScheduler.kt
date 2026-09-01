package com.example.nag.planner

import android.content.Context
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Wraps [Placer] with a persisted record of where each one-off task has already been
 * placed, so that navigating between weeks — or asking the same question from the
 * Today screen, the Calendar screen, and the widget — doesn't each independently
 * re-decide the same one-off task into a different day. [Placer.place] alone is a pure
 * function of whatever week it's asked about; called fresh for a different week it has
 * no memory of an earlier answer, which is what let a single one-off task appear to be
 * placed in several weeks at once, including weeks already in the past.
 *
 * Recurring tasks (daily / times-a-week) are still recomputed fresh per week — that's
 * correct, they're supposed to happen again — only a one-off task gets "claimed" here.
 */
object PlannerScheduler {

    fun schedule(
        context: Context,
        weekStart: LocalDate,
        today: LocalDate,
        events: List<PlannerEvent>,
        tasks: List<PlannerTask>,
        overrides: PlannerOverrides,
        dayShape: DayShape
    ): List<ScheduledBlock> {
        val assignments = PlannerStore.loadTaskAssignments(context)
        val oneOff = tasks.filter { it.recurrence == TaskRecurrence.None }
        val recurring = tasks.filter { it.recurrence != TaskRecurrence.None }
        val unassigned = oneOff.filter { it.id !in assignments }

        val placed = Placer.place(weekStart, events, recurring + unassigned, overrides, dayShape, minDate = today)

        val newlyAssigned = placed.filter { block ->
            block.kind == BlockKind.TASK && unassigned.any { it.id == block.occurrenceKey.sourceId }
        }
        if (newlyAssigned.isNotEmpty()) {
            PlannerStore.saveTaskAssignments(context, assignments + newlyAssigned.associateBy { it.occurrenceKey.sourceId })
        }

        val placedTaskIds = placed.filter { it.kind == BlockKind.TASK }.map { it.occurrenceKey.sourceId }.toSet()
        val weekDays = (0 until 7).map { weekStart.plusDays(it.toLong()) }
        val alreadyAssignedInWeek = assignments.values.filter {
            it.start.toLocalDate() in weekDays && it.occurrenceKey.sourceId !in placedTaskIds
        }

        return (placed + alreadyAssignedInWeek).sortedBy { it.start }
    }

    /** Forgets every not-yet-past one-off placement so the next view re-decides them from scratch. */
    fun recalculate(context: Context, today: LocalDate) {
        val kept = PlannerStore.loadTaskAssignments(context).filterValues { it.start.toLocalDate() < today }
        PlannerStore.saveTaskAssignments(context, kept)
    }

    /**
     * Pins a one-off task's already-placed occurrence to a new start time (any date —
     * this is also how a drag across days lands), without re-running the placer for
     * everything. Any other already-placed one-off task the move now overlaps gets
     * pushed to the next free slot that same day, same as the placer would have found
     * it a spot originally; a locked event is never pushed, and if no free slot exists
     * the pushed task is left overlapping rather than losing its placement entirely.
     */
    fun moveTaskAssignment(
        context: Context,
        taskId: String,
        newStart: LocalDateTime,
        durationMinutes: Int,
        events: List<PlannerEvent>,
        overrides: PlannerOverrides,
        dayShape: DayShape
    ) {
        val assignments = PlannerStore.loadTaskAssignments(context).toMutableMap()
        val existing = assignments[taskId] ?: return
        val newEnd = newStart.plusMinutes(durationMinutes.toLong())
        val date = newStart.toLocalDate()
        val moved = existing.copy(
            occurrenceKey = OccurrenceKey(taskId, date),
            start = newStart,
            end = newEnd
        )
        assignments[taskId] = moved

        val dayEvents = events.mapNotNull { event ->
            val eventDate = event.start.toLocalDate()
            if (eventDate != date) return@mapNotNull null
            val key = OccurrenceKey(event.id, eventDate)
            val lockState = overrides.lockStateFor(key, BlockKind.EVENT)
            if (lockState == LockState.SKIPPED) return@mapNotNull null
            ScheduledBlock(key, BlockKind.EVENT, event.title, event.start, event.end, lockState, event.location)
        }

        val overlapping = assignments.values.filter { other ->
            other.occurrenceKey.sourceId != taskId &&
                other.start.toLocalDate() == date &&
                other.start < newEnd && newStart < other.end
        }

        for (other in overlapping) {
            val otherId = other.occurrenceKey.sourceId
            val otherDuration = Duration.between(other.start, other.end).toMinutes().toInt()
            val busy = dayEvents + assignments.values.filter {
                it.occurrenceKey.sourceId != otherId && it.start.toLocalDate() == date
            }
            val slot = Placer.findSlot(date, otherDuration, dayShape, busy)
            if (slot != null) {
                assignments[otherId] = other.copy(
                    occurrenceKey = OccurrenceKey(otherId, slot.first.toLocalDate()),
                    start = slot.first,
                    end = slot.second
                )
            }
        }

        PlannerStore.saveTaskAssignments(context, assignments)
    }
}
