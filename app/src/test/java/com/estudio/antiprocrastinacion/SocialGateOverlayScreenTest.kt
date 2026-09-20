package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.socialgate.SocialGateEscapeInfo
import com.estudio.antiprocrastinacion.app.socialgate.escapeDialogMessage
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SocialGateOverlayScreenTest {
    @Test
    fun `escape dialog with 2 remaining escapes explains multiple uses available`() {
        val escape =
            SocialGateEscapeInfo(
                canUse = true,
                remaining = 2,
                limitPerWeek = 2,
                nextAvailableLabel = null,
            )

        val message = escapeDialogMessage(escape, "Instagram")

        assertThat(message).contains("Vas a desbloquear Instagram sin terminar el repaso.")
        assertThat(message).contains("Tenés 2 comodines de escape disponibles esta semana.")
        assertThat(message).doesNotContain("último comodín")
    }

    @Test
    fun `escape dialog with 1 remaining escape warns that using it exhausts quota until cooldown date`() {
        val escape =
            SocialGateEscapeInfo(
                canUse = true,
                remaining = 1,
                limitPerWeek = 2,
                nextAvailableLabel = "Martes 27 de Septiembre",
            )

        val message = escapeDialogMessage(escape, "WhatsApp")

        assertThat(message).contains("Vas a desbloquear WhatsApp sin terminar el repaso.")
        assertThat(message).contains("Este es tu último comodín de escape disponible esta semana.")
        assertThat(message).contains("Si lo usás ahora, vas a poder volver a usarlo recién el Martes 27 de Septiembre.")
    }

    @Test
    fun `escape dialog when quota exhausted shows next available date`() {
        val escape =
            SocialGateEscapeInfo(
                canUse = false,
                remaining = 0,
                limitPerWeek = 2,
                nextAvailableLabel = "Viernes 30 de Septiembre",
            )

        val message = escapeDialogMessage(escape, "TikTok")

        assertThat(message).contains("Ya usaste tus comodines de escape de esta semana.")
        assertThat(message).contains("Vas a poder usar uno de nuevo el Viernes 30 de Septiembre.")
    }
}
