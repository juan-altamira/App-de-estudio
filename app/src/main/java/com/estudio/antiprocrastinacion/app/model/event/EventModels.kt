package com.estudio.antiprocrastinacion.app.model.event

import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.content.Surface
import kotlinx.serialization.Serializable

@Serializable
enum class AttemptOutcome {
    CORRECT_FIRST_TRY,
    CORRECT_AFTER_RESCUE,
    INCORRECT,
}

@Serializable
enum class SessionEventType {
    SESSION_STARTED,
    SESSION_RESUMED,
    SESSION_COMPLETED,
    SESSION_EXITED,
    BACK_MICRO_TRIGGERED,
}

@Serializable
enum class AbandonReason {
    BACK,
    CLOSE,
    TIMEOUT,
}

@Serializable
enum class ImportResultType {
    SUCCESS,
    FAILURE,
    WARNING,
}

data class ReviewEvent(
    val eventId: String,
    val sessionId: String,
    val nodeId: String,
    val itemId: String,
    val answerOutcome: AttemptOutcome,
    val latencyMs: Long,
    val surface: Surface,
    val sessionMode: SessionMode,
    val attemptIndex: Int,
    val occurredAt: Long,
)

data class SessionEvent(
    val eventId: String,
    val sessionId: String,
    val packetId: String,
    val topicUnitId: String,
    val type: SessionEventType,
    val payloadJson: String?,
    val occurredAt: Long,
)

data class AbandonEvent(
    val eventId: String,
    val sessionId: String,
    val nodeId: String?,
    val itemId: String?,
    val reason: AbandonReason,
    val occurredAt: Long,
)

data class ImportEvent(
    val eventId: String,
    val packageId: String,
    val schemaVersion: Int,
    val seedVersion: String?,
    val contentHash: String?,
    val result: ImportResultType,
    val createdAt: Long,
)
