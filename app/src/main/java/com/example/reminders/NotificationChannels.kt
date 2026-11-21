package com.example.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val CHANNEL_REMINDERS = "studyflow_reminders"
    const val CHANNEL_REMINDERS_SILENT = "studyflow_reminders_silent"
    const val CHANNEL_TIMER = "studyflow_timer"
    const val CHANNEL_GOALS = "studyflow_goals"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDERS,
            "Study & Homework Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts for homework deadlines, classes, exams, and revision sessions."
            enableVibration(true)
        }

        val silentReminderChannel = NotificationChannel(
            CHANNEL_REMINDERS_SILENT,
            "Quiet Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Muted study and revision reminders."
            setSound(null, null)
            enableVibration(false)
        }

        val timerChannel = NotificationChannel(
            CHANNEL_TIMER,
            "Focus Timer Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications when Pomodoro focus or break sessions complete."
        }

        val goalsChannel = NotificationChannel(
            CHANNEL_GOALS,
            "Goals & Milestones",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for completed daily goals and study milestones."
        }

        manager.createNotificationChannels(
            listOf(reminderChannel, silentReminderChannel, timerChannel, goalsChannel)
        )
    }
}
