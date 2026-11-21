package com.example.ui.screens.study

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.viewmodel.*
import java.util.concurrent.TimeUnit

@Composable
fun AiTutorPane(
    data: StudyFlowDashboardData,
    mainViewModel: MainStudyFlowViewModel,
    aiViewModel: AiAssistantViewModel
) {
    val uiState by aiViewModel.uiState.collectAsStateWithLifecycle()
    val gradeLevel = data.profile?.gradeLevel ?: "High School"

    var selectedQuizAnswers by remember(uiState.responseState) { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var quizRecorded by remember(uiState.responseState) { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_tutor_pane"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "ai_banner") {
            ElevatedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_ai_tutor),
                            contentDescription = "AI Study Companion illustration",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(MaterialTheme.shapes.medium)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("StudyFlow AI Companion", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Adapted for $gradeLevel • ${uiState.requestsUsedToday}/${uiState.dailyLimit} used today",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.ai_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item(key = "ai_modes") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Study Mode", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AiToolMode.entries.take(3).forEach { mode ->
                        FilterChip(
                            selected = uiState.mode == mode,
                            onClick = { aiViewModel.selectMode(mode) },
                            label = { Text(mode.label, maxLines = 1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AiToolMode.entries.drop(3).forEach { mode ->
                        FilterChip(
                            selected = uiState.mode == mode,
                            onClick = { aiViewModel.selectMode(mode) },
                            label = { Text(mode.label, maxLines = 1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item(key = "ai_input") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (data.notes.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(
                            onClick = {
                                val latestNote = data.notes.first()
                                aiViewModel.updatePrompt("${latestNote.title}:\n${latestNote.content}")
                            },
                            leadingIcon = { Icon(Icons.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Load Latest Note") }
                        )
                    }
                }
                OutlinedTextField(
                    value = uiState.promptInput,
                    onValueChange = aiViewModel::updatePrompt,
                    label = {
                        Text(
                            when (uiState.mode) {
                                AiToolMode.TUTOR -> "Ask a concept or step-by-step problem..."
                                AiToolMode.SUMMARIZE -> "Paste your lecture or chapter notes..."
                                AiToolMode.FLASHCARDS -> "Enter topic or paste notes for flashcards..."
                                AiToolMode.QUIZ -> "Enter topic or syllabus for a 5-question quiz..."
                                AiToolMode.REVISION_PLAN -> "Enter exam subject and chapters to plan..."
                            }
                        )
                    },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_prompt_input")
                )
                Button(
                    onClick = { aiViewModel.runAssistant(gradeLevel) },
                    enabled = uiState.responseState !is AiResponseState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("ai_submit_button")
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Run ${uiState.mode.label}")
                }
            }
        }

        item(key = "ai_output") {
            when (val resp = uiState.responseState) {
                AiResponseState.Idle -> {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Choose a mode above and enter a topic, question, or study notes to begin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                AiResponseState.Loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Preparing educational response...")
                    }
                }
                is AiResponseState.StreamingText -> {
                    ElevatedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = resp.text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                is AiResponseState.TextSuccess -> {
                    ElevatedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("AI Study Explanation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(text = resp.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                is AiResponseState.FlashcardsSuccess -> {
                    ElevatedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Generated Flashcards (${resp.cards.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                FilledTonalButton(
                                    onClick = {
                                        mainViewModel.saveGeneratedFlashcards(
                                            cards = resp.cards.map { it.question to it.answer },
                                            subjectId = "general",
                                            subjectName = "AI Generated"
                                        )
                                    }
                                ) {
                                    Text("Save All to Deck")
                                }
                            }
                            resp.cards.forEach { card ->
                                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                        Text("Q: ${card.question}", style = MaterialTheme.typography.titleSmall)
                                        Spacer(Modifier.height(4.dp))
                                        Text("A: ${card.answer}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                is AiResponseState.QuizSuccess -> {
                    ElevatedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Practice Quiz (${resp.questions.size} questions)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            resp.questions.forEachIndexed { qIdx, q ->
                                val chosen = selectedQuizAnswers[qIdx]
                                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("${qIdx + 1}. ${q.question}", style = MaterialTheme.typography.titleSmall)
                                        q.options.forEachIndexed { oIdx, opt ->
                                            OutlinedButton(
                                                onClick = {
                                                    if (chosen == null) {
                                                        selectedQuizAnswers = selectedQuizAnswers + (qIdx to oIdx)
                                                    }
                                                },
                                                colors = if (chosen != null) {
                                                    ButtonDefaults.outlinedButtonColors(
                                                        containerColor = when {
                                                            oIdx == q.correctIndex -> MaterialTheme.colorScheme.tertiaryContainer
                                                            oIdx == chosen -> MaterialTheme.colorScheme.errorContainer
                                                            else -> MaterialTheme.colorScheme.surface
                                                        }
                                                    )
                                                } else ButtonDefaults.outlinedButtonColors(),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(opt, modifier = Modifier.weight(1f))
                                            }
                                        }
                                        if (chosen != null) {
                                            Text(
                                                text = q.explanation,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (chosen != q.correctIndex) {
                                                TextButton(
                                                    onClick = {
                                                        aiViewModel.explainQuizMistake(
                                                            question = q.question,
                                                            chosenOption = q.options[chosen],
                                                            correctOption = q.options.getOrElse(q.correctIndex) { "" },
                                                            gradeLevel = gradeLevel
                                                        )
                                                    }
                                                ) {
                                                    Text("Explain Mistake Step-by-Step")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (selectedQuizAnswers.size == resp.questions.size && !quizRecorded) {
                                val correct = resp.questions.indices.count { selectedQuizAnswers[it] == resp.questions[it].correctIndex }
                                Button(
                                    onClick = {
                                        quizRecorded = true
                                        mainViewModel.recordQuizResult(
                                            title = "AI Practice Quiz",
                                            subjectId = null,
                                            subjectName = "AI Tutor",
                                            correctCount = correct,
                                            totalCount = resp.questions.size
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Save Quiz Score ($correct/${resp.questions.size})")
                                }
                            }
                        }
                    }
                }
                is AiResponseState.RevisionPlanSuccess -> {
                    ElevatedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Suggested Revision Plan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                FilledTonalButton(
                                    onClick = {
                                        val now = System.currentTimeMillis()
                                        resp.steps.forEach { step ->
                                            mainViewModel.scheduleRevision(
                                                subjectId = "general",
                                                subjectName = "AI Plan",
                                                chapterTitle = "${step.topic} (${step.activity})",
                                                scheduledAtMillis = now + TimeUnit.DAYS.toMillis(step.dayOffset.toLong().coerceAtLeast(1L)),
                                                intervalDays = step.dayOffset.coerceIn(1, 30)
                                            )
                                        }
                                    }
                                ) {
                                    Text("Add All to Revisions")
                                }
                            }
                            resp.steps.forEach { step ->
                                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                        Text("Day +${step.dayOffset} • ${step.topic} (${step.durationMinutes}m)", style = MaterialTheme.typography.titleSmall)
                                        Text(step.activity, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                is AiResponseState.Error -> {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = resp.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            TextButton(onClick = { aiViewModel.runAssistant(gradeLevel) }) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }
}
