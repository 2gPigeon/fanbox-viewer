package com.example.fanboxviewer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "creators")
data class CreatorEntity(
    @PrimaryKey val creatorId: String,
    val userId: String? = null,
    val name: String,
    val iconUrl: String?,
    val isSupporting: Boolean = true,
    val lastSyncedAt: Long? = null,
)
