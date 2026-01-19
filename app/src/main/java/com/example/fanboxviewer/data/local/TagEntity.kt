package com.example.fanboxviewer.data.local

import androidx.room.Entity

@Entity(
    tableName = "creator_tags",
    primaryKeys = ["creatorId", "name"],
)
data class TagEntity(
    val creatorId: String,
    val name: String,
    val createdAt: Long,
)
