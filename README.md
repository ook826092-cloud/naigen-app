# NaiGen — Android Nai2API 文生图客户端

一个基于 Nai2API 文生图教程（7.12 版本）的 Android 原生 Kotlin 客户端，**后端完全用 Android 原生方式实现**（OkHttp + Coroutines + Room + DataStore），不依赖 Python / 任何外部服务器。

**v2.0.0 重大更新：**
- ✅ 29 个社区风格全部使用真实 NovelAI Diffusion 4.5 画师串，零占位符
- ✅ 移除 Markdown 链接复制功能（按用户反馈精简 UI）
- ✅ 新增手机品牌识别 + 保活引导页（覆盖 9 大厂商）
- ✅ Android 15 / 16 前台服务通知 + 实时进度条
- ✅ 性能优化（Coil 全局缓存 / Bitmap 降采样 / @Immutable / Room 索引）
- ✅ 生成任务从前台 Activity 迁移到 Service，App 切后台不中断

## 教程功能映射

| 教程章节 | 教程实现 | 本 App 实现 |
|---------|---------|------------|
| §5.1 Nai2API 概述 | `https://nai.sta1n.cn` Job 异步模式 | `NaiApiClient` + `NaiRepository.generate()` |
| §5.5.8 `_get_artist_string` 风格路由 | Python `_get_artist_string` 方法 | `StyleRegistry.resolveArtistString()` |
| §5.5.10 Job 异步流程 | `requests.post` + `time.sleep` 轮询 | `client.createJob` + `delay()` + `client.pollJob` |
| §5.5.11 文生图 `generate_text_to_image` | Python 同步方法 | `NaiRepository.generate()` suspend 函数 |
| §5.5.13 29 个社区风格 | `nai_styles.json` 文件 | `CommunityStyles.ALL` Kotlin 常量 |
| §5.7.3 7 个内置画风串 | `ARTIST_PRESETS` dict | `ArtistPresets.ALL` Kotlin 常量（含 7.12 完整画师串） |
| §5.7.4 `--variants N` 并发生成 | `ThreadPoolExecutor` | `generateVariants()` 用 `async { ... }.awaitAll()` |
| §5.5.4 自动风格检测 | `AUTO_STYLE_KEYWORDS` 表 | `AutoStyleKeywords.detect()` |
| §5.5.12 余额查询 | `GET /api/me` | `NaiRepository.checkBalance()` |
| §5.12 文件命名 `YYYYMMDD_NNN.png` | `next_dated_output_path` | `ImageSaver.savePrivate()` |
| §5.13 Markdown 图片引用 | `![](url)` 格式 | `MarkdownBuilder.image()` |
| §5.6 内置 7 预设 | 2.5d/fresh/doujin/galgame/comicDoujin/animeOld/realistic_loli | 同 |
| §5.8 9 个尺寸选项 | 竖图/横图/方图/2K/4K × 3 | `SizeOptions.ALL` |
| §5.10 全部 CLI 参数 | `--steps/--scale/--cfg/--sampler/--negative/--artist` | 生成页"高级参数"折叠面板 |

## 技术栈

| 层 | 选型 | 理由 |
|---|---|---|
| UI | Jetpack Compose + Material 3 | Google 现代声明式 UI，2024+ 官方推荐 |
| 视觉风格 | iOS 极简风（纯白/纯灰 + 细边框 + iOS 系统蓝） | 用户在 AskUserQuestion 中选定 |
| 异步 | Kotlin Coroutines + Flow | 替代 Python 的 `time.sleep` + `ThreadPoolExecutor` |
| 网络 | OkHttp 4.12 + kotlinx.serialization | 替代 Python `requests`，端点少不需要 Retrofit |
| 数据库 | Room 2.6 (HistoryDao + FavoritesDao) | 历史记录 + Prompt 收藏夹 |
| 偏好 | Preferences DataStore | 替代 Python `.env`（存 token、上次参数） |
| 图片加载 | Coil 2.7 | 加载 ByteArray / 保存到相册 |
| 后台 | 前台服务 (foregroundServiceType=dataSync) | App 后台时继续轮询 Nai2API Job |
| 桌面 | AppWidgetProvider + RemoteViews | "快速生图"小组件 |
| 最低 SDK | Android 8.0 (API 26) | 用户选定，覆盖 95%+ 设备 |
| 目标 SDK | Android 14 (API 34) | 最新稳定 |

## 工程结构

