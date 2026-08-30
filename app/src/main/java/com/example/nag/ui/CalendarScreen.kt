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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nag.data.DayStatus
import com.example.nag.data.Habit
import com.example.nag.data.habitColorArgb
import com.example.nag.logic.Schedule
import com.example.nag.planner.BlockKind
import com.example.nag.planner.LockState
import com.example.nag.planner.ScheduledBlock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
    var selectedBlock by remember { mutableStateOf<ScheduledBlock?>(null) }

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

        WeekGrid(state, weekStart, today, onDayClick = { selected = it }, onBlockClick = { selectedBlock = it })
        Legend()
    }

    val day = selected
    if (day != null) {
        DayDetailDialog(state, day, today, { selected = null }, onSetDone, onClearDone)
    }

    val block = selectedBlock
    if (block != null) {
        ScheduledBlockDialog(
            block = block,
            onDismiss = { selectedBlock = null },
            onDelete = if (block.kind == BlockKind.TASK) {
                {
                    state.deleteTask(block.occurrenceKey.sourceId)
                    selectedBlock = null
                }
            } else null
        )
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

// Timeline covers 06:00-24:00. Most class/task activity for a student falls in this
// window; anything before it would be an unusual edge case not worth the extra height.
private const val TIMELINE_START_HOUR = 6
private const val TIMELINE_END_HOUR = 24
private val HOUR_HEIGHT = 26.dp
private val HOUR_GUTTER_WIDTH = 20.dp

@Composable
private fun WeekGrid(
    state: AppState,
    weekStart: LocalDate,
    today: LocalDate,
    onDayClick: (LocalDate) -> Unit,
    onBlockClick: (ScheduledBlock) -> Unit
) {
    val schedule = remember(state.plannerEvents, state.plannerTasks, weekStart) { state.plannerSchedule(weekStart) }

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

        Spacer(Modifier.height(4.dp))

        Row(Modifier.fillMaxWidth()) {
            HourGutter()
            repeat(7) { offset ->
                val date = weekStart.plusDays(offset.toLong())
                DayTimelineColumn(
                    blocks = schedule.filter { it.start.toLocalDate() == date },
                    onBlockClick = onBlockClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HourGutter() {
    val totalHours = TIMELINE_END_HOUR - TIMELINE_START_HOUR
    Box(Modifier.width(HOUR_GUTTER_WIDTH).height(HOUR_HEIGHT * totalHours)) {
        for (hour in TIMELINE_START_HOUR until TIMELINE_END_HOUR step 3) {
            Text(
                hour.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.offset(y = HOUR_HEIGHT * (hour - TIMELINE_START_HOUR) - 5.dp)
            )
        }
    }
}

@Composable
private fun DayTimelineColumn(
    blocks: List<ScheduledBlock>,
    onBlockClick: (ScheduledBlock) -> Unit,
    modifier: Modifier
) {
    val totalHours = TIMELINE_END_HOUR - TIMELINE_START_HOUR
    val lineColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier
            .fillMaxWidth()
            .height(HOUR_HEIGHT * totalHours)
            .drawBehind {
                val hourPx = HOUR_HEIGHT.toPx()
                for (hour in 0..totalHours) {
                    val y = hour * hourPx
                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
            }
    ) {
        blocks.forEach { block -> ScheduledBlockView(block, onClick = { onBlockClick(block) }) }
    }
}

@Composable
private fun ScheduledBlockView(block: ScheduledBlock, onClick: () -> Unit) {
    val startMinutes = ((block.start.hour - TIMELINE_START_HOUR) * 60 + block.start.minute)
        .coerceIn(0, (TIMELINE_END_HOUR - TIMELINE_START_HOUR) * 60)
    val top = HOUR_HEIGHT * (startMinutes / 60f)
    val durationHours = (Duration.between(block.start, block.end).toMinutes() / 60f).coerceAtLeast(0.25f)
    val blockHeight = HOUR_HEIGHT * durationHours
    val borderColor = if (block.lockState == LockState.LOCKED)
        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline

    Box(
        Modifier
            .fillMaxWidth()
            .height(blockHeight)
            .offset(y = top)
            .padding(horizontal = 1.dp, vertical = 0.5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .border(1.dp, borderColor, RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 1.dp)
    ) {
        Text(
            block.title,
            fontSize = 8.sp,
            lineHeight = 9.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
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

private val BLOCK_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val BLOCK_DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d")

/** Everything known about one class or task, shown in full on tap rather than picked over. */
@Composable
private fun ScheduledBlockDialog(
    block: ScheduledBlock,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(block.title) },
        text = {
            Column {
                BlockInfoRow("Type", if (block.kind == BlockKind.EVENT) "Class" else "Task")
                BlockInfoRow("Day", block.start.toLocalDate().format(BLOCK_DAY_FORMAT))
                BlockInfoRow(
                    "Time",
                    "${block.start.format(BLOCK_TIME_FORMAT)} – ${block.end.format(BLOCK_TIME_FORMAT)}"
                )
                BlockInfoRow("Duration", formatBlockDuration(Duration.between(block.start, block.end)))
                block.location?.let { BlockInfoRow("Location", it) }
                BlockInfoRow(
                    "Status",
                    when (block.lockState) {
                        LockState.LOCKED -> "Locked — won't move on replan"
                        LockState.FLEXIBLE -> "Flexible — the planner can move this"
                        LockState.SKIPPED -> "Skipped"
                    }
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = onDelete?.let { delete ->
            { TextButton(onClick = delete) { Text("Delete task") } }
        }
    )
}

@Composable
private fun BlockInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatBlockDuration(duration: Duration): String {
    val totalMinutes = duration.toMinutes()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0L -> "${minutes}m"
        minutes == 0L -> "${hours}h"
        else -> "${hours}h ${minutes}m"
    }
}
