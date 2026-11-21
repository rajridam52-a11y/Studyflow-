package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.StudyFlowDatabase
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StudyFlowWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateWidgets(context, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun refreshAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, StudyFlowWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch {
                updateWidgets(context, manager, ids)
            }
        }

        private suspend fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val uid = try { Firebase.auth.currentUser?.uid } catch (_: Exception) { null }
            var pendingHw = 0
            var focusMinutesToday = 0
            var streak = 0
            var nextReminderLine = "Tap to open StudyFlow"

            if (!uid.isNullOrBlank()) {
                val db = StudyFlowDatabase.getInstance(context)
                val profile = db.userProfileDao().getProfileOnce(uid)
                streak = profile?.currentStreak ?: 0
                pendingHw = db.homeworkDao().getAllOnce(uid).count { !it.completed }

                val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                focusMinutesToday = db.studySessionDao().getAllOnce(uid)
                    .filter { it.completed && it.startedAtMillis >= todayStart }
                    .sumOf { it.durationMinutes }

                val nextReminder = db.reminderDao().getAllOnce(uid)
                    .filter { it.status == "UPCOMING" || it.status == "SNOOZED" }
                    .minByOrNull { it.triggerAtMillis }

                if (nextReminder != null) {
                    val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                    nextReminderLine = "Next: ${nextReminder.title} (${fmt.format(Date(nextReminder.triggerAtMillis))})"
                }
            }

            val launchIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                1001,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            for (widgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_studyflow).apply {
                    setTextViewText(R.id.widget_streak_badge, "${streak}d streak")
                    setTextViewText(
                        R.id.widget_summary_line,
                        "$pendingHw homework pending • ${focusMinutesToday}m focused today"
                    )
                    setTextViewText(R.id.widget_next_reminder, nextReminderLine)
                    setOnClickPendingIntent(R.id.widget_root, pendingIntent)
                }
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }
}
