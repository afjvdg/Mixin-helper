package com.example.minecraftmixinhelper.domain.service

/** 把内部 mappingType 代码映射为界面可读的中文标签。 */
object MappingTypeLabel {
    fun of(mappingType: String): String = when (mappingType.lowercase()) {
        "yarn" -> "Yarn"
        "parchment" -> "Mojmap + Parchment"
        "mcp" -> "MCP"
        "mojmap" -> "Mojmap"
        else -> mappingType.ifBlank { "-" }
    }
}
