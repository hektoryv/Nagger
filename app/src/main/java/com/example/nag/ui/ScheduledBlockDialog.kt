package com.example.nag.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nag.planner.BlockKind
import com.example.nag.planner.LockState
import com.example.nag.planner.ScheduledBlock
import java.time.Duration
import java.time.format.DateTimeFormatter

private val BLOCK_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val BLOCK_DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d")

/**
 * Everything known about one class or task, shown in full on tap rather than picked
 * over. Shared between the Calendar week view and the Today screen, since both surface
 * scheduled blocks now.
 */
@Composable
fun ScheduledBlockDialog(
    block: ScheduledBlock,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSetLockState: ((LockState?) -> Unit)? = null
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
                block.deadline?.let { BlockInfoRow("Deadline", it.format(BLOCK_DAY_FORMAT)) }
                BlockInfoRow(
                    "Status",
                    when (block.lockState) {
                        LockState.LOCKED -> "Locked — won't move on replan"
                        LockState.FLEXIBLE -> "Flexible — the planner can move this"
                        LockState.SKIPPED -> "Skipped"
                    }
                )

                if (onSetLockState != null) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "For just this occurrence — the rest of this class is unaffected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row {
                        if (block.lockState != LockState.FLEXIBLE) {
                            TextButton(onClick = { onSetLockState(LockState.FLEXIBLE) }) {
                                Text("Make flexible")
                            }
                        }
                        if (block.lockState != LockState.SKIPPED) {
                            TextButton(onClick = { onSetLockState(LockState.SKIPPED) }) {
                                Text("Skip")
                            }
                        }
                        if (block.lockState != LockState.LOCKED) {
                            TextButton(onClick = { onSetLockState(null) }) {
                                Text("Reset to locked")
                            }
                        }
                    }
                }
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
