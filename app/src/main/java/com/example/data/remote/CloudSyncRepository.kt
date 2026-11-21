package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.local.*
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class CloudSyncRepository(
    private val db: FirebaseFirestore,
    private val localDb: StudyFlowDatabase,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context, localDb: StudyFlowDatabase) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        localDb = localDb
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before syncing.")

    suspend fun syncAll(userId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch during cloud sync." }
            val userRef = db.collection("users").document(uid)

            // 1. Sync user profile (never overwrite existing createdAt)
            localDb.userProfileDao().getProfileOnce(uid)?.let { profile ->
                val existingSnap = userRef.get().await()
                val basePayload = mutableMapOf<String, Any?>(
                    "userId" to uid,
                    "displayName" to profile.displayName.ifBlank { "Student" }.take(80),
                    "email" to profile.email.take(120),
                    "gradeLevel" to profile.gradeLevel.ifBlank { "High School" }.take(40),
                    "schoolName" to profile.schoolName.take(100),
                    "academicYear" to profile.academicYear.take(30),
                    "preferredStartHour" to profile.preferredStartHour.coerceIn(0, 23),
                    "preferredEndHour" to profile.preferredEndHour.coerceIn(0, 23),
                    "dailyGoalMinutes" to profile.dailyGoalMinutes.coerceIn(15, 720),
                    "themeMode" to profile.themeMode,
                    "accentPalette" to profile.accentPalette,
                    "soundEnabled" to profile.soundEnabled,
                    "vibrationEnabled" to profile.vibrationEnabled,
                    "language" to profile.language.take(20),
                    "privacyPolicyAccepted" to profile.privacyPolicyAccepted,
                    "currentStreak" to profile.currentStreak.coerceAtLeast(0),
                    "longestStreak" to profile.longestStreak.coerceAtLeast(0),
                    "totalFocusMinutes" to profile.totalFocusMinutes.coerceAtLeast(0),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!existingSnap.exists()) {
                    basePayload["createdAt"] = FieldValue.serverTimestamp()
                    @Suppress("UNCHECKED_CAST")
                    userRef.set(basePayload.filterValues { it != null } as Map<String, Any>).await()
                } else {
                    val updateMap = basePayload.toMutableMap().apply { remove("userId") }
                    @Suppress("UNCHECKED_CAST")
                    userRef.update(updateMap.filterValues { it != null } as Map<String, Any>).await()
                }
            }

            // 2. Two-way merge by updatedAtMillis (local data is never deleted on conflict or failure)
            val subjectsCount = syncCollection(
                uid = uid,
                collectionName = "subjects",
                localItems = localDb.subjectDao().getAllOnce(uid),
                idOf = { it.id },
                updatedAtOf = { it.updatedAtMillis },
                toCreateMap = { s ->
                    mapOf(
                        "userId" to uid,
                        "name" to s.name.ifBlank { "Subject" }.take(80),
                        "code" to s.code.take(20),
                        "teacherName" to s.teacherName.take(80),
                        "colorHex" to s.colorHex,
                        "targetAttendancePercent" to s.targetAttendancePercent.coerceIn(0, 100),
                        "updatedAtMillis" to s.updatedAtMillis
                    )
                },
                fromSnapshot = { doc ->
                    SubjectEntity(
                        id = doc.id,
                        userId = uid,
                        name = doc.getString("name") ?: "Subject",
                        code = doc.getString("code").orEmpty(),
                        teacherName = doc.getString("teacherName").orEmpty(),
                        colorHex = doc.getLong("colorHex") ?: 0xFF2563EBL,
                        targetAttendancePercent = (doc.getLong("targetAttendancePercent") ?: 75L).toInt(),
                        updatedAtMillis = doc.getLong("updatedAtMillis") ?: 0L
                    )
                },
                upsertLocal = { localDb.subjectDao().upsertAll(it) }
            )

            val hwCount = syncCollection(
                uid = uid,
                collectionName = "homework",
                localItems = localDb.homeworkDao().getAllOnce(uid),
                idOf = { it.id },
                updatedAtOf = { it.updatedAtMillis },
                toCreateMap = { h ->
                    mapOf(
                        "userId" to uid,
                        "subjectId" to h.subjectId.ifBlank { "general" },
                        "subjectName" to h.subjectName.take(80),
                        "title" to h.title.ifBlank { "Homework" }.take(140),
                        "description" to h.description.take(2000),
                        "dueAtMillis" to h.dueAtMillis.coerceAtLeast(0L),
                        "priority" to h.priority,
                        "completed" to h.completed,
                        "completedAtMillis" to h.completedAtMillis,
                        "attachmentUri" to h.attachmentUri?.take(500),
                        "attachmentLabel" to h.attachmentLabel?.take(120),
                        "reminderEnabled" to h.reminderEnabled,
                        "updatedAtMillis" to h.updatedAtMillis
                    )
                },
                fromSnapshot = { doc ->
                    HomeworkEntity(
                        id = doc.id,
                        userId = uid,
                        subjectId = doc.getString("subjectId") ?: "general",
                        subjectName = doc.getString("subjectName").orEmpty(),
                        title = doc.getString("title") ?: "Homework",
                        description = doc.getString("description").orEmpty(),
                        dueAtMillis = doc.getLong("dueAtMillis") ?: System.currentTimeMillis(),
                        priority = doc.getString("priority") ?: "MEDIUM",
                        completed = doc.getBoolean("completed") ?: false,
                        completedAtMillis = doc.getLong("completedAtMillis"),
                        attachmentUri = doc.getString("attachmentUri"),
                        attachmentLabel = doc.getString("attachmentLabel"),
                        reminderEnabled = doc.getBoolean("reminderEnabled") ?: true,
                        updatedAtMillis = doc.getLong("updatedAtMillis") ?: 0L
                    )
                },
                upsertLocal = { localDb.homeworkDao().upsertAll(it) }
            )

            val remCount = syncCollection(
                uid = uid,
                collectionName = "reminders",
                localItems = localDb.reminderDao().getAllOnce(uid),
                idOf = { it.id },
                updatedAtOf = { it.updatedAtMillis },
                toCreateMap = { r ->
                    mapOf(
                        "userId" to uid,
                        "title" to r.title.ifBlank { "Reminder" }.take(140),
                        "notes" to r.notes.take(1000),
                        "category" to r.category,
                        "priority" to r.priority,
                        "triggerAtMillis" to r.triggerAtMillis.coerceAtLeast(0L),
                        "advanceMinutes" to r.advanceMinutes.coerceIn(0, 1440),
                        "repeatRule" to r.repeatRule,
                        "customRepeatDays" to r.customRepeatDays.take(40),
                        "status" to r.status,
                        "soundEnabled" to r.soundEnabled,
                        "vibrationEnabled" to r.vibrationEnabled,
                        "linkedEntityId" to r.linkedEntityId?.take(128),
                        "updatedAtMillis" to r.updatedAtMillis
                    )
                },
                fromSnapshot = { doc ->
                    ReminderEntity(
                        id = doc.id,
                        userId = uid,
                        title = doc.getString("title") ?: "Reminder",
                        notes = doc.getString("notes").orEmpty(),
                        category = doc.getString("category") ?: "CUSTOM",
                        priority = doc.getString("priority") ?: "MEDIUM",
                        triggerAtMillis = doc.getLong("triggerAtMillis") ?: System.currentTimeMillis(),
                        advanceMinutes = (doc.getLong("advanceMinutes") ?: 0L).toInt(),
                        repeatRule = doc.getString("repeatRule") ?: "NONE",
                        customRepeatDays = doc.getString("customRepeatDays").orEmpty(),
                        status = doc.getString("status") ?: "UPCOMING",
                        soundEnabled = doc.getBoolean("soundEnabled") ?: true,
                        vibrationEnabled = doc.getBoolean("vibrationEnabled") ?: true,
                        linkedEntityId = doc.getString("linkedEntityId"),
                        updatedAtMillis = doc.getLong("updatedAtMillis") ?: 0L
                    )
                },
                upsertLocal = { localDb.reminderDao().upsertAll(it) }
            )

            val notesCount = syncCollection(
                uid = uid,
                collectionName = "notes",
                localItems = localDb.studyNoteDao().getAllOnce(uid),
                idOf = { it.id },
                updatedAtOf = { it.updatedAtMillis },
                toCreateMap = { n ->
                    mapOf(
                        "userId" to uid,
                        "subjectId" to n.subjectId.ifBlank { "general" },
                        "subjectName" to n.subjectName.take(80),
                        "chapterName" to n.chapterName.take(100),
                        "title" to n.title.ifBlank { "Untitled Note" }.take(140),
                        "content" to n.content.take(20000),
                        "important" to n.important,
                        "bookmarked" to n.bookmarked,
                        "attachmentUri" to n.attachmentUri?.take(500),
                        "updatedAtMillis" to n.updatedAtMillis
                    )
                },
                fromSnapshot = { doc ->
                    StudyNoteEntity(
                        id = doc.id,
                        userId = uid,
                        subjectId = doc.getString("subjectId") ?: "general",
                        subjectName = doc.getString("subjectName").orEmpty(),
                        chapterName = doc.getString("chapterName").orEmpty(),
                        title = doc.getString("title") ?: "Note",
                        content = doc.getString("content").orEmpty(),
                        important = doc.getBoolean("important") ?: false,
                        bookmarked = doc.getBoolean("bookmarked") ?: false,
                        attachmentUri = doc.getString("attachmentUri"),
                        updatedAtMillis = doc.getLong("updatedAtMillis") ?: 0L
                    )
                },
                upsertLocal = { localDb.studyNoteDao().upsertAll(it) }
            )

            val cardsCount = syncCollection(
                uid = uid,
                collectionName = "flashcards",
                localItems = localDb.flashcardDao().getAllOnce(uid),
                idOf = { it.id },
                updatedAtOf = { it.updatedAtMillis },
                toCreateMap = { f ->
                    mapOf(
                        "userId" to uid,
                        "subjectId" to f.subjectId.ifBlank { "general" },
                        "subjectName" to f.subjectName.take(80),
                        "chapterName" to f.chapterName.take(100),
                        "question" to f.question.ifBlank { "Question" }.take(1000),
                        "answer" to f.answer.ifBlank { "Answer" }.take(2000),
                        "difficult" to f.difficult,
                        "mastered" to f.mastered,
                        "reviewCount" to f.reviewCount.coerceAtLeast(0),
                        "updatedAtMillis" to f.updatedAtMillis
                    )
                },
                fromSnapshot = { doc ->
                    FlashcardEntity(
                        id = doc.id,
                        userId = uid,
                        subjectId = doc.getString("subjectId") ?: "general",
                        subjectName = doc.getString("subjectName").orEmpty(),
                        chapterName = doc.getString("chapterName").orEmpty(),
                        question = doc.getString("question") ?: "",
                        answer = doc.getString("answer") ?: "",
                        difficult = doc.getBoolean("difficult") ?: false,
                        mastered = doc.getBoolean("mastered") ?: false,
                        reviewCount = (doc.getLong("reviewCount") ?: 0L).toInt(),
                        updatedAtMillis = doc.getLong("updatedAtMillis") ?: 0L
                    )
                },
                upsertLocal = { localDb.flashcardDao().upsertAll(it) }
            )

            val examsCount = syncCollection(
                uid = uid,
                collectionName = "exams",
                localItems = localDb.examDao().getAllOnce(uid),
                idOf = { it.id },
                updatedAtOf = { it.updatedAtMillis },
                toCreateMap = { e ->
                    mapOf(
                        "userId" to uid,
                        "subjectId" to e.subjectId.ifBlank { "general" },
                        "subjectName" to e.subjectName.take(80),
                        "title" to e.title.ifBlank { "Exam" }.take(120),
                        "examAtMillis" to e.examAtMillis.coerceAtLeast(0L),
                        "importantTopics" to e.importantTopics.take(2000),
                        "preparationStatus" to e.preparationStatus,
                        "mockTest" to e.mockTest,
                        "obtainedScore" to e.obtainedScore,
                        "maxScore" to e.maxScore,
                        "updatedAtMillis" to e.updatedAtMillis
                    )
                },
                fromSnapshot = { doc ->
                    ExamEntity(
                        id = doc.id,
                        userId = uid,
                        subjectId = doc.getString("subjectId") ?: "general",
                        subjectName = doc.getString("subjectName").orEmpty(),
                        title = doc.getString("title") ?: "Exam",
                        examAtMillis = doc.getLong("examAtMillis") ?: System.currentTimeMillis(),
                        importantTopics = doc.getString("importantTopics").orEmpty(),
                        preparationStatus = doc.getString("preparationStatus") ?: "IN_PROGRESS",
                        mockTest = doc.getBoolean("mockTest") ?: false,
                        obtainedScore = doc.getLong("obtainedScore")?.toInt(),
                        maxScore = doc.getLong("maxScore")?.toInt(),
                        updatedAtMillis = doc.getLong("updatedAtMillis") ?: 0L
                    )
                },
                upsertLocal = { localDb.examDao().upsertAll(it) }
            )

            val timetableCount = syncCollection(
                uid = uid,
                collectionName = "timetable",
                localItems = localDb.timetableDao().getAllOnce(uid),
                idOf = { it.id },
                updatedAtOf = { it.updatedAtMillis },
                toCreateMap = { t ->
                    mapOf(
                        "userId" to uid,
                        "subjectId" to t.subjectId.take(128),
                        "subjectName" to t.subjectName.take(80),
                        "title" to t.title.ifBlank { "Study Session" }.take(100),
                        "dayOfWeek" to t.dayOfWeek.coerceIn(1, 7),
                        "startMinuteOfDay" to t.startMinuteOfDay.coerceIn(0, 1439),
                        "endMinuteOfDay" to t.endMinuteOfDay.coerceIn((t.startMinuteOfDay + 1).coerceAtMost(1440), 1440),
                        "sessionKind" to t.sessionKind,
                        "recurring" to t.recurring,
                        "notifyBeforeMinutes" to t.notifyBeforeMinutes.coerceIn(0, 180),
                        "location" to t.location.take(100),
                        "updatedAtMillis" to t.updatedAtMillis
                    )
                },
                fromSnapshot = { doc ->
                    TimetableSessionEntity(
                        id = doc.id,
                        userId = uid,
                        subjectId = doc.getString("subjectId").orEmpty(),
                        subjectName = doc.getString("subjectName").orEmpty(),
                        title = doc.getString("title") ?: "Session",
                        dayOfWeek = (doc.getLong("dayOfWeek") ?: 1L).toInt().coerceIn(1, 7),
                        startMinuteOfDay = (doc.getLong("startMinuteOfDay") ?: 540L).toInt(),
                        endMinuteOfDay = (doc.getLong("endMinuteOfDay") ?: 600L).toInt(),
                        sessionKind = doc.getString("sessionKind") ?: "STUDY",
                        recurring = doc.getBoolean("recurring") ?: true,
                        notifyBeforeMinutes = (doc.getLong("notifyBeforeMinutes") ?: 10L).toInt(),
                        location = doc.getString("location").orEmpty(),
                        updatedAtMillis = doc.getLong("updatedAtMillis") ?: 0L
                    )
                },
                upsertLocal = { localDb.timetableDao().upsertAll(it) }
            )

            val chaptersCount = syncCollection(
                uid = uid,
                collectionName = "chapters",
                localItems = localDb.chapterDao().getAllOnce(uid),
                idOf = { it.id },
                updatedAtOf = { it.updatedAtMillis },
                toCreateMap = { c ->
                    mapOf(
                        "userId" to uid,
                        "subjectId" to c.subjectId.ifBlank { "general" },
                        "examId" to c.examId?.take(128),
                        "title" to c.title.ifBlank { "Chapter" }.take(120),
                        "orderIndex" to c.orderIndex.coerceAtLeast(0),
                        "completed" to c.completed,
                        "difficult" to c.difficult,
                        "revisionCount" to c.revisionCount.coerceAtLeast(0),
                        "updatedAtMillis" to c.updatedAtMillis
                    )
                },
                fromSnapshot = { doc ->
                    ChapterEntity(
                        id = doc.id,
                        userId = uid,
                        subjectId = doc.getString("subjectId") ?: "general",
                        examId = doc.getString("examId"),
                        title = doc.getString("title") ?: "Chapter",
                        orderIndex = (doc.getLong("orderIndex") ?: 0L).toInt(),
                        completed = doc.getBoolean("completed") ?: false,
                        difficult = doc.getBoolean("difficult") ?: false,
                        revisionCount = (doc.getLong("revisionCount") ?: 0L).toInt(),
                        updatedAtMillis = doc.getLong("updatedAtMillis") ?: 0L
                    )
                },
                upsertLocal = { localDb.chapterDao().upsertAll(it) }
            )

            val revisionsCount = syncCollection(
                uid = uid,
                collectionName = "revisions",
                localItems = localDb.revisionDao().getAllOnce(uid),
                idOf = { it.id },
                updatedAtOf = { it.updatedAtMillis },
                toCreateMap = { r ->
                    mapOf(
                        "userId" to uid,
                        "subjectId" to r.subjectId.ifBlank { "general" },
                        "subjectName" to r.subjectName.take(80),
                        "chapterId" to r.chapterId?.take(128),
                        "chapterTitle" to r.chapterTitle.ifBlank { "Topic" }.take(120),
                        "scheduledAtMillis" to r.scheduledAtMillis.coerceAtLeast(0L),
                        "intervalDays" to r.intervalDays.coerceIn(1, 365),
                        "completed" to r.completed,
                        "difficult" to r.difficult,
                        "completedAtMillis" to r.completedAtMillis,
                        "updatedAtMillis" to r.updatedAtMillis
                    )
                },
                fromSnapshot = { doc ->
                    RevisionScheduleEntity(
                        id = doc.id,
                        userId = uid,
                        subjectId = doc.getString("subjectId") ?: "general",
                        subjectName = doc.getString("subjectName").orEmpty(),
                        chapterId = doc.getString("chapterId"),
                        chapterTitle = doc.getString("chapterTitle") ?: "Topic",
                        scheduledAtMillis = doc.getLong("scheduledAtMillis") ?: System.currentTimeMillis(),
                        intervalDays = (doc.getLong("intervalDays") ?: 3L).toInt().coerceIn(1, 365),
                        completed = doc.getBoolean("completed") ?: false,
                        difficult = doc.getBoolean("difficult") ?: false,
                        completedAtMillis = doc.getLong("completedAtMillis"),
                        updatedAtMillis = doc.getLong("updatedAtMillis") ?: 0L
                    )
                },
                upsertLocal = { localDb.revisionDao().upsertAll(it) }
            )

            val sessionsCount = syncCollection(
                uid = uid,
                collectionName = "study_sessions",
                localItems = localDb.studySessionDao().getAllOnce(uid),
                idOf = { it.id },
                updatedAtOf = { it.updatedAtMillis },
                toCreateMap = { s ->
                    mapOf(
                        "userId" to uid,
                        "subjectId" to s.subjectId.take(128),
                        "subjectName" to s.subjectName.take(80),
                        "mode" to s.mode,
                        "durationMinutes" to s.durationMinutes.coerceIn(1, 600),
                        "startedAtMillis" to s.startedAtMillis.coerceAtLeast(0L),
                        "endedAtMillis" to s.endedAtMillis.coerceAtLeast(s.startedAtMillis),
                        "completed" to s.completed,
                        "updatedAtMillis" to s.updatedAtMillis
                    )
                },
                fromSnapshot = { doc ->
                    StudySessionEntity(
                        id = doc.id,
                        userId = uid,
                        subjectId = doc.getString("subjectId").orEmpty(),
                        subjectName = doc.getString("subjectName").orEmpty(),
                        mode = doc.getString("mode") ?: "POMODORO",
                        durationMinutes = (doc.getLong("durationMinutes") ?: 25L).toInt(),
                        startedAtMillis = doc.getLong("startedAtMillis") ?: 0L,
                        endedAtMillis = doc.getLong("endedAtMillis") ?: 0L,
                        completed = doc.getBoolean("completed") ?: true,
                        updatedAtMillis = doc.getLong("updatedAtMillis") ?: 0L
                    )
                },
                upsertLocal = { localDb.studySessionDao().upsertAll(it) }
            )

            val goalsCount = syncCollection(
                uid = uid,
                collectionName = "goals",
                localItems = localDb.goalDao().getAllOnce(uid),
                idOf = { it.id },
                updatedAtOf = { it.updatedAtMillis },
                toCreateMap = { g ->
                    mapOf(
                        "userId" to uid,
                        "title" to g.title.ifBlank { "Goal" }.take(120),
                        "subjectId" to g.subjectId?.take(128),
                        "period" to g.period,
                        "targetValue" to g.targetValue.coerceIn(1, 100000),
                        "currentValue" to g.currentValue.coerceIn(0, 100000),
                        "unit" to g.unit.take(24),
                        "completed" to g.completed,
                        "updatedAtMillis" to g.updatedAtMillis
                    )
                },
                fromSnapshot = { doc ->
                    GoalEntity(
                        id = doc.id,
                        userId = uid,
                        title = doc.getString("title") ?: "Goal",
                        subjectId = doc.getString("subjectId"),
                        period = doc.getString("period") ?: "DAILY",
                        targetValue = (doc.getLong("targetValue") ?: 60L).toInt(),
                        currentValue = (doc.getLong("currentValue") ?: 0L).toInt(),
                        unit = doc.getString("unit") ?: "mins",
                        completed = doc.getBoolean("completed") ?: false,
                        updatedAtMillis = doc.getLong("updatedAtMillis") ?: 0L
                    )
                },
                upsertLocal = { localDb.goalDao().upsertAll(it) }
            )

            val attendanceCount = syncCollection(
                uid = uid,
                collectionName = "attendance",
                localItems = localDb.attendanceDao().getAllOnce(uid),
                idOf = { it.id },
                updatedAtOf = { it.updatedAtMillis },
                toCreateMap = { a ->
                    mapOf(
                        "userId" to uid,
                        "subjectId" to a.subjectId.ifBlank { "general" },
                        "subjectName" to a.subjectName.take(80),
                        "dateMillis" to a.dateMillis.coerceAtLeast(0L),
                        "present" to a.present,
                        "notes" to a.notes.take(300),
                        "updatedAtMillis" to a.updatedAtMillis
                    )
                },
                fromSnapshot = { doc ->
                    AttendanceRecordEntity(
                        id = doc.id,
                        userId = uid,
                        subjectId = doc.getString("subjectId") ?: "general",
                        subjectName = doc.getString("subjectName").orEmpty(),
                        dateMillis = doc.getLong("dateMillis") ?: System.currentTimeMillis(),
                        present = doc.getBoolean("present") ?: true,
                        notes = doc.getString("notes").orEmpty(),
                        updatedAtMillis = doc.getLong("updatedAtMillis") ?: 0L
                    )
                },
                upsertLocal = { localDb.attendanceDao().upsertAll(it) }
            )

            val quizzesCount = syncCollection(
                uid = uid,
                collectionName = "quiz_results",
                localItems = localDb.quizResultDao().getAllOnce(uid),
                idOf = { it.id },
                updatedAtOf = { it.updatedAtMillis },
                toCreateMap = { q ->
                    val total = q.totalCount.coerceAtLeast(1)
                    mapOf(
                        "userId" to uid,
                        "subjectId" to q.subjectId?.take(128),
                        "subjectName" to q.subjectName.take(80),
                        "title" to q.title.ifBlank { "Practice Quiz" }.take(120),
                        "correctCount" to q.correctCount.coerceIn(0, total),
                        "totalCount" to total,
                        "takenAtMillis" to q.takenAtMillis.coerceAtLeast(0L),
                        "updatedAtMillis" to q.updatedAtMillis
                    )
                },
                fromSnapshot = { doc ->
                    QuizResultEntity(
                        id = doc.id,
                        userId = uid,
                        subjectId = doc.getString("subjectId"),
                        subjectName = doc.getString("subjectName").orEmpty(),
                        title = doc.getString("title") ?: "Quiz",
                        correctCount = (doc.getLong("correctCount") ?: 0L).toInt(),
                        totalCount = (doc.getLong("totalCount") ?: 1L).toInt().coerceAtLeast(1),
                        takenAtMillis = doc.getLong("takenAtMillis") ?: System.currentTimeMillis(),
                        updatedAtMillis = doc.getLong("updatedAtMillis") ?: 0L
                    )
                },
                upsertLocal = { localDb.quizResultDao().upsertAll(it) }
            )

            val achievementsCount = syncCollection(
                uid = uid,
                collectionName = "achievements",
                localItems = localDb.achievementDao().getAllOnce(uid),
                idOf = { it.id },
                updatedAtOf = { it.updatedAtMillis },
                toCreateMap = { a ->
                    mapOf(
                        "userId" to uid,
                        "code" to a.code.ifBlank { "ACHIEVEMENT" }.take(60),
                        "title" to a.title.ifBlank { "Achievement" }.take(80),
                        "description" to a.description.take(240),
                        "unlockedAtMillis" to a.unlockedAtMillis.coerceAtLeast(0L),
                        "updatedAtMillis" to a.updatedAtMillis
                    )
                },
                toUpdateMap = { a ->
                    mapOf(
                        "title" to a.title.ifBlank { "Achievement" }.take(80),
                        "description" to a.description.take(240),
                        "unlockedAtMillis" to a.unlockedAtMillis.coerceAtLeast(0L),
                        "updatedAtMillis" to a.updatedAtMillis
                    )
                },
                fromSnapshot = { doc ->
                    AchievementEntity(
                        id = doc.id,
                        userId = uid,
                        code = doc.getString("code") ?: "ACHIEVEMENT",
                        title = doc.getString("title") ?: "Achievement",
                        description = doc.getString("description").orEmpty(),
                        unlockedAtMillis = doc.getLong("unlockedAtMillis") ?: 0L,
                        updatedAtMillis = doc.getLong("updatedAtMillis") ?: 0L
                    )
                },
                upsertLocal = { localDb.achievementDao().upsertAll(it) }
            )

            val notificationsCount = syncCollection(
                uid = uid,
                collectionName = "notifications",
                localItems = localDb.appNotificationDao().getAllOnce(uid),
                idOf = { it.id },
                updatedAtOf = { it.updatedAtMillis },
                toCreateMap = { n ->
                    mapOf(
                        "userId" to uid,
                        "title" to n.title.ifBlank { "Notification" }.take(120),
                        "message" to n.message.ifBlank { "Update" }.take(500),
                        "category" to n.category.ifBlank { "SYSTEM" }.take(40),
                        "read" to n.read,
                        "timestampMillis" to n.timestampMillis.coerceAtLeast(0L),
                        "updatedAtMillis" to n.updatedAtMillis
                    )
                },
                toUpdateMap = { n ->
                    mapOf(
                        "read" to n.read,
                        "updatedAtMillis" to n.updatedAtMillis
                    )
                },
                fromSnapshot = { doc ->
                    AppNotificationEntity(
                        id = doc.id,
                        userId = uid,
                        title = doc.getString("title") ?: "Notification",
                        message = doc.getString("message").orEmpty(),
                        category = doc.getString("category") ?: "SYSTEM",
                        read = doc.getBoolean("read") ?: false,
                        timestampMillis = doc.getLong("timestampMillis") ?: 0L,
                        updatedAtMillis = doc.getLong("updatedAtMillis") ?: 0L
                    )
                },
                upsertLocal = { localDb.appNotificationDao().upsertAll(it) }
            )

            val totalSynced = subjectsCount + hwCount + remCount + notesCount + cardsCount + examsCount +
                timetableCount + chaptersCount + revisionsCount + sessionsCount + goalsCount +
                attendanceCount + quizzesCount + achievementsCount + notificationsCount
            Result.success("Synced $totalSynced records with Cloud Firestore")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$userId")
            Result.failure(e)
        }
    }

    private suspend fun <T> syncCollection(
        uid: String,
        collectionName: String,
        localItems: List<T>,
        idOf: (T) -> String,
        updatedAtOf: (T) -> Long,
        toCreateMap: (T) -> Map<String, Any?>,
        toUpdateMap: ((T) -> Map<String, Any?>)? = null,
        fromSnapshot: (DocumentSnapshot) -> T,
        upsertLocal: suspend (List<T>) -> Unit
    ): Int {
        val colRef = db.collection("users").document(uid).collection(collectionName)
        val remoteSnapshots = try {
            colRef.get().await().documents
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colRef.path)
            throw e
        }

        val remoteMap = remoteSnapshots.associateBy { it.id }
        val localMap = localItems.associateBy { idOf(it) }

        val toPull = mutableListOf<T>()
        for (doc in remoteSnapshots) {
            val remoteUpdated = doc.getLong("updatedAtMillis") ?: 0L
            val localItem = localMap[doc.id]
            if (localItem == null || remoteUpdated > updatedAtOf(localItem)) {
                toPull.add(fromSnapshot(doc))
            }
        }
        if (toPull.isNotEmpty()) {
            upsertLocal(toPull)
        }

        var pushed = 0
        for (item in localItems) {
            val docId = idOf(item)
            if (!docId.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) continue
            val remoteDoc = remoteMap[docId]
            val remoteUpdated = remoteDoc?.getLong("updatedAtMillis") ?: -1L
            if (remoteDoc == null || updatedAtOf(item) > remoteUpdated) {
                val docRef = colRef.document(docId)
                if (remoteDoc == null) {
                    val rawMap = toCreateMap(item).filterValues { it != null }.toMutableMap()
                    rawMap["createdAt"] = FieldValue.serverTimestamp()
                    rawMap["updatedAt"] = FieldValue.serverTimestamp()
                    @Suppress("UNCHECKED_CAST")
                    docRef.set(rawMap as Map<String, Any>).await()
                } else {
                    val rawMap = (toUpdateMap?.invoke(item) ?: toCreateMap(item))
                        .filterValues { it != null }
                        .toMutableMap()
                    rawMap.remove("userId")
                    rawMap["updatedAt"] = FieldValue.serverTimestamp()
                    @Suppress("UNCHECKED_CAST")
                    docRef.update(rawMap as Map<String, Any>).await()
                }
                pushed++
            }
        }
        return pushed + toPull.size
    }

    suspend fun deleteAllUserData(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val uid = requireUserId()
            check(uid == userId) { "Cannot delete another user's data." }
            val userRef = db.collection("users").document(uid)
            val subcollections = listOf(
                "subjects", "homework", "reminders", "timetable", "exams",
                "chapters", "revisions", "notes", "study_sessions", "goals",
                "attendance", "flashcards", "quiz_results", "achievements", "notifications"
            )
            for (sub in subcollections) {
                val docs = userRef.collection(sub).get().await().documents
                for (doc in docs) {
                    doc.reference.delete().await()
                }
            }
            userRef.delete().await()

            // Also clear local Room records for this user
            localDb.userProfileDao().deleteForUser(uid)
            localDb.subjectDao().deleteForUser(uid)
            localDb.homeworkDao().deleteForUser(uid)
            localDb.reminderDao().deleteForUser(uid)
            localDb.timetableDao().deleteForUser(uid)
            localDb.examDao().deleteForUser(uid)
            localDb.chapterDao().deleteForUser(uid)
            localDb.revisionDao().deleteForUser(uid)
            localDb.studyNoteDao().deleteForUser(uid)
            localDb.studySessionDao().deleteForUser(uid)
            localDb.goalDao().deleteForUser(uid)
            localDb.attendanceDao().deleteForUser(uid)
            localDb.flashcardDao().deleteForUser(uid)
            localDb.quizResultDao().deleteForUser(uid)
            localDb.achievementDao().deleteForUser(uid)
            localDb.appNotificationDao().deleteForUser(uid)
            localDb.calendarEventDao().deleteForUser(uid)

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$userId")
            Result.failure(e)
        }
    }
}