```
naigen-app/
├── settings.gradle.kts              # 单 module 工程配置
├── build.gradle.kts                 # 顶层插件声明
├── gradle.properties                # JVM 内存 + AndroidX
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts             # 依赖 + 编译配置
    ├── proguard-rules.pro           # 保留 serializer / Room / OkHttp
    └── src/main/
        ├── AndroidManifest.xml      # 权限 + Activity + Service + Widget + FileProvider
        ├── java/com/naigen/app/
        │   ├── NaiApplication.kt    # 入口 + 手动 DI（不引入 Hilt）
        │   ├── MainActivity.kt      # 单 Activity + Compose Navigation
        │   ├── data/
        │   │   ├── api/             # NaiApiClient + DTO
        │   │   ├── model/           # GenRequest / GenResult / StylePreset / SizeOption
        │   │   ├── repository/      # NaiRepository (Job 异步) + History/Favorites
        │   │   ├── db/              # AppDatabase + Dao + Entity
        │   │   ├── prefs/           # SettingsStore (DataStore)
        │   │   └── styles/          # ArtistPresets + CommunityStyles + AutoStyleKeywords + SizeOptions
        │   ├── service/
        │   │   └── GenerationService.kt   # 前台服务，后台轮询
        │   ├── ui/
        │   │   ├── theme/           # Color/Type/Shape/Theme (iOS 极简风)
        │   │   ├── components/      # GroupedList + ListRow + TagChip + PrimaryButton
        │   │   ├── navigation/      # AppNavGraph + 5 个 Tab
        │   │   └── screen/
        │   │       ├── generate/    # 生成页（核心交互）
        │   │       ├── history/     # 历史记录
        │   │       ├── favorites/   # Prompt 收藏夹
        │   │       ├── stylepicker/ # 风格选择
        │   │       └── settings/    # Token / 余额 / NSFW
        │   ├── widget/              # QuickGenWidgetReceiver
        │   └── util/                # ImageSaver / MarkdownBuilder / DateUtils / ShareUtils
        └── res/
            ├── values/              # colors.xml + strings.xml + themes.xml
            ├── values-night/        # 暗色主题
            ├── drawable/            # launcher 图标 (vector)
            ├── mipmap-anydpi-v26/   # 自适应图标
            ├── layout/              # widget_quick_gen.xml
            └── xml/                 # FileProvider paths + Widget info + Backup rules
```

## 部署步骤

### 1. 导入 Android Studio

1. 把整个 `naigen-app` 目录复制到本地（注意路径不要含中文/空格）
2. 打开 Android Studio → `File` → `Open` → 选择 `naigen-app` 根目录
3. 等待 Gradle sync 完成（首次会下载 ~500MB 依赖，国内可挂梯子或在 `settings.gradle.kts` 里把 `mavenCentral()` 改成阿里云镜像）

### 2. 填入 Nai2API Token

**两种方式：**

- **App 内填入（推荐）**：编译安装后打开 App → 底部 Tab "设置" → "Token" 输入框填入 `STA1N-xxxxx…`
- **代码内硬编码**：编辑 `SettingsStore.kt` 第 23 行 `token` 的默认值（不推荐，会进入 APK）

Token 仅保存在 App 私有目录的 DataStore 中，不会上传任何服务器。

### 3. 编译 Debug APK

