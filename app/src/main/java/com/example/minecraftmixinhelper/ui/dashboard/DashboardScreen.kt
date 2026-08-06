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
    val loaders = listOf("Fabric", "Forge", "NeoForge")

    // 版本下拉菜单状态
    var versionExpanded by remember { mutableStateOf(false) }
    var loaderExpanded by remember { mutableStateOf(false) }

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
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = versionExpanded) }
            )
            ExposedDropdownMenu(
                expanded = versionExpanded,
                onDismissRequest = { versionExpanded = false }
            ) {
                versions.forEach { version ->
                    DropdownMenuItem(
                        text = { Text(version.version) },
                        onClick = {
                            selectedVersion = version.version
                            versionExpanded = false
                        }
                    )
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

        Button(
            onClick = {
                if (selectedVersion.isNotBlank()) {
                    viewModel.downloadMappingsForVersion(selectedVersion, selectedLoader)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedVersion.isNotBlank()
        ) {
            Text("获取版本并下载映射")
        }

        // 状态反馈（含进度提示和详细错误）
        when (status) {
            is DashboardStatus.Idle -> {
                Text("请选择版本和加载器后点击按钮", color = MaterialTheme.colorScheme.outline)
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
    }
}