package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.screens.planner.ExamsAndCalendarTab
import com.example.ui.screens.planner.HomeworkTab
import com.example.ui.screens.planner.TimetableTab
import com.example.viewmodel.MainStudyFlowViewModel
import com.example.viewmodel.StudyFlowDashboardData

@Composable
fun PlannerScreen(
    data: StudyFlowDashboardData,
    viewModel: MainStudyFlowViewModel
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Homework", "Timetable", "Exams & Calendar")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("planner_screen")
    ) {
        Text(
            text = "Academic Planner",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp)
        )
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, maxLines = 1) }
                )
            }
        }
        when (selectedTab) {
            0 -> HomeworkTab(data = data, viewModel = viewModel)
            1 -> TimetableTab(data = data, viewModel = viewModel)
            else -> ExamsAndCalendarTab(data = data, viewModel = viewModel)
        }
    }
}
