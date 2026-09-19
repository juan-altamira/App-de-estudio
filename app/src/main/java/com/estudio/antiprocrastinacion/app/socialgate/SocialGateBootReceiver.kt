package com.estudio.antiprocrastinacion.app.socialgate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arma el vigilante del gate tras reiniciar el teléfono o tras una actualización de la app.
 * `ensureRunning` no hace nada si faltan permisos; si están, vuelve a levantar el FGS.
 *
 * Arrancar este FGS `specialUse` desde acá usa los casos exentos de Android para
 * BOOT_COMPLETED/MY_PACKAGE_REPLACED. El receiver no abre ninguna pantalla por sí solo.
 */
class SocialGateBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> SocialGateServiceController.ensureRunning(context)
        }
    }
}
