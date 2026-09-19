package com.estudio.antiprocrastinacion.app.socialgate

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Puente en memoria entre el vigilante y la única Activity de la app.
 *
 * El gate se renderiza dentro de [com.estudio.antiprocrastinacion.MainActivity], no en una
 * ventana `TYPE_APPLICATION_OVERLAY`. Así Android no crea la notificación del sistema que
 * ofrece revocar la superposición. El servicio sigue siendo dueño de la lógica y de las
 * corrutinas; este objeto solo publica estado inmutable y devuelve las acciones de la UI.
 */
object SocialGateActivityHost {
    private data class Callbacks(
        val onSubmitAnswer: (String, Boolean, Long) -> Unit,
        val onRevealAnswer: () -> Unit,
        val onContinueAfterFeedback: () -> Unit,
        val onUseEscape: () -> Unit,
    )

    private val _promptState = MutableStateFlow<SocialGatePromptState?>(null)
    val promptState: StateFlow<SocialGatePromptState?> = _promptState.asStateFlow()

    private val _returnToPreviousApp = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val returnToPreviousApp: SharedFlow<Unit> = _returnToPreviousApp.asSharedFlow()

    private var callbacks: Callbacks? = null
    private var lastPromptKey: String? = null
    private var promptShownAtElapsedMs: Long = 0L

    @Volatile
    private var activityResumed = false

    @Volatile
    private var activityHasWindowFocus = false

    val isActivityInteractive: Boolean
        get() = activityResumed && activityHasWindowFocus

    fun showPrompt(
        state: SocialGatePromptState,
        onSubmitAnswer: (String, Boolean, Long) -> Unit,
        onRevealAnswer: () -> Unit,
        onContinueAfterFeedback: () -> Unit,
        onUseEscape: () -> Unit,
    ) {
        val prompt = state.prompt
        val promptKey =
            "${state.targetPackageName}:${prompt.session.sessionId}:${prompt.item.itemId}:" +
                "${state.answerFeedback?.title}:${state.pendingPrompt?.item?.itemId}"
        if (promptKey != lastPromptKey) {
            promptShownAtElapsedMs = SystemClock.elapsedRealtime()
            lastPromptKey = promptKey
        }
        callbacks = Callbacks(onSubmitAnswer, onRevealAnswer, onContinueAfterFeedback, onUseEscape)
        _promptState.value = state
    }

    fun hide() {
        _promptState.value = null
        callbacks = null
        lastPromptKey = null
        promptShownAtElapsedMs = 0L
    }

    fun submitAnswer(
        responseText: String,
        isCorrect: Boolean,
    ) {
        val shownAt = promptShownAtElapsedMs
        val latencyMs =
            if (shownAt == 0L) {
                0L
            } else {
                (SystemClock.elapsedRealtime() - shownAt).coerceAtLeast(0L)
            }
        callbacks?.onSubmitAnswer?.invoke(responseText, isCorrect, latencyMs)
    }

    fun revealAnswer() {
        callbacks?.onRevealAnswer?.invoke()
    }

    fun continueAfterFeedback() {
        callbacks?.onContinueAfterFeedback?.invoke()
    }

    fun useEscape() {
        callbacks?.onUseEscape?.invoke()
    }

    fun requestReturnToPreviousApp() {
        _returnToPreviousApp.tryEmit(Unit)
    }

    fun onActivityResumed() {
        activityResumed = true
    }

    fun onActivityPaused() {
        activityResumed = false
        activityHasWindowFocus = false
    }

    fun onActivityWindowFocusChanged(hasFocus: Boolean) {
        activityHasWindowFocus = hasFocus
    }
}

/** Evita tormentas de `startActivity` si HyperOS tarda en devolver el foco. */
internal class GateActivityLaunchThrottle(
    private val minimumIntervalMs: Long,
) {
    private var lastLaunchAtElapsedMs: Long? = null

    fun tryAcquire(nowElapsedMs: Long): Boolean {
        val previous = lastLaunchAtElapsedMs
        val intervalElapsed = previous == null || nowElapsedMs < previous || nowElapsedMs - previous >= minimumIntervalMs
        if (!intervalElapsed) return false
        lastLaunchAtElapsedMs = nowElapsedMs
        return true
    }

    fun reset() {
        lastLaunchAtElapsedMs = null
    }
}
