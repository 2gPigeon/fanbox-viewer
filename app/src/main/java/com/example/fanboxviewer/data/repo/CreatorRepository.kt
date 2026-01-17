package com.example.fanboxviewer.data.repo

import com.example.fanboxviewer.data.local.CreatorDao
import com.example.fanboxviewer.data.local.CreatorEntity
import kotlinx.coroutines.flow.Flow

class CreatorRepository(private val dao: CreatorDao) {
    fun observeSupporting(): Flow<List<CreatorEntity>> = dao.observeSupporting()
    suspend fun upsertAll(creators: List<CreatorEntity>) = dao.upsertAll(creators)
    suspend fun clearAll() = dao.clearAll()
}
