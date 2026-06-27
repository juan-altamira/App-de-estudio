package com.estudio.antiprocrastinacion.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.estudio.antiprocrastinacion.app.data.local.db.ActiveSessionEntity

@Dao
interface SessionDao {
    @Upsert
    suspend fun upsertActiveSession(session: ActiveSessionEntity)

    @Query("SELECT * FROM active_sessions WHERE mode = :mode LIMIT 1")
    suspend fun getActiveSession(mode: String): ActiveSessionEntity?

    @Query("SELECT * FROM active_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getActiveSessionById(sessionId: String): ActiveSessionEntity?

    @Query("SELECT * FROM active_sessions ORDER BY lastInteractionAt DESC")
    suspend fun getActiveSessions(): List<ActiveSessionEntity>

    @Query("DELETE FROM active_sessions WHERE mode = :mode")
    suspend fun clearActiveSession(mode: String)

    @Query("DELETE FROM active_sessions")
    suspend fun clearActiveSessions()
}
