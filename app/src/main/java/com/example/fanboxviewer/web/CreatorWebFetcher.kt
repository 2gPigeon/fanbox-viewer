package com.example.fanboxviewer.web

import android.content.Context
import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class CreatorWebItem(
    val creatorId: String,
    val name: String,
    val iconUrl: String?,
    val userId: String? = null,
)

class CreatorWebFetcher {
    suspend fun fetchSupporting(context: Context): List<CreatorWebItem> = suspendCancellableCoroutine { cont ->
        val url = "https://www.fanbox.cc/manage/subscriptions"
        val appContext = context.applicationContext
        val webView = WebView(appContext)
        webView.visibility = View.GONE

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        try {
            // Allow third-party cookies just in case the page pulls data across subdomains
            CookieManager::class.java.getMethod("setAcceptThirdPartyCookies", WebView::class.java, Boolean::class.java)
                .invoke(cookieManager, webView, true)
        } catch (_: Throwable) {}

        val done = CompletableDeferred<Unit>()

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.settings.safeBrowsingEnabled = true
        }
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT

        val targets = listOf(
            url,
            "https://www.fanbox.cc/manage/subscriptions?status=supporting",
            "https://www.fanbox.cc/manage/subscriptions/active"
        )
        var currentIndex = 0

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                super.onPageFinished(view, finishedUrl)
                val js = (
                    "(() => {" +
                        "try {" +
                        // capture any anchor containing /@{id}
                        "  const anchors = Array.from(document.querySelectorAll('a[href*=\"/@\"]'));" +
                        "  const items = [];" +
                        "  const seen = new Set();" +
                        "  for (const a of anchors) {" +
                        "    const href = a.getAttribute('href') || '';" +
                        "    const m = href.match(/\\/@([^\\/?#]+)/);" +
                        "    if (!m) continue;" +
                        "    const id = m[1];" +
                        "    if (seen.has(id)) continue;" +
                        "    seen.add(id);" +
                        "    let name = (a.textContent || '').trim();" +
                        "    if (!name) { const t = a.getAttribute('title'); if (t) name = t; }" +
                        "    const img = a.querySelector('img');" +
                        "    const icon = img ? img.src : null;" +
                        "    if (id) { items.push({creatorId: id, name: name || id, iconUrl: icon}); }" +
                        "  }" +
                        "  return JSON.stringify(items);" +
                        "} catch (e) { return JSON.stringify({error: String(e)}); }" +
                    ")()"
                )

                fun pollAndParse(attempt: Int) {
                    if (attempt > 40) {
                        // give up on this URL and try the next one if available
                        if (currentIndex < targets.lastIndex) {
                            currentIndex += 1
                            webView.loadUrl(targets[currentIndex])
                        } else {
                            if (!done.isCompleted) {
                                done.complete(Unit)
                                cont.resume(emptyList())
                            }
                        }
                        return
                    }
                    view?.evaluateJavascript(js) { value ->
                        try {
                            // value is a JSON-formatted result string; when returning a JS string
                            // evaluateJavascript wraps it in quotes. For null/undefined it may be the literal "null".
                            val unwrapped: String? = if (value == null) null else if (value.startsWith("\"") && value.endsWith("\"")) {
                                value.substring(1, value.length - 1)
                                    .replace("\\n", "\n")
                                    .replace("\\\"", "\"")
                            } else value

                            if (unwrapped == null || unwrapped == "null" || unwrapped == "undefined" || unwrapped.isBlank()) {
                                webView.postDelayed({ pollAndParse(attempt + 1) }, 500)
                                return@evaluateJavascript
                            }

                            if (unwrapped.startsWith("{\"error\"")) {
                                // Try a few more times in case the DOM wasn't ready yet
                                if (attempt < 3) {
                                    webView.postDelayed({ pollAndParse(attempt + 1) }, 500)
                                } else {
                                    if (!done.isCompleted) {
                                        done.complete(Unit)
                                        cont.resumeWithException(IllegalStateException(unwrapped))
                                    }
                                }
                                return@evaluateJavascript
                            }

                            val text = unwrapped
                            val arr = JSONArray(text)
                            if (arr.length() == 0) {
                                webView.postDelayed({ pollAndParse(attempt + 1) }, 500)
                            } else {
                                val list = buildList {
                                    for (i in 0 until arr.length()) {
                                        val o = arr.getJSONObject(i)
                                        add(
                                            CreatorWebItem(
                                                creatorId = o.optString("creatorId"),
                                                name = o.optString("name"),
                                                iconUrl = o.optString("iconUrl").ifBlank { null }
                                            )
                                        )
                                    }
                                }
                                if (!done.isCompleted) {
                                    done.complete(Unit)
                                    cont.resume(list)
                                }
                            }
                        } catch (e: Exception) {
                            // If parse failed due to non-array, retry a few times before failing hard
                            if (attempt < 5) {
                                webView.postDelayed({ pollAndParse(attempt + 1) }, 500)
                            } else {
                                if (!done.isCompleted) {
                                    done.complete(Unit)
                                    cont.resumeWithException(e)
                                }
                            }
                        }
                    }
                }

                pollAndParse(0)
            }
        }

        webView.loadUrl(targets.first())

        cont.invokeOnCancellation {
            if (!done.isCompleted) done.complete(Unit)
            webView.destroy()
        }

        done.invokeOnCompletion {
            webView.destroy()
        }
    }
}
