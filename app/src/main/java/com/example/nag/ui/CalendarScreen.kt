package com.example.nag.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nag.data.DayStatus
import com.example.nag.data.Habit
import com.example.nag.data.habitColorArgb
import com.example.nag.logic.Schedule
import com.example.nag.planner.BlockKind
import com.example.nag.planner.DayShape
import com.example.nag.planner.LockState
import com.example.nag.planner.OccurrenceKey
import com.example.nag.planner.PlannerTask
import com.example.nag.planner.ScheduledBlock
import com.example.nag.planner.TaskRecurrence
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

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
    var editingTask by remember { mutableStateOf<PlannerTask?>(null) }

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
            } else null,
            onSetLockState = if (block.kind == BlockKind.EVENT) {
                { lockState ->
                    if (lockState == null) state.clearOverride(block.occurrenceKey)
                    else state.setOverride(block.occurrenceKey, lockState)
                    selectedBlock = null
                }
            } else null,
            isDone = block.occurrenceKey in state.plannerCompletions,
            onToggleDone = if (block.kind == BlockKind.TASK) {
                {
                    state.setTaskOccurrenceDone(block.occurrenceKey, block.occurrenceKey !in state.plannerCompletions)
                    selectedBlock = null
                }
            } else null,
            onMove = if (isMovableTask(state, block)) {
                { newStart ->
                    state.moveTaskAssignment(block.occurrenceKey.sourceId, newStart)
                    selectedBlock = null
                }
            } else null,
            onEdit = if (block.kind == BlockKind.TASK) {
                {
                    editingTask = state.plannerTasks.firstOrNull { it.id == block.occurrenceKey.sourceId }
                    selectedBlock = null
                }
            } else null
        )
    }

    val editing = editingTask
    if (editing != null) {
        TaskDialog(
            existing = editing,
            onDismiss = { editingTask = null },
            onSave = {
                editingTask = null
                state.upsertTask(it)
            },
            onDelete = {
                state.deleteTask(editing.id)
                editingTask = null
            }
        )
    }
}

/**
 * Only a one-off task has a persisted placement to move — a recurring task's
 * occurrence is recomputed fresh every week, so there's nothing to pin yet.
 */
private fun isMovableTask(state: AppState, block: ScheduledBlock): Boolean =
    block.kind == BlockKind.TASK &&
        state.plannerTasks.firstOrNull { it.id == block.occurrenceKey.sourceId }?.recurrence == TaskRecurrence.None

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
    val schedule = remember(
        state.plannerEvents, state.plannerTasks, state.plannerOverrides, state.plannerTaskAssignments,
        state.dayShape, weekStart, today
    ) {
        state.plannerSchedule(weekStart, today)
    }

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
            WeekTimeline(
                weekStart = weekStart,
                schedule = schedule,
                completions = state.plannerCompletions,
                dayShape = state.dayShape,
                onBlockClick = onBlockClick,
                isMovable = { block -> isMovableTask(state, block) },
                onBlockMove = { block, newStart -> state.moveTaskAssignment(block.occurrenceKey.sourceId, newStart) },
                modifier = Modifier.weight(1f)
            )
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

/**
 * All seven days drawn on one shared canvas rather than as seven independent columns,
 * so a held block can be dragged both up/down (a new time) and left/right (a new day)
 * instead of being confined to the column it started in.
 */
@Composable
private fun WeekTimeline(
    weekStart: LocalDate,
    schedule: List<ScheduledBlock>,
    completions: Set<OccurrenceKey>,
    dayShape: DayShape,
    onBlockClick: (ScheduledBlock) -> Unit,
    isMovable: (ScheduledBlock) -> Boolean,
    onBlockMove: (ScheduledBlock, LocalDateTime) -> Unit,
    modifier: Modifier
) {
    val totalHours = TIMELINE_END_HOUR - TIMELINE_START_HOUR
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val dayBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val dinnerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
    val windDownColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(HOUR_HEIGHT * totalHours)
    ) {
        val columnWidthPx = with(density) { maxWidth.toPx() } / 7f

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val hourPx = HOUR_HEIGHT.toPx()
                    fun yFor(time: LocalTime): Float =
                        ((time.hour - TIMELINE_START_HOUR) * 60 + time.minute) / 60f * hourPx

                    for (day in 0 until 7) {
                        val x = day * columnWidthPx
                        drawRect(
                            dinnerColor,
                            topLeft = Offset(x, yFor(dayShape.dinnerStart)),
                            size = Size(columnWidthPx, yFor(dayShape.dinnerEnd) - yFor(dayShape.dinnerStart))
                        )
                        drawRect(
                            windDownColor,
                            topLeft = Offset(x, yFor(dayShape.windDownStart)),
                            size = Size(columnWidthPx, size.height - yFor(dayShape.windDownStart))
                        )
                    }
                    for (hour in 0..totalHours) {
                        val y = hour * hourPx
                        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    }
                    for (day in 1 until 7) {
                        val x = day * columnWidthPx
                        drawLine(dayBorderColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    }
                }
        )

        schedule.forEach { block ->
            val dayIndex = (block.start.toLocalDate().toEpochDay() - weekStart.toEpochDay()).toInt()
            if (dayIndex in 0..6) {
                // Keyed by identity, not list position: `schedule` is sorted by start
                // time, so a move can reorder it on every recomposition. Without this,
                // Compose tracks each child by its slot in the list rather than which
                // block it actually is, which is what made a moved block intermittently
                // render with another block's stale position/drag state or vanish.
                key(block.occurrenceKey) {
                    ScheduledBlockView(
                        block = block,
                        dayIndex = dayIndex,
                        columnWidthPx = columnWidthPx,
                        isDone = block.occurrenceKey in completions,
                        isMovable = isMovable(block),
                        onClick = { onBlockClick(block) },
                        onMove = { newStart -> onBlockMove(block, newStart) }
                    )
                }
            }
        }
    }
}

