package com.estudio.antiprocrastinacion.app.socialgate

import android.util.Log
import com.estudio.antiprocrastinacion.app.domain.repository.SessionRepository
import com.estudio.antiprocrastinacion.app.domain.repository.SocialGateRepository
import com.estudio.antiprocrastinacion.app.domain.session.SessionEngine
import com.estudio.antiprocrastinacion.app.domain.session.SessionTransition
import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.content.Surface
import com.estudio.antiprocrastinacion.app.model.state.SocialGateRule
import com.estudio.antiprocrastinacion.app.model.state.SocialGateRuntimePhase
import com.estudio.antiprocrastinacion.app.model.state.StudyPrompt
import com.estudio.antiprocrastinacion.app.model.state.UserAnswer
import com.estudio.antiprocrastinacion.app.ui.common.TimeProvider
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class SocialGateAnswerFeedback(
    val isCorrect: Boolean,
    val title: String,
    val detail: String? = null,
)

/**
 * Estado del comodín de escape para mostrar en el overlay del gate.
 *
 * @param canUse si todavía quedan usos en la ventana de 7 días.
 * @param remaining usos restantes antes de tocar el comodín.
 * @param limitPerWeek límite configurado (hoy 1; ver [SocialGateEscape]).
 * @param nextAvailableLabel etiqueta de día ("el martes 20 de junio") con el
 *   próximo uso disponible. Se completa cuando usar ahora agota la cuota, o
 *   cuando ya está bloqueado. Null si todavía quedan usos sueltos.
 */
data class SocialGateEscapeInfo(
    val canUse: Boolean,
    val remaining: Int,
    val limitPerWeek: Int,
    val nextAvailableLabel: String?,
)

data class SocialGatePromptState(
    val targetPackageName: String,
    val targetAppDisplayName: String,
    val prompt: StudyPrompt,
    val gateResolvedCount: Int,
    val gateRequiredCorrectAnswers: Int,
    val revealAnswer: Boolean = false,
    val answerFeedback: SocialGateAnswerFeedback? = null,
    val pendingPrompt: StudyPrompt? = null,
    val isPendingCompletion: Boolean = false,
    val selectedResponseText: String? = null,
    val escape: SocialGateEscapeInfo =
        SocialGateEscapeInfo(
            canUse = true,
            remaining = SocialGateEscape.MAX_USES_PER_WEEK,
            limitPerWeek = SocialGateEscape.MAX_USES_PER_WEEK,
            nextAvailableLabel = null,
        ),
)

data class SocialGateBlockingState(
    val targetPackageName: String,
    val targetAppDisplayName: String,
    val activeSessionTitle: String,
    val activeSessionModeLabel: String,
)

sealed interface SocialGateCoordinatorResult {
    data object Allowed : SocialGateCoordinatorResult
    data object HideOverlay : SocialGateCoordinatorResult
    data class ShowPrompt(val state: SocialGatePromptState) : SocialGateCoordinatorResult
    data class OpenStudyAppAndHideOverlay(val sessionId: String?) : SocialGateCoordinatorResult
}

