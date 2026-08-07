package com.example.minecraftmixinhelper.domain.service

/**
 * 解析 MCP 映射：joined.srg + methods.csv / fields.csv。
 *
 * ## 数据来源（稳定、全面）
 * 原 MCPBot（mcpbot.bspk.rs）已下线。MCP 映射现稳定托管在 **Forge Maven**：
 * - `de/oceanlabs/mcp/mcp/<mc>/mcp-<mc>-srg.zip` 内含 `joined.srg`
 *   （混淆名 -> SRG 名，类名直接是可读类名）
 * - `de/oceanlabs/mcp/mcp_stable/<build>-<family>/mcp_stable-<build>-<family>.zip`
 *   内含 `methods.csv` / `fields.csv`（SRG 成员名 -> MCP 名），覆盖 1.7.10 ~ 1.15。
 *
 * ## 三级命名链
 * 混淆名(Notch，如 `k`) -> SRG/Searge（`func_1234_k` / `field_1234_a`）-> MCP 可读名（`getHealth`）。
 *
 * ## CSV 列
 * `searge,name,side,desc` —— `name`（index 1）是 MCP 可读名，`desc`（index 3）是描述/Javadoc。
 */
object McpParser {

    data class McpCsv(
        val methods: Map<String, String>,  // func_xxx -> MCP 方法名
        val fields: Map<String, String>,   // field_xxx -> MCP 字段名
        val methodJavadoc: Map<String, String> = emptyMap() // func_xxx -> 描述
    )

    fun parse(srg: String, csv: McpCsv): List<MojmapParser.ParsedMapping> {
        val result = mutableListOf<MojmapParser.ParsedMapping>()
        for (rawLine in srg.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val parts = line.split(Regex("""\s+"""))
            when (parts.getOrNull(0)) {
                "CL:" -> {
                    if (parts.size >= 3) {
                        val obfClass = parts[1].replace('/', '.')
                        val deobfClass = parts[2].replace('/', '.')
                        result.add(
                            MojmapParser.ParsedMapping(
                                type = "CLASS",
                                className = deobfClass,
                                obfuscatedName = obfClass,
                                deobfuscatedName = deobfClass
                            )
                        )
                    }
                }
                "FD:" -> {
                    if (parts.size >= 3) {
                        val srgField = nameOf(parts[2])
                        val deobfOwner = ownerOf(parts[2]).replace('/', '.')
                        result.add(
                            MojmapParser.ParsedMapping(
                                type = "FIELD",
                                className = deobfOwner,
                                obfuscatedName = srgField,
                                deobfuscatedName = csv.fields[srgField] ?: srgField
                            )
                        )
                    }
                }
                "MD:" -> {
                    if (parts.size >= 5) {
                        val srgMethod = nameOf(parts[3])
                        val deobfClass = ownerOf(parts[3]).replace('/', '.')
                        val deobfDesc = parts[4]
                        val mcpName = csv.methods[srgMethod] ?: srgMethod
                        val parsed = try {
                            AsmDescriptorParser.parse(deobfDesc)
                        } catch (e: Exception) {
                            null
                        }
                        result.add(
                            MojmapParser.ParsedMapping(
                                type = "METHOD",
                                className = deobfClass,
                                obfuscatedName = srgMethod,
                                deobfuscatedName = mcpName,
                                descriptor = deobfDesc,
                                params = parsed?.parameters ?: emptyList(),
                                returnType = parsed?.returnType,
                                javadoc = csv.methodJavadoc[srgMethod]
                            )
                        )
                    }
                }
            }
        }
        return result
    }

    /**
     * 解析 MCP 名称 CSV（methods.csv / fields.csv）。
     * 列：`searge,name,side,desc`，取 index 0（SRG 名）与 index 1（MCP 名）；
     * 若需要，index 3 可作为描述/Javadoc。
     */
    fun parseNameCsv(csv: String, withJavadoc: Boolean = false): Pair<Map<String, String>, Map<String, String>> {
        val names = mutableMapOf<String, String>()
        val javadocs = mutableMapOf<String, String>()
        csv.lineSequence()
            .drop(1) // 跳过表头
            .filter { it.isNotBlank() }
            .forEach { line ->
                val cols = splitCsvLine(line)
                if (cols.size >= 2) {
                    val searge = cols[0].trim()
                    val name = cols[1].trim()
                    if (searge.isNotEmpty() && name.isNotEmpty()) {
                        names[searge] = name
                        if (withJavadoc && cols.size >= 4) {
                            val desc = cols[3].trim()
                            if (desc.isNotEmpty()) javadocs[searge] = desc
                        }
                    }
                }
            }
        return names to javadocs
    }

    /** 按逗号切分一行 CSV，尊重双引号包裹（描述里可能含逗号）。 */
    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result.add(sb.toString())
                    sb.setLength(0)
                }
                else -> sb.append(ch)
            }
        }
        result.add(sb.toString())
        return result
    }

    private fun ownerOf(full: String): String = full.substringBeforeLast('/', full)
    private fun nameOf(full: String): String = full.substringAfterLast('/', full)
}
