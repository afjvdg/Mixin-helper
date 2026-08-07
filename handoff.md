# 交接文档补充 / Handoff Addendum（2026-08-06）

> 给下一个 agent：本会话分支 `arena/019fd6f6-mixin-helper` 可能已被自动删除，且下一个 agent **无法查看本会话沙箱**。请从仓库主线/新分支拉取最新代码（PR #3 已可编译）。本文件由当前 agent 在「联通性测试」后撰写。

---

## 0. 联通性测试结论（复刻上一个 agent 的下载操作）

**结论：本沙箱无法连通任何外部站点，复刻失败。**

- DNS：`piston-meta.mojang.com` / `meta.fabricmc.net` / `maven.parchmentmc.org` / `github.com` 均能解析到 IP。
- 但所有出站连接被墙：
  - HTTPS：TLS 握手在 ClientHello 后即被切断（`SSL_ERROR_SYSCALL` / `EOF`，Python `urllib` 同样 `TLS/SSL connection has been closed`）。
  - HTTP/80：同样返回 `000`。
- 已尝试：curl + Python `urllib.request`（复刻上一个 agent 的 Python 下载法），均失败。

**含义**：上一个 agent「下载 parchment / mojmap / yarn 用于验证解析器」的操作，在当前环境**不可复刻**。本项目 4 个解析器（AsmDescriptorParser / MojmapParser / TinyParser / ParchmentParser）是**依据格式规格手工编写、未用真实数据验证**，应视为「未经真实数据验证」。

**若未来网络可用，复刻步骤（供参考）**：

```python
import urllib.request, zipfile, io

def get(url):
    return urllib.request.urlopen(url, timeout=30).read().decode()

# 1) Mojang client_mappings (ProGuard 文本)
manifest = get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
# 解析 JSON 取某 release 版本的 url -> 该 json 的 downloads.client_mappings.url
# mojmap_text = get(client_mappings_url)

# 2) Yarn tiny
yarn = get("https://meta.fabricmc.net/v2/versions/yarn/1.20.1")  # 取 stable 的 maven 字段
# jar_url = f"https://maven.fabricmc.net/net/fabricmc/yarn/{ver}/yarn-{ver}.jar"
# 下载 jar -> zipfile 解压 mappings/mappings.tiny

# 3) Parchment json
meta = get("https://maven.parchmentmc.org/org/parchmentmc/data/parchment/maven-metadata.xml")
# 取 <version> -> zip_url = f".../parchment/{ver}/parchment-{ver}-tiny.zip" -> 解压 parchment.json
```

用这些真实样本跑解析器单测，修正 4 个解析器。

---

## 1. 当前进展

- PR #3（分支 `arena/019fd6f6-mixin-helper`）已开，功能代码完整，**编译已通过**。
- 用户已应用我给的两处修复：
  - `MappingRepository.decideMappingType`：`List<Int> >=` 改为自定义 `tupleGe(a, b)` 按位比较。
  - `SearchScreen.kt`：补 `import ...data.local.MappingEntity`（否则 `mutableStateOf<MappingEntity?>` 及 `items`/`DetailDialog` 全报错）。
- 功能清单：4 解析器、多源版本列表、下载编排（Mojmap/Yarn/Parchment）、Room v1→v2→v3 迁移、FTS4 搜索重构、Dashboard/Search UI 重构、删除 Mixin 代码生成器。

## 2. 仍待办

- **单元测试（0 测试）**：覆盖 AsmDescriptorParser / MojmapParser / TinyParser / ParchmentParser / 版本号比较。
- **Forge / NeoForge 实际映射下载未实现**：版本列表有条目且 `mappingType=mojmap`，但 `versionJsonUrl` 为空，真正下载会走 Mojang client_mappings 而失败。需另接 MCP/SRG 源，或在 UI 明示不支持。
- **端到端真机验证未做**：Room 迁移 v1→v2→v3 与 Mojmap/Yarn/Parchment 真实下载/解析**从未在真实网络/真机跑过**（本沙箱无网络）。

## 3. 交接补充：图标 + 搜索框体验（下一个 agent 执行）

### 3.1 画一个图标（若环境支持）
- 用 `generate_image` 工具生成 **512×512 透明背景像素风**图标，主题贴合「Minecraft Mixin Helper」（如「命令方块 + 放大镜」或「映射节点图」）。
- 缩放至 Android 各密度并替换：
  - `app/src/main/res/mipmap-mdpi/ic_launcher.png` (48)
  - `mipmap-hdpi` (72) / `xhdpi` (96) / `xxhdpi` (144) / `xxxhdpi` (192)
  - 同步 `ic_launcher_round.png`；生成自适应图标 `ic_launcher_foreground.png` (108) + `ic_launcher_background.xml`。
- 若 `generate_image` 不可用：回退为 `res/drawable/ic_launcher_foreground.xml` 矢量路径，或保留默认图标。

### 3.2 改进搜索框体验（用户无需手填匹配规则）
- **现状**：内部 `toFtsMatchQuery()` 已把输入自动清洗为 FTS4 前缀语法（`"词"*`），但 UI 无引导，用户不知道能搜什么、怎么搜。
- **设计（下一个 agent 实现）**：
  1. **实时建议下拉**：搜索框下方加建议面板，调用轻量 `repository.suggest(query, type, version)`（复用 FTS，LIMIT 10）实时展示匹配的类/方法/字段名；点击即填入精确词。
  2. **输入永远被归一化**：用户永不见原始 FTS 语法；框内显示提示「输入类名 / 方法名 / 字段名实时搜索」与结果计数。
  3. （可选）最近搜索记录。
- **目标**：搜索框「开箱即用」，不需要用户了解 FTS 前缀 / `*` 等匹配规则。

## 4. 给下一个 agent 的提醒

- 不要依赖本会话沙箱；从 `origin` 拉取最新代码（PR #3 已可编译）。
- 4 个解析器**未用真实数据验证**，写单测时务必用真实 mojmap / yarn / parchment 样本；若网络仍被墙，先写单测骨架（内联小规模样例），待有网环境再换真实样本。
- 本环境网络被防火墙全面阻断（仅 DNS 通、TLS/HTTP 全断），不要浪费时间在联网下载上；先专注离线可做的：单测骨架、UI、CI、图标。

---

## Appendix — 两处编译错误（已修复，供核对）

1. `MappingRepository.decideMappingType`：`List<Int> >= List<Int>` 无 `compareTo` → 改 `tupleGe(a, b)` 按位比较。
2. `SearchScreen.kt`：缺 `import com.example.minecraftmixinhelper.data.local.MappingEntity`。