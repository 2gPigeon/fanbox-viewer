package com.example.fanboxviewer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.fanboxviewer.AppContainer
import com.example.fanboxviewer.data.local.CreatorEntity
import com.example.fanboxviewer.data.prefs.TutorialPrefs
import com.example.fanboxviewer.ui.components.SpotlightTutorialOverlay
import com.example.fanboxviewer.ui.components.TutorialStep
import com.example.fanboxviewer.ui.components.tutorialTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    val creators: List<CreatorEntity>? by container.creatorRepository
        .observeSupporting()
        .collectAsState(initial = null)
    val syncing = remember { mutableStateOf(false) }
    val tutorialTargets = remember { mutableStateMapOf<String, Rect>() }
    val tutorialShown by TutorialPrefs.shownFlowNullable(ctx, TutorialPrefs.CreatorsShown).collectAsState(initial = null)
    val tutorialSteps = remember {
        listOf(
            TutorialStep("creators.bookmark", "ブックマーク", "ブックマーク済みのクリエイターを確認できます。"),
            TutorialStep("creators.hidden", "非表示", "非表示にしたクリエイターの一覧を表示します。"),
            TutorialStep("creators.settings", "設定", "アプリの各種設定を開きます。"),
            TutorialStep("creators.refresh", "更新", "サポート中のクリエイターを最新状態に更新します。"),
        )
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "クリエイター",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "一覧",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { scope.launch { TutorialPrefs.setShown(ctx, TutorialPrefs.CreatorsShown, false) } }
                        ) { Icon(Icons.Outlined.HelpOutline, contentDescription = "チュートリアル") }
                        IconButton(
                            modifier = Modifier.tutorialTarget("creators.bookmark", tutorialTargets),
                            onClick = onOpenBookmarks
                        ) { Icon(Icons.Filled.Bookmark, contentDescription = "ブックマーク") }
                        IconButton(
                            modifier = Modifier.tutorialTarget("creators.hidden", tutorialTargets),
                            onClick = onOpenHidden
                        ) { Icon(Icons.Filled.Block, contentDescription = "非表示") }
                        IconButton(
                            modifier = Modifier.tutorialTarget("creators.settings", tutorialTargets),
                            onClick = onOpenSettings
                        ) { Icon(Icons.Filled.Build, contentDescription = "設定") }
                    }
                )
            }
        ) { inner ->
            Box(
                modifier = Modifier
                    .padding(inner)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "サポート中のクリエイター",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
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
                        }, modifier = Modifier.tutorialTarget("creators.refresh", tutorialTargets)) {
                            Icon(Icons.Filled.Refresh, contentDescription = "更新")
                        }
                    }

                    if (syncing.value) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator()
                            Text("同期中...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    when {
                        creators == null -> {
                            // 読み込み中は何も表示しない（ローディング表示は上部に統一）
                        }
                        creators!!.isEmpty() -> {
                            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(Modifier.size(24.dp))
                                Text("サポート中のクリエイターがありません", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(creators!!, key = { it.creatorId }) { c ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenCreator(c.creatorId, c.name) },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = c.iconUrl,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                            )
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    c.name,
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val ts = c.lastSyncedAt
                                                if (ts != null && ts > 0) {
                                                    Text(
                                                        "最終更新 ${dateStringShort(ts)}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    SpotlightTutorialOverlay(
        steps = tutorialSteps,
        targetRects = tutorialTargets,
        visible = (tutorialShown == false),
        onFinish = { scope.launch { TutorialPrefs.setShown(ctx, TutorialPrefs.CreatorsShown) } }
    )
}

private fun dateStringShort(epochMs: Long): String {
    val dt = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
    return dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}
