# TODO 清单 — Minecraft Mixin Helper

> 基于 2026-08-06 全仓库代码审查与交接文档整理。按优先级排列：P0 = 阻断核心功能，P1 = 功能缺陷，P2 = 质量完善。
> 状态更新（2026-08-06 重建后）：P0 全部完成；P1 已全部完成；P2 大部分完成，剩余单元测试与 CI 打磨。

---

## P0 — 阻断性问题（核心功能不可用）

- [x] **修复假 `versionJsonUrl`，下载映射当前必失败**
  - 已修复：`VersionEntity` 新增 `versionJsonUrl` 列，版本列表从 `VersionManifest` 存下真实 URL，下载时直接使用。

- [x] **`MappingDao.fuzzySearchFts` 的 SQL 别名混用，编译/运行会报错**
  - 已修复：改用 `WHERE id IN (SELECT rowid FROM mappings_fts WHERE mappings_fts MATCH :query)`。

- [ ] **验证项目能否通过编译**
  - ⚠️ 当前沙箱无 JDK / Android SDK / 网络，无法本地执行 `./gradlew assembleDebug`。
  - 代码由接手 agent 依据交接文档重建，需在真实环境或 CI 中编译验证。

## P1 — 功能缺陷

- [x] **`MixinConfiguratorScreen` 未接入导航，用户进不去**
  - 已决定删除：Mixin 代码示例生成器对目标用户无用，已移除相关文件与死代码。

- [x] **Mojmap 解析不完整：只解析类，方法/字段是空壳**
  - 已修复：新增 `MojmapParser`，完整解析类/方法/字段，填充 `descriptor` / `params` / `returnType`。

- [x] **`decideMappingType` 用字符串字典序比较版本号，结果不可靠**
  - 已修复：改为数字元组比较；实际映射类型现由版本列表条目决定，`decideMappingType` 仅作兜底。

- [x] **`MixinTemplateRenderer` 生成 `@Local` 注解，Mixin API 中不存在**
  - 已随 Mixin 配置器一并删除。

- [x] **底部导航选中状态写死**
  - 已修复：用 `navController.currentBackStackEntryAsState()` 计算当前路由，导航使用 `launchSingleTop` + `restoreState`。

## P2 — 代码质量与完善

- [x] **清理未使用代码**
  - `ForgeNeoForgeApi` / `MappingDownloader.downloadYarnMappings()` / `AsmDescriptorBuilder` 均已被使用。

- [x] **`VersionEntity` 存 URL**
  - 新增 `versionJsonUrl` 列，含 v1→v2 迁移。

- [x] **TypeConverter 空串边界**
  - 已修复：`takeIf { it.isNotEmpty() }` 过滤空串，避免返回 `[""]`。

- [x] **搜索结果空态/加载态 UI**
  - 无结果显示“未找到匹配项”；输入 250ms 防抖；搜索中显示加载指示器。

- [x] **FTS 查询输入转义**
  - 已实现 `toFtsMatchQuery` 清洗非字母数字字符、按词用双引号包裹 + 前缀 `*`。

- [x] **Room 迁移链**
  - 已实现 v1→v2（复合主键 + versionJsonUrl）、v2→v3（paramNames / javadoc），并在 `AppModule` 注册。

- [ ] **补充单元测试**
  - 目前 0 测试。优先覆盖纯逻辑：`AsmDescriptorParser`、`MojmapParser`、`TinyParser`、`ParchmentParser`、`versionTuple`。

- [ ] **CI 工作流打磨**
  - 已修正无效 action 版本（checkout@v4 / setup-java@v4 / upload-artifact@v4）。
  - 可进一步：提交 `gradle-wrapper.jar` 入库，或在 workflow 增加 `./gradlew test` 步骤。

---

## 里程碑建议

1. **M1（地基打通）**：P0 全部 → 真实环境 `assembleDebug` 出 APK。
2. **M2（核心链路可用）**：P1 全部 → 真实下载 → 解析 → 搜索（Mixin 代码生成功能已按产品决策移除）。
3. **M3（工程化）**：P2 处理 → 补测试、打磨 CI。
