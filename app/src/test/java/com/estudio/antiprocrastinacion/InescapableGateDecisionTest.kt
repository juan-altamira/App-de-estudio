package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.socialgate.InescapableGateDecision
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InescapableGateDecisionTest {
    private val own = "com.estudio.antiprocrastinacion"

    @Test
    fun `keeps the gate over any other app while a gate is active`() {
        val keep =
            InescapableGateDecision.shouldKeepGateOver(
                foregroundPackage = "com.whatsapp",
                ownPackage = own,
                hasActiveGate = true,
            )
        assertThat(keep).isTrue()
    }

    @Test
    fun `keeps the gate over the launcher (cannot dodge by pressing home)`() {
        val keep =
            InescapableGateDecision.shouldKeepGateOver(
                foregroundPackage = "com.miui.home",
                ownPackage = own,
                hasActiveGate = true,
            )
        assertThat(keep).isTrue()
    }

    @Test
    fun `does not keep the gate when there is no active gate`() {
        val keep =
            InescapableGateDecision.shouldKeepGateOver(
                foregroundPackage = "com.whatsapp",
                ownPackage = own,
                hasActiveGate = false,
            )
        assertThat(keep).isFalse()
    }

    @Test
    fun `yields to our own study app so the user can study`() {
        val keep =
            InescapableGateDecision.shouldKeepGateOver(
                foregroundPackage = own,
                ownPackage = own,
                hasActiveGate = true,
            )
        assertThat(keep).isFalse()
    }

    @Test
    fun `yields to phone and incoming call screens`() {
        for (dialer in listOf("com.google.android.dialer", "com.xiaomi.incallui", "com.android.server.telecom")) {
            val keep =
                InescapableGateDecision.shouldKeepGateOver(
                    foregroundPackage = dialer,
                    ownPackage = own,
                    hasActiveGate = true,
                )
            assertThat(keep).isFalse()
        }
    }
}
