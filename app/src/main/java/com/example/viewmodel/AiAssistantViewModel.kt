package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferencesManager
import com.example.data.remote.AiStudyRepository
import com.example.data.remote.GeneratedFlashcardItem
import com.example.data.remote.GeneratedQuizQuestion
import com.example.data.remote.GeneratedRevisionStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AiToolMode(val label: String) {
    TUTOR("Explain Concept"),
    SUMMARIZE("Summarize Notes"),
    FLASHCARDS("Generate Flashcards"),
    QUIZ("Practice Quiz"),
    REVISION_PLAN("Revision Plan")
}

sealed interface AiResponseState {
    data object Idle : AiResponseState
    data object Loading : AiResponseState
    data class StreamingText(val text: String) : AiResponseState
    data class TextSuccess(val text: String) : AiResponseState
    data class FlashcardsSuccess(val cards: List<GeneratedFlashcardItem>) : AiResponseState
    data class QuizSuccess(val questions: List<GeneratedQuizQuestion>) : AiResponseState
    data class RevisionPlanSuccess(val steps: List<GeneratedRevisionStep>) : AiResponseState
    data class Error(val message: String) : AiResponseState
}

data class AiAssistantUiState(
    val mode: AiToolMode = AiToolMode.TUTOR,
    val promptInput: String = "",
    val responseState: AiResponseState = AiResponseState.Idle,
    val requestsUsedToday: Int = 0,
    val dailyLimit: Int = 30
)

class AiAssistantViewModel(
    private val aiRepository: AiStudyRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiAssistantUiState())
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.preferencesFlow.collect { prefs ->
                val today = StudyDateFormatters.todayDayKey()
                val used = if (prefs.aiRequestDayKey == today) prefs.aiRequestsToday else 0
                _uiState.update { it.copy(requestsUsedToday = used) }
            }
        }
    }

    fun selectMode(mode: AiToolMode) {
        _uiState.update { it.copy(mode = mode, responseState = AiResponseState.Idle) }
    }

    fun updatePrompt(text: String) {
        _uiState.update { it.copy(promptInput = text) }
    }

    fun runAssistant(
        gradeLevel: String,
        customPromptOverride: String? = null
    ) {
        val rawInput = (customPromptOverride ?: _uiState.value.promptInput).trim()
        if (rawInput.isBlank()) {
            _uiState.update {
                it.copy(responseState = AiResponseState.Error("Please enter a topic, question, or study note first."))
            }
            return
        }

        viewModelScope.launch {
            val prefs = preferencesManager.preferencesFlow.first()
            val today = StudyDateFormatters.todayDayKey()
            val usedToday = if (prefs.aiRequestDayKey == today) prefs.aiRequestsToday else 0
            if (usedToday >= _uiState.value.dailyLimit) {
                _uiState.update {
                    it.copy(
                        responseState = AiResponseState.Error(
                            "Daily AI study limit (${it.dailyLimit} requests) reached. Try reviewing your saved flashcards or notes!"
                        )
                    )
                }
                return@launch
            }

            preferencesManager.incrementAiUsage(today)
            val currentMode = _uiState.value.mode
            _uiState.update { it.copy(responseState = AiResponseState.Loading) }

            when (currentMode) {
                AiToolMode.TUTOR -> {
                    val prompt = "Explain this concept step by step so the student truly understands how to solve similar problems independently (do not just give a bare final answer):\n$rawInput"
                    streamText(prompt, gradeLevel)
                }

                AiToolMode.SUMMARIZE -> {
                    val prompt = "Summarize these study notes into crisp key concepts, definitions, and 3 quick self-check takeaways:\n$rawInput"
                    streamText(prompt, gradeLevel)
                }

                AiToolMode.FLASHCARDS -> {
                    aiRepository.generateFlashcards(rawInput, gradeLevel, count = 5)
                        .onSuccess { cards ->
                            if (cards.isEmpty()) {
                                _uiState.update { it.copy(responseState = AiResponseState.Error("No flashcards were generated. Try adding more detail.")) }
                            } else {
                                _uiState.update { it.copy(responseState = AiResponseState.FlashcardsSuccess(cards)) }
                            }
                        }
                        .onFailure { err ->
                            _uiState.update { it.copy(responseState = AiResponseState.Error(err.localizedMessage ?: "Failed to generate flashcards.")) }
                        }
                }

                AiToolMode.QUIZ -> {
                    aiRepository.generateQuiz(rawInput, gradeLevel, count = 5)
                        .onSuccess { questions ->
                            if (questions.isEmpty()) {
                                _uiState.update { it.copy(responseState = AiResponseState.Error("No quiz questions were generated. Try another topic.")) }
                            } else {
                                _uiState.update { it.copy(responseState = AiResponseState.QuizSuccess(questions)) }
                            }
                        }
                        .onFailure { err ->
                            _uiState.update { it.copy(responseState = AiResponseState.Error(err.localizedMessage ?: "Failed to generate quiz.")) }
                        }
                }

                AiToolMode.REVISION_PLAN -> {
                    aiRepository.generateRevisionPlan(rawInput, daysUntilExam = 7, gradeLevel = gradeLevel)
                        .onSuccess { steps ->
                            if (steps.isEmpty()) {
                                _uiState.update { it.copy(responseState = AiResponseState.Error("No revision steps were generated.")) }
                            } else {
                                _uiState.update { it.copy(responseState = AiResponseState.RevisionPlanSuccess(steps)) }
                            }
                        }
                        .onFailure { err ->
                            _uiState.update { it.copy(responseState = AiResponseState.Error(err.localizedMessage ?: "Failed to generate revision plan.")) }
                        }
                }
            }
        }
    }

    fun explainQuizMistake(question: String, chosenOption: String, correctOption: String, gradeLevel: String) {
        _uiState.update { it.copy(mode = AiToolMode.TUTOR) }
        val prompt = "I answered \"$chosenOption\" for the question: \"$question\", but the right answer is \"$correctOption\". Explain why in simple terms and how to remember this concept."
        viewModelScope.launch {
            _uiState.update { it.copy(responseState = AiResponseState.Loading) }
            streamText(prompt, gradeLevel)
        }
    }

    private suspend fun streamText(prompt: String, gradeLevel: String) {
        var latest = ""
        aiRepository.streamTutorResponse(prompt, gradeLevel)
            .catch { error ->
                if (error is CancellationException) throw error
                Log.e(TAG, "AI streaming failed", error)
                _uiState.update {
                    it.copy(responseState = AiResponseState.Error(error.localizedMessage ?: "AI request failed."))
                }
            }
            .collect { partial ->
                latest = partial
                _uiState.update { it.copy(responseState = AiResponseState.StreamingText(partial)) }
            }
        if (latest.isNotBlank() && _uiState.value.responseState is AiResponseState.StreamingText) {
            _uiState.update { it.copy(responseState = AiResponseState.TextSuccess(latest)) }
        }
    }

    private companion object {
        const val TAG = "AiAssistantVM"
    }
}
