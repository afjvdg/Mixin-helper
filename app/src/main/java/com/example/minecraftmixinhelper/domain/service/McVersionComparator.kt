package com.example.minecraftmixinhelper.domain.service

/**
 * Minecraft 版本号比较与映射类型决策（纯逻辑，便于单元测试）。
 *
 * 版本号按数字元组逐位比较：`1.20.1` -> [1, 20, 1]；`1.20.1-47.2.0` -> [1, 20, 1, 47, 2, 0]。
 * 遇到非纯数字片段（如 `rc1`、`snapshot`）即截断，因此 `1.21.4` 与 `1.21.4-rc1` 视为相等。
 */
object McVersionComparator {

    /** 版本号 -> 数字元组（首个非纯数字片段处截断）。 */
    fun tuple(v: String): List<Int> {
        val result = mutableListOf<Int>()
        for (part in v.split(Regex("""[.\-]"""))) {
            val n = part.toIntOrNull() ?: break
            result.add(n)
        }
        return result
    }

    /** a >= b */
    fun ge(a: String, b: String): Boolean = compare(a, b) >= 0

    /** 返回 -1 / 0 / 1 */
    fun compare(a: String, b: String): Int {
        val ta = tuple(a)
        val tb = tuple(b)
        val n = maxOf(ta.size, tb.size)
        for (i in 0 until n) {
            val x = ta.getOrElse(i) { 0 }
            val y = tb.getOrElse(i) { 0 }
            if (x != y) return if (x > y) 1 else -1
        }
        return 0
    }

    /**
     * 根据 MC 版本与加载器决定映射类型（仅兜底决策，UI 层面可覆盖）：
     * - fabric：始终使用 Yarn（对所有 MC 版本均有稳定发布）
     * - forge / neoforge：1.18+ 使用 Parchment（参数名 + Javadoc，叠加 Mojmap），
     *   更早版本仅 Mojmap（Mojang 官方映射）
     * - 其他（mojang / parchment）：Mojmap
     */
    fun decideMappingType(mcVersion: String, loader: String): String {
        return when (loader.lowercase()) {
            "fabric" -> "yarn"
            "forge", "neoforge" -> if (ge(mcVersion, "1.18")) "parchment" else "mojmap"
            else -> "mojmap"
        }
    }

    /**
     * 从模组加载器版本号中解析 Minecraft 版本：
     * - forge：`1.20.1-47.2.0` -> `1.20.1`（连字符前即 MC 版本）
     * - neoforge：`21.1.78` -> `1.21.1`、`20.4.237` -> `1.20.4`、`21.0.144` -> `1.21`；
     *   MC 26+ 起直接以 MC 版本为前缀：`26.2.0.49-beta` -> `26.2`
     */
    fun mcVersionOf(loader: String, modLoaderVersion: String): String? {
        val base = modLoaderVersion.substringBefore('-').trim()
        if (base.isEmpty()) return null
        val mcVersion = when (loader.lowercase()) {
            "forge" -> base
            "neoforge" -> {
                val parts = base.split('.').mapNotNull { it.toIntOrNull() }
                when {
                    parts.isEmpty() -> null
                    parts[0] >= 26 -> if (parts.size >= 2) "${parts[0]}.${parts[1]}" else null
                    parts.size >= 2 -> "1.${parts[0]}.${parts[1]}".removeSuffix(".0")
                    else -> null
                }
            }
            else -> base
        } ?: return null
        // 兜底校验：MC 版本必须以 `数字.数字` 开头
        return mcVersion.takeIf { it.containsMatchIn(Regex("""^\d+\.\d+""")) }
    }
}
