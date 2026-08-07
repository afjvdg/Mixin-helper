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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController, viewModel: DashboardViewModel = hiltViewModel()) {
    val versions by viewModel.versions.collectAsState()
    val status by viewModel.status.collectAsState()
    val downloadingIds by viewModel.downloadingIds.collectAsState()

    // 加载器选项：parchment 不再单独列（随 forge/neoforge 一起下载）；mojang 改名为 mojmap
    val loaders = listOf("ALL", "Mojmap", "Fabric", "Forge", "NeoForge")
    var selectedLoader by remember { mutableStateOf("ALL") }

    val filtered = versions.filter {
        selectedLoader == "ALL" || it.loader.equals(selectedLoader, ignoreCase = true)
    }

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
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text(s.message, color = MaterialTheme.colorScheme.primary)
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
                val isDownloading = v.id in downloadingIds
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(v.version, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${v.loader} · ${v.mappingType}",
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
                        if (v.isCached) {
                            // 已缓存：不显示下载按钮
                        } else {
                            Button(
                                onClick = { viewModel.downloadMappings(v) },
                                enabled = !isDownloading
                            ) {
                                Text(if (isDownloading) "下载中..." else "下载")
                            }
                        }
                    }
                }
            }
        }
    }
}
