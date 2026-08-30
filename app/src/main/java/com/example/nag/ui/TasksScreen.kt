package com.example.nag.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.example.nag.planner.PlannerTask
import com.example.nag.planner.TaskRecurrence

/**
 * Every task, placed or not — the only place an unplaced task (no room for it this
 * week) can be reached at all, and the only place a task can be edited rather than
 * just deleted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(state: AppState, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<PlannerTask?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tasks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add a task")
            }
        }
    ) { padding ->
        val tasks = state.plannerTasks.sortedBy { it.title }

        if (tasks.isEmpty()) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No tasks yet.", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tap + to add one — insurance, bouldering, whatever needs a slot.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                tasks.forEach { task ->
                    ListItem(
                        modifier = Modifier.clickable { editing = task },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(task.title) },
                        supportingContent = { Text(taskSummary(task)) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (creating) {
        TaskDialog(
            existing = null,
            onDismiss = { creating = false },
            onSave = {
                creating = false
                state.upsertTask(it)
            }
        )
    }

    val current = editing
    if (current != null) {
        TaskDialog(
            existing = current,
            onDismiss = { editing = null },
            onSave = {
                editing = null
                state.upsertTask(it)
            },
            onDelete = {
                state.deleteTask(current.id)
                editing = null
            }
        )
    }
}

private fun taskSummary(task: PlannerTask): String {
    val hours = task.durationMinutes / 60
    val minutes = task.durationMinutes % 60
    val duration = when {
        hours == 0 -> "${minutes}m"
        minutes == 0 -> "${hours}h"
        else -> "${hours}h ${minutes}m"
    }
    val repeat = when (val recurrence = task.recurrence) {
        TaskRecurrence.None -> "One-and-done"
        TaskRecurrence.Daily -> "Daily"
        is TaskRecurrence.TimesPerWeek -> "${recurrence.count}x a week"
    }
    val deadline = task.deadline?.let { " · due $it" } ?: ""
    return "$duration · $repeat$deadline"
}
