package com.example.minecraftmixinhelper.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.minecraftmixinhelper.data.local.VersionEntity
import com.example.minecraftmixinhelper.data.repository.MappingRepository
import com.example.minecraftmixinhelper.domain.service.McVersionComparator
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
    private val repository: MappingRepository,
    private val downloadManager: DownloadManager
) : ViewModel() {

    private val _versions = MutableStateFlow<List<VersionEntity>>(emptyList())
    val versions: StateFlow<List<VersionEntity>> = _versions.asStateFlow()

    // 全局状态与进行中的下载集合，均来自应用级 DownloadManager（支持后台下载）
    val status: StateFlow<DashboardStatus> = downloadManager.status
    val activeIds: StateFlow<Set<String>> = downloadManager.activeIds
    val progress: StateFlow<DownloadProgress> = downloadManager.progress

    init {
        loadVersionsIfNeeded()
    }

    private fun loadVersionsIfNeeded() {
        viewModelScope.launch {
            repository.getVersions().collect { cached ->
                // 数据库按 lastUpdated 返回，需在内存按版本号（降序，最新在前）排序，
                // 否则 1.10/1.8/1.16.2 这类会显示为混乱的插入顺序。
                _versions.value = cached.sortedWith { a, b ->
                    McVersionComparator.compare(b.version, a.version)
                }
                if (cached.isEmpty()) downloadManager.refreshVersions()
            }
        }
    }

    fun refreshVersions() = downloadManager.refreshVersions()

    fun downloadMappings(entity: VersionEntity) = downloadManager.download(entity)
}
