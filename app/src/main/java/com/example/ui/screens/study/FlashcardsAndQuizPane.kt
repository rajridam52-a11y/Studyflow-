package com.example.ui.screens.study

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.FlashcardEntity
import com.example.ui.components.EmptyStudyState
import com.example.ui.components.SubjectPickerRow
import com.example.viewmodel.MainStudyFlowViewModel
import com.example.viewmodel.StudyDateFormatters
import com.example.viewmodel.StudyFlowDashboardData

@Composable
fun FlashcardsAndQuizPane(
    data: StudyFlowDashboardData,
    viewModel: MainStudyFlowViewModel,
    onAskAiToExplainMistake: (question: String, chosen: String, correct: String) -> Unit,
    onOpenAiQuizGenerator: () -> Unit
) {
    var mode by remember { mutableIntStateOf(0) } // 0 = Flashcards, 1 = Deck Quiz & History

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
                label = { Text("Flashcards (${data.flashcards.size})") }
            )
            FilterChip(
                selected = mode == 1,
                onClick = { mode = 1 },
                label = { Text("Practice Quiz (${data.quizResults.size})") }
            )
        }
        if (mode == 0) {
            FlashcardsDeckSection(data = data, viewModel = viewModel, onOpenAi = onOpenAiQuizGenerator)
        } else {
            PracticeQuizSection(
                data = data,
                viewModel = viewModel,
                onAskAiToExplainMistake = onAskAiToExplainMistake,
                onOpenAiQuizGenerator = onOpenAiQuizGenerator
            )
        }
    }
}

@Composable
private fun FlashcardsDeckSection(
    data: StudyFlowDashboardData,
    viewModel: MainStudyFlowViewModel,
    onOpenAi: () -> Unit
) {
    var filterDifficult by remember { mutableStateOf(false) }
    var showCreateCard by remember { mutableStateOf(false) }
    var studyIndex by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }

    val deck = remember(data.flashcards, filterDifficult) {
        if (filterDifficult) data.flashcards.filter { it.difficult } else data.flashcards
    }
    val activeCard = deck.getOrNull(studyIndex.coerceIn(0, (deck.size - 1).coerceAtLeast(0)))

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("flashcards_section"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "fc_controls") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = filterDifficult,
                    onClick = {
                        filterDifficult = !filterDifficult
                        studyIndex = 0
                        revealed = false
                    },
                    label = { Text("Difficult Only") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onOpenAi) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("AI Cards")
                    }
                    FilledTonalButton(
                        onClick = { showCreateCard = true },
                        modifier = Modifier.testTag("add_flashcard_button")
                    ) {
                        Text("+ Card")
                    }
                }
            }
        }

        if (deck.isEmpty()) {
            item(key = "fc_empty") {
                EmptyStudyState(
                    icon = Icons.Filled.Style,
                    title = "No Flashcards Yet",
                    subtitle = "Create active-recall flashcards manually or let the AI Tutor generate a study deck from your notes.",
                    actionLabel = "Create Flashcard",
                    onAction = { showCreateCard = true }
                )
            }
        } else if (activeCard != null) {
            item(key = "fc_active_flip") {
                ElevatedCard(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (revealed) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { revealed = !revealed }
                        .testTag("flashcard_flip_surface")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "${activeCard.subjectName} • Card ${(studyIndex % deck.size) + 1} of ${deck.size}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = if (revealed) "ANSWER" else "QUESTION (Tap to Flip)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (revealed) activeCard.answer else activeCard.question,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.updateFlashcardReview(activeCard, mastered = false, difficult = true)
                                    studyIndex = (studyIndex + 1) % deck.size
                                    revealed = false
                                }
                            ) {
                                Text("Needs Practice")
                            }
                            Button(
                                onClick = {
                                    viewModel.updateFlashcardReview(activeCard, mastered = true, difficult = false)
                                    studyIndex = (studyIndex + 1) % deck.size
                                    revealed = false
                                }
                            ) {
                                Text("Mastered")
                            }
                        }
                    }
                }
            }

            items(deck, key = { it.id }) { card ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(card.question, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(card.answer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { viewModel.deleteFlashcard(card.id) }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete flashcard")
                        }
                    }
                }
            }
        }
    }

    if (showCreateCard) {
        var selectedSubject by remember { mutableStateOf(data.subjects.firstOrNull()) }
        var question by remember { mutableStateOf("") }
        var answer by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateCard = false },
            title = { Text("Create Flashcard") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SubjectPickerRow(
                        subjects = data.subjects,
                        selectedSubjectId = selectedSubject?.id ?: "general",
                        onSelect = { selectedSubject = it }
                    )
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        label = { Text("Front (Question)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = answer,
                        onValueChange = { answer = it },
                        label = { Text("Back (Answer)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveFlashcard(
                            subjectId = selectedSubject?.id ?: "general",
                            subjectName = selectedSubject?.name ?: "General",
                            chapterName = "",
                            question = question,
                            answer = answer
                        )
                        showCreateCard = false
                    },
                    enabled = question.isNotBlank() && answer.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateCard = false }) { Text("Cancel") }
            }
        )
    }
}

