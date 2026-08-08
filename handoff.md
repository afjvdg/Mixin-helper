# Mixin-helper 交接文档（Handoff）

> 最后更新：2026-08-08。功能已全部完成并可用。本文只保留**当下有效**的信息，供下一个 agent 交接。
> 项目：Minecraft Mixin Helper —— Android 端 Minecraft 模组开发辅助工具，多源映射下载解析、离线存储、毫秒级实时搜索。

---

## 1. 仓库与代码状态（当下）

- 本地仓库 `/home/user/Mixin-helper`，工作分支 `arena/019fdbb9-mixin-helper`（远程同名分支已推送），基于 `main`。
- **全部成果位于 PR #8**（OPEN）：`https://github.com/afjvdg/Mixin-helper/pull/8`。尚未合并到 `main`。
- 功能清单（全部完成）：
  - 多源版本列表（Fabric / Forge / NeoForge）+ 与 Mojang 正式版对照过滤 + 版本降序排序。
  - 下载编排：Yarn(Tiny v1/v2) / Mojang client_mappings / MCP(Forge Maven joined.srg + stable CSV) / Parchment(新坐标) + Forge/NeoForge 归一化。
  - 离线存储（Room）v1→v2→v3 迁移。
  - 内存前缀自动补全搜索（可读名/混淆名/类名，支持简单类名与段路径）。
  - Dashboard / Search 双界面、后台下载 + 全局锁 + 进度/速度显示。
  - 应用图标（全密度 + 自适应）。
- CI：`testDebugUnitTest` + `assembleDebug` 每次构建都跑，当前 **通过**。

## 2. 环境与网络

- 沙箱直连外网被墙（curl/urllib 失败），**无法本地跑 Gradle**；编译/测试以 GitHub CI 为准。
- `fetch_page` ✅（平台网络，抓真实网页/JSON/GitHub API/raw）；`web_search` ✅；`pip` ✅（PyPI，需 `--break-system-packages`）。

## 3. 数据源与格式要点（改代码时勿凭记忆重写）

### 3.1 Mojang client_mappings.txt（ProGuard 变体）
- 类行：`pkg.Class -> obf:`；方法行：`[start:end:]返回类型 名称(参数1, 参数2) -> obf`；字段行：`类型 名称 -> obf`；描述符由 `AsmDescriptorBuilder` 生成 JVM 形式。
- 保留经典 ProGuard 格式作回退。
- 26.x+ 无 `client_mappings` → 明确报错并建议 Yarn。

### 3.2 Tiny v1 / v2
- v2 表头 `tiny 2 0 <ns>...`；类行 `c`，成员行 `f|m <desc> <ns0名> <ns1名>`（owner 从最近类块继承）；`p` 参数行。
- v1 头 `v1 <ns>...`，扁平 `CLASS/FIELD/METHOD <owner> <desc> <ns0名> <ns1名>`。
- **必须两遍解析**（真实 Yarn 类字典序，成员描述符可引用后置类）；两遍都先把完整 classMap 收集齐再输出成员。
- 命名空间：源取 intermediary/hashed/yarn/official，目标可读取 named/mojang/official；描述符按规范引用源命名空间类名，输出前重映射为可读。

### 3.3 Parchment JSON（数组形态）
- `{"version","classes":[{"name","javadoc":[行],"methods":[{"name","descriptor","parameters":[{"index","name"}]}]}]}`；兼容旧 Map 形态作回退。
- 参数 index：非静态方法从 1 起（0=隐式 this），long/double 占 2 槽。
- javadoc 为按行数组，join 后 `trimEnd()`。

### 3.4 Parchment 数据源（已迁移）
- 后端已迁到 ldtteam JFrog：`https://ldtteam.jfrog.io/artifactory/parchmentmc-public/`（注意 `parchmentmc-public` 非 `parchmentmc`）；`maven.parchmentmc.org` 现重定向过去，作兜底。
- 坐标 `org.parchmentmc.data:parchment-<mc>:<YYYY.MM.DD>@zip`（zip 内 `parchment.json`）；旧坐标回退。
- zip 文件名实网确认：`parchment-<mc>-<date>.zip`（如 `parchment-1.21.1-2024.11.17.zip`）；`officialExport-*` 已移除（不存在）。

