package com.example.fanboxviewer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class PostTagEntry(
    val postId: String,
    val tagName: String,
)

@Dao
interface TagDao {
    @Query("SELECT * FROM creator_tags WHERE creatorId = :creatorId ORDER BY name ASC")
    fun observeTagsForCreator(creatorId: String): Flow<List<TagEntity>>

    @Query("SELECT postId, tagName FROM post_tags WHERE creatorId = :creatorId")
    fun observePostTagsForCreator(creatorId: String): Flow<List<PostTagEntry>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPostTags(tags: List<PostTagEntity>)

    @Query("DELETE FROM post_tags WHERE postId = :postId")
    suspend fun deletePostTagsForPost(postId: String)

    @Transaction
    suspend fun replacePostTags(creatorId: String, postId: String, tagNames: List<String>) {
        deletePostTagsForPost(postId)
        if (tagNames.isEmpty()) return
        val now = System.currentTimeMillis()
        insertTags(tagNames.map { TagEntity(creatorId = creatorId, name = it, createdAt = now) })
        insertPostTags(tagNames.map { PostTagEntity(postId = postId, creatorId = creatorId, tagName = it) })
    }
}
