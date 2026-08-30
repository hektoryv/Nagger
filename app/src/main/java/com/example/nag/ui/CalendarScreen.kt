package com.example.nag.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nag.data.DayStatus
import com.example.nag.data.Habit
import com.example.nag.data.habitColorArgb
import com.example.nag.logic.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    state: AppState,
    today: LocalDate,
    onSetDone: (Habit, LocalDate, Int?) -> Unit,
    onClearDone: (Habit, LocalDate) -> Unit,
    onOpen: (Habit) -> Unit
) {
    var weekStart by remember {
        mutableStateOf(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
    }
    var selected by remember { mutableStateOf<LocalDate?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 88.dp)
    ) {
        StatsStrip(state, today, onOpen)

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { weekStart = weekStart.minusWeeks(1) }) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous week")
            }
            Text(
                "Week of ${weekStart.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} " +
                    "${weekStart.dayOfMonth}, ${weekStart.year}",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { weekStart = weekStart.plusWeeks(1) }) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next week")
            }
        }

        WeekGrid(state, weekStart, today) { selected = it }
        Legend()
    }

    val day = selected
    if (day != null) {
        DayDetailDialog(state, day, today, { selected = null }, onSetDone, onClearDone)
    }
}

@Composable
private fun StatsStrip(state: AppState, today: LocalDate, onOpen: (Habit) -> Unit) {
    val log = state.log
    val active = state.habits.filter { it.active }
    val stats = active.map { it to Schedule.statsFor(it, today, log) }
    val done = stats.sumOf { it.second.done }
    val scheduled = stats.sumOf { it.second.scheduled }
    val percent = if (scheduled == 0) 0 else ((done * 100) / scheduled).coerceAtMost(100)
    val perfect = Schedule.perfectDayStreak(active, today, log)

    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$percent%",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "of the last 30 days",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(6.dp))
            Text(
                if (perfect > 0) "$perfect perfect ${if (perfect == 1) "day" else "days"} in a row"
                else "No clean streak going right now",
                style = MaterialTheme.typography.bodySmall
            )

            if (stats.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                stats.forEach { (habit, s) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(habit) }
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ColorDot(habit)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(habit.title, style = MaterialTheme.typography.bodyMedium)
                            val extra = buildList {
                                add("${s.done}/${s.scheduled} done")
                                if (s.streak > 0) add("streak ${s.streak}")
                                if (habit.tracksAmount && s.total > 0) add("${s.total} ${habit.unit}")
                            }
                            Text(extra.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "${s.percent}%",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekGrid(
    state: AppState,
    weekStart: LocalDate,
    today: LocalDate,
    onDayClick: (LocalDate) -> Unit
) {
    Column(Modifier.padding(horizontal = 6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        Row(Modifier.fillMaxWidth()) {
            repeat(7) { offset ->
                DayCell(state, weekStart.plusDays(offset.toLong()), today, Modifier.weight(1f), onDayClick)
            }
        }
    }
}

@Composable
private fun DayCell(
    state: AppState,
    date: LocalDate,
    today: LocalDate,
    modifier: Modifier,
    onClick: (LocalDate) -> Unit
) {
    val isToday = date == today
    val statuses = state.habits
        .map { it to Schedule.statusOn(it, date, today, state.log) }
        .filter { it.second != DayStatus.NONE }

    Column(
        modifier
            .padding(2.dp)
            .then(
                if (isToday) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .clickable { onClick(date) }
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(Modifier.height(3.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.height(8.dp)
        ) {
            statuses.take(6).forEach { (habit, status) ->
                Box(
                    Modifier
                        .width(4.dp)
                        .height(8.dp)
                        .background(barColor(habit, status), RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

@Composable
private fun barColor(habit: Habit, status: DayStatus): Color {
    val base = Color(habitColorArgb(habit.colorIndex))
    return when (status) {
        DayStatus.DONE -> base
        DayStatus.DUE -> base.copy(alpha = 0.55f)
        DayStatus.UPCOMING -> base.copy(alpha = 0.22f)
        DayStatus.PENDING -> base.copy(alpha = 0.35f)
        DayStatus.MISSED -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
        DayStatus.NONE -> Color.Transparent
    }
}

@Composable
private fun Legend() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LegendItem("Done", MaterialTheme.colorScheme.primary)
        LegendItem("Due", MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        LegendItem("Ahead", MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
        LegendItem("Missed", MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f))
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(4.dp)
                .height(10.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DayDetailDialog(
    state: AppState,
    date: LocalDate,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSetDone: (Habit, LocalDate, Int?) -> Unit,
    onClearDone: (Habit, LocalDate) -> Unit
) {
    val relevant = state.habits.filter {
        Schedule.statusOn(it, date, today, state.log) != DayStatus.NONE
    }
    val future = date.isAfter(today)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.toString()) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (relevant.isEmpty()) {
                    Text("Nothing scheduled.")
                } else {
                    relevant.forEach { habit ->
                        val status = Schedule.statusOn(habit, date, today, state.log)
                        val entry = Schedule.entryOn(habit, date, state.log)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = status == DayStatus.DONE,
                                enabled = !future,
                                onCheckedChange = { checked ->
                                    if (checked) onSetDone(habit, date, null)
                                    else onClearDone(habit, date)
                                }
                            )
                            ColorDot(habit)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(habit.title, style = MaterialTheme.typography.bodyMedium)
                                val label = when (status) {
                                    DayStatus.DONE -> if (entry?.amount != null)
                                        "Done · ${entry.amount} ${habit.unit}" else "Done"
                                    DayStatus.MISSED -> "Missed"
                                    DayStatus.DUE -> "Due today"
                                    DayStatus.UPCOMING -> "Scheduled"
                                    DayStatus.PENDING -> "Still waiting"
                                    DayStatus.NONE -> ""
                                }
                                Text(label, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (future) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Can't tick off a day that hasn't happened yet.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
