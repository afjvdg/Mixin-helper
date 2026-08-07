package com.example.minecraftmixinhelper.ui.dashboard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.minecraftmixinhelper.domain.service.MappingTypeLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController, viewModel: DashboardViewModel = hiltViewModel()) {
    val versions by viewModel.versions.collectAsState()
    val status by viewModel.status.collectAsState()
    val activeIds by viewModel.activeIds.collectAsState()
    val progress by viewModel.progress.collectAsState()

    // 加载器选项：parchment 随 forge/neoforge 一起下载，不再单列；mojang 官方映射并入 forge
    val loaders = listOf("ALL", "Fabric", "Forge", "NeoForge")
    var selectedLoader by remember { mutableStateOf("ALL") }

    val filtered = versions.filter {
        selectedLoader == "ALL" || it.loader.equals(selectedLoader, ignoreCase = true)
    }

    // 全局下载锁：只要有任意下载在进行，所有下载按钮都禁用
    val downloadingAny = activeIds.isNotEmpty()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Minecraft Mixin Helper",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { viewModel.refreshVersions() }) { Text("刷新") }
        }

        // 状态提示放在列表上方，保证不被版本列表遮住，也不遮住列表
        when (val s = status) {
            is DashboardStatus.Idle ->
                Text("选择版本并点击下载以缓存映射", color = MaterialTheme.colorScheme.outline)
            is DashboardStatus.Loading -> {
                Column {
                    val percent = progress.percent
                    if (percent != null) {
                        LinearProgressIndicator(
                            progress = { percent },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(s.message, color = MaterialTheme.colorScheme.primary)
                    if (progress.total != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${formatBytes(progress.downloaded)} / ${formatBytes(progress.total)} · ${formatBytes(progress.speed)}/s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
            is DashboardStatus.Success -> {
                Text(s.message, color = MaterialTheme.colorScheme.primary)
            }
            is DashboardStatus.Error -> {
                Column {
                    Text("✗ ${s.message}", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = { viewModel.refreshVersions() }) { Text("重试") }
                }
            }
        }

        // Loader 过滤（横向滑动式，避免被挤压/垂直拉伸）
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            loaders.forEach { loader ->
                FilterChip(
                    selected = selectedLoader == loader,
                    onClick = { selectedLoader = loader },
                    label = { Text(loader) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered, key = { it.id }) { v ->
                val isThisDownloading = v.id in activeIds
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(v.version, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${v.loader} · ${MappingTypeLabel.of(v.mappingType)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            if (v.isCached) {
                                Text(
                                    "✓ 已缓存",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        if (!v.isCached) {
                            Button(
                                onClick = { viewModel.downloadMappings(v) },
                                enabled = !downloadingAny && !isThisDownloading
                            ) {
                                Text(if (isThisDownloading) "下载中..." else "下载")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 把字节数格式化为可读大小（KB / MB）。 */
private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1fKB", kb)
    return String.format("%.2fMB", kb / 1024.0)
}
