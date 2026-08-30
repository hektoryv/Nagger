package com.example.nag.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nag.planner.DayShape
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Lets the times the planner treats as "day starts here", "don't schedule over
 * dinner", and "wind down, nothing new after this" be changed instead of the fixed
 * 08:00 / 18:00–19:00 / 22:00 defaults. Same [DayShape] instance the placer itself
 * reads, so what's set here is exactly what the planner respects.
 */
@Composable
fun DayShapeDialog(
    existing: DayShape,
    onDismiss: () -> Unit,
    onSave: (DayShape) -> Unit
) {
    val context = LocalContext.current
    var dayStart by remember { mutableStateOf(existing.dayStart) }
    var dinnerStart by remember { mutableStateOf(existing.dinnerStart) }
    var dinnerEnd by remember { mutableStateOf(existing.dinnerEnd) }
    var windDownStart by remember { mutableStateOf(existing.windDownStart) }
    var error by remember { mutableStateOf<String?>(null) }

    fun pick(current: LocalTime, onPicked: (LocalTime) -> Unit) {
        TimePickerDialog(
            context,
            { _, h, m -> onPicked(LocalTime.of(h, m)) },
            current.hour, current.minute, true
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Day shape") },
        text = {
            Column {
                Text(
                    "Where the planner will and won't put flexible tasks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                DayShapeTimeRow("Day starts", dayStart) { pick(dayStart) { dayStart = it } }
                DayShapeTimeRow("Dinner starts", dinnerStart) { pick(dinnerStart) { dinnerStart = it } }
                DayShapeTimeRow("Dinner ends", dinnerEnd) { pick(dinnerEnd) { dinnerEnd = it } }
                DayShapeTimeRow("Wind-down starts", windDownStart) { pick(windDownStart) { windDownStart = it } }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching { DayShape(dayStart, dinnerStart, dinnerEnd, windDownStart) }
                    .onSuccess { onSave(it) }
                    .onFailure { error = it.message ?: "Those times need to be in order: day start, dinner, wind-down." }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DayShapeTimeRow(label: String, time: LocalTime, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Text(time.format(TIME_FORMAT), fontWeight = FontWeight.SemiBold)
    }
}
