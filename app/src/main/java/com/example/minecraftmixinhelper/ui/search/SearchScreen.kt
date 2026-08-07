package com.example.minecraftmixinhelper.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(navController: NavController, viewModel: SearchViewModel = hiltViewModel()) {
    val results by viewModel.searchResults.collectAsState()
    val downloadedVersions by viewModel.downloadedVersions.collectAsState()
    val loading by viewModel.loading.collectAsState()

    var query by remember { mutableStateOf("") }
    var searchType by remember { mutableStateOf("ALL") }
    var selectedVersion by remember { mutableStateOf("") }
    var showDetail by remember { mutableStateOf<MappingEntity?>(null) }

    val types = listOf("ALL", "CLASS", "METHOD", "FIELD")
    val versionOptions = listOf("") + downloadedVersions

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // 搜索类型切换
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            types.forEach { t ->
                FilterChip(
                    selected = searchType == t,
                    onClick = { searchType = t; viewModel.setType(t) },
                    label = { Text(t) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // 版本范围选择（全部 / 已下载版本）
        var versionExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = versionExpanded,
            onExpandedChange = { versionExpanded = !versionExpanded }
        ) {
            OutlinedTextField(
                value = if (selectedVersion.isEmpty()) "全部版本" else selectedVersion,
                onValueChange = {},
                readOnly = true,
                label = { Text("版本范围") },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = versionExpanded) }
            )
            ExposedDropdownMenu(
                expanded = versionExpanded,
                onDismissRequest = { versionExpanded = false }
            ) {
                versionOptions.forEach { v ->
                    DropdownMenuItem(
                        text = { Text(if (v.isEmpty()) "全部版本" else v) },
                        onClick = {
                            selectedVersion = v
                            versionExpanded = false
                            viewModel.setVersion(v)
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        // 搜索输入框（禁止换行 + 单行）
        OutlinedTextField(
            value = query,
            onValueChange = { newValue ->
                if (!newValue.contains("\n")) {
                    query = newValue
                    viewModel.setQuery(newValue)
                }
            },
            label = { Text("输入关键词搜索（实时匹配）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))

        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))

        when {
            query.isBlank() -> Text("输入关键词开始搜索", color = MaterialTheme.colorScheme.outline)
            results.isEmpty() && !loading -> Text("未找到匹配项", color = MaterialTheme.colorScheme.outline)
            else -> LazyColumn {
                items(results) { mapping ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clickable { showDetail = mapping }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(mapping.deobfuscatedName, style = MaterialTheme.typography.titleMedium)
                            Text("混淆名: ${mapping.obfuscatedName}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                mapping.className,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text("类型: ${mapping.type}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    showDetail?.let { m -> DetailDialog(mapping = m, onDismiss = { showDetail = null }) }
}

@Composable
private fun DetailDialog(mapping: MappingEntity, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        title = { Text(mapping.deobfuscatedName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("类型: ${mapping.type}")
                Text("版本: ${mapping.version} / ${mapping.loader}")
                Text("类: ${mapping.className}")
                Text("混淆名: ${mapping.obfuscatedName}")
                if (!mapping.descriptor.isNullOrBlank()) Text("描述符: ${mapping.descriptor}")
                if (!mapping.params.isNullOrBlank()) Text("参数: ${mapping.params}")
                if (!mapping.paramNames.isNullOrEmpty()) Text("参数名: ${mapping.paramNames.joinToString(", ")}")
                if (!mapping.returnType.isNullOrBlank()) Text("返回类型: ${mapping.returnType}")
                if (!mapping.javadoc.isNullOrBlank()) Text("Javadoc: ${mapping.javadoc}")
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(mapping.deobfuscatedName)) }) {
                        Text("复制名称")
                    }
                    TextButton(onClick = { clipboard.setText(AnnotatedString(mapping.descriptor ?: "")) }) {
                        Text("复制描述符")
                    }
                }
            }
        }
    )
}
