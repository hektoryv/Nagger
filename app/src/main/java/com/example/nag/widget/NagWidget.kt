package com.example.nag.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.nag.MainActivity
import com.example.nag.R
import com.example.nag.data.Habit
import com.example.nag.data.Store
import com.example.nag.data.habitColorArgb
import com.example.nag.logic.Schedule
import com.example.nag.notify.ActionReceiver
import com.example.nag.notify.Scheduler
import java.time.LocalDate

/**
 * A plain RemoteViews widget rather than Glance: no extra dependency, and nothing that
 * can break when the Compose version moves. Rows are built one at a time and added to a
 * vertical container, which avoids the whole collection-widget apparatus.
 */
class NagWidget : AppWidgetProvider() {

    companion object {
        private const val MAX_ROWS = 6

        /** Redraw every placed widget. Call after anything that changes what's due. */
        fun refresh(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids = manager.getAppWidgetIds(ComponentName(context, NagWidget::class.java))
                if (ids == null || ids.isEmpty()) return
                ids.forEach { manager.updateAppWidget(it, build(context)) }
            }
        }

        private fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget)
            val today = LocalDate.now()
            val log = Store.loadLog(context)
            val habits = Store.loadHabits(context)

            val due = habits.filter { Schedule.isDueOn(it, today, log) }
            val doneToday = habits.filter { it.active && Schedule.doneOn(it, today, log) }

            views.setTextViewText(
                R.id.widget_title,
                when {
                    habits.isEmpty() -> "Nothing set up"
                    due.isEmpty() && doneToday.isEmpty() -> "Nothing due today"
                    due.isEmpty() -> "All clear today"
                    else -> "${due.size} to do today"
                }
            )

            views.setOnClickPendingIntent(
                R.id.widget_header,
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            views.removeAllViews(R.id.widget_items)

            val rows = (due + doneToday.filterNot { it in due }).take(MAX_ROWS)
            if (rows.isEmpty()) {
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_empty, View.GONE)
                rows.forEach { habit ->
                    views.addView(R.id.widget_items, row(context, habit, today, Schedule.doneOn(habit, today, log)))
                }
            }
            return views
        }

        private fun row(
            context: Context,
            habit: Habit,
            today: LocalDate,
            done: Boolean
        ): RemoteViews {
            val row = RemoteViews(context.packageName, R.layout.widget_item)
            row.setTextViewText(R.id.item_title, habit.title)
            row.setInt(R.id.item_dot, "setColorFilter", habitColorArgb(habit.colorIndex))
            row.setImageViewResource(
                R.id.item_check,
                if (done) R.drawable.ic_check_box else R.drawable.ic_check_box_blank
            )

            // Habits that count something open the app so a number can be typed;
            // plain ones tick straight from the home screen.
            val pending = if (habit.tracksAmount || done) {
                PendingIntent.getActivity(
                    context,
                    ("open" + habit.id).hashCode(),
                    Intent(context, MainActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra(MainActivity.EXTRA_PROMPT_HABIT_ID, habit.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                PendingIntent.getBroadcast(
                    context,
                    ("widget" + habit.id).hashCode(),
                    Intent(context, ActionReceiver::class.java)
                        .setAction(ActionReceiver.ACTION_DONE_NO_AMOUNT)
                        .putExtra(Scheduler.EXTRA_HABIT_ID, habit.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            row.setOnClickPendingIntent(R.id.item_root, pending)
            return row
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, build(context)) }
    }
}
