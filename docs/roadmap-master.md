# Roadmap maestro — App de estudio anti-procrastinación

Especificación consolidada de producto, lógica de negocio, arquitectura, MVP, versiones futuras y secuencia de implementación.

Versión normalizada para el repo actual, basada en:
- [roadmap_maestro_app_estudio_antiprocrastinacion.docx](/home/usuario/Descargas/roadmap_maestro_app_estudio_antiprocrastinacion.docx)
- [docs/project-context-fixed.md](/home/usuario/CascadeProjects/App%20de%20Estudio/docs/project-context-fixed.md)
- los contratos ya cerrados y ya implementados en el código

## Qué contiene este documento

- visión completa del producto y principios rectores
- lógica de negocio consolidada, sin huecos importantes
- arquitectura técnica del MVP local Android ya endurecida
- roadmap completo por fases y versiones futuras acordadas
- criterios de calidad, pruebas, riesgos y secuencia correcta de implementación
- checklist operativo vivo para seguir tachando lo ya hecho

## Cómo usar este archivo

Este archivo es la fuente de seguimiento de alto nivel del proyecto.

Debe usarse de esta manera:
- se consulta antes de tocar comportamiento de producto, persistencia, scheduler, sesiones o UX relevante
- se actualiza en la misma tarea cuando cambia el estado real del proyecto
- si este archivo y el código divergen, la tarea está incompleta

## Leyenda de estado

- `[x]` cerrado e implementado de forma suficiente para el alcance actual
- `[-]` parcialmente implementado, parcialmente validado o todavía con huecos importantes
- `[ ]` aceptado en alcance total, pero no implementado todavía

## Reglas de normalización para compatibilidad con el repo actual

Este roadmap toma el `.docx` como base textual, pero normaliza los puntos que hoy entrarían en conflicto con contratos ya cerrados del repo.

### 1. Programa vs `course`

El documento fuente habla de `programa`, pero el código actual usa `course` y `course_id`.

Regla normalizada:
- a nivel conceptual, `programa` y `course` refieren a la misma capa
- a nivel de contrato técnico del repo, siguen vigentes `course_id`, `CourseEntity` y `courses`

### 2. Tipos de nodo

El documento fuente enumera:
- `conceptual`
- `conceptual_comparative`
- `process`
- `causal`
- `applied`
- `integration`
- `boss`

El repo actual usa:
- `CONCEPT`
- `PROCESS`
- `COMPARISON`
- `INTEGRATION`
- `BOSS`

Regla normalizada:
- se conserva el propósito semántico del documento fuente
- el contrato técnico vigente del repo sigue siendo el enum actual
- mientras no se apruebe una migración explícita de taxonomía, `conceptual`, `causal` y `applied` quedan absorbidos operativamente bajo `CONCEPT`, y `conceptual_comparative` bajo `COMPARISON`
- si en el futuro se amplía la taxonomía, deberá hacerse sin romper identidad ni compatibilidad de importación

### 3. Respuestas abiertas y formatos textuales

El documento fuente lista formatos textuales como parte del sistema.

El repo actual ya cerró esta regla:
- la app nunca debe pedir tipeo libre durante el flujo de estudio
- las preguntas abiertas, de desarrollo o fill-style se resuelven con `Ver respuesta` y autoevaluación

Regla normalizada:
- los formatos textuales siguen existiendo como formatos de contenido y de dificultad/fricción
- en la UI actual no se validan por string comparison
- no hay input libre en estudio hasta que exista un evaluador real, cosa que no forma parte del producto actual

### 4. Hilt y DI

El documento fuente fija Hilt como parte del stack.

El repo actual conserva artefactos Hilt en el build, pero el wiring activo de ViewModels y dependencias corre hoy por `AppContainer` y factories manuales.

Regla normalizada:
- la intención arquitectónica de una capa DI separada se conserva
- el contrato funcional no depende hoy de Hilt como runtime principal
- el roadmap puede seguir mencionando Hilt como dirección/base de DI, siempre que no contradiga la implementación activa del repo

### 5. Editor manual dentro del MVP local

El documento fuente deja fuera de alcance un editor manual de contenido dentro del MVP local.

El repo actual ya implementó:
- import por archivo
- import manual por texto JSON crudo en el dispositivo

Regla normalizada:
- sigue fuera de alcance un editor rico/estructurado de contenido dentro del MVP local
- sí queda dentro del alcance actual la entrada manual de JSON crudo como herramienta de importación

### 6. Sesión activa y reemplazo

El documento fuente no contradecía esto, pero el repo actual ya cerró una regla más fuerte:
- una sesión activa nunca se reemplaza en silencio
- iniciar otra requiere confirmación explícita y descarte deliberado

Esa regla queda incorporada formalmente en este roadmap.

## Orden de ejecución actual del repo

Estos son los próximos frentes a atacar salvo repriorización explícita del usuario:

1. `[-]` Gestión robusta de contenido grande y revisión de importaciones
2. `[x]` Snapshot local exportable/importable para backup y portabilidad manual
3. `[-]` Hooks externos Android: notificación y gate social implementados; widget y alarma pendientes
4. `[ ]` Web sobria de authoring y estudio manual
5. `[ ]` Sync manual con Supabase y continuidad cross-device

## 1. Visión general y objetivo del producto

La app es un sistema de repaso espaciado anti-procrastinación pensado para que el usuario repase contenido serio aunque normalmente evitaría abrir una app de estudio.

La versión inicial está optimizada para un solo usuario y para Android, con una arquitectura local-first y authoring principalmente por JSON.

### 1.1 Qué es y qué no es

Es:
- una app Android-first
- una app centrada inicialmente en un solo usuario
- una app enfocada en repaso, no en aprendizaje inicial como función principal
- una app diseñada para maximizar adherencia real, no solo memoria teórica
- una app pensada para funcionar tanto en días de procrastinación como en días de estudio intenso

No es:
- una app de flashcards clásica
- una app que dependa de fuerza de voluntad pura
- una app que modele solo memoria
- una app pensada primero para múltiples usuarios ni para cohortes en la fase inicial

### 1.2 Objetivo central

