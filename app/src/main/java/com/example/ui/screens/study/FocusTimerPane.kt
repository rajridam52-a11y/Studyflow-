package com.example.ui.screens.study

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.EmptyStudyState
import com.example.viewmodel.FocusTimerMode
import com.example.viewmodel.FocusTimerViewModel
import com.example.viewmodel.StudyDateFormatters
import com.example.viewmodel.StudyFlowDashboardData

@Composable
fun FocusTimerPane(
    data: StudyFlowDashboardData,
    timerViewModel: FocusTimerViewModel
) {
    val state by timerViewModel.state.collectAsStateWithLifecycle()
    val totalSec = (state.selectedMinutes * 60).coerceAtLeast(1)
    val progress = (state.remainingSeconds.toFloat() / totalSec.toFloat()).coerceIn(0f, 1f)
    val mins = state.remainingSeconds / 60
    val secs = state.remainingSeconds % 60
    val timeLabel = String.format("%02d:%02d", mins, secs)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("focus_timer_pane"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item(key = "mode_chips") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FocusTimerMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.mode == mode,
                        onClick = { timerViewModel.selectMode(mode) },
                        enabled = !state.running,
                        label = { Text(mode.label, maxLines = 1) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item(key = "timer_dial") {
            val primaryColor = MaterialTheme.colorScheme.primary
            val trackColor = MaterialTheme.colorScheme.surfaceVariant
            ElevatedCard(
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "${state.mode.label} • ${state.selectedSubjectName}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Box(
                        modifier = Modifier.size(210.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = 14.dp.toPx()
                            val arcSize = Size(size.width - stroke, size.height - stroke)
                            val topLeft = Offset(stroke / 2f, stroke / 2f)
                            drawArc(
                                color = trackColor,
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = primaryColor,
                                startAngle = -90f,
                                sweepAngle = 360f * progress,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = timeLabel,
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Cycle #${state.completedPomodorosInCycle + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!state.running && state.mode == FocusTimerMode.CUSTOM) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Custom Length: ${state.selectedMinutes} min", style = MaterialTheme.typography.labelLarge)
                            Slider(
                                value = state.selectedMinutes.toFloat(),
                                onValueChange = { timerViewModel.setCustomMinutes(it.toInt()) },
                                valueRange = 10f..120f
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (state.running) timerViewModel.pause()
                                else timerViewModel.startOrResume()
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("timer_start_pause_button")
                        ) {
                            Icon(
                                imageVector = if (state.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.running) "Pause" else "Start Focus")
                        }
                        OutlinedButton(
                            onClick = { timerViewModel.completeEarly() },
                            enabled = state.remainingSeconds < state.selectedMinutes * 60,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Log Now")
                        }
                        IconButton(onClick = { timerViewModel.stopAndReset() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Reset timer")
                        }
                    }
                }
            }
        }

        if (data.subjects.isNotEmpty()) {
            item(key = "subject_chips") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Studying Subject", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.selectedSubjectId.isBlank(),
                            onClick = { timerViewModel.selectSubject("", "General Study") },
                            label = { Text("General") }
                        )
                        data.subjects.take(4).forEach { s ->
                            FilterChip(
                                selected = state.selectedSubjectId == s.id,
                                onClick = { timerViewModel.selectSubject(s.id, s.name) },
                                label = { Text(s.name) }
                            )
                        }
                    }
                }
            }
        }

        item(key = "session_summary") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Today", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${data.analytics.todayFocusMinutes} mins", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("This Week", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${data.analytics.weekFocusMinutes} mins", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item(key = "history_title") {
            Text(
                text = "Recent Focus Sessions (${data.studySessions.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (data.studySessions.isEmpty()) {
            item(key = "empty_sessions") {
                EmptyStudyState(
                    icon = Icons.Filled.HourglassBottom,
                    title = "No Logged Sessions Yet",
                    subtitle = "Complete a Pomodoro or custom study block to build your daily streak and focus chart."
                )
            }
        } else {
            items(data.studySessions.take(10), key = { it.id }) { sess ->
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
                        Column {
                            Text(
                                text = "${sess.subjectName.ifBlank { "General Study" }} • ${sess.mode}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = StudyDateFormatters.formatDateTime(sess.startedAtMillis),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AssistChip(onClick = {}, label = { Text("${sess.durationMinutes}m") })
                    }
                }
            }
        }
    }
}
