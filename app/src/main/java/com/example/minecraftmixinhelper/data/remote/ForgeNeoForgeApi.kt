package com.example.minecraftmixinhelper.data.remote

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class ForgeNeoForgeApi(private val client: HttpClient) {
    suspend fun getForgeMetadata(): String {
        return client.get("https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml").body()
    }

    suspend fun getNeoForgeMetadata(): String {
        return client.get("https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml").body()
    }

    suspend fun getParchmentMetadata(): String {
        return client.get("https://maven.parchmentmc.org/org/parchmentmc/data/parchment/maven-metadata.xml").body()
    }
}
