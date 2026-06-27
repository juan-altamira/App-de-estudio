package com.estudio.antiprocrastinacion.app.model.json

import com.estudio.antiprocrastinacion.app.model.content.ContentOrigin
import com.estudio.antiprocrastinacion.app.model.content.FacetType
import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.ItemRole
import com.estudio.antiprocrastinacion.app.model.content.NodeType
import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.content.Surface
import com.estudio.antiprocrastinacion.app.model.state.ItemReviewOutcome
import com.estudio.antiprocrastinacion.app.model.state.PendingPhase
import com.estudio.antiprocrastinacion.app.model.state.SessionPromptKind
import com.estudio.antiprocrastinacion.app.model.state.StudySessionPayload
import kotlinx.serialization.Serializable

@Serializable
data class ContentPackageDto(
    val packageId: String,
    val schemaVersion: Int,
    val seedVersion: String? = null,
    val generatedAt: Long,
    val contentHash: String? = null,
    val origin: ContentOrigin = ContentOrigin.IMPORTED,
    val courses: List<CourseDto>,
    val units: List<UnitDto>,
    val outcomes: List<OutcomeDto>,
    val nodes: List<NodeDto>,
    val items: List<ItemDto>,
)

@Serializable
data class UserStatePackageDto(
    val exportedAt: Long,
    val nodeStates: List<NodeStateDto>,
    val itemStates: List<ItemStateDto> = emptyList(),
    val itemOverrides: List<ItemOverrideDto> = emptyList(),
    val nodeFormatStats: List<NodeFormatStatDto> = emptyList(),
    val archivedNodeIds: List<String> = emptyList(),
    val activeSession: StudySessionDto? = null,
    val activeSessions: List<StudySessionDto> = emptyList(),
    val appSettings: AppSettingsDto,
    val socialGateRules: List<SocialGateRuleDto> = emptyList(),
    val socialGateDailyStates: List<SocialGateDailyStateDto> = emptyList(),
    val socialGateRuntimeState: SocialGateRuntimeStateDto? = null,
)

@Serializable
data class SyncSnapshotDto(
    val snapshotVersion: Int,
    val exportedAt: Long,
    val contentPackage: ContentPackageDto? = null,
    val contentPackages: List<ContentPackageDto> = emptyList(),
    val userStatePackage: UserStatePackageDto,
)

@Serializable
data class CourseDto(
    val courseId: String,
    val title: String,
    val description: String? = null,
    val version: Int,
    val updatedAt: Long,
)

@Serializable
data class UnitDto(
    val unitId: String,
    val courseId: String,
    val title: String,
    val description: String? = null,
    val orderIndex: Int,
    val version: Int,
    val updatedAt: Long,
)

@Serializable
data class OutcomeDto(
    val outcomeId: String,
    val unitId: String,
    val title: String,
    val description: String? = null,
    val version: Int,
    val updatedAt: Long,
)

@Serializable
data class NodeDto(
    val nodeId: String,
    val courseId: String,
    val unitId: String,
    val outcomeIds: List<String>,
    val title: String,
    val coreClaim: String,
    val type: NodeType,
    val weightExam: Double,
    val prerequisites: List<String>,
    val facets: List<FacetType>,
    val mustKnow: List<String>,
    val commonErrors: List<String>,
    val minimumMasteryDefinition: String,
    val surfaceEasyReady: Boolean,
    val surfaceEasyItemCount: Int,
    val sourceRefs: List<String>,
    val version: Int,
    val updatedAt: Long,
)

@Serializable
data class ItemOptionDto(
    val id: String,
    val text: String,
    val isCorrect: Boolean = false,
)

@Serializable
data class ItemDto(
    val itemId: String,
    val nodeId: String,
    val facet: FacetType,
    val format: ItemFormat,
    val frictionLevel: Int,
    val difficultySeed: Double,
    val itemRole: ItemRole,
    val allowedSurfaces: List<Surface>,
    val cooldownHours: Double,
    val stem: String,
    val correctAnswer: String,
    val feedbackShort: String,
    val coversMustKnow: List<String>,
    val variantGroupId: String? = null,
    val rescueGroupId: String? = null,
    val nodeComplexity: Double? = null,
    val facetComplexity: Double? = null,
    val distractorSimilarity: Double? = null,
    val prerequisiteDepth: Double? = null,
    val targetsErrorIds: List<String> = emptyList(),
    val commonErrorSignals: List<String> = emptyList(),
    val options: List<ItemOptionDto> = emptyList(),
    val version: Int,
    val updatedAt: Long,
    val sourceRefs: List<String> = emptyList(),
)

