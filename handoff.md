# Mixin-helper 交接文档（Handoff）

> 最后更新：2026-08-07。本文合并了历次交接的全部内容，只保留**当下有效**的信息。
> 项目：Minecraft Mixin Helper —— Android 端 Minecraft 模组开发辅助工具，多源映射（Mojang / Fabric Yarn / Forge / NeoForge / Parchment）下载解析、Room+FTS4 离线存储与搜索。

---

## 1. 仓库与代码状态（当下）

- 本地仓库位于 `/home/user/Mixin-helper`，分支 `arena/019fdac9-mixin-helper`（基于 main `e829ada`），**工作树干净**，所有成果已提交（最近提交 `79c0d2d`）。
- **PR #6 已合并/关闭，本会话 GitHub 远程权限失效**（无法 push / 开 PR）。下一个 agent 须在新会话中从本地仓库推新分支开新 PR。
- 功能清单（全部完成）：4 个解析器、多源版本列表、下载编排（Mojmap/Yarn/Parchment + Forge/NeoForge 归一化）、Room v1→v2→v3 迁移、FTS4 搜索、搜索建议下拉 + 最近搜索、Dashboard/Search UI、应用图标（全密度 + 自适应）。
- CI（`assembleDebug`）曾在 PR #6 上验证通过；`testDebugUnitTest` 步骤**未合入** CI（见 §5）。

## 2. 环境与网络（可能因环境而需要改变，以实测为准）

- 沙箱直连外网被墙：curl / urllib 全部失败（HTTP 000 / TLS 被切断）；**无法本地跑 Gradle**（依赖仓库不可达），编译/测试以 GitHub CI 为准。
- `fetch_page` ✅ 走平台网络，可抓取真实网页 / JSON / GitHub API 与 raw 文件（是获取真实数据的唯一通道；大文件如 10MB client_mappings.txt 会被截断/502，宜找小样本或官方测试资源）。
- `web_search` ✅；`pip` ✅ 仅限 PyPI（需 `--break-system-packages`，Pillow 已装）。

## 3. 映射格式要点（已用真实数据/官方源码核实，改代码时勿凭记忆重写）

### 3.1 Mojang client_mappings.txt（ProGuard 变体，参考 Feather io-proguard `MetadataProguardParser`）
- 类行：`pkg.Class -> obf:`（冒号结尾）
- 方法行：`[start:end:]返回类型 名称(参数1, 参数2) -> obf` —— 可读类型、逗号分隔、数组用 `[]` 后缀；描述符由代码转 JVM 形式
- 字段行：`类型 名称 -> obf`
- 保留经典 ProGuard 格式（`name(params)ret -> obf` / `name:desc -> obf`）作回退
- 26.x+ 的 version.json 已无 `client_mappings`（Mojang 停止发布）→ 明确报错并建议 Yarn

### 3.2 Tiny v2（参考 Fabric Wiki `documentation:tiny2`、tiny-remapper 测试资源）
- 表头：`tiny 2 0 <ns0> <ns1> ...`；类行 `c <ns0名> <ns1名>`；成员行 `f|m <desc> <ns0名> <ns1名> ...`（**desc 在前，owner 从最近类块继承**）；`p` 为参数行
- **必须两遍解析**：真实 Yarn 类按字典序排列，成员描述符可引用「后置定义」的类；单遍解析会让描述符重映射失效（已踩坑，见 §4）
- 描述符按规范引用「源」命名空间类名，输出前用完整 classMap 重映射为可读命名空间

### 3.3 Parchment JSON（参考 Feather `docs/specs/MappingDataContainer.md` 与 Gson adapter）
- 官方导出是**数组形态**（versioned MDC）：`{"version","packages":[{"name"}],"classes":[{"name","javadoc":[行...],"methods":[{"name","descriptor","parameters":[{"index","name"}]}]}]}`
- 参数 index 语义：非静态方法从 1 起（0 = 隐式 this），long/double 占 2 槽；解析时按槽位落位
- javadoc 为按行数组，join 后需 `trimEnd()`
- 解析器兼容旧 Map 形态（`classes: {类名: {methods: {"name(desc)ret": {...}}}}`）作为回退

