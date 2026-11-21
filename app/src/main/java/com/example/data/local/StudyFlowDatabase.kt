package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserProfileEntity::class,
        SubjectEntity::class,
        HomeworkEntity::class,
        ReminderEntity::class,
        TimetableSessionEntity::class,
        ExamEntity::class,
        ChapterEntity::class,
        RevisionScheduleEntity::class,
        StudyNoteEntity::class,
        StudySessionEntity::class,
        GoalEntity::class,
        AttendanceRecordEntity::class,
        FlashcardEntity::class,
        QuizResultEntity::class,
        AchievementEntity::class,
        AppNotificationEntity::class,
        CalendarEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class StudyFlowDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun subjectDao(): SubjectDao
    abstract fun homeworkDao(): HomeworkDao
    abstract fun reminderDao(): ReminderDao
    abstract fun timetableDao(): TimetableDao
    abstract fun examDao(): ExamDao
    abstract fun chapterDao(): ChapterDao
    abstract fun revisionDao(): RevisionDao
    abstract fun studyNoteDao(): StudyNoteDao
    abstract fun studySessionDao(): StudySessionDao
    abstract fun goalDao(): GoalDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun quizResultDao(): QuizResultDao
    abstract fun achievementDao(): AchievementDao
    abstract fun appNotificationDao(): AppNotificationDao
    abstract fun calendarEventDao(): CalendarEventDao

    companion object {
        @Volatile
        private var instance: StudyFlowDatabase? = null

        fun getInstance(context: Context): StudyFlowDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    StudyFlowDatabase::class.java,
                    "studyflow_database.db"
                ).build().also { instance = it }
            }
        }
    }
}
