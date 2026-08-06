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

    // 下载 Mojang client_mappings
    suspend fun downloadMojangMappings(versionJsonUrl: String): String {
        val versionJson: String = client.get(versionJsonUrl).body()
        val parsed = json.parseToJsonElement(versionJson).jsonObject
        val mappingsUrl = parsed["downloads"]?.jsonObject
            ?.get("client_mappings")?.jsonObject
            ?.get("url")?.jsonPrimitive?.content
            ?: throw Exception("No client_mappings found")

        return client.get(mappingsUrl).bodyAsText()
    }

    // 下载 Fabric Yarn mappings（简化版）
    suspend fun downloadYarnMappings(gameVersion: String): String {
        val response: String = client.get("https://meta.fabricmc.net/v2/versions/mappings/$gameVersion").body()
        // 实际项目中可进一步解析
        return response
    }
}