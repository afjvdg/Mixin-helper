# Minecraft Mixin Helper - APK 构建指南

## 当前状态
项目代码已全部完成（包含真实映射下载 + 模糊搜索 + Mixin 生成器）。

## 构建步骤（在你的本地机器执行）

### 1. 准备环境
```bash
# 安装 JDK 17+
# 安装 Android Studio 或命令行工具

# 下载 Android SDK（如果没有）
sdkmanager "platforms;android-34" "build-tools;34.0.0"
```

### 2. 克隆/复制项目
将整个 `minecraft-mixin-helper/` 文件夹复制到你的开发电脑。

### 3. 构建 APK

```bash
cd minecraft-mixin-helper

# 赋予执行权限
chmod +x gradlew

# 清理并构建 Debug APK
./gradlew clean assembleDebug

# 构建产物位置：
# app/build/outputs/apk/debug/app-debug.apk
```

### 4. 安装到手机

```bash
# 通过 adb 安装
adb install app/build/outputs/apk/debug/app-debug.apk

# 或直接用文件管理器安装（需允许未知来源）
```

## 已实现功能（构建后可用）

- ✅ 真实下载 Mojang / Fabric 映射
- ✅ 模糊搜索（FTS5 + LIKE）
- ✅ 离线映射存储
- ✅ Mixin 代码实时生成 + 复制
- ✅ ASM 描述符构建
- ✅ 版本管理

## 注意事项
- 首次构建会下载 Gradle + Android SDK（约 1.5GB）
- 构建时间：5~15 分钟（取决于网络）
- 生成的 APK 为 Debug 版本，可直接安装测试

---

构建完成后把 `app-debug.apk` 发给我即可完成最终交付。
