package com.estudio.antiprocrastinacion.app.model.state

import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.Item
import com.estudio.antiprocrastinacion.app.model.content.Node
import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.content.Surface
import kotlinx.serialization.Serializable

data class NodeState(
    val nodeId: String,
    val memoryScore: Double = 0.35,
    val stabilityHours: Double = 24.0,
    val retrievability: Double = 0.50,
    val difficultyUser: Double = 0.50,
    val frictionUser: Double = 0.30,
    val coverageScore: Double = 0.0,
    val avgLatencyMs: Double = 0.0,
    val errorRate: Double = 0.0,
    val abandonRate: Double = 0.0,
    val lastReviewedAt: Long? = null,
    val nextReviewAt: Long? = null,
    val timesSeen: Int = 0,
    val timesCorrectFirstTry: Int = 0,
    val timesCorrectAfterRescue: Int = 0,
    val timesFailed: Int = 0,
    val timesAbandoned: Int = 0,
    val lastSurfaceUsed: Surface? = null,
    val lastSessionMode: SessionMode? = null,
)

@Serializable
enum class ItemReviewOutcome {
    CLEAN_CORRECT,
    CLEAN_CORRECT_LATE,
    CLEAN_CORRECT_VERY_LATE,
    RECOVERED_AFTER_ONE_FAILURE,
    RECOVERED_AFTER_MULTIPLE_FAILURES,
    // Marca persistida cuando una tarjeta real se falla (en pendientes o gate) y todavía no se corrigió.
    // Mientras tenga esta marca, la próxima respuesta correcta se trata como recuperación (un solo
    // descuento), aunque la sesión muera antes de corregir. Reemplaza al antiguo GATE_FAILURE como
    // mecanismo de castigo, pero GATE_FAILURE se conserva por compatibilidad con datos ya guardados.
    FAILED_AWAITING_RECOVERY,
    GATE_FAILURE,
    ABANDONED,
}

data class ItemState(
    val itemId: String,
    val stage: Int = 0,
    val lastReviewedAt: Long? = null,
    val nextReviewAt: Long? = null,
    val timesShown: Int = 0,
    val timesFailed: Int = 0,
    val timesCorrectFirstTry: Int = 0,
    val timesRecoveredAfterFailure: Int = 0,
    val timesAbandoned: Int = 0,
    val lastOutcome: ItemReviewOutcome? = null,
)

data class NodeFormatStat(
    val nodeId: String,
    val format: ItemFormat,
    val attempts: Int = 0,
    val successes: Int = 0,
    val avgLatencyMs: Double = 0.0,
)

@Serializable
enum class SessionPromptKind {
    REAL_PENDING,
    REAL_CORRECTION,
    AUXILIARY_ANZUELO,
    AUXILIARY_RESCUE,
    REAL_GATE,
    AUXILIARY_GATE_FILL,
    MANUAL,
    BACK_MICRO,
}

@Serializable
enum class PendingPhase {
    FIRST_PASS,
    CORRECTION,
}

@Serializable
data class StudySessionPayload(
    val pendingPhase: PendingPhase? = null,
    val correctionQueueItemIds: List<String> = emptyList(),
    val failCountsByItemId: Map<String, Int> = emptyMap(),
    val consecutiveErrorCount: Int = 0,
    val vetoedAuxiliaryItemIds: List<String> = emptyList(),
    val usedAuxiliaryItemIds: List<String> = emptyList(),
    val resolvedRealItemIds: List<String> = emptyList(),
    val gateFillerItemIds: List<String> = emptyList(),
    val gateFillerCursor: Int = 0,
    val suspendedItemId: String? = null,
    val suspendedPromptKind: SessionPromptKind? = null,
)

data class AppSettings(
    val quickSessionTargetDefault: Int = 4,
    val deepSessionQuestionTarget: Int = 12,
    val backExitWindowSeconds: Int = 8,
    val cognitiveNotificationsEnabled: Boolean = false,
    val cognitiveNotificationsPerDay: Int = 1,
    val cognitiveNotificationWindowStartMinutes: Int = 18 * 60,
    val cognitiveNotificationWindowEndMinutes: Int = 21 * 60,
    val seedAppliedVersion: String? = null,
    val seedPackageId: String? = null,
    val demoContentEnabled: Boolean = true,
)

data class TopicPacket(
    val packetId: String,
    val topicUnitId: String,
    val currentCourseTitle: String = "",
    val currentTopicTitle: String,
    val surface: Surface,
    val sessionMode: SessionMode,
    val nodeIds: List<String>,
    val initialItemIds: List<String>,
    val rescueItemIds: List<String>,
    val goalCorrectCount: Int,
    val priorityScore: Double,
    val initialPromptKind: SessionPromptKind = SessionPromptKind.MANUAL,
    val payload: StudySessionPayload = StudySessionPayload(),
)

data class StudySession(
    val sessionId: String,
    val mode: SessionMode,
    val surface: Surface,
    val packetId: String,
    val topicUnitId: String,
    val currentCourseTitle: String = "",
    val currentTopicTitle: String,
    val queueItemIds: List<String>,
    val rescueQueueItemIds: List<String>,
    val goalCorrectCount: Int,
    val currentNodeId: String?,
    val currentItemId: String?,
    val currentAttemptIndex: Int,
    val currentPromptKind: SessionPromptKind = SessionPromptKind.MANUAL,
    val correctCount: Int,
    val stepIndex: Int,
    val startedAt: Long,
    val lastInteractionAt: Long,
    val isMicroPromptActive: Boolean,
    val isExitArmed: Boolean,
    val exitArmedUntil: Long?,
    val payload: StudySessionPayload = StudySessionPayload(),
)

data class SessionScoring(
    val dueScore: Double,
    val fragilityScore: Double,
    val frictionScore: Double,
    val abandonmentScore: Double,
    val recentTopicPenalty: Double,
    val priority: Double,
)

data class StudyPrompt(
    val session: StudySession,
    val node: Node,
    val item: Item,
    val promptIndex: Int,
    val feedback: String? = null,
    val pendingFailedItemCount: Int = 0,
    // Para tarjetas auxiliares de rescate: stems de las tarjetas reales falladas que dispararon el rescate
    // (alimenta el "ver detalle" de la UI). Vacío para prompts reales o cuando no aplica.
    val auxiliaryTriggerStems: List<String> = emptyList(),
)

data class UserAnswer(
    val responseText: String,
    val isCorrect: Boolean,
    val latencyMs: Long,
)
