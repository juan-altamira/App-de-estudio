package com.estudio.antiprocrastinacion.app.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey val courseId: String,
    val title: String,
    val description: String?,
    val version: Int,
    val updatedAt: Long,
)

@Entity(
    tableName = "units",
    indices = [Index("courseId")],
)
data class UnitEntity(
    @PrimaryKey val unitId: String,
    val courseId: String,
    val title: String,
    val description: String?,
    val orderIndex: Int,
    val version: Int,
    val updatedAt: Long,
)

@Entity(
    tableName = "outcomes",
    indices = [Index("unitId")],
)
data class OutcomeEntity(
    @PrimaryKey val outcomeId: String,
    val unitId: String,
    val title: String,
    val description: String?,
    val version: Int,
    val updatedAt: Long,
)

@Entity(
    tableName = "nodes",
    indices = [Index("courseId"), Index("unitId"), Index("archivedCandidate"), Index("contentOrigin")],
)
data class NodeEntity(
    @PrimaryKey val nodeId: String,
    val courseId: String,
    val unitId: String,
    val outcomeIdsJson: String,
    val title: String,
    val coreClaim: String,
    val type: String,
    val weightExam: Double,
    val prerequisitesJson: String,
    val facetsJson: String,
    val mustKnowJson: String,
    val commonErrorsJson: String,
    val minimumMasteryDefinition: String,
    val surfaceEasyReady: Boolean,
    val surfaceEasyItemCount: Int,
    val sourceRefsJson: String,
    val version: Int,
    val updatedAt: Long,
    val archivedCandidate: Boolean,
    val contentOrigin: String,
)

@Entity(
    tableName = "items",
    indices = [Index("nodeId"), Index("itemRole"), Index("frictionLevel"), Index("contentOrigin")],
)
data class ItemEntity(
    @PrimaryKey val itemId: String,
    val nodeId: String,
    val facet: String,
    val format: String,
    val frictionLevel: Int,
    val difficultySeed: Double,
    val itemRole: String,
    val allowedSurfacesJson: String,
    val cooldownHours: Double,
    val stem: String,
    val correctAnswer: String,
    val feedbackShort: String,
    val coversMustKnowJson: String,
    val variantGroupId: String?,
    val rescueGroupId: String?,
    val nodeComplexity: Double?,
    val facetComplexity: Double?,
    val distractorSimilarity: Double?,
    val prerequisiteDepth: Double?,
    val targetsErrorIdsJson: String,
    val commonErrorSignalsJson: String,
    val optionsJson: String,
    val version: Int,
    val updatedAt: Long,
    val sourceRefsJson: String,
    val contentOrigin: String,
)

@Entity(tableName = "node_states")
data class NodeStateEntity(
    @PrimaryKey val nodeId: String,
    val memoryScore: Double,
    val stabilityHours: Double,
    val retrievability: Double,
    val difficultyUser: Double,
    val frictionUser: Double,
    val coverageScore: Double,
    val avgLatencyMs: Double,
    val errorRate: Double,
    val abandonRate: Double,
    val lastReviewedAt: Long?,
    val nextReviewAt: Long?,
    val timesSeen: Int,
    val timesCorrectFirstTry: Int,
    val timesCorrectAfterRescue: Int,
    val timesFailed: Int,
    val timesAbandoned: Int,
    val lastSurfaceUsed: String?,
    val lastSessionMode: String?,
)

@Entity(tableName = "item_states")
data class ItemStateEntity(
    @PrimaryKey val itemId: String,
    val stage: Int,
    val lastReviewedAt: Long?,
    val nextReviewAt: Long?,
    val timesSeen: Int,
    val timesFailed: Int,
    val timesCorrectFirstTry: Int,
    val timesRecoveredAfterFailure: Int,
    val timesAbandoned: Int,
    val lastOutcome: String?,
)

@Entity(tableName = "item_overrides")
data class ItemOverrideEntity(
    @PrimaryKey val itemId: String,
    val archived: Boolean,
    val stemOverride: String?,
    val correctAnswerOverride: String?,
    val optionsJsonOverride: String?,
)

@Entity(
    tableName = "node_format_stats",
    primaryKeys = ["nodeId", "format"],
)
data class NodeFormatStatEntity(
    val nodeId: String,
    val format: String,
    val attempts: Int,
    val successes: Int,
    val avgLatencyMs: Double,
)

@Entity(tableName = "active_sessions")
data class ActiveSessionEntity(
    @PrimaryKey val sessionId: String,
    val mode: String,
    val surface: String,
    val packetId: String,
    val topicUnitId: String,
    val currentCourseTitle: String,
    val currentTopicTitle: String,
    val queueJson: String,
    val rescueQueueJson: String,
    val goalCorrectCount: Int,
    val currentNodeId: String?,
    val currentItemId: String?,
    val currentAttemptIndex: Int,
    val currentPromptKind: String,
    val correctCount: Int,
    val stepIndex: Int,
    val startedAt: Long,
    val lastInteractionAt: Long,
    val isMicroPromptActive: Boolean,
    val isExitArmed: Boolean,
    val exitArmedUntil: Long?,
    val payloadJson: String,
)

@Entity(
    tableName = "review_events",
    indices = [Index("sessionId"), Index("nodeId"), Index("occurredAt")],
)
data class ReviewEventEntity(
    @PrimaryKey val eventId: String,
    val sessionId: String,
    val nodeId: String,
    val itemId: String,
    val answerOutcome: String,
    val latencyMs: Long,
    val surface: String,
    val sessionMode: String,
    val attemptIndex: Int,
    val occurredAt: Long,
)

@Entity(
    tableName = "session_events",
    indices = [Index("sessionId"), Index("topicUnitId"), Index("occurredAt")],
)
data class SessionEventEntity(
    @PrimaryKey val eventId: String,
    val sessionId: String,
    val packetId: String,
    val topicUnitId: String,
    val type: String,
    val payloadJson: String?,
    val occurredAt: Long,
)

@Entity(
    tableName = "abandon_events",
    indices = [Index("sessionId"), Index("nodeId"), Index("occurredAt")],
)
data class AbandonEventEntity(
    @PrimaryKey val eventId: String,
    val sessionId: String,
    val nodeId: String?,
    val itemId: String?,
    val reason: String,
    val occurredAt: Long,
)

@Entity(
    tableName = "import_events",
    indices = [Index("packageId"), Index("createdAt")],
)
data class ImportEventEntity(
    @PrimaryKey val eventId: String,
    val packageId: String,
    val schemaVersion: Int,
    val seedVersion: String?,
    val contentHash: String?,
    val result: String,
    val createdAt: Long,
)
