package com.example.fanboxviewer.web

import android.content.Context
import com.example.fanboxviewer.net.ApiClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

data class PostWebItem(
    val postId: String,
    val creatorId: String,
    val title: String,
    val summary: String?,
    val url: String,
    val thumb: String?,
    val publishedAt: Long,
)

class PostApiService(private val context: Context) {
    private val client = ApiClient(context)

    fun fetchPostsForCreatorWithDebug(creatorId: String, limit: Int = 5000, offset: Int = 0): Pair<List<PostWebItem>, String> {
        val log = StringBuilder()
        data class Endpoint(val url: String, val method: String, val body: String?)
        val isNumericId = creatorId.all { it.isDigit() }
        val bodyJsonCreator = "{" + "\"creatorId\":\"$creatorId\",\"limit\":$limit,\"offset\":$offset" + "}"
        val bodyJsonUser = "{" + "\"userId\":\"$creatorId\",\"limit\":$limit,\"offset\":$offset" + "}"
        val candidates = listOf(
            Endpoint("https://api.fanbox.cc/post.listCreator?creatorId=$creatorId&sort=newest&limit=$limit", "GET", null),
            Endpoint("https://api.fanbox.cc/post.listCreator?userId=$creatorId&sort=newest&limit=$limit", "GET", null),
            Endpoint("https://api.fanbox.cc/post.listCreator", "POST", if (isNumericId) bodyJsonUser else bodyJsonCreator),
            Endpoint("https://api.fanbox.cc/post.listCreator", "POST", bodyJsonCreator),
            Endpoint("https://api.fanbox.cc/post.listCreator", "POST", bodyJsonUser),
        )
        for (ep in candidates) {
            try {
                val builder = Request.Builder().url(ep.url.toHttpUrl())
                if (ep.method == "POST") {
                    val media = "application/json; charset=utf-8".toMediaType()
                    val body = (ep.body ?: "{}").toRequestBody(media)
                    builder.post(body).header("Content-Type", "application/json")
                } else builder.get()
                val req = builder.build()
                client.execute(req).use { resp ->
                    val code = resp.code
                    log.append(ep.method).append(' ').append(ep.url).append(" -> ").append(code).append('\n')
                    val txt = resp.body?.string() ?: ""
                    log.append("len=").append(txt.length).append(" snippet=").append(txt.take(200)).append('\n')
                    if (!resp.isSuccessful) return@use
                    val parsed = parsePostsFromApi(txt, creatorId)
                    if (parsed.isNotEmpty()) return parsed to log.toString()
                }
            } catch (e: Exception) {
                log.append("exception ").append(e::class.java.simpleName).append(':').append(e.message).append(" @ ").append(ep.url).append('\n')
            }
        }
        return emptyList<PostWebItem>() to log.toString()
    }

    internal fun parsePostsFromApi(body: String, creatorId: String): List<PostWebItem> {
        if (body.isBlank()) return emptyList()
        return try {
            val root = JSONObject(body)
            val arrays = mutableListOf<JSONArray>()
            fun addIfArray(obj: JSONObject, name: String) {
                if (obj.has(name) && obj.opt(name) is JSONArray) arrays += obj.getJSONArray(name)
            }
            if (root.has("body")) {
                val b = root.get("body")
                when (b) {
                    is JSONArray -> arrays += b
                    is JSONObject -> {
                        listOf("items", "posts", "data", "list").forEach { addIfArray(b, it) }
                    }
                }
            }
            listOf("items", "posts", "data", "list").forEach { addIfArray(root, it) }

            val arr = arrays.firstOrNull() ?: return emptyList()
            val list = mutableListOf<PostWebItem>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                extractPost(o, creatorId)?.let { list += it }
            }
            list
        } catch (_: Exception) { emptyList() }
    }

    private fun extractPost(o: JSONObject, fallbackCreatorId: String): PostWebItem? {
        val id = o.optString("id", o.optString("postId", "")).ifBlank { return null }
        val creator = when {
            o.has("creatorId") -> o.optString("creatorId")
            o.has("creator") && o.opt("creator") is JSONObject -> o.getJSONObject("creator").optString("creatorId")
            o.has("user") && o.opt("user") is JSONObject -> o.getJSONObject("user").optString("creatorId", o.getJSONObject("user").optString("userId"))
            else -> fallbackCreatorId
        }.ifBlank { fallbackCreatorId }
        val title = o.optString("title", id)
        val summary = when {
            o.has("excerpt") -> o.optString("excerpt")
            o.has("summary") -> o.optString("summary")
            else -> null
        }
        val thumb = when {
            o.has("coverImageUrl") -> o.optString("coverImageUrl")
            o.has("cover") && o.opt("cover") is JSONObject -> o.getJSONObject("cover").optString("url")
            o.has("thumbnailUrl") -> o.optString("thumbnailUrl")
            else -> null
        }
        val publishedAt = parseEpoch(o)
        val url = "https://www.fanbox.cc/@$creator/posts/$id"
        return PostWebItem(id, creator, title, summary, url, thumb?.ifBlank { null }, publishedAt)
    }

    private fun parseEpoch(o: JSONObject): Long {
        val keys = listOf("publishedDatetime", "publishedAt", "updatedDatetime", "createdDatetime")
        for (k in keys) {
            val v = o.optString(k, null)
            if (!v.isNullOrBlank()) {
                try {
                    return OffsetDateTime.parse(v, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli()
                } catch (_: Exception) {}
                try { return Instant.parse(v).toEpochMilli() } catch (_: Exception) {}
            }
        }
        val numKeys = listOf("publishedAt", "publishedTime", "updatedAt")
        for (k in numKeys) {
            val n = o.optLong(k, Long.MIN_VALUE)
            if (n != Long.MIN_VALUE && n > 0) {
                return if (n > 3_000_000_000L) n else n * 1000
            }
        }
        return System.currentTimeMillis()
    }
}
