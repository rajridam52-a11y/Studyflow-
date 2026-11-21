package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.screens.study.AiTutorPane
import com.example.ui.screens.study.FlashcardsAndQuizPane
import com.example.ui.screens.study.FocusTimerPane
import com.example.ui.screens.study.ProgressAndAttendancePane
import com.example.ui.screens.study.RevisionAndNotesPane
import com.example.viewmodel.AiAssistantViewModel
import com.example.viewmodel.AiToolMode
import com.example.viewmodel.FocusTimerViewModel
import com.example.viewmodel.MainStudyFlowViewModel
import com.example.viewmodel.StudyFlowDashboardData

@Composable
fun StudyScreen(
    initialToolIndex: Int,
    onToolIndexConsumed: () -> Unit,
    data: StudyFlowDashboardData,
    mainViewModel: MainStudyFlowViewModel,
    timerViewModel: FocusTimerViewModel,
    aiViewModel: AiAssistantViewModel
) {
    var selectedTool by remember { mutableIntStateOf(initialToolIndex.coerceIn(0, 4)) }

    LaunchedEffect(initialToolIndex) {
        if (initialToolIndex in 0..4) {
            selectedTool = initialToolIndex
            onToolIndexConsumed()
        }
    }

    val tabs = listOf("Timer", "Notes & Revise", "Cards & Quiz", "AI Tutor", "Progress")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("study_screen")
    ) {
        Text(
            text = "Study Studio",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp)
        )
        ScrollableTabRow(
            selectedTabIndex = selectedTool,
            edgePadding = 12.dp
        ) {
            tabs.forEachIndexed { idx, label ->
                Tab(
                    selected = selectedTool == idx,
                    onClick = { selectedTool = idx },
                    text = { Text(label, maxLines = 1) }
                )
            }
        }
        when (selectedTool) {
            0 -> FocusTimerPane(data = data, timerViewModel = timerViewModel)
            1 -> RevisionAndNotesPane(data = data, viewModel = mainViewModel)
            2 -> FlashcardsAndQuizPane(
                data = data,
                viewModel = mainViewModel,
                onAskAiToExplainMistake = { question, chosen, correct ->
                    aiViewModel.explainQuizMistake(
                        question = question,
                        chosenOption = chosen,
                        correctOption = correct,
                        gradeLevel = data.profile?.gradeLevel ?: "High School"
                    )
                    selectedTool = 3
                },
                onOpenAiQuizGenerator = {
                    aiViewModel.selectMode(AiToolMode.QUIZ)
                    selectedTool = 3
                }
            )
            3 -> AiTutorPane(
                data = data,
                mainViewModel = mainViewModel,
                aiViewModel = aiViewModel
            )
            else -> ProgressAndAttendancePane(data = data, viewModel = mainViewModel)
        }
    }
}
