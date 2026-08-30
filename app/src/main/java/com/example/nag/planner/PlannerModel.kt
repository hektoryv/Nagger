package com.example.nag.planner

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

/**
 * A fixed calendar block ingested from an ICS feed, e.g. one class session. This is a
 * concrete occurrence (a specific start/end), not a recurrence rule — the ICS feed is
 * expected to already expand recurring classes into individual VEVENTs.
 */
data class PlannerEvent(
    val id: String,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val location: String? = null
) {
    init {
        require(end > start) { "event must not end before it starts: $title" }
    }

    val durationMinutes: Long get() = Duration.between(start, end).toMinutes()
}

/** How often a task repeats. [None] is a one-off task. */
sealed class TaskRecurrence {
    object None : TaskRecurrence()
    object Daily : TaskRecurrence()
    data class TimesPerWeek(val count: Int) : TaskRecurrence() {
        init {
            require(count > 0) { "timesPerWeek must be positive, was $count" }
        }
    }
}

/**
 * A user-entered task with a duration estimate, to be placed into the week by the
 * planner. Unlike [PlannerEvent], a task has no fixed time until something schedules it.
 */
data class PlannerTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val durationMinutes: Int,
    val deadline: LocalDate? = null,
    val recurrence: TaskRecurrence = TaskRecurrence.None
) {
    init {
        require(durationMinutes > 0) { "durationMinutes must be positive, was $durationMinutes" }
    }
}

/** What kind of source a scheduled block came from. */
enum class BlockKind { EVENT, TASK }

/**
 * Whether one occurrence of an event or task can be moved by a replan.
 *
 * LOCKED blocks are never touched by the placer — this is the "red border" in the
 * weekly view. Events are LOCKED by default; tasks are FLEXIBLE by default. SKIPPED
 * means the occurrence is dropped entirely for that date.
 */
enum class LockState { LOCKED, FLEXIBLE, SKIPPED }

/** Default lock state for a freshly-ingested block, before any user override. */
fun defaultLockState(kind: BlockKind): LockState = when (kind) {
    BlockKind.EVENT -> LockState.LOCKED
    BlockKind.TASK -> LockState.FLEXIBLE
}

/**
 * Identifies one occurrence of an event or task. Override state (skip / make flexible)
 * is stored per occurrence, not per definition — skipping one class instance must not
 * affect the rest of that recurring class.
 */
data class OccurrenceKey(val sourceId: String, val date: LocalDate)

/** User-set lock-state overrides, keyed by occurrence. Absent entries fall back to the source's default. */
data class PlannerOverrides(private val states: Map<OccurrenceKey, LockState> = emptyMap()) {

    fun lockStateFor(key: OccurrenceKey, kind: BlockKind): LockState =
        states[key] ?: defaultLockState(kind)

    fun with(key: OccurrenceKey, state: LockState): PlannerOverrides =
        PlannerOverrides(states + (key to state))

    fun cleared(key: OccurrenceKey): PlannerOverrides =
        PlannerOverrides(states - key)
}

/**
 * The shape of a day the placer must respect: don't schedule over dinner, don't start
 * new work before [dayStart], don't place anything at or after [windDownStart].
 */
data class DayShape(
    val dayStart: LocalTime = LocalTime.of(8, 0),
    val dinnerStart: LocalTime = LocalTime.of(18, 0),
    val dinnerEnd: LocalTime = LocalTime.of(19, 0),
    val windDownStart: LocalTime = LocalTime.of(22, 0)
) {
    init {
        require(dayStart < dinnerStart) { "dayStart must be before dinnerStart" }
        require(dinnerStart < dinnerEnd) { "dinnerStart must be before dinnerEnd" }
        require(dinnerEnd <= windDownStart) { "dinnerEnd must not be after windDownStart" }
    }
}

/** A block sitting in the week's schedule, whether fixed from an event or placed by the planner. */
data class ScheduledBlock(
    val occurrenceKey: OccurrenceKey,
    val kind: BlockKind,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val lockState: LockState
)
