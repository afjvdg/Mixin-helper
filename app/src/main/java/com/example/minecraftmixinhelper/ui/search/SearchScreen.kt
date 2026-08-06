package com.example.minecraftmixinhelper.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
            )
        )

        Spacer(Modifier.height(16.dp))

        // 搜索结果
        LazyColumn {
            items(results) { mapping ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(mapping.deobfuscatedName, style = MaterialTheme.typography.titleMedium)
                        Text("混淆名: ${mapping.obfuscatedName}", style = MaterialTheme.typography.bodySmall)
                        Text(mapping.className, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        Text("类型: ${mapping.type}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}