### 3.4 Parchment 数据源（真实世界已变化，旧 URL 已死）
- maven 后端迁移（maven.parchmentmc.org → ldtteam.jfrog.io），**坐标改为** `org.parchmentmc.data:parchment-<mc>:<YYYY.MM.DD>@zip`（zip 内条目为 `parchment.json`）；旧坐标 `parchment:<ver>:tiny@zip` 作回退
- 版本列表用 GitHub 分支 API（`ParchmentMC/Parchment` 的 `versions/X.Y.x` 分支）；补丁版本（如 1.21 → 1.21.11）从分支 `build.gradle` 的 `compass { version = '...' }` 解析
- **zip 文件名已实网确认**（2026-08-07 经 ldtteam JFrog 目录列表核实 1.21.1 / 1.21.8）：**`parchment-<mc>-<date>.zip`**（如 `parchment-1.21.1-2024.11.17.zip`、`parchment-1.21.8-2025.07.20.zip`）；`officialExport-<date>.zip` 为历史遗留命名，**真实仓库中不存在**，已从代码移除
- **仓库 base 已实网确认并修正**：真实仓库在 `https://ldtteam.jfrog.io/artifactory/parchmentmc-public/`（注意是 `parchmentmc-public` 而非 `parchmentmc`）；`maven.parchmentmc.org` 现重定向到该 JFrog 仓库。`MappingDownloader` 新坐标已改为优先请求 JFrog、`maven.parchmentmc.org` 兜底

### 3.5 Forge / NeoForge 版本归一化
- forge：`1.20.1-47.2.0` → `1.20.1`；neoforge：`21.1.78` → `1.21.1`、`26.2.0.49-beta` → `26.2`（MC 26+ 直接以 MC 版本为前缀）
- 下载时按 MC 版本查 Mojang manifest 解析 version.json URL；Parchment 数据缺失时对 forge/neoforge 优雅降级为纯 Mojmap
- 版本比较：数字元组逐位比较，遇非纯数字片段截断（`1.21.4` == `1.21.4-rc1`）

## 4. 代码坑（已踩过，避免重踩）

