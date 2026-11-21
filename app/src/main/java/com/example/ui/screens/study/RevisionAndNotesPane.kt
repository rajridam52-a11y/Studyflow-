package com.example.ui.screens.study

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.RevisionScheduleEntity
import com.example.data.local.StudyNoteEntity
import com.example.data.local.SubjectEntity
import com.example.ui.components.EmptyStudyState
import com.example.ui.components.SubjectPickerRow
import com.example.viewmodel.MainStudyFlowViewModel
import com.example.viewmodel.StudyDateFormatters
import com.example.viewmodel.StudyFlowDashboardData
import java.util.concurrent.TimeUnit

@Composable
fun RevisionAndNotesPane(
    data: StudyFlowDashboardData,
    viewModel: MainStudyFlowViewModel
) {
    var subMode by remember { mutableIntStateOf(0) } // 0 = Notes, 1 = Smart Revision

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = subMode == 0,
                onClick = { subMode = 0 },
                leadingIcon = { Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text("Notes (${data.notes.size})") }
            )
            FilterChip(
                selected = subMode == 1,
                onClick = { subMode = 1 },
                leadingIcon = { Icon(Icons.Filled.Autorenew, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text("Spaced Revision (${data.revisions.count { !it.completed }})") }
            )
        }
        if (subMode == 0) {
            NotesManagerSection(data = data, viewModel = viewModel)
        } else {
            SmartRevisionSection(data = data, viewModel = viewModel)
        }
    }
}

