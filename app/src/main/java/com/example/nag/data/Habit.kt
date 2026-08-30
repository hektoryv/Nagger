package com.example.nag.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

/** How a habit decides which days it wants your attention. */
enum class ScheduleKind {
    /** Anchored to the start date, then every N days. */
    EVERY_N_DAYS,

    /** Fixed weekdays, e.g. Mon / Wed / Fri. */
    WEEKDAYS,

    /** A loose target: N times in a Monday-to-Sunday week, any days. */
    TIMES_PER_WEEK,

    /** Asks every night until you tick it, then it's finished for good. */
    UNTIL_DONE
}

enum class MissPolicy {
    /** Keeps asking every night until it's done. */
    ROLL_OVER,

    /** Marks the day missed and waits for the next scheduled day. */
    SKIP
}

data class Habit(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val kind: ScheduleKind = ScheduleKind.EVERY_N_DAYS,
    val intervalDays: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val timesPerWeek: Int = 3,
    val startDate: LocalDate = LocalDate.now(),
    val missPolicy: MissPolicy = MissPolicy.ROLL_OVER,
    val colorIndex: Int = 0,
    val tracksAmount: Boolean = false,
    val unit: String = "reps",
    val paused: Boolean = false,
    val finished: Boolean = false
) {
    /** Whether this habit is currently asking anything of you at all. */
    val active: Boolean get() = !paused && !finished

    fun rhythm(): String = when {
        finished -> "Finished"
        kind == ScheduleKind.UNTIL_DONE -> "Every night until done"
        kind == ScheduleKind.TIMES_PER_WEEK ->
            "$timesPerWeek× a week"

        kind == ScheduleKind.WEEKDAYS -> when {
            weekdays.isEmpty() -> "No days picked"
            weekdays.size == 7 -> "Every day"
            else -> weekdays.sortedBy { it.value }
                .joinToString("/") { it.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } }
        }

        intervalDays == 1 -> "Every day"
        intervalDays == 2 -> "Every other day"
        intervalDays == 7 -> "Weekly"
        else -> "Every $intervalDays days"
    }

    /** What a streak of this habit is counted in. */
    fun streakUnit(count: Int): String = when (kind) {
        ScheduleKind.TIMES_PER_WEEK -> if (count == 1) "week" else "weeks"
        else -> if (count == 1) "time" else "in a row"
    }
}

/** One completed occurrence. Amount is null for habits that don't count anything. */
data class Entry(
    val habitId: String,
    val date: LocalDate,
    val amount: Int? = null
)

/** How a habit stands on one particular day. Drives the calendar bars. */
enum class DayStatus { NONE, DONE, MISSED, DUE, UPCOMING, PENDING }

/** ARGB values used for habit bars, notification accents and dots. */
val HabitColorsArgb = intArrayOf(
    0xFF2E7D32.toInt(), // green
    0xFF1565C0.toInt(), // blue
    0xFFEF6C00.toInt(), // orange
    0xFF6A1B9A.toInt(), // purple
    0xFF00838F.toInt(), // teal
    0xFFC62828.toInt(), // red
    0xFFAD1457.toInt(), // pink
    0xFF558B2F.toInt()  // olive
)

fun habitColorArgb(index: Int): Int = HabitColorsArgb[index.mod(HabitColorsArgb.size)]
