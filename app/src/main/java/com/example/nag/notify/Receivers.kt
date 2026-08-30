package com.example.nag.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.example.nag.data.Backup
import com.example.nag.data.Store
import com.example.nag.logic.Schedule
import com.example.nag.widget.NagWidget
import java.time.LocalDate

/**
 * Fires at the nightly check-in time, or an hour after you tap "Not yet".
 * If it carries a habit id it only asks about that one.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Notifications.createChannel(context)
        val today = LocalDate.now()
        val log = Store.loadLog(context)
        val single = intent.getStringExtra(Scheduler.EXTRA_HABIT_ID)

        if (single != null) {
            Store.findHabit(context, single)
                ?.takeIf { Schedule.isDueOn(it, today, log) }
                ?.let { Notifications.show(context, it) }
            return
        }

        Store.loadHabits(context)
            .filter { Schedule.isDueOn(it, today, log) }
            .forEach { Notifications.show(context, it) }

        NagWidget.refresh(context)
        Scheduler.scheduleNightly(context)
    }
}

/** Handles the notification buttons, including the number typed inline. */
class ActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DONE = "com.example.nag.DONE"
        const val ACTION_DONE_NO_AMOUNT = "com.example.nag.DONE_NO_AMOUNT"
        const val ACTION_SNOOZE = "com.example.nag.SNOOZE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(Scheduler.EXTRA_HABIT_ID) ?: return
        val today = LocalDate.now()

        when (intent.action) {
            ACTION_DONE -> {
                val typed = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(Notifications.KEY_AMOUNT)
                    ?.toString()
                    ?.filter { it.isDigit() }
                Store.setDone(context, id, today, typed?.toIntOrNull())
            }

            ACTION_DONE_NO_AMOUNT -> Store.setDone(context, id, today, null)

            ACTION_SNOOZE -> Scheduler.snooze(context, id)
        }

        Notifications.cancel(context, id)
        NagWidget.refresh(context)
        Backup.auto(context)
    }
}

/** Alarms are wiped on reboot, so book the next one again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.scheduleNightly(context)
        NagWidget.refresh(context)
    }
}