- que el usuario no abandone
- que el usuario entre al estudio sin depender de decidir voluntariamente estudiar
- que el sistema mantenga vivos los temas importantes
- que el costo de entrada sea bajísimo
- que la continuidad sea natural
- que abandonar requiera una decisión activa
- que en los días buenos el sistema permita estudiar mucho, no solo rescatar días malos

### Estado actual del repo

- `[x]` Dirección de producto alineada
- `[x]` Android local-first como fase actual
- `[x]` Un solo usuario como alcance actual
- `[x]` Quick, deep y drain ya forman parte del producto real

## 2. Principios fundamentales del producto

- la unidad base no es la tarjeta; la unidad base es el nodo
- se repasan nodos, no tarjetas aisladas
- el sistema modela memoria, fricción, cobertura y peso curricular
- el backlog total no domina la UX
- las superficies externas solo disparan el primer toque; la continuidad ocurre en pantalla completa dentro de la app
- seguir debe ser fácil y abandonar debe requerir una decisión activa
- la app no depende de apertura voluntaria; usa hooks externos cuando entren en las fases posteriores
- no hay sistema de impugnación, rival ni hater por ahora

### Estado actual del repo

- `[x]` Nodo como unidad base real
- `[x]` Continuidad in-app full-screen
- `[x]` Seguir fácil y abandonar activo como principio de UX
- `[-]` Hooks externos parcialmente implementados: notificación y gate social; widget y alarma pendientes

## 3. Modelo conceptual del contenido

La jerarquía base del contenido es:

`programa → unidad → outcome → nodo → facetas → ítems`

### 3.1 Programa

Es la fuente de verdad del contenido. Puede incluir programa oficial, apuntes, slides, parciales, fuentes del curso y cualquier material que el usuario defina como válido.

### 3.2 Unidad

Bloque temático grande. Ejemplos:
- consenso en Ethereum
- memoria virtual
- phrasal verbs
- economía monetaria

### 3.3 Outcome

Habilidad concreta que debe dominarse. Ejemplos:
- distinguir fork choice de finality
- explicar slot/epoch/checkpoint
- comparar conceptos
- describir un proceso completo

### 3.4 Nodo

Unidad real de repaso.

Debe ser:
- una idea central cerrable
- evaluable
- suficientemente importante para mantenerse viva por separado
- suficientemente acotada para reaparecer seguido

### 3.5 Facetas

Subcaras del nodo que permiten repasar el conocimiento desde distintos ángulos.

### 3.6 Ítems

Preguntas o ejercicios concretos visibles en la app; son la forma visible de activar o medir parte de un nodo.

### Estado actual del repo

- `[x]` Jerarquía conceptual cerrada
- `[x]` Modelos persistidos para `course/unit/outcome/node/item`
- `[x]` La semántica conceptual de `programa` queda absorbida por `course` en el contrato técnico vigente

## 4. Especificación de nodos

### 4.1 Propiedades

Cada nodo:
- cubre una idea central
- no debe ser trivial
- no debe ser monstruosamente grande
- debe estar ligado a outcomes reales
- debe poder aparecer en distintos formatos
- debe permitir medición de dominio real

### 4.2 Campos obligatorios

`node_id`, `course_id`, `unit_id`, `outcome_ids`, `title`, `core_claim`, `type`, `weight_exam`, `prerequisites`, `facets`, `must_know`, `common_errors`, `minimum_mastery_definition`, `surface_easy_ready`, `surface_easy_item_count`, `source_refs`, `version`, `updated_at`

### 4.3 Regla de identidad

Mientras conserve el mismo `node_id`, el sistema lo trata como el mismo nodo.

No existe detección automática de cambio semántico radical.

Si realmente es otro nodo:
- se crea con otro `node_id`

Si se quiere resetear progreso:
- se hace con una acción manual explícita

### 4.4 Tipos de nodo

Tipos de nodo definidos en el documento fuente:
- `conceptual`
- `conceptual_comparative`
- `process`
- `causal`
- `applied`
- `integration`
- `boss`

### Normalización compatible con el repo actual

El contrato técnico vigente sigue siendo:
- `CONCEPT`
- `PROCESS`
- `COMPARISON`
- `INTEGRATION`
- `BOSS`

La intención del documento fuente se conserva, pero sin abrir una migración de taxonomía en esta etapa.

### Estado actual del repo

- `[x]` Identidad por `node_id` cerrada
- `[x]` No hay reset implícito por cambios de wording
- `[x]` Campos de nodo modelados
- `[-]` La taxonomía exacta del documento fuente está normalizada, no replicada 1:1 en código

## 5. Facetas y errores típicos

### 5.1 Familias de facetas

- `definicion_funcional`
- `proceso_paso_a_paso`
- `diferencia_entre_conceptos`
- `causa_efecto`
- `escenario_aplicado`
- `caso_limite`
- `error_tipico`
- `integracion_con_otro_nodo`

Cada nodo debería tener entre 3 y 6 facetas útiles.

### 5.2 Common errors y traps

Las confusiones típicas viven dentro del nodo afectado.

Se representan:
- en `common_errors`
- como faceta `error_tipico`

Los ítems `trap` detectan confusiones conceptuales reales, no wording capcioso.

Puede existir un nodo comparativo aparte solo cuando la distinción entre conceptos ya es conocimiento en sí mismo.

### 5.3 Funcionamiento completo

En temas importantes debe existir al menos un nodo integrador o boss.

Las partes se repasan; el sistema completo se evalúa.

El funcionamiento completo no reemplaza las preguntas puntuales.

### Estado actual del repo

- `[x]` Familias de facetas modeladas explícitamente
- `[x]` `common_errors` y `trap` forman parte del contrato
- `[-]` La cobertura de integration/boss depende todavía del banco real cargado

## 6. Especificación de ítems

### 6.1 Campos obligatorios

`item_id`, `node_id`, `facet`, `format`, `friction_level`, `difficulty_seed`, `item_role`, `allowed_surfaces`, `cooldown_hours`, `stem`, `correct_answer`, `feedback_short`, `covers_must_know`, `variant_group_id`, `rescue_group_id`, `version`, `updated_at`, `source_refs`

### 6.2 Campos muy recomendables

`node_complexity`, `facet_complexity`, `distractor_similarity`, `prerequisite_depth`, `targets_error_ids`, `common_error_signals`, `options`

