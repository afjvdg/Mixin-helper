package com.example.minecraftmixinhelper.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.minecraftmixinhelper.data.local.VersionEntity
import com.example.minecraftmixinhelper.data.repository.MappingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
                // 重新收集一次
                repository.getVersions().collect { newVersions ->
                    _versions.value = newVersions
                    _status.value = DashboardStatus.Idle
                    return@collect
                }
            } catch (e: Exception) {
                _status.value = DashboardStatus.Error("获取版本失败: ${e.message}")
            }
        }
    }

    fun downloadMappingsForVersion(version: String, loader: String) {
        viewModelScope.launch {
            _status.value = DashboardStatus.Loading("正在决定映射类型并下载...")

            try {
                val mappingType = repository.decideMappingType(version, loader)
                _status.value = DashboardStatus.Loading("正在下载 $mappingType 映射...")

                // 实际项目中应从版本数据中获取真实 versionJsonUrl
                val versionJsonUrl = "https://piston-meta.mojang.com/v1/packages/.../$version.json"

                repository.downloadAndParseMappings(version, versionJsonUrl, mappingType)

                _status.value = DashboardStatus.Success
            } catch (e: Exception) {
                _status.value = DashboardStatus.Error("下载失败: ${e.message}")
            }
        }
    }
}