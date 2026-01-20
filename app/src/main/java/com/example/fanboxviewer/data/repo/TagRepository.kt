package com.example.fanboxviewer.data.repo

import com.example.fanboxviewer.data.local.TagDao
import com.example.fanboxviewer.data.local.TagEntity
import com.example.fanboxviewer.data.local.PostTagEntry
import com.example.fanboxviewer.data.local.PostTagEntity
import kotlinx.coroutines.flow.Flow

class TagRepository(private val dao: TagDao) {
    fun observeTagsForCreator(creatorId: String): Flow<List<TagEntity>> =
        dao.observeTagsForCreator(creatorId)

    fun observePostTagsForCreator(creatorId: String): Flow<List<PostTagEntry>> =
        dao.observePostTagsForCreator(creatorId)

    suspend fun listAllTags(): List<TagEntity> = dao.listAllTags()

    suspend fun listAllPostTags(): List<PostTagEntity> = dao.listAllPostTags()

    suspend fun insertTags(tags: List<TagEntity>) = dao.insertTags(tags)

    suspend fun insertPostTags(tags: List<PostTagEntity>) = dao.insertPostTags(tags)

    suspend fun replacePostTags(creatorId: String, postId: String, tagNames: List<String>) {
        val normalized = tagNames
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        dao.replacePostTags(creatorId, postId, normalized)
    }
}
