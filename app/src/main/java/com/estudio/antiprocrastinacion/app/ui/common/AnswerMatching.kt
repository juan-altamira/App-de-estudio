package com.estudio.antiprocrastinacion.app.ui.common

/**
 * Shared true/false answer matching used by every study surface (in-app quick/deep study and the
 * social gate overlay). Keeping a single implementation avoids the two surfaces drifting apart and
 * grading the same answer differently.
 *
 * Matching is tolerant to casing, surrounding whitespace, accents and common spellings
 * (`verdadero/verdad/true/v`, `falso/false/f`). For any other expected value it falls back to an
 * exact normalized comparison.
 */
fun isTrueFalseMatch(
    input: String,
    expected: String,
): Boolean {
    val normalizedInput = normalizeAnswer(input)
    val normalizedExpected = normalizeAnswer(expected)
    val truthy = setOf("verdadero", "verdad", "true", "v")
    val falsy = setOf("falso", "false", "f")

    return when {
        normalizedExpected in truthy -> normalizedInput in truthy
        normalizedExpected in falsy -> normalizedInput in falsy
        else -> normalizedInput == normalizedExpected
    }
}

private fun normalizeAnswer(value: String): String =
    value
        .trim()
        .lowercase()
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
