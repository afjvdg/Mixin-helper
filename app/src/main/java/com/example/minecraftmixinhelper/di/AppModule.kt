package com.example.minecraftmixinhelper.di

import android.content.Context
import androidx.room.Room
import com.example.minecraftmixinhelper.data.local.AppDatabase
import com.example.minecraftmixinhelper.data.local.MappingDao
import com.example.minecraftmixinhelper.data.local.VersionDao
import com.example.minecraftmixinhelper.data.remote.FabricApi
import com.example.minecraftmixinhelper.data.remote.ForgeNeoForgeApi
import com.example.minecraftmixinhelper.data.remote.MappingDownloader
import com.example.minecraftmixinhelper.data.remote.MojangApi
import com.example.minecraftmixinhelper.data.repository.MappingRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    @Provides
    @Singleton
    fun provideMojangApi(client: HttpClient): MojangApi = MojangApi(client)

    @Provides
    @Singleton
    fun provideFabricApi(client: HttpClient): FabricApi = FabricApi(client)

    @Provides
    @Singleton
    fun provideForgeNeoForgeApi(client: HttpClient): ForgeNeoForgeApi = ForgeNeoForgeApi(client)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "mixin_db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideMappingDao(db: AppDatabase): MappingDao = db.mappingDao()

    @Provides
    fun provideVersionDao(db: AppDatabase): VersionDao = db.versionDao()

    @Provides
    @Singleton
    fun provideMappingDownloader(client: HttpClient): MappingDownloader = MappingDownloader(client)

    @Provides
    @Singleton
    fun provideMappingRepository(
        mappingDao: MappingDao,
        versionDao: VersionDao,
        mojangApi: MojangApi,
        fabricApi: FabricApi,
        forgeApi: ForgeNeoForgeApi,
        downloader: MappingDownloader
    ): MappingRepository = MappingRepository(mappingDao, versionDao, mojangApi, fabricApi, forgeApi, downloader)
}