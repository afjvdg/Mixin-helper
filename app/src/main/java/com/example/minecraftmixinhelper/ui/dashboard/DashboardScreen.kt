package com.example.minecraftmixinhelper.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

    val loaders = listOf("ALL", "Mojang", "Fabric", "Forge", "NeoForge", "Parchment")
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

        // Loader 过滤
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            loaders.forEach { loader ->
                FilterChip(
                    selected = selectedLoader == loader,
                    onClick = { selectedLoader = loader },
                    label = { Text(loader) }
                )
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered) { v ->
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
                        Button(onClick = { viewModel.downloadMappings(v) }) {
                            Text(if (v.isCached) "重新下载" else "下载")
                        }
                    }
                }
            }
        }

        // 状态卡片（成功/失败持久保留，不会被进度条冲掉）
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
    }
}
