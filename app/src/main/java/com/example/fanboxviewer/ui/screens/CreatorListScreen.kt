package com.example.fanboxviewer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.fanboxviewer.AppContainer
import com.example.fanboxviewer.data.local.CreatorEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorListScreen(
    container: AppContainer,
    onOpenCreator: (creatorId: String, name: String) -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHidden: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val creators by container.creatorRepository.observeSupporting().collectAsState(initial = emptyList())
    val syncing = remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("クリエイター一覧") },
                actions = {
                    IconButton(onClick = onOpenBookmarks) { Text("★") }
                    IconButton(onClick = onOpenHidden) { Text("非") }
                    IconButton(onClick = onOpenSettings) { Text("設") }
                }
            )
        }
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = {
                    scope.launch {
                        syncing.value = true
                        try {
                            val raw = withTimeout(20000) {
                                val api = com.example.fanboxviewer.web.CreatorApiService(ctx)
                                val (apiList, _) = withContext(Dispatchers.IO) { api.fetchSupportingCreatorsWithDebug() }
                                if (apiList.isNotEmpty()) apiList else {
                                    val inPage = com.example.fanboxviewer.web.InPageApi()
                                    val (wvList, _) = inPage.listSupportingCreators(ctx)
                                    if (wvList.isNotEmpty()) wvList else {
                                        val fetcher = com.example.fanboxviewer.web.CreatorWebFetcher()
                                        fetcher.fetchSupporting(context = ctx)
                                    }
                                }
                            }
                            val enriched = withContext(Dispatchers.IO) {
                                val resolver = com.example.fanboxviewer.web.CreatorApiService(ctx)
                                raw.map { c ->
                                    if (c.creatorId.all { it.isDigit() }) {
                                        val (handle, uid, _) = resolver.resolveCreatorIds(c.creatorId)
                                        if (!handle.isNullOrBlank()) c.copy(creatorId = handle, userId = uid ?: c.userId) else c
                                    } else c
                                }
                            }
                            val now = System.currentTimeMillis()
                            val mapped = enriched.map {
                                CreatorEntity(
                                    creatorId = it.creatorId,
                                    userId = it.userId,
                                    name = it.name,
                                    iconUrl = it.iconUrl,
                                    isSupporting = true,
                                    lastSyncedAt = now,
                                )
                            }
                            container.creatorRepository.upsertAll(mapped)
                        } catch (_: Exception) {
                        } finally {
                            syncing.value = false
                        }
                    }
                }) { Icon(Icons.Filled.Refresh, contentDescription = "同期") }
            }

            if (syncing.value) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator()
                }
            }

            if (creators.isEmpty()) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(24.dp))
                    Text("支援中クリエイターがありません。")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(creators, key = { it.creatorId }) { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenCreator(c.creatorId, c.name) }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = c.iconUrl,
                                contentDescription = null,
                                modifier = Modifier.height(40.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Text("最終同期: ${c.lastSyncedAt ?: 0}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

