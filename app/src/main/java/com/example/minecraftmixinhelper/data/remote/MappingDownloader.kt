package com.example.minecraftmixinhelper.data.remote

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class MappingDownloader(private val client: HttpClient) {

    // 下载 Mojang client_mappings（26.x 起已不再附带，会明确报错）
    suspend fun downloadMojangMappings(versionJsonUrl: String): String {
        val versionJson: String = client.get(versionJsonUrl).body()
        val mappingsUrl = extractClientMappingsUrl(versionJson)
        if (mappingsUrl == null) {
            throw Exception("该版本的 version.json 未附带 client_mappings（Mojang 未发布官方映射），请改用 Fabric / Yarn 映射")
        }
        return client.get(mappingsUrl).bodyAsText()
    }

    // 下载 Fabric Yarn 映射：查最新稳定版 -> 下载 yarn jar -> 解压 mappings/mappings.tiny
    suspend fun downloadYarnMappings(gameVersion: String): String {
        val yarnVersions: List<YarnVersion> = client.get("https://meta.fabricmc.net/v2/versions/yarn/$gameVersion").body()
        val stable = yarnVersions.firstOrNull { it.stable }
            ?: yarnVersions.firstOrNull()
            ?: throw Exception("未找到 $gameVersion 的 Yarn 映射版本")
        val mavenUrl = "https://maven.fabricmc.net/net/fabricmc/yarn/${stable.version}/yarn-${stable.version}.jar"
        val bytes = client.get(mavenUrl).body<ByteArray>()
        return extractEntry(bytes, "mappings/mappings.tiny")
            ?: throw Exception("Yarn jar 中未找到 mappings/mappings.tiny")
    }

    /**
     * 下载 Parchment 参数映射（parchment.json）并解压。
     *
     * Parchment 官方自 2023 年起改用按 MC 版本分 artifact 的坐标（见
     * https://parchmentmc.org/docs/maven.html）：
     * `org.parchmentmc.data:parchment-<mc>:<YYYY.MM.DD>@zip`（zip 内含 `parchment.json`）
     * 旧坐标 `org.parchmentmc.data:parchment:<ver>:tiny@zip` 作为回退。
     *
     * 后端仓库已迁移：`maven.parchmentmc.org` 现重定向到 ldtteam 的 JFrog 仓库
     * `https://ldtteam.jfrog.io/artifactory/parchmentmc-public/`。故新坐标优先请求
     * JFrog 真实仓库，`maven.parchmentmc.org` 保留作兜底。
     *
     * zip 文件名已实网确认：`parchment-<mc>-<date>.zip`
     * （如 `parchment-1.21.1-2024.11.17.zip`、`parchment-1.21.8-2025.07.20.zip`）。
     *
     * [mcVersion] 可能是次版本（如 `1.21`，来自版本列表的分支名），此时先从
     * Parchment 数据仓库的 `build.gradle` 解析 compass 补丁版本（如 `1.21.11`）。
     */
    suspend fun downloadParchmentJson(mcVersion: String): String {
        val patch = resolveParchmentPatch(mcVersion)

        // 新坐标：优先 JFrog 真实仓库，`maven.parchmentmc.org` 兜底
        // （该域名会重定向到同一 JFrog 仓库，保留可兼容旧网络环境）。
        val newCoordinateHosts = listOf(
            "https://ldtteam.jfrog.io/artifactory/parchmentmc-public",
            "https://maven.parchmentmc.org"
        )
        for (repo in newCoordinateHosts) {
            try {
                val metadata = client.get(
                    "$repo/org/parchmentmc/data/parchment-$patch/maven-metadata.xml"
                ).bodyAsText()
                val export = latestReleaseVersion(metadata)
                if (export != null) {
                    // 真实仓库中的 zip 文件名：`parchment-<mc>-<date>.zip`
                    // （officialExport-<date>.zip 为历史遗留命名，真实仓库中不存在，已移除）
                    val file = "parchment-$patch-$export.zip"
                    val bytes = client.get("$repo/org/parchmentmc/data/parchment-$patch/$export/$file")
                        .body<ByteArray>()
                    val parsed = extractEntry(bytes, "parchment.json")
                    if (parsed != null) return parsed
                }
            } catch (e: Exception) {
                // 尝试下一个仓库
            }
        }

        // 旧坐标回退：org.parchmentmc.data:parchment:{ver}:tiny@zip
        try {
            val metadata = client.get(
                "https://maven.parchmentmc.org/org/parchmentmc/data/parchment/maven-metadata.xml"
            ).bodyAsText()
            val target = legacyVersionFor(metadata, patch)
            if (target != null) {
                val zipUrl = "https://maven.parchmentmc.org/org/parchmentmc/data/parchment/" +
                    "$target/parchment-$target-tiny.zip"
                val bytes = client.get(zipUrl).body<ByteArray>()
                val parsed = extractEntry(bytes, "parchment.json")
                if (parsed != null) return parsed
            }
        } catch (e: Exception) {
            // 两个坐标都失败，抛明确错误
        }
        throw Exception("parchmentmc 未发布 $mcVersion 的数据（新坐标 parchment-$patch 与旧坐标均未找到）")
    }

    /**
     * 次版本（`1.21`）-> 补丁版本（`1.21.11`）：读取 Parchment 数据仓库
     * `versions/X.Y.x` 分支的 build.gradle 中的 `compass { version = '...' }`。
     * 已是补丁版本则原样返回。
     */
    private suspend fun resolveParchmentPatch(mcVersion: String): String {
        if (mcVersion.split('.').size >= 3) return mcVersion
        val minor = mcVersion.substringBefore('-')
        if (!minor.matches(Regex("""^\d+\.\d+$"""))) return mcVersion
        val buildGradle = try {
            client.get(
                "https://raw.githubusercontent.com/ParchmentMC/Parchment/versions/$minor.x/build.gradle"
            ).bodyAsText()
        } catch (e: Exception) {
            return mcVersion
        }
        return COMPASS_VERSION_RE.find(buildGradle)?.groupValues?.get(1) ?: mcVersion
    }

    /** 取 maven-metadata 中最新非 SNAPSHOT 的发布版本（日期版本字典序即时间序）。 */
    private fun latestReleaseVersion(metadata: String): String? =
        PARCHMENT_VERSION_RE.findAll(metadata)
            .map { it.groupValues[1] }
            .filter { !it.contains("SNAPSHOT", ignoreCase = true) }
            .sortedDescending()
            .firstOrNull()

    /** 旧坐标：优先精确匹配 MC 版本，其次 `MC版本-` 前缀（如 1.20.1-2023.09.09）。 */
    private fun legacyVersionFor(metadata: String, mcVersion: String): String? {
        val versions = PARCHMENT_VERSION_RE.findAll(metadata).map { it.groupValues[1] }.toList()
        return versions.firstOrNull { it == mcVersion }
            ?: versions.filter { it.startsWith("$mcVersion-") }.sortedDescending().firstOrNull()
    }

    private fun extractClientMappingsUrl(versionJson: String): String? {
        return try {
            val parsed = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .parseToJsonElement(versionJson).jsonObject
            parsed["downloads"]?.jsonObject
                ?.get("client_mappings")?.jsonObject
                ?.get("url")?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }

    private fun extractEntry(bytes: ByteArray, entryName: String): String? {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == entryName) {
                    return zis.bufferedReader(Charsets.UTF_8).readText()
                }
                entry = zis.nextEntry
            }
        }
        return null
    }

    private companion object {
        val PARCHMENT_VERSION_RE = Regex("""<version>(.+?)</version>""")
        val COMPASS_VERSION_RE = Regex("""compass\s*\{\s*version\s*=\s*'([^']+)'""")
    }
}