### 6.3 Identidad y edición

Mientras conserve el mismo `item_id`, se trata como el mismo ítem.

Cambiar wording no crea un ítem nuevo.

En fases posteriores se permitirá editar texto, opciones, feedback y refs, pero no cambiar libremente el formato estructural del ítem.

### Estado actual del repo

- `[x]` Identidad por `item_id` cerrada
- `[x]` Wording, feedback y refs no resetean progreso
- `[x]` Campos principales modelados
- `[-]` La edición estructurada avanzada sigue siendo futura

## 7. Fricción, dificultad y roles de ítems

### 7.1 Diferencia entre fricción y dificultad

Fricción:
- qué tanto cuesta empezar o responder una interacción

Dificultad:
- qué tan probable es fallar aunque se intente seriamente

La fricción se estima principalmente por formato.

La dificultad se inicializa con `difficulty_seed` y luego se recalibra con desempeño real.

### 7.2 Friction levels

#### Nivel 1

Formatos principales:
- `true_false`
- `multiple_choice`
- `choose_false_statement`
- `matching_simple`
- `fill_one_word`

Uso típico:
- alarma
- notificación
- widget
- gate
- back
- rescates rápidos

#### Nivel 2

Formatos principales:
- `order_steps_short`
- `fill_short_blank`
- `mini_scenario_mcq`
- `connect_concepts`

Uso típico:
- raid normal
- quick flow avanzado

#### Nivel 3

Formatos principales:
- `one_sentence_explanation`
- `compare_A_vs_B`
- `what_happens_if_X`

Uso típico:
- deep mode
- integración media

#### Nivel 4

Formatos principales:
- `full_reconstruction`
- `long_process_explanation`
- `case_without_hints`

Uso típico:
- boss
- integración profunda

### Normalización compatible con el repo actual

Los formatos textuales siguen existiendo a nivel de contenido y de fricción.

Pero en la UI actual:
- no se pide escritura libre
- `fill_one_word`, `fill_short_blank`, `one_sentence_explanation`, `compare_A_vs_B`, `what_happens_if_X`, `full_reconstruction`, `long_process_explanation` y `case_without_hints` se resuelven con `Ver respuesta` y autoevaluación

### 7.3 Roles de ítems

#### core

Contenido principal del nodo.

Entra en `Vaciar tema`:
- sí

#### variant

Otra forma útil de preguntar, no solo fallback.

Entra en `Vaciar tema`:
- sí

#### trap

Detecta una confusión típica real.

Entra en `Vaciar tema`:
- sí

#### integration

Une varias facetas o varios nodos.

Entra en `Vaciar tema`:
- sí

#### boss

Chequeo profundo o funcionamiento completo.

Entra en `Vaciar tema`:
- sí

#### rescue

Ítem de emergencia para bajar fricción tras error.

Entra en `Vaciar tema`:
- no

### Estado actual del repo

- `[x]` Fricción y dificultad diferenciadas
- `[x]` Roles `core/variant/trap/integration/boss/rescue`
- `[x]` `rescue` fuera de `Vaciar tema`
- `[x]` Política sin tipeo libre cerrada

## 8. Superficies del sistema y principio de continuidad

El sistema define desde ahora todas las superficies futuras, aunque el MVP local implementa solo tres.

### Superficies

- `ALARM`
  - implementada en MVP local: no
  - rol: alarma estricta futura
- `NOTIFICATION`
  - implementada en MVP local: sí
  - rol: notificación cognitiva
- `WIDGET`
  - implementada en MVP local: no
  - rol: widget futuro
- `SOCIAL_GATE`
  - implementada en MVP local: sí
  - rol: gate a X/TikTok/Instagram sobre `Tarjetas pendientes`
- `IN_APP_QUICK`
  - implementada en MVP local: sí
  - rol: repaso rápido full-screen
- `IN_APP_DEEP`
  - implementada en MVP local: sí
  - rol: modo profundo / vaciar tema
- `BACK_MICRO`
  - implementada en MVP local: sí
  - rol: micro-pregunta al tocar back

### 8.1 Principio absoluto

La primera respuesta puede ocurrir fuera de la app, pero la continuidad siempre ocurre ya dentro, en pantalla completa.

No hay preview ni home intermedio después del handoff.

### 8.2 Regla UX

- seguir debe ser fácil
- abandonar debe requerir una decisión activa
- feedback arriba, progreso local visible y siguiente pregunta ya cargada abajo

### Estado actual del repo

- `[x]` Superficies futuras definidas
- `[x]` `IN_APP_QUICK`, `IN_APP_DEEP` y `BACK_MICRO` implementadas
- `[-]` Hooks externos parcialmente implementados: notificación y gate social ya existen; widget y alarma siguen pendientes

## 9. Banco simple obligatorio y pools de selección

### 9.1 Requisito por nodo

Todo nodo debe tener al menos 4 ítems aptos para superficies externas.

Distribución mínima sugerida:
- 2 MCQ simples
- 1 true/false o choose_false
- 1 fill-one-word o discriminación A/B

Campos asociados:
- `surface_easy_ready`
- `surface_easy_item_count`

### 9.2 Pools

- `due_easy_pool`: ítems fáciles realmente due
- `derived_easy_pool`: ítems fáciles derivados de nodos due aunque el nodo sea complejo
- `maintenance_pool`: preguntas fáciles de mantenimiento sobre nodos base o estables
- `cooldown_repeat_pool`: preguntas fáciles ya usadas, reutilizadas si venció su cooldown

### 9.3 Política por superficie

- alarma futura:
  - `due_easy → derived_easy → maintenance → cooldown_repeat`
- notificación futura:
  - `due_easy → derived_easy → nada`
- widget futuro:
  - `due_easy → derived_easy → estado neutro`
- gate social vigente:
  - abre/reusa `Tarjetas pendientes` QUICK
  - si no hay deuda real pendiente → no gate
  - no usa fillers gate-only ni tarjetas futuras/no due
- back micro:
  - `easy_current_node → derived_current_node → cooled_repeat_same_context`
- quick in-app:
  - `due_easy → derived_easy → maintenance → cooldown`

### Estado actual del repo

- `[x]` Banco easy y campos base modelados
- `[x]` Quick usa el orden de fallback acordado
- `[ ]` Superficies externas todavía no usan esos pools porque no están implementadas

