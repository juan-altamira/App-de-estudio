package com.estudio.antiprocrastinacion.app.socialgate

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.estudio.antiprocrastinacion.MainActivity
import com.estudio.antiprocrastinacion.StudyApplication
import com.estudio.antiprocrastinacion.app.model.state.SocialGateRule
import com.estudio.antiprocrastinacion.app.ui.common.TimeProvider
import com.estudio.antiprocrastinacion.app.ui.common.DefaultTimeProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Vigilante del gate social SIN accesibilidad.
 *
 * Reemplaza a `SocialGateAccessibilityService`. Corre como Foreground Service `specialUse`,
 * sondea `UsageStatsManager` para saber qué app está en foreground y, cuando aparece una app
 * social bloqueada, alimenta al `SocialGateCoordinator` (la lógica del gate no cambia) y trae al
 * frente la única `MainActivity`, que renderiza el gate en su propio contenido.
 *
 * Por qué es más estable que la accesibilidad en HyperOS/MIUI: los permisos (Acceso de uso y
 * Mostrar sobre otras apps) NO se auto-revocan; solo el proceso puede morir, y vuelve solo por
 * START_STICKY + arranque en boot + arranque al abrir la app. No se crea ninguna ventana
 * `TYPE_APPLICATION_OVERLAY`, por lo que Android no publica el aviso de superposición revocable.
 */
