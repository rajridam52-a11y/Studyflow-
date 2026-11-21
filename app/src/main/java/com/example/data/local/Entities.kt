package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "user_profiles",
    indices = [Index("userId")]
)
data class UserProfileEntity(
    @PrimaryKey val userId: String,
    val displayName: String,
    val email: String = "",
    val gradeLevel: String = "High School",
    val schoolName: String = "",
    val academicYear: String = "2026-2027",
    val preferredStartHour: Int = 16,
    val preferredEndHour: Int = 21,
    val dailyGoalMinutes: Int = 90,
    val themeMode: String = "SYSTEM",
    val accentPalette: String = "ROYAL_BLUE",
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val language: String = "English",
    val privacyPolicyAccepted: Boolean = true,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val totalFocusMinutes: Int = 0,
    val lastStudyDayKey: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "subjects",
    indices = [Index("userId")]
)
data class SubjectEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val code: String = "",
    val teacherName: String = "",
    val colorHex: Long = 0xFF2563EBL,
    val targetAttendancePercent: Int = 75,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "homework",
    indices = [Index("userId"), Index("subjectId"), Index("dueAtMillis")]
)
data class HomeworkEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val subjectId: String,
    val subjectName: String = "",
    val title: String,
    val description: String = "",
    val dueAtMillis: Long,
    val priority: String = "MEDIUM", // LOW, MEDIUM, HIGH
    val completed: Boolean = false,
    val completedAtMillis: Long? = null,
    val attachmentUri: String? = null,
    val attachmentLabel: String? = null,
    val reminderEnabled: Boolean = true,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "reminders",
    indices = [Index("userId"), Index("triggerAtMillis"), Index("status")]
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val notes: String = "",
    val category: String = "CUSTOM", // CUSTOM, HOMEWORK, ASSIGNMENT, EXAM, STUDY_SESSION, REVISION, CLASS
    val priority: String = "MEDIUM", // LOW, MEDIUM, HIGH
    val triggerAtMillis: Long,
    val advanceMinutes: Int = 0,
    val repeatRule: String = "NONE", // NONE, DAILY, WEEKLY, CUSTOM_DAYS
    val customRepeatDays: String = "", // comma-separated DayOfWeek ints 1..7
    val status: String = "UPCOMING", // UPCOMING, COMPLETED, MISSED, SNOOZED
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val linkedEntityId: String? = null,
    val lastTriggeredMillis: Long = 0L,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "timetable_sessions",
    indices = [Index("userId"), Index("dayOfWeek")]
)
data class TimetableSessionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val subjectId: String = "",
    val subjectName: String = "",
    val title: String,
    val dayOfWeek: Int, // 1 = Monday .. 7 = Sunday
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val sessionKind: String = "STUDY", // CLASS, STUDY, BREAK
    val recurring: Boolean = true,
    val notifyBeforeMinutes: Int = 10,
    val location: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "exams",
    indices = [Index("userId"), Index("examAtMillis")]
)
data class ExamEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val subjectId: String,
    val subjectName: String = "",
    val title: String,
    val examAtMillis: Long,
    val importantTopics: String = "",
    val preparationStatus: String = "IN_PROGRESS", // NOT_STARTED, IN_PROGRESS, READY, COMPLETED
    val mockTest: Boolean = false,
    val obtainedScore: Int? = null,
    val maxScore: Int? = null,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "chapters",
    indices = [Index("userId"), Index("subjectId")]
)
data class ChapterEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val subjectId: String,
    val examId: String? = null,
    val title: String,
    val orderIndex: Int = 0,
    val completed: Boolean = false,
    val difficult: Boolean = false,
    val revisionCount: Int = 0,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "revisions",
    indices = [Index("userId"), Index("scheduledAtMillis")]
)
data class RevisionScheduleEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val subjectId: String,
    val subjectName: String = "",
    val chapterId: String? = null,
    val chapterTitle: String,
    val scheduledAtMillis: Long,
    val intervalDays: Int = 3,
    val completed: Boolean = false,
    val difficult: Boolean = false,
    val completedAtMillis: Long? = null,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "notes",
    indices = [Index("userId"), Index("subjectId")]
)
data class StudyNoteEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val subjectId: String,
    val subjectName: String = "",
    val chapterName: String = "",
    val title: String,
    val content: String,
    val important: Boolean = false,
    val bookmarked: Boolean = false,
    val attachmentUri: String? = null,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "study_sessions",
    indices = [Index("userId"), Index("startedAtMillis")]
)
data class StudySessionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val subjectId: String = "",
    val subjectName: String = "",
    val mode: String = "POMODORO", // POMODORO, CUSTOM, SHORT_BREAK, LONG_BREAK
    val durationMinutes: Int,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val completed: Boolean = true,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "goals",
    indices = [Index("userId")]
)
data class GoalEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val subjectId: String? = null,
    val period: String = "DAILY", // DAILY, WEEKLY, CUSTOM
    val targetValue: Int,
    val currentValue: Int = 0,
    val unit: String = "mins",
    val completed: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "attendance",
    indices = [Index("userId"), Index("subjectId"), Index("dateMillis")]
)
data class AttendanceRecordEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val subjectId: String,
    val subjectName: String = "",
    val dateMillis: Long,
    val present: Boolean,
    val notes: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "flashcards",
    indices = [Index("userId"), Index("subjectId")]
)
data class FlashcardEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val subjectId: String,
    val subjectName: String = "",
    val chapterName: String = "",
    val question: String,
    val answer: String,
    val difficult: Boolean = false,
    val mastered: Boolean = false,
    val reviewCount: Int = 0,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "quiz_results",
    indices = [Index("userId"), Index("takenAtMillis")]
)
data class QuizResultEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val subjectId: String? = null,
    val subjectName: String = "",
    val title: String,
    val correctCount: Int,
    val totalCount: Int,
    val takenAtMillis: Long,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "achievements",
    indices = [Index("userId")]
)
data class AchievementEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val code: String,
    val title: String,
    val description: String = "",
    val unlockedAtMillis: Long,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "notifications",
    indices = [Index("userId"), Index("timestampMillis")]
)
data class AppNotificationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val message: String,
    val category: String = "SYSTEM",
    val read: Boolean = false,
    val timestampMillis: Long,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "calendar_events",
    indices = [Index("userId"), Index("eventAtMillis")]
)
data class CalendarEventEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val description: String = "",
    val eventAtMillis: Long,
    val reminderEnabled: Boolean = true,
    val updatedAtMillis: Long = System.currentTimeMillis()
)