## 10. Scheduler y prioridad

El scheduler trabaja a nivel nodo, no tarjeta.

El sistema mezcla materias a nivel global, pero no dentro de una microsecuencia corta:
- mezcla global
- cohesión local

### 10.1 Topic packet

Unidad lógica de sesión local.

Contiene:
- tema principal
- superficie
- secuencia de ítems
- rescates
- objetivo de correctas
- contexto temático estable

No cambia de `topicUnitId` ni de superficie una vez creado.

### 10.2 Fórmula de prioridad normalizada del MVP endurecido

`priority = 0.40·dueScore + 0.25·fragilityScore + 0.20·frictionScore + 0.15·abandonmentScore − 0.20·recentTopicPenalty`

### 10.3 Definiciones V1

- `dueScore = clamp(overdueHours / 72.0, 0.0, 1.0)`
- `fragilityScore = 1.0 - memoryScore`
- `frictionScore = frictionUser`
- `abandonmentScore = clamp(recentAbandonsLast14d / 3.0, 0.0, 1.0)`
- `recentTopicPenalty = 1.0` si fue el último topic, `0.5` si fue el penúltimo, `0.0` si no

### 10.4 Reglas de selección

- siempre excluir `archived_candidate` del scheduler y de las estadísticas activas por defecto
- excluir nodos sin ítems válidos para la superficie y modo actual
- excluir rescues salvo cuando la lógica local de sesión los pida
- la urgencia del nodo no endurece automáticamente la entrada: un nodo muy fallado puede reaparecer pronto, pero entrar con un ítem fácil

### Estado actual del repo

- `[x]` Scheduler a nivel nodo
- `[x]` Fórmula de prioridad implementada
- `[x]` `recentTopicPenalty` implementado
- `[x]` `archived_candidate` excluido
- `[-]` Ajuste fino sobre bancos grandes todavía pendiente

## 11. Estados, métricas y actualización por nodo

### 11.1 Escalas continuas

`memoryScore`, `retrievability`, `frictionUser`, `difficultyUser`, `coverageScore`, `errorRate`, `abandonRate` y `formatSuccessRate` viven en `0.0..1.0`.

Todo se clampa antes de persistir.

### 11.2 NodeState mínimo

`nodeId`, `memoryScore`, `stabilityHours`, `retrievability`, `difficultyUser`, `frictionUser`, `coverageScore`, `avgLatencyMs`, `errorRate`, `abandonRate`, `lastReviewedAt`, `nextReviewAt`, `timesSeen`, `timesCorrectFirstTry`, `timesCorrectAfterRescue`, `timesFailed`, `timesAbandoned`, `lastSurfaceUsed`, `lastSessionMode`

### 11.3 Defaults iniciales

- `memoryScore = 0.35`
- `stabilityHours = 24.0`
- `retrievability = 0.50`
- `frictionUser = 0.30`
- `difficultyUser = difficultySeed`
- `coverageScore = 0.0` al principio

### 11.4 Reglas V1 de actualización

- primer acierto limpio: `stabilityHours × 2.0`
- acierto tras rescue: `stabilityHours × 1.25`
- error: `stabilityHours × 0.5`
- abandono: `stabilityHours × 0.35`
- `memoryScore` y `retrievability` se recalculan con clamp usando último resultado y latencia; nunca negativos ni > 1.0
- `frictionUser` sube con latencias altas o abandono y baja con respuestas rápidas correctas
- `difficultyUser` se mueve lentamente hacia el `errorRate` observado, con suavizado exponencial

### 11.5 Estadísticas por formato

Se decidió reemplazar `formatSuccessRatesJson` por una tabla normalizada:

`node_format_stats(nodeId, format, attempts, successes, avgLatencyMs)`

### Estado actual del repo

- `[x]` Escalas continuas cerradas
- `[x]` `stabilityHours` como `Double`
- `[x]` `node_format_stats` como tabla, no JSON en columna
- `[x]` Actualización de métricas en el motor de sesión

## 12. SessionEngine y StudySession

### 12.1 StudySession persistida

`sessionId`, `mode`, `surface`, `packetId`, `topicUnitId`, `currentNodeId`, `currentItemId`, `goalCorrectCount`, `correctCount`, `stepIndex`, `startedAt`, `lastInteractionAt`, `isExitArmed`, `exitArmedUntil`, `currentTopicTitle`

### 12.2 Métodos del SessionEngine

- `startQuickSession()`
- `startDeepSession(unitId)`
- `startDrainTopic(unitId)`
- `submitAnswer(answerPayload)`
- `handleBackPressed()`
- `handleInactivityTimeout()`
- `resumeActiveSessionIfAny()`

### 12.3 Loop local de error

- intento 1: diagnóstico/principal
- intento 2: variante
- intento 3: rescue o microconfirmación
- intento 4+ en quick: solo variantes/rescues fáciles hasta una correcta

### Estado actual del repo

- `[x]` Quick, deep y drain implementados
- `[x]` `active_sessions` persistido
- `[x]` `resumeActiveSessionIfAny()`
- `[x]` Loop principal → variante → rescue cubierto
- `[x]` Si existe una sesión activa real al abrir la app, el arranque vuelve directo a estudio
- `[-]` La recreación exacta de `Activity/process` sigue parcialmente limitada por el entorno de instrumentación MIUI

## 13. Quick flow, deep mode y vaciar tema

### 13.1 Repaso rápido

Flujo full-screen sin intermedios.

Feedback breve arriba, progreso local visible y siguiente pregunta ya cargada.

Reglas:
- primer ítem siempre `friction 1`
- primer ítem nunca `trap`, `integration` ni `boss`
- los primeros 2 ítems nunca pueden ser `boss` ni `integration`
- segundo ítem puede ser `friction 1` o `2` solo si el primero fue correcto y rápido
- subir a `friction 3` solo con engagement real y condiciones del nodo

### 13.2 Modo profundo

Selecciona:
- cobertura
- facetas flojas
- traps relevantes
- integration
- bosses

Permite sesiones más largas y densas.

### 13.3 Vaciar tema

Recorre todo el banco normal del tema.

Incluye:
- `core`
- `variant`
- `trap`
- `integration`
- `boss`

