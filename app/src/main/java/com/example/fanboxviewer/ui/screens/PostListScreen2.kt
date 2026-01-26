package com.example.fanboxviewer.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
            } catch (_: Exception) {
            }
        }
    }
    val preferredId = preferredIdState.value
    val posts by container.postRepository.observeByCreator(preferredId).collectAsState(initial = emptyList())
    val creatorTags by container.tagRepository.observeTagsForCreator(preferredId).collectAsState(initial = emptyList())
    val postTagEntries by container.tagRepository.observePostTagsForCreator(preferredId).collectAsState(initial = emptyList())
    val query = remember { mutableStateOf("") }
    val selectedYear = remember { mutableStateOf<String?>(null) }
    val selectedTag = remember { mutableStateOf<String?>(null) }
    val syncing = remember { mutableStateOf(false) }
    val editingPostId = remember { mutableStateOf<String?>(null) }

    val years = run {
        if (posts.isEmpty()) emptyList() else {
            val minY = posts.minOf { yearOf(it.publishedAt) }
            val maxY = posts.maxOf { yearOf(it.publishedAt) }
            (maxY downTo minY).toList()
        }
    }
    val postTagsById = remember(postTagEntries) { postTagEntries.groupBy { it.postId } }
    val filtered = posts.filter {
        val matchesQuery = query.value.isBlank() || it.title.contains(query.value, ignoreCase = true) || (it.summary?.contains(query.value, true) == true)
        val matchesYear = selectedYear.value?.let { y -> yearOf(it.publishedAt).toString() == y } ?: true
        val matchesTag = selectedTag.value?.let { tag ->
            postTagsById[it.postId]?.any { entry -> entry.tagName == tag } == true
        } ?: true
        matchesQuery && matchesYear && matchesTag
    }

    Scaffold(topBar = {
        TopAppBar(title = {
            Column {
                Text(creatorName, fontWeight = FontWeight.Bold)
                Text("投稿一覧", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        container.postRepository.upsertAllPreservingUserState(mapped)
                    } catch (_: Exception) {
                    } finally {
                        syncing.value = false
                    }
                }
            }) { Icon(Icons.Filled.Refresh, contentDescription = "更新") }
        })
    }) { inner ->
        Box(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (syncing.value) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("同期中...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = query.value,
                            onValueChange = { query.value = it },
                            label = { Text("検索（タイトル/要約）") }
                        )
                        val hasYearFilter = years.isNotEmpty()
                        val hasTagFilter = creatorTags.isNotEmpty()
                        if (hasYearFilter || hasTagFilter) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (hasYearFilter) {
                                    val expanded = remember { mutableStateOf(false) }
                                    val currentLabel = selectedYear.value ?: "すべて"
                                    val yearWeight = if (hasTagFilter) 0.45f else 1f
                                    Box(Modifier.weight(yearWeight)) {
                                        DropdownFilterField(
                                            label = "年",
                                            value = currentLabel,
                                            onClick = { expanded.value = true },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        DropdownMenu(
                                            expanded = expanded.value,
                                            onDismissRequest = { expanded.value = false }
                                        ) {
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
                                if (hasTagFilter) {
                                    val expanded = remember { mutableStateOf(false) }
                                    val currentLabel = selectedTag.value ?: "すべて"
                                    val tagWeight = if (hasYearFilter) 0.65f else 1f
                                    Box(Modifier.weight(tagWeight)) {
                                        DropdownFilterField(
                                            label = "タグ",
                                            value = currentLabel,
                                            onClick = { expanded.value = true },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        DropdownMenu(
                                            expanded = expanded.value,
                                            onDismissRequest = { expanded.value = false }
                                        ) {
                                            DropdownMenuItem(text = { Text("すべて") }, onClick = {
                                                selectedTag.value = null
                                                expanded.value = false
                                            })
                                            creatorTags.forEach { tag ->
                                                DropdownMenuItem(text = { Text(tag.name) }, onClick = {
                                                    selectedTag.value = tag.name
                                                    expanded.value = false
                                                })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.postId }) { p ->
                        val tagsForPost = postTagsById[p.postId]?.map { it.tagName } ?: emptyList()
                        PostRow(
                            p = p,
                            tags = tagsForPost,
                            onOpen = {
                                scope.launch { container.postRepository.setLastOpened(p.postId, System.currentTimeMillis()) }
                                onOpenPost(p.url)
                            },
                            onToggleBookmark = {
                                scope.launch { container.postRepository.setBookmarked(p.postId, !p.isBookmarked) }
                            },
                            onToggleHidden = {
                                scope.launch { container.postRepository.setHidden(p.postId, !p.isHidden) }
                            },
                            onEditTags = { editingPostId.value = p.postId }
                        )
                    }
                }
            }
        }
    }

    val editingId = editingPostId.value
    if (editingId != null) {
        val selectedForPost = postTagsById[editingId]?.map { it.tagName }?.toSet() ?: emptySet()
        TagEditDialog(
            tagOptions = creatorTags.map { it.name },
            initialSelected = selectedForPost,
            onDismiss = { editingPostId.value = null },
            onSave = { newTags ->
                scope.launch { container.tagRepository.replacePostTags(preferredId, editingId, newTags) }
                editingPostId.value = null
            }
        )
    }
}

@Composable
private fun PostRow(
    p: PostEntity,
    tags: List<String>,
    onOpen: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleHidden: () -> Unit,
    onEditTags: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpen() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(p.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(dateString(p.publishedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!p.summary.isNullOrBlank()) {
                Text(p.summary!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!p.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = p.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
            if (tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.forEach { tag ->
                        TagChip(name = tag)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = onToggleBookmark) {
                    if (p.isBookmarked) {
                        Icon(Icons.Filled.Bookmark, contentDescription = "ブックマーク済み")
                    } else {
                        Icon(Icons.Outlined.BookmarkBorder, contentDescription = "ブックマーク")
                    }
                }
                IconButton(onClick = onToggleHidden) { Icon(Icons.Filled.Block, contentDescription = if (p.isHidden) "非表示解除" else "非表示") }
                IconButton(onClick = onEditTags) { Icon(Icons.Filled.Label, contentDescription = "タグ編集") }
            }
        }
    }
}

@Composable
private fun TagChip(name: String) {
    val color = tagColorFor(name)
    val border = color.copy(alpha = 0.35f)
    val textColor = if (color.luminance() > 0.5f) Color(0xFF1F1F1F) else color
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, border)
    ) {
        Box(
            modifier = Modifier
                .height(28.dp)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = name, color = textColor, fontSize = 12.sp)
        }
    }
    Spacer(Modifier.width(2.dp))
}

@Composable
private fun DropdownFilterField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$label: $value", style = MaterialTheme.typography.bodyMedium)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
    }
}

@Composable
private fun TagEditDialog(
    tagOptions: List<String>,
    initialSelected: Set<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    val selected = remember(initialSelected) { mutableStateOf(initialSelected) }
    val newTag = remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("タグ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tagOptions.isEmpty()) {
                    Text("タグがありません")
                } else {
                    tagOptions.forEach { tag ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = selected.value.contains(tag),
                                onCheckedChange = { checked ->
                                    selected.value = if (checked) selected.value + tag else selected.value - tag
                                }
                            )
                            Text(tag, modifier = Modifier.weight(1f))
                        }
                    }
                }
                OutlinedTextField(
                    value = newTag.value,
                    onValueChange = { newTag.value = it },
                    label = { Text("新規タグ") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val added = newTag.value.trim()
                val merged = if (added.isNotBlank()) selected.value + added else selected.value
                onSave(merged.toList())
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

private fun dateString(epochMs: Long): String {
    val dt = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
    return dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}

private fun yearOf(epochMs: Long): Int {
    val dt = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
    return dt.year
}

private fun tagColorFor(name: String): Color {
    val hue = ((name.hashCode() % 360) + 360) % 360
    return Color.hsl(hue.toFloat(), 0.55f, 0.6f)
}
