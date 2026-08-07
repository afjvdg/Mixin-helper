package com.example.minecraftmixinhelper.ui.dashboard

import com.example.minecraftmixinhelper.data.local.VersionEntity
import com.example.minecraftmixinhelper.data.repository.MappingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用级下载管理器（单例，独立于任何界面的生命周期）。
 *
 * - **后台下载**：使用应用级 [CoroutineScope]，用户离开 Dashboard 页面（如切到搜索页）
 *   甚至页面被重建时，下载仍继续。
 * - **全局下载锁**：同一时刻只允许一个下载任务。已有下载在进行时，再次触发会给出明确提示
 *   （而非无响应）。
 */
@Singleton
class DownloadManager @Inject constructor(
    private val repository: MappingRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 正在下载的版本 id 集合（用于禁用对应按钮）
    private val _activeIds = MutableStateFlow<Set<String>>(emptySet())
    val activeIds: StateFlow<Set<String>> = _activeIds.asStateFlow()

    // 全局状态（提示 / 进度 / 成功 / 失败），供 Dashboard 展示
    private val _status = MutableStateFlow<DashboardStatus>(DashboardStatus.Idle)
    val status: StateFlow<DashboardStatus> = _status.asStateFlow()

    /** 从远程刷新版本列表（后台执行）。 */
    fun refreshVersions() {
        scope.launch {
            _status.value = DashboardStatus.Loading("正在从远程获取版本列表...")
            try {
                repository.fetchAndCacheVersions()
                _status.value = DashboardStatus.Success("版本列表已更新")
            } catch (e: Exception) {
                _status.value = DashboardStatus.Error("获取版本失败: ${e.message}")
            }
        }
    }

    /** 下载并解析映射（后台执行）。全局锁：同一时刻只允许一个下载。 */
    fun download(entity: VersionEntity) {
        if (_activeIds.value.isNotEmpty()) {
            _status.value = DashboardStatus.Error("已有下载正在进行，请等待完成后再下载其他版本")
            return
        }
        scope.launch {
            _activeIds.value = _activeIds.value + entity.id
            _status.value =
                DashboardStatus.Loading("正在下载 ${entity.version} (${entity.loader})...")
            try {
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
            } finally {
                _activeIds.value = _activeIds.value - entity.id
            }
        }
    }
}
