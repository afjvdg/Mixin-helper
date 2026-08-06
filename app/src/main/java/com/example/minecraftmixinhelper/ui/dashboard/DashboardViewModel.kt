package com.example.minecraftmixinhelper.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.minecraftmixinhelper.data.local.VersionEntity
import com.example.minecraftmixinhelper.data.repository.MappingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DashboardStatus {
    object Idle : DashboardStatus()
    data class Loading(val message: String) : DashboardStatus()
    object Success : DashboardStatus()
    data class Error(val message: String) : DashboardStatus()
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: MappingRepository
) : ViewModel() {

    private val _versions = MutableStateFlow<List<VersionEntity>>(emptyList())
    val versions: StateFlow<List<VersionEntity>> = _versions.asStateFlow()

    private val _status = MutableStateFlow<DashboardStatus>(DashboardStatus.Idle)
    val status: StateFlow<DashboardStatus> = _status.asStateFlow()

    init {
        // 启动时加载版本列表（如果数据库为空则从远程获取）
        loadVersionsIfNeeded()
    }

    private fun loadVersionsIfNeeded() {
        viewModelScope.launch {
            repository.getVersions().collect { cachedVersions ->
                if (cachedVersions.isEmpty()) {
                    fetchVersionsFromRemote()
                } else {
                    _versions.value = cachedVersions
                }
            }
        }
    }

    private fun fetchVersionsFromRemote() {
        viewModelScope.launch {
            _status.value = DashboardStatus.Loading("正在从远程获取版本列表...")
            try {
                repository.fetchAndCacheVersions()
                // 获取最新数据（取一次即可，避免一直 collect 导致状态被覆盖）
                val newVersions = repository.getVersions().first()
                _versions.value = newVersions
                _status.value = DashboardStatus.Success
            } catch (e: Exception) {
                _status.value = DashboardStatus.Error("获取版本失败: ${e.message}")
            }
        }
    }

    fun refreshVersions() {
        fetchVersionsFromRemote()
    }

    fun downloadMappingsForVersion(version: String, loader: String) {
        viewModelScope.launch {
            _status.value = DashboardStatus.Loading("正在决定映射类型并下载...")

            try {
                val mappingType = repository.decideMappingType(version, loader)
                _status.value = DashboardStatus.Loading("正在下载 $mappingType 映射...")

                // 从已缓存的版本列表中取真实的 versionJsonUrl
                val versionEntity = _versions.value.find {
                    it.version == version && it.loader.equals(loader, ignoreCase = true)
                } ?: repository.getVersions().first().find {
                    it.version == version && it.loader.equals(loader, ignoreCase = true)
                }
                // 对 mojang/fabric 做不同处理：
                // - mojang 版本必须有 versionUrl
                // - fabric/forge 等走 Mojmap 回退逻辑或直接用 mojang 映射
                val versionJsonUrl = versionEntity?.versionUrl
                    ?: run {
                        // 尝试在 mojang 列表中找对应 mc 版本的 URL
                        val mojangEntity = (_versions.value + repository.getVersions().first())
                            .find { it.version == version && it.loader == "mojang" }
                        mojangEntity?.versionUrl
                    }
                    ?: if (loader.equals("fabric", ignoreCase = true) || loader.equals("forge", ignoreCase = true) || loader.equals("neoforge", ignoreCase = true)) {
                        // 对于非 mojang loader，若找不到 mojang 的 URL，则尝试直接用 mc 版本的 mojang URL
                        // 这里抛异常提示用户先选择带 URL 的 mojang 版本
                        throw IllegalArgumentException("未找到版本 $version 的真实下载地址，请先刷新版本列表或选择 mojang 渠道的版本")
                    } else {
                        throw IllegalArgumentException("未找到版本 $version 的下载地址")
                    }

                repository.downloadAndParseMappings(version, versionJsonUrl, mappingType)

                _status.value = DashboardStatus.Success
            } catch (e: Exception) {
                _status.value = DashboardStatus.Error("下载失败: ${e.message}")
            }
        }
    }

    // 重载：直接传入实体，更可靠
    fun downloadMappingsForVersion(entity: VersionEntity) {
        downloadMappingsForVersion(entity.version, entity.loader)
    }
}
