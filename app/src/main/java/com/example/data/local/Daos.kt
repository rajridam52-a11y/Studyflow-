package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    fun observeProfile(userId: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    suspend fun getProfileOnce(userId: String): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity)

    @Query("DELETE FROM user_profiles WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)
}

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects WHERE userId = :userId ORDER BY name ASC")
    fun observeAll(userId: String): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects WHERE userId = :userId")
    suspend fun getAllOnce(userId: String): List<SubjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SubjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SubjectEntity>)

    @Query("DELETE FROM subjects WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM subjects WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)
}

@Dao
interface HomeworkDao {
    @Query("SELECT * FROM homework WHERE userId = :userId ORDER BY completed ASC, dueAtMillis ASC")
    fun observeAll(userId: String): Flow<List<HomeworkEntity>>

    @Query("SELECT * FROM homework WHERE userId = :userId")
    suspend fun getAllOnce(userId: String): List<HomeworkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: HomeworkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<HomeworkEntity>)

    @Query("DELETE FROM homework WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM homework WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE userId = :userId ORDER BY triggerAtMillis ASC")
    fun observeAll(userId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE userId = :userId")
    suspend fun getAllOnce(userId: String): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE status IN ('UPCOMING', 'SNOOZED')")
    suspend fun getAllActiveGlobalOnce(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getByIdOnce(id: String): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ReminderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ReminderEntity>)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM reminders WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)
}

@Dao
interface TimetableDao {
    @Query("SELECT * FROM timetable_sessions WHERE userId = :userId ORDER BY dayOfWeek ASC, startMinuteOfDay ASC")
    fun observeAll(userId: String): Flow<List<TimetableSessionEntity>>

    @Query("SELECT * FROM timetable_sessions WHERE userId = :userId")
    suspend fun getAllOnce(userId: String): List<TimetableSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TimetableSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TimetableSessionEntity>)

    @Query("DELETE FROM timetable_sessions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM timetable_sessions WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)
}

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams WHERE userId = :userId ORDER BY examAtMillis ASC")
    fun observeAll(userId: String): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exams WHERE userId = :userId")
    suspend fun getAllOnce(userId: String): List<ExamEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ExamEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ExamEntity>)

    @Query("DELETE FROM exams WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM exams WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE userId = :userId ORDER BY orderIndex ASC, title ASC")
    fun observeAll(userId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE userId = :userId")
    suspend fun getAllOnce(userId: String): List<ChapterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ChapterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ChapterEntity>)

    @Query("DELETE FROM chapters WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM chapters WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)
}

@Dao
interface RevisionDao {
    @Query("SELECT * FROM revisions WHERE userId = :userId ORDER BY completed ASC, scheduledAtMillis ASC")
    fun observeAll(userId: String): Flow<List<RevisionScheduleEntity>>

    @Query("SELECT * FROM revisions WHERE userId = :userId")
    suspend fun getAllOnce(userId: String): List<RevisionScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RevisionScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RevisionScheduleEntity>)

    @Query("DELETE FROM revisions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM revisions WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)
}

@Dao
interface StudyNoteDao {
    @Query("SELECT * FROM notes WHERE userId = :userId ORDER BY important DESC, updatedAtMillis DESC")
    fun observeAll(userId: String): Flow<List<StudyNoteEntity>>

    @Query("SELECT * FROM notes WHERE userId = :userId")
    suspend fun getAllOnce(userId: String): List<StudyNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: StudyNoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<StudyNoteEntity>)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM notes WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)
}

@Dao
interface StudySessionDao {
    @Query("SELECT * FROM study_sessions WHERE userId = :userId ORDER BY startedAtMillis DESC")
    fun observeAll(userId: String): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM study_sessions WHERE userId = :userId")
    suspend fun getAllOnce(userId: String): List<StudySessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: StudySessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<StudySessionEntity>)

    @Query("DELETE FROM study_sessions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM study_sessions WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE userId = :userId ORDER BY completed ASC, updatedAtMillis DESC")
    fun observeAll(userId: String): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE userId = :userId")
    suspend fun getAllOnce(userId: String): List<GoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: GoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<GoalEntity>)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM goals WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance WHERE userId = :userId ORDER BY dateMillis DESC")
    fun observeAll(userId: String): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance WHERE userId = :userId")
    suspend fun getAllOnce(userId: String): List<AttendanceRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: AttendanceRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<AttendanceRecordEntity>)

    @Query("DELETE FROM attendance WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM attendance WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)
}

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcards WHERE userId = :userId ORDER BY mastered ASC, updatedAtMillis DESC")
    fun observeAll(userId: String): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE userId = :userId")
    suspend fun getAllOnce(userId: String): List<FlashcardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FlashcardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<FlashcardEntity>)

    @Query("DELETE FROM flashcards WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM flashcards WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)
}

@Dao
interface QuizResultDao {
    @Query("SELECT * FROM quiz_results WHERE userId = :userId ORDER BY takenAtMillis DESC")
    fun observeAll(userId: String): Flow<List<QuizResultEntity>>

    @Query("SELECT * FROM quiz_results WHERE userId = :userId")
    suspend fun getAllOnce(userId: String): List<QuizResultEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: QuizResultEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<QuizResultEntity>)

    @Query("DELETE FROM quiz_results WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)
}

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements WHERE userId = :userId ORDER BY unlockedAtMillis DESC")
    fun observeAll(userId: String): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements WHERE userId = :userId")
    suspend fun getAllOnce(userId: String): List<AchievementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: AchievementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<AchievementEntity>)

    @Query("DELETE FROM achievements WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)
}

@Dao
interface AppNotificationDao {
    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY timestampMillis DESC")
    fun observeAll(userId: String): Flow<List<AppNotificationEntity>>

    @Query("SELECT * FROM notifications WHERE userId = :userId")
    suspend fun getAllOnce(userId: String): List<AppNotificationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: AppNotificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<AppNotificationEntity>)

    @Query("UPDATE notifications SET read = 1, updatedAtMillis = :nowMillis WHERE userId = :userId")
    suspend fun markAllRead(userId: String, nowMillis: Long)

    @Query("DELETE FROM notifications WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)
}

@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events WHERE userId = :userId ORDER BY eventAtMillis ASC")
    fun observeAll(userId: String): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE userId = :userId")
    suspend fun getAllOnce(userId: String): List<CalendarEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CalendarEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CalendarEventEntity>)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM calendar_events WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)
}
