# TODO 清单 — Minecraft Mixin Helper

> 基于 2026-08-06 全仓库代码审查整理。按优先级排列：P0 = 阻断核心功能，P1 = 功能缺陷，P2 = 质量完善。

---

## P0 — 阻断性问题（核心功能不可用）

- [ ] **修复假 `versionJsonUrl`，下载映射当前必失败**
  - 位置：`ui/dashboard/DashboardViewModel.kt` → `downloadMappingsForVersion()`
  - 问题：URL 是占位符 `https://piston-meta.mojang.com/v1/packages/.../$version.json`，中间的 `...` 是字面量，请求必然 404，走兜底逻辑。
  - 修复：从 `VersionManifest` 的版本条目中取真实 URL（`MojangApi.getVersionManifest()` 已返回 `VersionEntry.url`），在版本列表中存下来，下载时使用。

- [ ] **`MappingDao.fuzzySearchFts` 的 SQL 别名混用，编译/运行会报错**
  - 位置：`data/local/MappingDao.kt`
  - 问题：`JOIN mappings_fts fts ON ...` 但 WHERE 里写的是 `mappings_fts MATCH :query`；SQLite 中表一旦起别名，语句内只能用别名。
  - 修复：改为 `fts MATCH :query`（或去掉别名）。

- [ ] **验证项目能否通过编译**
  - 仓库里没有任何测试，且从未验证过构建。
  - 步骤：补 `gradle-wrapper.jar` → `./gradlew assembleDebug`，先确认全项目能编译，再谈其他修复。
  - 风险点：FTS 查询校验、`compose.menuAnchor()` API 变更、Room 实体/FTS 配置等编译期检查。

## P1 — 功能缺陷

- [ ] **`MixinConfiguratorScreen` 未接入导航，用户进不去**
  - 位置：`MainActivity.kt`
  - 修复：在 `NavHost` 注册 `composable("mixin")` 路由，并在搜索/仪表盘页面加入口。

- [ ] **Mojmap 解析不完整：只解析类，方法/字段是空壳**
  - 位置：`data/repository/MappingRepository.kt` → `parseMojmap()`
  - 修复：补全方法映射 `name(desc) -> obf` 与字段映射的正则解析，填充 `descriptor` / `params` / `returnType` 字段。
  - 同时去掉 `take(500)` 截断（或改为分页/分批插入，防止 OOM 的同时不丢数据）。

- [ ] **`decideMappingType` 用字符串字典序比较版本号，结果不可靠**
  - 位置：`data/repository/MappingRepository.kt`
  - 问题：`mcVersion <= "1.12.2"` / `>= "1.21.11"` 是字符串比较（如 "1.9" > "1.12"），且 1.21.11 分界点存疑。
  - 修复：实现 SemanticVersion 解析后按数字比较；分界版本（1.12.2 / 1.18 / Fabric mojmap 起始版本）需按事实核对。

- [ ] **`MixinTemplateRenderer` 生成 `@Local` 注解，Mixin API 中不存在**
  - 位置：`domain/service/MixinTemplateRenderer.kt`
  - 问题：生成的模板代码含 `@Local`，编译不过；且 `cancellable` 分支里两行代码完全一样（明显是复制粘贴）。
  - 修复：去掉 `@Local`，按 `@Inject` / `@Redirect` 等注入类型生成正确的回调参数（`CallbackInfo` / `CallbackInfoReturnable<T>`），并支持 `@Redirect` 的真实参数列表。

- [ ] **底部导航选中状态写死**
  - 位置：`MainActivity.kt`
  - 问题：两个 `NavigationBarItem` 的 `selected` 硬编码为 `true/false`，永远高亮"版本"。
  - 修复：用 `navController.currentBackStackEntryAsState()` 计算当前路由并对比。

## P2 — 代码质量与完善

- [ ] **清理未使用代码**
  - `ForgeNeoForgeApi` 定义了但从未被调用（Repository 注入了也没用）。
  - `MappingDownloader.downloadYarnMappings()` 只返回原始 JSON 未解析。
  - `AsmDescriptor` / `AsmDescriptorBuilder` 目前没有任何调用方（README 已声明移除 ASM 功能，可考虑删除或接入 Mixin 配置器做描述符自动生成）。

- [ ] **`VersionEntity` 存 URL：版本列表缓存后需要可回溯的真实映射 URL**
  - 当前 `downloadMappingsForVersion` 只有版本号 + loader，无法从缓存数据拿到真实下载地址，离线后再点下载会失败。

- [ ] **TypeConverter 空串边界**
  - 位置：`data/local/Converters.kt`
  - 问题：`value?.split(",")` 在空串时返回 `[""]`。
  - 修复：过滤空元素。

- [ ] **搜索结果空态/加载态 UI**
  - `SearchScreen` 无结果时显示空白，无 "无结果" 提示；搜索无防抖，输入过快会连续触发查询。

- [ ] **FTS 查询输入转义**
  - `fuzzySearchFts` 直接把用户输入拼进 `MATCH :query`，FTS 特殊字符（如 `"`、`*`、`-`）会导致语法错误或意外行为，需要转义。

- [ ] **补充单元测试**
  - 目前 0 测试。优先覆盖纯逻辑：
    - `AsmDescriptorBuilder`（数组、基本类型、嵌套）
    - `MappingRepository.parseMojmap`（类/方法/字段解析）
    - `decideMappingType`（各 loader × 版本组合）
    - `MixinTemplateRenderer`（各注入类型生成结果）
    - 版本号比较工具函数

- [ ] **CI 工作流打磨**
  - `gradle-wrapper.jar` 用 curl 从 Gradle 源码仓库拉取，建议改为把 jar 提交进仓库（GitHub 允许 1MB 内二进制）或改用 `gradle/actions/setup-gradle` 的 wrapper 校验。
  - 可加 `./gradlew test` 步骤，跑单元测试。

---

## 里程碑建议

1. **M1（先把地基打通）**：P0 全部 → 本地能 `assembleDebug` 出 APK
2. **M2（核心链路可用）**：P1 全部 → 真实下载 → 解析 → 搜索 → 生成 Mixin 代码闭环
3. **M3（工程化）**：P2 按需处理 → 补测试、清死代码、打磨 UI
