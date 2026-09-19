package com.estudio.antiprocrastinacion.app.socialgate

/**
 * Decide si un gate ya disparado debe seguir mostrándose encima de la app que ahora está en
 * primer plano ("inescapable"): el gate no se puede esquivar yendo a Home, abriendo otra app
 * o bajando notificaciones; permanece hasta que se resuelve (se estudia) o se usa el escape.
 *
 * Cede (NO se mantiene) en dos casos:
 *  - cuando el foreground es la propia app de estudio (ahí se estudia, el overlay sobra);
 *  - cuando el foreground es teléfono/llamadas (para no dejar al usuario incomunicado ante
 *    una llamada entrante).
 */
object InescapableGateDecision {
    /** Apps a las que el gate SIEMPRE cede (teléfono / llamada entrante). */
    val ALWAYS_ALLOWED_PACKAGES: Set<String> =
        setOf(
            "com.google.android.dialer",
            "com.android.dialer",
            "com.android.incallui",
            "com.xiaomi.incallui",
            "com.android.server.telecom",
            "com.android.phone",
            "com.xiaomi.phone",
        )

    fun shouldKeepGateOver(
        foregroundPackage: String?,
        ownPackage: String,
        hasActiveGate: Boolean,
        alwaysAllowed: Set<String> = ALWAYS_ALLOWED_PACKAGES,
    ): Boolean {
        if (!hasActiveGate) return false
        if (foregroundPackage == ownPackage) return false
        if (foregroundPackage != null && foregroundPackage in alwaysAllowed) return false
        return true
    }

    /**
     * Decide si el servicio debe volver a traer la Activity del gate al frente.
     *
     * El paquete reportado puede seguir siendo el propio durante unos milisegundos después de
     * Home/Recientes. Por eso manda la visibilidad real de la Activity. Teléfono y pantalla de
     * bloqueo siempre tienen prioridad.
     */
    fun shouldBringStudyActivityToFront(
        hasActiveGate: Boolean,
        isStudyActivityInteractive: Boolean,
        isScreenInteractive: Boolean,
        isKeyguardLocked: Boolean,
        foregroundPackage: String?,
        alwaysAllowed: Set<String> = ALWAYS_ALLOWED_PACKAGES,
    ): Boolean {
        if (!hasActiveGate || isStudyActivityInteractive) return false
        if (!isScreenInteractive || isKeyguardLocked) return false
        if (foregroundPackage != null && foregroundPackage in alwaysAllowed) return false
        return true
    }
}
