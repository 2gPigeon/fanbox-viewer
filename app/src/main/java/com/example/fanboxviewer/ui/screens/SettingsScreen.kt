package com.example.fanboxviewer.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fanboxviewer.AppContainer
import com.example.fanboxviewer.data.export.UserDataTransfer
import kotlinx.coroutines.launch
import java.time.LocalDate


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val status = remember { mutableStateOf<String?>(null) }
    val busy = remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy.value = true
            status.value = "エクスポート中..."
            try {
                val summary = UserDataTransfer.exportToUri(
                    context = context,
                    uri = uri,
                    postRepository = container.postRepository,
                    tagRepository = container.tagRepository,
                )
                status.value = "完了: タグ${summary.tagCount} / 投稿タグ${summary.postTagCount} / 状態${summary.postStateCount}"
            } catch (e: Exception) {
                status.value = "失敗: ${e.message ?: "unknown error"}"
            } finally {
                busy.value = false
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy.value = true
            status.value = "インポート中..."
            try {
                val summary = UserDataTransfer.importFromUri(
                    context = context,
                    uri = uri,
                    postRepository = container.postRepository,
                    tagRepository = container.tagRepository,
                )
                status.value = "完了: タグ${summary.tagCount} / 投稿タグ${summary.postTagCount} / ブクマ${summary.appliedBookmarks}/${summary.bookmarkCount} / 非表示${summary.appliedHidden}/${summary.hiddenCount}"
            } catch (e: Exception) {
                status.value = "失敗: ${e.message ?: "unknown error"}"
            } finally {
                busy.value = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "アプリ",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "設定",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "データ移行",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            "タグ/非表示/ブックマークを保存・復元します。事前に投稿を同期してください。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TransferActionRow(
                            title = "インポート",
                            subtitle = "端末から読み込み",
                            icon = Icons.Filled.FileDownload,
                            enabled = !busy.value,
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) }
                        )
                        TransferActionRow(
                            title = "エクスポート",
                            subtitle = "端末へ書き出し",
                            icon = Icons.Filled.FileUpload,
                            enabled = !busy.value,
                            onClick = {
                                val fileName = "fanboxviewer-userdata-${LocalDate.now()}.json"
                                exportLauncher.launch(fileName)
                            }
                        )
                        status.value?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "セッション",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        SettingActionRow(
                            title = "ログアウト",
                            subtitle = "この端末からログアウトします",
                            icon = Icons.Filled.Logout,
                            enabled = !busy.value,
                            tone = RowTone.Danger,
                            onClick = onLogout
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "ストレージ",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        SettingActionRow(
                            title = "キャッシュ削除",
                            subtitle = "ブックマーク・非表示は保持",
                            icon = Icons.Filled.DeleteSweep,
                            enabled = !busy.value,
                            onClick = { scope.launch { container.postRepository.clearNonUserState() } }
                        )
                        Divider()
                        SettingActionRow(
                            title = "全リセット",
                            subtitle = "クリエイター・投稿・状態を削除",
                            icon = Icons.Filled.DeleteForever,
                            enabled = !busy.value,
                            onClick = {
                                scope.launch {
                                    container.postRepository.clearAll()
                                    container.creatorRepository.clearAll()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferActionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SettingActionRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        enabled = enabled,
        onClick = onClick
    )
}

private enum class RowTone { Normal, Danger }

@Composable
private fun SettingActionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    tone: RowTone = RowTone.Normal,
) {
    val baseColor = if (tone == RowTone.Danger) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val iconTint = if (tone == RowTone.Danger) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val badgeColor = iconTint.copy(alpha = 0.12f)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = baseColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(badgeColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(">", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
