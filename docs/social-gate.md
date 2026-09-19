# Gate social — comportamiento vigente

Este documento es la fuente de verdad sobre como funciona hoy el gate social
(el bloqueo que aparece sobre apps como Instagram). Si cambia la logica del
gate, actualizar este archivo, `docs/project-context-fixed.md`, `docs/testing-policy.md`,
`docs/roadmap-master.md` y `AGENTS.md` en la misma tarea.

Archivos clave:

- `app/socialgate/SocialGateCoordinator.kt` — orquesta montar gate, responder,
  feedback, desbloqueo y comodin de escape.
- `app/domain/session/DefaultSessionEngine.kt` — `startSocialGateSession` queda
  solo como compatibilidad y delega en `startQuickSession`.
- `app/domain/scheduler/DefaultSchedulerService.kt` — `buildSocialGatePacket`
  queda como camino legacy real-only: no rellena y no adelanta tarjetas futuras.
- `app/socialgate/SocialGateOverlayScreen.kt` — UI del gate, espejada con
  Tarjetas pendientes, mas boton de escape.
- `app/socialgate/SocialGateMonitorService.kt` — Foreground Service `specialUse`
  que detecta el foreground via `UsageStatsManager` (sin accesibilidad) y alimenta
  al coordinador. Publica la pantalla en la Activity y la vuelve a traer al frente
  mientras el gate siga activo. `SocialGateServiceController` lo arranca (app,
  boot, regla); `SocialGateBootReceiver` lo re-arma tras reiniciar.
- `app/socialgate/UsageStatsForegroundReader.kt` + `ForegroundEventResolver.kt` —
  detección del paquete en primer plano (la logica pura es testeable).
- `app/socialgate/SocialGateActivityHost.kt` — `StateFlow` de presentación,
  callbacks y estado real de foco de la única Activity.

NOTA: el gate ya NO usa accesibilidad. En HyperOS/MIUI quedaba "configurada pero
inactiva" (el binding se auto-revoca). Tampoco crea una ventana
`TYPE_APPLICATION_OVERLAY`: el gate se dibuja dentro de `MainActivity`, por lo que
Android no genera la notificación de sistema "se muestra sobre otras apps" que
enlaza directamente a revocar la superposición. `SYSTEM_ALERT_WINDOW` se mantiene
solo para que Android permita el background Activity launch.

GATE INESCAPABLE: el gate se dispara al abrir una app social, pero una vez arriba
la Activity vuelve al frente si se intenta ir a Home, Recientes, notificaciones u
otra app, hasta resolverlo (estudiar) o usar el comodín de escape. El monitor pulsa
cada 500 ms y limita `startActivity` a un intento cada 750 ms para no producir una
tormenta de relanzamientos. El sondeo comienza solo después de recibir la primera
foto de reglas y vuelve a evaluar el foreground cuando esas reglas cambian, incluso
si el paquete visible no cambió. La decisión vive en `InescapableGateDecision` (puro,
testeable): cede ante teléfono/llamadas, pantalla apagada y lock screen.

LIMITE DE PLATAFORMA: en un telefono personal Android siempre conserva salidas de
sistema como forzar detencion, el boton Stop de "Apps activas", revocar permisos,
modo seguro o desinstalar. Una app ordinaria no puede eliminar esas salidas. Este
gate elimina los atajos que controlamos y la notificacion revocadora del alert
window; acercarse a kiosk/device-owner requeriria un dispositivo administrado y
sigue fuera del alcance del MVP.

## 1. Contrato actual

El gate social permanece **permanentemente activo** para todas las aplicaciones soportadas (`Instagram`, `TikTok`, `X`). No existe switch de apagado ni estado inactivo en la UI ni en la persistencia. `SocialGateSchedule.normalizeRule` y `LocalSocialGateRepository` fuerzan incondicionalmente `enabled = true` para toda regla procesada o almacenada, preservando frecuencias y ventanas horarias configurables.

El gate reutiliza o abre la sesion QUICK de `Tarjetas pendientes`. Cuando una
app social pasa a foreground y el gate esta due:

1. Si ya hay un gate activo para ese paquete, se restaura.
2. Si hay un token de desbloqueo vigente para ese mismo foreground, se permite.
3. Si existe una sesion QUICK activa, el gate se monta sobre esa sesion.
4. Si no existe QUICK activa, el gate abre `Tarjetas pendientes` con
   `startQuickSession()`.
5. Si no hay deuda real pendiente para `Tarjetas pendientes`, el gate no aplica:
   no bloquea, no abre la pantalla del gate y no inventa preguntas.

Las sesiones `DEEP` y `DRAIN` nunca se montan como gate ni se reemplazan. Si hay
una sesion profunda activa y tambien deuda real de Tarjetas pendientes, el gate
abre QUICK en su propio slot y conserva intacta la sesion profunda.

## 2. Preguntas y repeticion espaciada

El gate muestra la misma UI y usa la misma logica de Tarjetas pendientes:

- misma sesion QUICK;
- misma seleccion de deuda real pendiente;
- mismo SR/eventos de review para preguntas reales;
- mismo primer pase y misma correccion final de falladas;
- mismos `anzuelo` y `rescue` que Tarjetas pendientes cuando esa sesion los
  dispare.

No existe relleno gate-only. El gate no usa una tarjeta solo para completar un
contador de desbloqueo, no toma tarjetas no due de otros dias y no convierte
rescates en deuda real. Si una tarjeta se responde bien, su estado de SR se
actualiza y no debe reaparecer inmediatamente como deuda pendiente; si se falla,
queda viva para la fase de correccion hasta responderla bien.

`buildSocialGatePacket` se mantiene por compatibilidad interna, pero debe
permanecer real-only:

- devuelve `null` si no hay preguntas reales due aptas para gate;
- no usa `RESCUE` ni fillers;
- no usa tarjetas futuras/no due;
- no acota la deuda real a `requiredCorrectAnswers`.

## 3. Desbloqueo

El gate se desbloquea solo cuando la sesion de Tarjetas pendientes termina sin
errores reales vivos. Mientras `SessionTransition.Advanced` devuelva otra
pregunta, la pantalla sigue bloqueando. Cuando `SessionTransition.Completed`
confirma que no queda deuda real pendiente, se muestra el feedback de la ultima
respuesta y el boton pasa a `Desbloquear app`.

El contador visible del gate representa avance dentro de la deuda real de la
sesion QUICK, no un objetivo artificial de "correctas por gate".

## 4. Comodin de escape

El comodin de escape permite desbloquear la app social actual sin terminar el
repaso:

- aparece pequeno, en la esquina inferior del gate, solo si hay un uso disponible;
- el texto del boton es solo `Escape`, sin contador: si esta visible se puede usar; si
  no, no se muestra;
- al tocarlo abre confirmacion con la proxima fecha disponible;
- al confirmar, emite un token solo para el foreground actual;
- no completa la sesion de estudio;
- no otorga credito de SR;
- al salir de esa app social, el token deja de servir y el gate puede volver si
  aun queda deuda real pendiente.

El limite vive en `SocialGateEscape.MAX_USES_PER_WEEK`.

- 1 uso por ventana movil de 7 dias: una vez usado, no vuelve hasta que ese uso cumple
  7 dias (maximo 1 semana de espera).
- Si no queda uso, el boton deja de mostrarse.
- Los usos quedan registrados en `SocialGateRuntimeState.escapeUsesAt`.
- Anti-abuso de reloj: `pruneEscapeUses` conserva los usos con fecha futura respecto del
  reloj actual, asi que atrasar la hora del telefono NO reabre el comodin.

## 5. TODO vivo

- Mantener tests unitarios para que el coordinador nunca vuelva a llamar al
  carril social separado cuando el gate real esta due.
- Mantener tests unitarios para que el scheduler legacy no acepte fillers,
  rescues ni tarjetas futuras como gate.
- Los tests UI/instrumentados del gate siguen sujetos a la salvaguarda del
  dispositivo MIUI: nunca ejecutarlos contra las preguntas reales del telefono
  primario; usar base aislada en emulador/dispositivo separado.
