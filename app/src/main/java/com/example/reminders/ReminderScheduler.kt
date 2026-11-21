package com.example.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.local.ReminderEntity
import java.util.Calendar
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val TAG = "ReminderScheduler"
    const val ACTION_TRIGGER_REMINDER = "com.studyflow.student.ACTION_TRIGGER_REMINDER"
    const val ACTION_SNOOZE_REMINDER = "com.studyflow.student.ACTION_SNOOZE_REMINDER"
    const val ACTION_COMPLETE_REMINDER = "com.studyflow.student.ACTION_COMPLETE_REMINDER"
    const val EXTRA_REMINDER_ID = "extra_reminder_id"
    const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    fun scheduleReminder(context: Context, reminder: ReminderEntity) {
        NotificationChannels.ensureChannels(context)
        if (reminder.status != "UPCOMING" && reminder.status != "SNOOZED") {
            cancelReminder(context, reminder.id)
            return
        }

        val effectiveTrigger = (reminder.triggerAtMillis - reminder.advanceMinutes * 60_000L)
            .coerceAtLeast(System.currentTimeMillis() + 5_000L)

        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = buildTriggerPendingIntent(context, reminder.id)

        try {
            if (canScheduleExactAlarms(context)) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    effectiveTrigger,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    effectiveTrigger,
                    pendingIntent
                )
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "Exact alarm rejected, using inexact fallback", se)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                effectiveTrigger,
                pendingIntent
            )
        }
    }

    fun cancelReminder(context: Context, reminderId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = buildTriggerPendingIntent(context, reminderId)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun computeNextRepeatTrigger(reminder: ReminderEntity): Long? {
        val base = maxOf(reminder.triggerAtMillis, System.currentTimeMillis())
        val cal = Calendar.getInstance().apply { timeInMillis = reminder.triggerAtMillis }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)

        return when (reminder.repeatRule) {
            "DAILY" -> {
                val next = Calendar.getInstance().apply {
                    timeInMillis = base
                    add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                }
                next.timeInMillis
            }
            "WEEKLY" -> {
                val next = Calendar.getInstance().apply {
                    timeInMillis = base
                    add(Calendar.WEEK_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                }
                next.timeInMillis
            }
            "CUSTOM_DAYS" -> {
                val targetDays = reminder.customRepeatDays
                    .split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .filter { it in 1..7 }
                    .toSet()
                if (targetDays.isEmpty()) return null
                for (offset in 1..7) {
                    val candidate = Calendar.getInstance().apply {
                        timeInMillis = base
                        add(Calendar.DAY_OF_YEAR, offset)
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                    }
                    val javaDay = candidate.get(Calendar.DAY_OF_WEEK)
                    val isoDay = if (javaDay == Calendar.SUNDAY) 7 else javaDay - 1
                    if (isoDay in targetDays) return candidate.timeInMillis
                }
                null
            }
            else -> null
        }
    }

    fun schedulePeriodicBackgroundWorkers(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        val missedScanRequest = PeriodicWorkRequestBuilder<MissedReminderScanWorker>(
            15, TimeUnit.MINUTES
        ).build()
        workManager.enqueueUniquePeriodicWork(
            "studyflow_missed_reminder_scan",
            ExistingPeriodicWorkPolicy.KEEP,
            missedScanRequest
        )

        val syncRequest = PeriodicWorkRequestBuilder<CloudSyncWorker>(
            6, TimeUnit.HOURS
        ).build()
        workManager.enqueueUniquePeriodicWork(
            "studyflow_cloud_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    private fun buildTriggerPendingIntent(context: Context, reminderId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_TRIGGER_REMINDER
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
