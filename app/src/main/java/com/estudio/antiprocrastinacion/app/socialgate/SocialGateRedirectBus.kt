package com.estudio.antiprocrastinacion.app.socialgate

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton event bus that signals the Compose UI to navigate directly
 * to a study session after a social gate is completed.
 *
 * This avoids relying on Intent extras and lifecycle observers,
 * which are unreliable when the app is brought to the foreground
 * from an AccessibilityService overlay.
 */
object SocialGateRedirectBus {
    private const val TAG = "SocialGate"
    private val _pendingSessionId = MutableStateFlow<String?>(null)
    val pendingSessionId: StateFlow<String?> = _pendingSessionId.asStateFlow()

    fun postRedirect(sessionId: String) {
        Log.d(TAG, "RedirectBus: posting sessionId=$sessionId")
        _pendingSessionId.value = sessionId
    }

    fun consume() {
        Log.d(TAG, "RedirectBus: consumed")
        _pendingSessionId.value = null
    }
}
