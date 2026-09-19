package com.estudio.antiprocrastinacion.app.socialgate

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Estado de los permisos/condiciones que necesita el gate social SIN accesibilidad:
 *  - "Acceso de uso" (PACKAGE_USAGE_STATS) para detectar la app en foreground.
 *  - "Mostrar sobre otras apps" (SYSTEM_ALERT_WINDOW) para que Android permita traer la Activity
 *    al frente desde segundo plano. No se dibuja una ventana de superposición.
 *  - El servicio vigilante efectivamente corriendo.
 */
data class SocialGateGuardStatus(
    val usageAccessGranted: Boolean,
    val backgroundLaunchGranted: Boolean,
    val serviceRunning: Boolean,
) {
    /** Permisos listos: el servicio ya puede vigilar y bloquear. */
    val permissionsReady: Boolean = usageAccessGranted && backgroundLaunchGranted

    /** Todo en orden: permisos concedidos y servicio activo. */
    val fullyOperational: Boolean = permissionsReady && serviceRunning

    /** Permisos dados pero el servicio no corre (lo análogo a "configurado pero inactivo"). */
    val configuredButNotRunning: Boolean = permissionsReady && !serviceRunning
}

fun hasUsageStatsAccess(context: Context): Boolean {
    val appOpsManager = context.getSystemService(AppOpsManager::class.java) ?: return false
    val mode =
        appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName,
        )
    return mode == AppOpsManager.MODE_ALLOWED
}

fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

fun getSocialGateGuardStatus(context: Context): SocialGateGuardStatus =
    SocialGateGuardStatus(
        usageAccessGranted = hasUsageStatsAccess(context),
        backgroundLaunchGranted = canDrawOverlays(context),
        serviceRunning = SocialGateMonitorService.isRunning,
    )

fun usageAccessSettingsIntent(): Intent =
    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

fun overlaySettingsIntent(context: Context): Intent =
    Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
