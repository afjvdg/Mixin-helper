# Mixin-helper 交接文档（Handoff）

> 最后更新：2026-08-07。本文合并了历次交接的全部内容，只保留**当下有效**的信息。
> 项目：Minecraft Mixin Helper —— Android 端 Minecraft 模组开发辅助工具，多源映射（Mojang / Fabric Yarn / Forge / NeoForge / Parchment）下载解析、Room+FTS4 离线存储与搜索。

---

## 1. 仓库与代码状态（当下）

- 本地仓库位于 `/home/user/Mixin-helper`，分支 `arena/019fdac9-mixin-helper`（基于 main `e829ada`），**工作树干净**，所有成果已提交（最近提交 `79c0d2d`）。
- **PR #6 已合并/关闭，本会话 GitHub 远程权限失效**（无法 push / 开 PR）。下一个 agent 须在新会话中从本地仓库推新分支开新 PR。
- 功能清单（全部完成）：4 个解析器、多源版本列表、下载编排（Mojmap/Yarn/Parchment + Forge/NeoForge 归一化）、Room v1→v2→v3 迁移、FTS4 搜索、搜索建议下拉 + 最近搜索、Dashboard/Search UI、应用图标（全密度 + 自适应）。
- CI（`assembleDebug`）曾在 PR #6 上验证通过；`testDebugUnitTest` 步骤**未合入** CI（见 §5）。

## 2. 环境与网络（实测结论，勿再重复试探）

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
- zip 文件名留了两个候选（`parchment-<mc>-<date>.zip` / `officialExport-<date>.zip`），未实网确认

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
- Parchment 新坐标 zip 文件名候选未实网确认

## 7. 下一步（给下一个 agent）

1. 新会话中直接基于本地仓库（已含全部修复）推新分支 → 开 PR → CI 跑 `assembleDebug` + `testDebugUnitTest`
2. 若测试仍有失败，按 §3 格式要点与 §4 代码坑排查
3. 向仓库所有者说明：在 CI 补单测步骤（一行命令，需 workflows 权限）
4. 有条件时做真机端到端验证（§6）
