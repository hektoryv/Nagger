package com.example.nag.planner

import android.content.Context
import java.time.LocalTime

/** Persists the ICS feed URL and the last successfully synced events. Own prefs file, kept separate from Store's habit data. */
object PlannerStore {

    private const val PREFS = "nag_planner_prefs"
    private const val KEY_FEED_URL = "feed_url"
    private const val KEY_EVENTS = "events"
    private const val KEY_TASKS = "tasks"
    private const val KEY_OVERRIDES = "overrides"
    private const val KEY_COMPLETIONS = "completions"
    private const val KEY_TASK_ASSIGNMENTS = "task_assignments"
    private const val KEY_DAY_START_HOUR = "day_start_hour"
    private const val KEY_DAY_START_MINUTE = "day_start_minute"
    private const val KEY_DINNER_START_HOUR = "dinner_start_hour"
    private const val KEY_DINNER_START_MINUTE = "dinner_start_minute"
    private const val KEY_DINNER_END_HOUR = "dinner_end_hour"
    private const val KEY_DINNER_END_MINUTE = "dinner_end_minute"
    private const val KEY_WIND_DOWN_HOUR = "wind_down_hour"
    private const val KEY_WIND_DOWN_MINUTE = "wind_down_minute"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadFeedUrl(context: Context): String? = prefs(context).getString(KEY_FEED_URL, null)

    fun saveFeedUrl(context: Context, url: String?) {
        prefs(context).edit().apply {
            if (url.isNullOrBlank()) remove(KEY_FEED_URL) else putString(KEY_FEED_URL, url)
        }.apply()
    }

    fun loadEvents(context: Context): List<PlannerEvent> {
        val raw = prefs(context).getString(KEY_EVENTS, null) ?: return emptyList()
        return PlannerJson.eventsFromString(raw)
    }

    fun saveEvents(context: Context, events: List<PlannerEvent>) {
        prefs(context).edit().putString(KEY_EVENTS, PlannerJson.eventsToString(events)).apply()
    }

    fun loadTasks(context: Context): List<PlannerTask> {
        val raw = prefs(context).getString(KEY_TASKS, null) ?: return emptyList()
        return PlannerJson.tasksFromString(raw)
    }

    fun saveTasks(context: Context, tasks: List<PlannerTask>) {
        prefs(context).edit().putString(KEY_TASKS, PlannerJson.tasksToString(tasks)).apply()
    }

    fun loadOverrides(context: Context): Map<OccurrenceKey, LockState> {
        val raw = prefs(context).getString(KEY_OVERRIDES, null) ?: return emptyMap()
        return PlannerJson.overridesFromString(raw)
    }

    fun saveOverrides(context: Context, overrides: Map<OccurrenceKey, LockState>) {
        prefs(context).edit().putString(KEY_OVERRIDES, PlannerJson.overridesToString(overrides)).apply()
    }

    fun loadCompletions(context: Context): Set<OccurrenceKey> {
        val raw = prefs(context).getString(KEY_COMPLETIONS, null) ?: return emptySet()
        return PlannerJson.completionsFromString(raw)
    }

    fun saveCompletions(context: Context, completions: Set<OccurrenceKey>) {
        prefs(context).edit().putString(KEY_COMPLETIONS, PlannerJson.completionsToString(completions)).apply()
    }

    /**
     * Where each one-off task has already been placed, so it's decided once and stays
     * put — see [PlannerScheduler]. Recurring tasks don't need this; they recompute
     * fresh every week on purpose.
     */
    fun loadTaskAssignments(context: Context): Map<String, ScheduledBlock> {
        val raw = prefs(context).getString(KEY_TASK_ASSIGNMENTS, null) ?: return emptyMap()
        return PlannerJson.taskAssignmentsFromString(raw)
    }

    fun saveTaskAssignments(context: Context, assignments: Map<String, ScheduledBlock>) {
        prefs(context).edit().putString(KEY_TASK_ASSIGNMENTS, PlannerJson.taskAssignmentsToString(assignments)).apply()
    }

    fun clearTaskAssignment(context: Context, taskId: String) {
        saveTaskAssignments(context, loadTaskAssignments(context) - taskId)
    }

    /** Falls back to the built-in default if nothing's saved, or if what's saved no longer forms a valid shape. */
    fun loadDayShape(context: Context): DayShape {
        val p = prefs(context)
        val default = DayShape()
        return runCatching {
            DayShape(
                dayStart = LocalTime.of(
                    p.getInt(KEY_DAY_START_HOUR, default.dayStart.hour),
                    p.getInt(KEY_DAY_START_MINUTE, default.dayStart.minute)
                ),
                dinnerStart = LocalTime.of(
                    p.getInt(KEY_DINNER_START_HOUR, default.dinnerStart.hour),
                    p.getInt(KEY_DINNER_START_MINUTE, default.dinnerStart.minute)
                ),
                dinnerEnd = LocalTime.of(
                    p.getInt(KEY_DINNER_END_HOUR, default.dinnerEnd.hour),
                    p.getInt(KEY_DINNER_END_MINUTE, default.dinnerEnd.minute)
                ),
                windDownStart = LocalTime.of(
                    p.getInt(KEY_WIND_DOWN_HOUR, default.windDownStart.hour),
                    p.getInt(KEY_WIND_DOWN_MINUTE, default.windDownStart.minute)
                )
            )
        }.getOrDefault(default)
    }

    fun saveDayShape(context: Context, dayShape: DayShape) {
        prefs(context).edit().apply {
            putInt(KEY_DAY_START_HOUR, dayShape.dayStart.hour)
            putInt(KEY_DAY_START_MINUTE, dayShape.dayStart.minute)
            putInt(KEY_DINNER_START_HOUR, dayShape.dinnerStart.hour)
            putInt(KEY_DINNER_START_MINUTE, dayShape.dinnerStart.minute)
            putInt(KEY_DINNER_END_HOUR, dayShape.dinnerEnd.hour)
            putInt(KEY_DINNER_END_MINUTE, dayShape.dinnerEnd.minute)
            putInt(KEY_WIND_DOWN_HOUR, dayShape.windDownStart.hour)
            putInt(KEY_WIND_DOWN_MINUTE, dayShape.windDownStart.minute)
        }.apply()
    }
}