Excluye:
- `rescue`

### Estado actual del repo

- `[x]` Quick full-screen operativo
- `[x]` Deep y drain operativos
- `[x]` `Vaciar tema` excluye `rescue`
- `[-]` Validación de calidad percibida con contenido grande todavía pendiente

## 14. Botón atrás, abandono y restauración exacta

### 14.1 Back micro

- primer back muestra micro-pregunta del mismo contexto
- si responde, `exit_armed = true` y `exit_armed_until = now + 8 s`
- si toca back dentro de esos 8 s, sale
- si expira la ventana, el próximo back vuelve a disparar micro-pregunta

### 14.2 Abandono

- registrar abandono por back sin responder
- registrar abandono por cierre desde pregunta sin responder
- registrar abandono por 5 minutos exactos sin respuesta con la pantalla activa
- no registrar abandono por pérdida breve o ambigua de foreground

### 14.3 Restauración exacta

Al recrearse proceso/activity, restaurar exactamente:
- `packet`
- colas
- `goalCorrectCount`
- paso actual
- `isExitArmed`
- `exitArmedUntil`
- contexto temático

### Estado actual del repo

- `[x]` `back_exit` de 8 segundos
- `[x]` abandono por back y timeout
- `[x]` salida deliberada preserva sesión incompleta continuable
- `[x]` relanzar la app con una sesión activa visible vuelve directo al estudio correcto
- `[-]` la restauración exacta bajo recreación de `Activity/process` todavía no está totalmente cerrada de punta a punta en UI instrumentada

## 15. Arquitectura técnica concreta del MVP local Android

### 15.1 Alcance

App Android local, un solo módulo `app`, Compose + Hilt + Room + DataStore + Navigation + SAF, sin hooks externos ni sync en esta fase.

Entrega:
- repaso rápido
- modo profundo
- vaciar tema

### 15.2 Estructura de paquetes

- `app.di`
- `app.model.content`
- `app.model.state`
- `app.model.event`
- `app.model.json`
- `app.data.local.db`
- `app.data.local.dao`
- `app.data.local.store`
- `app.data.importing`
- `app.domain.scheduler`
- `app.domain.session`
- `app.domain.repository`
- `app.ui.home`
- `app.ui.content`
- `app.ui.study.quick`
- `app.ui.study.deep`
- `app.ui.common`

### 15.3 Stack elegido

- Kotlin + Jetpack Compose
- `MainActivity` única + `NavHost`
- MVVM con `UiState + UiEffect` y `StateFlow`
- Hilt
- Room
- DataStore
- `kotlinx.serialization`
- Storage Access Framework para importación JSON
- StrictMode activo en debug desde el primer día

### Normalización compatible con el repo actual

La intención de DI separada se conserva.

El wiring activo del repo hoy corre principalmente por `AppContainer` y factories manuales de ViewModel.

Los artefactos Hilt siguen presentes en el build y en módulos auxiliares, pero la semántica del producto no depende hoy de Hilt como camino principal en runtime.

### Estado actual del repo

- `[x]` Single-module Android app
- `[x]` `MainActivity` única + `NavHost`
- `[x]` Room + DataStore + serialization + SAF
- `[x]` StrictMode debug
- `[-]` La capa DI está funcionalmente cerrada, pero la alineación total entre intención Hilt y wiring activo todavía puede refinarse

## 16. Persistencia local

### 16.1 Tablas Room

#### Contenido

- `courses`
- `units`
- `outcomes`
- `nodes`
- `items`

#### Estado agregado

- `node_states`
- `node_format_stats`
- `active_sessions`

#### Eventos

- `review_events`
- `session_events`
- `abandon_events`
- `import_events`

### 16.2 DataStore

Solo para settings globales y flags livianos.

No guardar progreso ni eventos en DataStore.

### 16.3 Reglas de persistencia

- `nodes.archived_candidate = true` excluye el nodo del scheduler por defecto
- `nodes.is_seeded` e `items.is_seeded` solo para trazabilidad
- exportar schema Room desde el primer commit

### Estado actual del repo

- `[x]` Persistencia local cerrada para MVP
- `[x]` DataStore solo para settings
- `[x]` Schema Room exportado

## 17. Entidades y DAOs del MVP

### Entidades principales acordadas

- `CourseEntity(courseId PK, title, description, version, updatedAt)`
- `UnitEntity(unitId PK, courseId FK, title, description, orderIndex, version, updatedAt)`
- `OutcomeEntity(outcomeId PK, unitId FK, title, description, version, updatedAt)`
- `NodeEntity(nodeId PK, courseId, unitId, outcomeIdsJson, title, coreClaim, type, weightExam, prerequisitesJson, facetsJson, mustKnowJson, commonErrorsJson, minimumMasteryDefinition, surfaceEasyReady, surfaceEasyItemCount, sourceRefsJson, version, updatedAt, archivedCandidate)`
- `ItemEntity(itemId PK, nodeId FK, facet, format, frictionLevel, difficultySeed, itemRole, allowedSurfacesJson, cooldownHours, stem, correctAnswer, feedbackShort, coversMustKnow, variantGroupId, rescueGroupId, nodeComplexity, facetComplexity, distractorSimilarity, prerequisiteDepth, targetsErrorIdsJson, commonErrorSignalsJson, optionsJson, version, updatedAt, sourceRefsJson)`
- `NodeStateEntity(nodeId PK, …campos agregados…)`
- `NodeFormatStatsEntity(nodeId + format, attempts, successes, avgLatencyMs)`
- `ActiveSessionEntity(sessionId PK, mode, surface, packetId, topicUnitId, currentNodeId, currentItemId, queueJson, rescueQueueJson, goalCorrectCount, correctCount, stepIndex, startedAt, lastInteractionAt, isExitArmed, exitArmedUntil)`
- `ReviewEventEntity(eventId PK, sessionId, nodeId, itemId, answerOutcome, latencyMs, surface, sessionMode, attemptIndex, occurredAt)`
- `SessionEventEntity(eventId PK, sessionId, packetId, type, payloadJson, occurredAt)`
- `AbandonEventEntity(eventId PK, sessionId, nodeId, itemId, reason, occurredAt)`
- `ImportEventEntity(eventId PK, packageId, schemaVersion, seedVersion, contentHash, result, createdAt)`

