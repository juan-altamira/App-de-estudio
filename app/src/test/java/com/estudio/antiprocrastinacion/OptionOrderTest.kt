package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.domain.session.OptionOrder
import com.estudio.antiprocrastinacion.app.model.content.ItemOption
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OptionOrderTest {
    private fun sampleOptions(): List<ItemOption> =
        listOf(
            ItemOption(id = "a", text = "Opción A", isCorrect = false),
            ItemOption(id = "b", text = "Opción B", isCorrect = true),
            ItemOption(id = "c", text = "Opción C", isCorrect = false),
            ItemOption(id = "d", text = "Opción D", isCorrect = false),
            ItemOption(id = "e", text = "Opción E", isCorrect = false),
        )

    private fun List<ItemOption>.ids(): List<String> = map { it.id }

    @Test
    fun `same seed yields the same order (stable within a presentation)`() {
        val first = OptionOrder.shuffledForPresentation(sampleOptions(), "sess-1", "item-1", 1)
        val second = OptionOrder.shuffledForPresentation(sampleOptions(), "sess-1", "item-1", 1)

        assertThat(first.ids()).isEqualTo(second.ids())
    }

    @Test
    fun `shuffle preserves the same options and their correctness flags`() {
        val original = sampleOptions()
        val shuffled = OptionOrder.shuffledForPresentation(original, "sess-1", "item-1", 1)

        // Mismo conjunto de opciones (sin importar el orden) y mismo tamaño.
        assertThat(shuffled).containsExactlyElementsIn(original)
        // La correcta sigue siendo exactamente una y sigue marcada correcta.
        assertThat(shuffled.filter { it.isCorrect }.map { it.id }).containsExactly("b")
    }

    @Test
    fun `order varies across attempt index (re-showing reshuffles)`() {
        val orders =
            (1..12).map { attempt ->
                OptionOrder.shuffledForPresentation(sampleOptions(), "sess-1", "item-1", attempt).ids()
            }
        // Determinístico, pero NO todas las presentaciones quedan igual.
        assertThat(orders.toSet().size).isGreaterThan(1)
    }

    @Test
    fun `order varies across sessions (different review = different order)`() {
        val orders =
            (1..12).map { s ->
                OptionOrder.shuffledForPresentation(sampleOptions(), "sess-$s", "item-1", 1).ids()
            }
        assertThat(orders.toSet().size).isGreaterThan(1)
    }

    @Test
    fun `lists with fewer than two options are returned unchanged`() {
        val single = listOf(ItemOption(id = "a", text = "Sola", isCorrect = true))
        assertThat(OptionOrder.shuffledForPresentation(single, "s", "i", 3)).isEqualTo(single)
        assertThat(OptionOrder.shuffledForPresentation(emptyList(), "s", "i", 3)).isEmpty()
    }
}
