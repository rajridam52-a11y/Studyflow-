package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.local.*
import com.example.reminders.SmartRescheduleEngine
import com.example.viewmodel.StudyDateFormatters
import com.example.viewmodel.StudyFlowDashboardData
import java.util.Calendar
import java.util.concurrent.TimeUnit

@Composable
fun EmptyStudyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(4.dp))
                FilledTonalButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun PriorityBadge(priority: String) {
    val (bg, fg, label) = when (priority.uppercase()) {
        "HIGH" -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "High"
        )
        "LOW" -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "Low"
        )
        else -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "Medium"
        )
    }
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = bg
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun StudyProgressRing(
    progress: Float,
    label: String,
    valueText: String,
    ringColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    size: Dp = 84.dp
) {
    val clamped = progress.coerceIn(0f, 1f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 8.dp.toPx()
                val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
                val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * clamped,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun StudyWeeklyBarChart(
    dailyMinutes: List<Pair<String, Int>>,
    barColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    modifier: Modifier = Modifier
) {
    val maxVal = (dailyMinutes.maxOfOrNull { it.second } ?: 0).coerceAtLeast(30)
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            if (dailyMinutes.isEmpty()) return@Canvas
            val count = dailyMinutes.size
            val slotWidth = size.width / count
            val barWidth = (slotWidth * 0.48f).coerceAtMost(28.dp.toPx())
            val corner = CornerRadius(6.dp.toPx(), 6.dp.toPx())

            dailyMinutes.forEachIndexed { index, (_, mins) ->
                val x = index * slotWidth + (slotWidth - barWidth) / 2f
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = corner
                )
                val ratio = (mins.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
                val barHeight = (size.height * ratio).coerceAtLeast(if (mins > 0) 6.dp.toPx() else 0f)
                if (barHeight > 0f) {
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = corner
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            dailyMinutes.forEach { (day, mins) ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${mins}m",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun SubjectPickerRow(
    subjects: List<SubjectEntity>,
    selectedSubjectId: String,
    onSelect: (SubjectEntity?) -> Unit,
    onQuickAddSubject: ((String) -> Unit)? = null
) {
    var showInlineAdd by remember { mutableStateOf(false) }
    var newSubjectName by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Subject",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = selectedSubjectId == "general" || selectedSubjectId.isBlank(),
                onClick = { onSelect(null) },
                label = { Text("General") }
            )
            if (onQuickAddSubject != null) {
                AssistChip(
                    onClick = { showInlineAdd = !showInlineAdd },
                    label = { Text("+ Subject") }
                )
            }
        }
        if (subjects.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                subjects.take(4).forEach { subject ->
                    FilterChip(
                        selected = subject.id == selectedSubjectId,
                        onClick = { onSelect(subject) },
                        label = {
                            Text(
                                subject.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
            if (subjects.size > 4) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    subjects.drop(4).take(4).forEach { subject ->
                        FilterChip(
                            selected = subject.id == selectedSubjectId,
                            onClick = { onSelect(subject) },
                            label = { Text(subject.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }
        }
        if (showInlineAdd && onQuickAddSubject != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newSubjectName,
                    onValueChange = { newSubjectName = it },
                    label = { Text("New subject name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(
                    onClick = {
                        if (newSubjectName.isNotBlank()) {
                            onQuickAddSubject(newSubjectName.trim())
                            newSubjectName = ""
                            showInlineAdd = false
                        }
                    }
                ) {
                    Text("Add")
                }
            }
        }
    }
}

enum class QuickAddKind(val label: String, val icon: ImageVector) {
    HOMEWORK("Homework", Icons.AutoMirrored.Filled.Assignment),
    REMINDER("Reminder", Icons.Filled.NotificationsActive),
    SESSION("Study Session", Icons.Filled.Schedule),
    NOTE("Note", Icons.Filled.EditNote),
    GOAL("Goal", Icons.Filled.Flag)
}

@Composable
fun GlobalQuickAddDialog(
    subjects: List<SubjectEntity>,
    onDismiss: () -> Unit,
    onQuickAddSubject: (String) -> Unit,
    onAddHomework: (subjectId: String, subjectName: String, title: String, desc: String, dueAt: Long, priority: String) -> Unit,
    onAddReminder: (title: String, notes: String, category: String, priority: String, triggerAt: Long) -> Unit,
    onAddTimetableSession: (subjectId: String, subjectName: String, title: String, dayOfWeek: Int, startMin: Int, endMin: Int) -> Boolean,
    onAddNote: (subjectId: String, subjectName: String, chapter: String, title: String, content: String) -> Unit,
    onAddGoal: (title: String, period: String, target: Int, unit: String, subjectId: String?) -> Unit
) {
    var selectedKind by remember { mutableStateOf(QuickAddKind.HOMEWORK) }
    var selectedSubject by remember { mutableStateOf<SubjectEntity?>(subjects.firstOrNull()) }
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("MEDIUM") }
    var dueHoursOffset by remember { mutableIntStateOf(24) }
    var sessionDay by remember { mutableIntStateOf(StudyDateFormatters.currentIsoDayOfWeek()) }
    var sessionHour by remember { mutableIntStateOf(16) }
    var goalTarget by remember { mutableStateOf("60") }
    var goalUnit by remember { mutableStateOf("mins") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick Add") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    QuickAddKind.entries.take(3).forEach { kind ->
                        FilterChip(
                            selected = selectedKind == kind,
                            onClick = { selectedKind = kind },
                            label = { Text(kind.label, maxLines = 1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    QuickAddKind.entries.drop(3).forEach { kind ->
                        FilterChip(
                            selected = selectedKind == kind,
                            onClick = { selectedKind = kind },
                            label = { Text(kind.label, maxLines = 1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (selectedKind != QuickAddKind.REMINDER) {
                    SubjectPickerRow(
                        subjects = subjects,
                        selectedSubjectId = selectedSubject?.id ?: "general",
                        onSelect = { selectedSubject = it },
                        onQuickAddSubject = onQuickAddSubject
                    )
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = {
                        Text(
                            when (selectedKind) {
                                QuickAddKind.HOMEWORK -> "Homework title"
                                QuickAddKind.REMINDER -> "Reminder title"
                                QuickAddKind.SESSION -> "Study session title"
                                QuickAddKind.NOTE -> "Note title"
                                QuickAddKind.GOAL -> "Goal title (e.g., Daily Math Practice)"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_add_title_input")
                )

                if (selectedKind != QuickAddKind.GOAL) {
                    OutlinedTextField(
                        value = details,
                        onValueChange = { details = it },
                        label = {
                            Text(
                                when (selectedKind) {
                                    QuickAddKind.NOTE -> "Note content"
                                    QuickAddKind.SESSION -> "Room or study focus"
                                    else -> "Notes / description (optional)"
                                }
                            )
                        },
                        minLines = if (selectedKind == QuickAddKind.NOTE) 3 else 1,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                when (selectedKind) {
                    QuickAddKind.HOMEWORK, QuickAddKind.REMINDER -> {
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
                        Text("Schedule", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1 to "In 1h", 3 to "In 3h", 24 to "Tomorrow", 72 to "In 3 days").forEach { (hrs, lbl) ->
                                FilterChip(
                                    selected = dueHoursOffset == hrs,
                                    onClick = { dueHoursOffset = hrs },
                                    label = { Text(lbl) }
                                )
                            }
                        }
                    }

                    QuickAddKind.SESSION -> {
                        Text("Day", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            (1..7).forEach { d ->
                                FilterChip(
                                    selected = sessionDay == d,
                                    onClick = { sessionDay = d },
                                    label = { Text(SmartRescheduleEngine.dayName(d).take(2)) }
                                )
                            }
                        }
                        Text("Start Hour: ${SmartRescheduleEngine.formatMinutes(sessionHour * 60)}", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = sessionHour.toFloat(),
                            onValueChange = { sessionHour = it.toInt() },
                            valueRange = 6f..21f,
                            steps = 14
                        )
                    }

                    QuickAddKind.GOAL -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = goalTarget,
                                onValueChange = { goalTarget = it.filter(Char::isDigit) },
                                label = { Text("Target") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = goalUnit,
                                onValueChange = { goalUnit = it },
                                label = { Text("Unit") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    QuickAddKind.NOTE -> Unit
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val subjId = selectedSubject?.id ?: "general"
                    val subjName = selectedSubject?.name ?: "General"
                    when (selectedKind) {
                        QuickAddKind.HOMEWORK -> {
                            onAddHomework(
                                subjId,
                                subjName,
                                title,
                                details,
                                System.currentTimeMillis() + TimeUnit.HOURS.toMillis(dueHoursOffset.toLong()),
                                priority
                            )
                            onDismiss()
                        }
                        QuickAddKind.REMINDER -> {
                            onAddReminder(
                                title,
                                details,
                                "CUSTOM",
                                priority,
                                System.currentTimeMillis() + TimeUnit.HOURS.toMillis(dueHoursOffset.toLong())
                            )
                            onDismiss()
                        }
                        QuickAddKind.SESSION -> {
                            val ok = onAddTimetableSession(
                                subjId,
                                subjName,
                                title,
                                sessionDay,
                                sessionHour * 60,
                                (sessionHour * 60 + 60).coerceAtMost(1440)
                            )
                            if (ok) onDismiss()
                        }
                        QuickAddKind.NOTE -> {
                            onAddNote(subjId, subjName, "", title, details)
                            onDismiss()
                        }
                        QuickAddKind.GOAL -> {
                            onAddGoal(
                                title,
                                "DAILY",
                                goalTarget.toIntOrNull()?.coerceAtLeast(1) ?: 60,
                                goalUnit.ifBlank { "mins" },
                                selectedSubject?.id
                            )
                            onDismiss()
                        }
                    }
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("quick_add_save_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun GlobalSearchDialog(
    data: StudyFlowDashboardData,
    onDismiss: () -> Unit,
    onNavigateToRoute: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()

    val results = remember(q, data) {
        if (q.isBlank()) emptyList()
        else buildList {
            data.homework.filter {
                it.title.lowercase().contains(q) || it.subjectName.lowercase().contains(q)
            }.forEach {
                add(Triple("Homework", "${it.title} • ${it.subjectName}", "planner"))
            }
            data.reminders.filter {
                it.title.lowercase().contains(q) || it.notes.lowercase().contains(q)
            }.forEach {
                add(Triple("Reminder", "${it.title} (${it.status})", "reminders"))
            }
            data.notes.filter {
                it.title.lowercase().contains(q) || it.content.lowercase().contains(q)
            }.forEach {
                add(Triple("Note", "${it.title} • ${it.subjectName}", "study"))
            }
            data.exams.filter {
                it.title.lowercase().contains(q) || it.subjectName.lowercase().contains(q)
            }.forEach {
                add(Triple("Exam", "${it.title} • ${StudyDateFormatters.formatShortDate(it.examAtMillis)}", "planner"))
            }
            data.flashcards.filter {
                it.question.lowercase().contains(q) || it.answer.lowercase().contains(q)
            }.forEach {
                add(Triple("Flashcard", it.question, "study"))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search StudyFlow") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search homework, notes, exams, reminders...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("global_search_input")
                )
                if (q.isBlank()) {
                    Text(
                        "Type a keyword or subject name to search across your academic workspace.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (results.isEmpty()) {
                    Text(
                        "No matching records found for \"$query\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(results) { (kind, label, dest) ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDismiss()
                                        onNavigateToRoute(dest)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AssistChip(onClick = {}, label = { Text(kind) })
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun NotificationCenterDialog(
    notifications: List<AppNotificationEntity>,
    onDismiss: () -> Unit,
    onMarkAllRead: () -> Unit,
    onClearAll: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Notification Center")
                if (notifications.isNotEmpty()) {
                    TextButton(onClick = onMarkAllRead) { Text("Mark Read") }
                }
            }
        },
        text = {
            if (notifications.isEmpty()) {
                EmptyStudyState(
                    icon = Icons.Filled.NotificationsNone,
                    title = "All Caught Up",
                    subtitle = "Reminder alerts, exam notices, and unlocked achievements will appear here."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notifications, key = { it.id }) { item ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (item.read) {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = StudyDateFormatters.formatShortDate(item.timestampMillis),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = item.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            if (notifications.isNotEmpty()) {
                TextButton(onClick = onClearAll) { Text("Clear History") }
            }
        }
    )
}

@Composable
fun CustomizeDashboardDialog(
    currentOrder: List<DashboardCardType>,
    hiddenCards: Set<DashboardCardType>,
    onDismiss: () -> Unit,
    onSave: (List<DashboardCardType>, Set<DashboardCardType>) -> Unit
) {
    var order by remember { mutableStateOf(currentOrder) }
    var hidden by remember { mutableStateOf(hiddenCards) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize Dashboard") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(order.size) { index ->
                    val card = order[index]
                    val visible = card !in hidden
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = visible,
                                onCheckedChange = { checked ->
                                    hidden = if (checked) hidden - card else hidden + card
                                }
                            )
                            Text(
                                text = card.defaultTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val mutable = order.toMutableList()
                                        val tmp = mutable[index - 1]
                                        mutable[index - 1] = mutable[index]
                                        mutable[index] = tmp
                                        order = mutable
                                    }
                                },
                                enabled = index > 0
                            ) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                            }
                            IconButton(
                                onClick = {
                                    if (index < order.lastIndex) {
                                        val mutable = order.toMutableList()
                                        val tmp = mutable[index + 1]
                                        mutable[index + 1] = mutable[index]
                                        mutable[index] = tmp
                                        order = mutable
                                    }
                                },
                                enabled = index < order.lastIndex
                            ) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(order, hidden)
                    onDismiss()
                },
                modifier = Modifier.testTag("save_dashboard_layout_button")
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
