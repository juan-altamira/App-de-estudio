package com.estudio.antiprocrastinacion.app.model.state

data class SocialGateRule(
    val packageName: String,
    val displayName: String,
    val enabled: Boolean = false,
    val maxTriggersPerDay: Int = 1,
    val requiredCorrectAnswers: Int = 3,
    val windowStartMinutes: Int = 0,
    val windowEndMinutes: Int = 22 * 60,
)

data class SocialGateDailyState(
    val packageName: String,
    val localDate: String,
    val solvedCount: Int = 0,
    val lastSolvedAt: Long? = null,
)

enum class SocialGateRuntimePhase {
    IDLE,
    ACTIVE_GATE,
    BLOCKING_EXISTING_SESSION,
    UNLOCKED_FOR_CURRENT_FOREGROUND,
}

data class SocialGateRuntimeState(
    val phase: SocialGateRuntimePhase = SocialGateRuntimePhase.IDLE,
    val targetPackageName: String? = null,
    val activeGateSessionId: String? = null,
    val gateUnlockBaselineCorrectCount: Int? = null,
    val gateUnlockRequiredCorrectAnswers: Int? = null,
    val unlockTokenPackageName: String? = null,
    val unlockTokenIssuedAt: Long? = null,
    val lastForegroundPackageName: String? = null,
    val lastForegroundChangedAt: Long? = null,
    // Timestamps (epoch ms) de cada uso del comodín de escape. Se podan los > 7 días.
    val escapeUsesAt: List<Long> = emptyList(),
)

data class SocialGateInstalledApp(
    val packageName: String,
    val displayName: String,
    val installed: Boolean,
    val enabled: Boolean,
    val maxTriggersPerDay: Int,
    val requiredCorrectAnswers: Int,
    val windowStartMinutes: Int = 0,
    val windowEndMinutes: Int = 22 * 60,
)