@Serializable
data class NodeStateDto(
    val nodeId: String,
    val memoryScore: Double,
    val stabilityHours: Double,
    val retrievability: Double,
    val difficultyUser: Double,
    val frictionUser: Double,
    val coverageScore: Double,
    val avgLatencyMs: Double,
    val errorRate: Double,
    val abandonRate: Double,
    val lastReviewedAt: Long? = null,
    val nextReviewAt: Long? = null,
    val timesSeen: Int,
    val timesCorrectFirstTry: Int,
    val timesCorrectAfterRescue: Int,
    val timesFailed: Int,
    val timesAbandoned: Int,
    val lastSurfaceUsed: Surface? = null,
    val lastSessionMode: SessionMode? = null,
)

@Serializable
data class ItemStateDto(
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

@Serializable
data class ItemOverrideDto(
    val itemId: String,
    val archived: Boolean = false,
    val stemOverride: String? = null,
    val correctAnswerOverride: String? = null,
    val optionsOverride: List<ItemOptionDto>? = null,
)

@Serializable
data class NodeFormatStatDto(
    val nodeId: String,
    val format: ItemFormat,
    val attempts: Int,
    val successes: Int,
    val avgLatencyMs: Double,
)

@Serializable
data class AppSettingsDto(
    val quickSessionTargetDefault: Int,
    val deepSessionQuestionTarget: Int,
    val backExitWindowSeconds: Int,
    val cognitiveNotificationsEnabled: Boolean = false,
    val cognitiveNotificationsPerDay: Int = 1,
    val cognitiveNotificationWindowStartMinutes: Int = 18 * 60,
    val cognitiveNotificationWindowEndMinutes: Int = 21 * 60,
    val seedAppliedVersion: String? = null,
    val seedPackageId: String? = null,
    val demoContentEnabled: Boolean,
)

@Serializable
data class SocialGateRuleDto(
    val packageName: String,
    val displayName: String,
    val enabled: Boolean = false,
    val maxTriggersPerDay: Int = 1,
    val requiredCorrectAnswers: Int = 3,
    val windowStartMinutes: Int = 0,
    val windowEndMinutes: Int = 22 * 60,
)

@Serializable
data class SocialGateDailyStateDto(
    val packageName: String,
    val localDate: String,
    val solvedCount: Int = 0,
    val lastSolvedAt: Long? = null,
)

@Serializable
data class SocialGateRuntimeStateDto(
    val phase: String = "IDLE",
    val targetPackageName: String? = null,
    val activeGateSessionId: String? = null,
    val gateUnlockBaselineCorrectCount: Int? = null,
    val gateUnlockRequiredCorrectAnswers: Int? = null,
    val unlockTokenPackageName: String? = null,
    val unlockTokenIssuedAt: Long? = null,
    val lastForegroundPackageName: String? = null,
    val lastForegroundChangedAt: Long? = null,
    val escapeUsesAt: List<Long> = emptyList(),
)

@Serializable
data class StudySessionDto(
    val sessionId: String,
    val mode: SessionMode,
    val surface: Surface,
    val packetId: String,
    val topicUnitId: String,
    val currentCourseTitle: String,
    val currentTopicTitle: String,
    val queueItemIds: List<String>,
    val rescueQueueItemIds: List<String>,
    val goalCorrectCount: Int,
    val currentNodeId: String? = null,
    val currentItemId: String? = null,
    val currentAttemptIndex: Int,
    val currentPromptKind: SessionPromptKind = SessionPromptKind.MANUAL,
    val correctCount: Int,
    val stepIndex: Int,
    val startedAt: Long,
    val lastInteractionAt: Long,
    val isMicroPromptActive: Boolean,
    val isExitArmed: Boolean,
    val exitArmedUntil: Long? = null,
    val payload: StudySessionPayload = StudySessionPayload(),
)
