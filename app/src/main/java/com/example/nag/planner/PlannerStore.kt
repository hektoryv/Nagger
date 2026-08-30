package com.example.nag.planner

import android.content.Context

/** Persists the ICS feed URL and the last successfully synced events. Own prefs file, kept separate from Store's habit data. */
object PlannerStore {

    private const val PREFS = "nag_planner_prefs"
    private const val KEY_FEED_URL = "feed_url"
    private const val KEY_EVENTS = "events"
    private const val KEY_TASKS = "tasks"
    private const val KEY_OVERRIDES = "overrides"

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
}
