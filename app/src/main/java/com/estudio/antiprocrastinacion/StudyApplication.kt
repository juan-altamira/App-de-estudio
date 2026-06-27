package com.estudio.antiprocrastinacion

import android.app.Application
import com.estudio.antiprocrastinacion.app.di.AppContainer
import com.estudio.antiprocrastinacion.app.socialgate.SocialGateServiceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StudyApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            container.cognitiveNotificationScheduler.refreshSchedule()
        }
        // Levanta el vigilante del gate (no hace nada si faltan permisos; el servicio se
        // autodetiene si no hay reglas activas). Reemplaza al viejo AccessibilityService.
        SocialGateServiceController.ensureRunning(this)
    }
}