### 17.1 DAOs principales

- `ContentDao`: upserts, fetch tree, mark archived candidates, list schedulable nodes, get items by node and friction
- `NodeStateDao`: get/upsert/update stats, fetch due nodes, reset node progress
- `SessionDao`: create/update/clear active session
- `EventDao`: insert review/session/abandon/import, query recent events for scoring
- `ImportDao`: lookup package/version/hash para seed idempotente

### Estado actual del repo

- `[x]` Entidades base implementadas
- `[x]` DAOs principales implementados
- `[-]` Algunas diferencias menores de forma concreta respecto del texto fuente pueden existir, pero sin cambiar el contrato funcional

## 18. Importación JSON y seed demo idempotente

### 18.1 Validación en dos etapas

- `StructuralValidation`: parse, obligatorios, enums, unicidad, referencias, `allowed_surfaces`, `outcome_ids`, coherencia mínima
- `AuthoringValidation`: warnings por pocas facetas, poca cobertura easy, pocos traps, pocos integration/boss, distribución de fricción pobre

### 18.2 Reglas de upsert

- mismo `node_id` o `item_id` actualiza contenido sin resetear progreso
- cambios de wording, opciones, feedback o refs no crean identidad nueva
- nodos faltantes en una reimportación se marcan `archived_candidate = true`

### 18.3 Seed demo

- `packageId` fijo, `schemaVersion`, `seedVersion` y `contentHash`
- si la DB está vacía: sembrar
- si ya existe el mismo `packageId + seedVersion`: no resembrar
- si cambia `seedVersion`: hacer upsert idempotente
- si existe contenido real importado por el usuario, el demo no se prioriza en Home y solo entra al scheduler si sigue activo y no fue archivado

### Estado actual del repo

- `[x]` Import en dos etapas
- `[x]` Upsert por ID
- `[x]` Seed demo idempotente
- `[x]` Demo no prioritario frente a contenido real
- `[x]` Búsqueda local sobre contenido cargado
- `[x]` Filtro por activos vs archivados candidatos
- `[x]` Resumen visible de volumen cargado para revisar árboles grandes
- `[x]` Preflight manual antes de importar con conteo de volumen y solapamiento por IDs
- `[x]` Preflight de import reconoce aliases de schema seguros para `AuthoringDraftPackage`/`ContentPackageDto`, rechaza paquetes planos de preguntas y muestra diagnósticos estructurados expandibles
- `[x]` Import por archivo de `content_package` soporta `importMode: "additive"` para agregar unidades/preguntas sin archivar nodos hermanos ausentes
- `[x]` Importación por texto editable revisado: único input inicial de texto fuente, extractor local conservador, draft editable por campo visible con alta/baja de preguntas, compilador determinístico multiunidad a `ContentPackageDto` y validación con perfil `REVIEWED_QUESTION_BANK`
- `[-]` Revisión de paquetes grandes y diffs de import todavía necesita más robustez visible

## 19. Repositorios e interfaces estables

- `ContentRepository: importContentPackage(uriOrJson), seedDemoIfNeeded(), getCourseTree(), getNodeDetails(nodeId), setArchivedCandidate(nodeId, archived)`
- `ProgressRepository: getNodeState(nodeId), upsertNodeState(nodeState), resetNodeProgress(nodeId), getDueNodeCandidates(surface, mode)`
- `SessionRepository: createSession(packet), getActiveSession(), updateSession(session), closeSession(sessionId)`
- `EventRepository: recordReviewEvent(...), recordSessionEvent(...), recordAbandonEvent(...), recordImportEvent(...)`
- `SettingsRepository: observeSettings(), updateSettings(...)`
- `ImportValidator`
- `SchedulerService`
- `SessionEngine`

### Estado actual del repo

- `[x]` Interfaces y repositorios base presentes
- `[x]` SessionEngine y SchedulerService operativos

## 20. Contratos JSON y paquetes de datos

### 20.1 Paquetes conceptuales

- `content_package`: importar/exportar contenido
- `user_state_package`: progreso y configuración de la cuenta
- `sync_snapshot`: snapshot coherente para futura sync

### 20.2 Regla futura ya cerrada

Contenido y progreso van separados.

Mismo `node_id` o `item_id` preserva identidad y progreso.

La sync futura será manual con botón `subir cambios`.

### Estado actual del repo

- `[x]` `content_package` ya es contrato real
- `[x]` `content_package` acepta metadata raíz `importMode: "additive"` en preflight/import por archivo; esa metadata no se persiste dentro del `ContentPackageDto`
- `[x]` `sync_snapshot` ya es contrato y flujo real de export/import local
- `[-]` `user_state_package` ya existe como contrato embebido dentro de `sync_snapshot`, pero todavía no se expone como flujo standalone separado

## 21. Hooks externos y roadmap funcional completo

El MVP local ya implementa notificación cognitiva y gate social. Widget y alarma siguen en roadmap futuro.

### Componentes futuros

- notificación cognitiva
  - estado: implementada en MVP local
  - reglas acordadas: 1 por día si hay pending apto, expira a 24 h, si no hay easy/derived no manda, handoff inmediato a full-screen
- widget
  - estado: fase futura
  - reglas acordadas: una sola pregunta, estado neutro si no hay apta, tocar y salir sin responder cuenta como abandono
- alarma
  - estado: fase futura
  - reglas acordadas: full-screen, superpuesta, no se puede cerrar hasta N correctas, botones de volumen solo silencian 30 s, sin apagado de emergencia, 1..10 correctas
- gate social
  - estado: implementado en MVP local
  - reglas acordadas vigentes: X/TikTok/Instagram, frecuencia diaria configurable, abre/reusa `Tarjetas pendientes`, no bloquea si no hay deuda real pendiente, no inventa fillers, desbloquea solo cuando la deuda pendiente queda resuelta o con comodín foreground-only
- web + sync + authoring
  - estado: fase futura
  - reglas acordadas: web sobria en Svelte, authoring por JSON + editor mínimo, Supabase, sync manual

### Estado actual del repo

- `[-]` Notificación cognitiva y gate social implementados; widget y alarma pendientes

## 22. Roadmap por fases y versiones

### Fase 0 — Bootstrap

