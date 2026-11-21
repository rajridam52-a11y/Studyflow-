package com.example.viewmodel

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.reminders.NotificationChannels
import com.example.reminders.ReminderScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FocusTimerMode(val label: String, val defaultMinutes: Int) {
    POMODORO("Pomodoro", 25),
    CUSTOM("Custom Focus", 45),
    SHORT_BREAK("Short Break", 5),
    LONG_BREAK("Long Break", 15)
}

data class FocusTimerState(
    val mode: FocusTimerMode = FocusTimerMode.POMODORO,
    val selectedMinutes: Int = 25,
    val remainingSeconds: Int = 25 * 60,
    val running: Boolean = false,
    val selectedSubjectId: String = "",
    val selectedSubjectName: String = "General Study",
    val completedPomodorosInCycle: Int = 0,
    val sessionStartMillis: Long = 0L
)

class FocusTimerViewModel(
    private val appContext: Context,
    private val onSessionCompleted: (mode: FocusTimerMode, minutes: Int, subjectId: String, subjectName: String, startedAt: Long) -> Unit
) : ViewModel() {

    private val _state = MutableStateFlow(FocusTimerState())
    val state: StateFlow<FocusTimerState> = _state.asStateFlow()

    private var tickerJob: Job? = null

    fun selectMode(mode: FocusTimerMode) {
        if (_state.value.running) return
        _state.update {
            it.copy(
                mode = mode,
                selectedMinutes = mode.defaultMinutes,
                remainingSeconds = mode.defaultMinutes * 60
            )
        }
    }

    fun setCustomMinutes(minutes: Int) {
        if (_state.value.running) return
        val clamped = minutes.coerceIn(5, 180)
        _state.update {
            it.copy(
                mode = if (clamped == 25) FocusTimerMode.POMODORO else FocusTimerMode.CUSTOM,
                selectedMinutes = clamped,
                remainingSeconds = clamped * 60
            )
        }
    }

    fun selectSubject(subjectId: String, subjectName: String) {
        _state.update {
            it.copy(
                selectedSubjectId = subjectId,
                selectedSubjectName = subjectName.ifBlank { "General Study" }
            )
        }
    }

    fun startOrResume() {
        val current = _state.value
        if (current.running) return
        val startMs = if (current.sessionStartMillis == 0L) System.currentTimeMillis() else current.sessionStartMillis
        _state.update { it.copy(running = true, sessionStartMillis = startMs) }
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (_state.value.running && _state.value.remainingSeconds > 0) {
                delay(1000L)
                _state.update { st ->
                    if (!st.running) st
                    else st.copy(remainingSeconds = (st.remainingSeconds - 1).coerceAtLeast(0))
                }
            }
            if (_state.value.running && _state.value.remainingSeconds == 0) {
                finishCurrentSession(completedFully = true)
            }
        }
    }

    fun pause() {
        tickerJob?.cancel()
        _state.update { it.copy(running = false) }
    }

    fun stopAndReset() {
        tickerJob?.cancel()
        _state.update {
            it.copy(
                running = false,
                remainingSeconds = it.selectedMinutes * 60,
                sessionStartMillis = 0L
            )
        }
    }

    fun completeEarly() {
        tickerJob?.cancel()
        finishCurrentSession(completedFully = false)
    }

    private fun finishCurrentSession(completedFully: Boolean) {
        val st = _state.value
        val elapsedSeconds = (st.selectedMinutes * 60) - st.remainingSeconds
        val loggedMinutes = if (completedFully) st.selectedMinutes else (elapsedSeconds / 60).coerceAtLeast(1)
        val started = if (st.sessionStartMillis > 0L) st.sessionStartMillis else System.currentTimeMillis() - loggedMinutes * 60_000L

        if (st.mode == FocusTimerMode.POMODORO || st.mode == FocusTimerMode.CUSTOM) {
            onSessionCompleted(st.mode, loggedMinutes, st.selectedSubjectId, st.selectedSubjectName, started)
        }
        sendTimerNotification(st.mode)

        val nextCycleCount = if (st.mode == FocusTimerMode.POMODORO) st.completedPomodorosInCycle + 1 else st.completedPomodorosInCycle
        val nextMode = when {
            st.mode == FocusTimerMode.POMODORO && nextCycleCount % 4 == 0 -> FocusTimerMode.LONG_BREAK
            st.mode == FocusTimerMode.POMODORO -> FocusTimerMode.SHORT_BREAK
            st.mode == FocusTimerMode.SHORT_BREAK || st.mode == FocusTimerMode.LONG_BREAK -> FocusTimerMode.POMODORO
            else -> st.mode
        }

        _state.update {
            it.copy(
                running = false,
                mode = nextMode,
                selectedMinutes = nextMode.defaultMinutes,
                remainingSeconds = nextMode.defaultMinutes * 60,
                completedPomodorosInCycle = nextCycleCount,
                sessionStartMillis = 0L
            )
        }
    }

    private fun sendTimerNotification(finishedMode: FocusTimerMode) {
        NotificationChannels.ensureChannels(appContext)
        if (!ReminderScheduler.hasNotificationPermission(appContext)) return
        val title = when (finishedMode) {
            FocusTimerMode.POMODORO, FocusTimerMode.CUSTOM -> "Focus session complete!"
            FocusTimerMode.SHORT_BREAK, FocusTimerMode.LONG_BREAK -> "Break complete — ready to study?"
        }
        val body = when (finishedMode) {
            FocusTimerMode.POMODORO -> "Great work! Take a short break to recharge."
            FocusTimerMode.CUSTOM -> "Your study block has been logged to your progress tracker."
            else -> "Jump back into your next focus block when ready."
        }
        val notification = NotificationCompat.Builder(appContext, NotificationChannels.CHANNEL_TIMER)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        appContext.getSystemService(NotificationManager::class.java)
            ?.notify(8801, notification)
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
    }
}