/**
 * Hold-and-drag to move a flexible task: a normal tap still opens the detail dialog,
 * but a long-press-then-drag (only wired up for movable blocks — see [isMovableTask])
 * slides the block anywhere in the week grid — a new time, a new day, or both —
 * snapping to the nearest 15 minutes and nearest day column on release. Dims and
 * jiggles side to side while dragging, like an app icon in jiggle mode, and goes
 * still the moment it's dropped or the drag is cancelled.
 */
@Composable
private fun ScheduledBlockView(
    block: ScheduledBlock,
    dayIndex: Int,
    columnWidthPx: Float,
    isDone: Boolean,
    isMovable: Boolean,
    onClick: () -> Unit,
    onMove: (LocalDateTime) -> Unit
) {
    val density = LocalDensity.current
    val totalMinutes = (TIMELINE_END_HOUR - TIMELINE_START_HOUR) * 60
    val startMinutes = ((block.start.hour - TIMELINE_START_HOUR) * 60 + block.start.minute)
        .coerceIn(0, totalMinutes)
    val durationMinutes = Duration.between(block.start, block.end).toMinutes().toInt()
    val durationHours = (durationMinutes / 60f).coerceAtLeast(0.25f)
    val blockHeight = HOUR_HEIGHT * durationHours
    val borderColor = if (block.lockState == LockState.LOCKED)
        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline

    val hourPx = with(density) { HOUR_HEIGHT.toPx() }
    val topPx = startMinutes / 60f * hourPx
    val leftPx = dayIndex * columnWidthPx
    val blockHeightPx = with(density) { blockHeight.toPx() }
    val timelineHeightPx = totalMinutes / 60f * hourPx
    val maxTopPx = (timelineHeightPx - blockHeightPx).coerceAtLeast(0f)
    val maxLeftPx = (6 * columnWidthPx).coerceAtLeast(0f)

    var dragOffsetX by remember(block.occurrenceKey) { mutableStateOf(0f) }
    var dragOffsetY by remember(block.occurrenceKey) { mutableStateOf(0f) }
    var dragging by remember(block.occurrenceKey) { mutableStateOf(false) }

    // A small held-down wiggle, like an app icon in jiggle mode: shakes while dragging,
    // settles back to still the moment it's dropped or the drag is cancelled.
    val shakeAngle = remember(block.occurrenceKey) { Animatable(0f) }
    LaunchedEffect(dragging) {
        if (dragging) {
            while (true) {
                shakeAngle.animateTo(2.5f, tween(90, easing = LinearEasing))
                shakeAngle.animateTo(-2.5f, tween(90, easing = LinearEasing))
            }
        } else {
            shakeAngle.animateTo(0f, tween(100))
        }
    }

    Box(
        Modifier
            .width(with(density) { columnWidthPx.toDp() })
            .height(blockHeight)
            .offset { IntOffset((leftPx + dragOffsetX).roundToInt(), (topPx + dragOffsetY).roundToInt()) }
            .rotate(shakeAngle.value)
            .padding(horizontal = 1.dp, vertical = 0.5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .border(1.dp, borderColor, RoundedCornerShape(3.dp))
            .alpha(if (dragging) 0.7f else 1f)
            .clickable(onClick = onClick)
            .then(
                if (isMovable) {
                    Modifier.pointerInput(block.occurrenceKey) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragging = true },
                            onDragEnd = {
                                dragging = false
                                val draggedMinutes = (dragOffsetY / hourPx * 60f).roundToInt()
                                val snappedMinutes = (draggedMinutes / 15f).roundToInt() * 15
                                val minMinuteOfDay = TIMELINE_START_HOUR * 60
                                val maxMinuteOfDay = (TIMELINE_END_HOUR * 60 - durationMinutes)
                                    .coerceAtLeast(minMinuteOfDay)
                                val actualStartMinuteOfDay = block.start.hour * 60 + block.start.minute
                                val proposedMinute = actualStartMinuteOfDay + snappedMinutes
                                val clampedMinute = proposedMinute.coerceIn(minMinuteOfDay, maxMinuteOfDay)

                                val draggedDays = (dragOffsetX / columnWidthPx).roundToInt()
                                val newDayIndex = (dayIndex + draggedDays).coerceIn(0, 6)
                                val newDate = block.start.toLocalDate().plusDays((newDayIndex - dayIndex).toLong())

                                dragOffsetX = 0f
                                dragOffsetY = 0f
                                onMove(LocalDateTime.of(newDate, LocalTime.of(clampedMinute / 60, clampedMinute % 60)))
                            },
                            onDragCancel = {
                                dragging = false
                                dragOffsetX = 0f
                                dragOffsetY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetX = (dragOffsetX + dragAmount.x).coerceIn(-leftPx, maxLeftPx - leftPx)
                                dragOffsetY = (dragOffsetY + dragAmount.y).coerceIn(-topPx, maxTopPx - topPx)
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 2.dp, vertical = 1.dp)
    ) {
        Text(
            block.title,
            fontSize = 8.sp,
            lineHeight = 9.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (isDone) TextDecoration.LineThrough else null,
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendItem("Done", MaterialTheme.colorScheme.primary)
            LegendItem("Due", MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
            LegendItem("Ahead", MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
            LegendItem("Missed", MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendItem("Locked", MaterialTheme.colorScheme.error)
            LegendItem("Flexible", MaterialTheme.colorScheme.outline)
            LegendItem("Dinner", MaterialTheme.colorScheme.tertiaryContainer)
            LegendItem("Wind-down", MaterialTheme.colorScheme.surfaceVariant)
        }
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
