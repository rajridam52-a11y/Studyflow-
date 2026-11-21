package com.example.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.StudyFlowDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootAndTimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = StudyFlowDatabase.getInstance(context)
                    val activeReminders = db.reminderDao().getAllActiveGlobalOnce()
                    val now = System.currentTimeMillis()
                    for (reminder in activeReminders) {
                        if (reminder.triggerAtMillis > now - 15 * 60_000L) {
                            ReminderScheduler.scheduleReminder(context, reminder)
                        } else {
                            db.reminderDao().upsert(
                                reminder.copy(status = "MISSED", updatedAtMillis = now)
                            )
                        }
                    }
                    ReminderScheduler.schedulePeriodicBackgroundWorkers(context)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
