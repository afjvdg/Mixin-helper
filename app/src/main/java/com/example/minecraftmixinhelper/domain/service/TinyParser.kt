package com.example.minecraftmixinhelper.domain.service

/**
 * 解析 Yarn mappings.tiny，同时支持 Tiny v1 与 Tiny v2 两种格式。
 *
 * ## Tiny v2 行格式（Fabric Wiki 官方规范，字段/方法行的 owner 从最近的类块继承）
 *
 * ```
 * tiny   2   0   official   intermediary   named
 * c      a   class_123   pkg/SomeClass
 *     f   [I      a   field_789   someField        # f <desc> <ns0名> <ns1名> ...
 *     m   (III)V  a   method_456  someMethod       # m <desc> <ns0名> <ns1名> ...
 * ```
 *
 * ## Tiny v1 行格式（Fabric Wiki 官方规范，扁平结构，每条独立）
 *
 * ```
 * v1   official   intermediary   named
 * CLASS   <ns0名>   <ns1名>   ...              # CLASS <name-ns0> <name-ns1> ...
 * FIELD   <owner-ns0>   <desc>   <name-ns0>   <name-ns1>   ...
 * METHOD  <owner-ns0>   <desc>   <name-ns0>   <name-ns1>   ...
 * ```
 *
 * - v1 中成员行的 owner 是该类在「第 0 个命名空间」下的类名，描述符引用 ns0 类名。
 * - 两版都统一输出：`obfuscatedName` = 源命名空间（intermediary 等），
 *   `deobfuscatedName` / `className` = 可读命名空间（named / mojang / official）。
 * - 描述符按规范引用「源」命名空间类名，通过完整类映射重映射为可读命名空间。
 */
object TinyParser {

    private val V2_HEADER_RE = Regex("""^tiny\s+(\d+)\s+(\d+)\s+(.+)$""")
    private val DESC_CLASS_RE = Regex("""L([\w/$]+);""")

    fun parse(raw: String): List<MojmapParser.ParsedMapping> {
        val lines = raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
        if (lines.isEmpty()) return emptyList()
        return if (lines.first().startsWith("v1")) {
            parseV1(lines)
        } else {
            parseV2(lines)
        }
    }

    // ---------------- Tiny v1 ----------------

    private fun parseV1(lines: List<String>): List<MojmapParser.ParsedMapping> {
        val namespaces = lines.first()
            .removePrefix("v1").trim()
            .split(Regex("""\s+""")).filter { it.isNotEmpty() }
        require(namespaces.isNotEmpty()) { "不是合法的 Tiny 映射文件: ${lines.first()}" }

        // 源 = 混淆侧命名空间（intermediary / official 等），目标 = 可读命名空间（named / mojang）。
        // 对 Yarn（official/intermediary/named）应取 named 作为可读目标，而非 official。
        val sourceIdx = pickIndex(namespaces, "intermediary", "hashed", "yarn", "official") ?: 0
        val targetIdx = pickIndex(namespaces, "named", "mojang", "official")
            ?: (namespaces.lastIndex)

        // v1 为扁平结构：每行独立，owner 显式给出（ns0 类名），无继承关系。
        val classMap = mutableMapOf<String, String>() // source(ns0) -> target(可读)
        for (line in lines.drop(1)) {
            val parts = line.split(Regex("""\s+"""))
            if (parts.isNotEmpty() && parts[0] == "CLASS" && parts.size > maxOf(sourceIdx, targetIdx)) {
                classMap[parts[1 + 0]] = parts[1 + targetIdx] // ns0 -> 可读
            }
        }

        val result = mutableListOf<MojmapParser.ParsedMapping>()
        for (line in lines.drop(1)) {
            val parts = line.split(Regex("""\s+"""))
            if (parts.isEmpty()) continue
            when (parts[0]) {
                "CLASS" -> {
                    if (parts.size > maxOf(sourceIdx, targetIdx)) {
                        val source = parts[1 + sourceIdx]
                        val target = parts[1 + targetIdx]
                        result.add(
                            MojmapParser.ParsedMapping(
                                type = "CLASS",
                                className = target.replace('/', '.'),
                                obfuscatedName = source.replace('/', '.'),
                                deobfuscatedName = target.replace('/', '.')
                            )
                        )
                    }
                }
                "FIELD" -> {
                    if (parts.size >= 2 + maxOf(sourceIdx, targetIdx) + 1) {
                        val ownerNs0 = parts[1]        // 类（ns0）
                        val desc = parts[2]
                        val obf = parts[3 + sourceIdx]
                        val named = parts[3 + targetIdx]
                        result.add(
                            MojmapParser.ParsedMapping(
                                type = "FIELD",
                                className = remapClass(ownerNs0, classMap),
                                obfuscatedName = obf,
                                deobfuscatedName = named,
                                descriptor = remapDescriptor(desc, classMap)
                            )
                        )
                    }
                }
                "METHOD" -> {
                    if (parts.size >= 2 + maxOf(sourceIdx, targetIdx) + 1) {
                        val ownerNs0 = parts[1]
                        val desc = parts[2]
                        val obf = parts[3 + sourceIdx]
                        val named = parts[3 + targetIdx]
                        val parsed = try {
                            AsmDescriptorParser.parse(desc)
                        } catch (e: Exception) {
                            null
                        }
                        result.add(
                            MojmapParser.ParsedMapping(
                                type = "METHOD",
                                className = remapClass(ownerNs0, classMap),
                                obfuscatedName = obf,
                                deobfuscatedName = named,
                                descriptor = remapDescriptor(desc, classMap),
                                params = parsed?.parameters ?: emptyList(),
                                returnType = parsed?.returnType
                            )
                        )
                    }
                }
            }
        }
        return result
    }

