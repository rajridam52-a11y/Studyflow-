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
import com.example.data.local.ChapterEntity
import com.example.data.local.ExamEntity
import com.example.data.local.SubjectEntity
import com.example.ui.components.EmptyStudyState
import com.example.ui.components.SubjectPickerRow
import com.example.viewmodel.MainStudyFlowViewModel
import com.example.viewmodel.StudyDateFormatters
import com.example.viewmodel.StudyFlowDashboardData
import java.util.Calendar
import java.util.concurrent.TimeUnit

@Composable
fun ExamsAndCalendarTab(
    data: StudyFlowDashboardData,
    viewModel: MainStudyFlowViewModel
) {
    var mode by remember { mutableIntStateOf(0) } // 0 = Exams & Syllabus, 1 = Unified Calendar

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = mode == 0,
                onClick = { mode = 0 },
                leadingIcon = { Icon(Icons.Filled.School, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text("Exams & Syllabus") }
            )
            FilterChip(
                selected = mode == 1,
                onClick = { mode = 1 },
                leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text("Calendar") }
            )
        }
        if (mode == 0) {
            ExamsSyllabusSection(data = data, viewModel = viewModel)
        } else {
            CalendarAgendaSection(data = data, viewModel = viewModel)
        }
    }
}

@Composable
private fun ExamsSyllabusSection(
    data: StudyFlowDashboardData,
    viewModel: MainStudyFlowViewModel
) {
    var showAddExam by remember { mutableStateOf(false) }
    var editingExam by remember { mutableStateOf<ExamEntity?>(null) }
    var newChapterText by remember { mutableStateOf("") }
    var activeChapterExamId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("exams_section"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "exam_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Examinations & Mock Tests (${data.exams.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                FilledTonalButton(
                    onClick = { showAddExam = true },
                    modifier = Modifier.testTag("add_exam_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Exam")
                }
            }
        }

        if (data.exams.isEmpty()) {
            item(key = "exam_empty") {
                EmptyStudyState(
                    icon = Icons.Filled.Quiz,
                    title = "No Exams Scheduled",
                    subtitle = "Add upcoming exams, chapter-wise syllabi, difficult topics, and mock test scores.",
                    actionLabel = "Add Exam",
                    onAction = { showAddExam = true }
                )
            }
        } else {
            items(data.exams, key = { it.id }) { exam ->
                val chapters = remember(data.chapters, exam.id) {
                    data.chapters.filter { it.examId == exam.id || (it.examId == null && it.subjectId == exam.subjectId) }
                }
                val doneCount = chapters.count { it.completed }
                ElevatedCard(
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = exam.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${exam.subjectName} • ${StudyDateFormatters.formatDateTime(exam.examAtMillis)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            AssistChip(
                                onClick = {},
                                label = { Text(StudyDateFormatters.formatCountdown(exam.examAtMillis)) }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = {}, label = { Text(exam.preparationStatus.replace("_", " ")) })
                            if (exam.mockTest && exam.obtainedScore != null && exam.maxScore != null) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text("Mock: ${exam.obtainedScore}/${exam.maxScore}") }
                                )
                            }
                        }
                        if (exam.importantTopics.isNotBlank()) {
                            Text(
                                text = "Key Topics: ${exam.importantTopics}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (chapters.isNotEmpty()) {
                            Text(
                                text = "Syllabus Progress ($doneCount/${chapters.size})",
                                style = MaterialTheme.typography.labelLarge
                            )
                            LinearProgressIndicator(
                                progress = { doneCount.toFloat() / chapters.size.coerceAtLeast(1) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                            )
                            chapters.forEach { ch ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = ch.completed,
                                        onCheckedChange = { viewModel.toggleChapterCompleted(ch) }
                                    )
                                    Text(
                                        text = ch.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { viewModel.toggleChapterDifficult(ch) }) {
                                        Icon(
                                            imageVector = if (ch.difficult) Icons.Filled.Warning else Icons.Filled.OutlinedFlag,
                                            contentDescription = "Mark difficult",
                                            tint = if (ch.difficult) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        if (activeChapterExamId == exam.id) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newChapterText,
                                    onValueChange = { newChapterText = it },
                                    label = { Text("Chapter or syllabus topic") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                FilledTonalButton(
                                    onClick = {
                                        if (newChapterText.isNotBlank()) {
                                            viewModel.saveChapter(exam.subjectId, exam.id, newChapterText)
                                            newChapterText = ""
                                            activeChapterExamId = null
                                        }
                                    }
                                ) { Text("Add") }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = {
                                activeChapterExamId = if (activeChapterExamId == exam.id) null else exam.id
                            }) {
                                Text("+ Syllabus Chapter")
                            }
                            Row {
                                IconButton(onClick = { editingExam = exam }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit exam")
                                }
                                IconButton(onClick = { viewModel.deleteExam(exam.id) }) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete exam")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddExam || editingExam != null) {
        ExamEditorDialog(
            existing = editingExam,
            subjects = data.subjects,
            onQuickAddSubject = { viewModel.saveSubject(name = it) },
            onDismiss = {
                showAddExam = false
                editingExam = null
            },
            onSave = { id, subjId, subjName, title, examAt, topics, status, isMock, score, maxScore ->
                viewModel.saveExam(
                    id = id,
                    subjectId = subjId,
                    subjectName = subjName,
                    title = title,
                    examAtMillis = examAt,
                    importantTopics = topics,
                    preparationStatus = status,
                    mockTest = isMock,
                    obtainedScore = score,
                    maxScore = maxScore
                )
                showAddExam = false
                editingExam = null
            }
        )
    }
}

@Composable
private fun ExamEditorDialog(
    existing: ExamEntity?,
    subjects: List<SubjectEntity>,
    onQuickAddSubject: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        id: String?,
        subjId: String,
        subjName: String,
        title: String,
        examAtMillis: Long,
        topics: String,
        status: String,
        mockTest: Boolean,
        obtainedScore: Int?,
        maxScore: Int?
    ) -> Unit
) {
    var selectedSubject by remember {
        mutableStateOf(subjects.firstOrNull { it.id == existing?.subjectId } ?: subjects.firstOrNull())
    }
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var topics by remember { mutableStateOf(existing?.importantTopics.orEmpty()) }
    var status by remember { mutableStateOf(existing?.preparationStatus ?: "IN_PROGRESS") }
    var daysOut by remember {
        mutableIntStateOf(
            if (existing != null) {
                TimeUnit.MILLISECONDS.toDays((existing.examAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)).toInt()
            } else 7
        )
    }
    var mockTest by remember { mutableStateOf(existing?.mockTest ?: false) }
    var scoreText by remember { mutableStateOf(existing?.obtainedScore?.toString().orEmpty()) }
    var maxScoreText by remember { mutableStateOf(existing?.maxScore?.toString() ?: "100") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Examination" else "Edit Examination") },
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
                    label = { Text("Exam / Test Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exam_title_input")
                )
                Text("Days from today: $daysOut", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = daysOut.toFloat(),
                    onValueChange = { daysOut = it.toInt() },
                    valueRange = 0f..60f
                )
                OutlinedTextField(
                    value = topics,
                    onValueChange = { topics = it },
                    label = { Text("Important topics / syllabus notes") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Preparation Status", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("NOT_STARTED", "IN_PROGRESS", "READY", "COMPLETED").forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mock test score record", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = mockTest, onCheckedChange = { mockTest = it })
                }
                if (mockTest) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = scoreText,
                            onValueChange = { scoreText = it.filter(Char::isDigit) },
                            label = { Text("Score") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = maxScoreText,
                            onValueChange = { maxScoreText = it.filter(Char::isDigit) },
                            label = { Text("Max") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cal = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, daysOut)
                        set(Calendar.HOUR_OF_DAY, 9)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }
                    onSave(
                        existing?.id,
                        selectedSubject?.id ?: "general",
                        selectedSubject?.name ?: "General",
                        title,
                        cal.timeInMillis,
                        topics,
                        status,
                        mockTest,
                        scoreText.toIntOrNull(),
                        maxScoreText.toIntOrNull()
                    )
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("save_exam_button")
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private data class CalendarAgendaItem(
    val id: String,
    val kind: String,
    val title: String,
    val subtitle: String,
    val timestampMillis: Long
)

@Composable
private fun CalendarAgendaSection(
    data: StudyFlowDashboardData,
    viewModel: MainStudyFlowViewModel
) {
    var filterKind by remember { mutableStateOf("ALL") }
    var showAddEvent by remember { mutableStateOf(false) }

    val agendaItems = remember(data, filterKind) {
        buildList {
            data.homework.forEach {
                add(CalendarAgendaItem("hw_${it.id}", "HOMEWORK", it.title, "${it.subjectName} • Homework", it.dueAtMillis))
            }
            data.exams.forEach {
                add(CalendarAgendaItem("ex_${it.id}", "EXAM", it.title, "${it.subjectName} • Exam", it.examAtMillis))
            }
            data.revisions.filter { !it.completed }.forEach {
                add(CalendarAgendaItem("rv_${it.id}", "REVISION", "Revise: ${it.chapterTitle}", it.subjectName, it.scheduledAtMillis))
            }
            data.calendarEvents.forEach {
                add(CalendarAgendaItem("ev_${it.id}", "EVENT", it.title, it.description.ifBlank { "Custom Event" }, it.eventAtMillis))
            }
        }.filter { filterKind == "ALL" || it.kind == filterKind }
            .sortedBy { it.timestampMillis }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("calendar_section"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "cal_header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Academic Calendar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    FilledTonalButton(onClick = { showAddEvent = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Event")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL" to "All", "HOMEWORK" to "Homework", "EXAM" to "Exams", "REVISION" to "Revisions", "EVENT" to "Events").forEach { (k, lbl) ->
                        FilterChip(
                            selected = filterKind == k,
                            onClick = { filterKind = k },
                            label = { Text(lbl) }
                        )
                    }
                }
            }
        }

        if (agendaItems.isEmpty()) {
            item(key = "cal_empty") {
                EmptyStudyState(
                    icon = Icons.Filled.EventNote,
                    title = "Calendar Clear",
                    subtitle = "Homework deadlines, exams, revisions, and custom events are unified here automatically.",
                    actionLabel = "Add Event",
                    onAction = { showAddEvent = true }
                )
            }
        } else {
            items(agendaItems, key = { it.id }) { item ->
                ElevatedCard(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = StudyDateFormatters.formatDateTime(item.timestampMillis),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (item.kind == "EVENT") {
                            IconButton(onClick = { viewModel.deleteCalendarEvent(item.id.removePrefix("ev_")) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete event")
                            }
                        } else {
                            AssistChip(onClick = {}, label = { Text(item.kind) })
                        }
                    }
                }
            }
        }
    }

    if (showAddEvent) {
        var evtTitle by remember { mutableStateOf("") }
        var evtDesc by remember { mutableStateOf("") }
        var daysAhead by remember { mutableIntStateOf(1) }

        AlertDialog(
            onDismissRequest = { showAddEvent = false },
            title = { Text("Add Calendar Event") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = evtTitle,
                        onValueChange = { evtTitle = it },
                        label = { Text("Event title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = evtDesc,
                        onValueChange = { evtDesc = it },
                        label = { Text("Description (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("In $daysAhead day(s)", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = daysAhead.toFloat(),
                        onValueChange = { daysAhead = it.toInt() },
                        valueRange = 0f..30f
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cal = Calendar.getInstance().apply {
                            add(Calendar.DAY_OF_YEAR, daysAhead)
                            set(Calendar.HOUR_OF_DAY, 10)
                            set(Calendar.MINUTE, 0)
                        }
                        viewModel.saveCalendarEvent(evtTitle, evtDesc, cal.timeInMillis)
                        showAddEvent = false
                    },
                    enabled = evtTitle.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddEvent = false }) { Text("Cancel") }
            }
        )
    }
}
