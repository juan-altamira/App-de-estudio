package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.ui.common.isTrueFalseMatch
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnswerMatchingTest {
    @Test
    fun `matches compiled true and false answers`() {
        assertThat(isTrueFalseMatch("Verdadero", "Verdadero")).isTrue()
        assertThat(isTrueFalseMatch("Falso", "Falso")).isTrue()
    }

    @Test
    fun `rejects the opposite answer`() {
        assertThat(isTrueFalseMatch("Verdadero", "Falso")).isFalse()
        assertThat(isTrueFalseMatch("Falso", "Verdadero")).isFalse()
    }

    @Test
    fun `is tolerant to casing whitespace accents and spellings`() {
        assertThat(isTrueFalseMatch("  verdadero ", "VERDADERO")).isTrue()
        assertThat(isTrueFalseMatch("v", "Verdadero")).isTrue()
        assertThat(isTrueFalseMatch("true", "Verdadero")).isTrue()
        assertThat(isTrueFalseMatch("f", "Falso")).isTrue()
        assertThat(isTrueFalseMatch("false", "Falso")).isTrue()
    }

    @Test
    fun `falls back to exact normalized comparison for non true-false answers`() {
        assertThat(isTrueFalseMatch("parís", "PARIS")).isTrue()
        assertThat(isTrueFalseMatch("Londres", "Paris")).isFalse()
    }
}
