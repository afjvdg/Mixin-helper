package com.example.minecraftmixinhelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.minecraftmixinhelper.ui.dashboard.DashboardScreen
import com.example.minecraftmixinhelper.ui.mixin.MixinConfiguratorScreen
import com.example.minecraftmixinhelper.ui.search.SearchScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MinecraftMixinHelperApp()
            }
        }
    }
}

@Composable
fun MinecraftMixinHelperApp() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = { navController.navigate("dashboard") },
                    label = { Text("版本") },
                    icon = { /* Icon */ }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("search") },
                    label = { Text("搜索") },
                    icon = { /* Icon */ }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate("mixin") },
                    label = { Text("Mixin") },
                    icon = { /* Icon */ }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(padding)
        ) {
            composable("dashboard") { DashboardScreen(navController) }
            composable("search") { SearchScreen(navController) }
            composable("mixin") { MixinConfiguratorScreen(navController) }
        }
    }
}