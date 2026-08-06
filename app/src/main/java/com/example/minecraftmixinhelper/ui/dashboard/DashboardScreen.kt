package com.example.minecraftmixinhelper.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController, viewModel: DashboardViewModel = hiltViewModel()) {
    val versions by viewModel.versions.collectAsState()
    val status by viewModel.status.collectAsState()

    var selectedVersion by remember { mutableStateOf("") }
    var selectedLoader by remember { mutableStateOf("Fabric") }
    val loaders = listOf("Fabric", "Forge", "NeoForge", "mojang")

    // 版本下拉菜单状态
    var versionExpanded by remember { mutableStateOf(false) }
    var loaderExpanded by remember { mutableStateOf(false) }

    // 根据当前 loader 过滤版本列表（mojang 显示全部，否则显示对应 loader 或 mojang 的版本）
    val filteredVersions = remember(versions, selectedLoader) {
        if (selectedLoader.equals("mojang", ignoreCase = true)) {
            versions.filter { it.loader == "mojang" }
        } else {
            // 显示该 loader 的版本 + mojang 的版本（用户可用 mojang 的 URL 下载映射）
            versions.filter { it.loader.equals(selectedLoader, ignoreCase = true) || it.loader == "mojang" }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Minecraft Mixin Helper", style = MaterialTheme.typography.headlineMedium)

        // Minecraft 版本下拉
        ExposedDropdownMenuBox(
            expanded = versionExpanded,
            onExpandedChange = { versionExpanded = !versionExpanded }
        ) {
            OutlinedTextField(
                value = selectedVersion,
                onValueChange = {},
                label = { Text("Minecraft 版本") },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = versionExpanded) },
                supportingText = { if (filteredVersions.isEmpty()) Text("暂无版本，请下拉刷新") }
            )
            ExposedDropdownMenu(
                expanded = versionExpanded,
                onDismissRequest = { versionExpanded = false }
            ) {
                if (filteredVersions.isEmpty()) {
                    DropdownMenuItem(text = { Text("暂无数据") }, onClick = {})
                } else {
                    filteredVersions.take(100).forEach { version ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(version.version)
                                    Text(
                                        "${version.loader} • ${version.mappingType}" + if (version.isCached) " • 已缓存" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            },
                            onClick = {
                                selectedVersion = version.version
                                versionExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Mod Loader 下拉
        ExposedDropdownMenuBox(
            expanded = loaderExpanded,
            onExpandedChange = { loaderExpanded = !loaderExpanded }
        ) {
            OutlinedTextField(
                value = selectedLoader,
                onValueChange = {},
                label = { Text("Mod Loader") },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = loaderExpanded) }
            )
            ExposedDropdownMenu(
                expanded = loaderExpanded,
                onDismissRequest = { loaderExpanded = false }
            ) {
                loaders.forEach { loader ->
                    DropdownMenuItem(
                        text = { Text(loader) },
                        onClick = {
                            selectedLoader = loader
                            loaderExpanded = false
                        }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (selectedVersion.isNotBlank()) {
                        viewModel.downloadMappingsForVersion(selectedVersion, selectedLoader)
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = selectedVersion.isNotBlank()
            ) {
                Text("下载映射")
            }
            OutlinedButton(
                onClick = { viewModel.refreshVersions() },
                modifier = Modifier.weight(1f)
            ) {
                Text("刷新版本列表")
            }
        }

        OutlinedButton(
            onClick = { navController.navigate("search") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("去搜索页查看映射")
        }

        OutlinedButton(
            onClick = { navController.navigate("mixin") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("打开 Mixin 配置器")
        }

        // 状态反馈（含进度提示和详细错误）
        when (status) {
            is DashboardStatus.Idle -> {
                Text("请选择版本和加载器后点击下载", color = MaterialTheme.colorScheme.outline)
            }
            is DashboardStatus.Loading -> {
                val message = (status as DashboardStatus.Loading).message
                Column {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(message, color = MaterialTheme.colorScheme.primary)
                }
            }
            is DashboardStatus.Success -> {
                Text("✓ 映射下载成功，已缓存到本地！", color = MaterialTheme.colorScheme.primary)
            }
            is DashboardStatus.Error -> {
                val errorMsg = (status as DashboardStatus.Error).message
                Text("✗ $errorMsg", color = MaterialTheme.colorScheme.error)
            }
        }

        if (versions.isNotEmpty()) {
            Text("已加载 ${versions.size} 个版本", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}
