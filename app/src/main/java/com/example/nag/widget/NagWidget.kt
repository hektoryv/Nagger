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
import com.example.nag.planner.BlockKind
import com.example.nag.planner.DayShape
import com.example.nag.planner.Placer
import com.example.nag.planner.PlannerOverrides
import com.example.nag.planner.PlannerStore
import com.example.nag.planner.ScheduledBlock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * A plain RemoteViews widget rather than Glance: no extra dependency, and nothing that
 * can break when the Compose version moves. Rows are built one at a time and added to a
 * vertical container, which avoids the whole collection-widget apparatus.
 */
class NagWidget : AppWidgetProvider() {

    companion object {
        private const val MAX_ROWS = 6
        private val WIDGET_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

        // Not read from AppState's color scheme (Compose) — just distinct enough to tell
        // a locked class from a flexible task at a glance, same red/neutral split as the app.
        // Plain val, not const: same reason HabitColorsArgb below isn't const either —
        // .toInt() on a hex literal past Int range isn't guaranteed a constant expression.
        private val EVENT_DOT_COLOR = 0xFFC62828.toInt()
        private val TASK_DOT_COLOR = 0xFF1565C0.toInt()

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
            val schedule = todaySchedule(context, today)

            views.setTextViewText(
                R.id.widget_title,
                when {
                    habits.isEmpty() && schedule.isEmpty() -> "Nothing set up"
                    due.isEmpty() && doneToday.isEmpty() && schedule.isEmpty() -> "Nothing due today"
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

            val scheduleRows = schedule.take(MAX_ROWS)
            val habitRows = (due + doneToday.filterNot { it in due })
                .take((MAX_ROWS - scheduleRows.size).coerceAtLeast(0))

            if (scheduleRows.isEmpty() && habitRows.isEmpty()) {
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_empty, View.GONE)
                scheduleRows.forEach { block -> views.addView(R.id.widget_items, scheduleRow(context, block)) }
                habitRows.forEach { habit ->
                    views.addView(R.id.widget_items, row(context, habit, today, Schedule.doneOn(habit, today, log)))
                }
            }
            return views
        }

        /** Today's classes and tasks, read-only here — no per-instance overrides UI on a widget. */
        private fun todaySchedule(context: Context, today: LocalDate): List<ScheduledBlock> {
            val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val events = PlannerStore.loadEvents(context)
            val tasks = PlannerStore.loadTasks(context)
            val overrides = PlannerStore.loadOverrides(context)
            return Placer.place(weekStart, events, tasks, PlannerOverrides(overrides), DayShape())
                .filter { it.start.toLocalDate() == today }
                .sortedBy { it.start }
        }

        /** Read-only row: no checkbox, tapping just opens the app. */
        private fun scheduleRow(context: Context, block: ScheduledBlock): RemoteViews {
            val row = RemoteViews(context.packageName, R.layout.widget_item)
            row.setTextViewText(
                R.id.item_title,
                "${block.start.toLocalTime().format(WIDGET_TIME_FORMAT)}  ${block.title}"
            )
            row.setViewVisibility(R.id.item_check, View.GONE)
            row.setInt(
                R.id.item_dot,
                "setColorFilter",
                if (block.kind == BlockKind.EVENT) EVENT_DOT_COLOR else TASK_DOT_COLOR
            )
            val pending = PendingIntent.getActivity(
                context,
                ("schedule" + block.occurrenceKey.sourceId + block.start).hashCode(),
                Intent(context, MainActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            row.setOnClickPendingIntent(R.id.item_root, pending)
            return row
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
