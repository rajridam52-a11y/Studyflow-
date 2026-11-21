package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.*
import com.example.reminders.SmartRescheduleEngine
import com.example.ui.components.EmptyStudyState
import com.example.ui.components.PriorityBadge
import com.example.ui.components.StudyProgressRing
import com.example.ui.components.StudyWeeklyBarChart
import com.example.viewmodel.MainStudyFlowViewModel
import com.example.viewmodel.StudyDateFormatters
import com.example.viewmodel.StudyFlowDashboardData
import java.util.Calendar
import java.util.concurrent.TimeUnit

private val MotivationalMessages = listOf(
    "Small focused sessions every day build unstoppable momentum.",
    "Progress beats perfection — finish one priority task first.",
    "Reviewing a little today saves hours of cramming later.",
    "Consistent habits turn big academic goals into calm routines.",
    "Take mindful breaks so your focus stays sharp all week."
)

@Composable
fun HomeScreen(
    data: StudyFlowDashboardData,
    preferences: AppPreferences,
    viewModel: MainStudyFlowViewModel,
    onOpenSearch: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenCustomizeDashboard: () -> Unit,
    onOpenQuickAdd: () -> Unit,
    onNavigateTab: (String) -> Unit,
    onNavigateToStudyTool: (Int) -> Unit
) {
    val unreadCount = remember(data.notifications) { data.notifications.count { !it.read } }
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val motivationalQuote = remember {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        MotivationalMessages[day % MotivationalMessages.size]
    }
    val studentName = data.profile?.displayName?.split(" ")?.firstOrNull().orEmpty().ifBlank { "Student" }
    val visibleCards = remember(preferences.dashboardOrder, preferences.hiddenDashboardCards) {
        preferences.dashboardOrder.filter { it !in preferences.hiddenDashboardCards }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header: Greeting, Date, Actions
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = StudyDateFormatters.formatFullDateHeader(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$greeting, $studentName",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onOpenSearch,
                        modifier = Modifier.testTag("home_search_button")
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "Search StudyFlow")
                    }
                    IconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier.testTag("home_notifications_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge { Text("$unreadCount") }
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Notifications, contentDescription = "Notification Center")
                        }
                    }
                    IconButton(
                        onClick = onOpenCustomizeDashboard,
                        modifier = Modifier.testTag("home_customize_button")
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = "Customize Dashboard")
                    }
                }
            }
        }

        // Motivational + Streak Banner
        item(key = "hero_banner") {
            ElevatedCard(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocalFireDepartment,
                                contentDescription = "Study Streak",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${data.profile?.currentStreak ?: 0}-day study streak",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        AssistChip(
                            onClick = { onNavigateToStudyTool(0) },
                            label = {
                                Text("${data.analytics.todayFocusMinutes}/${data.profile?.dailyGoalMinutes ?: 90}m today")
                            }
                        )
                    }
                    Text(
                        text = motivationalQuote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilledTonalButton(
                                onClick = { onNavigateToStudyTool(0) },
                                modifier = Modifier.testTag("home_start_focus_btn")
                            ) {
                                Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Focus Timer")
                            }
                        }
                        item {
                            FilledTonalButton(
                                onClick = { onNavigateToStudyTool(3) },
                                modifier = Modifier.testTag("home_open_ai_btn")
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("AI Tutor")
                            }
                        }
                        item {
                            FilledTonalButton(
                                onClick = onOpenQuickAdd,
                                modifier = Modifier.testTag("home_quick_add_chip")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Quick Add")
                            }
                        }
                    }
                }
            }
        }

        // Customizable Cards in User-Defined Order
        if (visibleCards.isEmpty()) {
            item(key = "all_hidden") {
                EmptyStudyState(
                    icon = Icons.Filled.DashboardCustomize,
                    title = "Dashboard Cards Hidden",
                    subtitle = "Tap Customize above to show timetable, homework, reminders, and analytics cards.",
                    actionLabel = "Customize Cards",
                    onAction = onOpenCustomizeDashboard
                )
            }
        } else {
            items(visibleCards, key = { it.name }) { cardType ->
                when (cardType) {
                    DashboardCardType.TODAY_TIMETABLE -> TodayTimetableCard(
                        data = data,
                        onOpenPlanner = { onNavigateTab("planner") }
                    )
                    DashboardCardType.PENDING_HOMEWORK -> PendingHomeworkCard(
                        data = data,
                        onToggleComplete = viewModel::toggleHomeworkCompleted,
                        onOpenPlanner = { onNavigateTab("planner") }
                    )
                    DashboardCardType.UPCOMING_EXAMS -> UpcomingExamsCard(
                        data = data,
                        onOpenPlanner = { onNavigateTab("planner") }
                    )
                    DashboardCardType.TODAY_REMINDERS -> TodayRemindersCard(
                        data = data,
                        onComplete = viewModel::completeReminder,
                        onSnooze = { viewModel.snoozeReminder(it, 15) },
                        onOpenReminders = { onNavigateTab("reminders") }
                    )
                    DashboardCardType.DAILY_GOALS -> DailyGoalsCard(
                        data = data,
                        onIncrement = viewModel::incrementGoal,
                        onQuickAdd = onOpenQuickAdd
                    )
                    DashboardCardType.STUDY_STATS -> StudyStatsDashboardCard(
                        data = data,
                        onOpenProgress = { onNavigateToStudyTool(4) }
                    )
                    DashboardCardType.REVISION_PROGRESS -> RevisionProgressCard(
                        data = data,
                        onCompleteRevision = { viewModel.completeRevisionAndSuggestNext(it, true) },
                        onOpenRevisions = { onNavigateToStudyTool(1) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardSectionCard(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
            content()
        }
    }
}

@Composable
private fun TodayTimetableCard(
    data: StudyFlowDashboardData,
    onOpenPlanner: () -> Unit
) {
    val todayDay = StudyDateFormatters.currentIsoDayOfWeek()
    val todaySessions = remember(data.timetable, todayDay) {
        data.timetable.filter { it.dayOfWeek == todayDay }.sortedBy { it.startMinuteOfDay }
    }
    val freeSlots = remember(data.timetable, todayDay, data.profile) {
        SmartRescheduleEngine.findFreeSlotsForDay(
            dayOfWeek = todayDay,
            sessions = data.timetable,
            preferredStartHour = data.profile?.preferredStartHour ?: 8,
            preferredEndHour = data.profile?.preferredEndHour ?: 21,
            minDurationMinutes = 30
        )
    }

    DashboardSectionCard(
        title = "Today's Timetable (${SmartRescheduleEngine.dayName(todayDay)})",
        actionLabel = "View Planner",
        onAction = onOpenPlanner
    ) {
        if (todaySessions.isEmpty()) {
            Text(
                text = "No scheduled classes or study blocks for today yet. Add your timetable in Planner.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            todaySessions.take(4).forEach { s ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = s.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${s.subjectName.ifBlank { s.sessionKind }} • ${SmartRescheduleEngine.formatMinutes(s.startMinuteOfDay)} - ${SmartRescheduleEngine.formatMinutes(s.endMinuteOfDay)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AssistChip(onClick = onOpenPlanner, label = { Text(s.sessionKind) })
                    }
                }
            }
        }
        if (freeSlots.isNotEmpty()) {
            val nextFree = freeSlots.first()
            Text(
                text = "Open study window: ${SmartRescheduleEngine.formatMinutes(nextFree.startMinuteOfDay)} - ${SmartRescheduleEngine.formatMinutes(nextFree.endMinuteOfDay)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PendingHomeworkCard(
    data: StudyFlowDashboardData,
    onToggleComplete: (HomeworkEntity) -> Unit,
    onOpenPlanner: () -> Unit
) {
    val pending = remember(data.homework) {
        data.homework.filter { !it.completed }.sortedBy { it.dueAtMillis }
    }
    DashboardSectionCard(
        title = "Pending Homework & Deadlines",
        actionLabel = "All (${data.homework.size})",
        onAction = onOpenPlanner
    ) {
        LinearProgressIndicator(
            progress = { data.analytics.homeworkCompletionPercent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        )
        Text(
            text = "${data.analytics.homeworkCompletionPercent}% completed (${data.analytics.homeworkCompleted}/${data.analytics.homeworkTotal})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (pending.isEmpty()) {
            Text(
                text = "No pending homework right now. Enjoy your free time or plan ahead!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            pending.take(4).forEach { hw ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = hw.completed,
                            onCheckedChange = { onToggleComplete(hw) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = hw.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${hw.subjectName} • ${StudyDateFormatters.formatCountdown(hw.dueAtMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (hw.dueAtMillis < System.currentTimeMillis()) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        PriorityBadge(hw.priority)
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingExamsCard(
    data: StudyFlowDashboardData,
    onOpenPlanner: () -> Unit
) {
    val now = System.currentTimeMillis()
    val upcoming = remember(data.exams, now) {
        data.exams.filter { it.examAtMillis >= now - TimeUnit.HOURS.toMillis(6) }
            .sortedBy { it.examAtMillis }
    }
    DashboardSectionCard(
        title = "Upcoming Exams",
        actionLabel = "Exam Prep",
        onAction = onOpenPlanner
    ) {
        if (upcoming.isEmpty()) {
            Text(
                text = "No upcoming exams scheduled. Add exam dates and syllabi in Planner.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            upcoming.take(3).forEach { exam ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = exam.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${exam.subjectName} • ${StudyDateFormatters.formatDateTime(exam.examAtMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AssistChip(
                            onClick = onOpenPlanner,
                            label = { Text(StudyDateFormatters.formatCountdown(exam.examAtMillis)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayRemindersCard(
    data: StudyFlowDashboardData,
    onComplete: (ReminderEntity) -> Unit,
    onSnooze: (ReminderEntity) -> Unit,
    onOpenReminders: () -> Unit
) {
    val endToday = StudyDateFormatters.endOfTodayMillis()
    val active = remember(data.reminders, endToday) {
        data.reminders.filter {
            it.status in listOf("UPCOMING", "SNOOZED", "MISSED") &&
                it.triggerAtMillis <= endToday + TimeUnit.DAYS.toMillis(1)
        }.sortedBy { it.triggerAtMillis }
    }
    DashboardSectionCard(
        title = "Today's Reminders",
        actionLabel = "Manage",
        onAction = onOpenReminders
    ) {
        if (active.isEmpty()) {
            Text(
                text = "No reminders due today. Create smart reminders with custom repeats anytime.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            active.take(4).forEach { r ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (r.status == "MISSED") {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = r.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${r.status} • ${StudyDateFormatters.formatDateTime(r.triggerAtMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onSnooze(r) }) {
                            Icon(Icons.Filled.Snooze, contentDescription = "Snooze 15m")
                        }
                        IconButton(onClick = { onComplete(r) }) {
                            Icon(Icons.Filled.CheckCircleOutline, contentDescription = "Complete reminder")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyGoalsCard(
    data: StudyFlowDashboardData,
    onIncrement: (GoalEntity, Int) -> Unit,
    onQuickAdd: () -> Unit
) {
    DashboardSectionCard(
        title = "Daily & Study Goals",
        actionLabel = "+ Goal",
        onAction = onQuickAdd
    ) {
        if (data.goals.isEmpty()) {
            Text(
                text = "No goals set yet. Tap + Goal to track daily focus minutes, pages, or chapters.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            data.goals.take(4).forEach { goal ->
                val ratio = (goal.currentValue.toFloat() / goal.targetValue.coerceAtLeast(1)).coerceIn(0f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = goal.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${goal.currentValue}/${goal.targetValue} ${goal.unit}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = { onIncrement(goal, 10) },
                            enabled = !goal.completed
                        ) {
                            Icon(Icons.Filled.AddCircleOutline, contentDescription = "Advance goal")
                        }
                    }
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyStatsDashboardCard(
    data: StudyFlowDashboardData,
    onOpenProgress: () -> Unit
) {
    DashboardSectionCard(
        title = "Study-Time & Academic Progress",
        actionLabel = "Full Report",
        onAction = onOpenProgress
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
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
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Weekly study time: ${data.analytics.weekFocusMinutes} mins total",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        StudyWeeklyBarChart(dailyMinutes = data.analytics.dailyMinutesLast7Days)
    }
}

@Composable
private fun RevisionProgressCard(
    data: StudyFlowDashboardData,
    onCompleteRevision: (RevisionScheduleEntity) -> Unit,
    onOpenRevisions: () -> Unit
) {
    val due = remember(data.revisions) {
        data.revisions.filter { !it.completed }.sortedBy { it.scheduledAtMillis }
    }
    DashboardSectionCard(
        title = "Revision Progress",
        actionLabel = "Revise",
        onAction = onOpenRevisions
    ) {
        LinearProgressIndicator(
            progress = { data.analytics.revisionPercent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        )
        Text(
            text = "${data.analytics.revisionCompleted} of ${data.analytics.revisionTotal} scheduled revisions completed",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (due.isEmpty()) {
            Text(
                text = "All scheduled spaced revisions are up to date.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            due.take(3).forEach { rev ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(rev.chapterTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            "${rev.subjectName} • Every ${rev.intervalDays}d",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(onClick = { onCompleteRevision(rev) }) {
                        Text("Done")
                    }
                }
            }
        }
    }
}
