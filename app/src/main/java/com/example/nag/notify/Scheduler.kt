package com.example.nag.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.nag.data.Store
import java.time.LocalDateTime
import java.time.ZoneId

object Scheduler {

    const val EXTRA_HABIT_ID = "habitId"
    private const val DAILY_REQUEST_CODE = 1000

    private fun alarmManager(context: Context) =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager(context).canScheduleExactAlarms()

    /** Books the next nightly check-in. Safe to call as often as you like. */
    fun scheduleNightly(context: Context) {
        val (hour, minute) = Store.reminderTime(context)
        val now = LocalDateTime.now()
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)

        val pending = PendingIntent.getBroadcast(
            context,
            DAILY_REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setAlarm(context, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pending)
    }

    /** Asks about one habit again in an hour. */
    fun snooze(context: Context, habitId: String, minutes: Long = 60) {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_HABIT_ID, habitId)
        val pending = PendingIntent.getBroadcast(
            context,
            habitId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setAlarm(context, System.currentTimeMillis() + minutes * 60_000, pending)
    }

    private fun setAlarm(context: Context, triggerAtMillis: Long, pending: PendingIntent) {
        val am = alarmManager(context)
        try {
            if (canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }
}
