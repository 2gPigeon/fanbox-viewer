package com.example.fanboxviewer.data.repo

import com.example.fanboxviewer.data.local.PostDao
import com.example.fanboxviewer.data.local.PostEntity
import com.example.fanboxviewer.data.local.PostUserStateRow
import kotlinx.coroutines.flow.Flow

class PostRepository(private val dao: PostDao) {
    fun observeByCreator(creatorId: String): Flow<List<PostEntity>> = dao.observeByCreator(creatorId)
    fun observeBookmarked(): Flow<List<PostEntity>> = dao.observeBookmarked()
    fun observeHidden(): Flow<List<PostEntity>> = dao.observeHidden()
    suspend fun upsertAll(posts: List<PostEntity>) = dao.upsertAll(posts)
    suspend fun upsertAllPreservingUserState(posts: List<PostEntity>) {
        if (posts.isEmpty()) return
        val states = dao.listUserStateByIds(posts.map { it.postId }).associateBy { it.postId }
        val merged = posts.map { post ->
            val state = states[post.postId]
            if (state == null) post else post.copy(
                isBookmarked = state.isBookmarked,
                isHidden = state.isHidden,
                lastOpenedAt = state.lastOpenedAt
            )
        }
        dao.upsertAll(merged)
    }
    suspend fun listUserState(): List<PostUserStateRow> = dao.listUserState()
    suspend fun setBookmarked(postId: String, value: Boolean) = dao.setBookmarked(postId, value)
    suspend fun setHidden(postId: String, value: Boolean) = dao.setHidden(postId, value)
    suspend fun setBookmarkedBulk(postIds: List<String>): Int {
        if (postIds.isEmpty()) return 0
        return dao.setBookmarkedBulk(postIds)
    }
    suspend fun setHiddenBulk(postIds: List<String>): Int {
        if (postIds.isEmpty()) return 0
        return dao.setHiddenBulk(postIds)
    }
    suspend fun setLastOpened(postId: String, ts: Long) = dao.setLastOpened(postId, ts)
    suspend fun clearAll() = dao.clearAll()
    suspend fun clearNonUserState() = dao.clearNonUserState()
}
