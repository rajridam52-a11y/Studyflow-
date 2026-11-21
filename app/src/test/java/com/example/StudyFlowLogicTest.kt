package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.*
import com.example.reminders.ReminderScheduler
import com.example.reminders.SmartRescheduleEngine
import com.example.security.PrivacyLockManager
import com.example.viewmodel.StudyDateFormatters
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StudyFlowLogicTest {

    private lateinit var db: StudyFlowDatabase
    private val testUser = "test_student_1"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, StudyFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `smart reschedule detects timetable conflicts accurately`() {
        val existing = listOf(
            TimetableSessionEntity(
                id = "tt_1",
                userId = testUser,
                title = "Physics Lab",
                dayOfWeek = 1,
                startMinuteOfDay = 16 * 60,
                endMinuteOfDay = 17 * 60
            )
        )
        val overlapping = SmartRescheduleEngine.findConflicts(
            candidateDayOfWeek = 1,
            startMinuteOfDay = 16 * 60 + 30,
            endMinuteOfDay = 17 * 60 + 30,
            existingSessions = existing
        )
        assertEquals(1, overlapping.size)

        val clearSlot = SmartRescheduleEngine.findConflicts(
            candidateDayOfWeek = 1,
            startMinuteOfDay = 17 * 60,
            endMinuteOfDay = 18 * 60,
            existingSessions = existing
        )
        assertTrue(clearSlot.isEmpty())
    }

    @Test
    fun `smart reschedule finds free slots and suggests open times`() {
        val sessions = listOf(
            TimetableSessionEntity(
                id = "tt_busy",
                userId = testUser,
                title = "Math Class",
                dayOfWeek = 2,
                startMinuteOfDay = 16 * 60,
                endMinuteOfDay = 18 * 60
            )
        )
        val free = SmartRescheduleEngine.findFreeSlotsForDay(
            dayOfWeek = 2,
            sessions = sessions,
            preferredStartHour = 15,
            preferredEndHour = 21,
            minDurationMinutes = 45
        )
        assertEquals(2, free.size)
        assertEquals(15 * 60, free[0].startMinuteOfDay)
        assertEquals(16 * 60, free[0].endMinuteOfDay)
        assertEquals(18 * 60, free[1].startMinuteOfDay)
        assertEquals(21 * 60, free[1].endMinuteOfDay)

        val suggestions = SmartRescheduleEngine.suggestRescheduleSlots(
            nowMillis = System.currentTimeMillis(),
            durationMinutes = 45,
            preferredStartHour = 8,
            preferredEndHour = 22,
            timetable = sessions,
            existingReminders = emptyList()
        )
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.first().endAtMillis > suggestions.first().startAtMillis)
    }

    @Test
    fun `reminder repeat scheduler calculates daily weekly and custom days`() {
        val baseCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
        }
        val daily = ReminderEntity(
            id = "r1",
            userId = testUser,
            title = "Daily Review",
            triggerAtMillis = baseCal.timeInMillis,
            repeatRule = "DAILY"
        )
        val nextDaily = ReminderScheduler.computeNextRepeatTrigger(daily)
        assertNotNull(nextDaily)
        assertTrue(nextDaily!! > baseCal.timeInMillis)

        val custom = daily.copy(repeatRule = "CUSTOM_DAYS", customRepeatDays = "1,3,5")
        val nextCustom = ReminderScheduler.computeNextRepeatTrigger(custom)
        assertNotNull(nextCustom)

        val once = daily.copy(repeatRule = "NONE")
        assertNull(ReminderScheduler.computeNextRepeatTrigger(once))
    }

    @Test
    fun `privacy lock manager hashes and verifies pins securely`() {
        val salt = PrivacyLockManager.generateSaltBase64()
        val hash = PrivacyLockManager.hashPin("4829", salt)
        assertTrue(PrivacyLockManager.verifyPin("4829", salt, hash))
        assertFalse(PrivacyLockManager.verifyPin("0000", salt, hash))
    }

    @Test
    fun `countdown formatter labels overdue and upcoming accurately`() {
        val now = 1_700_000_000_000L
        assertTrue(
            StudyDateFormatters.formatCountdown(now + TimeUnit.HOURS.toMillis(5), now)
                .contains("5h left")
        )
        assertTrue(
            StudyDateFormatters.formatCountdown(now - TimeUnit.DAYS.toMillis(2), now)
                .startsWith("Overdue")
        )
    }

    @Test
    fun `room database persists subjects homework notes flashcards and attendance`() = runTest {
        db.subjectDao().upsert(
            SubjectEntity(id = "sub_math", userId = testUser, name = "Mathematics", targetAttendancePercent = 80)
        )
        db.homeworkDao().upsert(
            HomeworkEntity(
                id = "hw_1",
                userId = testUser,
                subjectId = "sub_math",
                subjectName = "Mathematics",
                title = "Linear Algebra Problem Set",
                dueAtMillis = System.currentTimeMillis() + 86_400_000L,
                priority = "HIGH"
            )
        )
        db.studyNoteDao().upsert(
            StudyNoteEntity(
                id = "n_1",
                userId = testUser,
                subjectId = "sub_math",
                subjectName = "Mathematics",
                title = "Eigenvalues",
                content = "Av = lambda v",
                important = true,
                bookmarked = true
            )
        )
        db.attendanceDao().upsert(
            AttendanceRecordEntity(
                id = "att_1",
                userId = testUser,
                subjectId = "sub_math",
                subjectName = "Mathematics",
                dateMillis = System.currentTimeMillis(),
                present = true
            )
        )

        assertEquals(1, db.subjectDao().getAllOnce(testUser).size)
        assertEquals("Linear Algebra Problem Set", db.homeworkDao().getAllOnce(testUser).first().title)
        assertTrue(db.studyNoteDao().getAllOnce(testUser).first().bookmarked)
        assertTrue(db.attendanceDao().getAllOnce(testUser).first().present)
    }
}
