package com.example.minecraftmixinhelper.data.remote

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable

@Serializable
data class FabricGameVersion(val version: String)

@Serializable
data class FabricLoaderVersion(val loader: LoaderInfo)

@Serializable
data class LoaderInfo(val version: String)

class FabricApi(private val client: HttpClient) {
    suspend fun getGameVersions(): List<FabricGameVersion> {
        return client.get("https://meta.fabricmc.net/v2/versions/game").body()
    }

    suspend fun getLoaderVersions(gameVersion: String): List<FabricLoaderVersion> {
        return client.get("https://meta.fabricmc.net/v2/versions/loader/$gameVersion").body()
    }
}