package com.example.fanboxviewer.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "posts",
    indices = [Index(value = ["creatorId"])],
)
data class PostEntity(
    @PrimaryKey val postId: String,
    val creatorId: String,
    val title: String,
    val summary: String?,
    val url: String,
    val thumbnailUrl: String?,
    val publishedAt: Long, // epoch millis
    val isBookmarked: Boolean = false,
    val isHidden: Boolean = false,
    val lastOpenedAt: Long? = null,
)

