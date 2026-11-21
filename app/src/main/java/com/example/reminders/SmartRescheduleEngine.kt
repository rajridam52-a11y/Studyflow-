package com.example.reminders

import com.example.data.local.ReminderEntity
import com.example.data.local.TimetableSessionEntity
import java.util.Calendar

data class RescheduleSuggestion(
    val startAtMillis: Long,
    val endAtMillis: Long,
    val label: String,
    val reason: String
)

data class FreeTimeSlot(
    val dayOfWeek: Int,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int
)

object SmartRescheduleEngine {

    /**
     * Checks whether a candidate timetable session overlaps any existing session on the same day.
     */
    fun findConflicts(
        candidateDayOfWeek: Int,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        existingSessions: List<TimetableSessionEntity>,
        ignoreSessionId: String? = null
    ): List<TimetableSessionEntity> {
        return existingSessions.filter { session ->
            session.id != ignoreSessionId &&
                session.dayOfWeek == candidateDayOfWeek &&
                startMinuteOfDay < session.endMinuteOfDay &&
                endMinuteOfDay > session.startMinuteOfDay
        }
    }

    /**
     * Identifies available free-time windows between [preferredStartHour] and [preferredEndHour]
     * for a given day of week (1=Monday..7=Sunday).
     */
    fun findFreeSlotsForDay(
        dayOfWeek: Int,
        sessions: List<TimetableSessionEntity>,
        preferredStartHour: Int = 8,
        preferredEndHour: Int = 22,
        minDurationMinutes: Int = 30
    ): List<FreeTimeSlot> {
        val windowStart = (preferredStartHour.coerceIn(0, 22)) * 60
        val windowEnd = (preferredEndHour.coerceIn(preferredStartHour + 1, 24)) * 60
        val daySessions = sessions
            .filter { it.dayOfWeek == dayOfWeek }
            .sortedBy { it.startMinuteOfDay }

        val free = mutableListOf<FreeTimeSlot>()
        var cursor = windowStart
        for (s in daySessions) {
            if (s.startMinuteOfDay > cursor) {
                val end = minOf(s.startMinuteOfDay, windowEnd)
                if (end - cursor >= minDurationMinutes) {
                    free.add(FreeTimeSlot(dayOfWeek, cursor, end))
                }
            }
            if (s.endMinuteOfDay > cursor) {
                cursor = maxOf(cursor, s.endMinuteOfDay)
            }
        }
        if (windowEnd > cursor && windowEnd - cursor >= minDurationMinutes) {
            free.add(FreeTimeSlot(dayOfWeek, cursor, windowEnd))
        }
        return free
    }

    /**
     * Suggests up to 3 open reschedule times over the next 3 days for a missed reminder or study session,
     * avoiding timetable conflicts and existing upcoming reminders.
     */
    fun suggestRescheduleSlots(
        nowMillis: Long,
        durationMinutes: Int,
        preferredStartHour: Int,
        preferredEndHour: Int,
        timetable: List<TimetableSessionEntity>,
        existingReminders: List<ReminderEntity>
    ): List<RescheduleSuggestion> {
        val safeDuration = durationMinutes.coerceIn(20, 120)
        val suggestions = mutableListOf<RescheduleSuggestion>()
        val busyReminderTimes = existingReminders
            .filter { it.status == "UPCOMING" || it.status == "SNOOZED" }
            .map { it.triggerAtMillis }

        for (dayOffset in 0..2) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val javaDay = cal.get(Calendar.DAY_OF_WEEK) // Sun=1..Sat=7
            val isoDay = if (javaDay == Calendar.SUNDAY) 7 else javaDay - 1 // Mon=1..Sun=7
            val nowMinuteToday = if (dayOffset == 0) {
                cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE) + 15
            } else {
                0
            }

            val freeSlots = findFreeSlotsForDay(
                dayOfWeek = isoDay,
                sessions = timetable,
                preferredStartHour = preferredStartHour,
                preferredEndHour = preferredEndHour,
                minDurationMinutes = safeDuration
            )

            for (slot in freeSlots) {
                val candidateStartMin = maxOf(slot.startMinuteOfDay, roundUpToQuarterHour(nowMinuteToday))
                if (candidateStartMin + safeDuration <= slot.endMinuteOfDay) {
                    val startCal = (cal.clone() as Calendar).apply {
                        set(Calendar.HOUR_OF_DAY, candidateStartMin / 60)
                        set(Calendar.MINUTE, candidateStartMin % 60)
                    }
                    val startMs = startCal.timeInMillis
                    val hasNearbyReminder = busyReminderTimes.any { kotlin.math.abs(it - startMs) < 30 * 60_000L }
                    if (!hasNearbyReminder) {
                        val endMs = startMs + safeDuration * 60_000L
                        val dayLabel = when (dayOffset) {
                            0 -> "Today"
                            1 -> "Tomorrow"
                            else -> dayName(isoDay)
                        }
                        val timeLabel = formatMinutes(candidateStartMin)
                        suggestions.add(
                            RescheduleSuggestion(
                                startAtMillis = startMs,
                                endAtMillis = endMs,
                                label = "$dayLabel at $timeLabel",
                                reason = "Open ${slot.endMinuteOfDay - candidateStartMin}m window with no timetable conflicts"
                            )
                        )
                        if (suggestions.size >= 3) return suggestions
                    }
                }
            }
        }

        // Fallback if student's timetable is completely packed
        if (suggestions.isEmpty()) {
            val fallbackStart = roundUpMillisToHalfHour(nowMillis + 60 * 60_000L)
            suggestions.add(
                RescheduleSuggestion(
                    startAtMillis = fallbackStart,
                    endAtMillis = fallbackStart + safeDuration * 60_000L,
                    label = "In 1 hour",
                    reason = "Quick catch-up slot"
                )
            )
        }
        return suggestions
    }

    private fun roundUpToQuarterHour(minute: Int): Int {
        if (minute <= 0) return 0
        val rem = minute % 15
        return if (rem == 0) minute else minute + (15 - rem)
    }

    private fun roundUpMillisToHalfHour(millis: Long): Long {
        val thirtyMin = 30 * 60_000L
        return ((millis + thirtyMin - 1) / thirtyMin) * thirtyMin
    }

    fun formatMinutes(minuteOfDay: Int): String {
        val h24 = (minuteOfDay / 60).coerceIn(0, 23)
        val m = (minuteOfDay % 60).coerceIn(0, 59)
        val amPm = if (h24 < 12) "AM" else "PM"
        val h12 = when {
            h24 == 0 -> 12
            h24 > 12 -> h24 - 12
            else -> h24
        }
        return String.format("%d:%02d %s", h12, m, amPm)
    }

    fun dayName(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "Monday"
        2 -> "Tuesday"
        3 -> "Wednesday"
        4 -> "Thursday"
        5 -> "Friday"
        6 -> "Saturday"
        else -> "Sunday"
    }
}
