# 交接文档 / Session Handoff — Minecraft Mixin Helper

> **给下一个 coding session 的 agent 看**。本会话（分支 `arena/019fd62f-mixin-helper`）已把项目的主要功能全部实现完成，但**改动只提交在本地，尚未推送到 GitHub**。接手后第一件事就是推送 + 开 PR（见第 1 节）。

---

## 0. 一句话现状

核心功能已全部可用（代码层面）：多源版本列表 → 真实映射下载（Mojang / Yarn / Parchment）→ 离线解析入库 → 模糊搜索 + 详情复制。剩余工作只有：**推送开 PR、真实环境编译验证、补单元测试、CI 打磨**。

---

## 1. 新 session 第一件事（必须）

当前分支 `arena/019fd62f-mixin-helper` 上有 **3 个未推送的本地提交**（见第 5 节），GitHub 上只能看到最初那个 README PR。接手后：

```bash
git push origin arena/019fd62f-mixin-helper
gh pr create --base main --head arena/019fd62f-mixin-helper \
  --title "完善映射下载与搜索：多源版本列表 / Mojmap / Yarn / Parchment" \
  --body "见 HANDOFF.md 第 2 节"
```

若沙箱有 JDK + Android SDK（`which java && echo $ANDROID_HOME`），**先跑一次构建验证再开 PR**：

```bash
chmod +x gradlew
./gradlew assembleDebug        # 或 ./gradlew test 跑单元测试（若有）
```

> ⚠️ 本会话的沙箱**没有 JDK / Android SDK**，从未真正编译过，只能保证代码逻辑层正确。编译验证是 PR 前最重要的待办。

---

## 2. 本会话做了什么（改动全览）

### 2.1 已完成功能

| 功能 | 说明 |
|------|------|
| **版本列表多源获取** | Mojang 正式版(40) + Fabric 稳定版(30) + Forge(10) + NeoForge(10) + Parchment 条目；单源失败不影响其他源；刷新时保留"已缓存"标记；版本号按数字元组排序（修复字符串字典序比较 bug） |
| **Mojang 映射下载 + 完整解析** | `MojmapParser` 完整解析 ProGuard 格式的**类/方法/字段**（原实现只解析类且 `take(500)` 截断）；生成 JVM 描述符 / 参数 / 返回类型；每个版本存真实 `versionJsonUrl`（修复原来写死的假 URL） |
| **Yarn 映射下载 + 解析** | 查 meta.fabricmc.net 最新稳定版 → 下载 maven yarn jar → 解压 `mappings/mappings.tiny` → `TinyParser` 解析 **Tiny v1 / v2**，描述符中的类名自动重映射为可读名 |
| **Parchment 参数名 + Javadoc** | 查 maven.parchmentmc.org 最新版 → 下载 zip → 解压 `parchment.json` → 按官方 MDC 规范解析，与 Mojmap 合并；参数 index 正确处理 this 占位、long/double 双位、static 回退 |
| **搜索页重构** | 版本范围选择（全部/已下载版本）、250ms 防抖实时搜索、**FTS4 前缀自动补全**（`"词"*`）+ LIKE 回退、点击结果弹详情（描述符/参数/返回类型/参数名/Javadoc）+ 一键复制名称/描述符 |
| **Dashboard 重构** | Loader 过滤版本下拉（Mojang/Fabric/Forge/NeoForge/Parchment）、"✓ 已缓存"标记、**持久状态卡片**（成功/失败不会被进度条冲掉）+ 重试按钮 |
| **Room 迁移** | v1→v2（mappings 加 version/loader 列、versions 改复合主键 id=version\|loader、加 versionJsonUrl）、v2→v3（mappings 加 paramNames/javadoc 列）；移除破坏性迁移 |
| **修复** | 底部导航高亮写死、Converters 空串返回 `[""]`、FTS SQL 别名混用、FTS 输入转义、`decideMappingType` 版本号比较 |

### 2.2 已删除（产品决策）

- **Mixin 代码示例生成器**（用户判断：会写的不需要模板，不会写的用不上）
  - 删除 `ui/mixin/MixinConfiguratorScreen.kt`、`domain/service/MixinTemplateRenderer.kt`（生成的代码含无效的 `@Local` 注解）、`domain/model/AsmDescriptor.kt`（死代码）
  - **保留** `domain/service/AsmDescriptorBuilder.kt`（Mojmap/Tiny 解析器仍在用）
  - 应用名 "Minecraft Mixin Helper" 不变

