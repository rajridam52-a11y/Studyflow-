package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ReminderEntity
import com.example.reminders.ReminderScheduler
import com.example.reminders.SmartRescheduleEngine
import com.example.ui.components.EmptyStudyState
import com.example.ui.components.PriorityBadge
import com.example.viewmodel.MainStudyFlowViewModel
import com.example.viewmodel.StudyDateFormatters
import com.example.viewmodel.StudyFlowDashboardData
import java.util.Calendar
import java.util.concurrent.TimeUnit

private val ReminderCategories = listOf(
    "CUSTOM" to "Custom",
    "HOMEWORK" to "Homework",
    "ASSIGNMENT" to "Assignment",
    "EXAM" to "Exam",
    "STUDY_SESSION" to "Study Session",
    "REVISION" to "Revision",
    "CLASS" to "Class"
)

@Composable
fun RemindersScreen(
    data: StudyFlowDashboardData,
    viewModel: MainStudyFlowViewModel,
    onOpenNotificationCenter: () -> Unit
) {
    val context = LocalContext.current
    var filterTab by remember { mutableIntStateOf(0) } // 0=Upcoming, 1=Snoozed, 2=Missed, 3=Completed
    var showReliabilityDetails by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<ReminderEntity?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    var hasNotifPerm by remember { mutableStateOf(ReminderScheduler.hasNotificationPermission(context)) }
    var canExactAlarm by remember { mutableStateOf(ReminderScheduler.canScheduleExactAlarms(context)) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPerm = granted
    }

    val missedReminders = remember(data.reminders) {
        data.reminders.filter { it.status == "MISSED" }.sortedByDescending { it.triggerAtMillis }
    }
    val smartSlots = remember(data.timetable, data.reminders, data.profile) {
        viewModel.getSmartRescheduleSuggestions(durationMinutes = 45, data = data)
    }

    val filtered = remember(data.reminders, filterTab) {
        val targetStatus = when (filterTab) {
            0 -> "UPCOMING"
            1 -> "SNOOZED"
            2 -> "MISSED"
            else -> "COMPLETED"
        }
        data.reminders.filter { it.status == targetStatus }.sortedBy { it.triggerAtMillis }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("reminders_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "rem_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Smart Reminders",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onOpenNotificationCenter) {
                        Icon(Icons.Filled.History, contentDescription = "Notification history")
                    }
                    FilledTonalButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.testTag("add_reminder_button")
                    ) {
                        Icon(Icons.Filled.AddAlarm, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New")
                    }
                }
            }
        }

        // Permission & Exact Alarm Reliability Banner
        if (!hasNotifPerm || !canExactAlarm || showReliabilityDetails) {
            item(key = "perm_banner") {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                            Text(
                                text = "Reminder Reliability & Permissions",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = stringResource(R.string.alarm_reliability_notice),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Button(
                                    onClick = {
                                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                ) {
                                    Text("Allow Notifications")
                                }
                            }
                            if (!canExactAlarm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(
                                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                        canExactAlarm = ReminderScheduler.canScheduleExactAlarms(context)
                                    }
                                ) {
                                    Text("Exact Alarm Access")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item(key = "reliability_hint") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Notifications & exact alarms active",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = { showReliabilityDetails = !showReliabilityDetails }) {
                        Text("Reliability Info")
                    }
                }
            }
        }

        // Smart Rescheduling Panel for Missed Reminders
        if (missedReminders.isNotEmpty() && smartSlots.isNotEmpty()) {
            item(key = "smart_reschedule_card") {
                val targetMissed = missedReminders.first()
                ElevatedCard(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Smart Reschedule Suggestion",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "You missed \"${targetMissed.title}\". Pick a conflict-free slot based on your timetable:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        smartSlots.forEach { slot ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(slot.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        Text(slot.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    FilledTonalButton(
                                        onClick = { viewModel.applySmartReschedule(targetMissed, slot) },
                                        modifier = Modifier.testTag("apply_smart_reschedule_btn")
                                    ) {
                                        Text("Apply")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "rem_tabs") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("Upcoming", "Snoozed", "Missed", "Done").forEachIndexed { idx, lbl ->
                    SegmentedButton(
                        selected = filterTab == idx,
                        onClick = { filterTab = idx },
                        shape = SegmentedButtonDefaults.itemShape(idx, 4)
                    ) {
                        Text(lbl, maxLines = 1)
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item(key = "rem_empty") {
                EmptyStudyState(
                    icon = Icons.Filled.Alarm,
                    title = "No Reminders Here",
                    subtitle = "Create custom, class, exam, homework, or revision reminders with advance alerts and smart rescheduling.",
                    actionLabel = "Create Reminder",
                    onAction = { showCreateDialog = true }
                )
            }
        } else {
            items(filtered, key = { it.id }) { rem ->
                ElevatedCard(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                AssistChip(onClick = {}, label = { Text(rem.category.replace("_", " ")) })
                                if (rem.repeatRule != "NONE") {
                                    AssistChip(onClick = {}, label = { Text(rem.repeatRule) })
                                }
                            }
                            PriorityBadge(rem.priority)
                        }
                        Text(
                            text = rem.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (rem.notes.isNotBlank()) {
                            Text(
                                text = rem.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${StudyDateFormatters.formatDateTime(rem.triggerAtMillis)}" +
                                if (rem.advanceMinutes > 0) " • ${rem.advanceMinutes}m advance alert" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (rem.status != "COMPLETED") {
                                    OutlinedButton(onClick = { viewModel.snoozeReminder(rem, 15) }) {
                                        Text("Snooze 15m")
                                    }
                                    FilledTonalButton(onClick = { viewModel.completeReminder(rem) }) {
                                        Text("Complete")
                                    }
                                }
                            }
                            Row {
                                IconButton(onClick = { editingReminder = rem }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit reminder")
                                }
                                IconButton(onClick = { viewModel.deleteReminder(rem.id) }) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete reminder")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog || editingReminder != null) {
        ReminderEditorDialog(
            existing = editingReminder,
            defaultSound = data.profile?.soundEnabled ?: true,
            defaultVibration = data.profile?.vibrationEnabled ?: true,
            onDismiss = {
                showCreateDialog = false
                editingReminder = null
            },
            onSave = { id, title, notes, category, priority, triggerAt, advance, repeatRule, customDays, sound, vib ->
                viewModel.saveReminder(
                    id = id,
                    title = title,
                    notes = notes,
                    category = category,
                    priority = priority,
                    triggerAtMillis = triggerAt,
                    advanceMinutes = advance,
                    repeatRule = repeatRule,
                    customRepeatDays = customDays,
                    soundEnabled = sound,
                    vibrationEnabled = vib
                )
                showCreateDialog = false
                editingReminder = null
            }
        )
    }
}

@Composable
private fun ReminderEditorDialog(
    existing: ReminderEntity?,
    defaultSound: Boolean,
    defaultVibration: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        id: String?,
        title: String,
        notes: String,
        category: String,
        priority: String,
        triggerAtMillis: Long,
        advanceMinutes: Int,
        repeatRule: String,
        customRepeatDays: String,
        soundEnabled: Boolean,
        vibrationEnabled: Boolean
    ) -> Unit
) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var category by remember { mutableStateOf(existing?.category ?: "CUSTOM") }
    var priority by remember { mutableStateOf(existing?.priority ?: "MEDIUM") }
    var minutesDelay by remember { mutableIntStateOf(60) }
    var advanceMinutes by remember { mutableIntStateOf(existing?.advanceMinutes ?: 10) }
    var repeatRule by remember { mutableStateOf(existing?.repeatRule ?: "NONE") }
    var selectedDays by remember {
        mutableStateOf(
            existing?.customRepeatDays.orEmpty()
                .split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .toSet()
        )
    }
    var soundEnabled by remember { mutableStateOf(existing?.soundEnabled ?: defaultSound) }
    var vibrationEnabled by remember { mutableStateOf(existing?.vibrationEnabled ?: defaultVibration) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Create Reminder" else "Edit Reminder") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Reminder title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reminder_title_input")
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Category", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReminderCategories.take(4).forEach { (key, lbl) ->
                        FilterChip(
                            selected = category == key,
                            onClick = { category = key },
                            label = { Text(lbl) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReminderCategories.drop(4).forEach { (key, lbl) ->
                        FilterChip(
                            selected = category == key,
                            onClick = { category = key },
                            label = { Text(lbl) }
                        )
                    }
                }
                Text("Priority", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("LOW", "MEDIUM", "HIGH").forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                if (existing == null) {
                    Text("Trigger Time", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(15 to "15m", 60 to "1h", 180 to "3h", 1440 to "Tomorrow").forEach { (mins, lbl) ->
                            FilterChip(
                                selected = minutesDelay == mins,
                                onClick = { minutesDelay = mins },
                                label = { Text(lbl) }
                            )
                        }
                    }
                }
                Text("Advance Notification: ${advanceMinutes}m before", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 5, 10, 15, 30).forEach { adv ->
                        FilterChip(
                            selected = advanceMinutes == adv,
                            onClick = { advanceMinutes = adv },
                            label = { Text(if (adv == 0) "At time" else "${adv}m") }
                        )
                    }
                }
                Text("Repeat Schedule", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("NONE" to "Once", "DAILY" to "Daily", "WEEKLY" to "Weekly", "CUSTOM_DAYS" to "Custom").forEach { (rule, lbl) ->
                        FilterChip(
                            selected = repeatRule == rule,
                            onClick = { repeatRule = rule },
                            label = { Text(lbl) }
                        )
                    }
                }
                if (repeatRule == "CUSTOM_DAYS") {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..7).forEach { d ->
                            val isSel = d in selectedDays
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    selectedDays = if (isSel) selectedDays - d else selectedDays + d
                                },
                                label = { Text(SmartRescheduleEngine.dayName(d).take(2)) }
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Notification sound")
                    Switch(checked = soundEnabled, onCheckedChange = { soundEnabled = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Vibration")
                    Switch(checked = vibrationEnabled, onCheckedChange = { vibrationEnabled = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trigger = existing?.triggerAtMillis
                        ?: (System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutesDelay.toLong()))
                    onSave(
                        existing?.id,
                        title,
                        notes,
                        category,
                        priority,
                        trigger,
                        advanceMinutes,
                        repeatRule,
                        selectedDays.sorted().joinToString(","),
                        soundEnabled,
                        vibrationEnabled
                    )
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("save_reminder_button")
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
