package com.example.minecraftmixinhelper.data.remote

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable

@Serializable
data class FabricGameVersion(val version: String, val stable: Boolean = false)

@Serializable
data class FabricLoaderVersion(val loader: LoaderInfo)

@Serializable
data class LoaderInfo(val version: String)

@Serializable
data class YarnVersion(
    val version: String,
    val stable: Boolean,
    val gameVersion: String = "",
    val maven: String = ""
)

class FabricApi(private val client: HttpClient) {
    suspend fun getGameVersions(): List<FabricGameVersion> {
        return client.get("https://meta.fabricmc.net/v2/versions/game").body()
    }

    suspend fun getLoaderVersions(gameVersion: String): List<FabricLoaderVersion> {
        return client.get("https://meta.fabricmc.net/v2/versions/loader/$gameVersion").body()
    }

    // Yarn 映射版本列表（含 stable 字段）
    suspend fun getYarnVersions(): List<YarnVersion> {
        return client.get("https://meta.fabricmc.net/v2/versions/yarn").body()
    }
}
