package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.socialgate.InescapableGateDecision
import com.estudio.antiprocrastinacion.app.socialgate.GateActivityLaunchThrottle
import com.estudio.antiprocrastinacion.app.socialgate.ForegroundProcessingTracker
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

    @Test
    fun `brings study activity back when gate is active and activity lost focus`() {
        val shouldBring =
            InescapableGateDecision.shouldBringStudyActivityToFront(
                hasActiveGate = true,
                isStudyActivityInteractive = false,
                isScreenInteractive = true,
                isKeyguardLocked = false,
                foregroundPackage = "com.miui.home",
            )

        assertThat(shouldBring).isTrue()
    }

    @Test
    fun `brings study activity back even when UsageStats still reports own stale package`() {
        val shouldBring =
            InescapableGateDecision.shouldBringStudyActivityToFront(
                hasActiveGate = true,
                isStudyActivityInteractive = false,
                isScreenInteractive = true,
                isKeyguardLocked = false,
                foregroundPackage = own,
            )

        assertThat(shouldBring).isTrue()
    }

    @Test
    fun `does not dispute calls lock screen or an already interactive gate activity`() {
        assertThat(
            InescapableGateDecision.shouldBringStudyActivityToFront(
                hasActiveGate = true,
                isStudyActivityInteractive = false,
                isScreenInteractive = true,
                isKeyguardLocked = false,
                foregroundPackage = "com.xiaomi.incallui",
            ),
        ).isFalse()
        assertThat(
            InescapableGateDecision.shouldBringStudyActivityToFront(
                hasActiveGate = true,
                isStudyActivityInteractive = false,
                isScreenInteractive = false,
                isKeyguardLocked = true,
                foregroundPackage = null,
            ),
        ).isFalse()
        assertThat(
            InescapableGateDecision.shouldBringStudyActivityToFront(
                hasActiveGate = true,
                isStudyActivityInteractive = true,
                isScreenInteractive = true,
                isKeyguardLocked = false,
                foregroundPackage = own,
            ),
        ).isFalse()
    }

    @Test
    fun `activity relaunch throttle allows first and spaced attempts but rejects a launch storm`() {
        val throttle = GateActivityLaunchThrottle(minimumIntervalMs = 750L)

        assertThat(throttle.tryAcquire(1_000L)).isTrue()
        assertThat(throttle.tryAcquire(1_500L)).isFalse()
        assertThat(throttle.tryAcquire(1_750L)).isTrue()
        throttle.reset()
        assertThat(throttle.tryAcquire(1_751L)).isTrue()
    }

    @Test
    fun `foreground processing waits for enabled rules before classifying the current app`() {
        val tracker = ForegroundProcessingTracker()

        assertThat(tracker.shouldProcess("com.zhiliaoapp.musically")).isFalse()

        tracker.onRulesChanged(hasEnabledRules = true)

        assertThat(tracker.shouldProcess("com.zhiliaoapp.musically")).isTrue()
        assertThat(tracker.shouldProcess("com.zhiliaoapp.musically")).isFalse()
    }

    @Test
    fun `rule reload forces the same foreground package to be evaluated again`() {
        val tracker = ForegroundProcessingTracker()
        tracker.onRulesChanged(hasEnabledRules = true)
        assertThat(tracker.shouldProcess("com.instagram.android")).isTrue()
        assertThat(tracker.shouldProcess("com.instagram.android")).isFalse()

        tracker.onRulesChanged(hasEnabledRules = true)

        assertThat(tracker.shouldProcess("com.instagram.android")).isTrue()
        tracker.onRulesChanged(hasEnabledRules = false)
        assertThat(tracker.shouldProcess("com.instagram.android")).isFalse()
    }
}
