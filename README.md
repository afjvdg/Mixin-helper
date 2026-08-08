# Minecraft Mixin Helper

> ⚠️ 本项目由 AI（Arena.ai Agent）辅助开发。功能已可正常使用，详见下方说明。

**🌐 [Read this README in English](README_EN.md)**

## 项目简介

**Minecraft Mixin Helper** 是 Android 端 Minecraft 模组开发辅助工具：下载并解析主流映射集（Mojang / Fabric Yarn / Forge / NeoForge / MCP / Parchment），离线存储在本地，并提供毫秒级的映射名实时搜索。

支持的映射集与下载方式：

| 加载器 | 使用映射 | 说明 |
| --- | --- | --- |
| Fabric | Yarn | 始终使用 Yarn（`meta.fabricmc.net`） |
| Forge（≥1.17） | Mojmap + Parchment | mojmap 官方映射 + Parchment（参数名 / Javadoc）捆绑下载 |
| Forge（<1.17） | MCP | 从 Forge Maven 下载 joined.srg + MCP stable CSV（含参数名 / Javadoc） |
| NeoForge | Mojmap + Parchment | 同 Forge ≥1.17 |

核心能力：

- 多源版本列表（Fabric / Forge / NeoForge），与 Mojang 官方正式版列表对照过滤，剔除预览版与垃圾版本；版本按号降序。
- 真实下载与解析：Yarn(Tiny v1/v2) / Mojang client_mappings / MCP(joined.srg+CSV) / Parchment(parchment.json)。
- 离线存储（Room），含 v1→v2→v3 数据库迁移。
- **内存前缀自动补全搜索**：有序数组 + 二分查找，对可读名 / 混淆名 / 类名（含简单类名与段路径，支持 `client.`）毫秒级实时补全；支持按类型 / 版本 / 加载器过滤，结果截断提示。
- 搜索结果详情：描述符 / 参数 / 参数名 / 返回类型 / Javadoc + 一键复制。
- 后台下载 + 全局下载锁 + 实时进度（进度条 / 已下载 / 总量 / 速度）。
- 下载状态持久反馈与重试。

---

## 构建

### GitHub Actions 自动构建

已配置完整工作流：推送 `main` / `master` 即自动跑单元测试（`testDebugUnitTest`）并构建 `assembleDebug`，产出 `app-debug.apk` 上传到 Artifacts。

获取 APK：仓库 **Actions** → 最新构建记录 → **Artifacts** → `app-debug`。

### 本地构建

```bash
chmod +x gradlew
./gradlew clean assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

> 注：`gradle-wrapper.jar` 为二进制文件，工作流会通过 `curl` 下载官方 v8.5.0 版本。

---

## 技术栈

- Kotlin + Jetpack Compose (Material 3)
- Room + FTS4
- Hilt（DI）
- Ktor Client (OkHttp) + kotlinx.serialization
- Gradle 8.5

---

## 测试

- 单元测试：解析器（AsmDescriptor / Mojmap / Tiny v1&v2 / Parchment / MCP）+ 版本比较 + 内存搜索索引。
- CI 每次构建都执行 `testDebugUnitTest`。

---

## 已知行为（非 bug）

- MC 26.x 起 Mojang 停止发布 `client_mappings`，下载 Mojmap 会明确报错并建议改用 Fabric / Yarn。
- Parchment 并非所有版本都有数据；缺失时 Forge / NeoForge 会优雅降级为纯 Mojmap。
- Forge 1.16.x 在列表中标记为 MCP，但 MCP 官方仅发布到 1.15；下载时会自动回退 Mojang 官方映射（若无该版本官方映射则报错）。

---

## 注意事项

- 首次构建需下载 Android SDK（约 5~15 分钟）。
- 生成的为 Debug APK，安装时需允许「未知来源应用」。

---

## 许可证

Apache License 2.0
