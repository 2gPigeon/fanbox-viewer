package com.example.fanboxviewer.web

import android.content.Context
import com.example.fanboxviewer.net.ApiClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class CreatorApiService(private val context: Context) {
    private val client = ApiClient(context)

    fun fetchSupportingCreators(): List<CreatorWebItem> {
        // Try known likely endpoints in order
        val candidates = listOf(
            "https://api.fanbox.cc/plan.listSupporting",
            "https://api.fanbox.cc/creator.listSupporting",
        )
        val errors = mutableListOf<String>()
        for (url in candidates) {
            try {
                val req = Request.Builder().url(url.toHttpUrl()).get().build()
                client.execute(req).use { resp ->
                    if (!resp.isSuccessful) {
                        errors += "${resp.code} ${resp.message} @ $url"
                        return@use
                    }
                    val body = resp.body?.string() ?: ""
                    val parsed = parseCreatorsFromApi(body)
                    if (parsed.isNotEmpty()) return parsed
                    errors += "empty @ $url"
                }
            } catch (e: Exception) {
                errors += "exception ${e.message} @ $url"
            }
        }
        if (errors.isNotEmpty()) {
            android.util.Log.w("CreatorAPI", "fetchSupportingCreators failed: ${errors.joinToString(" | ")}")
        }
        return emptyList()
    }

    fun fetchSupportingCreatorsWithDebug(): Pair<List<CreatorWebItem>, String> {
        val log = StringBuilder()
        data class Endpoint(val url: String, val method: String, val body: String?)
        val candidates = listOf(
            Endpoint("https://api.fanbox.cc/plan.listSupporting", "POST", "{}"),
            Endpoint("https://api.fanbox.cc/creator.listSupporting", "POST", "{}"),
            Endpoint("https://api.fanbox.cc/plan.listSupporting", "GET", null),
            Endpoint("https://api.fanbox.cc/creator.listSupporting", "GET", null),
        )
        for (ep in candidates) {
            try {
                val builder = Request.Builder().url(ep.url.toHttpUrl())
                if (ep.method == "POST") {
                    val media = "application/json; charset=utf-8".toMediaType()
                    val body = (ep.body ?: "{}").toRequestBody(media)
                    builder.post(body).header("Content-Type", "application/json")
                } else {
                    builder.get()
                }
                val req = builder.build()
                client.execute(req).use { resp ->
                    val code = resp.code
                    log.append(ep.method).append(' ').append(ep.url).append(" -> ").append(code).append('\n')
                    val body = resp.body?.string() ?: ""
                    log.append("len=").append(body.length).append(" snippet=").append(body.take(200)).append('\n')
                    // Attach a larger raw slice for debugging keyword search
                    if (body.isNotBlank()) {
                        log.append("RAW:").append(body.take(4000)).append('\n')
                    }
                    if (!resp.isSuccessful) return@use
                    // Log top-level keys for debugging
                    try {
                        val root = JSONObject(body)
                        log.append("rootKeys=").append(root.keys().asSequence().toList()).append('\n')
                        if (root.has("body")) {
                            val b = root.get("body")
                            when (b) {
                                is JSONObject -> log.append("body.keys=").append(b.keys().asSequence().toList()).append('\n')
                                is JSONArray -> log.append("body.length=").append(b.length()).append('\n')
                            }
                        }
                    } catch (_: Exception) {}

                    val parsed = parseCreatorsFromApi(body).ifEmpty {
                        parseCreatorsHeuristically(body)
                    }
                    if (parsed.isNotEmpty()) return parsed to log.toString()
                }
            } catch (e: Exception) {
                log.append("exception ").append(e::class.java.simpleName).append(':').append(e.message).append(" @ ").append(ep.url).append('\n')
            }
        }
        return emptyList<CreatorWebItem>() to log.toString()
    }

    internal fun parseCreatorsFromApi(body: String): List<CreatorWebItem> {
        if (body.isBlank()) return emptyList()
        return try {
            val root = JSONObject(body)
            // Try common envelopes: { body: { items: [...] } } or { items: [...] }
            val candidates = mutableListOf<JSONArray>()
            if (root.has("body")) {
                val b = root.get("body")
                when (b) {
                    is JSONObject -> {
                        val names = listOf("items", "plans", "supportings", "supporting", "supportingCreators", "creators", "data")
                        names.forEach { n -> if (b.has(n) && b.opt(n) is JSONArray) candidates += b.getJSONArray(n) }
                    }
                    is JSONArray -> candidates += b
                }
            }
            val topNames = listOf("items", "plans", "supportings", "supporting", "supportingCreators", "creators", "data")
            topNames.forEach { n -> if (root.has(n) && root.opt(n) is JSONArray) candidates += root.getJSONArray(n) }

            val arr = candidates.firstOrNull() ?: return emptyList()
            val list = mutableListOf<CreatorWebItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                extractCreatorFromAny(o)?.let { list += it }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseCreatorsHeuristically(body: String): List<CreatorWebItem> {
        return try {
            val root = JSONObject(body)
            val arrays = mutableListOf<JSONArray>()
            fun collectArrays(obj: JSONObject) {
                obj.keys().forEach { k ->
                    when (val v = obj.opt(k)) {
                        is JSONArray -> arrays += v
                        is JSONObject -> collectArrays(v)
                    }
                }
            }
            collectArrays(root)
            for (arr in arrays) {
                val list = mutableListOf<CreatorWebItem>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    extractCreatorFromAny(o)?.let { list += it }
                }
                if (list.isNotEmpty()) return list
            }
            emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun extractCreatorFromAny(o: JSONObject): CreatorWebItem? {
        // Prefer top-level creatorId if present (e.g. { "user": {...}, "creatorId": "handle" })
        val topHandle = o.optString("creatorId").ifBlank { null }
        val creatorObj = if (o.has("creator") && o.opt("creator") is JSONObject) o.getJSONObject("creator") else null
        val userObj = if (o.has("user") && o.opt("user") is JSONObject) o.getJSONObject("user") else null

        val handle = topHandle
            ?: creatorObj?.optString("creatorId")?.takeIf { it.isNotBlank() }
            ?: userObj?.optString("creatorId")?.takeIf { it.isNotBlank() }

        val uid = (userObj?.optString("userId") ?: creatorObj?.optString("userId") ?: o.optString("userId"))
            .takeIf { it.isNotBlank() }

        val displayObj = creatorObj ?: userObj ?: o
        val idForName = handle ?: uid ?: displayObj.optString("id")
        if (idForName.isNullOrBlank()) return null

        val name = displayObj.optString("name", displayObj.optString("displayName", idForName))
        val icon = when {
            displayObj.has("iconUrl") -> displayObj.optString("iconUrl")
            displayObj.has("icon") && displayObj.opt("icon") is JSONObject -> displayObj.getJSONObject("icon").optString("url")
            else -> null
        }

        val finalHandle = handle ?: idForName
        return CreatorWebItem(
            creatorId = finalHandle,
            name = name.ifBlank { finalHandle },
            iconUrl = icon?.ifBlank { null },
            userId = uid
        )
    }

    fun resolveCreatorIds(inputId: String): Triple<String?, String?, String> {
        // Returns (creatorHandle, userId, debugLog)
        val log = StringBuilder()
        val endpoints = buildList {
            val isNumeric = inputId.all { it.isDigit() }
            if (isNumeric) add("https://api.fanbox.cc/creator.get?userId=$inputId")
            add("https://api.fanbox.cc/creator.get?creatorId=$inputId")
        }
        for (url in endpoints) {
            try {
                val req = okhttp3.Request.Builder().url(url.toHttpUrl()).get().build()
                client.execute(req).use { resp ->
                    log.append("GET ").append(url).append(" -> ").append(resp.code).append('\n')
                    val txt = resp.body?.string() ?: ""
                    log.append("len=").append(txt.length).append(" snippet=").append(txt.take(200)).append('\n')
                    if (!resp.isSuccessful) return@use
                    try {
                        val root = JSONObject(txt)
                        val b = root.opt("body")
                        if (b is JSONObject) {
                            val handle = b.optString("creatorId").ifBlank { null }
                            val user = b.opt("user")
                            val uid = if (user is JSONObject) user.optString("userId").ifBlank { null } else null
                            if (handle != null || uid != null) return Triple(handle, uid, log.toString())
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                log.append("exception ").append(e::class.java.simpleName).append(':').append(e.message).append(" @ ").append(url).append('\n')
            }
        }
        return Triple(null, null, log.toString())
    }
}
