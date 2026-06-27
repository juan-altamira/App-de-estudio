package com.estudio.antiprocrastinacion.app.socialgate

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.estudio.antiprocrastinacion.app.ui.common.StudyTheme

class SocialGateOverlayHost(
    private val context: Context,
    private val windowManager: WindowManager,
) {
    private class OverlayCallbacks(
        val onSubmitAnswer: (String, Boolean, Long) -> Unit,
        val onRevealAnswer: () -> Unit,
        val onContinueAfterFeedback: () -> Unit,
        val onUseEscape: () -> Unit,
    )

    private var composeView: ComposeView? = null
    private var overlayOwner: SocialGateOverlayOwner? = null
    private var isAttached = false
    private var lastPromptKey: String? = null
    private var promptShownAtMs: Long = 0L
    private var promptState by mutableStateOf<SocialGatePromptState?>(null)
    private var callbacks: OverlayCallbacks? = null

    fun showPrompt(
        state: SocialGatePromptState,
        onSubmitAnswer: (String, Boolean, Long) -> Unit,
        onRevealAnswer: () -> Unit,
        onContinueAfterFeedback: () -> Unit,
        onUseEscape: () -> Unit,
    ) {
        ensureAttached()
        val prompt = state.prompt
        val promptKey = "${prompt.session.sessionId}:${prompt.item.itemId}:${state.answerFeedback?.title}:${state.pendingPrompt?.item?.itemId}"
        if (promptKey != lastPromptKey) {
            promptShownAtMs = System.currentTimeMillis()
            lastPromptKey = promptKey
        }
        callbacks = OverlayCallbacks(onSubmitAnswer, onRevealAnswer, onContinueAfterFeedback, onUseEscape)
        promptState = state
    }

    fun hide() {
        if (!isAttached && composeView == null && overlayOwner == null) return
        val view = composeView
        val owner = overlayOwner
        if (isAttached && view != null) {
            runCatching {
                windowManager.removeViewImmediate(view)
            }.onFailure { error ->
                Log.w(TAG, "Ignoring stale social gate overlay removal", error)
            }
        }
        runCatching {
            owner?.destroy()
        }.onFailure { error ->
            Log.w(TAG, "Ignoring stale social gate overlay lifecycle teardown", error)
        }
        composeView = null
        overlayOwner = null
        isAttached = false
        lastPromptKey = null
        promptShownAtMs = 0L
        promptState = null
        callbacks = null
    }

    private fun ensureAttached() {
        if (isAttached) return

        val owner = SocialGateOverlayOwner().apply { create() }
        val view =
            ComposeView(context).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                isClickable = true
                isFocusable = true
                setContent {
                    StudyTheme {
                        promptState?.let { state ->
                            SocialGateOverlayScreen(
                                state = state,
                                onSubmitAnswer = { responseText, isCorrect ->
                                    callbacks?.onSubmitAnswer?.invoke(
                                        responseText,
                                        isCorrect,
                                        System.currentTimeMillis() - promptShownAtMs,
                                    )
                                },
                                onRevealAnswer = { callbacks?.onRevealAnswer?.invoke() },
                                onContinueAfterFeedback = { callbacks?.onContinueAfterFeedback?.invoke() },
                                onUseEscape = { callbacks?.onUseEscape?.invoke() },
                            )
                        }
                    }
                }
            }

        val layoutParams =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                // Sin accesibilidad usamos overlay de aplicación (requiere SYSTEM_ALERT_WINDOW).
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

        windowManager.addView(view, layoutParams)
        composeView = view
        overlayOwner = owner
        isAttached = true
    }

    private companion object {
        const val TAG = "SocialGate"
    }
}

/**
 * A window added directly through WindowManager has no Activity behind it, so Compose
 * cannot find the view-tree owners it needs. This owner backs the overlay's ComposeView
 * for the lifetime of a single attach/detach cycle.
 */
private class SocialGateOverlayOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun create() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}
