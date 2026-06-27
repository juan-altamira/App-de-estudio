package com.estudio.antiprocrastinacion.app.socialgate

/**
 * Evento simplificado de uso, agnóstico de Android, para poder testear la lógica de
 * resolución del foreground sin depender de `UsageStatsManager`.
 */
data class ForegroundUsageEvent(
    val packageName: String,
    val movedToForeground: Boolean,
    val timestampMs: Long,
)

/**
 * Lógica PURA de detección del paquete en primer plano a partir de los eventos de uso.
 *
 * Reemplaza al antiguo `ForegroundAppResolver` (que leía la lista de ventanas de la
 * accesibilidad). Acá la fuente es `UsageStatsManager`: el paquete en foreground es el del
 * último evento de "pasó a primer plano". Si en la ventana consultada no hubo ningún evento
 * de foreground (el usuario sigue en la misma app desde hace rato), se conserva el último
 * conocido (`previousForeground`) para no perder el estado entre sondeos.
 */
object ForegroundEventResolver {
    fun latestForegroundPackage(
        events: List<ForegroundUsageEvent>,
        previousForeground: String?,
    ): String? {
        val lastForegroundEvent =
            events
                .filter { it.movedToForeground }
                .maxByOrNull { it.timestampMs }
        return lastForegroundEvent?.packageName ?: previousForeground
    }
}
