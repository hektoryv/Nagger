package com.example.nag

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.nag.data.Backup
import com.example.nag.data.Habit
import com.example.nag.notify.Notifications
import com.example.nag.notify.Scheduler
import com.example.nag.ui.AmountDialog
import com.example.nag.ui.AppState
import com.example.nag.ui.CalendarScreen
import com.example.nag.ui.DetailScreen
import com.example.nag.ui.HabitDialog
import com.example.nag.ui.PlannerLinkDialog
import com.example.nag.ui.PromptDialog
import com.example.nag.ui.TaskDialog
import com.example.nag.ui.TasksScreen
import com.example.nag.ui.TodayScreen
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PROMPT_HABIT_ID = "promptHabitId"
    }

    private lateinit var state: AppState
    private var promptHabitId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifications.createChannel(this)
        state = AppState(this)
        promptHabitId = intent?.getStringExtra(EXTRA_PROMPT_HABIT_ID)

        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                NagApp(state, promptHabitId) { promptHabitId = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        promptHabitId = intent.getStringExtra(EXTRA_PROMPT_HABIT_ID)
    }

    override fun onResume() {
        super.onResume()
        // Pick up anything ticked from a notification or the widget while we were away.
        state.reload()
        Scheduler.scheduleNightly(this)
    }
}

