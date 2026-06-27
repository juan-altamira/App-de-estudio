package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.socialgate.ForegroundEventResolver
import com.estudio.antiprocrastinacion.app.socialgate.ForegroundUsageEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ForegroundEventResolverTest {
    @Test
    fun `picks the package of the most recent foreground event`() {
        val resolved =
            ForegroundEventResolver.latestForegroundPackage(
                events =
                    listOf(
                        ForegroundUsageEvent("com.miui.home", movedToForeground = true, timestampMs = 100),
                        ForegroundUsageEvent("com.instagram.android", movedToForeground = true, timestampMs = 300),
                        ForegroundUsageEvent("com.android.systemui", movedToForeground = true, timestampMs = 200),
                    ),
                previousForeground = null,
            )

        assertThat(resolved).isEqualTo("com.instagram.android")
    }

    @Test
    fun `ignores events that are not foreground transitions`() {
        val resolved =
            ForegroundEventResolver.latestForegroundPackage(
                events =
                    listOf(
                        ForegroundUsageEvent("com.instagram.android", movedToForeground = true, timestampMs = 100),
                        ForegroundUsageEvent("com.zhiliaoapp.musically", movedToForeground = false, timestampMs = 500),
                    ),
                previousForeground = null,
            )

        assertThat(resolved).isEqualTo("com.instagram.android")
    }

    @Test
    fun `keeps the previous foreground when there are no new foreground events`() {
        val resolved =
            ForegroundEventResolver.latestForegroundPackage(
                events = emptyList(),
                previousForeground = "com.instagram.android",
            )

        assertThat(resolved).isEqualTo("com.instagram.android")
    }

    @Test
    fun `returns null when there are no events and no previous foreground`() {
        val resolved =
            ForegroundEventResolver.latestForegroundPackage(
                events = emptyList(),
                previousForeground = null,
            )

        assertThat(resolved).isNull()
    }
}
