package com.estudio.antiprocrastinacion.app.data.local.store

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.estudio.antiprocrastinacion.app.model.state.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "app_settings")

class AppSettingsStore(
    private val context: Context,
) {
    private object Keys {
        val QuickTarget = intPreferencesKey("quick_target")
        val DeepTarget = intPreferencesKey("deep_target")
        val BackExitWindow = intPreferencesKey("back_exit_window")
        val CognitiveNotificationsEnabled = booleanPreferencesKey("cognitive_notifications_enabled")
        val CognitiveNotificationsPerDay = intPreferencesKey("cognitive_notifications_per_day")
        val CognitiveNotificationWindowStartMinutes = intPreferencesKey("cognitive_notification_window_start_minutes")
        val CognitiveNotificationWindowEndMinutes = intPreferencesKey("cognitive_notification_window_end_minutes")
        val SeedVersion = stringPreferencesKey("seed_version")
        val SeedPackageId = stringPreferencesKey("seed_package_id")
        val DemoEnabled = booleanPreferencesKey("demo_enabled")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map(::mapPreferences)

    suspend fun getCurrent(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val updated = transform(mapPreferences(prefs))
            prefs[Keys.QuickTarget] = updated.quickSessionTargetDefault
            prefs[Keys.DeepTarget] = updated.deepSessionQuestionTarget
            prefs[Keys.BackExitWindow] = updated.backExitWindowSeconds
            prefs[Keys.CognitiveNotificationsEnabled] = updated.cognitiveNotificationsEnabled
            prefs[Keys.CognitiveNotificationsPerDay] = updated.cognitiveNotificationsPerDay
            prefs[Keys.CognitiveNotificationWindowStartMinutes] = updated.cognitiveNotificationWindowStartMinutes
            prefs[Keys.CognitiveNotificationWindowEndMinutes] = updated.cognitiveNotificationWindowEndMinutes
            prefs[Keys.DemoEnabled] = updated.demoContentEnabled
            if (updated.seedAppliedVersion != null) {
                prefs[Keys.SeedVersion] = updated.seedAppliedVersion
            } else {
                prefs.remove(Keys.SeedVersion)
            }
            if (updated.seedPackageId != null) {
                prefs[Keys.SeedPackageId] = updated.seedPackageId
            } else {
                prefs.remove(Keys.SeedPackageId)
            }
        }
    }

    private fun mapPreferences(preferences: Preferences): AppSettings =
        AppSettings(
            quickSessionTargetDefault = preferences[Keys.QuickTarget] ?: 4,
            deepSessionQuestionTarget = preferences[Keys.DeepTarget] ?: 12,
            backExitWindowSeconds = preferences[Keys.BackExitWindow] ?: 8,
            cognitiveNotificationsEnabled = preferences[Keys.CognitiveNotificationsEnabled] ?: false,
            cognitiveNotificationsPerDay = preferences[Keys.CognitiveNotificationsPerDay] ?: 1,
            cognitiveNotificationWindowStartMinutes = preferences[Keys.CognitiveNotificationWindowStartMinutes] ?: 18 * 60,
            cognitiveNotificationWindowEndMinutes = preferences[Keys.CognitiveNotificationWindowEndMinutes] ?: 21 * 60,
            seedAppliedVersion = preferences[Keys.SeedVersion],
            seedPackageId = preferences[Keys.SeedPackageId],
            demoContentEnabled = preferences[Keys.DemoEnabled] ?: true,
        )
}
