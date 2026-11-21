package com.example.ui.screens.planner

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.SubjectEntity
import com.example.data.local.TimetableSessionEntity
import com.example.reminders.SmartRescheduleEngine
import com.example.ui.components.EmptyStudyState
import com.example.ui.components.SubjectPickerRow
import com.example.viewmodel.MainStudyFlowViewModel
import com.example.viewmodel.StudyDateFormatters
import com.example.viewmodel.StudyFlowDashboardData

@Composable
fun TimetableTab(
    data: StudyFlowDashboardData,
    viewModel: MainStudyFlowViewModel
) {
    var weeklyView by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableIntStateOf(StudyDateFormatters.currentIsoDayOfWeek()) }
    var editingSession by remember { mutableStateOf<TimetableSessionEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val daySessions = remember(data.timetable, selectedDay, weeklyView) {
        if (weeklyView) {
            data.timetable.sortedWith(compareBy<TimetableSessionEntity> { it.dayOfWeek }.thenBy { it.startMinuteOfDay })
        } else {
            data.timetable.filter { it.dayOfWeek == selectedDay }.sortedBy { it.startMinuteOfDay }
        }
    }

    val freeSlots = remember(data.timetable, selectedDay, data.profile) {
        SmartRescheduleEngine.findFreeSlotsForDay(
            dayOfWeek = selectedDay,
            sessions = data.timetable,
            preferredStartHour = data.profile?.preferredStartHour ?: 8,
            preferredEndHour = data.profile?.preferredEndHour ?: 21,
            minDurationMinutes = 30
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("timetable_tab"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "tt_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !weeklyView,
                        onClick = { weeklyView = false },
                        label = { Text("Daily") }
                    )
                    FilterChip(
                        selected = weeklyView,
                        onClick = { weeklyView = true },
                        label = { Text("Weekly") }
                    )
                }
                FilledTonalButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.testTag("add_timetable_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Session")
                }
            }
        }

        if (!weeklyView) {
            item(key = "day_selector") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    (1..7).forEach { day ->
                        FilterChip(
                            selected = selectedDay == day,
                            onClick = { selectedDay = day },
                            label = { Text(SmartRescheduleEngine.dayName(day).take(3)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item(key = "free_time_card") {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Free Study Windows (${SmartRescheduleEngine.dayName(selectedDay)})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (freeSlots.isEmpty()) {
                        Text(
                            text = "No open 30+ minute windows within your preferred hours.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            text = freeSlots.joinToString("  •  ") {
                                "${SmartRescheduleEngine.formatMinutes(it.startMinuteOfDay)} - ${SmartRescheduleEngine.formatMinutes(it.endMinuteOfDay)}"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        if (daySessions.isEmpty()) {
            item(key = "tt_empty") {
                EmptyStudyState(
                    icon = Icons.Filled.CalendarViewWeek,
                    title = "No Timetable Sessions",
                    subtitle = "Add classes, recurring study blocks, or breaks. Conflict detection keeps your schedule overlap-free.",
                    actionLabel = "Add Timetable Session",
                    onAction = { showAddDialog = true }
                )
            }
        } else {
            items(daySessions, key = { it.id }) { session ->
                ElevatedCard(
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AssistChip(onClick = {}, label = { Text(session.sessionKind) })
                                if (weeklyView) {
                                    Text(
                                        text = SmartRescheduleEngine.dayName(session.dayOfWeek),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                text = session.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${SmartRescheduleEngine.formatMinutes(session.startMinuteOfDay)} - ${SmartRescheduleEngine.formatMinutes(session.endMinuteOfDay)}" +
                                    if (session.subjectName.isNotBlank()) " • ${session.subjectName}" else "" +
                                    if (session.location.isNotBlank()) " • ${session.location}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row {
                            IconButton(onClick = { editingSession = session }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit session")
                            }
                            IconButton(onClick = { viewModel.deleteTimetableSession(session.id) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete session")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog || editingSession != null) {
        TimetableSessionDialog(
            existing = editingSession,
            defaultDay = selectedDay,
            subjects = data.subjects,
            allSessions = data.timetable,
            onQuickAddSubject = { viewModel.saveSubject(name = it) },
            onDismiss = {
                showAddDialog = false
                editingSession = null
            },
            onSave = { id, subjId, subjName, title, day, startMin, endMin, kind, notifyMins, location ->
                val ok = viewModel.saveTimetableSession(
                    id = id,
                    subjectId = subjId,
                    subjectName = subjName,
                    title = title,
                    dayOfWeek = day,
                    startMinuteOfDay = startMin,
                    endMinuteOfDay = endMin,
                    sessionKind = kind,
                    notifyBeforeMinutes = notifyMins,
                    location = location
                )
                if (ok) {
                    showAddDialog = false
                    editingSession = null
                }
            }
        )
    }
}

@Composable
private fun TimetableSessionDialog(
    existing: TimetableSessionEntity?,
    defaultDay: Int,
    subjects: List<SubjectEntity>,
    allSessions: List<TimetableSessionEntity>,
    onQuickAddSubject: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        id: String?,
        subjectId: String,
        subjectName: String,
        title: String,
        dayOfWeek: Int,
        startMin: Int,
        endMin: Int,
        kind: String,
        notifyBefore: Int,
        location: String
    ) -> Unit
) {
    var selectedSubject by remember {
        mutableStateOf(subjects.firstOrNull { it.id == existing?.subjectId } ?: subjects.firstOrNull())
    }
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var dayOfWeek by remember { mutableIntStateOf(existing?.dayOfWeek ?: defaultDay) }
    var startHour by remember { mutableIntStateOf((existing?.startMinuteOfDay ?: 540) / 60) }
    var durationMinutes by remember {
        mutableIntStateOf(((existing?.endMinuteOfDay ?: 600) - (existing?.startMinuteOfDay ?: 540)).coerceIn(30, 180))
    }
    var kind by remember { mutableStateOf(existing?.sessionKind ?: "STUDY") }
    var notifyBefore by remember { mutableIntStateOf(existing?.notifyBeforeMinutes ?: 10) }
    var location by remember { mutableStateOf(existing?.location.orEmpty()) }

    val startMin = startHour * 60
    val endMin = (startMin + durationMinutes).coerceAtMost(1440)
    val conflicts = remember(dayOfWeek, startMin, endMin, allSessions, existing) {
        SmartRescheduleEngine.findConflicts(dayOfWeek, startMin, endMin, allSessions, existing?.id)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Timetable Session" else "Edit Session") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SubjectPickerRow(
                    subjects = subjects,
                    selectedSubjectId = selectedSubject?.id ?: "general",
                    onSelect = { selectedSubject = it },
                    onQuickAddSubject = onQuickAddSubject
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Session title (e.g., Calculus Lecture)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("timetable_title_input")
                )
                Text("Type", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("CLASS", "STUDY", "BREAK").forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = { kind = k },
                            label = { Text(k) }
                        )
                    }
                }
                Text("Day of Week", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..7).forEach { d ->
                        FilterChip(
                            selected = dayOfWeek == d,
                            onClick = { dayOfWeek = d },
                            label = { Text(SmartRescheduleEngine.dayName(d).take(2)) }
                        )
                    }
                }
                Text(
                    "Start: ${SmartRescheduleEngine.formatMinutes(startMin)} • End: ${SmartRescheduleEngine.formatMinutes(endMin)}",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = startHour.toFloat(),
                    onValueChange = { startHour = it.toInt() },
                    valueRange = 6f..22f,
                    steps = 15
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 45, 60, 90, 120).forEach { dur ->
                        FilterChip(
                            selected = durationMinutes == dur,
                            onClick = { durationMinutes = dur },
                            label = { Text("${dur}m") }
                        )
                    }
                }
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location / Room (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (conflicts.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Conflict detected with \"${conflicts.first().title}\" (${SmartRescheduleEngine.formatMinutes(conflicts.first().startMinuteOfDay)})",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        existing?.id,
                        selectedSubject?.id ?: "",
                        selectedSubject?.name ?: "",
                        title,
                        dayOfWeek,
                        startMin,
                        endMin,
                        kind,
                        notifyBefore,
                        location
                    )
                },
                enabled = title.isNotBlank() && conflicts.isEmpty(),
                modifier = Modifier.testTag("save_timetable_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
