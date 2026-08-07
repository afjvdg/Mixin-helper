package com.example.minecraftmixinhelper.data.remote

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

@Serializable
private data class VersionJson(
    val downloads: Downloads? = null
)

@Serializable
private data class Downloads(
    val client_mappings: MappingDownload? = null
)

@Serializable
private data class MappingDownload(val url: String)

class MappingDownloader(private val client: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    // 下载 Mojang client_mappings（26.x 起已不再附带，会明确报错）
    suspend fun downloadMojangMappings(versionJsonUrl: String): String {
        val versionJson: String = client.get(versionJsonUrl).body()
        val parsed = json.parseToJsonElement(versionJson).jsonObject
        val mappingsUrl = parsed["downloads"]?.jsonObject
            ?.get("client_mappings")?.jsonObject
            ?.get("url")?.jsonPrimitive?.content
        if (mappingsUrl == null) {
            throw Exception("该版本（26.x+）的 version.json 不再附带 client_mappings（Mojang 已停止发布），请改用 Fabric / Yarn 映射")
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

    // 下载 Parchment 参数映射：查版本 -> 下载 zip -> 解压 parchment.json
    suspend fun downloadParchmentJson(version: String): String {
        val metadata: String = client.get("https://maven.parchmentmc.org/org/parchmentmc/data/parchment/maven-metadata.xml").body()
        val versions = PARCHMENT_VERSION_RE.findAll(metadata).map { it.groupValues[1] }.toList()
        val target = versions.firstOrNull { it == version }
            ?: versions.firstOrNull { it.startsWith("$version-") || it.startsWith(version) }
            ?: throw Exception("parchmentmc 未发布版本 $version 的数据")
        val zipUrl = "https://maven.parchmentmc.org/org/parchmentmc/data/parchment/$target/parchment-$target-tiny.zip"
        val bytes = client.get(zipUrl).body<ByteArray>()
        return extractEntry(bytes, "parchment.json")
            ?: throw Exception("parchment zip 中未找到 parchment.json")
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
    }
}
