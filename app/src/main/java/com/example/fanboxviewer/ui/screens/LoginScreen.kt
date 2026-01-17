package com.example.fanboxviewer.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private fun hasFanboxSession(): Boolean {
    val cm = CookieManager.getInstance()
    val cookies = buildList {
        add(cm.getCookie("https://www.fanbox.cc") ?: "")
        add(cm.getCookie("https://fanbox.cc") ?: "")
        add(cm.getCookie("https://www.fanbox.cc/") ?: "")
        add(cm.getCookie("https://fanbox.cc/") ?: "")
    }.joinToString(";")
    if (cookies.isBlank()) return false
    val lower = cookies.lowercase()
    // より厳密な判定: 認証セッション系のクッキー名を要求
    // 例: fanbox系のセッション名に 'fanbox' と 'sess' を含むもの
    val likelyAuth = lower.contains("fanbox") && lower.contains("sess")
    return likelyAuth
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val loginUrl = "https://www.fanbox.cc/login"
    val hasSession = remember { mutableStateOf(hasFanboxSession()) }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("FANBOXにログインしてください")
        AndroidView(
            modifier = Modifier.weight(1f),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            // 自動遷移はせず、セッションとURLの両方で判定を厳格化
                            val cookieOk = hasFanboxSession()
                            val notLoginPage = url?.contains("login", ignoreCase = true) != true
                            hasSession.value = cookieOk && notLoginPage
                        }
                    }
                    loadUrl(loginUrl)
                }
            }
        )
        Button(onClick = onLoggedIn, enabled = hasSession.value) {
            Text("続行（ログイン済みを検知）")
        }
    }
}