Objetivo:
- preparar el repo y los cimientos

Entregables:
- Gradle
- Compose
- Hilt
- Room
- DataStore
- Navigation
- serialization
- StrictMode
- testing base

Estado actual:
- `[x]`

### Fase 1 — Contrato de datos e import

Objetivo:
- poder importar contenido válido sin romper identidad

Entregables:
- DTOs
- validator en dos etapas
- SAF import
- seed demo idempotente

Estado actual:
- `[x]`

### Fase 2 — Persistencia local

Objetivo:
- tener contenido, progreso y eventos confiables

Entregables:
- entidades Room
- DAOs
- repositorios
- `node_format_stats`
- `active_sessions`

Estado actual:
- `[x]`

### Fase 3 — Scheduler y SessionEngine

Objetivo:
- hacer que el núcleo de estudio funcione de verdad

Entregables:
- `priority`
- `topic_packet`
- `quick/deep/drain`
- scoring
- abandono
- restauración

Estado actual:
- `[x]`

### Fase 4 — UI full-screen

Objetivo:
- entregar el flujo in-app usable y continuo

Entregables:
- Home
- Content
- Quick
- Deep
- Vaciar tema
- feedback/progreso
- back micro

Estado actual:
- `[-]`

### Fase 5 — Hardening del MVP local

Objetivo:
- dejar el MVP local sólido

Entregables:
- fixtures reales
- migraciones
- reimport
- restauración interrumpida
- aceptación

Estado actual:
- `[-]`

### Fase 6 — Hooks externos

Objetivo:
- completar la V1 Android acordada

Entregables:
- notificación
- widget
- alarma
- gate social

Estado actual:
- `[-]`

### Fase 7 — Web + sync

Objetivo:
- authoring y estudio manual desde PC

Entregables:
- Svelte
- import/export JSON
- editor mínimo
- Supabase
- sync manual

Estado actual:
- `[ ]`

### Fase 8 — Evolución futura

Objetivo:
- escalar sin romper diseño base

Entregables:
- más tuning
- multiusuario futuro si se decide
- analítica
- mejoras posteriores

Estado actual:
- `[ ]`

### 22.1 Regla de implementación

La arquitectura y el plan de producto completos están cerrados, pero la secuencia de implementación correcta sigue siendo incremental:
- primero el core local in-app
- después hooks externos
- después nube/web

Esto no cambia el alcance final; solo reduce el riesgo de errores.

## 23. Secuencia correcta de implementación del MVP local

1. Bootstrap Android: proyecto, toolchain, Hilt, Room, DataStore, Navigation, serialization, StrictMode, export de schema Room y base de tests.
2. DTOs, `ImportValidator` en dos etapas, importador SAF y seed demo idempotente.
3. Entidades Room, DAOs, repositorios, `node_format_stats`, `active_sessions` y reset manual por nodo.
4. `SchedulerService`, `SessionEngine`, scoring normalizado, quick/deep/drain, reglas de abandono y restauración.
5. Pantallas Compose, ViewModels, full-screen flow, feedback breve, progreso local y back intercept.
6. Hardening con fixtures reales, tests de migración, tests de sesión interrumpida y comportamiento de reimportación.

### Estado actual del repo

- `[x]` Pasos 1, 2 y 3
- `[-]` Paso 4 cerrado en núcleo, pero no completo en restauración visible
- `[-]` Paso 5 operativo, pero todavía con huecos de robustez visible
- `[-]` Paso 6 parcialmente avanzado

## 24. Testing y criterios de aceptación

### 24.1 Unit tests obligatorios

- validación estructural fatal vs warnings semánticos
- seed demo idempotente y no prioritario frente a contenido real
- upsert por ID y preservación de progreso ante cambios textuales
- `archived_candidate` excluido de quick, deep y drain
- cálculo de prioridad y `recentTopicPenalty` sobre últimos 2 topics
- `goalCorrectCount` inmutable durante la sesión
- quick flow arranca en fricción 1 y los primeros 2 ítems no son boss/integration
- loop principal → variante → rescue
- `back_exit` dentro y fuera de la ventana de 8 s
- `Vaciar tema` incluye trap/integration/boss y excluye rescue

### 24.2 Instrumentados y Compose

- primer arranque con seed
- import por SAF con errores fatales y con warnings
- quick flow full-screen sin volver al home
- recreación de `Activity` en quick, deep y drain con restauración exacta
- reset manual por nodo con confirmación
- feedback y progreso visibles

### 24.3 Tests técnicos

- schema Room exportado desde v1
- `MigrationTestHelper` listo desde la primera migración
- StrictMode activo en debug para detectar trabajo indebido en main thread

### 24.4 Escenarios de aceptación

- un `topic_packet` no cambia de tema a mitad de sesión
- quick flow se mantiene liviano al inicio
- modo profundo incluye `integration` y `boss`
- reimportar contenido conserva identidad y progreso
- reset manual por nodo solo afecta progreso de ese nodo
- abandono por timeout/back/cierre queda registrado de forma consistente con el estado agregado

### Estado actual del repo

- `[x]` Cobertura fuerte de unit tests en import, scheduler y session engine
- `[x]` Tests Android de persistencia de sesión y deep/drain sin depender de Activity visible
- `[-]` Tests UI/instrumentados completos limitados por MIUI en este device
- `[ ]` Migration tests reales cuando cambie schema

## 25. Riesgos conocidos y cómo evitarlos

### IDs inestables

Qué se rompe:
- progreso roto o duplicado

Mitigación acordada:
- upsert por ID
- IDs explícitos
- no derivar identidad del wording

### Demo seed invasivo

Qué se rompe:
- contamina el uso real

Mitigación acordada:
- seed idempotente y no prioritario frente a contenido real

### JSON roto o mediocre

Qué se rompe:
- imports frágiles o contenido pobre

Mitigación acordada:
- validator en dos etapas, fatal vs warning

### Persistencia parcial

Qué se rompe:
- desalineación entre eventos y estado

Mitigación acordada:
- transacción única evento + estado agregado

### Quick flow pesado

Qué se rompe:
- baja adherencia

Mitigación acordada:
- primer ítem `friction 1`
- primeros 2 ítems sin `boss/integration`

### Sesión interrumpida

