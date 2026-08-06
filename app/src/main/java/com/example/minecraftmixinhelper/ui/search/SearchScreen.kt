package com.example.minecraftmixinhelper.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@Composable
fun SearchScreen(navController: NavController, viewModel: SearchViewModel = hiltViewModel()) {
    var query by remember { mutableStateOf("") }
    var searchType by remember { mutableStateOf("CLASS") } // CLASS, METHOD, FIELD
    val results by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val hasSearched by viewModel.hasSearched.collectAsState()

    // 当搜索类型切换时，用当前 query 重新搜索
    LaunchedEffect(searchType) {
        if (query.isNotBlank()) {
            viewModel.search(query, searchType)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // 搜索类型切换
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = searchType == "CLASS",
                onClick = { searchType = "CLASS" },
                label = { Text("类") }
            )
            FilterChip(
                selected = searchType == "METHOD",
                onClick = { searchType = "METHOD" },
                label = { Text("方法") }
            )
            FilterChip(
                selected = searchType == "FIELD",
                onClick = { searchType = "FIELD" },
                label = { Text("字段") }
            )
        }

        Spacer(Modifier.height(8.dp))

        // 搜索输入框（禁止换行 + 单行）
        OutlinedTextField(
            value = query,
            onValueChange = { newValue ->
                // 禁止换行
                if (!newValue.contains("\n")) {
                    query = newValue
                    viewModel.search(newValue, searchType)
                }
            },
            label = { Text("输入关键词搜索（实时匹配）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search
            ),
            trailingIcon = {
                if (query.isNotEmpty()) {
                    TextButton(onClick = {
                        query = ""
                        viewModel.clear()
                    }) { Text("清除") }
                }
            }
        )

        Spacer(Modifier.height(8.dp))

        // 快捷入口：跳转 Mixin 配置器
        OutlinedButton(
            onClick = { navController.navigate("mixin") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("打开 Mixin 配置器")
        }

        Spacer(Modifier.height(16.dp))

        when {
            isSearching -> {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator()
                        Text("搜索中...", color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            !hasSearched && results.isEmpty() -> {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("输入关键词开始搜索", color = MaterialTheme.colorScheme.outline)
                }
            }
            hasSearched && results.isEmpty() -> {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("无结果，试试其他关键词或切换类型", color = MaterialTheme.colorScheme.outline)
                }
            }
            else -> {
                Text("共 ${results.size} 条结果", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
                LazyColumn {
                    items(results) { mapping ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(mapping.deobfuscatedName, style = MaterialTheme.typography.titleMedium)
                                Text("混淆名: ${mapping.obfuscatedName}", style = MaterialTheme.typography.bodySmall)
                                Text(mapping.className, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                Text("类型: ${mapping.type}", style = MaterialTheme.typography.labelSmall)
                                if (!mapping.descriptor.isNullOrBlank()) {
                                    Text("描述符: ${mapping.descriptor}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
