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
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class InPageApi {
    suspend fun listSupportingCreators(context: Context): Pair<List<CreatorWebItem>, String> = suspendCancellableCoroutine { cont ->
        val appContext = context.applicationContext
        val webView = WebView(appContext)
        webView.visibility = View.GONE

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        try {
            CookieManager::class.java.getMethod("setAcceptThirdPartyCookies", WebView::class.java, Boolean::class.java)
                .invoke(cookieManager, webView, true)
        } catch (_: Throwable) {}

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) webView.settings.safeBrowsingEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT

        val logSb = StringBuilder()
        val endpoints = listOf(
            Triple("POST", "https://api.fanbox.cc/plan.listSupporting", "{}"),
            Triple("POST", "https://api.fanbox.cc/creator.listSupporting", "{}"),
        )

        fun jsFetch(method: String, url: String, body: String): String {
            val escapedBody = body.replace("\\", "\\\\").replace("\"", "\\\"")
            val js = """
                (async () => {
                  try {
                    const getCsrf = () => {
                      try {
                        const m = document.querySelector('meta[name="csrf-token"]');
                        if (m && m.content) return m.content;
                      } catch {}
                      try {
                        const ck = document.cookie.split(';').map(s=>s.trim());
                        for (const c of ck) {
                          const i = c.indexOf('=');
                          const k = i>0 ? c.substring(0,i) : c;
                          const v = i>0 ? c.substring(i+1) : '';
                          if (/csrf/i.test(k)) return v;
                        }
                      } catch {}
                      try {
                        const ls = localStorage.getItem('csrfToken') || localStorage.getItem('csrf_token') || localStorage.getItem('csrf');
                        if (ls) return ls;
                      } catch {}
                      return null;
                    };
                    const csrf = getCsrf();
                    const res = await fetch("$url", {
                      method: "$method",
                      headers: {
                        'Content-Type': 'application/json',
                        'Accept': 'application/json, text/plain, */*',
                        ${'$'}{csrf != null ? "'x-csrf-token': '"+csrf+"'," : ""}
                      },
                      credentials: 'include',
                      body: ${if (method == "POST") "\"$escapedBody\"" else "undefined"}
                    });
                    const text = await res.text();
                    return JSON.stringify({status: res.status, body: text.slice(0, 10000)});
                  } catch (e) {
                    return JSON.stringify({error: String(e)});
                  }
                })()
            """.trimIndent()
            return js
        }

        var idx = 0
        fun tryNextFetch() {
            if (idx >= endpoints.size) {
                cont.resume(emptyList<CreatorWebItem>() to logSb.toString())
                webView.destroy()
                return
            }
            val (method, url, body) = endpoints[idx++]
            val js = jsFetch(method, url, body)
            webView.evaluateJavascript(js) { value ->
                try {
                    val text = if (value != null && value.startsWith("\"") && value.endsWith("\"")) {
                        value.substring(1, value.length - 1).replace("\\n", "\n").replace("\\\"", "\"")
                    } else value ?: "{}"
                    val obj = JSONObject(text)
                    if (obj.has("error")) {
                        logSb.append("WV exception ").append(obj.optString("error")).append(" @ ").append(url).append('\n')
                        tryNextFetch()
                        return@evaluateJavascript
                    }
                    val status = obj.optInt("status", -1)
                    val bodyStr = obj.optString("body", "")
                    logSb.append("WV ").append(method).append(' ').append(url).append(" -> ").append(status)
                        .append(" len=").append(bodyStr.length).append('\n')
                    // Attach raw slice for visibility
                    logSb.append("RAW:").append(bodyStr.take(4000)).append('\n')
                    val parsed = CreatorApiService(context).parseCreatorsFromApi(bodyStr)
                    if (parsed.isNotEmpty()) {
                        cont.resume(parsed to logSb.toString())
                        webView.destroy()
                    } else {
                        tryNextFetch()
                    }
                } catch (e: Exception) {
                    logSb.append("WV parse exception ").append(e.message).append('\n')
                    tryNextFetch()
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                tryNextFetch()
            }
        }
        webView.loadUrl("https://www.fanbox.cc/")

        cont.invokeOnCancellation {
            webView.destroy()
        }
    }
}
