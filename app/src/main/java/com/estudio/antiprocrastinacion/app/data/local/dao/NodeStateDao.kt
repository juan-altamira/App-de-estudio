package com.estudio.antiprocrastinacion.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.estudio.antiprocrastinacion.app.data.local.db.ItemStateEntity
import com.estudio.antiprocrastinacion.app.data.local.db.NodeFormatStatEntity
import com.estudio.antiprocrastinacion.app.data.local.db.NodeStateEntity

@Dao
interface NodeStateDao {
    @Upsert
    suspend fun upsertNodeState(nodeState: NodeStateEntity)

    @Upsert
    suspend fun upsertItemState(itemState: ItemStateEntity)

    @Upsert
    suspend fun upsertItemStates(itemStates: List<ItemStateEntity>)

    @Upsert
    suspend fun upsertFormatStat(stat: NodeFormatStatEntity)

    @Upsert
    suspend fun upsertFormatStats(stats: List<NodeFormatStatEntity>)

    @Query("SELECT * FROM node_states WHERE nodeId = :nodeId LIMIT 1")
    suspend fun getNodeState(nodeId: String): NodeStateEntity?

    @Query("SELECT * FROM item_states WHERE itemId = :itemId LIMIT 1")
    suspend fun getItemState(itemId: String): ItemStateEntity?

    @Query("SELECT * FROM item_states WHERE itemId IN (:itemIds)")
    suspend fun getItemStates(itemIds: List<String>): List<ItemStateEntity>

    @Query("SELECT * FROM item_states")
    suspend fun getAllItemStates(): List<ItemStateEntity>

    @Query("SELECT * FROM node_states")
    suspend fun getAllNodeStates(): List<NodeStateEntity>

    @Query("SELECT * FROM node_format_stats ORDER BY nodeId, format")
    suspend fun getAllFormatStats(): List<NodeFormatStatEntity>

    @Query("SELECT * FROM node_format_stats WHERE nodeId = :nodeId ORDER BY format")
    suspend fun getFormatStats(nodeId: String): List<NodeFormatStatEntity>

    @Query("DELETE FROM node_states WHERE nodeId = :nodeId")
    suspend fun deleteNodeState(nodeId: String)

    @Query("DELETE FROM item_states WHERE itemId = :itemId")
    suspend fun deleteItemState(itemId: String)

    @Query(
        """
        DELETE FROM item_states
        WHERE itemId IN (
            SELECT itemId FROM items WHERE nodeId = :nodeId
        )
        """,
    )
    suspend fun deleteItemStatesForNode(nodeId: String)

    @Query("DELETE FROM node_format_stats WHERE nodeId = :nodeId")
    suspend fun deleteFormatStats(nodeId: String)

    @Query("DELETE FROM node_states")
    suspend fun deleteAllNodeStates()

    @Query("DELETE FROM item_states")
    suspend fun deleteAllItemStates()

    @Query("DELETE FROM node_format_stats")
    suspend fun deleteAllFormatStats()
}
