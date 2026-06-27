package com.estudio.antiprocrastinacion.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.estudio.antiprocrastinacion.app.data.local.db.CourseEntity
import com.estudio.antiprocrastinacion.app.data.local.db.ItemEntity
import com.estudio.antiprocrastinacion.app.data.local.db.ItemOverrideEntity
import com.estudio.antiprocrastinacion.app.data.local.db.NodeEntity
import com.estudio.antiprocrastinacion.app.data.local.db.OutcomeEntity
import com.estudio.antiprocrastinacion.app.data.local.db.UnitEntity

@Dao
interface ContentDao {
    @Upsert
    suspend fun upsertCourses(courses: List<CourseEntity>)

    @Upsert
    suspend fun upsertUnits(units: List<UnitEntity>)

    @Upsert
    suspend fun upsertOutcomes(outcomes: List<OutcomeEntity>)

    @Upsert
    suspend fun upsertNodes(nodes: List<NodeEntity>)

    @Upsert
    suspend fun upsertItems(items: List<ItemEntity>)

    @Upsert
    suspend fun upsertItemOverrides(overrides: List<ItemOverrideEntity>)

    @Query("SELECT * FROM courses ORDER BY title")
    suspend fun getCourses(): List<CourseEntity>

    @Query("SELECT * FROM units ORDER BY courseId, orderIndex, title")
    suspend fun getUnits(): List<UnitEntity>

    @Query("SELECT * FROM outcomes ORDER BY unitId, title")
    suspend fun getOutcomes(): List<OutcomeEntity>

    @Query("SELECT * FROM nodes ORDER BY unitId, title")
    suspend fun getNodes(): List<NodeEntity>

    @Query("SELECT * FROM nodes WHERE archivedCandidate = 0 ORDER BY unitId, title")
    suspend fun getSchedulableNodes(): List<NodeEntity>

    @Query("SELECT * FROM nodes WHERE archivedCandidate = 0 AND unitId = :unitId ORDER BY title")
    suspend fun getSchedulableNodesByUnit(unitId: String): List<NodeEntity>

    @Query("SELECT * FROM nodes WHERE nodeId = :nodeId LIMIT 1")
    suspend fun getNodeById(nodeId: String): NodeEntity?

    @Query("SELECT * FROM nodes WHERE nodeId IN (:nodeIds)")
    suspend fun getNodesByIds(nodeIds: List<String>): List<NodeEntity>

    @Query("SELECT * FROM nodes WHERE contentOrigin = :contentOrigin")
    suspend fun getNodesByOrigin(contentOrigin: String): List<NodeEntity>

    @Query("SELECT * FROM items WHERE nodeId = :nodeId ORDER BY frictionLevel, itemRole")
    suspend fun getItemsForNode(nodeId: String): List<ItemEntity>

    @Query("SELECT * FROM items WHERE nodeId IN (:nodeIds) ORDER BY nodeId, frictionLevel, itemRole")
    suspend fun getItemsForNodes(nodeIds: List<String>): List<ItemEntity>

    @Query("SELECT * FROM items WHERE itemId = :itemId LIMIT 1")
    suspend fun getItemById(itemId: String): ItemEntity?

    @Query("SELECT * FROM items")
    suspend fun getAllItems(): List<ItemEntity>

    @Query("SELECT * FROM item_overrides WHERE itemId = :itemId LIMIT 1")
    suspend fun getItemOverrideById(itemId: String): ItemOverrideEntity?

    @Query("SELECT * FROM item_overrides WHERE itemId IN (:itemIds)")
    suspend fun getItemOverrides(itemIds: List<String>): List<ItemOverrideEntity>

    @Query("SELECT * FROM item_overrides")
    suspend fun getAllItemOverrides(): List<ItemOverrideEntity>

    @Query("UPDATE nodes SET archivedCandidate = :archived WHERE nodeId = :nodeId")
    suspend fun setArchivedCandidate(nodeId: String, archived: Boolean)

    @Query("DELETE FROM item_overrides WHERE itemId = :itemId")
    suspend fun deleteItemOverride(itemId: String)

    @Query("SELECT COUNT(*) FROM nodes WHERE contentOrigin = :contentOrigin AND archivedCandidate = 0")
    suspend fun countActiveNodesByOrigin(contentOrigin: String): Int
}
