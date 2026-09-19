package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.socialgate.SocialGateCatalog
import com.estudio.antiprocrastinacion.app.socialgate.SocialGateSchedule
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SocialGateCatalogTest {
    @Test
    fun `supported apps contains instagram, tiktok, and x`() {
        val packages = SocialGateCatalog.supportedApps.map { it.packageName }
        assertThat(packages).containsExactly(
            "com.instagram.android",
            "com.zhiliaoapp.musically",
            "com.twitter.android",
        )
    }

    @Test
    fun `default rules for supported apps are all enabled with standard schedule and frequency`() {
        val defaultRules = SocialGateCatalog.defaultRules()
        assertThat(defaultRules).hasSize(3)

        defaultRules.forEach { rule ->
            assertThat(rule.enabled).isTrue()
            assertThat(rule.maxTriggersPerDay).isEqualTo(1)
            assertThat(rule.requiredCorrectAnswers).isEqualTo(3)
            assertThat(rule.windowStartMinutes).isEqualTo(SocialGateSchedule.DEFAULT_WINDOW_START_MINUTES)
            assertThat(rule.windowEndMinutes).isEqualTo(SocialGateSchedule.DEFAULT_WINDOW_END_MINUTES)
        }
    }
}