    // ---------------- Tiny v2 ----------------

    private fun parseV2(lines: List<String>): List<MojmapParser.ParsedMapping> {
        val headerMatch = V2_HEADER_RE.find(lines.first())
        require(headerMatch != null) { "不是合法的 Tiny 映射文件: ${lines.first()}" }

        val namespaces = headerMatch.groupValues[3].split(Regex("""\s+""")).filter { it.isNotEmpty() }
        val sourceIdx = pickIndex(namespaces, "intermediary", "hashed", "yarn", "official")
            ?: 0
        val targetIdx = pickIndex(namespaces, "named", "mojang", "official")
            ?: (namespaces.lastIndex)

        // 真实 Yarn 类按字典序排列，成员描述符可能引用「后置定义」的类，
        // 因此必须两遍解析：第一遍先收集完整 classMap，第二遍再输出成员，
        // 否则被后置类引用的描述符无法重映射（单遍解析会失效）。
        val classMap = mutableMapOf<String, String>() // source class (slashes) -> target class (slashes)
        for (line in lines.drop(1)) {
            val token = line.firstOrNull() ?: continue
            if (token != 'c') continue
            val parts = line.substring(1).trim().split(Regex("""\s+"""))
            if (parts.isNotEmpty() && parts.size > maxOf(sourceIdx, targetIdx)) {
                classMap[parts[sourceIdx]] = parts[targetIdx]
            }
        }

        val result = mutableListOf<MojmapParser.ParsedMapping>()
        var currentClass: String? = null // 当前类（源命名空间，斜杠形式）

        for (line in lines.drop(1)) {
            val token = line.firstOrNull() ?: continue
            val parts = line.substring(1).trim().split(Regex("""\s+"""))
            if (parts.isEmpty()) continue

            when (token) {
                'c' -> {
                    // c <ns0名> <ns1名> ...（所有者从本行起继承）
                    if (parts.size > maxOf(sourceIdx, targetIdx)) {
                        val source = parts[sourceIdx]
                        val target = parts[targetIdx]
                        currentClass = source
                        result.add(
                            MojmapParser.ParsedMapping(
                                type = "CLASS",
                                className = target.replace('/', '.'),
                                obfuscatedName = source.replace('/', '.'),
                                deobfuscatedName = target.replace('/', '.')
                            )
                        )
                    }
                }
                'f' -> {
                    // f <desc> <ns0名> <ns1名> ...（owner = 最近的类）
                    if (currentClass != null && parts.size >= 1 + maxOf(sourceIdx, targetIdx) + 1) {
                        val desc = parts[0]
                        val obf = parts[1 + sourceIdx]
                        val named = parts[1 + targetIdx]
                        result.add(
                            MojmapParser.ParsedMapping(
                                type = "FIELD",
                                className = remapClass(currentClass, classMap),
                                obfuscatedName = obf,
                                deobfuscatedName = named,
                                descriptor = remapDescriptor(desc, classMap)
                            )
                        )
                    }
                }
                'm' -> {
                    // m <desc> <ns0名> <ns1名> ...（owner = 最近的类）
                    if (currentClass != null && parts.size >= 1 + maxOf(sourceIdx, targetIdx) + 1) {
                        val desc = parts[0]
                        val obf = parts[1 + sourceIdx]
                        val named = parts[1 + targetIdx]
                        val parsed = try {
                            AsmDescriptorParser.parse(desc)
                        } catch (e: Exception) {
                            null
                        }
                        result.add(
                            MojmapParser.ParsedMapping(
                                type = "METHOD",
                                className = remapClass(currentClass, classMap),
                                obfuscatedName = obf,
                                deobfuscatedName = named,
                                descriptor = remapDescriptor(desc, classMap),
                                params = parsed?.parameters ?: emptyList(),
                                returnType = parsed?.returnType
                            )
                        )
                    }
                }
                // 'p'（参数行）与 'v'（局部变量行）暂不消费
            }
        }
        return result
    }

    private fun pickIndex(namespaces: List<String>, vararg candidates: String): Int? {
        for (c in candidates) {
            val idx = namespaces.indexOfFirst { it.equals(c, ignoreCase = true) }
            if (idx >= 0) return idx
        }
        return null
    }

    private fun remapClass(name: String, classMap: Map<String, String>): String {
        val mapped = classMap[name] ?: name
        return mapped.replace('/', '.')
    }

    private fun remapDescriptor(desc: String, classMap: Map<String, String>): String {
        return DESC_CLASS_RE.replace(desc) { m ->
            val cls = m.groupValues[1]
            val mapped = classMap[cls] ?: cls
            "L$mapped;"
        }
    }
}