### 2.3 文件清单

**新增**：
```
domain/service/AsmDescriptorParser.kt   # JVM 描述符解析（参数/返回类型）
domain/service/MojmapParser.kt          # Mojang client_mappings 解析
domain/service/TinyParser.kt            # Yarn Tiny v1/v2 解析 + 描述符重映射
domain/service/ParchmentParser.kt       # parchment.json 解析 + 与 Mojmap 合并
```

**修改**（核心）：
```
data/repository/MappingRepository.kt    # 版本拉取 / 下载编排 / 搜索（最大改动）
data/remote/MappingDownloader.kt        # Mojang + Yarn jar 解压 + Parchment zip 解压
data/local/AppDatabase.kt               # 迁移 1→2、2→3
data/local/MappingEntity.kt             # +version/loader/paramNames/javadoc
data/local/VersionEntity.kt             # 复合主键 + versionJsonUrl
data/local/MappingDao.kt                # 搜索 SQL 重写 + DownloadedVersion
data/remote/MojangApi.kt / FabricApi.kt
di/AppModule.kt                         # 注册迁移链
ui/dashboard/*  ui/search/*  MainActivity.kt
README.md  TODO.md（状态已勾选）
```

---

## 3. 遗留待办（照抄 TODO.md 未完成项）

- [ ] **编译验证**（最高优先，沙箱无 SDK 无法做）：`./gradlew assembleDebug`
- [ ] **单元测试**：目前 0 测试。优先覆盖纯逻辑——`AsmDescriptorBuilder`、`MojmapParser`、`TinyParser`、`ParchmentParser`、版本号比较。可参考思路：本会话用 Python 1:1 复刻过解析逻辑并跑断言，全部通过（样例与断言在 /tmp，未入库）
- [ ] **CI 打磨**：`gradle-wrapper.jar` 目前靠 CI 里 curl 下载，建议直接提交 jar 入库；可在 workflow 加 `./gradlew test` 步骤
- [ ] （可选）搜索详情加"复制 Mixin 目标"按钮，拼 `@Inject(method = "tick()V")` 字符串——一行代码，不做也不影响

---

## 4. 已知技术要点 / 坑（接手者必读）

1. **26.x 起新版 Minecraft 的 version.json 已不再附带 `client_mappings`**（Mojang 官方停止发布），下载 Mojmap 会明确报错，提示改用 Fabric/Yarn。这是外部事实，不是 bug。
2. **Parchment 并非所有版本都有**数据（maven.parchmentmc.org 查不到会明确报错）。
3. **FTS4 前缀匹配**写法：`MATCH "\"词\"*"`，多个词用空格连接；输入需清洗（去掉 `" * -` 等特殊字符）。
4. **Room 迁移链**必须保持 `MIGRATION_1_2` + `MIGRATION_2_3` 都注册，否则从 v1 老库升级会崩（`AppModule.provideDatabase` 已注册）。
5. 版本列表的 `VersionEntity.id` = `"$version|$loader"` 复合主键；`loader` 取值：`mojang / fabric / forge / neoforge / parchment`；`mappingType` 取值：`mojmap / yarn / mcp / parchment`。

---

## 5. 分支与提交状态

- 分支：`arena/019fd62f-mixin-helper`（本会话专用，勿换分支）
- 基线：`74f10e9`（main 上最初的 README 更新）
- **未推送提交（3 个）**：
  ```
  e6b36b2  支持 Parchment 映射（参数名 + Javadoc），完成中优先级任务 7
           （含此前版本列表/映射解析/搜索等全部工作）
  93b7f18  更新 TODO 清单状态：标记已完成项
  5c75c1d  按产品决策移除 Mixin 代码示例生成器
  ```
- 工作树干净（`git status` 无未提交改动）

---

## 6. 给用户的提示

- 开新 session 时，把本文件路径告诉 agent：`请阅读仓库根目录的 HANDOFF.md 并执行第 1 节操作（push + 开 PR），然后按第 3 节待办继续`
- 本 session 的所有验证脚本（Python 复刻解析器 + 断言）位于沙箱 `/tmp/verify_parsers.py`、`/tmp/verify_parchment.py`，新环境通常没有，可按需重写为 JUnit。