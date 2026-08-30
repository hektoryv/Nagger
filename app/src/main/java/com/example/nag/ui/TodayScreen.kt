package com.example.nag.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nag.data.Habit
import com.example.nag.data.habitColorArgb
import com.example.nag.logic.Schedule
import com.example.nag.planner.BlockKind
import com.example.nag.planner.ScheduledBlock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    state: AppState,
    today: LocalDate,
    onTick: (Habit) -> Unit,
    onUntick: (Habit) -> Unit,
    onOpen: (Habit) -> Unit
) {
    val log = state.log
    val due = state.habits.filter { Schedule.isDueOn(it, today, log) }
    val doneToday = state.habits.filter {
        it.active && Schedule.doneOn(it, today, log) && it !in due
    }
    val later = state.habits.filter {
        it.active && it !in due && it !in doneToday
    }
    val paused = state.habits.filter { it.paused }
    val finished = state.habits.filter { it.finished }
    val schedule = state.plannerScheduleForDay(today).sortedBy { it.start }
    var selectedBlock by remember { mutableStateOf<ScheduledBlock?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 88.dp)
    ) {
        Card(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    when {
                        state.habits.isEmpty() && schedule.isEmpty() -> "Nothing set up yet"
                        due.isEmpty() && doneToday.isEmpty() -> "Nothing due today"
                        due.isEmpty() -> "All clear for today"
                        else -> "${due.size} to do today"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (state.habits.isEmpty() && schedule.isEmpty())
                        "Tap + to add something like \"do pull-ups\"."
                    else
                        "Check-in at %02d:%02d".format(state.reminder.first, state.reminder.second),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (schedule.isNotEmpty()) {
            SectionLabel("Today's schedule")
            schedule.forEach { block ->
                ScheduledBlockRow(
                    block = block,
                    isDone = block.occurrenceKey in state.plannerCompletions,
                    onToggleDone = if (block.kind == BlockKind.TASK) {
                        { done -> state.setTaskOccurrenceDone(block.occurrenceKey, done) }
                    } else null,
                    onClick = { selectedBlock = block }
                )
                HorizontalDivider()
            }
        }

        if (due.isNotEmpty()) {
            SectionLabel("Due today")
            due.forEach { habit ->
                HabitRow(habit, false, log, today, { onTick(habit) }, { onOpen(habit) })
                HorizontalDivider()
            }
        }

        if (doneToday.isNotEmpty()) {
            SectionLabel("Done today")
            doneToday.forEach { habit ->
                HabitRow(habit, true, log, today, { onUntick(habit) }, { onOpen(habit) })
                HorizontalDivider()
            }
        }

        if (later.isNotEmpty()) {
            SectionLabel("Coming up")
            later.forEach { habit ->
                PlainRow(
                    habit = habit,
                    supporting = "${habit.rhythm()} · ${Schedule.nextUpLabel(habit, today, log)}",
                    struck = false,
                    onClick = { onOpen(habit) }
                )
                HorizontalDivider()
            }
        }

        if (paused.isNotEmpty()) {
            SectionLabel("Paused")
            paused.forEach { habit ->
                PlainRow(habit, "Paused · tap to resume", false) { onOpen(habit) }
                HorizontalDivider()
            }
        }

        if (finished.isNotEmpty()) {
            SectionLabel("Finished")
            finished.forEach { habit ->
                PlainRow(habit, "Done and dusted", true) { onOpen(habit) }
                HorizontalDivider()
            }
        }
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
            } else null
        )
    }
}

@Composable
private fun HabitRow(
    habit: Habit,
    checked: Boolean,
    log: List<com.example.nag.data.Entry>,
    today: LocalDate,
    onToggle: () -> Unit,
    onOpen: () -> Unit
) {
    val amount = Schedule.entryOn(habit, today, log)?.amount
    val streak = Schedule.streak(habit, today, log)

    ListItem(
        modifier = Modifier.clickable { onOpen() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
        },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(habit)
                Spacer(Modifier.size(8.dp))
                Text(
                    habit.title,
                    textDecoration = if (checked) TextDecoration.LineThrough else null
                )
            }
        },
        supportingContent = {
            val bits = buildList {
                add(habit.rhythm())
                if (checked && amount != null) add("$amount ${habit.unit}")
                if (streak > 1) add("$streak ${habit.streakUnit(streak)}")
            }
            Text(bits.joinToString(" · "))
        }
    )
}

@Composable
private fun PlainRow(
    habit: Habit,
    supporting: String,
    struck: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { ColorDot(habit) },
        headlineContent = {
            Text(
                habit.title,
                textDecoration = if (struck) TextDecoration.LineThrough else null
            )
        },
        supportingContent = { Text(supporting) }
    )
}

private val ROW_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun ScheduledBlockRow(
    block: ScheduledBlock,
    isDone: Boolean,
    onToggleDone: ((Boolean) -> Unit)?,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = if (onToggleDone != null) {
            { Checkbox(checked = isDone, onCheckedChange = onToggleDone) }
        } else null,
        headlineContent = {
            Text(block.title, textDecoration = if (isDone) TextDecoration.LineThrough else null)
        },
        supportingContent = {
            val kind = if (block.kind == BlockKind.EVENT) "Class" else "Task"
            val bits = buildList {
                add("$kind · ${block.start.format(ROW_TIME_FORMAT)} – ${block.end.format(ROW_TIME_FORMAT)}")
                block.deadline?.let { add("due $it") }
            }
            Text(bits.joinToString(" · "))
        }
    )
}

@Composable
fun ColorDot(habit: Habit, size: Int = 10) {
    Box(
        Modifier
            .size(size.dp)
            .background(Color(habitColorArgb(habit.colorIndex)), CircleShape)
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp)
    )
}