@Composable
private fun NotesManagerSection(
    data: StudyFlowDashboardData,
    viewModel: MainStudyFlowViewModel
) {
    var search by remember { mutableStateOf("") }
    var onlyBookmarked by remember { mutableStateOf(false) }
    var onlyImportant by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<StudyNoteEntity?>(null) }
    var showAddNote by remember { mutableStateOf(false) }

    val filtered = remember(data.notes, search, onlyBookmarked, onlyImportant) {
        val q = search.trim().lowercase()
        data.notes.filter { n ->
            val matchQ = q.isBlank() ||
                n.title.lowercase().contains(q) ||
                n.content.lowercase().contains(q) ||
                n.subjectName.lowercase().contains(q)
            val matchBm = !onlyBookmarked || n.bookmarked
            val matchImp = !onlyImportant || n.important
            matchQ && matchBm && matchImp
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("notes_section"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "notes_controls") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Offline Study Notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    FilledTonalButton(
                        onClick = { showAddNote = true },
                        modifier = Modifier.testTag("add_note_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New Note")
                    }
                }
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search notes, chapters, keywords...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = onlyBookmarked,
                        onClick = { onlyBookmarked = !onlyBookmarked },
                        label = { Text("Bookmarked") }
                    )
                    FilterChip(
                        selected = onlyImportant,
                        onClick = { onlyImportant = !onlyImportant },
                        label = { Text("Important") }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item(key = "notes_empty") {
                EmptyStudyState(
                    icon = Icons.Filled.Description,
                    title = "No Study Notes Yet",
                    subtitle = "Save offline subject notes with bullets, checklists, and attached whiteboard or textbook photos.",
                    actionLabel = "Create Note",
                    onAction = { showAddNote = true }
                )
            }
        } else {
            items(filtered, key = { it.id }) { note ->
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(note.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${note.subjectName}${if (note.chapterName.isNotBlank()) " • ${note.chapterName}" else ""}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row {
                                IconButton(onClick = { viewModel.toggleNoteImportant(note) }) {
                                    Icon(
                                        imageVector = if (note.important) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        contentDescription = "Mark important",
                                        tint = if (note.important) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { viewModel.toggleNoteBookmark(note) }) {
                                    Icon(
                                        imageVector = if (note.bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                        contentDescription = "Bookmark note",
                                        tint = if (note.bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (note.content.isNotBlank()) {
                            Text(
                                text = note.content,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!note.attachmentUri.isNullOrBlank()) {
                            AsyncImage(
                                model = note.attachmentUri,
                                contentDescription = "Note image attachment",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(MaterialTheme.shapes.small)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(onClick = { editingNote = note }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit note")
                            }
                            IconButton(onClick = { viewModel.deleteNote(note.id) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete note")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddNote || editingNote != null) {
        NoteEditorDialog(
            existing = editingNote,
            subjects = data.subjects,
            onQuickAddSubject = { viewModel.saveSubject(name = it) },
            onDismiss = {
                showAddNote = false
                editingNote = null
            },
            onSave = { id, subjId, subjName, chapter, title, content, imp, bm, attUri ->
                viewModel.saveNote(
                    id = id,
                    subjectId = subjId,
                    subjectName = subjName,
                    chapterName = chapter,
                    title = title,
                    content = content,
                    important = imp,
                    bookmarked = bm,
                    attachmentUri = attUri
                )
                showAddNote = false
                editingNote = null
            }
        )
    }
}

@Composable
private fun NoteEditorDialog(
    existing: StudyNoteEntity?,
    subjects: List<SubjectEntity>,
    onQuickAddSubject: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        id: String?,
        subjId: String,
        subjName: String,
        chapter: String,
        title: String,
        content: String,
        important: Boolean,
        bookmarked: Boolean,
        attachmentUri: String?
    ) -> Unit
) {
    var selectedSubject by remember {
        mutableStateOf(subjects.firstOrNull { it.id == existing?.subjectId } ?: subjects.firstOrNull())
    }
    var chapter by remember { mutableStateOf(existing?.chapterName.orEmpty()) }
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var content by remember { mutableStateOf(existing?.content.orEmpty()) }
    var important by remember { mutableStateOf(existing?.important ?: false) }
    var bookmarked by remember { mutableStateOf(existing?.bookmarked ?: false) }
    var attachmentUri by remember { mutableStateOf(existing?.attachmentUri) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) attachmentUri = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Study Note" else "Edit Note") },
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
                    value = chapter,
                    onValueChange = { chapter = it },
                    label = { Text("Chapter / Folder (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Note title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_title_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = { content += "\n• " }, label = { Text("• Bullet") })
                    AssistChip(onClick = { content += "\n[ ] " }, label = { Text("[ ] Task") })
                    AssistChip(onClick = { content += "**Key Term**: " }, label = { Text("Key Term") })
                }
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Note content") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (attachmentUri == null) "Attach Photo" else "Change Photo")
                    }
                    if (attachmentUri != null) {
                        TextButton(onClick = { attachmentUri = null }) { Text("Remove") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = important,
                        onClick = { important = !important },
                        label = { Text("Important") }
                    )
                    FilterChip(
                        selected = bookmarked,
                        onClick = { bookmarked = !bookmarked },
                        label = { Text("Bookmark") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        existing?.id,
                        selectedSubject?.id ?: "general",
                        selectedSubject?.name ?: "General",
                        chapter,
                        title,
                        content,
                        important,
                        bookmarked,
                        attachmentUri
                    )
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("save_note_button")
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SmartRevisionSection(
    data: StudyFlowDashboardData,
    viewModel: MainStudyFlowViewModel
) {
    var showAddRevision by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("revision_section"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "rev_stats") {
            ElevatedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Spaced Revision: ${data.analytics.revisionPercent}%",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        FilledTonalButton(onClick = { showAddRevision = true }) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Schedule")
                        }
                    }
                    LinearProgressIndicator(
                        progress = { data.analytics.revisionPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    )
                    Text(
                        text = "Completing a revision queues the next interval (3d -> 7d -> 14d), or shorter intervals for difficult chapters.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (data.revisions.isEmpty()) {
            item(key = "rev_empty") {
                EmptyStudyState(
                    icon = Icons.Filled.Autorenew,
                    title = "No Spaced Revisions Yet",
                    subtitle = "Schedule chapters to review at spaced intervals and mark difficult topics for closer follow-up.",
                    actionLabel = "Schedule Revision",
                    onAction = { showAddRevision = true }
                )
            }
        } else {
            items(data.revisions, key = { it.id }) { rev ->
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rev.chapterTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${rev.subjectName} • ${StudyDateFormatters.formatDateTime(rev.scheduledAtMillis)} • Every ${rev.intervalDays}d",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.toggleRevisionDifficult(rev) }) {
                                Icon(
                                    imageVector = if (rev.difficult) Icons.Filled.Warning else Icons.Filled.OutlinedFlag,
                                    contentDescription = "Mark difficult",
                                    tint = if (rev.difficult) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!rev.completed) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(onClick = { viewModel.completeRevisionAndSuggestNext(rev, true) }) {
                                        Text("Done + Queue Next")
                                    }
                                    OutlinedButton(onClick = { viewModel.completeRevisionAndSuggestNext(rev, false) }) {
                                        Text("Done Only")
                                    }
                                }
                            } else {
                                AssistChip(onClick = {}, label = { Text("Completed") })
                            }
                            IconButton(onClick = { viewModel.deleteRevision(rev.id) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete revision")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddRevision) {
        var selectedSubject by remember { mutableStateOf(data.subjects.firstOrNull()) }
        var chapterTitle by remember { mutableStateOf("") }
        var intervalDays by remember { mutableIntStateOf(3) }
        var difficult by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddRevision = false },
            title = { Text("Schedule Chapter Revision") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SubjectPickerRow(
                        subjects = data.subjects,
                        selectedSubjectId = selectedSubject?.id ?: "general",
                        onSelect = { selectedSubject = it }
                    )
                    OutlinedTextField(
                        value = chapterTitle,
                        onValueChange = { chapterTitle = it },
                        label = { Text("Chapter or topic") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("First Review Interval", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 3, 7, 14).forEach { days ->
                            FilterChip(
                                selected = intervalDays == days,
                                onClick = { intervalDays = days },
                                label = { Text("${days}d") }
                            )
                        }
                    }
                    FilterChip(
                        selected = difficult,
                        onClick = { difficult = !difficult },
                        label = { Text("Mark as Difficult Topic") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.scheduleRevision(
                            subjectId = selectedSubject?.id ?: "general",
                            subjectName = selectedSubject?.name ?: "General",
                            chapterTitle = chapterTitle,
                            scheduledAtMillis = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(intervalDays.toLong()),
                            intervalDays = intervalDays,
                            difficult = difficult
                        )
                        showAddRevision = false
                    },
                    enabled = chapterTitle.isNotBlank()
                ) { Text("Schedule") }
            },
            dismissButton = {
                TextButton(onClick = { showAddRevision = false }) { Text("Cancel") }
            }
        )
    }
}
