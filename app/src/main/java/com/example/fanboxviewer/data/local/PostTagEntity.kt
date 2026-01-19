package com.example.fanboxviewer.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "post_tags",
    primaryKeys = ["postId", "tagName"],
    indices = [Index(value = ["creatorId"]), Index(value = ["postId"])],
)
data class PostTagEntity(
    val postId: String,
    val creatorId: String,
    val tagName: String,
)
