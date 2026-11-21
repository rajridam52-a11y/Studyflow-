package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.backup.DataExportManager
import com.example.data.local.*
import com.example.data.remote.CloudSyncRepository
import com.example.reminders.ReminderScheduler
import com.example.reminders.RescheduleSuggestion
import com.example.reminders.SmartRescheduleEngine
import com.example.security.PrivacyLockManager
import com.example.ui.theme.AccentPalette
import com.example.ui.theme.ThemeMode
import com.example.widget.StudyFlowWidgetProvider
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class MainStudyFlowViewModel(
    private val appContext: Context,
    private val db: StudyFlowDatabase,
    private val preferencesManager: PreferencesManager,
    private val cloudSyncRepository: CloudSyncRepository,
    private val exportManager: DataExportManager,
    val currentUserId: String,
    private val initialDisplayName: String,
    private val initialEmail: String
) : ViewModel() {

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    val preferences: StateFlow<AppPreferences> = preferencesManager.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppPreferences())

    private val coreAcademicFlow = combine(
        db.userProfileDao().observeProfile(currentUserId),
        db.subjectDao().observeAll(currentUserId),
        db.homeworkDao().observeAll(currentUserId),
        db.reminderDao().observeAll(currentUserId),
        db.timetableDao().observeAll(currentUserId)
    ) { profile, subjects, homework, reminders, timetable ->
        CoreAcademicSlice(profile, subjects, homework, reminders, timetable)
    }

    private val studyProgressFlow = combine(
        db.examDao().observeAll(currentUserId),
        db.chapterDao().observeAll(currentUserId),
        db.revisionDao().observeAll(currentUserId),
        db.studyNoteDao().observeAll(currentUserId),
        db.studySessionDao().observeAll(currentUserId)
    ) { exams, chapters, revisions, notes, sessions ->
        StudyProgressSlice(exams, chapters, revisions, notes, sessions)
    }

    private val engagementFlow = combine(
        db.goalDao().observeAll(currentUserId),
        db.attendanceDao().observeAll(currentUserId),
        db.flashcardDao().observeAll(currentUserId),
        db.quizResultDao().observeAll(currentUserId),
        db.achievementDao().observeAll(currentUserId)
    ) { goals, attendance, flashcards, quizzes, achievements ->
        EngagementSlice(goals, attendance, flashcards, quizzes, achievements)
    }

    val dashboardState: StateFlow<UiState<StudyFlowDashboardData>> = combine(
        coreAcademicFlow,
        studyProgressFlow,
        engagementFlow,
        db.appNotificationDao().observeAll(currentUserId),
        db.calendarEventDao().observeAll(currentUserId)
    ) { core, progress, engage, notifications, calendarEvents ->
        val analytics = computeAnalytics(
            subjects = core.subjects,
            homework = core.homework,
            revisions = progress.revisions,
            sessions = progress.sessions,
            goals = engage.goals,
            attendance = engage.attendance,
            quizzes = engage.quizzes
        )
        UiState.Success(
            StudyFlowDashboardData(
                profile = core.profile,
                subjects = core.subjects,
                homework = core.homework,
                reminders = core.reminders,
                timetable = core.timetable,
                exams = progress.exams,
                chapters = progress.chapters,
                revisions = progress.revisions,
                notes = progress.notes,
                studySessions = progress.sessions,
                goals = engage.goals,
                attendance = engage.attendance,
                flashcards = engage.flashcards,
                quizResults = engage.quizzes,
                achievements = engage.achievements,
                notifications = notifications,
                calendarEvents = calendarEvents,
                analytics = analytics
            )
        ) as UiState<StudyFlowDashboardData>
    }.catch { err ->
        emit(UiState.Error(err.localizedMessage ?: "Failed to load study data."))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    init {
        viewModelScope.launch {
            ensureUserProfileExists()
            scanMissedRemindersNow()
            ReminderScheduler.schedulePeriodicBackgroundWorkers(appContext)
        }
    }

    private suspend fun ensureUserProfileExists() {
        val existing = db.userProfileDao().getProfileOnce(currentUserId)
        if (existing == null) {
            db.userProfileDao().upsert(
                UserProfileEntity(
                    userId = currentUserId,
                    displayName = initialDisplayName.ifBlank { "Student" },
                    email = initialEmail,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun postMessage(msg: String) {
        _statusMessage.value = msg
    }

    // --- Profile & Preferences ---
    fun updateProfile(
        displayName: String,
        gradeLevel: String,
        schoolName: String,
        academicYear: String,
        preferredStartHour: Int,
        preferredEndHour: Int,
        dailyGoalMinutes: Int,
        soundEnabled: Boolean,
        vibrationEnabled: Boolean,
        language: String
    ) {
        viewModelScope.launch {
            val current = db.userProfileDao().getProfileOnce(currentUserId)
                ?: UserProfileEntity(userId = currentUserId, displayName = displayName)
            db.userProfileDao().upsert(
                current.copy(
                    displayName = displayName.trim().ifBlank { "Student" }.take(80),
                    gradeLevel = gradeLevel.trim().ifBlank { "High School" }.take(40),
                    schoolName = schoolName.trim().take(100),
                    academicYear = academicYear.trim().take(30),
                    preferredStartHour = preferredStartHour.coerceIn(0, 22),
                    preferredEndHour = preferredEndHour.coerceIn(preferredStartHour + 1, 23),
                    dailyGoalMinutes = dailyGoalMinutes.coerceIn(15, 720),
                    soundEnabled = soundEnabled,
                    vibrationEnabled = vibrationEnabled,
                    language = language.ifBlank { "English" }.take(20),
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
            _statusMessage.value = "Profile updated."
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesManager.setThemeMode(mode) }
    }

    fun setAccentPalette(palette: AccentPalette) {
        viewModelScope.launch { preferencesManager.setAccentPalette(palette) }
    }

    fun setOfflineMode(offline: Boolean) {
        viewModelScope.launch { preferencesManager.setOfflineMode(offline) }
    }

    fun saveDashboardLayout(order: List<DashboardCardType>, hidden: Set<DashboardCardType>) {
        viewModelScope.launch {
            preferencesManager.saveDashboardLayout(order, hidden)
            _statusMessage.value = "Dashboard layout updated."
        }
    }

    fun enablePinLock(pin: String) {
        if (pin.length < 4) {
            _statusMessage.value = "PIN must be at least 4 digits."
            return
        }
        viewModelScope.launch {
            val salt = PrivacyLockManager.generateSaltBase64()
            val hash = PrivacyLockManager.hashPin(pin, salt)
            preferencesManager.setPinLock(true, hash, salt)
            _statusMessage.value = "App PIN lock enabled."
        }
    }

    fun disablePinLock() {
        viewModelScope.launch {
            preferencesManager.clearSecuritySettings()
            _statusMessage.value = "Privacy lock disabled."
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setBiometricEnabled(enabled)
        }
    }

    // --- Subjects ---
    fun saveSubject(
        id: String? = null,
        name: String,
        code: String = "",
        teacherName: String = "",
        colorHex: Long = 0xFF2563EBL,
        targetAttendancePercent: Int = 75
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            _statusMessage.value = "Subject name is required."
            return
        }
        viewModelScope.launch {
            db.subjectDao().upsert(
                SubjectEntity(
                    id = id ?: newId("subj"),
                    userId = currentUserId,
                    name = cleanName.take(80),
                    code = code.trim().take(20),
                    teacherName = teacherName.trim().take(80),
                    colorHex = colorHex,
                    targetAttendancePercent = targetAttendancePercent.coerceIn(0, 100),
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
            _statusMessage.value = "Subject saved."
        }
    }

    fun deleteSubject(id: String) {
        viewModelScope.launch {
            db.subjectDao().deleteById(id)
            _statusMessage.value = "Subject removed."
        }
    }

    // --- Homework ---
    fun saveHomework(
        id: String? = null,
        subjectId: String,
        subjectName: String,
        title: String,
        description: String,
        dueAtMillis: Long,
        priority: String,
        attachmentUri: String? = null,
        attachmentLabel: String? = null,
        reminderEnabled: Boolean = true
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            _statusMessage.value = "Homework title is required."
            return
        }
        viewModelScope.launch {
            val hwId = id ?: newId("hw")
            val now = System.currentTimeMillis()
            val entity = HomeworkEntity(
                id = hwId,
                userId = currentUserId,
                subjectId = subjectId.ifBlank { "general" },
                subjectName = subjectName.ifBlank { "General" },
                title = cleanTitle.take(140),
                description = description.trim().take(2000),
                dueAtMillis = dueAtMillis,
                priority = priority,
                completed = false,
                attachmentUri = attachmentUri,
                attachmentLabel = attachmentLabel,
                reminderEnabled = reminderEnabled,
                updatedAtMillis = now
            )
            db.homeworkDao().upsert(entity)
            if (reminderEnabled && dueAtMillis > now) {
                val reminderTrigger = (dueAtMillis - TimeUnit.HOURS.toMillis(2))
                    .coerceAtLeast(now + 60_000L)
                val rem = ReminderEntity(
                    id = "rem_$hwId",
                    userId = currentUserId,
                    title = "Homework due: $cleanTitle",
                    notes = "$subjectName • Due ${StudyDateFormatters.formatDateTime(dueAtMillis)}",
                    category = "HOMEWORK",
                    priority = priority,
                    triggerAtMillis = reminderTrigger,
                    linkedEntityId = hwId,
                    updatedAtMillis = now
                )
                db.reminderDao().upsert(rem)
                ReminderScheduler.scheduleReminder(appContext, rem)
            }
            StudyFlowWidgetProvider.refreshAllWidgets(appContext)
            _statusMessage.value = "Homework saved."
        }
    }

    fun toggleHomeworkCompleted(item: HomeworkEntity) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val nextDone = !item.completed
            db.homeworkDao().upsert(
                item.copy(
                    completed = nextDone,
                    completedAtMillis = if (nextDone) now else null,
                    updatedAtMillis = now
                )
            )
            if (nextDone) {
                unlockAchievementIfNew(
                    code = "FIRST_HOMEWORK",
                    title = "Task Finisher",
                    description = "Completed your first homework assignment in StudyFlow."
                )
                val completedCount = db.homeworkDao().getAllOnce(currentUserId).count { it.completed }
                if (completedCount >= 5) {
                    unlockAchievementIfNew(
                        code = "FIVE_HOMEWORK",
                        title = "Homework Pro",
                        description = "Completed 5 homework assignments."
                    )
                }
            }
            StudyFlowWidgetProvider.refreshAllWidgets(appContext)
        }
    }

    fun deleteHomework(id: String) {
        viewModelScope.launch {
            db.homeworkDao().deleteById(id)
            db.reminderDao().deleteById("rem_$id")
            ReminderScheduler.cancelReminder(appContext, "rem_$id")
            StudyFlowWidgetProvider.refreshAllWidgets(appContext)
            _statusMessage.value = "Homework deleted."
        }
    }

    // --- Reminders & Smart Rescheduling ---
    fun saveReminder(
        id: String? = null,
        title: String,
        notes: String,
        category: String,
        priority: String,
        triggerAtMillis: Long,
        advanceMinutes: Int,
        repeatRule: String,
        customRepeatDays: String,
        soundEnabled: Boolean,
        vibrationEnabled: Boolean
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            _statusMessage.value = "Reminder title is required."
            return
        }
        viewModelScope.launch {
            val rem = ReminderEntity(
                id = id ?: newId("rem"),
                userId = currentUserId,
                title = cleanTitle.take(140),
                notes = notes.trim().take(1000),
                category = category,
                priority = priority,
                triggerAtMillis = triggerAtMillis,
                advanceMinutes = advanceMinutes.coerceIn(0, 1440),
                repeatRule = repeatRule,
                customRepeatDays = customRepeatDays,
                status = "UPCOMING",
                soundEnabled = soundEnabled,
                vibrationEnabled = vibrationEnabled,
                updatedAtMillis = System.currentTimeMillis()
            )
            db.reminderDao().upsert(rem)
            ReminderScheduler.scheduleReminder(appContext, rem)
            StudyFlowWidgetProvider.refreshAllWidgets(appContext)
            _statusMessage.value = "Reminder scheduled."
        }
    }

    fun snoozeReminder(reminder: ReminderEntity, minutes: Int = 15) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val updated = reminder.copy(
                triggerAtMillis = now + minutes * 60_000L,
                status = "SNOOZED",
                updatedAtMillis = now
            )
            db.reminderDao().upsert(updated)
            ReminderScheduler.scheduleReminder(appContext, updated)
            _statusMessage.value = "Snoozed for $minutes minutes."
        }
    }

    fun completeReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val updated = reminder.copy(status = "COMPLETED", updatedAtMillis = now)
            db.reminderDao().upsert(updated)
            ReminderScheduler.cancelReminder(appContext, reminder.id)
            StudyFlowWidgetProvider.refreshAllWidgets(appContext)
            _statusMessage.value = "Reminder marked completed."
        }
    }

    fun deleteReminder(id: String) {
        viewModelScope.launch {
            db.reminderDao().deleteById(id)
            ReminderScheduler.cancelReminder(appContext, id)
            StudyFlowWidgetProvider.refreshAllWidgets(appContext)
            _statusMessage.value = "Reminder deleted."
        }
    }

    fun getSmartRescheduleSuggestions(
        durationMinutes: Int = 45,
        data: StudyFlowDashboardData
    ): List<RescheduleSuggestion> {
        val profile = data.profile
        return SmartRescheduleEngine.suggestRescheduleSlots(
            nowMillis = System.currentTimeMillis(),
            durationMinutes = durationMinutes,
            preferredStartHour = profile?.preferredStartHour ?: 16,
            preferredEndHour = profile?.preferredEndHour ?: 21,
            timetable = data.timetable,
            existingReminders = data.reminders
        )
    }

    fun applySmartReschedule(reminder: ReminderEntity, suggestion: RescheduleSuggestion) {
        viewModelScope.launch {
            val updated = reminder.copy(
                triggerAtMillis = suggestion.startAtMillis,
                status = "UPCOMING",
                updatedAtMillis = System.currentTimeMillis()
            )
            db.reminderDao().upsert(updated)
            ReminderScheduler.scheduleReminder(appContext, updated)
            StudyFlowWidgetProvider.refreshAllWidgets(appContext)
            _statusMessage.value = "Rescheduled to ${suggestion.label}."
        }
    }

    private suspend fun scanMissedRemindersNow() {
        val now = System.currentTimeMillis()
        val grace = 15 * 60_000L
        val reminders = db.reminderDao().getAllOnce(currentUserId)
        for (r in reminders) {
            if ((r.status == "UPCOMING" || r.status == "SNOOZED") && r.triggerAtMillis + grace < now) {
                db.reminderDao().upsert(r.copy(status = "MISSED", updatedAtMillis = now))
            }
        }
    }

    // --- Timetable ---
    fun saveTimetableSession(
        id: String? = null,
        subjectId: String,
        subjectName: String,
        title: String,
        dayOfWeek: Int,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        sessionKind: String,
        notifyBeforeMinutes: Int,
        location: String
    ): Boolean {
        if (title.trim().isEmpty()) {
            _statusMessage.value = "Session title is required."
            return false
        }
        if (endMinuteOfDay <= startMinuteOfDay) {
            _statusMessage.value = "End time must be after start time."
            return false
        }
        viewModelScope.launch {
            val existing = db.timetableDao().getAllOnce(currentUserId)
            val conflicts = SmartRescheduleEngine.findConflicts(
                dayOfWeek, startMinuteOfDay, endMinuteOfDay, existing, id
            )
            if (conflicts.isNotEmpty()) {
                _statusMessage.value = "Schedule conflict with \"${conflicts.first().title}\"."
                return@launch
            }
            db.timetableDao().upsert(
                TimetableSessionEntity(
                    id = id ?: newId("tt"),
                    userId = currentUserId,
                    subjectId = subjectId,
                    subjectName = subjectName,
                    title = title.trim().take(100),
                    dayOfWeek = dayOfWeek.coerceIn(1, 7),
                    startMinuteOfDay = startMinuteOfDay,
                    endMinuteOfDay = endMinuteOfDay,
                    sessionKind = sessionKind,
                    notifyBeforeMinutes = notifyBeforeMinutes.coerceIn(0, 120),
                    location = location.trim().take(100),
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
            _statusMessage.value = "Timetable session saved."
        }
        return true
    }

    fun deleteTimetableSession(id: String) {
        viewModelScope.launch {
            db.timetableDao().deleteById(id)
            _statusMessage.value = "Session deleted."
        }
    }

    // --- Exams & Chapters ---
    fun saveExam(
        id: String? = null,
        subjectId: String,
        subjectName: String,
        title: String,
        examAtMillis: Long,
        importantTopics: String,
        preparationStatus: String,
        mockTest: Boolean,
        obtainedScore: Int? = null,
        maxScore: Int? = null
    ) {
        val clean = title.trim()
        if (clean.isEmpty()) {
            _statusMessage.value = "Exam title is required."
            return
        }
        viewModelScope.launch {
            val examId = id ?: newId("exam")
            val now = System.currentTimeMillis()
            db.examDao().upsert(
                ExamEntity(
                    id = examId,
                    userId = currentUserId,
                    subjectId = subjectId.ifBlank { "general" },
                    subjectName = subjectName.ifBlank { "General" },
                    title = clean.take(120),
                    examAtMillis = examAtMillis,
                    importantTopics = importantTopics.trim().take(2000),
                    preparationStatus = preparationStatus,
                    mockTest = mockTest,
                    obtainedScore = obtainedScore,
                    maxScore = maxScore,
                    updatedAtMillis = now
                )
            )
            if (examAtMillis > now + TimeUnit.DAYS.toMillis(1)) {
                val rem = ReminderEntity(
                    id = "rem_$examId",
                    userId = currentUserId,
                    title = "Exam tomorrow: $clean",
                    notes = "$subjectName • Review important topics",
                    category = "EXAM",
                    priority = "HIGH",
                    triggerAtMillis = examAtMillis - TimeUnit.DAYS.toMillis(1),
                    linkedEntityId = examId,
                    updatedAtMillis = now
                )
                db.reminderDao().upsert(rem)
                ReminderScheduler.scheduleReminder(appContext, rem)
            }
            _statusMessage.value = "Examination saved."
        }
    }

    fun deleteExam(id: String) {
        viewModelScope.launch {
            db.examDao().deleteById(id)
            _statusMessage.value = "Examination deleted."
        }
    }

    fun saveChapter(subjectId: String, examId: String?, title: String, difficult: Boolean = false) {
        if (title.trim().isEmpty()) return
        viewModelScope.launch {
            db.chapterDao().upsert(
                ChapterEntity(
                    id = newId("ch"),
                    userId = currentUserId,
                    subjectId = subjectId.ifBlank { "general" },
                    examId = examId,
                    title = title.trim().take(120),
                    difficult = difficult,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    fun toggleChapterCompleted(chapter: ChapterEntity) {
        viewModelScope.launch {
            db.chapterDao().upsert(
                chapter.copy(
                    completed = !chapter.completed,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    fun toggleChapterDifficult(chapter: ChapterEntity) {
        viewModelScope.launch {
            db.chapterDao().upsert(
                chapter.copy(
                    difficult = !chapter.difficult,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    // --- Smart Revision ---
    fun scheduleRevision(
        subjectId: String,
        subjectName: String,
        chapterTitle: String,
        scheduledAtMillis: Long,
        intervalDays: Int,
        difficult: Boolean = false
    ) {
        if (chapterTitle.trim().isEmpty()) {
            _statusMessage.value = "Chapter or topic title is required."
            return
        }
        viewModelScope.launch {
            val revId = newId("rev")
            val now = System.currentTimeMillis()
            db.revisionDao().upsert(
                RevisionScheduleEntity(
                    id = revId,
                    userId = currentUserId,
                    subjectId = subjectId.ifBlank { "general" },
                    subjectName = subjectName.ifBlank { "General" },
                    chapterTitle = chapterTitle.trim().take(120),
                    scheduledAtMillis = scheduledAtMillis,
                    intervalDays = intervalDays.coerceIn(1, 90),
                    difficult = difficult,
                    updatedAtMillis = now
                )
            )
            if (scheduledAtMillis > now) {
                val rem = ReminderEntity(
                    id = "rem_$revId",
                    userId = currentUserId,
                    title = "Revise: ${chapterTitle.trim()}",
                    notes = "$subjectName spaced revision",
                    category = "REVISION",
                    priority = if (difficult) "HIGH" else "MEDIUM",
                    triggerAtMillis = scheduledAtMillis,
                    linkedEntityId = revId,
                    updatedAtMillis = now
                )
                db.reminderDao().upsert(rem)
                ReminderScheduler.scheduleReminder(appContext, rem)
            }
            _statusMessage.value = "Revision scheduled."
        }
    }

    fun completeRevisionAndSuggestNext(item: RevisionScheduleEntity, scheduleNext: Boolean = true) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            db.revisionDao().upsert(
                item.copy(
                    completed = true,
                    completedAtMillis = now,
                    updatedAtMillis = now
                )
            )
            if (scheduleNext) {
                val nextInterval = when {
                    item.difficult -> (item.intervalDays * 2).coerceIn(1, 14)
                    item.intervalDays < 3 -> 3
                    item.intervalDays < 7 -> 7
                    else -> 14
                }
                val nextAt = now + TimeUnit.DAYS.toMillis(nextInterval.toLong())
                db.revisionDao().upsert(
                    RevisionScheduleEntity(
                        id = newId("rev"),
                        userId = currentUserId,
                        subjectId = item.subjectId,
                        subjectName = item.subjectName,
                        chapterId = item.chapterId,
                        chapterTitle = item.chapterTitle,
                        scheduledAtMillis = nextAt,
                        intervalDays = nextInterval,
                        difficult = item.difficult,
                        updatedAtMillis = now
                    )
                )
            }
            unlockAchievementIfNew(
                code = "FIRST_REVISION",
                title = "Memory Builder",
                description = "Completed a scheduled spaced revision session."
            )
            _statusMessage.value = if (scheduleNext) "Revision completed & next interval queued." else "Revision completed."
        }
    }

    fun toggleRevisionDifficult(item: RevisionScheduleEntity) {
        viewModelScope.launch {
            db.revisionDao().upsert(
                item.copy(difficult = !item.difficult, updatedAtMillis = System.currentTimeMillis())
            )
        }
    }

    fun deleteRevision(id: String) {
        viewModelScope.launch { db.revisionDao().deleteById(id) }
    }

    // --- Notes ---
    fun saveNote(
        id: String? = null,
        subjectId: String,
        subjectName: String,
        chapterName: String,
        title: String,
        content: String,
        important: Boolean,
        bookmarked: Boolean,
        attachmentUri: String? = null
    ) {
        if (title.trim().isEmpty()) {
            _statusMessage.value = "Note title is required."
            return
        }
        viewModelScope.launch {
            db.studyNoteDao().upsert(
                StudyNoteEntity(
                    id = id ?: newId("note"),
                    userId = currentUserId,
                    subjectId = subjectId.ifBlank { "general" },
                    subjectName = subjectName.ifBlank { "General" },
                    chapterName = chapterName.trim().take(100),
                    title = title.trim().take(140),
                    content = content.take(20000),
                    important = important,
                    bookmarked = bookmarked,
                    attachmentUri = attachmentUri,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
            _statusMessage.value = "Note saved."
        }
    }

    fun toggleNoteBookmark(note: StudyNoteEntity) {
        viewModelScope.launch {
            db.studyNoteDao().upsert(
                note.copy(bookmarked = !note.bookmarked, updatedAtMillis = System.currentTimeMillis())
            )
        }
    }

    fun toggleNoteImportant(note: StudyNoteEntity) {
        viewModelScope.launch {
            db.studyNoteDao().upsert(
                note.copy(important = !note.important, updatedAtMillis = System.currentTimeMillis())
            )
        }
    }

    fun deleteNote(id: String) {
        viewModelScope.launch {
            db.studyNoteDao().deleteById(id)
            _statusMessage.value = "Note deleted."
        }
    }

    // --- Study Sessions, Streaks, Goals & Achievements ---
    fun recordFocusSession(
        mode: FocusTimerMode,
        durationMinutes: Int,
        subjectId: String,
        subjectName: String,
        startedAtMillis: Long
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            db.studySessionDao().upsert(
                StudySessionEntity(
                    id = newId("sess"),
                    userId = currentUserId,
                    subjectId = subjectId.ifBlank { "general" },
                    subjectName = subjectName.ifBlank { "General Study" },
                    mode = mode.name,
                    durationMinutes = durationMinutes.coerceAtLeast(1),
                    startedAtMillis = startedAtMillis,
                    endedAtMillis = now,
                    completed = true,
                    updatedAtMillis = now
                )
            )
            updateStreakAndGoalsAfterStudy(durationMinutes, subjectId)
            StudyFlowWidgetProvider.refreshAllWidgets(appContext)
            _statusMessage.value = "Logged ${durationMinutes}m focus session!"
        }
    }

    private suspend fun updateStreakAndGoalsAfterStudy(minutes: Int, subjectId: String) {
        val now = System.currentTimeMillis()
        val todayKey = StudyDateFormatters.todayDayKey(now)
        val yesterdayKey = StudyDateFormatters.todayDayKey(now - TimeUnit.DAYS.toMillis(1))
        val profile = db.userProfileDao().getProfileOnce(currentUserId)
            ?: UserProfileEntity(userId = currentUserId, displayName = initialDisplayName.ifBlank { "Student" })

        val newStreak = when (profile.lastStudyDayKey) {
            todayKey -> profile.currentStreak.coerceAtLeast(1)
            yesterdayKey -> profile.currentStreak + 1
            else -> 1
        }
        val bestStreak = maxOf(profile.longestStreak, newStreak)
        val totalMinutes = profile.totalFocusMinutes + minutes

        db.userProfileDao().upsert(
            profile.copy(
                currentStreak = newStreak,
                longestStreak = bestStreak,
                totalFocusMinutes = totalMinutes,
                lastStudyDayKey = todayKey,
                updatedAtMillis = now
            )
        )

        // Advance matching minute-based goals
        val goals = db.goalDao().getAllOnce(currentUserId)
        for (g in goals) {
            if (!g.completed && g.unit.equals("mins", ignoreCase = true) &&
                (g.subjectId == null || g.subjectId == subjectId)
            ) {
                val updatedVal = (g.currentValue + minutes).coerceAtMost(g.targetValue)
                val done = updatedVal >= g.targetValue
                db.goalDao().upsert(
                    g.copy(currentValue = updatedVal, completed = done, updatedAtMillis = now)
                )
            }
        }

        unlockAchievementIfNew(
            code = "FIRST_FOCUS",
            title = "Deep Focus Starter",
            description = "Completed your first timed study session."
        )
        if (newStreak >= 3) {
            unlockAchievementIfNew(
                code = "STREAK_3",
                title = "3-Day Momentum",
                description = "Studied consistently across 3 days."
            )
        }
        if (totalMinutes >= 300) {
            unlockAchievementIfNew(
                code = "FIVE_HOURS",
                title = "5-Hour Scholar",
                description = "Accumulated 300+ minutes of focused study."
            )
        }
    }

    fun saveGoal(
        id: String? = null,
        title: String,
        period: String,
        targetValue: Int,
        unit: String,
        subjectId: String? = null
    ) {
        if (title.trim().isEmpty()) {
            _statusMessage.value = "Goal title is required."
            return
        }
        viewModelScope.launch {
            db.goalDao().upsert(
                GoalEntity(
                    id = id ?: newId("goal"),
                    userId = currentUserId,
                    title = title.trim().take(120),
                    subjectId = subjectId,
                    period = period,
                    targetValue = targetValue.coerceAtLeast(1),
                    currentValue = 0,
                    unit = unit.ifBlank { "mins" }.take(24),
                    completed = false,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
            _statusMessage.value = "Goal added."
        }
    }

    fun incrementGoal(goal: GoalEntity, delta: Int) {
        viewModelScope.launch {
            val next = (goal.currentValue + delta).coerceIn(0, goal.targetValue)
            db.goalDao().upsert(
                goal.copy(
                    currentValue = next,
                    completed = next >= goal.targetValue,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteGoal(id: String) {
        viewModelScope.launch { db.goalDao().deleteById(id) }
    }

    // --- Attendance ---
    fun markAttendance(subjectId: String, subjectName: String, present: Boolean, dateMillis: Long = System.currentTimeMillis(), notes: String = "") {
        viewModelScope.launch {
            db.attendanceDao().upsert(
                AttendanceRecordEntity(
                    id = newId("att"),
                    userId = currentUserId,
                    subjectId = subjectId,
                    subjectName = subjectName,
                    dateMillis = dateMillis,
                    present = present,
                    notes = notes.trim().take(300),
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
            _statusMessage.value = if (present) "Marked Present for $subjectName." else "Marked Absent for $subjectName."
        }
    }

    // --- Flashcards & Quizzes ---
    fun saveFlashcard(
        id: String? = null,
        subjectId: String,
        subjectName: String,
        chapterName: String,
        question: String,
        answer: String
    ) {
        if (question.trim().isEmpty() || answer.trim().isEmpty()) {
            _statusMessage.value = "Question and answer are both required."
            return
        }
        viewModelScope.launch {
            db.flashcardDao().upsert(
                FlashcardEntity(
                    id = id ?: newId("fc"),
                    userId = currentUserId,
                    subjectId = subjectId.ifBlank { "general" },
                    subjectName = subjectName.ifBlank { "General" },
                    chapterName = chapterName.trim().take(100),
                    question = question.trim().take(1000),
                    answer = answer.trim().take(2000),
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
            _statusMessage.value = "Flashcard saved."
        }
    }

    fun saveGeneratedFlashcards(cards: List<Pair<String, String>>, subjectId: String, subjectName: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val entities = cards.map { (q, a) ->
                FlashcardEntity(
                    id = newId("fc"),
                    userId = currentUserId,
                    subjectId = subjectId.ifBlank { "general" },
                    subjectName = subjectName.ifBlank { "AI Generated" },
                    chapterName = "AI Study Set",
                    question = q.take(1000),
                    answer = a.take(2000),
                    updatedAtMillis = now
                )
            }
            db.flashcardDao().upsertAll(entities)
            _statusMessage.value = "Saved ${entities.size} flashcards to your deck."
        }
    }

    fun updateFlashcardReview(card: FlashcardEntity, mastered: Boolean, difficult: Boolean) {
        viewModelScope.launch {
            db.flashcardDao().upsert(
                card.copy(
                    mastered = mastered,
                    difficult = difficult,
                    reviewCount = card.reviewCount + 1,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteFlashcard(id: String) {
        viewModelScope.launch { db.flashcardDao().deleteById(id) }
    }

    fun recordQuizResult(title: String, subjectId: String?, subjectName: String, correctCount: Int, totalCount: Int) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            db.quizResultDao().upsert(
                QuizResultEntity(
                    id = newId("quiz"),
                    userId = currentUserId,
                    subjectId = subjectId,
                    subjectName = subjectName,
                    title = title.take(120),
                    correctCount = correctCount.coerceAtLeast(0),
                    totalCount = totalCount.coerceAtLeast(1),
                    takenAtMillis = now,
                    updatedAtMillis = now
                )
            )
            unlockAchievementIfNew(
                code = "FIRST_QUIZ",
                title = "Quiz Explorer",
                description = "Completed a practice quiz and reviewed explanations."
            )
        }
    }

    // --- Calendar Events & Notifications ---
    fun saveCalendarEvent(title: String, description: String, eventAtMillis: Long) {
        if (title.trim().isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = newId("evt")
            db.calendarEventDao().upsert(
                CalendarEventEntity(
                    id = id,
                    userId = currentUserId,
                    title = title.trim().take(120),
                    description = description.trim().take(500),
                    eventAtMillis = eventAtMillis,
                    updatedAtMillis = now
                )
            )
            if (eventAtMillis > now) {
                val rem = ReminderEntity(
                    id = "rem_$id",
                    userId = currentUserId,
                    title = title.trim(),
                    notes = description.trim(),
                    category = "CUSTOM",
                    priority = "MEDIUM",
                    triggerAtMillis = eventAtMillis,
                    linkedEntityId = id,
                    updatedAtMillis = now
                )
                db.reminderDao().upsert(rem)
                ReminderScheduler.scheduleReminder(appContext, rem)
            }
            _statusMessage.value = "Calendar event added."
        }
    }

    fun deleteCalendarEvent(id: String) {
        viewModelScope.launch { db.calendarEventDao().deleteById(id) }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            db.appNotificationDao().markAllRead(currentUserId, System.currentTimeMillis())
        }
    }

    fun clearNotifications() {
        viewModelScope.launch {
            db.appNotificationDao().deleteForUser(currentUserId)
        }
    }

    // --- Cloud Sync, Export, Import, Delete ---
    fun triggerCloudSync() {
        viewModelScope.launch {
            val prefs = preferencesManager.preferencesFlow.first()
            if (prefs.offlineMode) {
                _statusMessage.value = "Offline mode is active. Disable Offline Mode to sync."
                return@launch
            }
            _isSyncing.value = true
            cloudSyncRepository.syncAll(currentUserId)
                .onSuccess { msg ->
                    preferencesManager.updateSyncStatus(System.currentTimeMillis(), msg)
                    _statusMessage.value = msg
                }
                .onFailure { err ->
                    preferencesManager.updateSyncStatus(0L, "Sync paused — local data preserved")
                    _statusMessage.value = "Sync failed (local data is safe): ${err.localizedMessage ?: "Network error"}"
                }
            _isSyncing.value = false
        }
    }

    fun exportBackupJson(uri: Uri) {
        viewModelScope.launch {
            exportManager.exportJsonToUri(currentUserId, uri)
                .onSuccess { _statusMessage.value = it }
                .onFailure { _statusMessage.value = "Export failed: ${it.localizedMessage}" }
        }
    }

    fun importBackupJson(uri: Uri) {
        viewModelScope.launch {
            exportManager.importJsonFromUri(currentUserId, uri)
                .onSuccess { _statusMessage.value = it }
                .onFailure { _statusMessage.value = "Restore failed: ${it.localizedMessage}" }
        }
    }

    fun exportProgressCsv(uri: Uri) {
        viewModelScope.launch {
            exportManager.exportAcademicReportCsvToUri(currentUserId, uri)
                .onSuccess { _statusMessage.value = it }
                .onFailure { _statusMessage.value = "Report export failed: ${it.localizedMessage}" }
        }
    }

    fun deleteAllMyData(onDeleted: () -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            cloudSyncRepository.deleteAllUserData(currentUserId)
                .onSuccess {
                    preferencesManager.clearSecuritySettings()
                    _statusMessage.value = "All cloud and local StudyFlow data deleted."
                    onDeleted()
                }
                .onFailure { err ->
                    _statusMessage.value = "Could not delete cloud data: ${err.localizedMessage}"
                }
            _isSyncing.value = false
        }
    }

    private suspend fun unlockAchievementIfNew(code: String, title: String, description: String) {
        val existing = db.achievementDao().getAllOnce(currentUserId)
        if (existing.any { it.code == code }) return
        val now = System.currentTimeMillis()
        db.achievementDao().upsert(
            AchievementEntity(
                id = newId("ach"),
                userId = currentUserId,
                code = code,
                title = title,
                description = description,
                unlockedAtMillis = now,
                updatedAtMillis = now
            )
        )
        db.appNotificationDao().upsert(
            AppNotificationEntity(
                id = newId("notif"),
                userId = currentUserId,
                title = "Achievement Unlocked: $title",
                message = description,
                category = "ACHIEVEMENT",
                read = false,
                timestampMillis = now,
                updatedAtMillis = now
            )
        )
    }

    private fun computeAnalytics(
        subjects: List<SubjectEntity>,
        homework: List<HomeworkEntity>,
        revisions: List<RevisionScheduleEntity>,
        sessions: List<StudySessionEntity>,
        goals: List<GoalEntity>,
        attendance: List<AttendanceRecordEntity>,
        quizzes: List<QuizResultEntity>
    ): AcademicAnalytics {
        val now = System.currentTimeMillis()
        val todayStart = StudyDateFormatters.startOfTodayMillis()
        val weekStart = todayStart - TimeUnit.DAYS.toMillis(6)
        val monthStart = todayStart - TimeUnit.DAYS.toMillis(29)

        val completedSessions = sessions.filter { it.completed }
        val todayMins = completedSessions.filter { it.startedAtMillis >= todayStart }.sumOf { it.durationMinutes }
        val weekMins = completedSessions.filter { it.startedAtMillis >= weekStart }.sumOf { it.durationMinutes }
        val monthMins = completedSessions.filter { it.startedAtMillis >= monthStart }.sumOf { it.durationMinutes }
        val totalMins = completedSessions.sumOf { it.durationMinutes }

        val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())
        val last7Days = (6 downTo 0).map { offset ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = todayStart
                add(Calendar.DAY_OF_YEAR, -offset)
            }
            val start = cal.timeInMillis
            val end = start + TimeUnit.DAYS.toMillis(1)
            val mins = completedSessions
                .filter { it.startedAtMillis in start until end }
                .sumOf { it.durationMinutes }
            dayFmt.format(Date(start)) to mins
        }

        val hwTotal = homework.size
        val hwDone = homework.count { it.completed }
        val hwOverdue = homework.count { !it.completed && it.dueAtMillis < now }
        val hwPct = if (hwTotal == 0) 0 else (hwDone * 100 / hwTotal)

        val revTotal = revisions.size
        val revDone = revisions.count { it.completed }
        val revPct = if (revTotal == 0) 0 else (revDone * 100 / revTotal)

        val subjAtt = mutableMapOf<String, Int>()
        val lowAttNames = mutableListOf<String>()
        for (s in subjects) {
            val records = attendance.filter { it.subjectId == s.id }
            if (records.isNotEmpty()) {
                val pct = records.count { it.present } * 100 / records.size
                subjAtt[s.id] = pct
                if (pct < s.targetAttendancePercent) {
                    lowAttNames.add(s.name)
                }
            }
        }
        val overallAtt = if (attendance.isEmpty()) 100 else (attendance.count { it.present } * 100 / attendance.size)

        val avgQuiz = if (quizzes.isEmpty()) 0 else {
            quizzes.map { it.correctCount * 100.0 / it.totalCount.coerceAtLeast(1) }.average().toInt()
        }
        val goalPct = if (goals.isEmpty()) 0 else (goals.count { it.completed } * 100 / goals.size)

        return AcademicAnalytics(
            todayFocusMinutes = todayMins,
            weekFocusMinutes = weekMins,
            monthFocusMinutes = monthMins,
            totalFocusMinutes = totalMins,
            dailyMinutesLast7Days = last7Days,
            homeworkTotal = hwTotal,
            homeworkCompleted = hwDone,
            homeworkOverdue = hwOverdue,
            homeworkCompletionPercent = hwPct,
            revisionTotal = revTotal,
            revisionCompleted = revDone,
            revisionPercent = revPct,
            overallAttendancePercent = overallAtt,
            subjectAttendance = subjAtt,
            lowAttendanceSubjectNames = lowAttNames,
            averageQuizPercent = avgQuiz,
            goalCompletionPercent = goalPct
        )
    }

    private fun newId(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(12)}"

    private data class CoreAcademicSlice(
        val profile: UserProfileEntity?,
        val subjects: List<SubjectEntity>,
        val homework: List<HomeworkEntity>,
        val reminders: List<ReminderEntity>,
        val timetable: List<TimetableSessionEntity>
    )

    private data class StudyProgressSlice(
        val exams: List<ExamEntity>,
        val chapters: List<ChapterEntity>,
        val revisions: List<RevisionScheduleEntity>,
        val notes: List<StudyNoteEntity>,
        val sessions: List<StudySessionEntity>
    )

    private data class EngagementSlice(
        val goals: List<GoalEntity>,
        val attendance: List<AttendanceRecordEntity>,
        val flashcards: List<FlashcardEntity>,
        val quizzes: List<QuizResultEntity>,
        val achievements: List<AchievementEntity>
    )
}
