package com.estudio.antiprocrastinacion.app.domain.session

import com.estudio.antiprocrastinacion.app.model.event.AbandonReason
import com.estudio.antiprocrastinacion.app.model.state.StudyPrompt
import com.estudio.antiprocrastinacion.app.model.state.StudySession
import com.estudio.antiprocrastinacion.app.model.state.UserAnswer

interface SessionEngine {
    suspend fun startQuickSession(): StudyPrompt?
    suspend fun startNotificationSession(
        preferredUnitId: String? = null,
        preferredItemId: String? = null,
    ): StudyPrompt?
    suspend fun startSocialGateSession(goalCorrectCount: Int): StudyPrompt?
    suspend fun startDeepSession(unitId: String): StudyPrompt?
    suspend fun startDrainSession(unitId: String): StudyPrompt?
    suspend fun terminateSession(sessionId: String): Boolean
    suspend fun resumeSession(sessionId: String): StudyPrompt?
    suspend fun submitAnswer(sessionId: String, answer: UserAnswer): SessionTransition
    suspend fun handleBackPressed(sessionId: String): BackPressResult
    suspend fun recordAbandon(sessionId: String, reason: AbandonReason)
    suspend fun handleInactivityTimeout(sessionId: String): BackPressResult
}

sealed interface SessionTransition {
    data class Advanced(val prompt: StudyPrompt) : SessionTransition
    data class Completed(val session: StudySession) : SessionTransition
}

sealed interface BackPressResult {
    data class ShowMicroPrompt(val prompt: StudyPrompt) : BackPressResult
    data class Continue(val prompt: StudyPrompt) : BackPressResult
    data class Exit(val sessionId: String) : BackPressResult
}
