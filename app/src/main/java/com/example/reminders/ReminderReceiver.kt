package com.example.reminders

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppNotificationEntity
import com.example.data.local.StudyFlowDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = StudyFlowDatabase.getInstance(context)
                val reminderDao = db.reminderDao()
                val reminder = reminderDao.getByIdOnce(reminderId) ?: return@launch
                val now = System.currentTimeMillis()

                when (intent.action) {
                    ReminderScheduler.ACTION_TRIGGER_REMINDER -> {
                        // Deduplicate if triggered within the last 30 seconds
                        if (now - reminder.lastTriggeredMillis < 30_000L) return@launch
                        if (reminder.status == "COMPLETED") return@launch

                        showReminderNotification(context, reminder)

                        db.appNotificationDao().upsert(
                            AppNotificationEntity(
                                id = UUID.randomUUID().toString(),
                                userId = reminder.userId,
                                title = reminder.title,
                                message = reminder.notes.ifBlank { "${reminder.category.replace('_', ' ')} reminder" },
                                category = reminder.category,
                                read = false,
                                timestampMillis = now,
                                updatedAtMillis = now
                            )
                        )

                        val nextTrigger = ReminderScheduler.computeNextRepeatTrigger(reminder)
                        if (nextTrigger != null) {
                            val updated = reminder.copy(
                                triggerAtMillis = nextTrigger,
                                status = "UPCOMING",
                                lastTriggeredMillis = now,
                                updatedAtMillis = now
                            )
                            reminderDao.upsert(updated)
                            ReminderScheduler.scheduleReminder(context, updated)
                        } else {
                            reminderDao.upsert(
                                reminder.copy(
                                    lastTriggeredMillis = now,
                                    updatedAtMillis = now
                                )
                            )
                        }
                    }

                    ReminderScheduler.ACTION_SNOOZE_REMINDER -> {
                        val snoozeMins = intent.getIntExtra(ReminderScheduler.EXTRA_SNOOZE_MINUTES, 15)
                        val newTrigger = now + snoozeMins * 60_000L
                        val updated = reminder.copy(
                            triggerAtMillis = newTrigger,
                            status = "SNOOZED",
                            updatedAtMillis = now
                        )
                        reminderDao.upsert(updated)
                        ReminderScheduler.scheduleReminder(context, updated)
                        dismissNotification(context, reminderId)
                    }

                    ReminderScheduler.ACTION_COMPLETE_REMINDER -> {
                        val updated = reminder.copy(
                            status = "COMPLETED",
                            updatedAtMillis = now
                        )
                        reminderDao.upsert(updated)
                        ReminderScheduler.cancelReminder(context, reminderId)
                        dismissNotification(context, reminderId)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun dismissNotification(context: Context, reminderId: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.cancel(reminderId.hashCode())
    }

    private fun showReminderNotification(
        context: Context,
        reminder: com.example.data.local.ReminderEntity
    ) {
        NotificationChannels.ensureChannels(context)
        if (!ReminderScheduler.hasNotificationPermission(context)) return

        val channelId = if (reminder.soundEnabled) {
            NotificationChannels.CHANNEL_REMINDERS
        } else {
            NotificationChannels.CHANNEL_REMINDERS_SILENT
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            reminder.id.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            (reminder.id + "_snooze").hashCode(),
            Intent(context, ReminderReceiver::class.java).apply {
                action = ReminderScheduler.ACTION_SNOOZE_REMINDER
                putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminder.id)
                putExtra(ReminderScheduler.EXTRA_SNOOZE_MINUTES, 15)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeIntent = PendingIntent.getBroadcast(
            context,
            (reminder.id + "_complete").hashCode(),
            Intent(context, ReminderReceiver::class.java).apply {
                action = ReminderScheduler.ACTION_COMPLETE_REMINDER
                putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminder.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(reminder.title)
            .setContentText(
                reminder.notes.ifBlank {
                    "Priority: ${reminder.priority} • ${reminder.category.replace('_', ' ')}"
                }
            )
            .setPriority(
                if (reminder.priority == "HIGH") NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(0, "Snooze 15m", snoozeIntent)
            .addAction(0, "Mark Done", completeIntent)

        if (!reminder.vibrationEnabled) {
            builder.setVibrate(longArrayOf(0L))
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.notify(reminder.id.hashCode(), builder.build())
    }
}
