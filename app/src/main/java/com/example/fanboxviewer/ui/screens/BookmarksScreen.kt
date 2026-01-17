package com.example.fanboxviewer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fanboxviewer.AppContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    container: AppContainer,
    onOpenPost: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val posts by container.postRepository.observeBookmarked().collectAsState(initial = emptyList())

    Scaffold(topBar = { TopAppBar(title = { Text("ブックマーク") }) }) { inner ->
        LazyColumn(Modifier.padding(inner).fillMaxSize()) {
            items(posts, key = { it.postId }) { p ->
                Column(
                    Modifier.fillMaxWidth().clickable {
                        scope.launch { container.postRepository.setLastOpened(p.postId, System.currentTimeMillis()) }
                        onOpenPost(p.url)
                    }.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(p.title)
                    Text(p.url)
                }
                Divider()
            }
        }
    }
}

