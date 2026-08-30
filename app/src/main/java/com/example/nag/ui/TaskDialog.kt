package com.example.nag.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.nag.planner.PlannerTask
import com.example.nag.planner.TaskRecurrence
import java.time.LocalDate

private enum class RepeatChoice { ONCE, DAILY, TIMES_PER_WEEK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDialog(
    onDismiss: () -> Unit,
    onSave: (PlannerTask) -> Unit
) {
    val context = LocalContext.current

    var title by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("1") }
    var minutes by remember { mutableStateOf("0") }
    var repeatKind by remember { mutableStateOf(RepeatChoice.ONCE) }
    var timesPerWeek by remember { mutableStateOf(2) }
    var hasDeadline by remember { mutableStateOf(false) }
    var deadline by remember { mutableStateOf<LocalDate?>(null) }

    val durationMinutes = (hours.toIntOrNull() ?: 0) * 60 + (minutes.toIntOrNull() ?: 0)
    val canSave = title.isNotBlank() && durationMinutes > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New task") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("What is it") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))
                Text("About how long does it take?", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { hours = it.filter(Char::isDigit) },
                        label = { Text("Hours") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(90.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter(Char::isDigit) },
                        label = { Text("Minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(90.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text("Repeats?", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.padding(top = 6.dp)) {
                    FilterChip(
                        selected = repeatKind == RepeatChoice.ONCE,
                        onClick = { repeatKind = RepeatChoice.ONCE },
                        label = { Text("One-and-done") }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = repeatKind == RepeatChoice.DAILY,
                        onClick = { repeatKind = RepeatChoice.DAILY },
                        label = { Text("Daily") }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = repeatKind == RepeatChoice.TIMES_PER_WEEK,
                        onClick = { repeatKind = RepeatChoice.TIMES_PER_WEEK },
                        label = { Text("A few times a week") }
                    )
                    if (repeatKind == RepeatChoice.TIMES_PER_WEEK) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = timesPerWeek.toString(),
                            onValueChange = {
                                timesPerWeek = it.toIntOrNull()?.coerceIn(1, 7) ?: timesPerWeek
                            },
                            label = { Text("Times") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(80.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Hard deadline",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = hasDeadline, onCheckedChange = { hasDeadline = it })
                }
                if (hasDeadline) {
                    TextButton(onClick = {
                        val base = deadline ?: LocalDate.now()
                        DatePickerDialog(
                            context,
                            { _, year, month, day -> deadline = LocalDate.of(year, month + 1, day) },
                            base.year, base.monthValue - 1, base.dayOfMonth
                        ).show()
                    }) {
                        Text(deadline?.toString() ?: "Pick a date")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        PlannerTask(
                            title = title.trim(),
                            durationMinutes = durationMinutes,
                            deadline = if (hasDeadline) deadline else null,
                            recurrence = when (repeatKind) {
                                RepeatChoice.ONCE -> TaskRecurrence.None
                                RepeatChoice.DAILY -> TaskRecurrence.Daily
                                RepeatChoice.TIMES_PER_WEEK -> TaskRecurrence.TimesPerWeek(timesPerWeek)
                            }
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
