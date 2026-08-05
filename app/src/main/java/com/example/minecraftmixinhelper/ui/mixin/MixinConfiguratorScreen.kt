package com.example.minecraftmixinhelper.ui.mixin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.minecraftmixinhelper.domain.service.MixinTemplateRenderer

@Composable
fun MixinConfiguratorScreen(navController: NavController) {
    var targetClass by remember { mutableStateOf("net.minecraft.world.entity.player.Player") }
    var methodName by remember { mutableStateOf("tick") }
    var descriptor by remember { mutableStateOf("(Lnet/minecraft/world/entity/player/Player;I)V") }
    var injectionType by remember { mutableStateOf("@Inject") }
    var atTarget by remember { mutableStateOf("HEAD") }
    var cancellable by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current

    val generatedCode = remember(targetClass, methodName, descriptor, injectionType, atTarget, cancellable) {
        MixinTemplateRenderer.render(targetClass, methodName, descriptor, injectionType, atTarget, cancellable)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Mixin 配置器", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(value = targetClass, onValueChange = { targetClass = it }, label = { Text("目标类") })
        OutlinedTextField(value = methodName, onValueChange = { methodName = it }, label = { Text("方法名") })
        OutlinedTextField(value = descriptor, onValueChange = { descriptor = it }, label = { Text("描述符") })

        Row {
            OutlinedTextField(value = injectionType, onValueChange = { injectionType = it }, label = { Text("注入类型") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = atTarget, onValueChange = { atTarget = it }, label = { Text("@At") }, modifier = Modifier.weight(1f))
        }

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = cancellable, onCheckedChange = { cancellable = it })
            Text("cancellable")
        }

        Spacer(Modifier.height(16.dp))

        Text("生成的 Mixin 代码", style = MaterialTheme.typography.titleMedium)
        Card {
            Text(
                generatedCode,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(16.dp))

        Button(onClick = {
            clipboardManager.setText(AnnotatedString(generatedCode))
        }) {
            Text("复制到剪贴板")
        }
    }
}