package com.estudio.antiprocrastinacion.app.domain.scheduler

import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.state.TopicPacket

interface SchedulerService {
    suspend fun buildQuickPacket(
        preferredUnitId: String? = null,
        preferredItemId: String? = null,
    ): TopicPacket?
    suspend fun buildNotificationPacket(
        preferredUnitId: String? = null,
        preferredItemId: String? = null,
    ): TopicPacket?
    suspend fun buildSocialGatePacket(goalCorrectCount: Int): TopicPacket?
    suspend fun buildDeepPacket(unitId: String): TopicPacket?
    suspend fun buildDrainPacket(unitId: String): TopicPacket?
    suspend fun getAvailableUnits(): List<DeepModeUnitOption>
}

data class DeepModeUnitOption(
    val unitId: String,
    val courseTitle: String,
    val unitTitle: String,
    val availableNodeCount: Int,
)
