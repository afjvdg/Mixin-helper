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
    data class Success(val message: String) : DashboardStatus()
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
        loadVersionsIfNeeded()
    }

    private fun loadVersionsIfNeeded() {
        viewModelScope.launch {
            repository.getVersions().collect { cached ->
                _versions.value = cached
                if (cached.isEmpty()) refreshVersions()
            }
        }
    }

    fun refreshVersions() {
        viewModelScope.launch {
            _status.value = DashboardStatus.Loading("正在从远程获取版本列表...")
            try {
                repository.fetchAndCacheVersions()
                _status.value = DashboardStatus.Success("版本列表已更新")
            } catch (e: Exception) {
                _status.value = DashboardStatus.Error("获取版本失败: ${e.message}")
            }
        }
    }

    fun downloadMappings(entity: VersionEntity) {
        viewModelScope.launch {
            _status.value =
                DashboardStatus.Loading("正在下载 ${entity.version} (${entity.loader})...")
            try {
                // 优先使用版本列表阶段确定的映射类型；为空时兜底决策
                val mappingType = entity.mappingType
                    .ifBlank { repository.decideMappingType(entity.version, entity.loader) }
                _status.value = DashboardStatus.Loading("正在下载 $mappingType 映射...")
                repository.downloadAndParseMappings(
                    entity.version,
                    entity.versionJsonUrl,
                    mappingType,
                    entity.loader
                )
                _status.value = DashboardStatus.Success(
                    "✓ ${entity.version} (${entity.loader}) 映射下载成功，已缓存到本地！"
                )
            } catch (e: Exception) {
                _status.value = DashboardStatus.Error("下载失败: ${e.message}")
            }
        }
    }
}
