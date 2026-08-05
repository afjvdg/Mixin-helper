package com.example.minecraftmixinhelper.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun DashboardScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Minecraft Mixin Helper", style = MaterialTheme.typography.headlineMedium)

        var mcVersion by remember { mutableStateOf("1.20.1") }
        var loader by remember { mutableStateOf("Fabric") }
        var mappingType by remember { mutableStateOf("Mojmap") }

        OutlinedTextField(value = mcVersion, onValueChange = { mcVersion = it }, label = { Text("Minecraft 版本") })
        OutlinedTextField(value = loader, onValueChange = { loader = it }, label = { Text("Mod Loader") })
        OutlinedTextField(value = mappingType, onValueChange = { mappingType = it }, label = { Text("映射类型") })

        Button(onClick = { /* TODO: 触发版本抓取 */ }) {
            Text("获取版本 & 下载映射")
        }

        Text("离线缓存状态：已缓存 1.20.1 Fabric Mojmap", color = MaterialTheme.colorScheme.primary)
    }
}