package com.example.minecraftmixinhelper.ui.dashboard

import com.example.minecraftmixinhelper.data.local.VersionEntity
import com.example.minecraftmixinhelper.data.remote.DownloadProgressListener
import com.example.minecraftmixinhelper.data.remote.MappingDownloader
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

/** 下载进度：已下载 / 总量（null 表示未知）、速度（字节/秒）。 */
data class DownloadProgress(
    val downloaded: Long = 0,
    val total: Long? = null,
    val speed: Long = 0,
    val percent: Float? = null // 0..1；null 表示未知
)

/**
 * 应用级下载管理器（单例，独立于任何界面的生命周期）。
 *
 * - **后台下载**：使用应用级 [CoroutineScope]，用户离开 Dashboard 页面（如切到搜索页）
 *   甚至页面被重建时，下载仍继续。
 * - **全局下载锁**：同一时刻只允许一个下载任务。已有下载在进行时，再次触发会给出明确提示。
 * - **下载进度 + 速度**：通过 [MappingDownloader] 的字节级回调实时更新。
 */
@Singleton
class DownloadManager @Inject constructor(
    repository: MappingRepository,
    downloader: MappingDownloader
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 正在下载的版本 id 集合（用于禁用对应按钮）
    private val _activeIds = MutableStateFlow<Set<String>>(emptySet())
    val activeIds: StateFlow<Set<String>> = _activeIds.asStateFlow()

    // 全局状态（提示 / 进度 / 成功 / 失败），供 Dashboard 展示
    private val _status = MutableStateFlow<DashboardStatus>(DashboardStatus.Idle)
    val status: StateFlow<DashboardStatus> = _status.asStateFlow()

    // 下载进度
    private val _progress = MutableStateFlow<DownloadProgress>(DownloadProgress())
    val progress: StateFlow<DownloadProgress> = _progress.asStateFlow()

    private val _repository = repository

    init {
        // 把字节级进度回调接到 DownloadManager（单例全局锁保证同时只有一个下载）。
        // onDownload 会频繁触发（每读一块一次），故速度用「固定时间窗口」采样：
        // 只在距上次采样满 SPEED_WINDOW_MS 时才用该窗口内累计字节数算平均速度，
        // 避免瞬时速率随回调频率跳变。
        downloader.bindProgressListener(object : DownloadProgressListener {
            override fun onProgress(downloaded: Long, total: Long?) {
                val now = System.currentTimeMillis()
                // 进度每次都更新
                val percent = if (total != null && total > 0) {
                    (downloaded.toFloat() / total).coerceIn(0f, 1f)
                } else null
                // 速度按窗口采样：窗口内字节数 / 窗口时长
                var newSpeed = _progress.value.speed
                if (now - lastSpeedSampleTime >= SPEED_WINDOW_MS) {
                    val elapsed = (now - lastSpeedSampleTime).coerceAtLeast(1)
                    val delta = (downloaded - lastSpeedSampleDownloaded).coerceAtLeast(0)
                    val inst = delta * 1000 / elapsed
                    // 轻量平滑：新速度取 3/4 旧 + 1/4 新，抑制跳变
                    newSpeed = (newSpeed * 3 + inst) / 4
                    lastSpeedSampleTime = now
                    lastSpeedSampleDownloaded = downloaded
                }
                _progress.value = DownloadProgress(
                    downloaded = downloaded,
                    total = total,
                    speed = newSpeed,
                    percent = percent
                )
            }
        })
    }

    private var lastSpeedSampleTime: Long = System.currentTimeMillis()
    private var lastSpeedSampleDownloaded: Long = 0

    private companion object {
        const val SPEED_WINDOW_MS = 500L
    }

    /** 从远程刷新版本列表（后台执行）。 */
    fun refreshVersions() {
        scope.launch {
            _status.value = DashboardStatus.Loading("正在从远程获取版本列表...")
            try {
                _repository.fetchAndCacheVersions()
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
            _progress.value = DownloadProgress()
            lastSpeedSampleTime = System.currentTimeMillis()
            lastSpeedSampleDownloaded = 0
            _status.value =
                DashboardStatus.Loading("正在下载 ${entity.version} (${entity.loader})...")
            try {
                val mappingType = entity.mappingType
                    .ifBlank { _repository.decideMappingType(entity.version, entity.loader) }
                _status.value = DashboardStatus.Loading("正在下载 $mappingType 映射...")
                _repository.downloadAndParseMappings(
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