- **`contentOrNull` 是扩展属性**：ktor 2.3.7 → kotlinx-serialization-json 1.5.1，`JsonPrimitive.contentOrNull` 必须显式 `import kotlinx.serialization.json.contentOrNull`（漏了会整片编译失败）
- **`Regex.matches()` 是整串匹配**：做前缀校验要用 `containsMatchIn`（`McVersionComparator` 已踩；`MappingDownloader.kt:103` 的 `^\d+\.\d+$` 是整串匹配的正确用法，勿改）
- **Kotlin 反引号函数名不允许 `> < : ; [ ] / \` 等字符**（测试方法名里写 `->` 会编译失败，用「到」代替）
- Dashboard 下载必须用 `entity.mappingType`（`decideMappingType` 只是兜底；Parchment 源曾被覆写为 mojmap 而必然失败）
- FTS4 前缀匹配写法 `MATCH "\"词\"*"`（输入先清洗特殊字符）；Room 查询用子查询避免 FTS 表 JOIN 别名混用
- **自适应图标前景必须是「有密度限定符」的位图**：`ic_launcher_foreground.png`（108×108）**不能放在 `mipmap-anydpi-v26/`**（`anydpi` = 密度无关，放位图会让资源表无法解析尺寸，第三方工具/系统读图标时得到宽高 0 → `Bitmap.createBitmap: width and height must be > 0`，表现为图标纯色/无法渲染）。应放在 `mipmap-xxxhdpi/ic_launcher_foreground.png`，`mipmap-anydpi-v26/` 只留 `ic_launcher.xml` / `ic_launcher_round.xml`
- **纯 Compose 应用不要引用 `Theme.Material3.*` XML 主题**：它来自 `com.google.android.material`（未声明该依赖），应使用框架 `android:Theme.Material.Light.NoActionBar`（详见 §8）

## 5. 测试现状

- 36 个 JUnit4 用例（AsmDescriptorParser/Builder、MojmapParser、TinyParser、ParchmentParser、McVersionComparator），测试资源含 tiny-remapper 官方 `mapping1-3.tiny`
- 首轮 CI 跑出 **9 个失败，已全部修复**（本地提交 `79c0d2d`）：
  - Parser bug（真实功能影响）：`AsmDescriptorParser` 数组维度拼在类型名前；`McVersionComparator` 用 `matches()` 整串匹配导致三段版本号被拒（Forge 列表丢版本）；`TinyParser` 单遍解析导致后置类引用重映射失效（改两遍解析）
  - Test bug：MojmapParserTest 方法数 3→4、getPoses 描述符补 `()`；TinyParserTest mapping1 类名断言写反
  - 不一致：`ParchmentParser.javadocOf` 数组形态 join 后 `trimEnd()`
- 修复已用 Python 逐断言模拟验证，但**未在真实 JVM 跑过**——下一 agent 开 PR 后以 CI 的 `testDebugUnitTest` 为最终裁决，预期 36/36 全绿
- **CI 单测步骤未合入**：仓库规则要求修改 `.github/workflows/*` 需 `workflows` 权限（GitHub App 无）。测试代码已就绪，仓库所有者在 `assembleDebug` 前手动加一行 `./gradlew testDebugUnitTest -q` 即可

## 6. 遗留事项（需真机 / 有网环境）

- Room v1→v2→v3 迁移从未在真机运行
- Mojmap / Yarn / Parchment 真实下载 + 解析的端到端从未验证（解析器已有真实格式样本单测覆盖）
- ~~Parchment 新坐标 zip 文件名候选~~ 已实网确认（`parchment-<mc>-<date>.zip`，见 §3.4），代码已同步修正 base URL 与文件名

## 7. 下一步（给下一个 agent）

1. 新会话中直接基于本地仓库（已含全部修复）推新分支 → 开 PR → CI 跑 `assembleDebug` + `testDebugUnitTest`
2. 若测试仍有失败，按 §3 格式要点与 §4 代码坑排查
3. 向仓库所有者说明：在 CI 补单测步骤（一行命令，需 workflows 权限）
4. 有条件时做真机端到端验证（§6）

## 8. 安装闪退 / 图标问题排查记录（2026-08-07）

现象：真机（Android 16 / SDK 36）安装应用时「安装程序闪退」，MT 管理器显示图标为纯色，其尝试提取图标为 PNG 时抛 `IllegalArgumentException: width and height must be > 0`。

已修复两处「前置」根因：
1. **自适应图标前景位图放错目录**：`ic_launcher_foreground.png` 原在 `mipmap-anydpi-v26/`（`anydpi` = 密度无关），位图无密度限定符，资源表无法解析其尺寸 → 第三方/系统读图标得到宽高 0。已 `git mv` 至 `mipmap-xxxhdpi/ic_launcher_foreground.png`（108×108 为标准 xxxhdpi 前景尺寸），`mipmap-anydpi-v26/` 只保留两个 adaptive-icon XML。
2. **纯 Compose 项目引用了未声明的 XML 主题**：`Theme.Material3.DayNight.NoActionBar` 来自 `com.google.android.material`，但 `build.gradle.kts` 未声明该依赖，且该 style 是自引用（parent=自身）→ 无法解析。已改为框架主题 `android:Theme.Material.Light.NoActionBar`（API 21+，minSdk 26 恒可用），应用与 Activity 的 `android:theme` 均指向新的 `Theme.MinecraftMixinHelper`。

图标 PNG 本身经验证均为标准 8-bit RGBA、尺寸合规、内容正常，**非图标图片损坏**；问题在资源放置位置与主题依赖。

> 若换装后仍失败，需补充真机报错文案（如 "There was a problem parsing the package" / "App not installed" / logcat）与目标 SDK、签名信息进一步定位。

## 9. 版本列表 / 下载失败 / UI 修复记录（2026-08-07）

现象（真机复现）：
- 下载报 `Serializer for class 'VersionManifest' is not found`、`缺少 version.json URL`
- 版本列表不全（mojmap / fabric 空白，forge / neoforge 不全）
- 加载器筛选框被挤压、提示/进度被版本列表遮住、下载按钮可重复点击

已修复：
1. **kotlinx.serialization 编译器插件缺失**（致命根因）：`@Serializable` 类（`VersionManifest` / `FabricGameVersion` / `YarnVersion` 等）因未应用 `org.jetbrains.kotlin.plugin.serialization` 而**运行时无序列化器** → `body()` 反序列化抛 "Serializer not found"，导致 mojang / fabric 版本源整源空白。已：`gradle/libs.versions.toml` 加 `jetbrains-kotlin-serialization` 插件（version.ref=kotlin）与 `kotlinx-serialization-json:1.6.3`；`app/build.gradle.kts` 应用插件并加依赖。
2. **移除独立 Parchment 源**：Parchment 不再单独列（随 forge/neoforge 一起下载），删除 `fetchParchmentVersions` / `ForgeNeoForgeApi.getParchmentBranches`。这也消除了独立 Parchment 源 `versionJsonUrl` 为空导致的 `缺少 version.json URL` 报错。
3. **loader 改名 mojang→mojmap**：`fetchMojangVersions`→`fetchMojmapVersions`，loader="mojmap"。
4. **版本过滤规则**：`isSupportedMcVersion` 只删 `1.13.x` 与 `>=26`（`McVersionComparator.compare(v,"26")<0`），其余全保留；forge/neoforge 不再 `take(10)` 截断，改为归一化 MC 版本后 `distinct()` + 版本降序排序。
5. **UI**：加载器选项改为 `ALL/Mojmap/Fabric/Forge/NeoForge`，筛选行改 `horizontalScroll`（横向滑动，不再被挤压/垂直拉伸）；状态提示/进度条移到版本列表**上方**（不被遮住）；下载按钮用 `downloadingIds` 集合禁用防重复点击，已缓存条目不显示按钮。
