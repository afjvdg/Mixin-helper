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
        val methodJavadoc: Map<String, String> = emptyMap(), // func_xxx -> 方法描述
        val fieldJavadoc: Map<String, String> = emptyMap(),  // field_xxx -> 字段描述
        val params: Map<String, Map<Int, String>> = emptyMap() // funcId -> (槽位 -> 参数名)
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
                                deobfuscatedName = csv.fields[srgField] ?: srgField,
                                javadoc = csv.fieldJavadoc[srgField]
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
                                paramNames = buildParamNames(srgMethod, parsed?.parameters, csv.params),
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

    /**
     * 解析 MCP 参数名 CSV（params.csv）。
     * 列：`param,name,side`，param 形如 `p_110121_0_`（`p_<数字id>_<槽位>_`）。
     * 槽位规则与 JVM 参数一致：非静态方法首参数槽位为 1（0 为隐式 this），
     * long/double 各占 2 槽。返回 `funcId数字 -> (槽位 -> 参数名)`。
     */
    fun parseParamCsv(csv: String): Map<String, Map<Int, String>> {
        val result = mutableMapOf<String, MutableMap<Int, String>>()
        csv.lineSequence()
            .drop(1) // 跳过表头
            .filter { it.isNotBlank() }
            .forEach { line ->
                val cols = splitCsvLine(line)
                if (cols.size < 2) return@forEach
                val m = MCP_PARAM_RE.find(cols[0].trim()) ?: return@forEach
                val funcId = m.groupValues[1]
                val slot = m.groupValues[2].toIntOrNull() ?: return@forEach
                val name = cols[1].trim()
                if (name.isEmpty()) return@forEach
                result.getOrPut(funcId) { mutableMapOf() }[slot] = name
            }
        return result
    }

    /**
     * 为方法生成参数名列表：从 srgMethod（`func_<id>_<letter>`）提取数字 id，
     * 结合描述符参数类型，把 `p_<id>_<slot>_` 中的参数名按槽位落到参数位置。
     * 槽位计算：非静态方法首参数从 1 起（0 为 this），long/double 占 2 槽。
     */
    private fun buildParamNames(
        srgMethod: String,
        params: List<String>?,
        paramMap: Map<String, Map<Int, String>>
    ): List<String>? {
        if (params == null || params.isEmpty()) return null
        val m = SRG_METHOD_ID_RE.find(srgMethod) ?: return null
        val id = m.groupValues[1]
        val slots = paramMap[id] ?: return null
        // 判断是否静态：若有槽位 0 则视为静态（从 0 起），否则非静态（从 1 起）
        val static = slots.containsKey(0)
        var slot = if (static) 0 else 1
        val names = mutableListOf<String>()
        for (p in params) {
            names.add(slots[slot] ?: "")
            slot += if (p == "long" || p == "double") 2 else 1
        }
        return names
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

    // p_110121_0_  → id=110121, slot=0
    private val MCP_PARAM_RE = Regex("""p_(\d+)_(\d+)_""")
    // func_110121_a → id=110121
    private val SRG_METHOD_ID_RE = Regex("""func_(\d+)_""")
}
