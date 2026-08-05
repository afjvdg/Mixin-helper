package com.example.minecraftmixinhelper.data.remote

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class VersionJson(val downloads: Downloads? = null)

@Serializable
data class Downloads(val client_mappings: MappingDownload? = null)

@Serializable
data class MappingDownload(val url: String)

class MappingDownloader(private val client: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    // Mojang client_mappings 下载
    suspend fun downloadMojangMappings(versionJsonUrl: String): String {
        val versionJson: String = client.get(versionJsonUrl).body()
        val parsed = json.parseToJsonElement(versionJson).jsonObject
        val mappingsUrl = parsed["downloads"]?.jsonObject?.get("client_mappings")?.jsonObject?.get("url")?.jsonPrimitive?.content
            ?: throw Exception("No client_mappings found")

        return client.get(mappingsUrl).bodyAsText()
    }

    // Fabric Yarn mappings（通过 meta API 获取最新 yarn 版本后构造 Maven 下载链接）
    suspend fun downloadYarnMappings(gameVersion: String): String {
        val response: String = client.get("https://meta.fabricmc.net/v2/versions/mappings/$gameVersion").body()
        // 简化处理：取第一个 yarn 版本
        val mappingsList = json.decodeFromString<List<YarnMappingInfo>>(response)
        val yarnVersion = mappingsList.firstOrNull()?.version ?: throw Exception("No Yarn mappings")

        // 构造 Yarn mappings 下载 URL（实际为 tiny 格式）
        val yarnUrl = "https://maven.fabricmc.net/net/fabricmc/yarn/$yarnVersion/yarn-$yarnVersion-tiny.gz"
        return client.get(yarnUrl).bodyAsText()
    }
}

@Serializable
data class YarnMappingInfo(val version: String)