### 3.5 Forge / NeoForge 版本归一化
- forge：`1.20.1-47.2.0` → `1.20.1`；neoforge：`21.1.78` → `1.21.1`、`26.2.0.49-beta` → `26.2`（MC26+ 直接以 MC 为前缀）。
- 版本比较：数字元组逐位比较，非数字片段截断。
- 下载按 MC 版本查 Mojang manifest 解析 version.json URL；Parchment 缺失对 forge/neoforge 降级为纯 Mojmap。

### 3.6 MCP（Forge <1.17，Forge Maven）
- `de/oceanlabs/mcp/mcp/<mc>/mcp-<mc>-srg.zip` → `joined.srg`（`CL:`/`FD:`/`MD:`，混淆名→SRG，类名即可读）。
- `de/oceanlabs/mcp/mcp_stable/<build>-<family>/mcp_stable-<build>-<family>.zip` → `methods.csv`/`fields.csv`/`params.csv`，覆盖 1.7.10~1.15。
- CSV 列：`searge,name,side,desc`（MCP 名在 index1，desc=index3 Javadoc）；params：`p_<id>_<槽位>_`（非静态从 1 起，long/double 占 2 槽）。
- 三级命名链：Notch → SRG(`func_xxx`/`field_xxx`) → MCP 可读名。

## 4. 代码坑（已踩过，避免重踩）

- `JsonPrimitive.contentOrNull` 需显式 `import kotlinx.serialization.json.contentOrNull`。
- `Regex.matches()` 是整串匹配；前缀校验用 `containsMatchIn`（`MappingDownloader` 的 `^\d+\.\d+$` 是整串匹配正确用法）。
- Kotlin 反引号函数名不允许 `> < : ; [ ] / \` 等字符。
- **`object` 里不能用 `companion object`**（常量直接作为 `object` 的成员，否则编译失败）。
- 自适应图标前景位图（108×108）**必须**放 `mipmap-xxxhdpi/`，不能放 `mipmap-anydpi-v26/`（`anydpi`=密度无关，放位图会致资源无法解析尺寸 → 图标纯色/无法渲染）。
- 纯 Compose 应用**不要**引用 `Theme.Material3.*` XML 主题（来自未声明的 `com.google.android.material`），用框架 `android:Theme.Material.Light.NoActionBar`。
- **kotlinx.serialization 必须应用编译器插件** `org.jetbrains.kotlin.plugin.serialization` + 依赖 `kotlinx-serialization-json`，否则 `@Serializable` 运行时无序列化器（`Serializer not found`）。
- 内存搜索索引：排序与二分都**必须用小写 key**，否则大小写混合致二分失效。

## 5. 测试现状

- 单元测试：AsmDescriptorBuilder/Parser、MojmapParser、TinyParser(v1&v2)、ParchmentParser、McpParser、McVersionComparator、MappingIndex。测试资源含 tiny-remapper 官方 `mapping1-3.tiny`。
- CI 每次构建跑 `testDebugUnitTest`，当前全绿。

## 6. 遗留事项（需真机 / 有网环境，代码已就绪）

- Room v1→v2→v3 迁移未在真机升级路径实测。
- 各源真实下载+解析的端到端（Dashboard 下载 → 入库 → 搜索）未在真机完整验证（解析器已有真实格式单测覆盖）。
- Parchment 新坐标的运行时下载未在真机实测（文件名/base 已实网确认）。

## 7. 给下一个 agent

1. 所有工作都在 PR #8，请仓库所有者合并到 `main`（或按需关闭）。
2. 若需继续开发，基于 `arena/019fdbb9-mixin-helper` 推新分支开新 PR。
3. 有条件时做 §6 的真机端到端验证。
4. 可选增强：Javadoc / 参数名纳入搜索索引（当前只索引可读名/混淆名/类名）。
