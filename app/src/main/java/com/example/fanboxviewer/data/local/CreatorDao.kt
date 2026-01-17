package com.example.fanboxviewer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CreatorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(creators: List<CreatorEntity>)

    @Query("SELECT * FROM creators WHERE isSupporting = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeSupporting(): Flow<List<CreatorEntity>>

    @Query("SELECT * FROM creators WHERE creatorId = :id")
    suspend fun getById(id: String): CreatorEntity?

    @Query("UPDATE creators SET lastSyncedAt = :ts WHERE creatorId IN (:ids)")
    suspend fun updateLastSynced(ids: List<String>, ts: Long)

    @Query("DELETE FROM creators")
    suspend fun clearAll()
}
