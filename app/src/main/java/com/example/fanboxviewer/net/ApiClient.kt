package com.example.fanboxviewer.net

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class ApiClient(private val context: Context) {
    private val log = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val cookieInjector = Interceptor { chain ->
        val req = chain.request()
        val cookie = CookieUtil.buildCookieHeaderFor(
            "https://www.fanbox.cc/",
            "https://fanbox.cc/",
            "https://api.fanbox.cc/"
        )
        val ua = CookieUtil.defaultUserAgent(context)
        val newReq: Request = req.newBuilder().apply {
            header("User-Agent", ua)
            header("Accept", "application/json, text/plain, */*")
            header("Accept-Language", "ja-JP,ja;q=0.9,en-US;q=0.8,en;q=0.7")
            cookie?.let { header("Cookie", it) }
            // FANBOX APIs often require CORS-like headers; safe to send Origin/Referer as site root
            header("Origin", "https://www.fanbox.cc")
            header("Referer", "https://www.fanbox.cc/")
            header("X-Requested-With", "XMLHttpRequest")
            // Add CSRF token header if we have one in cookies
            val cookies = CookieUtil.parseCookieMap(cookie)
            val csrf = cookies["csrf"]
                ?: cookies["csrfToken"]
                ?: cookies["x-csrf-token"]
                ?: cookies["csrf_token"]
                ?: cookies.entries.firstOrNull { it.key.contains("csrf", ignoreCase = true) }?.value
            if (!csrf.isNullOrBlank()) header("x-csrf-token", csrf)
        }.build()
        chain.proceed(newReq)
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(cookieInjector)
        .addInterceptor(log)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun execute(request: Request): Response = client.newCall(request).execute()
}
