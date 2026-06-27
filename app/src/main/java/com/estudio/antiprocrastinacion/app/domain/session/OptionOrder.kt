package com.estudio.antiprocrastinacion.app.domain.session

import com.estudio.antiprocrastinacion.app.model.content.ItemOption
import kotlin.random.Random

/**
 * Baraja las opciones de una tarjeta para UNA presentación, de forma determinística, para que el
 * usuario no memorice la POSICIÓN de la respuesta correcta y tenga que razonarla.
 *
 * Seguridad: la corrección NO depende del orden. Cada [ItemOption] lleva su propio `isCorrect`, así
 * que reordenar la lista es transparente para la calificación, el SR y el log (se guarda el texto
 * elegido, no el índice). Solo se reordena la copia de presentación del prompt; el `Item` guardado
 * en el repositorio queda intacto.
 *
 * Determinismo: la semilla combina (sessionId, itemId, attemptIndex). Con eso:
 *  - dentro de una misma presentación el orden es ESTABLE (al volver de segundo plano o al mostrar
 *    el feedback no se reconstruye con otra semilla) → las opciones no "saltan";
 *  - entre presentaciones varía: otra review usa otro `sessionId`; si se falla la tarjeta y reaparece,
 *    `attemptIndex` sube → la respuesta correcta cae en otra posición.
 *
 * Solo aplica a opción múltiple (≥2 opciones). Verdadero/Falso y respuesta abierta no usan esta
 * lista; el guarda `size < 2` deja la lista intacta.
 */
object OptionOrder {
    fun shuffledForPresentation(
        options: List<ItemOption>,
        sessionId: String,
        itemId: String,
        attemptIndex: Int,
    ): List<ItemOption> {
        if (options.size < 2) return options
        return options.shuffled(Random(seedFor(sessionId, itemId, attemptIndex)))
    }

    private fun seedFor(
        sessionId: String,
        itemId: String,
        attemptIndex: Int,
    ): Long {
        var hash = 1_125_899_906_842_597L // primo grande como base estable
        sessionId.forEach { hash = 31 * hash + it.code }
        hash = 31 * hash + FIELD_SEPARATOR // evita que (a|bc) colisione con (ab|c)
        itemId.forEach { hash = 31 * hash + it.code }
        hash = 31 * hash + attemptIndex
        return hash
    }

    private const val FIELD_SEPARATOR = 0x9E3779B1L
}