Qué se rompe:
- pérdida del hilo

Mitigación acordada:
- restauración exacta de packet, colas, step y back state

### Contenido faltante en reimport

Qué se rompe:
- borrado accidental

Mitigación acordada:
- `archived_candidate` en vez de delete

### Estado actual del repo

- `[x]` Riesgos estructurales principales ya mitigados en contratos
- `[-]` Contenido grande y recreación exacta de `Activity/process` siguen siendo los riesgos funcionales más claros

## 26. Fuera de alcance del MVP local

- hooks externos restantes: alarma y widget
- sync manual con Supabase
- web Svelte de authoring/estudio manual
- editor manual de contenido rico dentro del MVP local
- auth, multiusuario, cohortes, analítica comparativa
- sistema de impugnación, rival, engine de debate o cualquier derivado

### Normalización compatible con el repo actual

Lo que sí queda dentro del MVP local actual:
- import manual de JSON crudo desde el dispositivo

Lo que sigue fuera:
- editor visual/estructurado rico de contenido

## 27. Lo que queda explícitamente registrado para no olvidarlo después

- la app final sí tendrá hooks externos: alarma, notificación, widget y gate social
- la app final sí tendrá web sobria para authoring y estudio manual desde PC
- la app final sí tendrá sync manual a nube con Supabase y separación clara contenido/progreso
- la lógica completa ya está cerrada y este documento la consolida para futuras versiones
- el MVP local no reemplaza la visión final; solo es la secuencia correcta para implementarla con menos errores

## 28. Checklist de salida del MVP local

- `[x]` Se puede importar un `content_package` real desde archivo.
- `[x]` Se carga un seed demo idempotente al primer arranque si la base está vacía.
- `[x]` Repaso rápido funciona end-to-end, guarda progreso y no vuelve al home al responder.
- `[x]` Modo profundo funciona con coverage + bosses + integration.
- `[x]` Vaciar tema recorre todo el banco normal del tema excluyendo rescues.
- `[x]` Back micro funciona con ventana de 8 segundos.
- `[x]` Abandono se registra correctamente por back, cierre y timeout.
- `[x]` Reimportación con mismos IDs conserva progreso.
- `[x]` Reset manual por nodo funciona con confirmación.
- `[-]` `Activity/process recreation` restaura la sesión exacta.
- `[-]` Todos los tests críticos pasan.

### Lectura correcta del estado actual

La salida del MVP local está muy avanzada, pero todavía no se puede marcar como completamente cerrada mientras sigan abiertos:
- validación más robusta en contenido grande
- recreación exacta de `Activity/process` en todos los entornos relevantes
- ejecución confiable de ciertos flows UI instrumentados en un device menos intrusivo que MIUI

## 29. Próximo paso técnico después de este roadmap

Con este documento consolidado, el siguiente paso ya no es seguir redefiniendo producto.

El paso correcto es seguir ejecutando en este orden:

1. endurecer gestión de contenido/import para bancos grandes
2. agregar snapshot local exportable/importable
3. recién después, avanzar a hooks externos
4. después, web + sync

## 30. Checklist operativo vivo

Esta sección existe para poder consultar rápido el estado real sin releer todo el documento.

### 30.1 Núcleo ya cerrado

- `[x]` Identidad estable por `node_id` y `item_id`
- `[x]` JSON import con upsert y validación en dos etapas
- `[x]` Seed demo idempotente
- `[x]` `archived_candidate` en vez de delete
- `[x]` Scheduler por nodo
- `[x]` Quick / Deep / Drain operativos
- `[x]` `Vaciar tema` excluye rescues
- `[x]` `back_exit` de 8 s
- `[x]` abandono por back y timeout
- `[x]` no hay tipeo libre en estudio
- `[x]` sesión activa no se reemplaza en silencio

### 30.2 Parcial pero en marcha

- `[-]` manejo serio de contenido grande
- `[-]` hardening final del MVP local
- `[-]` validación E2E completa en un entorno Android menos hostil que MIUI

### 30.2 bis Ya cerrado recientemente

- `[x]` relanzar la app con una sesión activa real entra directo al estudio correspondiente
- `[x]` búsqueda, filtro por archivado y resumen básico para revisar árboles de contenido grandes
- `[x]` preflight manual de import con volumen del paquete y qué parte actualiza por ID
- `[x]` preflight manual de import ahora también anticipa qué nodos activos podrían pasar a `archived_candidate` en una reimportación
- `[x]` preflight manual de import ahora desglosa el diff resumido por curso para paquetes grandes
- `[x]` preflight de import ahora acepta aliases seguros (`authoring_draft`, `QuestionDraftPackage`, `content_package`) solo con forma contractual correcta, normaliza enums mecánicamente y despliega errores con path/esperado/recibido/hint
- `[x]` import por archivo de paquetes finales puede ser aditivo explícito, preservando hermanos existentes del curso sin marcarlos `archived_candidate`
- `[x]` importador de texto editable revisado compila un único texto fuente y un draft revisable/editable por campo visible con alta/baja de preguntas a banco de preguntas multiunidad sin generar `RESCUE` ni meter perfil dentro de `ContentPackageDto`
- `[x]` revisión operativa de `archived_candidate` con selección y restauración masiva de archivados visibles
- `[x]` export/import local de `sync_snapshot` preservando contenido, progreso, `formatStats`, archivados candidatos, settings y sesión activa

### 30.3 Pendiente del alcance total

- `[-]` `user_state_package` standalone todavía no expuesto como flujo separado
- `[-]` notificación cognitiva
- `[x]` gate social respaldado por `Tarjetas pendientes` sin fillers gate-only
- `[ ]` widget
- `[ ]` alarma
- `[ ]` web sobria (cuando se llegue a esta parte hay que discutir bien el stack)
- `[ ]` sync manual con Supabase
- `[ ]` continuidad cross-device

### 30.4 Regla de mantenimiento

Cada vez que un punto de esta sección cambie de estado:
- se actualiza este archivo en la misma tarea
- se mantiene consistencia con [docs/project-context-fixed.md](/home/usuario/CascadeProjects/App%20de%20Estudio/docs/project-context-fixed.md)
- no se cambia un estado a `[x]` si todavía quedan huecos funcionales relevantes en ese frente
