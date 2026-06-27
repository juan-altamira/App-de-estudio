package com.estudio.antiprocrastinacion.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.estudio.antiprocrastinacion.app.data.local.db.AbandonEventEntity
import com.estudio.antiprocrastinacion.app.data.local.db.ImportEventEntity
import com.estudio.antiprocrastinacion.app.data.local.db.ReviewEventEntity
import com.estudio.antiprocrastinacion.app.data.local.db.SessionEventEntity

@Dao
interface EventDao {
    @Upsert
    suspend fun insertReviewEvent(event: ReviewEventEntity)

    @Upsert
    suspend fun insertSessionEvent(event: SessionEventEntity)

    @Upsert
    suspend fun insertAbandonEvent(event: AbandonEventEntity)

    @Upsert
    suspend fun insertImportEvent(event: ImportEventEntity)

    @Query("SELECT * FROM abandon_events WHERE occurredAt >= :since")
    suspend fun getAbandonEventsSince(since: Long): List<AbandonEventEntity>

    @Query(
        """
        SELECT topicUnitId FROM session_events
        WHERE type IN ('SESSION_STARTED', 'SESSION_RESUMED')
        ORDER BY occurredAt DESC
        LIMIT 2
        """
    )
    suspend fun getRecentTopicIds(): List<String>

    @Query("SELECT * FROM import_events WHERE packageId = :packageId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestImportEvent(packageId: String): ImportEventEntity?

    @Query(
        """
        SELECT
            itemId AS itemId,
            COUNT(*) AS attempts,
            SUM(CASE WHEN answerOutcome IN ('CORRECT_FIRST_TRY', 'CORRECT_AFTER_RESCUE') THEN 1 ELSE 0 END) AS successes
        FROM review_events
        WHERE itemId IN (:itemIds)
        GROUP BY itemId
        """,
    )
    suspend fun getItemPerformanceRows(itemIds: List<String>): List<ItemPerformanceRow>

    @Query(
        """
        SELECT
            itemId AS itemId,
            COUNT(*) AS attempts
        FROM review_events
        WHERE itemId IN (:itemIds) AND occurredAt < :before
        GROUP BY itemId
        """,
    )
    suspend fun getHistoricalAttemptRows(itemIds: List<String>, before: Long): List<ItemAttemptCountRow>

    @Query(
        """
        SELECT COUNT(*)
        FROM review_events
        WHERE sessionId = :sessionId
          AND itemId = :itemId
          AND attemptIndex = 1
          AND answerOutcome IN ('CORRECT_FIRST_TRY', 'CORRECT_AFTER_RESCUE')
        """,
    )
    suspend fun getSessionScheduledSuccessCount(sessionId: String, itemId: String): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM (
            SELECT itemId, MAX(occurredAt) AS latestScheduledAttemptAt
            FROM review_events
            WHERE sessionId = :sessionId AND attemptIndex = 1
            GROUP BY itemId
        ) latest
        JOIN review_events events
          ON events.sessionId = :sessionId
         AND events.itemId = latest.itemId
         AND events.occurredAt = latest.latestScheduledAttemptAt
         AND events.attemptIndex = 1
        WHERE events.answerOutcome = 'INCORRECT'
        """,
    )
    suspend fun getPendingFailedScheduledItemCount(sessionId: String): Int
}

data class ItemPerformanceRow(
    val itemId: String,
    val attempts: Int,
    val successes: Int,
)

data class ItemAttemptCountRow(
    val itemId: String,
    val attempts: Int,
)
