package com.example.fanboxviewer.data.export

import android.content.Context
import android.net.Uri
import com.example.fanboxviewer.data.local.PostTagEntity
import com.example.fanboxviewer.data.local.TagEntity
import com.example.fanboxviewer.data.repo.PostRepository
import com.example.fanboxviewer.data.repo.TagRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

data class ExportSummary(
    val tagCount: Int,
    val postTagCount: Int,
    val postStateCount: Int,
)

data class ImportSummary(
    val tagCount: Int,
    val postTagCount: Int,
    val bookmarkCount: Int,
    val hiddenCount: Int,
    val appliedBookmarks: Int,
    val appliedHidden: Int,
)

data class PostStatePayload(
    val postId: String,
    val creatorId: String,
    val bookmarked: Boolean,
    val hidden: Boolean,
)

object UserDataTransfer {
    private const val EXPORT_TYPE = "fanboxviewer-user-state"
    private const val EXPORT_VERSION = 1

    suspend fun exportToUri(
        context: Context,
        uri: Uri,
        postRepository: PostRepository,
        tagRepository: TagRepository,
    ): ExportSummary = withContext(Dispatchers.IO) {
        val tags = tagRepository.listAllTags()
        val postTags = tagRepository.listAllPostTags()
        val postStates = postRepository.listUserState()
            .map { PostStatePayload(it.postId, it.creatorId, it.isBookmarked, it.isHidden) }
            .filter { it.bookmarked || it.hidden }
        val json = encode(tags, postTags, postStates)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            OutputStreamWriter(out, StandardCharsets.UTF_8).use { writer ->
                writer.write(json)
            }
        } ?: error("Failed to open output stream.")
        ExportSummary(tags.size, postTags.size, postStates.size)
    }

    suspend fun importFromUri(
        context: Context,
        uri: Uri,
        postRepository: PostRepository,
        tagRepository: TagRepository,
    ): ImportSummary = withContext(Dispatchers.IO) {
        val json = context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { it.readText() }
        } ?: error("Failed to open input stream.")
        val payload = decode(json)
        if (payload.tags.isNotEmpty()) tagRepository.insertTags(payload.tags)
        if (payload.postTags.isNotEmpty()) tagRepository.insertPostTags(payload.postTags)
        val bookmarkIds = payload.postStates.filter { it.bookmarked }.map { it.postId }
        val hiddenIds = payload.postStates.filter { it.hidden }.map { it.postId }
        val appliedBookmarks = postRepository.setBookmarkedBulk(bookmarkIds)
        val appliedHidden = postRepository.setHiddenBulk(hiddenIds)
        ImportSummary(
            tagCount = payload.tags.size,
            postTagCount = payload.postTags.size,
            bookmarkCount = bookmarkIds.size,
            hiddenCount = hiddenIds.size,
            appliedBookmarks = appliedBookmarks,
            appliedHidden = appliedHidden,
        )
    }

    private data class DecodedPayload(
        val tags: List<TagEntity>,
        val postTags: List<PostTagEntity>,
        val postStates: List<PostStatePayload>,
    )

    private fun encode(
        tags: List<TagEntity>,
        postTags: List<PostTagEntity>,
        postStates: List<PostStatePayload>,
    ): String {
        val root = JSONObject()
        root.put("type", EXPORT_TYPE)
        root.put("version", EXPORT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        val tagsArray = JSONArray()
        tags.forEach { tag ->
            val obj = JSONObject()
            obj.put("creatorId", tag.creatorId)
            obj.put("name", tag.name)
            obj.put("createdAt", tag.createdAt)
            tagsArray.put(obj)
        }
        root.put("tags", tagsArray)
        val postTagsArray = JSONArray()
        postTags.forEach { tag ->
            val obj = JSONObject()
            obj.put("postId", tag.postId)
            obj.put("creatorId", tag.creatorId)
            obj.put("tagName", tag.tagName)
            postTagsArray.put(obj)
        }
        root.put("postTags", postTagsArray)
        val stateArray = JSONArray()
        postStates.forEach { state ->
            val obj = JSONObject()
            obj.put("postId", state.postId)
            obj.put("creatorId", state.creatorId)
            obj.put("bookmarked", state.bookmarked)
            obj.put("hidden", state.hidden)
            stateArray.put(obj)
        }
        root.put("postStates", stateArray)
        return root.toString(2)
    }

    private fun decode(json: String): DecodedPayload {
        val root = JSONObject(json)
        if (root.optString("type") != EXPORT_TYPE) {
            error("Unsupported export format.")
        }
        val version = root.optInt("version", EXPORT_VERSION)
        if (version != EXPORT_VERSION) {
            error("Unsupported export version: $version")
        }
        val tags = mutableListOf<TagEntity>()
        val tagsArray = root.optJSONArray("tags")
        if (tagsArray != null) {
            for (i in 0 until tagsArray.length()) {
                val obj = tagsArray.optJSONObject(i) ?: continue
                val creatorId = obj.optString("creatorId")
                val name = obj.optString("name")
                if (creatorId.isBlank() || name.isBlank()) continue
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                tags.add(TagEntity(creatorId = creatorId, name = name, createdAt = createdAt))
            }
        }
        val postTags = mutableListOf<PostTagEntity>()
        val postTagsArray = root.optJSONArray("postTags")
        if (postTagsArray != null) {
            for (i in 0 until postTagsArray.length()) {
                val obj = postTagsArray.optJSONObject(i) ?: continue
                val postId = obj.optString("postId")
                val creatorId = obj.optString("creatorId")
                val tagName = obj.optString("tagName")
                if (postId.isBlank() || creatorId.isBlank() || tagName.isBlank()) continue
                postTags.add(PostTagEntity(postId = postId, creatorId = creatorId, tagName = tagName))
            }
        }
        val postStates = mutableListOf<PostStatePayload>()
        val stateArray = root.optJSONArray("postStates")
        if (stateArray != null) {
            for (i in 0 until stateArray.length()) {
                val obj = stateArray.optJSONObject(i) ?: continue
                val postId = obj.optString("postId")
                val creatorId = obj.optString("creatorId")
                if (postId.isBlank()) continue
                val bookmarked = obj.optBoolean("bookmarked", false)
                val hidden = obj.optBoolean("hidden", false)
                if (!bookmarked && !hidden) continue
                postStates.add(
                    PostStatePayload(
                        postId = postId,
                        creatorId = creatorId,
                        bookmarked = bookmarked,
                        hidden = hidden,
                    )
                )
            }
        }
        return DecodedPayload(tags = tags, postTags = postTags, postStates = postStates)
    }
}
