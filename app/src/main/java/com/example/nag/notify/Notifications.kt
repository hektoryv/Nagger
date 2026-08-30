package com.example.nag.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.nag.MainActivity
import com.example.nag.R
import com.example.nag.data.Habit
import com.example.nag.data.ScheduleKind
import com.example.nag.data.habitColorArgb

object Notifications {

    const val CHANNEL_ID = "checkins"
    const val KEY_AMOUNT = "amount_text"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Nightly check-in",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Asks whether you did the thing"
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun show(context: Context, habit: Habit) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(habitColorArgb(habit.colorIndex))
            .setContentTitle("Did you ${habit.title.replaceFirstChar { it.lowercase() }}?")
            .setContentText(
                if (habit.kind == ScheduleKind.UNTIL_DONE) "Still waiting on this one"
                else habit.rhythm()
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // Tapping the body opens a straight yes/no prompt for this habit.
            .setContentIntent(openPromptIntent(context, habit))
            .setAutoCancel(true)

        if (habit.tracksAmount) {
            // An inline text field right in the shade: type "12", hit send, done.
            val amountInput = RemoteInput.Builder(KEY_AMOUNT)
                .setLabel("How many ${habit.unit}?")
                .build()
            builder.addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_check,
                    "Log ${habit.unit}",
                    actionIntent(context, habit.id, ActionReceiver.ACTION_DONE, mutable = true)
                ).addRemoteInput(amountInput)
                    .setAllowGeneratedReplies(false)
                    .build()
            )
            builder.addAction(
                R.drawable.ic_check,
                "Done",
                actionIntent(context, habit.id, ActionReceiver.ACTION_DONE_NO_AMOUNT)
            )
        } else {
            builder.addAction(
                R.drawable.ic_check,
                "Yes, done",
                actionIntent(context, habit.id, ActionReceiver.ACTION_DONE)
            )
        }

        builder.addAction(
            R.drawable.ic_later,
            "Not yet",
            actionIntent(context, habit.id, ActionReceiver.ACTION_SNOOZE)
        )

        // The "until done" ones stay pinned in the shade so they can't be swiped away.
        if (habit.kind == ScheduleKind.UNTIL_DONE) builder.setOngoing(true)

        runCatching {
            NotificationManagerCompat.from(context).notify(habit.id.hashCode(), builder.build())
        }
    }

    fun cancel(context: Context, habitId: String) {
        NotificationManagerCompat.from(context).cancel(habitId.hashCode())
    }

    fun openPromptIntent(context: Context, habit: Habit): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_PROMPT_HABIT_ID, habit.id)
        return PendingIntent.getActivity(
            context,
            habit.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionIntent(
        context: Context,
        habitId: String,
        action: String,
        mutable: Boolean = false
    ): PendingIntent {
        val intent = Intent(context, ActionReceiver::class.java)
            .setAction(action)
            .putExtra(Scheduler.EXTRA_HABIT_ID, habitId)
        // A RemoteInput action must be mutable so the system can attach what you typed.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, (action + habitId).hashCode(), intent, flags)
    }
}
