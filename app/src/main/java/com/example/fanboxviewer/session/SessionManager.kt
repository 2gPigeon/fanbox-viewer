package com.example.fanboxviewer.session

import android.webkit.CookieManager

class SessionManager {
    private val cookieManager = CookieManager.getInstance()

    fun isLoggedIn(): Boolean {
        val cookie = cookieManager.getCookie("https://fanbox.cc")
        return !cookie.isNullOrBlank()
    }

    fun logout() {
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
    }
}

