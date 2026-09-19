package com.estudio.antiprocrastinacion.app.socialgate

import android.content.Context
import android.content.pm.PackageManager
import com.estudio.antiprocrastinacion.app.model.state.SocialGateInstalledApp
import com.estudio.antiprocrastinacion.app.model.state.SocialGateRule

data class SupportedSocialGateApp(
    val packageName: String,
    val displayName: String,
)

object SocialGateCatalog {
    val supportedApps =
        listOf(
            SupportedSocialGateApp(packageName = "com.instagram.android", displayName = "Instagram"),
            SupportedSocialGateApp(packageName = "com.zhiliaoapp.musically", displayName = "TikTok"),
            SupportedSocialGateApp(packageName = "com.twitter.android", displayName = "X"),
        )

    fun defaultRule(app: SupportedSocialGateApp): SocialGateRule =
        SocialGateRule(
            packageName = app.packageName,
            displayName = app.displayName,
            enabled = true,
            maxTriggersPerDay = 1,
            requiredCorrectAnswers = 3,
            windowStartMinutes = SocialGateSchedule.DEFAULT_WINDOW_START_MINUTES,
            windowEndMinutes = SocialGateSchedule.DEFAULT_WINDOW_END_MINUTES,
        )

    fun defaultRules(): List<SocialGateRule> = supportedApps.map(::defaultRule)

    fun resolveInstalledApps(
        context: Context,
        rules: List<SocialGateRule>,
    ): List<SocialGateInstalledApp> {
        val packageManager = context.packageManager
        return supportedApps.mapNotNull { app ->
            val installed = packageManager.isInstalled(app.packageName)
            if (!installed) return@mapNotNull null
            val existing = rules.firstOrNull { it.packageName == app.packageName }
            val rule = SocialGateSchedule.normalizeRule(existing ?: defaultRule(app))
            SocialGateInstalledApp(
                packageName = app.packageName,
                displayName = app.displayName,
                installed = true,
                enabled = true,
                maxTriggersPerDay = rule.maxTriggersPerDay,
                requiredCorrectAnswers = rule.requiredCorrectAnswers,
                windowStartMinutes = rule.windowStartMinutes,
                windowEndMinutes = rule.windowEndMinutes,
            )
        }
    }
}

private fun PackageManager.isInstalled(packageName: String): Boolean =
    runCatching { getApplicationInfo(packageName, 0) }.isSuccess
