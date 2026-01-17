package com.example.fanboxviewer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fanboxviewer.AppContainer
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { TopAppBar(title = { Text("設定") }) }) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = onLogout) { Text("ログアウト") }
            Divider()
            Button(onClick = {
                scope.launch { container.postRepository.clearNonUserState() }
            }) { Text("キャッシュ削除（ブックマーク・非表示は保持）") }
            Button(onClick = {
                scope.launch {
                    container.postRepository.clearAll()
                    container.creatorRepository.clearAll()
                }
            }) { Text("全リセット（クリエイター・投稿・状態を削除）") }
        }
    }
}
