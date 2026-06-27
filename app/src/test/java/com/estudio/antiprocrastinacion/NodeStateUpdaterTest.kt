package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.domain.session.NodeStateUpdater
import com.estudio.antiprocrastinacion.app.model.content.ContentOrigin
import com.estudio.antiprocrastinacion.app.model.content.FacetType
import com.estudio.antiprocrastinacion.app.model.content.Item
import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.ItemRole
import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.content.Surface
import com.estudio.antiprocrastinacion.app.model.event.AbandonReason
import com.estudio.antiprocrastinacion.app.model.event.AttemptOutcome
import com.estudio.antiprocrastinacion.app.model.state.NodeState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NodeStateUpdaterTest {
    private val updater = NodeStateUpdater()

    @Test
    fun `correct first try doubles stability`() {
        val state = NodeState(nodeId = "node", stabilityHours = 24.0)

        val updated =
            updater.applyAnswer(
                current = state,
                item = sampleItem(),
                outcome = AttemptOutcome.CORRECT_FIRST_TRY,
                latencyMs = 2000,
                surface = Surface.IN_APP_QUICK,
                sessionMode = SessionMode.QUICK,
                now = 1_000L,
            )

        assertThat(updated.stabilityHours).isEqualTo(48.0)
        assertThat(updated.timesCorrectFirstTry).isEqualTo(1)
        assertThat(updated.memoryScore).isGreaterThan(state.memoryScore)
    }

    @Test
    fun `abandon lowers stability and increases friction`() {
        val state = NodeState(nodeId = "node", stabilityHours = 24.0, frictionUser = 0.2)

        val updated =
            updater.applyAbandon(
                current = state,
                nodeId = "node",
                surface = Surface.IN_APP_QUICK,
                sessionMode = SessionMode.QUICK,
                reason = AbandonReason.TIMEOUT,
                now = 1_000L,
            )

        assertThat(updated.stabilityHours).isWithin(0.0001).of(8.4)
        assertThat(updated.frictionUser).isGreaterThan(state.frictionUser)
        assertThat(updated.timesAbandoned).isEqualTo(1)
    }
}

private fun sampleItem(): Item =
    Item(
        itemId = "item",
        nodeId = "node",
        facet = FacetType.DEFINICION_FUNCIONAL,
        format = ItemFormat.MULTIPLE_CHOICE,
        frictionLevel = 1,
        difficultySeed = 0.3,
        itemRole = ItemRole.CORE,
        allowedSurfaces = listOf(Surface.IN_APP_QUICK),
        cooldownHours = 1.0,
        stem = "stem",
        correctAnswer = "ok",
        feedbackShort = "fb",
        coversMustKnow = listOf("mk"),
        variantGroupId = null,
        rescueGroupId = null,
        nodeComplexity = null,
        facetComplexity = null,
        distractorSimilarity = null,
        prerequisiteDepth = null,
        targetsErrorIds = emptyList(),
        commonErrorSignals = emptyList(),
        options = emptyList(),
        version = 1,
        updatedAt = 1L,
        sourceRefs = emptyList(),
        contentOrigin = ContentOrigin.IMPORTED,
    )

