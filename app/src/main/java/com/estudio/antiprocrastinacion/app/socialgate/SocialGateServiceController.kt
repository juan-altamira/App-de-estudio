package com.estudio.antiprocrastinacion.app.socialgate

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Punto único para arrancar/detener el [SocialGateMonitorService]. El servicio se autogestiona
 * (se detiene solo si no hay reglas activas o faltan permisos), así que estos helpers solo
 * "lo despiertan": en el arranque de la app, al reiniciar el teléfono, o al habilitar una regla.
 *
 * Arrancar un FGS desde segundo plano (boot) está permitido porque la app tiene
 * SYSTEM_ALERT_WINDOW, que exime de las restricciones de inicio en background.
 */
object SocialGateServiceController {
    private const val TAG = "SocialGate"

    fun ensureRunning(context: Context) {
        if (!hasUsageStatsAccess(context) || !canDrawOverlays(context)) {
            Log.d(TAG, "ensureRunning skipped: permisos incompletos")
            return
        }
        start(context)
    }

    fun start(context: Context) {
        val intent = Intent(context.applicationContext, SocialGateMonitorService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.applicationContext.startForegroundService(intent)
            } else {
                context.applicationContext.startService(intent)
            }
        }.onFailure { error ->
            Log.e(TAG, "No se pudo arrancar el SocialGateMonitorService", error)
        }
    }

    fun stop(context: Context) {
        val intent = Intent(context.applicationContext, SocialGateMonitorService::class.java)
        runCatching { context.applicationContext.stopService(intent) }
    }
}
