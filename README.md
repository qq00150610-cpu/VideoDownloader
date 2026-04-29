# 视频下载器 - Android App

一个功能完整的 Android 视频下载器，支持 Twitter/X 视频解析下载、在线/本地视频播放、锁屏控制等。

## ✨ 功能特性

### 📥 视频下载
- **Twitter/X 视频解析** — 粘贴推文链接即可解析下载
- **多画质选择** — 支持 SD/HD/1080p/4K 等多种画质
- **后台下载** — 前台服务下载，支持进度通知
- **下载管理** — 查看下载历史，下载完成直接播放

### 🎬 视频播放器
- **ExoPlayer 内核** — 硬件加速，流畅播放
- **在线播放** — 输入任意视频 URL 直接播放 (mp4/m3u8/dash 等)
- **本地播放** — 浏览和播放设备上的所有视频
- **播放列表** — 连续播放多个视频，支持上一个/下一个

### 🔒 锁屏 & 后台
- **锁屏控制** — MediaSession 集成，锁屏界面显示播放控制
- **通知栏控制** — 通知栏显示播放/暂停/上一个/下一个
- **画中画 (PiP)** — 小窗播放，边看边做其他事
- **后台播放** — 支持纯音频后台播放

### ⏩ 播放控制
- **快进/快退** — ±10 秒快速跳转
- **进度条拖拽** — 精确定位到任意时间点
- **倍速播放** — 0.5x ~ 2x 多档速度
- **手势控制** — 点击屏幕显示/隐藏控制栏

## 📋 系统要求

- Android 8.0 (API 26) 及以上
- 目标 SDK: 34 (Android 14)
- 网络连接（在线播放和下载需要）

## 🛠 构建方法

### 前置条件
1. 安装 [Android Studio](https://developer.android.com/studio) (2023.2+)
2. 确保 JDK 17 已安装
3. Android SDK 34

### 构建步骤

```bash
# 1. 解压项目
unzip VideoDownloader.zip
cd VideoDownloader

# 2. 用 Android Studio 打开项目
#    File -> Open -> 选择 VideoDownloader 目录

# 3. 等待 Gradle 同步完成

# 4. 连接设备或启动模拟器

# 5. 点击 Run ▶️ 或命令行构建：
./gradlew assembleDebug

# APK 输出位置：
# app/build/outputs/apk/debug/app-debug.apk
```

### Release 构建
```bash
# 生成签名 APK
./gradlew assembleRelease

# 需要配置签名密钥
# app/build.gradle.kts 中添加 signingConfigs
```

## 📁 项目结构

```
VideoDownloader/
├── app/src/main/
│   ├── AndroidManifest.xml          # 应用配置
│   ├── java/com/videodownloader/
│   │   ├── MainActivity.kt          # 主界面 (下载/播放器/视频列表)
│   │   ├── VideoDownloaderApp.kt    # Application 类
│   │   ├── data/
│   │   │   └── Models.kt            # 数据模型
│   │   ├── network/
│   │   │   └── TwitterParser.kt     # Twitter/X API 解析
│   │   ├── download/
│   │   │   └── DownloadManager.kt   # 下载管理
│   │   ├── service/
│   │   │   └── DownloadService.kt   # 前台下载服务
│   │   ├── player/
│   │   │   ├── PlayerActivity.kt    # 全屏播放器 + PiP
│   │   │   └── PlaybackService.kt   # 后台播放服务
│   │   └── util/
│   │       └── VideoScanner.kt      # 本地视频扫描
│   └── res/                          # 资源文件
├── build.gradle.kts                  # 根构建脚本
├── app/build.gradle.kts              # 应用构建脚本
└── README.md                         # 本文件
```

## 🔧 技术栈

| 组件 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 播放器 | ExoPlayer (Media3) |
| 网络 | OkHttp 4 |
| 图片加载 | Coil |
| 架构 | MVVM + StateFlow |

## 📝 使用说明

1. **下载视频**：在 Twitter/X 复制推文链接 → 打开 App → 粘贴到输入框 → 点击解析 → 选择画质 → 下载
2. **在线播放**：切换到「播放器」标签 → 输入视频 URL → 点击播放
3. **本地视频**：切换到「我的视频」标签 → 浏览已下载的视频 → 点击播放
4. **锁屏控制**：播放时锁屏，锁屏界面会显示播放控制按钮
5. **画中画**：播放时点击 PiP 按钮进入小窗模式

## ⚠️ 注意事项

- 仅下载你有权使用的视频
- 尊重版权和知识产权
- 下载的视频仅供个人使用
- 需要网络权限用于下载和在线播放
