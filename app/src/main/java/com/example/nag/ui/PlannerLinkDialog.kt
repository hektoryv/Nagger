package com.example.nag.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Where the class schedule ICS link is pasted in and, once saved, synced. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerLinkDialog(
    existing: String?,
    syncing: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var url by remember { mutableStateOf(existing ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Class schedule link") },
        text = {
            Column {
                Text(
                    "Paste the ICS link from your school's timetable (e.g. TimeEdit). " +
                        "Classes from it show up as locked blocks in the week view.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text("ICS link") },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
                when {
                    syncing -> Text(
                        "Syncing…",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    error != null -> Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(url) }, enabled = url.isNotBlank()) { Text("Save & sync") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
