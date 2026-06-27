package com.estudio.antiprocrastinacion.app.socialgate

/**
 * Configuración del comodín de escape del gate social.
 *
 * El comodín permite desbloquear la app social sin terminar el repaso, para
 * urgencias. Está limitado por una ventana móvil de 7 días.
 *
 * IMPORTANTE — límite de usos:
 *  - 1 por ventana móvil de 7 días: el comodín es una salida puntual, no un bypass persistente.
 *    Una vez usado, no vuelve a estar disponible hasta que ese uso cumple 7 días (máximo 1 semana
 *    de espera).
 *  - La cuenta de usos NO se reinicia al atrasar el reloj del teléfono: ver `pruneEscapeUses` en
 *    SocialGateCoordinator (conserva los usos con fecha futura respecto del reloj actual).
 *  - La UI no muestra un contador: el botón "Escape" aparece solo cuando hay un uso disponible y
 *    desaparece cuando se agotó.
 *
 * Ver docs/social-gate.md (sección 4).
 */
object SocialGateEscape {
    const val MAX_USES_PER_WEEK: Int = 1

    const val WINDOW_MS: Long = 7L * 24 * 60 * 60 * 1000
}
