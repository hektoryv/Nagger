package com.example.nag.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.nag.data.Backup
import com.example.nag.data.Habit
import com.example.nag.data.Store
import com.example.nag.logic.Schedule
import com.example.nag.notify.Notifications
import com.example.nag.notify.Scheduler
import com.example.nag.planner.DayShape
import com.example.nag.planner.Placer
import com.example.nag.planner.PlannerOverrides
import com.example.nag.planner.PlannerStore
import com.example.nag.planner.PlannerSync
import com.example.nag.planner.ScheduledBlock
import com.example.nag.widget.NagWidget
import java.time.LocalDate

/**
 * Single source of truth for the UI. Every write goes through here, which is also
 * where the widget gets refreshed and the automatic backup gets rewritten.
 */
class AppState(private val context: Context) {

    var habits by mutableStateOf(Store.loadHabits(context))
        private set

    var log by mutableStateOf(Store.loadLog(context))
        private set

    var reminder by mutableStateOf(Store.reminderTime(context))
        private set

    var backupFolder by mutableStateOf(Backup.folderLabel(context))
        private set

    var plannerFeedUrl by mutableStateOf(PlannerStore.loadFeedUrl(context))
        private set

    var plannerEvents by mutableStateOf(PlannerStore.loadEvents(context))
        private set

    var plannerSyncing by mutableStateOf(false)
        private set

    var plannerSyncError by mutableStateOf<String?>(null)
        private set

    fun reload() {
        habits = Store.loadHabits(context)
        log = Store.loadLog(context)
        reminder = Store.reminderTime(context)
        backupFolder = Backup.folderLabel(context)
        plannerFeedUrl = PlannerStore.loadFeedUrl(context)
        plannerEvents = PlannerStore.loadEvents(context)
    }

    private fun afterChange() {
        reload()
        Scheduler.scheduleNightly(context)
        NagWidget.refresh(context)
        Backup.auto(context)
    }

    fun upsertHabit(habit: Habit) {
        val updated = if (habits.any { it.id == habit.id })
            habits.map { if (it.id == habit.id) habit else it }
        else habits + habit
        Store.saveHabits(context, updated)
        afterChange()
    }

    fun deleteHabit(id: String) {
        Notifications.cancel(context, id)
        Store.deleteHabit(context, id)
        afterChange()
    }

    fun setPaused(habit: Habit, paused: Boolean) {
        if (paused) Notifications.cancel(context, habit.id)
        upsertHabit(habit.copy(paused = paused))
    }

    fun setDone(habitId: String, date: LocalDate, amount: Int?) {
        Notifications.cancel(context, habitId)
        Store.setDone(context, habitId, date, amount)
        afterChange()
    }

    fun clearDone(habitId: String, date: LocalDate) {
        Store.clearDone(context, habitId, date)
        afterChange()
    }

    fun setReminder(hour: Int, minute: Int) {
        Store.setReminderTime(context, hour, minute)
        afterChange()
    }

    fun restored() = afterChange()

    fun lastAmount(habitId: String): Int? = Store.lastAmount(context, habitId)

    fun nextColorIndex(): Int = Store.nextColorIndex(habits)

    fun habit(id: String): Habit? = habits.firstOrNull { it.id == id }

    fun dueToday(today: LocalDate): List<Habit> =
        habits.filter { Schedule.isDueOn(it, today, log) }

    // ---------- planner ----------

    fun setPlannerFeedUrl(url: String?) {
        val cleaned = url?.trim()?.ifBlank { null }
        PlannerStore.saveFeedUrl(context, cleaned)
        plannerFeedUrl = cleaned
    }

    /** Fetches and parses the feed, then persists whatever it got. Call from a coroutine. */
    suspend fun syncPlanner() {
        val url = plannerFeedUrl ?: return
        plannerSyncing = true
        plannerSyncError = null
        PlannerSync.sync(url)
            .onSuccess { events ->
                plannerEvents = events
                PlannerStore.saveEvents(context, events)
            }
            .onFailure { error ->
                plannerSyncError = error.message ?: "Couldn't reach the schedule link"
            }
        plannerSyncing = false
    }

    /** Locked events plus, once there's a way to enter tasks, whatever the placer fits around them. */
    fun plannerSchedule(weekStart: LocalDate): List<ScheduledBlock> =
        Placer.place(weekStart, plannerEvents, emptyList(), PlannerOverrides(), DayShape())
}
