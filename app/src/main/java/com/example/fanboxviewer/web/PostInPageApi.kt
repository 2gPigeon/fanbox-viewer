package com.example.fanboxviewer.web

import android.content.Context
import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

class PostInPageApi {
    suspend fun listPosts(context: Context, creatorId: String, limit: Int = 5000, offset: Int = 0): Pair<List<PostWebItem>, String> = suspendCancellableCoroutine { cont ->
        val appContext = context.applicationContext
        val webView = WebView(appContext)
        webView.visibility = View.GONE

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        try { CookieManager::class.java.getMethod("setAcceptThirdPartyCookies", WebView::class.java, Boolean::class.java).invoke(cookieManager, webView, true) } catch (_: Throwable) {}
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) webView.settings.safeBrowsingEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT

        val logSb = StringBuilder()
        fun jsFetch(creator: String, limit: Int, firstPublished: String?, firstId: String?): String {
            val isNumeric = creator.all { it.isDigit() }
            val firstFieldTs = if (firstPublished.isNullOrBlank()) "" else ",\\\"firstPublishedDatetime\\\":\\\"$firstPublished\\\""
            val firstFieldId = if (firstId.isNullOrBlank()) "" else ",\\\"firstId\\\":\\\"$firstId\\\""
            val body = if (isNumeric) {
                "{" + "\\\"userId\\\":\\\"$creator\\\",\\\"limit\\\":$limit$firstFieldTs$firstFieldId" + "}"
            } else {
                "{" + "\\\"creatorId\\\":\\\"$creator\\\",\\\"limit\\\":$limit$firstFieldTs$firstFieldId" + "}"
            }
            val js = """
                (async () => {
                  try {
                    const getCsrf = () => {
                      try { const m = document.querySelector('meta[name="csrf-token"]'); if (m && m.content) return m.content; } catch {}
                      try { const ls = localStorage.getItem('csrfToken') || localStorage.getItem('csrf_token') || localStorage.getItem('csrf'); if (ls) return ls; } catch {}
                      const ck = document.cookie.split(';').map(s=>s.trim());
                      for (const c of ck) { const i=c.indexOf('='); const k=i>0?c.substring(0,i):c; const v=i>0?c.substring(i+1):''; if (/csrf/i.test(k)) return v; }
                      return null;
                    };
                    const csrf = getCsrf();
                    const res = await fetch('https://api.fanbox.cc/post.listCreator', {
                      method: 'POST',
                      headers: {
                        'Content-Type': 'application/json', 'Accept': 'application/json, text/plain, */*',
                        ${'$'}{csrf != null ? "'x-csrf-token': '"+csrf+"'," : ""}
                      },
                      credentials: 'include',
                      body: '$body'
                    });
                    const text = await res.text();
                    return JSON.stringify({status: res.status, body: text.slice(0, 10000)});
                  } catch (e) { return JSON.stringify({error: String(e)}); }
                })()
            """.trimIndent()
            return js
        }

        fun formatFirstPublished(epochMs: Long): String {
            return try {
                val dt = java.time.Instant.ofEpochMilli(epochMs)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime()
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(dt)
            } catch (_: Exception) { "" }
        }

        val acc = mutableListOf<PostWebItem>()
        var nextFirst: String? = null
        var nextFirstId: String? = null
        var remaining = if (limit <= 0) Int.MAX_VALUE else limit

        fun fetchNextPage() {
            val pageSize = kotlin.math.min(50, remaining)
            webView.evaluateJavascript(jsFetch(creatorId, pageSize, nextFirst, nextFirstId)) { value ->
                val text = if (value != null && value.startsWith("\"") && value.endsWith("\"")) {
                    value.substring(1, value.length - 1).replace("\\n", "\n").replace("\\\"", "\"")
                } else value ?: "{}"
                try {
                    val obj = JSONObject(text)
                    if (obj.has("error")) {
                        logSb.append("WV exception ").append(obj.optString("error")).append('\n')
                        cont.resume(acc.toList() to logSb.toString())
                        webView.destroy()
                        return@evaluateJavascript
                    }
                    val status = obj.optInt("status", -1)
                    val bodyStr = obj.optString("body", "")
                    logSb.append("WV POST post.listCreator -> ").append(status).append(" len=").append(bodyStr.length).append('\n')
                    val parsed = PostApiService(appContext).parsePostsFromApi(bodyStr, creatorId)
                    if (parsed.isEmpty()) {
                        cont.resume(acc.toList() to logSb.toString())
                        webView.destroy()
                        return@evaluateJavascript
                    }
                    acc.addAll(parsed)
                    remaining -= parsed.size
                    if (remaining <= 0 || parsed.size < 50) {
                        cont.resume(acc.toList() to logSb.toString())
                        webView.destroy()
                        return@evaluateJavascript
                    }
                    val last = parsed.last()
                    val fp = formatFirstPublished(last.publishedAt)
                    if (fp.isBlank()) {
                        cont.resume(acc.toList() to logSb.toString())
                        webView.destroy()
                    } else {
                        nextFirst = fp
                        nextFirstId = last.postId
                        fetchNextPage()
                    }
                } catch (e: Exception) {
                    logSb.append("WV parse exception ").append(e.message).append('\n')
                    cont.resume(acc.toList() to logSb.toString())
                    webView.destroy()
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                fetchNextPage()
            }
        }
        webView.loadUrl("https://www.fanbox.cc/")
        cont.invokeOnCancellation { webView.destroy() }
    }
}
