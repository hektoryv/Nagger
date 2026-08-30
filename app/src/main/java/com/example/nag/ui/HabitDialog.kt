package com.example.nag.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.nag.data.Habit
import com.example.nag.data.HabitColorsArgb
import com.example.nag.data.MissPolicy
import com.example.nag.data.ScheduleKind
import com.example.nag.data.habitColorArgb
import java.time.DayOfWeek
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDialog(
    existing: Habit?,
    defaultColorIndex: Int,
    onDismiss: () -> Unit,
    onSave: (Habit) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val today = LocalDate.now()
    var confirmingDelete by remember { mutableStateOf(false) }

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var kind by remember { mutableStateOf(existing?.kind ?: ScheduleKind.EVERY_N_DAYS) }
    var interval by remember { mutableStateOf((existing?.intervalDays ?: 1).toString()) }
    var weekdays by remember {
        mutableStateOf(existing?.weekdays ?: setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
    }
    var timesPerWeek by remember { mutableStateOf(existing?.timesPerWeek ?: 3) }
    var startsTomorrow by remember { mutableStateOf(false) }
    var rollOver by remember {
        mutableStateOf((existing?.missPolicy ?: MissPolicy.ROLL_OVER) == MissPolicy.ROLL_OVER)
    }
    var tracksAmount by remember { mutableStateOf(existing?.tracksAmount ?: false) }
    var unit by remember { mutableStateOf(existing?.unit ?: "reps") }
    var colorIndex by remember { mutableStateOf(existing?.colorIndex ?: defaultColorIndex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New check-in" else "Edit check-in") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("What should I ask about?") },
                    placeholder = { Text("do pull-ups") }
                )

                Spacer(Modifier.height(14.dp))
                Text("How often", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = kind == ScheduleKind.EVERY_N_DAYS,
                            onClick = { kind = ScheduleKind.EVERY_N_DAYS },
                            label = { Text("Every N days") }
                        )
                        FilterChip(
                            selected = kind == ScheduleKind.WEEKDAYS,
                            onClick = { kind = ScheduleKind.WEEKDAYS },
                            label = { Text("Weekdays") }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = kind == ScheduleKind.TIMES_PER_WEEK,
                            onClick = { kind = ScheduleKind.TIMES_PER_WEEK },
                            label = { Text("N× a week") }
                        )
                        FilterChip(
                            selected = kind == ScheduleKind.UNTIL_DONE,
                            onClick = { kind = ScheduleKind.UNTIL_DONE },
                            label = { Text("Until done") }
                        )
                    }
                }

                when (kind) {
                    ScheduleKind.EVERY_N_DAYS -> {
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = interval,
                            onValueChange = { new -> interval = new.filter { it.isDigit() }.take(3) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            label = { Text("Ask every … days") },
                            supportingText = { Text("1 = every day, 2 = every other day") }
                        )
                        if (existing == null) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !startsTomorrow,
                                    onClick = { startsTomorrow = false },
                                    label = { Text("Starts today") }
                                )
                                FilterChip(
                                    selected = startsTomorrow,
                                    onClick = { startsTomorrow = true },
                                    label = { Text("Starts tomorrow") }
                                )
                            }
                            Text(
                                "Stagger a pair of every-other-day habits by starting one tomorrow.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    ScheduleKind.WEEKDAYS -> {
                        Spacer(Modifier.height(14.dp))
                        Text("Which days", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            DayOfWeek.values().forEach { day ->
                                val on = day in weekdays
                                FilterChip(
                                    selected = on,
                                    onClick = {
                                        weekdays = if (on) weekdays - day else weekdays + day
                                    },
                                    label = { Text(day.name.take(1)) }
                                )
                            }
                        }
                    }

                    ScheduleKind.TIMES_PER_WEEK -> {
                        Spacer(Modifier.height(14.dp))
                        Text("Times per week", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            (1..7).forEach { n ->
                                FilterChip(
                                    selected = timesPerWeek == n,
                                    onClick = { timesPerWeek = n },
                                    label = { Text("$n") }
                                )
                            }
                        }
                        Text(
                            "Any days you like, counted Monday to Sunday.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    ScheduleKind.UNTIL_DONE -> {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Asked every night, and the notification stays put until you say it's done.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (kind != ScheduleKind.UNTIL_DONE) {
                    Spacer(Modifier.height(14.dp))
                    SwitchRow(
                        label = "Keep asking if I miss it",
                        supporting = when {
                            rollOver && kind == ScheduleKind.TIMES_PER_WEEK ->
                                "Asks every night until the week's target is met"

                            rollOver -> "Nags every night until it's done"
                            kind == ScheduleKind.TIMES_PER_WEEK ->
                                "Only speaks up when you'd have to go every remaining day"

                            else -> "Marks the day missed and waits for the next one"
                        },
                        checked = rollOver,
                        onChange = { rollOver = it }
                    )
                }

                Spacer(Modifier.height(14.dp))
                SwitchRow(
                    label = "Count an amount",
                    supporting = if (tracksAmount) "Type the number into the notification"
                    else "Just yes or no",
                    checked = tracksAmount,
                    onChange = { tracksAmount = it }
                )

                if (tracksAmount) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it.take(16) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Counting what?") },
                        placeholder = { Text("reps") }
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text("Colour on the calendar", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HabitColorsArgb.indices.forEach { i ->
                        val selected = i == colorIndex.mod(HabitColorsArgb.size)
                        Box(
                            Modifier
                                .size(if (selected) 32.dp else 24.dp)
                                .background(Color(habitColorArgb(i)), CircleShape)
                                .clickable { colorIndex = i }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() &&
                    (kind != ScheduleKind.WEEKDAYS || weekdays.isNotEmpty()),
                onClick = {
                    val base = existing ?: Habit(title = title.trim())
                    onSave(
                        base.copy(
                            title = title.trim(),
                            kind = kind,
                            intervalDays = interval.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                            weekdays = weekdays,
                            timesPerWeek = timesPerWeek,
                            startDate = existing?.startDate
                                ?: if (startsTomorrow) today.plusDays(1) else today,
                            missPolicy = if (rollOver) MissPolicy.ROLL_OVER else MissPolicy.SKIP,
                            colorIndex = colorIndex,
                            tracksAmount = tracksAmount,
                            unit = unit.trim().ifEmpty { "reps" }
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = { confirmingDelete = true }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )

    if (confirmingDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete ${existing?.title ?: title}?") },
            text = { Text("Its history goes too. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    onDelete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Keep it") }
            }
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    supporting: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(supporting, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Small number prompt shown when ticking off a habit that counts something. */
@Composable
fun AmountDialog(
    habit: Habit,
    initial: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit
) {
    var text by remember { mutableStateOf(initial?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(habit.title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter { it.isDigit() }.take(6) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("How many ${habit.unit}?") }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Leave it blank to just mark it done.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.toIntOrNull()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** The yes/no prompt you land on when tapping a notification or a widget row. */
@Composable
fun PromptDialog(
    habit: Habit,
    lastAmount: Int?,
    onDismiss: () -> Unit,
    onDone: (Int?) -> Unit,
    onNotYet: () -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Did you ${habit.title.replaceFirstChar { it.lowercase() }}?") },
        text = {
            if (habit.tracksAmount) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter { it.isDigit() }.take(6) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("How many ${habit.unit}?") },
                    placeholder = { Text(lastAmount?.toString() ?: "") }
                )
            } else {
                Text(habit.rhythm())
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(text.toIntOrNull()) }) { Text("Yes, done") }
        },
        dismissButton = { TextButton(onClick = onNotYet) { Text("Not yet") } }
    )
}
