package com.example.minecraftmixinhelper.domain.service

/**
 * 内存前缀索引：为「纯前缀匹配」的小型数据（映射名，总量 1~5MB）提供毫秒级自动补全。
 *
 * 采用「有序数组 + 二分查找」方案（无需外部分布式索引）：
 * - 预处理：启动 / 每次下载完成后把全部名称加载进内存，按字典序排序。
 * - 检索：对每个字段维护一个有序字符串数组；输入前缀时用二分定位第一个
 *   >= 前缀的下标，再顺序扫描前缀相同的连续段。
 * - 结果截断：前缀过短命中过多时只返回前 [limit] 条，由调用方提示「结果过多」。
 *
 * 相比原先的 FTS(前缀) + LIKE(子串) 混用，这里「全部」字段 = 各字段前缀命中的
 * 并集，天然比任一单独字段多，且各字段语义一致（都是前缀匹配）。
 */
class MappingIndex {

    /** 索引条目：保存小写名（用于排序与二分）与原始行下标。 */
    private class Entry(val key: String, val row: Int)

    private val entries = mutableListOf<MappingEntityRef>()
    private var deobf: Array<Entry> = emptyArray()
    private var obf: Array<Entry> = emptyArray()
    private var className: Array<Entry> = emptyArray()   // 完整点分类名
    private var classNameSimple: Array<Entry> = emptyArray() // 简单类名（`.` 后最后一段）

    /** 原始行（供回填完整实体）。 */
    data class MappingEntityRef(
        val id: Long,
        val className: String,
        val obfuscatedName: String,
        val deobfuscatedName: String,
        val type: String,
        val version: String,
        val loader: String
    )

    /** 重建索引。传入所有映射行的精简引用。 */
    fun rebuild(rows: List<MappingEntityRef>) {
        entries.clear()
        entries.addAll(rows)
        // 按「小写名」排序，与二分搜索的大小写一致（避免大小写混合导致二分失效）。
        val build = { selector: (MappingEntityRef) -> String ->
            entries.mapIndexed { i, r ->
                Entry(selector(r).lowercase(), i)
            }.sortedBy { it.key }
                .toTypedArray()
        }
        deobf = build { it.deobfuscatedName }
        obf = build { it.obfuscatedName }
        className = build { it.className }
        // 简单类名索引：取完整类名最后一个 `.` 后的段，使「Player」这类输入能命中。
        classNameSimple = entries.mapIndexed { i, r ->
            Entry(simpleName(r.className).lowercase(), i)
        }.sortedBy { it.key }.toTypedArray()
    }

    private fun simpleName(full: String): String =
        full.substringAfterLast('.', full)

    /**
     * 前缀搜索。
     * @param prefix 用户输入前缀（已 trim）
     * @param field deobf / obf / class / 空(全部)
     * @param type 空或 ALL = 全部类型，否则精确匹配（CLASS/METHOD/FIELD）
     * @param version 空 = 全部版本，否则精确匹配
     * @param loader 空 = 全部加载器，否则精确匹配
     * @param limit 返回上限
     * @param tooMany 输出参数：命中总数是否超过 limit（用于提示「结果过多」）
     */
    fun search(
        prefix: String,
        field: String,
        type: String,
        version: String,
        loader: String,
        limit: Int
    ): Pair<List<MappingEntityRef>, Boolean> {
        if (prefix.isEmpty()) return emptyList<MappingEntityRef>() to false
        val lower = prefix.lowercase()
        val result = LinkedHashSet<MappingEntityRef>()
        val arrays = when (field.lowercase()) {
            "deobf" -> listOf(deobf)
            "obf" -> listOf(obf)
            // 类名搜索同时匹配完整点分类名与简单类名（如「Player」），扩大命中。
            "class" -> listOf(className, classNameSimple)
            else -> listOf(deobf, obf, className, classNameSimple)
        }
        var tooMany = false
        outer@ for (arr in arrays) {
            var i = lowerBound(arr, lower)
            while (i < arr.size && arr[i].key.startsWith(lower)) {
                val row = entries[arr[i].row]
                if (matches(row, type, version, loader)) {
                    result.add(row)
                    if (result.size > limit) {
                        tooMany = true
                        break@outer
                    }
                }
                i++
            }
        }
        return result.take(limit) to tooMany
    }

    private fun matches(r: MappingEntityRef, type: String, version: String, loader: String): Boolean {
        if (type.isNotBlank() && !type.equals("ALL", true) && !r.type.equals(type, true)) return false
        if (version.isNotBlank() && r.version != version) return false
        if (loader.isNotBlank() && !r.loader.equals(loader, true)) return false
        return true
    }

    /** 二分：第一个元素 key >= target 的下标。 */
    private fun lowerBound(arr: Array<Entry>, target: String): Int {
        var lo = 0
        var hi = arr.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (arr[mid].key < target) lo = mid + 1 else hi = mid
        }
        return lo
    }
}
