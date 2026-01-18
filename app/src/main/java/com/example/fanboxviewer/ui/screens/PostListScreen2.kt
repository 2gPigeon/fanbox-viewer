package com.example.fanboxviewer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.fanboxviewer.AppContainer
import com.example.fanboxviewer.data.local.PostEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostListScreen2(
    container: AppContainer,
    creatorId: String,
    creatorName: String,
    onOpenPost: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val preferredIdState = remember { mutableStateOf(creatorId) }
    if (creatorId.all { it.isDigit() } && preferredIdState.value == creatorId) {
        val resolveScope = rememberCoroutineScope()
        resolveScope.launch {
            try {
                val resolver = com.example.fanboxviewer.web.CreatorApiService(ctx)
                val (handle, _, _) = withContext(Dispatchers.IO) { resolver.resolveCreatorIds(creatorId) }
                if (!handle.isNullOrBlank()) preferredIdState.value = handle
            } catch (_: Exception) {}
        }
    }
    val preferredId = preferredIdState.value
    val posts by container.postRepository.observeByCreator(preferredId).collectAsState(initial = emptyList())
    val query = remember { mutableStateOf("") }
    val selectedYear = remember { mutableStateOf<String?>(null) }
    val syncing = remember { mutableStateOf(false) }

    val years = run {
        if (posts.isEmpty()) emptyList() else {
            val minY = posts.minOf { yearOf(it.publishedAt) }
            val maxY = posts.maxOf { yearOf(it.publishedAt) }
            (maxY downTo minY).toList()
        }
    }
    val filtered = posts.filter {
        val matchesQuery = query.value.isBlank() || it.title.contains(query.value, ignoreCase = true) || (it.summary?.contains(query.value, true) == true)
        val matchesYear = selectedYear.value?.let { y -> yearOf(it.publishedAt).toString() == y } ?: true
        matchesQuery && matchesYear
    }

    Scaffold(topBar = {
        TopAppBar(title = {
            Row {
                Text(creatorName, fontWeight = FontWeight.Bold, modifier = Modifier.alignByBaseline())
                Text("の投稿", modifier = Modifier.alignByBaseline())
            }
        }, actions = {
            IconButton(onClick = {
                scope.launch {
                    syncing.value = true
                    try {
                        val items = withTimeout(60000) {
                            val api = com.example.fanboxviewer.web.PostApiService(ctx)
                            val (apiList, _) = withContext(Dispatchers.IO) { api.fetchPostsForCreatorWithDebug(preferredId, limit = 5000) }
                            if (apiList.isNotEmpty()) apiList else {
                                val (wvList, _) = com.example.fanboxviewer.web.PostInPageApi().listPosts(ctx, preferredId, limit = 5000)
                                wvList
                            }
                        }
                        val mapped = items.map {
                            PostEntity(
                                postId = it.postId,
                                creatorId = it.creatorId,
                                title = it.title,
                                summary = it.summary,
                                url = it.url,
                                thumbnailUrl = it.thumb,
                                publishedAt = it.publishedAt
                            )
                        }
                        container.postRepository.upsertAll(mapped)
                    } catch (_: Exception) {
                    } finally { syncing.value = false }
                }
            }) { Icon(Icons.Filled.Refresh, contentDescription = "同期") }
        })
    }) { inner ->
        Column(Modifier.padding(inner).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (syncing.value) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = query.value,
                    onValueChange = { query.value = it },
                    label = { Text("検索（タイトル・概要）") }
                )
            }
            if (years.isNotEmpty()) {
                val expanded = remember { mutableStateOf(false) }
                val currentLabel = selectedYear.value ?: "すべて"
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(expanded = expanded.value, onExpandedChange = { expanded.value = !expanded.value }) {
                        OutlinedTextField(
                            modifier = Modifier.menuAnchor().weight(1f),
                            readOnly = true,
                            value = "年フィルタ: $currentLabel",
                            onValueChange = {},
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded.value) },
                            colors = ExposedDropdownMenuDefaults.textFieldColors()
                        )
                        ExposedDropdownMenu(expanded = expanded.value, onDismissRequest = { expanded.value = false }) {
                            DropdownMenuItem(text = { Text("すべて") }, onClick = {
                                selectedYear.value = null
                                expanded.value = false
                            })
                            years.forEach { y ->
                                DropdownMenuItem(text = { Text(y.toString()) }, onClick = {
                                    selectedYear.value = y.toString()
                                    expanded.value = false
                                })
                            }
                        }
                    }
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.postId }) { p ->
                    PostRow(
                        p = p,
                        onOpen = {
                            scope.launch { container.postRepository.setLastOpened(p.postId, System.currentTimeMillis()) }
                            onOpenPost(p.url)
                        },
                        onToggleBookmark = {
                            scope.launch { container.postRepository.setBookmarked(p.postId, !p.isBookmarked) }
                        },
                        onToggleHidden = {
                            scope.launch { container.postRepository.setHidden(p.postId, !p.isHidden) }
                        }
                    )
                    Divider()
                }
            }
        }
    }
}

@Composable
private fun PostRow(
    p: PostEntity,
    onOpen: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleHidden: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable { onOpen() }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(p.title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(dateString(p.publishedAt), fontSize = 12.sp)
        if (!p.summary.isNullOrBlank()) Text(p.summary!!, fontSize = 13.sp)
        if (!p.thumbnailUrl.isNullOrBlank()) {
            AsyncImage(model = p.thumbnailUrl, contentDescription = null)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onToggleBookmark) {
                if (p.isBookmarked) {
                    Icon(Icons.Filled.Bookmark, contentDescription = "ブックマーク解除")
                } else {
                    Icon(Icons.Outlined.BookmarkBorder, contentDescription = "ブックマーク")
                }
            }
            IconButton(onClick = onToggleHidden) { Icon(Icons.Filled.Block, contentDescription = if (p.isHidden) "非表示解除" else "非表示") }
        }
    }
}

private fun dateString(epochMs: Long): String {
    val dt = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
    return dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}

private fun yearOf(epochMs: Long): Int {
    val dt = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
    return dt.year
}

