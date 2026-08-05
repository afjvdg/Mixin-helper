package com.example.minecraftmixinhelper.data.remote

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable

@Serializable
data class VersionManifest(val versions: List<VersionEntry>)

@Serializable
data class VersionEntry(val id: String, val url: String)

class MojangApi(private val client: HttpClient) {
    suspend fun getVersionManifest(): VersionManifest {
        return client.get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").body()
    }

    suspend fun getVersionJson(url: String): String {
        return client.get(url).body()
    }
}