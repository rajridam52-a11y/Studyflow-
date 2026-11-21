package com.example.ui.screens.planner

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.HomeworkEntity
import com.example.data.local.SubjectEntity
import com.example.ui.components.EmptyStudyState
import com.example.ui.components.PriorityBadge
import com.example.ui.components.SubjectPickerRow
import com.example.viewmodel.MainStudyFlowViewModel
import com.example.viewmodel.StudyDateFormatters
import com.example.viewmodel.StudyFlowDashboardData
import java.util.Calendar
import java.util.concurrent.TimeUnit

@Composable
fun HomeworkTab(
    data: StudyFlowDashboardData,
    viewModel: MainStudyFlowViewModel
) {
    var statusTab by remember { mutableIntStateOf(0) } // 0=Pending, 1=Overdue, 2=Completed
    var searchQuery by remember { mutableStateOf("") }
    var filterPriority by remember { mutableStateOf("ALL") }
    var filterSubjectId by remember { mutableStateOf("ALL") }
    var sortByPriorityFirst by remember { mutableStateOf(false) }
    var editingHomework by remember { mutableStateOf<HomeworkEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val filteredList = remember(
        data.homework, statusTab, searchQuery, filterPriority, filterSubjectId, sortByPriorityFirst, now
    ) {
        data.homework.filter { hw ->
            val matchesStatus = when (statusTab) {
                0 -> !hw.completed && hw.dueAtMillis >= now
                1 -> !hw.completed && hw.dueAtMillis < now
                else -> hw.completed
            }
            val q = searchQuery.trim().lowercase()
            val matchesQuery = q.isBlank() ||
                hw.title.lowercase().contains(q) ||
                hw.description.lowercase().contains(q) ||
                hw.subjectName.lowercase().contains(q)
            val matchesPrio = filterPriority == "ALL" || hw.priority == filterPriority
            val matchesSubj = filterSubjectId == "ALL" || hw.subjectId == filterSubjectId
            matchesStatus && matchesQuery && matchesPrio && matchesSubj
        }.let { list ->
            if (sortByPriorityFirst) {
                val order = mapOf("HIGH" to 0, "MEDIUM" to 1, "LOW" to 2)
                list.sortedWith(compareBy<HomeworkEntity> { order[it.priority] ?: 1 }.thenBy { it.dueAtMillis })
            } else {
                list.sortedBy { it.dueAtMillis }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("homework_tab"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "hw_stats") {
            ElevatedCard(
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Completion: ${data.analytics.homeworkCompletionPercent}%",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        FilledTonalButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.testTag("add_homework_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Homework")
                        }
                    }
                    LinearProgressIndicator(
                        progress = { data.analytics.homeworkCompletionPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    )
                    Text(
                        text = "${data.analytics.homeworkCompleted} done • ${data.analytics.homeworkOverdue} overdue • ${data.analytics.homeworkTotal} total",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item(key = "hw_controls") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search homework by title or subject...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("homework_search_input")
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("Pending", "Overdue", "Completed").forEachIndexed { idx, label ->
                        SegmentedButton(
                            selected = statusTab == idx,
                            onClick = { statusTab = idx },
                            shape = SegmentedButtonDefaults.itemShape(idx, 3)
                        ) {
                            Text(label)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("ALL" to "All", "HIGH" to "High", "MEDIUM" to "Med", "LOW" to "Low").forEach { (key, lbl) ->
                        FilterChip(
                            selected = filterPriority == key,
                            onClick = { filterPriority = key },
                            label = { Text(lbl) }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    FilterChip(
                        selected = sortByPriorityFirst,
                        onClick = { sortByPriorityFirst = !sortByPriorityFirst },
                        label = { Text(if (sortByPriorityFirst) "Priority" else "Deadline") }
                    )
                }
                if (data.subjects.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = filterSubjectId == "ALL",
                            onClick = { filterSubjectId = "ALL" },
                            label = { Text("All Subjects") }
                        )
                        data.subjects.take(3).forEach { subj ->
                            FilterChip(
                                selected = filterSubjectId == subj.id,
                                onClick = { filterSubjectId = subj.id },
                                label = { Text(subj.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }
            }
        }

        if (filteredList.isEmpty()) {
            item(key = "hw_empty") {
                EmptyStudyState(
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    title = when (statusTab) {
                        1 -> "No Overdue Homework"
                        2 -> "No Completed Homework Yet"
                        else -> "No Pending Homework"
                    },
                    subtitle = "Tap Add Homework to create an assignment with attachments and automatic deadline reminders.",
                    actionLabel = "Add Homework",
                    onAction = { showAddDialog = true }
                )
            }
        } else {
            items(filteredList, key = { it.id }) { hw ->
                ElevatedCard(
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = hw.completed,
                                onCheckedChange = { viewModel.toggleHomeworkCompleted(hw) },
                                modifier = Modifier.testTag("homework_check_${hw.id}")
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = hw.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    textDecoration = if (hw.completed) TextDecoration.LineThrough else null
                                )
                                Text(
                                    text = "${hw.subjectName} • ${StudyDateFormatters.formatDateTime(hw.dueAtMillis)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            PriorityBadge(hw.priority)
                        }
                        if (hw.description.isNotBlank()) {
                            Text(
                                text = hw.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!hw.attachmentUri.isNullOrBlank()) {
                            if (hw.attachmentLabel == "Image Attachment") {
                                AsyncImage(
                                    model = hw.attachmentUri,
                                    contentDescription = "Homework attachment image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .clip(MaterialTheme.shapes.small)
                                )
                            } else {
                                AssistChip(
                                    onClick = {},
                                    leadingIcon = { Icon(Icons.Filled.AttachFile, contentDescription = null) },
                                    label = { Text(hw.attachmentLabel ?: "Document attached") }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (hw.completed) "Completed" else StudyDateFormatters.formatCountdown(hw.dueAtMillis),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (!hw.completed && hw.dueAtMillis < now) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                            Row {
                                IconButton(onClick = { editingHomework = hw }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit homework")
                                }
                                IconButton(onClick = { viewModel.deleteHomework(hw.id) }) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete homework")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog || editingHomework != null) {
        HomeworkEditorDialog(
            existing = editingHomework,
            subjects = data.subjects,
            onQuickAddSubject = { name -> viewModel.saveSubject(name = name) },
            onDismiss = {
                showAddDialog = false
                editingHomework = null
            },
            onSave = { id, subjId, subjName, title, desc, dueAt, priority, attUri, attLabel, remEnabled ->
                viewModel.saveHomework(
                    id = id,
                    subjectId = subjId,
                    subjectName = subjName,
                    title = title,
                    description = desc,
                    dueAtMillis = dueAt,
                    priority = priority,
                    attachmentUri = attUri,
                    attachmentLabel = attLabel,
                    reminderEnabled = remEnabled
                )
                showAddDialog = false
                editingHomework = null
            }
        )
    }
}

@Composable
private fun HomeworkEditorDialog(
    existing: HomeworkEntity?,
    subjects: List<SubjectEntity>,
    onQuickAddSubject: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        id: String?,
        subjectId: String,
        subjectName: String,
        title: String,
        desc: String,
        dueAtMillis: Long,
        priority: String,
        attachmentUri: String?,
        attachmentLabel: String?,
        reminderEnabled: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    var selectedSubject by remember {
        mutableStateOf(
            subjects.firstOrNull { it.id == existing?.subjectId } ?: subjects.firstOrNull()
        )
    }
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var priority by remember { mutableStateOf(existing?.priority ?: "MEDIUM") }
    var daysFromNow by remember {
        mutableIntStateOf(
            if (existing != null) {
                TimeUnit.MILLISECONDS.toDays((existing.dueAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)).toInt()
            } else 1
        )
    }
    var dueHour by remember {
        mutableIntStateOf(
            if (existing != null) {
                Calendar.getInstance().apply { timeInMillis = existing.dueAtMillis }.get(Calendar.HOUR_OF_DAY)
            } else 18
        )
    }
    var attachmentUri by remember { mutableStateOf(existing?.attachmentUri) }
    var attachmentLabel by remember { mutableStateOf(existing?.attachmentLabel) }
    var reminderEnabled by remember { mutableStateOf(existing?.reminderEnabled ?: true) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            attachmentUri = uri.toString()
            attachmentLabel = "Image Attachment"
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            attachmentUri = uri.toString()
            attachmentLabel = uri.lastPathSegment?.takeLast(32) ?: "Document"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Homework" else "Edit Homework") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    label = { Text("Assignment title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("homework_title_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / instructions") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Due in $daysFromNow day(s) at ${dueHour}:00", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Today", 1 to "Tomorrow", 3 to "+3d", 7 to "+1w").forEach { (d, lbl) ->
                        FilterChip(
                            selected = daysFromNow == d,
                            onClick = { daysFromNow = d },
                            label = { Text(lbl) }
                        )
                    }
                }
                Slider(
                    value = dueHour.toFloat(),
                    onValueChange = { dueHour = it.toInt() },
                    valueRange = 7f..22f,
                    steps = 14
                )
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Image")
                    }
                    OutlinedButton(
                        onClick = { documentPicker.launch(arrayOf("application/pdf", "text/*", "*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Document")
                    }
                }
                if (!attachmentLabel.isNullOrBlank()) {
                    AssistChip(
                        onClick = {
                            attachmentUri = null
                            attachmentLabel = null
                        },
                        label = { Text("Attached: $attachmentLabel (tap to remove)") }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-schedule deadline reminder", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cal = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, daysFromNow)
                        set(Calendar.HOUR_OF_DAY, dueHour)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }
                    onSave(
                        existing?.id,
                        selectedSubject?.id ?: "general",
                        selectedSubject?.name ?: "General",
                        title,
                        description,
                        cal.timeInMillis,
                        priority,
                        attachmentUri,
                        attachmentLabel,
                        reminderEnabled
                    )
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("save_homework_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
