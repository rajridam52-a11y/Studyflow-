package com.example.viewmodel

import com.example.data.local.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class AcademicAnalytics(
    val todayFocusMinutes: Int = 0,
    val weekFocusMinutes: Int = 0,
    val monthFocusMinutes: Int = 0,
    val totalFocusMinutes: Int = 0,
    val dailyMinutesLast7Days: List<Pair<String, Int>> = emptyList(),
    val homeworkTotal: Int = 0,
    val homeworkCompleted: Int = 0,
    val homeworkOverdue: Int = 0,
    val homeworkCompletionPercent: Int = 0,
    val revisionTotal: Int = 0,
    val revisionCompleted: Int = 0,
    val revisionPercent: Int = 0,
    val overallAttendancePercent: Int = 100,
    val subjectAttendance: Map<String, Int> = emptyMap(),
    val lowAttendanceSubjectNames: List<String> = emptyList(),
    val averageQuizPercent: Int = 0,
    val goalCompletionPercent: Int = 0
)

data class StudyFlowDashboardData(
    val profile: UserProfileEntity? = null,
    val subjects: List<SubjectEntity> = emptyList(),
    val homework: List<HomeworkEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val timetable: List<TimetableSessionEntity> = emptyList(),
    val exams: List<ExamEntity> = emptyList(),
    val chapters: List<ChapterEntity> = emptyList(),
    val revisions: List<RevisionScheduleEntity> = emptyList(),
    val notes: List<StudyNoteEntity> = emptyList(),
    val studySessions: List<StudySessionEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val attendance: List<AttendanceRecordEntity> = emptyList(),
    val flashcards: List<FlashcardEntity> = emptyList(),
    val quizResults: List<QuizResultEntity> = emptyList(),
    val achievements: List<AchievementEntity> = emptyList(),
    val notifications: List<AppNotificationEntity> = emptyList(),
    val calendarEvents: List<CalendarEventEntity> = emptyList(),
    val analytics: AcademicAnalytics = AcademicAnalytics()
)

object StudyDateFormatters {
    fun formatDateTime(millis: Long): String {
        val fmt = SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault())
        return fmt.format(Date(millis))
    }

    fun formatShortDate(millis: Long): String {
        val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
        return fmt.format(Date(millis))
    }

    fun formatFullDateHeader(millis: Long = System.currentTimeMillis()): String {
        val fmt = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        return fmt.format(Date(millis))
    }

    fun formatCountdown(targetMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val diff = targetMillis - nowMillis
        if (diff <= 0L) {
            val agoHours = TimeUnit.MILLISECONDS.toHours(-diff)
            return if (agoHours < 24) "Overdue (${agoHours.coerceAtLeast(1)}h)"
            else "Overdue (${TimeUnit.MILLISECONDS.toDays(-diff)}d)"
        }
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff) % 24
        return when {
            days > 0 -> "${days}d ${hours}h left"
            hours > 0 -> "${hours}h left"
            else -> "${TimeUnit.MILLISECONDS.toMinutes(diff).coerceAtLeast(1)}m left"
        }
    }

    fun todayDayKey(millis: Long = System.currentTimeMillis()): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return fmt.format(Date(millis))
    }

    fun currentIsoDayOfWeek(): Int {
        val javaDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return if (javaDay == Calendar.SUNDAY) 7 else javaDay - 1
    }

    fun startOfTodayMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun endOfTodayMillis(): Long = startOfTodayMillis() + TimeUnit.DAYS.toMillis(1) - 1L
}
