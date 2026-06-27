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

    fun resolveInstalledApps(
        context: Context,
        rules: List<SocialGateRule>,
    ): List<SocialGateInstalledApp> {
        val packageManager = context.packageManager
        return supportedApps.mapNotNull { app ->
            val installed = packageManager.isInstalled(app.packageName)
            if (!installed) return@mapNotNull null
            val rule =
                rules.firstOrNull { it.packageName == app.packageName }?.let(SocialGateSchedule::normalizeRule)
            SocialGateInstalledApp(
                packageName = app.packageName,
                displayName = app.displayName,
                installed = true,
                enabled = rule?.enabled ?: false,
                maxTriggersPerDay = rule?.maxTriggersPerDay ?: 1,
                requiredCorrectAnswers = rule?.requiredCorrectAnswers ?: 3,
                windowStartMinutes = rule?.windowStartMinutes ?: SocialGateSchedule.DEFAULT_WINDOW_START_MINUTES,
                windowEndMinutes = rule?.windowEndMinutes ?: SocialGateSchedule.DEFAULT_WINDOW_END_MINUTES,
            )
        }
    }
}

private fun PackageManager.isInstalled(packageName: String): Boolean =
    runCatching { getApplicationInfo(packageName, 0) }.isSuccess