class SocialGateCoordinator(
    private val socialGateRepository: SocialGateRepository,
    private val sessionRepository: SessionRepository,
    private val sessionEngine: SessionEngine,
    private val timeProvider: TimeProvider,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val logger: (String) -> Unit = { Log.d(TAG, it) },
) {
    private var currentPromptState: SocialGatePromptState? = null

    suspend fun onTargetForegroundStable(rule: SocialGateRule): SocialGateCoordinatorResult {
        val now = timeProvider.now()
        var runtime = socialGateRepository.getRuntimeState()
        val activeSession = sessionRepository.getMostRecentActiveSession()
        // Solo una sesión QUICK (Tarjetas pendientes o un gate heredado) puede usarse como gate. Las
        // sesiones DEEP/DRAIN quedan intactas; si el gate está due, se intenta abrir Tarjetas pendientes
        // en su propio slot QUICK. Si no hay deuda real pendiente, no se bloquea.
        val reusableQuickSession = sessionRepository.getActiveSession(SessionMode.QUICK)

        // 1. If a gate is already active for this package, restore it first.
        if (runtime.phase == SocialGateRuntimePhase.ACTIVE_GATE && runtime.targetPackageName == rule.packageName) {
            val activeGateSessionId = runtime.activeGateSessionId
            val activeGateSession =
                if (activeGateSessionId != null) {
                    sessionRepository.getActiveSessionById(activeGateSessionId)
                } else {
                    null
                }
            if (activeGateSessionId == null || activeGateSession == null) {
                clearStaleGateState(
                    now = now,
                    lastForegroundPackageName = rule.packageName,
                )
                runtime = socialGateRepository.getRuntimeState()
                log("cleared stale active gate target=${rule.packageName} activeSession=${activeGateSession?.sessionId} runtimeSession=$activeGateSessionId")
            } else {
                val restoredPrompt = restoreActiveGatePrompt(rule, activeGateSession, runtime, now)
                if (restoredPrompt != null) {
                    log("restore active gate session=${restoredPrompt.prompt.session.sessionId} target=${rule.packageName}")
                    return SocialGateCoordinatorResult.ShowPrompt(restoredPrompt)
                }
            }
        }

        if (hasValidUnlockForCurrentForeground(runtime, rule.packageName)) {
            socialGateRepository.updateRuntimeState {
                it.copy(
                    lastForegroundPackageName = rule.packageName,
                    lastForegroundChangedAt = now,
                )
            }
            log("allow unlocked target=${rule.packageName}")
            return SocialGateCoordinatorResult.Allowed
        }

        if (shouldInvalidateUnlockToken(runtime, rule.packageName)) {
            socialGateRepository.updateRuntimeState { current ->
                if (current.unlockTokenPackageName != rule.packageName) return@updateRuntimeState current
                current.copy(
                    phase = SocialGateRuntimePhase.IDLE,
                    targetPackageName = null,
                    activeGateSessionId = null,
                    gateUnlockBaselineCorrectCount = null,
                    gateUnlockRequiredCorrectAnswers = null,
                    unlockTokenPackageName = null,
                    unlockTokenIssuedAt = null,
                    lastForegroundPackageName = current.lastForegroundPackageName,
                    lastForegroundChangedAt = current.lastForegroundChangedAt,
                )
            }
            runtime = socialGateRepository.getRuntimeState()
            log("invalidated stale unlock token target=${rule.packageName}")
        }

        // 2. If a gate is due now, never replace an active session silently. The gate always runs the
        //    same QUICK/Tarjetas pendientes session: existing QUICK is reused, otherwise QUICK is opened.
        //    No separate SOCIAL_GATE packet is created, so there are no gate-only filler questions.
        if (isGateDueNow(rule, now)) {
            if (reusableQuickSession != null) {
                val reusedPrompt = armGateOverExistingSession(rule, reusableQuickSession, now)
                if (reusedPrompt != null) {
                    log("reuse QUICK session=${reusedPrompt.prompt.session.sessionId} as gate target=${rule.packageName}")
                    return SocialGateCoordinatorResult.ShowPrompt(reusedPrompt)
                }

                log("gate due but QUICK session could not be resumed, redirecting existing session=${reusableQuickSession.sessionId}")
                socialGateRepository.updateRuntimeState {
                    it.copy(
                        phase = SocialGateRuntimePhase.BLOCKING_EXISTING_SESSION,
                        targetPackageName = rule.packageName,
                        activeGateSessionId = reusableQuickSession.sessionId,
                        gateUnlockBaselineCorrectCount = null,
                        gateUnlockRequiredCorrectAnswers = null,
                        unlockTokenPackageName = null,
                        unlockTokenIssuedAt = null,
                        lastForegroundPackageName = rule.packageName,
                        lastForegroundChangedAt = now,
                    )
                }
                return SocialGateCoordinatorResult.OpenStudyAppAndHideOverlay(reusableQuickSession.sessionId)
            }

            val prompt = sessionEngine.startQuickSession()
            if (prompt != null) {
                val requiredCorrectAnswers = effectiveRequiredCorrectAnswers(rule, prompt.session.correctCount, prompt.session.goalCorrectCount)
                persistActiveGateRuntime(
                    packageName = rule.packageName,
                    sessionId = prompt.session.sessionId,
                    baselineCorrectCount = prompt.session.correctCount,
                    requiredCorrectAnswers = requiredCorrectAnswers,
                    now = now,
                )
                val promptState =
                    prompt.asSocialGatePromptState(
                        targetPackageName = rule.packageName,
                        targetAppDisplayName = rule.displayName,
                        gateResolvedCount = 0,
                        gateRequiredCorrectAnswers = requiredCorrectAnswers,
                    ).withEscape(now)
                currentPromptState = promptState
                log("activate pending-backed gate session=${prompt.session.sessionId} target=${rule.packageName}")
                return SocialGateCoordinatorResult.ShowPrompt(promptState)
            }

            socialGateRepository.updateRuntimeState {
                it.copy(
                    phase = SocialGateRuntimePhase.IDLE,
                    targetPackageName = null,
                    activeGateSessionId = null,
                    gateUnlockBaselineCorrectCount = null,
                    gateUnlockRequiredCorrectAnswers = null,
                    unlockTokenPackageName = null,
                    unlockTokenIssuedAt = null,
                    lastForegroundPackageName = rule.packageName,
                    lastForegroundChangedAt = now,
                )
            }
            log("allow target=${rule.packageName}: no real pending QUICK debt")
            return SocialGateCoordinatorResult.Allowed
        }

        // 3. If no gate is due, check if we should block because of an existing study session
        if (activeSession != null) {
            log("block existing session=${activeSession.sessionId} target=${rule.packageName} -> REDIRECTING")
            socialGateRepository.updateRuntimeState {
                it.copy(
                    phase = SocialGateRuntimePhase.BLOCKING_EXISTING_SESSION,
                    targetPackageName = rule.packageName,
                    activeGateSessionId = activeSession.sessionId,
                    gateUnlockBaselineCorrectCount = null,
                    gateUnlockRequiredCorrectAnswers = null,
                    unlockTokenPackageName = null,
                    unlockTokenIssuedAt = null,
                    lastForegroundPackageName = rule.packageName,
                    lastForegroundChangedAt = now,
                )
            }
            return SocialGateCoordinatorResult.OpenStudyAppAndHideOverlay(activeSession.sessionId)
        }

        // 4. Otherwise, allow
        socialGateRepository.updateRuntimeState {
            it.copy(
                phase = SocialGateRuntimePhase.IDLE,
                targetPackageName = null,
                activeGateSessionId = null,
                gateUnlockBaselineCorrectCount = null,
                gateUnlockRequiredCorrectAnswers = null,
                unlockTokenPackageName = null,
                unlockTokenIssuedAt = null,
                lastForegroundPackageName = rule.packageName,
                lastForegroundChangedAt = now,
            )
        }
        log("allow target=${rule.packageName}")
        return SocialGateCoordinatorResult.Allowed
    }

    suspend fun onNonTargetForegroundStable(packageName: String?): SocialGateCoordinatorResult {
        val now = timeProvider.now()
        socialGateRepository.updateRuntimeState { current ->
            val nextPhase =
                if (current.phase == SocialGateRuntimePhase.BLOCKING_EXISTING_SESSION) {
                    SocialGateRuntimePhase.IDLE
                } else {
                    current.phase
                }
            current.copy(
                phase = nextPhase,
                targetPackageName = if (nextPhase == SocialGateRuntimePhase.IDLE) null else current.targetPackageName,
                activeGateSessionId = if (nextPhase == SocialGateRuntimePhase.IDLE) null else current.activeGateSessionId,
                gateUnlockBaselineCorrectCount = if (nextPhase == SocialGateRuntimePhase.IDLE) null else current.gateUnlockBaselineCorrectCount,
                gateUnlockRequiredCorrectAnswers = if (nextPhase == SocialGateRuntimePhase.IDLE) null else current.gateUnlockRequiredCorrectAnswers,
                lastForegroundPackageName = packageName,
                lastForegroundChangedAt = now,
            )
        }
        log("hide overlay foreground=$packageName")
        return SocialGateCoordinatorResult.HideOverlay
    }

    suspend fun expireUnlockToken(packageName: String) {
        socialGateRepository.updateRuntimeState { current ->
            if (current.unlockTokenPackageName != packageName) return@updateRuntimeState current
            current.copy(
                phase = SocialGateRuntimePhase.IDLE,
                targetPackageName = null,
                activeGateSessionId = null,
                gateUnlockBaselineCorrectCount = null,
                gateUnlockRequiredCorrectAnswers = null,
                unlockTokenPackageName = null,
                unlockTokenIssuedAt = null,
            )
        }
        log("unlock token expired target=$packageName")
    }

    fun revealAnswer(): SocialGateCoordinatorResult {
        val current = currentPromptState ?: return SocialGateCoordinatorResult.HideOverlay
        val updated = current.copy(revealAnswer = true)
        currentPromptState = updated
        return SocialGateCoordinatorResult.ShowPrompt(updated)
    }

    suspend fun submitAnswer(
        responseText: String,
        isCorrect: Boolean,
        latencyMs: Long,
    ): SocialGateCoordinatorResult {
        val current = currentPromptState ?: return SocialGateCoordinatorResult.HideOverlay
        val activeSession = sessionRepository.getActiveSessionById(current.prompt.session.sessionId)
        if (activeSession?.sessionId != current.prompt.session.sessionId) {
            clearStaleGateState(
                now = timeProvider.now(),
                lastForegroundPackageName = current.targetPackageName,
            )
            log("hide overlay because gate session is stale expected=${current.prompt.session.sessionId} active=${activeSession?.sessionId}")
            return SocialGateCoordinatorResult.HideOverlay
        }
        val transition =
            try {
                sessionEngine.submitAnswer(
                    current.prompt.session.sessionId,
                    UserAnswer(
                        responseText = responseText,
                        isCorrect = isCorrect,
                        latencyMs = latencyMs,
                    ),
                )
            } catch (error: IllegalArgumentException) {
                if (error.message?.contains("No active session", ignoreCase = true) == true) {
                    clearStaleGateState(
                        now = timeProvider.now(),
                        lastForegroundPackageName = current.targetPackageName,
                    )
                    log("hide overlay after stale session exception session=${current.prompt.session.sessionId}")
                    return SocialGateCoordinatorResult.HideOverlay
                }
                throw error
            }
        val feedback = buildFeedback(isCorrect, current.prompt.item.feedbackShort)
        val runtime = socialGateRepository.getRuntimeState()

        return when (transition) {
            is SessionTransition.Advanced -> {
                // Modelo actual: el gate corre la sesión de Tarjetas pendientes COMPLETA. Mientras haya
                // próxima tarjeta (Advanced) el gate NO se desbloquea, haya sido correcta o no. El
                // desbloqueo ocurre solo cuando la sesión se completa (rama Completed), es decir cuando
                // se terminaron TODAS las pendientes (incluidas las corregidas). Igual que Tarjetas
                // pendientes: mostramos feedback (Correcto/Incorrecto + comentario) y al tocar "Seguir"
                // se avanza. Ver docs/social-gate.md.
                val updated =
                    current.copy(
                        gateResolvedCount = gateResolvedCount(transition.prompt.session, runtime).coerceAtMost(current.gateRequiredCorrectAnswers),
                        revealAnswer = true,
                        answerFeedback = feedback,
                        pendingPrompt = transition.prompt,
                        isPendingCompletion = false,
                        selectedResponseText = responseText.ifBlank { null },
                    )
                currentPromptState = updated
                log("answer registered (isCorrect=$isCorrect), showing feedback; gate continues until session completes")
                SocialGateCoordinatorResult.ShowPrompt(updated)
            }

            is SessionTransition.Completed -> {
                // Se terminaron las pendientes → el gate se desbloquea. Mostramos feedback
                // (correcto/incorrecto + comentario) y el desbloqueo ocurre al tocar "Desbloquear"
                // (continueAfterFeedback → isPendingCompletion).
                val updated =
                    current.copy(
                        gateResolvedCount = gateResolvedCount(transition.session, runtime).coerceAtMost(current.gateRequiredCorrectAnswers),
                        revealAnswer = true,
                        answerFeedback = feedback,
                        pendingPrompt = null,
                        isPendingCompletion = true,
                        selectedResponseText = responseText.ifBlank { null },
                    )
                currentPromptState = updated
                log("gate answer on last question, showing feedback")
                SocialGateCoordinatorResult.ShowPrompt(updated)
            }
        }
    }

    private suspend fun completeGateAndRedirect(
        packageName: String,
        continuationSessionId: String?,
    ): SocialGateCoordinatorResult {
        val now = timeProvider.now()
        socialGateRepository.incrementSolvedCount(
            packageName = packageName,
            localDate = now.toLocalDateKey(),
            solvedAt = now,
        )
        socialGateRepository.updateRuntimeState {
            it.copy(
                phase = SocialGateRuntimePhase.UNLOCKED_FOR_CURRENT_FOREGROUND,
                targetPackageName = null,
                activeGateSessionId = null,
                gateUnlockBaselineCorrectCount = null,
                gateUnlockRequiredCorrectAnswers = null,
                unlockTokenPackageName = packageName,
                unlockTokenIssuedAt = now,
                lastForegroundPackageName = packageName,
                lastForegroundChangedAt = now,
            )
        }
        currentPromptState = null

        // After unlocking, drop the user straight into the next real pending card if one still exists.
        // In the current contract the gate runs QUICK until completion, so this is usually null.
        val redirectSessionId =
            continuationSessionId
                ?: runCatching { sessionEngine.startQuickSession()?.session?.sessionId }.getOrNull()
        if (redirectSessionId != null) {
            log("gate completed → opening study at session=$redirectSessionId")
        } else {
            log("gate completed → nothing pending to continue")
        }
        return SocialGateCoordinatorResult.OpenStudyAppAndHideOverlay(redirectSessionId)
    }

    suspend fun continueAfterFeedback(): SocialGateCoordinatorResult {
        val current = currentPromptState ?: return SocialGateCoordinatorResult.HideOverlay
        current.pendingPrompt?.let { nextPrompt ->
            val updated =
                current.copy(
                    prompt = nextPrompt,
                    gateResolvedCount = current.gateResolvedCount,
                    revealAnswer = false,
                    answerFeedback = null,
                    pendingPrompt = null,
                    isPendingCompletion = false,
                    selectedResponseText = null,
                )
            currentPromptState = updated
            return SocialGateCoordinatorResult.ShowPrompt(updated)
        }

        if (current.isPendingCompletion) {
            return completeGateAndRedirect(
                packageName = current.targetPackageName,
                continuationSessionId = null,
            )
        }

        return SocialGateCoordinatorResult.ShowPrompt(current)
    }

    /**
     * Comodín de escape: desbloquea la app social actual sin completar el repaso.
     * Limitado por ventana móvil de 7 días ([SocialGateEscape.MAX_USES_PER_WEEK]).
     * NO completa la sesión de estudio ni otorga crédito de SR: solo emite un
     * token de desbloqueo para el foreground actual (al salir de la app expira).
     */
    suspend fun useEscape(): SocialGateCoordinatorResult {
        val current = currentPromptState ?: return SocialGateCoordinatorResult.HideOverlay
        val now = timeProvider.now()
        val runtime = socialGateRepository.getRuntimeState()
        val info = escapeInfo(runtime, now)
        if (!info.canUse) {
            // Defensa: la UI no debería permitir llegar acá, pero refrescamos el
            // estado para que el diálogo muestre el bloqueo correcto.
            val updated = current.copy(escape = info)
            currentPromptState = updated
            log("escape rejected (no uses left) target=${current.targetPackageName}")
            return SocialGateCoordinatorResult.ShowPrompt(updated)
        }

        val packageName = current.targetPackageName
        socialGateRepository.updateRuntimeState {
            it.copy(
                phase = SocialGateRuntimePhase.UNLOCKED_FOR_CURRENT_FOREGROUND,
                targetPackageName = null,
                activeGateSessionId = null,
                gateUnlockBaselineCorrectCount = null,
                gateUnlockRequiredCorrectAnswers = null,
                unlockTokenPackageName = packageName,
                unlockTokenIssuedAt = now,
                lastForegroundPackageName = packageName,
                lastForegroundChangedAt = now,
                escapeUsesAt = pruneEscapeUses(it.escapeUsesAt, now) + now,
            )
        }
        currentPromptState = null
        log("escape used target=$packageName remainingBefore=${info.remaining}")
        // Ocultamos el overlay: la app social queda en foreground y desbloqueada.
        return SocialGateCoordinatorResult.HideOverlay
    }

    fun clearInMemoryPromptState() {
        currentPromptState = null
    }

    /**
     * Prompt del gate activo sin resolver (en memoria), o null si no hay gate arriba.
     * Lo usa el servicio para mantener el gate "inescapable" encima de cualquier app hasta
     * que se resuelve (se estudia) o se usa el comodín de escape.
     */
    fun activeGatePrompt(): SocialGatePromptState? = currentPromptState

    private suspend fun SocialGatePromptState.withEscape(now: Long): SocialGatePromptState =
        copy(escape = escapeInfo(socialGateRepository.getRuntimeState(), now))

    /**
     * Conserva solo los usos que cuentan contra la cuota: los ocurridos dentro de los
     * últimos 7 días.
     *
     * Anti-abuso de reloj: NO descartamos timestamps "en el futuro" respecto de [now]
     * (es decir, no exigimos `now - it >= 0`). Si el usuario atrasa el reloj del teléfono
     * después de usar el comodín, el uso registrado quedaría en el futuro; descartarlo
     * reabriría el comodín con solo retroceder la hora. Manteniéndolo (`it > now - WINDOW`),
     * atrasar el reloj ya no libera el escape. Un uso con fecha futura (reloj manipulado)
     * expira solo cuando el tiempo real lo supera por más de 7 días, que es la dirección
     * segura para una app anti-procrastinación.
     */
    private fun pruneEscapeUses(
        uses: List<Long>,
        now: Long,
    ): List<Long> = uses.filter { it > now - SocialGateEscape.WINDOW_MS }.sorted()

    private fun escapeInfo(
        runtime: com.estudio.antiprocrastinacion.app.model.state.SocialGateRuntimeState,
        now: Long,
    ): SocialGateEscapeInfo {
        val limit = SocialGateEscape.MAX_USES_PER_WEEK
        val inWindow = pruneEscapeUses(runtime.escapeUsesAt, now)
        val used = inWindow.size
        val remaining = (limit - used).coerceAtLeast(0)
        val canUse = remaining > 0
        val nextAvailableLabel =
            when {
                !canUse -> inWindow.firstOrNull()?.let { formatEscapeDay(it + SocialGateEscape.WINDOW_MS) }
                remaining == 1 -> {
                    // Usar ahora agota la cuota: el próximo uso libre es cuando el
                    // más viejo de la ventana (incluyendo este uso) cumpla 7 días.
                    val newOldest = (inWindow + now).minOrNull() ?: now
                    formatEscapeDay(newOldest + SocialGateEscape.WINDOW_MS)
                }
                else -> null
            }
        return SocialGateEscapeInfo(
            canUse = canUse,
            remaining = remaining,
            limitPerWeek = limit,
            nextAvailableLabel = nextAvailableLabel,
        )
    }

    private fun formatEscapeDay(epochMs: Long): String {
        val date = Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate()
        val formatted = date.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es")))
        return formatted.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es")) else it.toString() }
    }

    private suspend fun isGateDueNow(
        rule: SocialGateRule,
        now: Long,
    ): Boolean {
        val today = now.toLocalDateKey()
        val dailyState =
            socialGateRepository.getDailyStates()
                .firstOrNull { it.packageName == rule.packageName && it.localDate == today }
        return SocialGateSchedule.isGateDueNow(rule, dailyState, now, zoneId)
    }

    private fun buildFeedback(
        isCorrect: Boolean,
        feedbackShort: String,
    ): SocialGateAnswerFeedback =
        SocialGateAnswerFeedback(
            isCorrect = isCorrect,
            title = if (isCorrect) "Correcto" else "Incorrecto",
            detail = feedbackShort.ifBlank { null },
        )

    private fun Long.toLocalDateKey(): String =
        Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate().toString()

    private suspend fun restoreActiveGatePrompt(
        rule: SocialGateRule,
        activeSession: com.estudio.antiprocrastinacion.app.model.state.StudySession,
        runtime: com.estudio.antiprocrastinacion.app.model.state.SocialGateRuntimeState,
        now: Long,
    ): SocialGatePromptState? {
        val prompt = sessionEngine.resumeSession(activeSession.sessionId) ?: return null
        val baseline = runtime.gateUnlockBaselineCorrectCount ?: prompt.session.correctCount
        val requiredCorrectAnswers = runtime.gateUnlockRequiredCorrectAnswers ?: effectiveRequiredCorrectAnswers(rule, prompt.session.correctCount, prompt.session.goalCorrectCount)
        persistActiveGateRuntime(
            packageName = rule.packageName,
            sessionId = prompt.session.sessionId,
            baselineCorrectCount = baseline,
            requiredCorrectAnswers = requiredCorrectAnswers,
            now = now,
        )
        val promptState =
            prompt.asSocialGatePromptState(
                targetPackageName = rule.packageName,
                targetAppDisplayName = rule.displayName,
                gateResolvedCount = gateResolvedCount(prompt.session, runtime).coerceAtMost(requiredCorrectAnswers),
                gateRequiredCorrectAnswers = requiredCorrectAnswers,
            ).withEscape(now)
        currentPromptState = promptState
        return promptState
    }

    private suspend fun armGateOverExistingSession(
        rule: SocialGateRule,
        activeSession: com.estudio.antiprocrastinacion.app.model.state.StudySession,
        now: Long,
    ): SocialGatePromptState? {
        val prompt = sessionEngine.resumeSession(activeSession.sessionId) ?: return null
        val requiredCorrectAnswers = effectiveRequiredCorrectAnswers(rule, prompt.session.correctCount, prompt.session.goalCorrectCount)
        persistActiveGateRuntime(
            packageName = rule.packageName,
            sessionId = prompt.session.sessionId,
            baselineCorrectCount = prompt.session.correctCount,
            requiredCorrectAnswers = requiredCorrectAnswers,
            now = now,
        )
        val promptState =
            prompt.asSocialGatePromptState(
                targetPackageName = rule.packageName,
                targetAppDisplayName = rule.displayName,
                gateResolvedCount = 0,
                gateRequiredCorrectAnswers = requiredCorrectAnswers,
            ).withEscape(now)
        currentPromptState = promptState
        return promptState
    }

    private suspend fun persistActiveGateRuntime(
        packageName: String,
        sessionId: String,
        baselineCorrectCount: Int,
        requiredCorrectAnswers: Int,
        now: Long,
    ) {
        socialGateRepository.updateRuntimeState {
            it.copy(
                phase = SocialGateRuntimePhase.ACTIVE_GATE,
                targetPackageName = packageName,
                activeGateSessionId = sessionId,
                gateUnlockBaselineCorrectCount = baselineCorrectCount,
                gateUnlockRequiredCorrectAnswers = requiredCorrectAnswers,
                unlockTokenPackageName = null,
                unlockTokenIssuedAt = null,
                lastForegroundPackageName = packageName,
                lastForegroundChangedAt = now,
            )
        }
    }

    private suspend fun clearStaleGateState(
        now: Long,
        lastForegroundPackageName: String?,
    ) {
        currentPromptState = null
        socialGateRepository.updateRuntimeState { current ->
            current.copy(
                phase = SocialGateRuntimePhase.IDLE,
                targetPackageName = null,
                activeGateSessionId = null,
                gateUnlockBaselineCorrectCount = null,
                gateUnlockRequiredCorrectAnswers = null,
                unlockTokenPackageName = null,
                unlockTokenIssuedAt = null,
                lastForegroundPackageName = lastForegroundPackageName ?: current.lastForegroundPackageName,
                lastForegroundChangedAt = now,
            )
        }
    }

    // El "objetivo" del gate es terminar TODAS las pendientes de Tarjetas pendientes. Ya no se topea con
    // rule.requiredCorrectAnswers: el denominador visible es la deuda real restante de la sesión QUICK.
    private fun effectiveRequiredCorrectAnswers(
        @Suppress("UNUSED_PARAMETER") rule: SocialGateRule,
        baselineCorrectCount: Int,
        sessionGoalCorrectCount: Int,
    ): Int = (sessionGoalCorrectCount - baselineCorrectCount).coerceAtLeast(1)

    private fun StudyPrompt.asSocialGatePromptState(
        targetPackageName: String,
        targetAppDisplayName: String,
        gateResolvedCount: Int,
        gateRequiredCorrectAnswers: Int,
    ): SocialGatePromptState =
        SocialGatePromptState(
            targetPackageName = targetPackageName,
            targetAppDisplayName = targetAppDisplayName,
            prompt = this,
            gateResolvedCount = gateResolvedCount,
            gateRequiredCorrectAnswers = gateRequiredCorrectAnswers,
        )

    private fun gateResolvedCount(
        session: com.estudio.antiprocrastinacion.app.model.state.StudySession,
        runtime: com.estudio.antiprocrastinacion.app.model.state.SocialGateRuntimeState,
    ): Int = (session.correctCount - (runtime.gateUnlockBaselineCorrectCount ?: session.correctCount)).coerceAtLeast(0)

    private fun hasValidUnlockForCurrentForeground(
        runtime: com.estudio.antiprocrastinacion.app.model.state.SocialGateRuntimeState,
        packageName: String,
    ): Boolean =
        runtime.phase == SocialGateRuntimePhase.UNLOCKED_FOR_CURRENT_FOREGROUND &&
            runtime.unlockTokenPackageName == packageName &&
            runtime.lastForegroundPackageName == packageName

    private fun shouldInvalidateUnlockToken(
        runtime: com.estudio.antiprocrastinacion.app.model.state.SocialGateRuntimeState,
        packageName: String,
    ): Boolean =
        runtime.unlockTokenPackageName == packageName &&
            !hasValidUnlockForCurrentForeground(runtime, packageName)

    private fun SessionMode.toGateLabel(): String =
        when (this) {
            SessionMode.QUICK -> "Tarjetas pendientes"
            SessionMode.DEEP -> "Modo profundo"
            SessionMode.DRAIN -> "Vaciar"
        }

    private fun log(message: String) {
        logger(message)
    }

    private companion object {
        const val TAG = "SocialGate"
    }
}
