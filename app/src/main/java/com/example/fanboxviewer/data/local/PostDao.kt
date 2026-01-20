package com.example.fanboxviewer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class PostUserStateRow(
    val postId: String,
    val creatorId: String,
    val isBookmarked: Boolean,
    val isHidden: Boolean,
)

@Dao
interface PostDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(posts: List<PostEntity>)

    @Query(
        "SELECT * FROM posts WHERE creatorId = :creatorId AND isHidden = 0 ORDER BY publishedAt DESC"
    )
    fun observeByCreator(creatorId: String): Flow<List<PostEntity>>

    @Query(
        "SELECT * FROM posts WHERE isBookmarked = 1 AND isHidden = 0 ORDER BY lastOpenedAt DESC, publishedAt DESC"
    )
    fun observeBookmarked(): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts WHERE isHidden = 1 ORDER BY publishedAt DESC")
    fun observeHidden(): Flow<List<PostEntity>>

    @Query("SELECT postId, creatorId, isBookmarked, isHidden FROM posts WHERE isBookmarked = 1 OR isHidden = 1")
    suspend fun listUserState(): List<PostUserStateRow>

    @Query("UPDATE posts SET isBookmarked = :bookmarked WHERE postId = :postId")
    suspend fun setBookmarked(postId: String, bookmarked: Boolean)

    @Query("UPDATE posts SET isHidden = :hidden WHERE postId = :postId")
    suspend fun setHidden(postId: String, hidden: Boolean)

    @Query("UPDATE posts SET isBookmarked = 1 WHERE postId IN (:postIds)")
    suspend fun setBookmarkedBulk(postIds: List<String>): Int

    @Query("UPDATE posts SET isHidden = 1 WHERE postId IN (:postIds)")
    suspend fun setHiddenBulk(postIds: List<String>): Int

    @Query("UPDATE posts SET lastOpenedAt = :ts WHERE postId = :postId")
    suspend fun setLastOpened(postId: String, ts: Long)

    @Query("DELETE FROM posts")
    suspend fun clearAll()

    @Query("DELETE FROM posts WHERE isBookmarked = 0 AND isHidden = 0")
    suspend fun clearNonUserState()
}
