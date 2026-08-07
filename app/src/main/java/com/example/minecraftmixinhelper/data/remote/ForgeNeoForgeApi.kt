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

    /**
     * Parchment 官方 maven 已迁移并按 MC 版本拆分 artifact（`parchment-<mc>`），
     * 不再有聚合的 maven-metadata；改为从 Parchment 数据仓库的分支列表
     * （`versions/X.Y.x`）获取支持的 MC 次版本。
     */
    suspend fun getParchmentBranches(): String {
        return client.get("https://api.github.com/repos/ParchmentMC/Parchment/branches?per_page=100").body()
    }
}
