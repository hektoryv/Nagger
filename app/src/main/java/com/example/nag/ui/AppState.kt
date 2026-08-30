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

    fun reload() {
        habits = Store.loadHabits(context)
        log = Store.loadLog(context)
        reminder = Store.reminderTime(context)
        backupFolder = Backup.folderLabel(context)
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
}
