package com.example.data.remote

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class GeneratedFlashcardItem(
    val question: String,
    val answer: String
)

data class GeneratedQuizQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

data class GeneratedRevisionStep(
    val dayOffset: Int,
    val topic: String,
    val activity: String,
    val durationMinutes: Int
)

class AiStudyRepository {

    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "You are StudyFlow's supportive academic tutor. " +
                        "Always adapt explanations to the student's grade level. " +
                        "Use age-appropriate, encouraging, educational guidance that helps students understand concepts step by step rather than simply copying answers. " +
                        "Be honest about uncertainty and never invent citations or grades."
                )
            }
        )
    }

    private val flashcardsModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "cards" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "question" to Schema.string(description = "Clear active-recall question"),
                            "answer" to Schema.string(description = "Concise, accurate answer")
                        )
                    ),
                    description = "List of generated flashcards"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val quizModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "questions" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "question" to Schema.string(description = "Multiple-choice question prompt"),
                            "options" to Schema.array(
                                items = Schema.string(),
                                description = "Exactly 4 answer choices"
                            ),
                            "correctIndex" to Schema.integer(description = "0-based index of the correct option (0-3)"),
                            "explanation" to Schema.string(description = "Brief educational explanation of why the answer is right")
                        )
                    ),
                    description = "List of multiple-choice questions"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val revisionPlanModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "steps" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "dayOffset" to Schema.integer(description = "Days from today (0 to 14)"),
                            "topic" to Schema.string(description = "Topic or chapter to revise"),
                            "activity" to Schema.string(description = "Recommended study method e.g. active recall, practice problems"),
                            "durationMinutes" to Schema.integer(description = "Recommended session length in minutes")
                        )
                    ),
                    description = "Ordered revision schedule steps"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    fun streamTutorResponse(prompt: String, gradeLevel: String): Flow<String> = flow {
        val fullPrompt = "Student Grade Level: $gradeLevel\n\nRequest:\n$prompt"
        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            chatModel.generateContentStream(fullPrompt).collect { chunk ->
                val textChunk = chunk.text.orEmpty()
                if (textChunk.isNotEmpty()) {
                    accumulated.append(textChunk)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("AI tutor returned an empty response.")
        }
    }

    suspend fun generateFlashcards(
        topicOrNotes: String,
        gradeLevel: String,
        count: Int = 5
    ): Result<List<GeneratedFlashcardItem>> {
        return try {
            val prompt = "Create $count educational flashcards for a $gradeLevel student based on:\n$topicOrNotes"
            val response = withTimeout(60_000L) {
                flashcardsModel.generateContent(prompt)
            }
            val raw = response.text ?: throw IllegalStateException("Model returned empty response.")
            val json = JSONObject(raw)
            val arr = json.getJSONArray("cards")
            val cards = (0 until arr.length()).map { idx ->
                val obj = arr.getJSONObject(idx)
                GeneratedFlashcardItem(
                    question = obj.getString("question").trim(),
                    answer = obj.getString("answer").trim()
                )
            }.filter { it.question.isNotBlank() && it.answer.isNotBlank() }
            Result.success(cards)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Flashcard generation timed out", e)
            Result.failure(Exception("AI request timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Flashcard generation failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateQuiz(
        topic: String,
        gradeLevel: String,
        count: Int = 5
    ): Result<List<GeneratedQuizQuestion>> {
        return try {
            val prompt = "Create $count multiple-choice practice questions (4 options each) for a $gradeLevel student on:\n$topic"
            val response = withTimeout(60_000L) {
                quizModel.generateContent(prompt)
            }
            val raw = response.text ?: throw IllegalStateException("Model returned empty response.")
            val json = JSONObject(raw)
            val arr = json.getJSONArray("questions")
            val questions = (0 until arr.length()).map { idx ->
                val obj = arr.getJSONObject(idx)
                val optsArr = obj.getJSONArray("options")
                val opts = (0 until optsArr.length()).map { optsArr.getString(it).trim() }
                GeneratedQuizQuestion(
                    question = obj.getString("question").trim(),
                    options = opts,
                    correctIndex = obj.optInt("correctIndex", 0).coerceIn(0, (opts.size - 1).coerceAtLeast(0)),
                    explanation = obj.optString("explanation", "")
                )
            }.filter { it.question.isNotBlank() && it.options.size >= 2 }
            Result.success(questions)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Quiz generation timed out", e)
            Result.failure(Exception("Quiz generation timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Quiz generation failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateRevisionPlan(
        subjectAndTopics: String,
        daysUntilExam: Int,
        gradeLevel: String
    ): Result<List<GeneratedRevisionStep>> {
        return try {
            val safeDays = daysUntilExam.coerceIn(1, 14)
            val prompt = "Create a realistic $safeDays-day spaced revision plan for a $gradeLevel student covering:\n$subjectAndTopics"
            val response = withTimeout(60_000L) {
                revisionPlanModel.generateContent(prompt)
            }
            val raw = response.text ?: throw IllegalStateException("Model returned empty response.")
            val json = JSONObject(raw)
            val arr = json.getJSONArray("steps")
            val steps = (0 until arr.length()).map { idx ->
                val obj = arr.getJSONObject(idx)
                GeneratedRevisionStep(
                    dayOffset = obj.optInt("dayOffset", idx).coerceIn(0, 30),
                    topic = obj.getString("topic").trim(),
                    activity = obj.getString("activity").trim(),
                    durationMinutes = obj.optInt("durationMinutes", 30).coerceIn(10, 180)
                )
            }
            Result.success(steps)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Revision plan generation timed out", e)
            Result.failure(Exception("Revision plan generation timed out. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Revision plan generation failed", e)
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "AiStudyRepository"
    }
}