private enum class Tab { TODAY, CALENDAR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NagApp(state: AppState, promptHabitId: String?, onPromptHandled: () -> Unit) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val coroutineScope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(Tab.TODAY) }
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Habit?>(null) }
    var creating by remember { mutableStateOf(false) }
    var amountFor by remember { mutableStateOf<Habit?>(null) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var plannerLinkOpen by remember { mutableStateOf(false) }
    var taskCreating by remember { mutableStateOf(false) }
    var tasksScreenOpen by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Syncs on first open (if a link is already saved) and again whenever it's changed.
    LaunchedEffect(state.plannerFeedUrl) {
        if (state.plannerFeedUrl != null) state.syncPlanner()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            val ok = Backup.writeTo(context, uri)
            toast(context, if (ok) "Backup saved" else "Couldn't write the backup")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val ok = Backup.restoreFrom(context, uri)
            state.restored()
            toast(context, if (ok) "Backup restored" else "That file didn't look like a backup")
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val ok = Backup.setFolder(context, uri)
            state.reload()
            toast(context, if (ok) "Backing up automatically" else "Couldn't use that folder")
        }
    }

    fun tick(habit: Habit) {
        if (habit.tracksAmount) amountFor = habit
        else state.setDone(habit.id, today, null)
    }

    // Detail screen takes over the whole window when open.
    val detail = detailId?.let { state.habit(it) }
    if (detail != null) {
        BackHandler { detailId = null }
        DetailScreen(
            state = state,
            habit = detail,
            today = today,
            onBack = { detailId = null },
            onEdit = { editing = detail }
        )
        EditingDialogs(state, today, editing, { editing = it }, amountFor, { amountFor = it })
        return
    }

    if (tasksScreenOpen) {
        BackHandler { tasksScreenOpen = false }
        TasksScreen(state = state, onBack = { tasksScreenOpen = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nag") },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = {
                                Text("Check-in time (%02d:%02d)".format(
                                    state.reminder.first, state.reminder.second
                                ))
                            },
                            onClick = {
                                menuOpen = false
                                TimePickerDialog(
                                    context,
                                    { _, h, m -> state.setReminder(h, m) },
                                    state.reminder.first, state.reminder.second, true
                                ).show()
                            }
                        )
                        if (!Scheduler.canScheduleExact(context)) {
                            DropdownMenuItem(
                                text = { Text("Allow exact alarms") },
                                onClick = {
                                    menuOpen = false
                                    openExactAlarmSettings(context)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    state.backupFolder?.let { "Auto-backup: $it" }
                                        ?: "Turn on auto-backup"
                                )
                            },
                            onClick = {
                                menuOpen = false
                                folderLauncher.launch(null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export backup") },
                            onClick = {
                                menuOpen = false
                                exportLauncher.launch("nag-backup.json")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Restore backup") },
                            onClick = {
                                menuOpen = false
                                importLauncher.launch(arrayOf("*/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    state.plannerFeedUrl?.let { "Class schedule link" }
                                        ?: "Add class schedule link"
                                )
                            },
                            onClick = {
                                menuOpen = false
                                plannerLinkOpen = true
                            }
                        )
                        if (state.plannerFeedUrl != null) {
                            DropdownMenuItem(
                                text = { Text(if (state.plannerSyncing) "Syncing…" else "Sync class schedule now") },
                                enabled = !state.plannerSyncing,
                                onClick = {
                                    menuOpen = false
                                    coroutineScope.launch { state.syncPlanner() }
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Manage tasks") },
                            onClick = {
                                menuOpen = false
                                tasksScreenOpen = true
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.TODAY,
                    onClick = { tab = Tab.TODAY },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text("Today") }
                )
                NavigationBarItem(
                    selected = tab == Tab.CALENDAR,
                    onClick = { tab = Tab.CALENDAR },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    label = { Text("Calendar") }
                )
            }
        },
        floatingActionButton = {
            when (tab) {
                Tab.TODAY -> ExtendedFloatingActionButton(
                    onClick = { creating = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add check-in") }
                )
                Tab.CALENDAR -> ExtendedFloatingActionButton(
                    onClick = { taskCreating = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add task") }
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (tab) {
                Tab.TODAY -> TodayScreen(
                    state = state,
                    today = today,
                    onTick = { tick(it) },
                    onUntick = { state.clearDone(it.id, today) },
                    onOpen = { detailId = it.id }
                )

                Tab.CALENDAR -> CalendarScreen(
                    state = state,
                    today = today,
                    onSetDone = { habit, date, amount -> state.setDone(habit.id, date, amount) },
                    onClearDone = { habit, date -> state.clearDone(habit.id, date) },
                    onOpen = { detailId = it.id }
                )
            }
        }
    }

    if (creating) {
        HabitDialog(
            existing = null,
            defaultColorIndex = state.nextColorIndex(),
            onDismiss = { creating = false },
            onSave = {
                creating = false
                state.upsertHabit(it)
            }
        )
    }

    EditingDialogs(state, today, editing, { editing = it }, amountFor, { amountFor = it })

    if (taskCreating) {
        TaskDialog(
            onDismiss = { taskCreating = false },
            onSave = {
                taskCreating = false
                state.upsertTask(it)
            }
        )
    }

    if (plannerLinkOpen) {
        PlannerLinkDialog(
            existing = state.plannerFeedUrl,
            syncing = state.plannerSyncing,
            error = state.plannerSyncError,
            onDismiss = { plannerLinkOpen = false },
            onSave = { url ->
                plannerLinkOpen = false
                state.setFeedUrl(url)
            },
            onRemove = {
                plannerLinkOpen = false
                state.setFeedUrl(null)
            }
        )
    }

    // Landed here from a notification or a widget row: ask straight out.
    val prompted = promptHabitId?.let { id -> state.habits.firstOrNull { it.id == id } }
    if (prompted != null) {
        PromptDialog(
            habit = prompted,
            lastAmount = state.lastAmount(prompted.id),
            onDismiss = onPromptHandled,
            onDone = { amount ->
                state.setDone(prompted.id, today, amount)
                onPromptHandled()
            },
            onNotYet = {
                Scheduler.snooze(context, prompted.id)
                Notifications.cancel(context, prompted.id)
                onPromptHandled()
            }
        )
    }
}

/** Edit and amount dialogs, needed from both the list and the detail screen. */
@Composable
private fun EditingDialogs(
    state: AppState,
    today: LocalDate,
    editing: Habit?,
    setEditing: (Habit?) -> Unit,
    amountFor: Habit?,
    setAmountFor: (Habit?) -> Unit
) {
    if (editing != null) {
        HabitDialog(
            existing = editing,
            defaultColorIndex = editing.colorIndex,
            onDismiss = { setEditing(null) },
            onSave = {
                setEditing(null)
                state.upsertHabit(it)
            },
            onDelete = {
                setEditing(null)
                state.deleteHabit(editing.id)
            }
        )
    }

    if (amountFor != null) {
        AmountDialog(
            habit = amountFor,
            initial = state.lastAmount(amountFor.id),
            onDismiss = { setAmountFor(null) },
            onConfirm = { amount ->
                setAmountFor(null)
                state.setDone(amountFor.id, today, amount)
            }
        )
    }
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
    }
}
