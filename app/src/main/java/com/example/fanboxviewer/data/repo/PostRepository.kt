package com.example.fanboxviewer.data.repo

import com.example.fanboxviewer.data.local.PostDao
import com.example.fanboxviewer.data.local.PostEntity
import kotlinx.coroutines.flow.Flow

class PostRepository(private val dao: PostDao) {
    fun observeByCreator(creatorId: String): Flow<List<PostEntity>> = dao.observeByCreator(creatorId)
    fun observeBookmarked(): Flow<List<PostEntity>> = dao.observeBookmarked()
    fun observeHidden(): Flow<List<PostEntity>> = dao.observeHidden()
    suspend fun upsertAll(posts: List<PostEntity>) = dao.upsertAll(posts)
    suspend fun setBookmarked(postId: String, value: Boolean) = dao.setBookmarked(postId, value)
    suspend fun setHidden(postId: String, value: Boolean) = dao.setHidden(postId, value)
    suspend fun setLastOpened(postId: String, ts: Long) = dao.setLastOpened(postId, ts)
    suspend fun clearAll() = dao.clearAll()
    suspend fun clearNonUserState() = dao.clearNonUserState()
}
