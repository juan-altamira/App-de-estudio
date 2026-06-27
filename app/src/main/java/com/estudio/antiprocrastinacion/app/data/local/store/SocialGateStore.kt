package com.estudio.antiprocrastinacion.app.data.local.store

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.estudio.antiprocrastinacion.app.model.json.SocialGateDailyStateDto
import com.estudio.antiprocrastinacion.app.model.json.SocialGateRuleDto
import com.estudio.antiprocrastinacion.app.model.json.SocialGateRuntimeStateDto
import com.estudio.antiprocrastinacion.app.ui.common.AppJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

private val Context.socialGateDataStore by preferencesDataStore(name = "social_gate_store")

@Serializable
data class SocialGateStoreSnapshotDto(
    val rules: List<SocialGateRuleDto> = emptyList(),
    val dailyStates: List<SocialGateDailyStateDto> = emptyList(),
    val runtimeState: SocialGateRuntimeStateDto = SocialGateRuntimeStateDto(),
)

class SocialGateStore(
    private val context: Context,
) {
    private object Keys {
        val Snapshot = stringPreferencesKey("social_gate_snapshot_json")
    }

    val snapshot: Flow<SocialGateStoreSnapshotDto> =
        context.socialGateDataStore.data.map(::mapPreferences)

    suspend fun getCurrent(): SocialGateStoreSnapshotDto = snapshot.first()

    suspend fun update(transform: (SocialGateStoreSnapshotDto) -> SocialGateStoreSnapshotDto) {
        context.socialGateDataStore.edit { prefs ->
            val current = mapPreferences(prefs)
            val updated = transform(current)
            prefs[Keys.Snapshot] = AppJson.encodeToString(updated)
        }
    }

    private fun mapPreferences(preferences: Preferences): SocialGateStoreSnapshotDto =
        preferences[Keys.Snapshot]
            ?.let { raw ->
                runCatching { AppJson.decodeFromString<SocialGateStoreSnapshotDto>(raw) }.getOrDefault(SocialGateStoreSnapshotDto())
            } ?: SocialGateStoreSnapshotDto()
}
