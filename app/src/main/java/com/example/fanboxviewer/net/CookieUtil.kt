package com.example.fanboxviewer.net

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings

object CookieUtil {
    fun buildCookieHeaderFor(vararg urls: String): String? {
        val cm = CookieManager.getInstance()
        val parts = mutableListOf<String>()
        urls.forEach { url ->
            val c = cm.getCookie(url)
            if (!c.isNullOrBlank()) parts += c
        }
        if (parts.isEmpty()) return null
        // merge, avoid duplicate cookie pairs
        val seen = LinkedHashSet<String>()
        parts.joinToString("; ").split(';').map { it.trim() }.forEach { pair ->
            if (pair.isNotBlank()) seen.add(pair)
        }
        return seen.joinToString("; ")
    }

    fun defaultUserAgent(context: Context): String = WebSettings.getDefaultUserAgent(context)

    fun parseCookieMap(cookieHeader: String?): Map<String, String> {
        if (cookieHeader.isNullOrBlank()) return emptyMap()
        val map = LinkedHashMap<String, String>()
        cookieHeader.split(';').forEach { part ->
            val p = part.trim()
            val idx = p.indexOf('=')
            if (idx > 0) {
                val k = p.substring(0, idx).trim()
                val v = p.substring(idx + 1)
                if (k.isNotBlank()) map[k] = v
            }
        }
        return map
    }
}
