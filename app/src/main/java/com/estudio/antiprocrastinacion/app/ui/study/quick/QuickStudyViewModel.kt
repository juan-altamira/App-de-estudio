package com.estudio.antiprocrastinacion.app.ui.study.quick

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estudio.antiprocrastinacion.app.domain.session.BackPressResult
import com.estudio.antiprocrastinacion.app.domain.session.SessionEngine
import com.estudio.antiprocrastinacion.app.domain.session.SessionTransition
import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.event.AbandonReason
import com.estudio.antiprocrastinacion.app.model.state.StudyPrompt
import com.estudio.antiprocrastinacion.app.model.state.UserAnswer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class QuickStudyUiState(
    val isLoading: Boolean = true,
    val prompt: StudyPrompt? = null,
    val revealAnswer: Boolean = false,
    val answerFeedback: AnswerFeedback? = null,
    val pendingAnswer: PendingAnswer? = null,
    val selectedResponseText: String? = null,
    val errorMessage: String? = null,
    val isTerminateSessionConfirmOpen: Boolean = false,
)

data class AnswerFeedback(
    val isCorrect: Boolean,
    val title: String,
    val detail: String? = null,
)

data class PendingAnswer(
    val responseText: String,
    val isCorrect: Boolean,
    val latencyMs: Long,
)

sealed interface QuickStudyEffect {
    data object ExitToHome : QuickStudyEffect
    data class ExitToModeMenu(val mode: SessionMode) : QuickStudyEffect
}