class SocialGateMonitorService : Service() {
    private val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Excepción no capturada en el monitor", throwable)
            hideGatePresentation()
        }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + exceptionHandler)

    private val coordinator by lazy { (applicationContext as StudyApplication).container.socialGateCoordinator }
    private val socialGateRepository by lazy { (applicationContext as StudyApplication).container.socialGateRepository }
    private val foregroundReader by lazy { UsageStatsForegroundReader(applicationContext) }
    private val keyguardManager by lazy { getSystemService(KeyguardManager::class.java) }
    private val powerManager by lazy { getSystemService(PowerManager::class.java) }
    private val timeProvider: TimeProvider = DefaultTimeProvider()
    private val activityLaunchThrottle = GateActivityLaunchThrottle(ACTIVITY_RELAUNCH_MIN_INTERVAL_MS)
    private val foregroundProcessingTracker = ForegroundProcessingTracker()

    @Volatile
    private var enabledRulesByPackage: Map<String, SocialGateRule> = emptyMap()
    private var pollJob: Job? = null
    private var rulesJob: Job? = null
    private var lastForegroundPackage: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startAsForeground()
        observeRules()
        log("monitor service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Idempotente: si el sistema relanza el servicio (START_STICKY) ya estamos en foreground.
        startAsForeground()
        if (rulesJob?.isActive != true) {
            observeRules()
        } else if (enabledRulesByPackage.isNotEmpty() && pollJob?.isActive != true) {
            startPolling()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        pollJob?.cancel()
        rulesJob?.cancel()
        hideGatePresentation()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun observeRules() {
        if (rulesJob?.isActive == true) return
        rulesJob =
            serviceScope.launch {
                socialGateRepository.observeRules().collectLatest { rules ->
                    enabledRulesByPackage = rules.filter { it.enabled }.associateBy { it.packageName }
                    foregroundProcessingTracker.onRulesChanged(enabledRulesByPackage.isNotEmpty())
                    // Sin reglas activas no tiene sentido seguir vigilando: liberamos recursos.
                    if (enabledRulesByPackage.isEmpty()) {
                        log("no hay reglas activas → stopSelf")
                        stopSelf()
                    } else if (pollJob?.isActive != true) {
                        // No sondeamos antes de tener la primera foto de reglas. En un reinicio de
                        // proceso, procesar antes la app social como "no objetivo" puede impedir
                        // restaurar el gate mientras ese mismo paquete siga en foreground.
                        startPolling()
                    }
                }
            }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob =
            serviceScope.launch {
                while (isActive) {
                    runCatching { pollOnce() }
                        .onFailure { error ->
                            if (error is CancellationException) throw error
                            Log.e(TAG, "Fallo en el sondeo de foreground", error)
                        }
                    delay(POLL_INTERVAL_MS)
                }
            }
    }

    private suspend fun pollOnce() {
        val now = timeProvider.now()
        val previous = lastForegroundPackage
        val foreground = withContext(Dispatchers.IO) {
            foregroundReader.resolveForegroundPackage(now, previous)
        }
        lastForegroundPackage = foreground

        // Se evalúa en CADA pulso, incluso si UsageStats todavía repite el mismo paquete. Al salir
        // por Home/Recientes, HyperOS puede seguir reportando la propia app unos milisegundos;
        // la pérdida real de foco de MainActivity permite reimponerla igualmente.
        coordinator.activeGatePrompt()?.let { activeGate ->
            presentGate(activeGate)
            ensureGateActivityVisible(activeGate, foreground)
        }

        if (!foregroundProcessingTracker.shouldProcess(foreground)) return
        processStableForeground(foreground)
    }

    private suspend fun processStableForeground(packageName: String?) {
        val rule = packageName?.let { enabledRulesByPackage[it] }
        if (rule != null) {
            applyResult(coordinator.onTargetForegroundStable(rule))
            return
        }

        val activeGate = coordinator.activeGatePrompt()
        if (
            activeGate != null &&
            (packageName == this.packageName || packageName in InescapableGateDecision.ALWAYS_ALLOWED_PACKAGES)
        ) {
            // La Activity propia es donde se responde. Una llamada conserva el estado detrás, pero
            // no se disputa el primer plano hasta que termina.
            presentGate(activeGate)
            ensureGateActivityVisible(activeGate, packageName)
            return
        }

        // App no-objetivo (launcher, SystemUI, cualquier otra). Actualizamos el estado del
        // coordinador igual (mantiene lastForeground, expira tokens, etc.).
        val result = coordinator.onNonTargetForegroundStable(packageName)

        // Gate inescapable: si hay un gate disparado sin resolver, volvemos a traer la Activity
        // sobre cualquier otra app hasta que se estudia o se usa el escape.
        val keepGate =
            InescapableGateDecision.shouldKeepGateOver(
                foregroundPackage = packageName,
                ownPackage = this.packageName,
                hasActiveGate = activeGate != null,
            )
        if (keepGate && activeGate != null) {
            applyResult(SocialGateCoordinatorResult.ShowPrompt(activeGate))
        } else {
            applyResult(result)
        }
    }

    private suspend fun applyResult(
        result: SocialGateCoordinatorResult,
        returnToPreviousApp: Boolean = false,
    ) {
        when (result) {
            SocialGateCoordinatorResult.Allowed,
            SocialGateCoordinatorResult.HideOverlay,
            -> {
                hideGatePresentation()
                if (returnToPreviousApp) {
                    SocialGateActivityHost.requestReturnToPreviousApp()
                }
            }

            is SocialGateCoordinatorResult.ShowPrompt -> {
                presentGate(result.state)
                ensureGateActivityVisible(result.state, lastForegroundPackage)
            }

            is SocialGateCoordinatorResult.OpenStudyAppAndHideOverlay -> {
                openStudyApp(result.sessionId)
                hideGatePresentation()
            }
        }
    }

    private fun presentGate(state: SocialGatePromptState) {
        SocialGateActivityHost.showPrompt(
            state = state,
            onSubmitAnswer = { responseText, isCorrect, latencyMs ->
                serviceScope.launch {
                    applyResult(coordinator.submitAnswer(responseText, isCorrect, latencyMs))
                }
            },
            onRevealAnswer = {
                serviceScope.launch { applyResult(coordinator.revealAnswer()) }
            },
            onContinueAfterFeedback = {
                serviceScope.launch { applyResult(coordinator.continueAfterFeedback()) }
            },
            onUseEscape = {
                serviceScope.launch {
                    val result = coordinator.useEscape()
                    applyResult(
                        result = result,
                        returnToPreviousApp = result == SocialGateCoordinatorResult.HideOverlay,
                    )
                }
            },
        )
    }

    private fun ensureGateActivityVisible(
        state: SocialGatePromptState,
        foregroundPackage: String?,
    ) {
        val shouldBringToFront =
            InescapableGateDecision.shouldBringStudyActivityToFront(
                hasActiveGate = true,
                isStudyActivityInteractive = SocialGateActivityHost.isActivityInteractive,
                isScreenInteractive = powerManager?.isInteractive == true,
                isKeyguardLocked = keyguardManager?.isKeyguardLocked == true,
                foregroundPackage = foregroundPackage,
            )
        if (!shouldBringToFront) return
        if (!activityLaunchThrottle.tryAcquire(SystemClock.elapsedRealtime())) return
        log("reimposing gate activity target=${state.targetPackageName} foreground=$foregroundPackage")
        openStudyApp(sessionId = null)
    }

    private fun openStudyApp(sessionId: String?) {
        if (sessionId != null) {
            SocialGateRedirectBus.postRedirect(sessionId)
        }
        val intent =
            Intent(this, MainActivity::class.java).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        try {
            startActivity(intent)
        } catch (error: Exception) {
            Log.e(TAG, "No se pudo abrir la app de estudio desde segundo plano", error)
        }
    }

    private fun hideGatePresentation() {
        SocialGateActivityHost.hide()
        activityLaunchThrottle.reset()
    }

    private fun startAsForeground() {
        ensureNotificationChannel()
        val notification = buildNotification()
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        }.onFailure { error ->
            Log.e(TAG, "No se pudo entrar en foreground", error)
        }
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Gate social",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Mantiene activo el bloqueo de apps sociales."
                setShowBadge(false)
            }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gate social activo")
            .setContentText("Vigilando apps sociales para tu estudio.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    private fun log(message: String) {
        Log.d(TAG, message)
    }

    companion object {
        private const val TAG = "SocialGate"
        private const val CHANNEL_ID = "social_gate_monitor"
        private const val NOTIFICATION_ID = 4711
        private const val POLL_INTERVAL_MS = 500L
        private const val ACTIVITY_RELAUNCH_MIN_INTERVAL_MS = 750L

        /** Lo lee [getSocialGateGuardStatus] para mostrar si el vigilante está corriendo. */
        @Volatile
        var isRunning: Boolean = false
            private set
    }
}

/**
 * Evita procesar el foreground antes de cargar reglas y fuerza una reevaluación cuando cambian.
 * Es especialmente importante tras `START_STICKY`: la app objetivo puede seguir abierta mientras
 * DataStore entrega su primera emisión.
 */
internal class ForegroundProcessingTracker {
    private var rulesReady = false
    private var hasProcessedForeground = false
    private var lastProcessedPackage: String? = null

    fun onRulesChanged(hasEnabledRules: Boolean) {
        rulesReady = hasEnabledRules
        hasProcessedForeground = false
        lastProcessedPackage = null
    }

    fun shouldProcess(packageName: String?): Boolean {
        if (!rulesReady) return false
        if (hasProcessedForeground && packageName == lastProcessedPackage) return false
        hasProcessedForeground = true
        lastProcessedPackage = packageName
        return true
    }
}