private data class DeckQuizQuestion(
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int
)

@Composable
private fun PracticeQuizSection(
    data: StudyFlowDashboardData,
    viewModel: MainStudyFlowViewModel,
    onAskAiToExplainMistake: (question: String, chosen: String, correct: String) -> Unit,
    onOpenAiQuizGenerator: () -> Unit
) {
    val generatedFromDeck = remember(data.flashcards) {
        if (data.flashcards.size < 4) emptyList()
        else {
            val pool = data.flashcards.shuffled().take(5)
            pool.map { card ->
                val distractors = data.flashcards.filter { it.id != card.id }.shuffled().take(3).map { it.answer }
                val opts = (distractors + card.answer).shuffled()
                DeckQuizQuestion(
                    prompt = card.question,
                    options = opts,
                    correctIndex = opts.indexOf(card.answer)
                )
            }
        }
    }

    var quizRunning by remember { mutableStateOf(false) }
    var currentQIndex by remember { mutableIntStateOf(0) }
    var selectedOption by remember { mutableIntStateOf(-1) }
    var score by remember { mutableIntStateOf(0) }
    var quizFinished by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "quiz_launchers") {
            ElevatedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Practice Quizzes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Test yourself offline using your Flashcards Deck (4+ cards needed) or generate a tailored AI Practice Quiz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                quizRunning = true
                                quizFinished = false
                                currentQIndex = 0
                                selectedOption = -1
                                score = 0
                            },
                            enabled = generatedFromDeck.isNotEmpty()
                        ) {
                            Text("Quiz My Flashcards")
                        }
                        FilledTonalButton(onClick = onOpenAiQuizGenerator) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("AI Practice Quiz")
                        }
                    }
                }
            }
        }

        if (quizRunning && generatedFromDeck.isNotEmpty()) {
            item(key = "active_deck_quiz") {
                if (quizFinished) {
                    ElevatedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Quiz Complete!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("You scored $score out of ${generatedFromDeck.size}.", style = MaterialTheme.typography.bodyLarge)
                            Button(onClick = { quizRunning = false }) { Text("Done") }
                        }
                    }
                } else {
                    val q = generatedFromDeck[currentQIndex]
                    ElevatedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Question ${currentQIndex + 1} of ${generatedFromDeck.size}", style = MaterialTheme.typography.labelLarge)
                            Text(q.prompt, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            q.options.forEachIndexed { idx, opt ->
                                val isSelected = selectedOption == idx
                                OutlinedButton(
                                    onClick = {
                                        if (selectedOption == -1) {
                                            selectedOption = idx
                                            if (idx == q.correctIndex) score++
                                        }
                                    },
                                    colors = if (selectedOption != -1) {
                                        ButtonDefaults.outlinedButtonColors(
                                            containerColor = when {
                                                idx == q.correctIndex -> MaterialTheme.colorScheme.tertiaryContainer
                                                isSelected -> MaterialTheme.colorScheme.errorContainer
                                                else -> MaterialTheme.colorScheme.surface
                                            }
                                        )
                                    } else ButtonDefaults.outlinedButtonColors(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(opt, modifier = Modifier.weight(1f))
                                }
                            }
                            if (selectedOption != -1) {
                                if (selectedOption != q.correctIndex) {
                                    TextButton(
                                        onClick = {
                                            onAskAiToExplainMistake(
                                                q.prompt,
                                                q.options[selectedOption],
                                                q.options[q.correctIndex]
                                            )
                                        }
                                    ) {
                                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Explain Mistake with AI Tutor")
                                    }
                                }
                                Button(
                                    onClick = {
                                        if (currentQIndex < generatedFromDeck.lastIndex) {
                                            currentQIndex++
                                            selectedOption = -1
                                        } else {
                                            quizFinished = true
                                            viewModel.recordQuizResult(
                                                title = "Flashcard Deck Quiz",
                                                subjectId = null,
                                                subjectName = "Mixed Subjects",
                                                correctCount = score,
                                                totalCount = generatedFromDeck.size
                                            )
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text(if (currentQIndex < generatedFromDeck.lastIndex) "Next" else "Finish")
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "quiz_history_header") {
            Text(
                text = "Quiz History (Avg: ${data.analytics.averageQuizPercent}%)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (data.quizResults.isEmpty()) {
            item(key = "quiz_empty") {
                EmptyStudyState(
                    icon = Icons.Filled.FactCheck,
                    title = "No Quizzes Taken Yet",
                    subtitle = "Complete a Flashcards Deck Quiz or an AI Practice Quiz to track your scores over time."
                )
            }
        } else {
            items(data.quizResults, key = { it.id }) { res ->
                val pct = (res.correctCount * 100 / res.totalCount.coerceAtLeast(1))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(res.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${res.subjectName} • ${StudyDateFormatters.formatShortDate(res.takenAtMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AssistChip(onClick = {}, label = { Text("${res.correctCount}/${res.totalCount} ($pct%)") })
                    }
                }
            }
        }
    }
}
