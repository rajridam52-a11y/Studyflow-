package com.example.ui.screens.study

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.ui.components.EmptyStudyState
import com.example.ui.components.StudyProgressRing
import com.example.ui.components.StudyWeeklyBarChart
import com.example.viewmodel.MainStudyFlowViewModel
import com.example.viewmodel.StudyDateFormatters
import com.example.viewmodel.StudyFlowDashboardData

@Composable
fun ProgressAndAttendancePane(
    data: StudyFlowDashboardData,
    viewModel: MainStudyFlowViewModel
) {
    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            viewModel.exportProgressCsv(uri)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("progress_attendance_pane"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Real Progress Summary + Export CSV
        item(key = "analytics_summary") {
            ElevatedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Academic Progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        FilledTonalButton(
                            onClick = { csvLauncher.launch("StudyFlow-Report-${StudyDateFormatters.todayDayKey()}.csv") },
                            modifier = Modifier.testTag("export_csv_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("CSV Report")
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StudyProgressRing(
                            progress = data.analytics.homeworkCompletionPercent / 100f,
                            label = "Homework",
                            valueText = "${data.analytics.homeworkCompletionPercent}%"
                        )
                        StudyProgressRing(
                            progress = data.analytics.revisionPercent / 100f,
                            label = "Revisions",
                            valueText = "${data.analytics.revisionPercent}%",
                            ringColor = MaterialTheme.colorScheme.secondary
                        )
                        StudyProgressRing(
                            progress = data.analytics.overallAttendancePercent / 100f,
                            label = "Attendance",
                            valueText = "${data.analytics.overallAttendancePercent}%",
                            ringColor = MaterialTheme.colorScheme.tertiary
                        )
                    }

                    HorizontalDivider()

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${data.analytics.todayFocusMinutes}m", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("7 Days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${data.analytics.weekFocusMinutes}m", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("30 Days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${data.analytics.monthFocusMinutes}m", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Quiz Avg", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${data.analytics.averageQuizPercent}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }

                    StudyWeeklyBarChart(dailyMinutes = data.analytics.dailyMinutesLast7Days)
                }
            }
        }

        // Low Attendance Alert
        if (data.analytics.lowAttendanceSubjectNames.isNotEmpty()) {
            item(key = "low_attendance_warning") {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            text = "Low attendance alert: ${data.analytics.lowAttendanceSubjectNames.joinToString(", ")} dropped below target attendance.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Attendance Tracker per Subject
        item(key = "attendance_header") {
            Text(
                text = "Subject Attendance Tracker (${data.analytics.overallAttendancePercent}%)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (data.subjects.isEmpty()) {
            item(key = "attendance_no_subjects") {
                EmptyStudyState(
                    icon = Icons.Filled.HowToReg,
                    title = "Add Subjects to Track Attendance",
                    subtitle = "Create subjects in Profile -> Manage Subjects (or via Quick Add) to log Present/Absent records."
                )
            }
        } else {
            items(data.subjects, key = { it.id }) { subject ->
                val records = remember(data.attendance, subject.id) {
                    data.attendance.filter { it.subjectId == subject.id }
                }
                val presentCount = records.count { it.present }
                val pct = if (records.isEmpty()) 100 else (presentCount * 100 / records.size)

                ElevatedCard(shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(subject.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = "$presentCount/${records.size} classes • $pct% (Target: ${subject.targetAttendancePercent}%)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (records.isNotEmpty() && pct < subject.targetAttendancePercent) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(
                                onClick = { viewModel.markAttendance(subject.id, subject.name, present = true) }
                            ) { Text("Present") }
                            OutlinedButton(
                                onClick = { viewModel.markAttendance(subject.id, subject.name, present = false) }
                            ) { Text("Absent") }
                        }
                    }
                }
            }
        }

        // Streaks & Supportive Achievements
        item(key = "achievements_section") {
            ElevatedCard(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Streaks & Milestones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        AssistChip(
                            onClick = {},
                            label = { Text("Best: ${data.profile?.longestStreak ?: 0}d") }
                        )
                    }
                    Text(
                        text = "Current steady streak: ${data.profile?.currentStreak ?: 0} day(s). Every completed focus session or assignment counts at your own pace.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (data.achievements.isEmpty()) {
                        Text(
                            text = "Complete your first focus session, homework, quiz, or revision to unlock milestone badges!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        data.achievements.forEach { ach ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column {
                                        Text(ach.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        Text(ach.description, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
