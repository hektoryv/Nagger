package com.example.nag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nag.data.Habit
import com.example.nag.data.ScheduleKind
import com.example.nag.data.habitColorArgb
import com.example.nag.logic.Schedule
import java.time.LocalDate

private data class Range(val label: String, val days: Long)

private val ranges = listOf(
    Range("30 days", 30),
    Range("90 days", 90),
    Range("All", 0)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    state: AppState,
    habit: Habit,
    today: LocalDate,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    var range by remember { mutableStateOf(ranges.first()) }
    val log = state.log
    val color = Color(habitColorArgb(habit.colorIndex))

    val windowDays = if (range.days == 0L) 3650L else range.days
    val stats = Schedule.statsFor(habit, today, log, windowDays)
    val entries = Schedule.entriesFor(habit, log).sortedByDescending { it.date }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(habit.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ColorDot(habit, size = 12)
                Spacer(Modifier.width(8.dp))
                Text(habit.rhythm(), style = MaterialTheme.typography.bodyMedium)
                if (habit.paused) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "· Paused",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Row(
                Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ranges.forEach { option ->
                    FilterChip(
                        selected = range == option,
                        onClick = { range = option },
                        label = { Text(option.label) }
                    )
                }
            }

            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    if (habit.tracksAmount) {
                        Text(
                            "${habit.unit.replaceFirstChar { it.uppercase() }} over time",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(10.dp))
                        BarChart(
                            points = Schedule.amountSeries(habit, today, log, range.days),
                            color = color,
                            unit = habit.unit,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            "Sessions per week",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(10.dp))
                        val weeks = if (range.days == 0L) 26 else (range.days / 7).toInt()
                        BarChart(
                            points = Schedule.weeklyCounts(habit, today, log, weeks.coerceAtLeast(4)),
                            color = color,
                            unit = "a week",
                            modifier = Modifier.fillMaxWidth(),
                            showTrend = false
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    StatRow("Completed", "${stats.done} of ${stats.scheduled}")
                    StatRow("Hit rate", "${stats.percent}%")
                    StatRow(
                        "Current streak",
                        "${stats.streak} ${habit.streakUnit(stats.streak)}"
                    )
                    if (habit.tracksAmount) {
                        StatRow("Best", "${stats.best} ${habit.unit}")
                        StatRow("Total", "${stats.total} ${habit.unit}")
                        if (stats.done > 0) {
                            StatRow("Average", "${stats.total / stats.done} ${habit.unit}")
                        }
                    }
                    if (habit.kind == ScheduleKind.TIMES_PER_WEEK) {
                        StatRow(
                            "This week",
                            "${Schedule.doneThisWeek(habit, today, log)} of ${habit.timesPerWeek}"
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { state.setPaused(habit, !habit.paused) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (habit.paused) "Resume" else "Pause")
                }
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Text("Edit")
                }
            }

            if (entries.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Text(
                    "HISTORY",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
                )
                entries.take(60).forEach { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            entry.date.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            if (entry.amount != null) "${entry.amount} ${habit.unit}" else "Done",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
