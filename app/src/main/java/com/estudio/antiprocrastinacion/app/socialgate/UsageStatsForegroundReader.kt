package com.estudio.antiprocrastinacion.app.socialgate

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Lee `UsageStatsManager` para resolver qué app está en primer plano, reemplazando la
 * detección por accesibilidad. Requiere el permiso "Acceso de uso" (PACKAGE_USAGE_STATS).
 *
 * Estrategia: en cada sondeo se consulta una ventana corta de eventos `ACTIVITY_RESUMED`.
 * Si no hubo eventos (el usuario lleva rato en la misma app), se mantiene el último foreground
 * conocido. Como respaldo para el arranque en frío (servicio reiniciado con una app social ya
 * abierta) se usa `queryUsageStats` para deducir la app activa aunque no haya eventos recientes.
 */
class UsageStatsForegroundReader(
    context: Context,
    private val lookbackMs: Long = DEFAULT_LOOKBACK_MS,
) {
    private val usageStatsManager =
        context.applicationContext.getSystemService(UsageStatsManager::class.java)

    fun resolveForegroundPackage(
        nowMs: Long,
        previousForeground: String?,
    ): String? {
        val manager = usageStatsManager ?: return previousForeground
        val events = readForegroundEvents(manager, nowMs - lookbackMs, nowMs)
        val resolved = ForegroundEventResolver.latestForegroundPackage(events, previousForeground)
        if (resolved != null) return resolved
        return mostRecentlyUsedPackage(manager, nowMs - lookbackMs, nowMs)
    }

    private fun readForegroundEvents(
        manager: UsageStatsManager,
        beginMs: Long,
        endMs: Long,
    ): List<ForegroundUsageEvent> {
        val collected = mutableListOf<ForegroundUsageEvent>()
        val usageEvents = manager.queryEvents(beginMs, endMs) ?: return collected
        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val isForeground =
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            val packageName = event.packageName
            if (isForeground && !packageName.isNullOrBlank()) {
                collected += ForegroundUsageEvent(
                    packageName = packageName,
                    movedToForeground = true,
                    timestampMs = event.timeStamp,
                )
            }
        }
        return collected
    }

    private fun mostRecentlyUsedPackage(
        manager: UsageStatsManager,
        beginMs: Long,
        endMs: Long,
    ): String? =
        manager
            .queryUsageStats(UsageStatsManager.INTERVAL_BEST, beginMs, endMs)
            ?.filter { it.lastTimeUsed in (beginMs + 1)..endMs }
            ?.maxByOrNull { it.lastTimeUsed }
            ?.packageName

    companion object {
        const val DEFAULT_LOOKBACK_MS: Long = 60_000L
    }
}
