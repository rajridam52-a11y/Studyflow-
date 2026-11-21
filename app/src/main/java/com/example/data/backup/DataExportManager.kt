package com.example.data.backup

import android.content.Context
import android.net.Uri
import com.example.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class StudyFlowBackupPayload(
    val version: Int = 1,
    val exportedAtMillis: Long = System.currentTimeMillis(),
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
    val calendarEvents: List<CalendarEventEntity> = emptyList()
)

class DataExportManager(
    private val context: Context,
    private val db: StudyFlowDatabase
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportJsonToUri(userId: String, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val payload = StudyFlowBackupPayload(
                profile = db.userProfileDao().getProfileOnce(userId),
                subjects = db.subjectDao().getAllOnce(userId),
                homework = db.homeworkDao().getAllOnce(userId),
                reminders = db.reminderDao().getAllOnce(userId),
                timetable = db.timetableDao().getAllOnce(userId),
                exams = db.examDao().getAllOnce(userId),
                chapters = db.chapterDao().getAllOnce(userId),
                revisions = db.revisionDao().getAllOnce(userId),
                notes = db.studyNoteDao().getAllOnce(userId),
                studySessions = db.studySessionDao().getAllOnce(userId),
                goals = db.goalDao().getAllOnce(userId),
                attendance = db.attendanceDao().getAllOnce(userId),
                flashcards = db.flashcardDao().getAllOnce(userId),
                quizResults = db.quizResultDao().getAllOnce(userId),
                achievements = db.achievementDao().getAllOnce(userId),
                calendarEvents = db.calendarEventDao().getAllOnce(userId)
            )
            val serialized = json.encodeToString(payload)
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(serialized.toByteArray(Charsets.UTF_8))
            } ?: return@withContext Result.failure(IllegalStateException("Could not open export destination."))
            Result.success("Exported ${payload.homework.size} homework items, ${payload.notes.size} notes, and ${payload.flashcards.size} flashcards.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importJsonFromUri(userId: String, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            } ?: return@withContext Result.failure(IllegalStateException("Could not read selected backup file."))

            val payload = json.decodeFromString<StudyFlowBackupPayload>(raw)
            payload.profile?.let { db.userProfileDao().upsert(it.copy(userId = userId)) }
            if (payload.subjects.isNotEmpty()) db.subjectDao().upsertAll(payload.subjects.map { it.copy(userId = userId) })
            if (payload.homework.isNotEmpty()) db.homeworkDao().upsertAll(payload.homework.map { it.copy(userId = userId) })
            if (payload.reminders.isNotEmpty()) db.reminderDao().upsertAll(payload.reminders.map { it.copy(userId = userId) })
            if (payload.timetable.isNotEmpty()) db.timetableDao().upsertAll(payload.timetable.map { it.copy(userId = userId) })
            if (payload.exams.isNotEmpty()) db.examDao().upsertAll(payload.exams.map { it.copy(userId = userId) })
            if (payload.chapters.isNotEmpty()) db.chapterDao().upsertAll(payload.chapters.map { it.copy(userId = userId) })
            if (payload.revisions.isNotEmpty()) db.revisionDao().upsertAll(payload.revisions.map { it.copy(userId = userId) })
            if (payload.notes.isNotEmpty()) db.studyNoteDao().upsertAll(payload.notes.map { it.copy(userId = userId) })
            if (payload.studySessions.isNotEmpty()) db.studySessionDao().upsertAll(payload.studySessions.map { it.copy(userId = userId) })
            if (payload.goals.isNotEmpty()) db.goalDao().upsertAll(payload.goals.map { it.copy(userId = userId) })
            if (payload.attendance.isNotEmpty()) db.attendanceDao().upsertAll(payload.attendance.map { it.copy(userId = userId) })
            if (payload.flashcards.isNotEmpty()) db.flashcardDao().upsertAll(payload.flashcards.map { it.copy(userId = userId) })
            if (payload.quizResults.isNotEmpty()) db.quizResultDao().upsertAll(payload.quizResults.map { it.copy(userId = userId) })
            if (payload.achievements.isNotEmpty()) db.achievementDao().upsertAll(payload.achievements.map { it.copy(userId = userId) })
            if (payload.calendarEvents.isNotEmpty()) db.calendarEventDao().upsertAll(payload.calendarEvents.map { it.copy(userId = userId) })

            Result.success("Restored ${payload.subjects.size} subjects, ${payload.homework.size} homework items, and ${payload.notes.size} notes.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportAcademicReportCsvToUri(userId: String, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val profile = db.userProfileDao().getProfileOnce(userId)
            val subjects = db.subjectDao().getAllOnce(userId)
            val homework = db.homeworkDao().getAllOnce(userId)
            val sessions = db.studySessionDao().getAllOnce(userId)
            val attendance = db.attendanceDao().getAllOnce(userId)
            val quizzes = db.quizResultDao().getAllOnce(userId)
            val revisions = db.revisionDao().getAllOnce(userId)

            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            val totalMinutes = sessions.filter { it.completed }.sumOf { it.durationMinutes }
            val completedHw = homework.count { it.completed }
            val completedRev = revisions.count { it.completed }

            val report = buildString {
                appendLine("StudyFlow Academic Progress Report")
                appendLine("Generated,$dateStr")
                appendLine("Student,${profile?.displayName ?: "Student"}")
                appendLine("Grade,${profile?.gradeLevel ?: "-"}")
                appendLine("Total Focus Minutes,$totalMinutes")
                appendLine("Homework Completed,$completedHw / ${homework.size}")
                appendLine("Revisions Completed,$completedRev / ${revisions.size}")
                appendLine()
                appendLine("Subject,Attendance %,Sessions Count,Quizzes Avg %")
                subjects.forEach { subj ->
                    val subjAtt = attendance.filter { it.subjectId == subj.id }
                    val attPct = if (subjAtt.isEmpty()) 100 else (subjAtt.count { it.present } * 100 / subjAtt.size)
                    val subjSess = sessions.count { it.subjectId == subj.id }
                    val subjQuiz = quizzes.filter { it.subjectId == subj.id }
                    val quizAvg = if (subjQuiz.isEmpty()) "-" else {
                        val avg = subjQuiz.map { it.correctCount * 100.0 / it.totalCount.coerceAtLeast(1) }.average()
                        "${avg.toInt()}%"
                    }
                    appendLine("\"${subj.name.replace("\"", "\"\"")}\",$attPct%,$subjSess,$quizAvg")
                }
            }
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(report.toByteArray(Charsets.UTF_8))
            } ?: return@withContext Result.failure(IllegalStateException("Could not open report file."))
            Result.success("Academic progress report saved.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