class QuickStudyViewModel(
    private val sessionEngine: SessionEngine,
) : ViewModel() {
    private val _uiState = MutableStateFlow(QuickStudyUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<QuickStudyEffect>()
    val effects = _effects.asSharedFlow()

    fun load(sessionId: String) {
        viewModelScope.launch {
            val prompt = sessionEngine.resumeSession(sessionId)
            if (prompt == null) {
                _effects.emit(QuickStudyEffect.ExitToHome)
                return@launch
            }
            val current = _uiState.value
            val sameInteraction =
                current.prompt?.session?.sessionId == prompt.session.sessionId &&
                    current.prompt?.item?.itemId == prompt.item.itemId &&
                    current.prompt?.session?.stepIndex == prompt.session.stepIndex
            _uiState.value =
                if (sameInteraction && current.answerFeedback != null) {
                    // Re-sync on resume without losing feedback for an answer not yet submitted.
                    current.copy(isLoading = false, prompt = prompt)
                } else {
                    QuickStudyUiState(
                        isLoading = false,
                        prompt = prompt,
                    )
                }
        }
    }

    fun revealAnswer() {
        _uiState.value = _uiState.value.copy(revealAnswer = true)
    }

    fun submitAnswer(responseText: String, isCorrect: Boolean, latencyMs: Long) {
        val prompt = _uiState.value.prompt ?: return
        if (_uiState.value.answerFeedback != null) return
        val feedback = buildAnswerFeedback(isCorrect = isCorrect, feedbackShort = prompt.item.feedbackShort)
        _uiState.value =
            _uiState.value.copy(
                answerFeedback = feedback,
                pendingAnswer = PendingAnswer(responseText, isCorrect, latencyMs),
                selectedResponseText = responseText,
                revealAnswer = prompt.item.options.isEmpty() && prompt.item.correctAnswer.isNotBlank(),
            )
    }

    fun submitSelfAssessment(responseText: String, isCorrect: Boolean, latencyMs: Long) {
        submitAnswer(responseText, isCorrect, latencyMs)
    }

    fun continueAfterFeedback() {
        val state = _uiState.value
        val prompt = state.prompt ?: return
        val pendingAnswer = state.pendingAnswer ?: return
        if (state.answerFeedback == null) return

        // Clear the pending answer before suspending so a second tap on "Seguir" cannot submit twice.
        _uiState.value = state.copy(pendingAnswer = null)

        viewModelScope.launch {
            val transition =
                try {
                    sessionEngine.submitAnswer(
                        prompt.session.sessionId,
                        UserAnswer(
                            responseText = pendingAnswer.responseText,
                            isCorrect = pendingAnswer.isCorrect,
                            latencyMs = pendingAnswer.latencyMs,
                        ),
                    )
                } catch (error: IllegalArgumentException) {
                    // The session can disappear underneath this screen (e.g. the social gate consumed
                    // it while the app was in background). Exit instead of crashing the process.
                    if (error.isStaleSessionError()) {
                        _effects.emit(QuickStudyEffect.ExitToHome)
                        return@launch
                    }
                    throw error
                }
            handleTransition(transition)
        }
    }

    fun retryCurrentPrompt() {
        _uiState.value =
            _uiState.value.copy(
                answerFeedback = null,
                pendingAnswer = null,
                selectedResponseText = null,
                revealAnswer = false,
            )
    }

    fun requestTerminateSession() {
        val prompt = _uiState.value.prompt ?: return
        if (prompt.session.mode == SessionMode.QUICK) return
        _uiState.value = _uiState.value.copy(isTerminateSessionConfirmOpen = true)
    }

    fun dismissTerminateSessionConfirmation() {
        _uiState.value = _uiState.value.copy(isTerminateSessionConfirmOpen = false)
    }

    fun confirmTerminateSession() {
        val prompt = _uiState.value.prompt ?: return
        if (prompt.session.mode == SessionMode.QUICK) return
        viewModelScope.launch {
            val terminated = sessionEngine.terminateSession(prompt.session.sessionId)
            _uiState.value = _uiState.value.copy(isTerminateSessionConfirmOpen = false)
            if (terminated) {
                _effects.emit(QuickStudyEffect.ExitToModeMenu(prompt.session.mode))
            }
        }
    }

    fun onBackPressed(sessionId: String) {
        if (_uiState.value.answerFeedback != null) {
            viewModelScope.launch {
                _effects.emit(QuickStudyEffect.ExitToHome)
            }
            return
        }
        viewModelScope.launch {
            val backResult =
                try {
                    sessionEngine.handleBackPressed(sessionId)
                } catch (error: IllegalArgumentException) {
                    if (error.isStaleSessionError()) {
                        BackPressResult.Exit(sessionId)
                    } else {
                        throw error
                    }
                }
            when (val result = backResult) {
                is BackPressResult.ShowMicroPrompt -> {
                    _uiState.value = QuickStudyUiState(isLoading = false, prompt = result.prompt)
                }
                is BackPressResult.Continue -> {
                    _uiState.value = QuickStudyUiState(isLoading = false, prompt = result.prompt)
                }
                is BackPressResult.Exit -> {
                    _effects.emit(QuickStudyEffect.ExitToHome)
                }
            }
        }
    }

    fun handleTimeout(sessionId: String) {
        viewModelScope.launch {
            sessionEngine.handleInactivityTimeout(sessionId)
            _effects.emit(QuickStudyEffect.ExitToHome)
        }
    }

    fun dismissAnswerFeedback() {
        _uiState.value =
            _uiState.value.copy(
                answerFeedback = null,
                pendingAnswer = null,
                selectedResponseText = null,
            )
    }

    private suspend fun handleTransition(
        transition: SessionTransition,
    ) {
        when (transition) {
            is SessionTransition.Advanced -> {
                _uiState.value = QuickStudyUiState(isLoading = false, prompt = transition.prompt)
            }
            is SessionTransition.Completed -> {
                _effects.emit(QuickStudyEffect.ExitToHome)
            }
        }
    }

    private fun buildAnswerFeedback(
        isCorrect: Boolean,
        feedbackShort: String,
    ): AnswerFeedback =
        AnswerFeedback(
            isCorrect = isCorrect,
            title = if (isCorrect) "Correcto" else "Incorrecto",
            detail = feedbackShort.ifBlank { null },
        )

    private fun IllegalArgumentException.isStaleSessionError(): Boolean =
        message?.contains("No active session", ignoreCase = true) == true
}
