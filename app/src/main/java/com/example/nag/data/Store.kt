package com.example.nag.data

import android.content.Context
import java.time.LocalDate

object Store {

    private const val PREFS = "nag_prefs"
    private const val KEY_HABITS = "habits"
    private const val KEY_LOG = "log"
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"

    internal fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- habits ----------

    fun loadHabits(context: Context): List<Habit> {
        val raw = prefs(context).getString(KEY_HABITS, null) ?: return emptyList()
        val habits = Json.habitsFromString(raw)
        if (habits.isEmpty()) return habits
        return migrateIfNeeded(context, raw, habits)
    }

    fun saveHabits(context: Context, habits: List<Habit>) {
        prefs(context).edit().putString(KEY_HABITS, Json.habitsToString(habits)).apply()
    }

    fun findHabit(context: Context, id: String): Habit? =
        loadHabits(context).firstOrNull { it.id == id }

    /** Lowest colour index not already taken, so new habits look distinct. */
    fun nextColorIndex(existing: List<Habit>): Int {
        val used = existing.map { it.colorIndex.mod(HabitColorsArgb.size) }.toSet()
        return HabitColorsArgb.indices.firstOrNull { it !in used } ?: existing.size
    }

    // ---------- log ----------

    fun loadLog(context: Context): List<Entry> {
        val raw = prefs(context).getString(KEY_LOG, null) ?: return emptyList()
        return Json.logFromString(raw)
    }

    fun saveLog(context: Context, log: List<Entry>) {
        prefs(context).edit().putString(KEY_LOG, Json.logToString(log)).apply()
    }

    /** Records a completion, replacing any existing one for that habit and day. */
    fun setDone(context: Context, habitId: String, date: LocalDate, amount: Int?) {
        val log = loadLog(context)
            .filterNot { it.habitId == habitId && it.date == date }
            .plus(Entry(habitId, date, amount))
        saveLog(context, log)

        // A one-off task is over once it's ticked.
        val habit = findHabit(context, habitId)
        if (habit != null && habit.kind == ScheduleKind.UNTIL_DONE) {
            saveHabits(context, loadHabits(context).map {
                if (it.id == habitId) it.copy(finished = true) else it
            })
        }
    }

    fun clearDone(context: Context, habitId: String, date: LocalDate) {
        saveLog(context, loadLog(context).filterNot { it.habitId == habitId && it.date == date })
        val habit = findHabit(context, habitId)
        if (habit != null && habit.kind == ScheduleKind.UNTIL_DONE) {
            saveHabits(context, loadHabits(context).map {
                if (it.id == habitId) it.copy(finished = false) else it
            })
        }
    }

    fun deleteHabit(context: Context, habitId: String) {
        saveHabits(context, loadHabits(context).filterNot { it.id == habitId })
        saveLog(context, loadLog(context).filterNot { it.habitId == habitId })
    }

    /** Last number logged, so an input can pre-fill with something sensible. */
    fun lastAmount(context: Context, habitId: String): Int? =
        loadLog(context).filter { it.habitId == habitId && it.amount != null }
            .maxByOrNull { it.date }?.amount

    // ---------- settings ----------

    fun reminderTime(context: Context): Pair<Int, Int> {
        val p = prefs(context)
        return p.getInt(KEY_HOUR, 23) to p.getInt(KEY_MINUTE, 0)
    }

    fun setReminderTime(context: Context, hour: Int, minute: Int) {
        prefs(context).edit().putInt(KEY_HOUR, hour).putInt(KEY_MINUTE, minute).apply()
    }

    fun replaceAll(context: Context, habits: List<Habit>, log: List<Entry>, hour: Int, minute: Int) {
        saveHabits(context, habits)
        saveLog(context, log)
        setReminderTime(context, hour, minute)
    }

    // ---------- migration ----------

    /**
     * v1 stored a single "lastDone" per habit and no colours; v2 had no start dates on
     * some records. Both are folded forward here, once.
     */
    private fun migrateIfNeeded(context: Context, raw: String, habits: List<Habit>): List<Habit> {
        if (!Json.looksLikeV1(raw)) return habits

        val log = loadLog(context).toMutableList()
        Json.v1Entries(raw).forEach { entry ->
            if (log.none { it.habitId == entry.habitId && it.date == entry.date }) log.add(entry)
        }
        val recoloured = habits.mapIndexed { i, h -> h.copy(colorIndex = i) }
        saveLog(context, log)
        saveHabits(context, recoloured)
        return recoloured
    }
}
