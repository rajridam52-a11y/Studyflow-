package com.example.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppNotificationEntity
import com.example.data.local.PreferencesManager
import com.example.data.local.StudyFlowDatabase
import com.example.data.remote.CloudSyncRepository
import com.example.widget.StudyFlowWidgetProvider
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.first
import java.util.UUID

class MissedReminderScanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = StudyFlowDatabase.getInstance(applicationContext)
        val now = System.currentTimeMillis()
        val graceWindowMs = 20 * 60_000L

        val active = db.reminderDao().getAllActiveGlobalOnce()
        for (reminder in active) {
            if (reminder.triggerAtMillis + graceWindowMs < now && reminder.lastTriggeredMillis < reminder.triggerAtMillis) {
                db.reminderDao().upsert(
                    reminder.copy(status = "MISSED", updatedAtMillis = now)
                )
                db.appNotificationDao().upsert(
                    AppNotificationEntity(
                        id = UUID.randomUUID().toString(),
                        userId = reminder.userId,
                        title = "Missed: ${reminder.title}",
                        message = "Tap Reminders to smart-reschedule into an open study slot.",
                        category = "MISSED_REMINDER",
                        read = false,
                        timestampMillis = now,
                        updatedAtMillis = now
                    )
                )
            }
        }
        StudyFlowWidgetProvider.refreshAllWidgets(applicationContext)
        return Result.success()
    }
}

class CloudSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val user = Firebase.auth.currentUser ?: return Result.success()
        val prefsManager = PreferencesManager(applicationContext)
        val prefs = prefsManager.preferencesFlow.first()
        if (prefs.offlineMode) return Result.success()

        val db = StudyFlowDatabase.getInstance(applicationContext)
        val syncRepo = CloudSyncRepository(applicationContext, db)
        val syncResult = syncRepo.syncAll(user.uid)
        syncResult.onSuccess { msg ->
            prefsManager.updateSyncStatus(System.currentTimeMillis(), msg)
        }.onFailure { err ->
            prefsManager.updateSyncStatus(0L, "Offline / retry scheduled (${err.localizedMessage ?: "error"})")
        }
        return Result.success()
    }
}