```bash
cd naigen-app
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

或 Android Studio → `Build` → `Build Bundle(s)/APK(s)` → `Build APK(s)`。

### 4. 安装到设备

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

或直接把 APK 传到手机点击安装（需开启"未知来源"）。

## 使用指南

### 基础生图

1. 打开 App，在生成页正向提示词框输入英文 prompt（逗号分隔）
2. 选风格：默认 2.5d，可在"风格" Tab 详细挑选 36 个预设
3. 选尺寸：默认竖图，循环切换 9 个尺寸
4. 点底部"生成"按钮，等待 30~40 秒
5. 完成后展示图片，可：
   - 复制 Markdown 链接（`![](https://nai.sta1n.cn/api/images/xxx/content)`）
   - 保存到相册（`Pictures/NaiGen/`）
   - 系统分享

### 并发生成 N 张变体

- 在"风格/尺寸"组里点 +/− 调整变体张数（1-6）
- 点底部"并发出 N 张"
- 同时提交 N 个独立 Job，耗时与单张接近，便于挑最佳

### 自动风格检测

- 在"智能"组里打开"自动风格检测"
- 输入 prompt 后，根据关键词自动切换风格（例如 prompt 含"水彩" → community:4 水彩水墨风）

### 高级参数

- 点"自定义画师串"行展开
- 可覆盖：Steps / Scale / CFG / Sampler / 自定义画师串

### 余额查询

- 底部"⚡"按钮，或设置页 → "查询余额"

### 历史记录

- 底部 Tab "历史"
- 缩略图 + prompt + 风格 + 耗时 + 时间
- 单条可：复制 Markdown / 保存到相册 / 分享 / 删除

### Prompt 收藏夹

- 底部 Tab "收藏" → 右上角 + 新建
- 保存常用 prompt 模板，可标记"负面词"
- 单条可一键复制

## 风格路由 Bug 防护

教程 §5.5.17 提到「不管传什么 `--style` 出来都是 2.5d」的常见 bug，本 App 已通过以下方式防护：

1. **`StyleRegistry.resolveArtistString()`**（对应教程 `_get_artist_string`）实现完整三段路由：
   - ① 社区风格 `community:ID` → 取 `communityStyles.byId(id).artistString`
   - ② 内置预设 → 取 `artistPresets.get(key).artistString`
   - ③ 都不在 → 原样返回（视为自定义画师串）

2. **`NaiRepository.generate()`** 强制调用 `resolveArtistString(styleKey)` 翻译成画师串，再赋值给 API 的 `artist` 字段

3. **`customArtist` 字段**（对应 `--artist`）非空时覆盖 `styleKey`，进入路由第 ③ 兜底分支

## 占位数据说明

**v2.0.0 已全部替换为真实画师串。** 29 个社区风格（id=1~30）现在全部使用基于 NovelAI Diffusion 4.5 语法编写的真实画师串，每个风格搭配 2~4 位画师加权 + 风格关键词明示，可直接出图。

如需替换为你私有的 `nai_styles.json` 数据：编辑 `app/src/main/java/com/naigen/app/data/styles/CommunityStyles.kt`，把对应 `StylePreset(...)` 的 `artistString` 字段替换即可。

## 隐私与权限

| 权限 | 用途 | 何时请求 |
|---|---|---|
| INTERNET | 调用 Nai2API | 安装即生效 |
| FOREGROUND_SERVICE_DATA_SYNC | 后台轮询生成任务 | 启动 GenerationService 时 |
| POST_NOTIFICATIONS | 显示生成进度通知 | Android 13+ 首次启动时弹窗 |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | 申请加入电池白名单 | 保活页跳转电池优化时 |
| READ_MEDIA_IMAGES | 保存到相册时扫描已存在文件 | 保存到相册时（仅 Android 13+） |
| WRITE_EXTERNAL_STORAGE | 保存到相册（Android 9 及以下） | 保存到相册时（仅 Android 9-） |

**所有用户数据（token / 历史 / 收藏 / 生成图片）均存储在 App 私有目录，不上传任何服务器。**

## 后台保活

### 厂商识别

App 启动后通过 `Build.MANUFACTURER` + `Build.BRAND` + 系统属性反射识别当前设备厂商，覆盖：

| 厂商 | 识别关键字 | 保活跳转入口 |
|---|---|---|
| 小米 / Redmi | xiaomi / redmi / ro.miui.ui.version.name | com.miui.securitycenter 自启动 + com.miui.powerkeeper 省电策略 |
| 华为 | huawei / ro.build.version.emui | com.huawei.systemmanager 应用启动管理 + 电池优化 |
| 荣耀 | honor | com.hihonor.systemmanager（fallback 到华为包名） |
| OPPO | oppo / ro.build.version.opporom | com.coloros.safecenter 自启动 + com.coloros.oppoguardelf 电池 |
| 一加 | oneplus | 与 OPPO 同源（已合并 ColorOS） |
| vivo / iQOO | vivo / ro.vivo.os.build.display.id | com.vivo.permissionmanager 后台弹出 + 自启动 |
| 三星 | samsung | com.samsung.android.lool 后台使用限制 |
| 魅族 | meizu | com.meizu.safe 自启动 + 后台运行 |
| Realme | realme | 与 OPPO 同源 |
| 原生 Android | 其他 | 仅电池优化（无需厂商保活） |

### 引导流程

保活页（底部 Tab "保活"）会：
1. 显示识别到的厂商名称 + 专属提示文案
2. 按顺序列出该厂商需要开启的保活项（自启动 / 电池 / 后台弹窗 / 通知）
3. 每项右侧有跳转按钮，点击直达该厂商的设置子页（多个候选 Intent 自动 fallback）
4. 显示当前状态（电池白名单是否已加入、通知权限是否已开），从设置返回时自动刷新
5. 兜底入口：应用详情页（所有 ROM 都支持）

### Android 15 / 16 适配

| 系统 | 强制要求 | 本 App 实现 |
|---|---|---|
| Android 14+ (API 34) | 前台服务必须声明 foregroundServiceType | Manifest 已声明 `dataSync` |
| Android 14+ | startForeground 必须用带 type 的重载 | `startForegroundCompat()` 按 SDK 分流 |
| Android 15 (API 35) | targetSdk 必须 ≥ 34 | 已设为 35 |
| Android 16 (API 36) | 前台服务启动 6 秒内必须显示进度通知 | `setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)` + 启动时立即 startForeground |
| Android 16 | 通知渠道重要性必须正确 | 进度渠道 LOW，结果渠道 DEFAULT，已分开声明 |

### 实时进度通知

- 轮询每 2 秒一次，每次同步更新通知 text + progress
- 通知模板：`正在生成图像` + `生成中… 12s · job abc12345`
- 进度条：max=60，current=已耗时（不足 0 显示 indeterminate）
- 完成时切换到结果渠道发普通通知（带点击跳转 MainActivity）
- 取消时直接 stopService 并取消通知

## 性能优化

| 维度 | 优化项 | 位置 |
|---|---|---|
| 网络层 | OkHttp 共享单例，连接池复用 + HTTP/2 | `NaiApiClient.client` |
| 网络层 | 重试 + 超时分层（connect 15s / read 60s / call 不限） | 同上 |
| 图片加载 | 全局 Coil ImageLoader，内存缓存 25% maxMemory，磁盘 50MB | `NaiApplication.newImageLoader()` |
| 图片加载 | 缩略图用 inSampleSize 降采样解码，避免大图 OOM | `ImageSaver.makeThumbnail()` |
| 图片加载 | 私有目录图片读取也带降采样 | `ImageSaver.readPrivateBitmap()` |
| Compose | StylePreset / GenRequest / GenResult 等加 @Immutable，跳过重组 | `data/model/Models.kt` |
| Compose | LazyColumn 全部用 key + contentType | `HistoryScreen` / `StylePickerScreen` |
| 数据库 | HistoryEntity 加 createdAt / styleKey 索引 | `HistoryEntity.kt` |
| 数据库 | FavoriteEntity 加 tag / isNegative 索引 | `FavoriteEntity.kt` |
| 协程 | 轮询用 `ensureActive()` 检查取消，避免无效等待 | `NaiRepository.generate()` |
| 协程 | 并发变体用 `async { }.awaitAll()`，flowOn(Dispatchers.IO) | `NaiRepository.generateVariants()` |
| 状态管理 | ViewModel 用 StateFlow + combine，UI 用 collectAsStateWithLifecycle | `GenerateViewModel` |
| Service | 单 coroutine scope，单 job，cancel 时立即停 | `GenerationService` |
| 通知 | `setOnlyAlertOnce(true)` + `setSilent(true)`，避免每次更新都震动 | `buildProgressNotification()` |

## 故障排查

### 编译失败：Gradle sync 超时

国内网络问题，在 `settings.gradle.kts` 把 `mavenCentral()` 改成阿里云镜像：

```kotlin
maven { url = uri("https://maven.aliyun.com/repository/public") }
maven { url = uri("https://maven.aliyun.com/repository/google") }
```

### 运行时：生成失败 "未配置 API Token"

打开 App 设置页填入 `STA1N-...` 格式的 token，token 不会显示在截图中（默认密文化）。

### 运行时：生成失败 "HTTP 401"

token 错误或已过期，到购买入口 https://www.qianxun1688.com/links/D07F549B 获取新 token。

### 运行时：轮询超时 180 秒

Nai2API 服务器排队过长，可在 `NaiRepository.kt` 第 261 行调整 `MAX_POLL_TIME_MS` 到 300_000L。

### 编译失败：KSP 报错 Room schema

清理项目：`./gradlew clean` → `./gradlew assembleDebug`。

## 版本

- v1.0.0 (2026-07-15) — 初版，对齐教程 7.12 版本

## 许可

本工程代码可自由使用。Nai2API 服务和购买入口属于第三方，使用前请遵守其服务条款。
