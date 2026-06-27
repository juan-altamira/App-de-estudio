package com.estudio.antiprocrastinacion.app.data.importing

import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.ItemRole
import com.estudio.antiprocrastinacion.app.model.content.Surface

object ContentImportRules {
    val strictFrictionOneFormats: Set<ItemFormat> =
        setOf(
            ItemFormat.TRUE_FALSE,
            ItemFormat.MULTIPLE_CHOICE,
            ItemFormat.CHOOSE_FALSE_STATEMENT,
            ItemFormat.MATCHING_SIMPLE,
        )

    val choiceFormats: Set<ItemFormat> =
        setOf(
            ItemFormat.MULTIPLE_CHOICE,
            ItemFormat.CHOOSE_FALSE_STATEMENT,
            ItemFormat.MATCHING_SIMPLE,
            ItemFormat.MINI_SCENARIO_MCQ,
        )

    val openAnswerFormats: Set<ItemFormat> =
        setOf(
            ItemFormat.FILL_ONE_WORD,
            ItemFormat.ORDER_STEPS_SHORT,
            ItemFormat.FILL_SHORT_BLANK,
            ItemFormat.CONNECT_CONCEPTS,
            ItemFormat.ONE_SENTENCE_EXPLANATION,
            ItemFormat.COMPARE_A_VS_B,
            ItemFormat.WHAT_HAPPENS_IF_X,
            ItemFormat.FULL_RECONSTRUCTION,
            ItemFormat.LONG_PROCESS_EXPLANATION,
            ItemFormat.CASE_WITHOUT_HINTS,
        )

    fun frictionFor(format: ItemFormat): Int =
        when (format) {
            ItemFormat.TRUE_FALSE,
            ItemFormat.MULTIPLE_CHOICE,
            ItemFormat.CHOOSE_FALSE_STATEMENT,
            ItemFormat.MATCHING_SIMPLE,
            ItemFormat.FILL_ONE_WORD
            -> 1
            ItemFormat.ORDER_STEPS_SHORT,
            ItemFormat.FILL_SHORT_BLANK,
            ItemFormat.MINI_SCENARIO_MCQ,
            ItemFormat.CONNECT_CONCEPTS
            -> 2
            ItemFormat.ONE_SENTENCE_EXPLANATION,
            ItemFormat.COMPARE_A_VS_B,
            ItemFormat.WHAT_HAPPENS_IF_X
            -> 3
            ItemFormat.FULL_RECONSTRUCTION,
            ItemFormat.LONG_PROCESS_EXPLANATION,
            ItemFormat.CASE_WITHOUT_HINTS
            -> 4
        }

    fun isStrictFrictionOne(format: ItemFormat, frictionLevel: Int): Boolean =
        frictionLevel == 1 && format in strictFrictionOneFormats

    fun allowedSurfacesFor(
        format: ItemFormat,
        role: ItemRole,
    ): List<Surface> {
        val frictionLevel = frictionFor(format)
        val strictFrictionOne = isStrictFrictionOne(format, frictionLevel)
        return when {
            role == ItemRole.RESCUE && strictFrictionOne ->
                listOf(Surface.IN_APP_QUICK, Surface.SOCIAL_GATE, Surface.BACK_MICRO)
            role == ItemRole.RESCUE ->
                listOf(Surface.IN_APP_QUICK)
            role == ItemRole.INTEGRATION || role == ItemRole.BOSS ->
                listOf(Surface.IN_APP_DEEP)
            strictFrictionOne ->
                listOf(
                    Surface.IN_APP_QUICK,
                    Surface.IN_APP_DEEP,
                    Surface.BACK_MICRO,
                    Surface.ALARM,
                    Surface.NOTIFICATION,
                    Surface.WIDGET,
                    Surface.SOCIAL_GATE,
                )
            // CORE/VARIANT open-recall content (one-sentence, compare, reconstruction…) belongs in the
            // daily pending review behind a low-friction anzuelo, not only in deep study. It stays out
            // of the strict external surfaces (notification/social-gate/back-micro) because those must
            // remain auto-gradable; the scheduler gates entry to those by isStrictFrictionOne anyway.
            else ->
                listOf(Surface.IN_APP_QUICK, Surface.IN_APP_DEEP)
        }
    }

    /**
     * Surfaces are always derived from (format, role); they are never hand-authored. This returns the
     * corrected surface list when a persisted item drifted from the current derivation (e.g. content
     * compiled before open-recall cards were allowed into the daily review), or null when it already
     * matches. Compared as sets so a mere ordering difference is not treated as drift.
     */
    fun repairedSurfacesOrNull(
        format: ItemFormat,
        role: ItemRole,
        current: List<Surface>,
    ): List<Surface>? {
        val derived = allowedSurfacesFor(format, role)
        return if (derived.toSet() != current.toSet()) derived else null
    }

    fun countsAsRealSurfaceEasy(
        format: ItemFormat,
        role: ItemRole,
        frictionLevel: Int,
        allowedSurfaces: List<Surface>,
    ): Boolean =
        role != ItemRole.RESCUE &&
            isStrictFrictionOne(format, frictionLevel) &&
            allowedSurfaces.any {
                it in setOf(
                    Surface.ALARM,
                    Surface.NOTIFICATION,
                    Surface.WIDGET,
                    Surface.SOCIAL_GATE,
                    Surface.IN_APP_QUICK,
                    Surface.BACK_MICRO,
                )
            }

    fun defaultCooldownHoursFor(
        format: ItemFormat,
        role: ItemRole,
    ): Double =
        when {
            role == ItemRole.RESCUE -> 1.0
            frictionFor(format) == 1 -> 4.0
            frictionFor(format) == 2 -> 12.0
            else -> 24.0
        }
}
