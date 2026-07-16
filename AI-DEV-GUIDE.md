# NaiGen — AI 辅助开发指南

> **本文档面向 AI 助手（Claude / GPT / Gemini 等）或新接手的开发者**。
> 目标：让 AI 在环境被重置、或换 AI 接手时，能快速理解项目并继续开发。
>
> 阅读本文档后，AI 应能独立完成：修 bug、加功能、发版、改 CI 等任务。

---

## 1. 项目概述

**NaiGen** 是一个 Android 原生 Kotlin 应用，基于 Nai2API 文生图教程思路实现（实际调用 nai.sta1n.cn 服务）。

- **仓库**：https://github.com/ook826092-cloud/naigen-app
- **可见性**：公开
- **许可证**：MIT
- **平台**：Android 8.0+ (API 26+)
- **目标 SDK**：Android 15 (API 35)
- **后端**：完全用 Android 原生方式实现（OkHttp + Coroutines + Room + DataStore），**不依赖 Python / 任何外部服务器**

### 核心功能
- 调用 nai.sta1n.cn 的 Job 异步文生图
- 7 个内置画风预设 + 29 个社区风格（NovelAI Diffusion 4.5 真实画师串）
- 自定义风格（Room 持久化）
- 9 大厂商识别 + 后台保活引导（小米/红米/vivo/iQOO 分开）
- Shizuku 集成（引导模式）
- Android 15/16 前台服务通知 + 实时进度条
- 多语言切换（7 种语言）
- NAI 4.5 全参数支持

---

## 2. 开发环境准备

### 2.1 必需工具
- **JDK 17**（Temurin 发行版）
- **Android Studio**（最新稳定版）
- **Android SDK** compileSdk 35
- **Git**
- **GitHub 账号 + PAT**（有 repo / actions / secrets 写权限）

### 2.2 克隆仓库
```bash
git clone https://github.com/ook826092-cloud/naigen-app.git
cd naigen-app
```

### 2.3 本地构建
```bash
# 编译 release APK（需要本地有 signing/keystore.properties，或走 debug 签名 fallback）
./gradlew assembleRelease

# 编译 debug APK（不需要签名配置）
./gradlew assembleDebug

# 清理
./gradlew clean
```

### 2.4 国内网络加速
如果 Gradle sync 超时，在 `settings.gradle.kts` 加阿里云镜像：
```kotlin
maven { url = uri("https://maven.aliyun.com/repository/public") }
maven { url = uri("https://maven.aliyun.com/repository/google") }
```

---

## 3. 项目结构

```
naigen-app/
├── .github/workflows/build.yml      # CI 工作流（手动触发）
├── app/
│   ├── build.gradle.kts             # 模块构建配置（版本号 + 签名 + 依赖）
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml      # 权限 + Activity + Service + Provider
│       ├── java/com/naigen/app/
│       │   ├── NaiApplication.kt    # Application 入口 + 手动 DI + Coil
│       │   ├── MainActivity.kt      # AppCompatActivity + Compose
│       │   ├── data/
│       │   │   ├── api/             # NaiApiClient + DTO
│       │   │   ├── model/           # GenRequest / GenResult / StylePreset 等
│       │   │   ├── repository/      # NaiRepository / History / Favorites / CustomStyle
│       │   │   ├── db/              # AppDatabase + 4 个 Dao + 3 个 Entity
│       │   │   ├── prefs/           # SettingsStore (DataStore)
│       │   │   └── styles/          # ArtistPresets + CommunityStyles + AutoStyleKeywords + SizeOptions
│       │   ├── service/             # GenerationService + GenerationBus
│       │   ├── ui/
│       │   │   ├── theme/           # Color / Type / Shape / Theme
│       │   │   ├── components/      # GroupedList / ListRow / TagChip / PrimaryButton
│       │   │   ├── navigation/      # AppNavGraph + Dest + SubDest
│       │   │   └── screen/
│       │   │       ├── generate/    # 生成页（核心交互）
│       │   │       ├── album/       # 相册页（历史图片网格）
│       │   │       └── settings/
│       │   │           ├── SettingsScreen.kt     # 设置主页（栏目列表）
│       │   │           ├── SettingsViewModel.kt  # 设置页 ViewModel
│       │   │           ├── api/                  # API 服务商配置
│       │   │           ├── styles/               # 风格管理 + 自定义风格 CRUD
│       │   │           ├── keepalive/            # 后台保活 + Shizuku
│       │   │           ├── language/             # 多语言切换
│       │   │           ├── theme/                # 深色/浅色模式切换
│       │   │           ├── logs/                 # 应用日志 + 网络日志查看
│       │   │           ├── about/                # 关于页
│       │   │           └── docs/                 # 说明文档跳转
│       │   ├── widget/              # QuickGenWidgetReceiver
│       │   └── util/
│       │       ├── keepalive/       # ManufacturerHelper + ShizukuHelper
│       │       ├── ImageSaver.kt    # 降采样解码 + MediaStore 相册
│       │       ├── DateUtils.kt
│       │       └── ShareUtils.kt
│       └── res/
│           ├── values/              # colors / strings / themes
│           ├── values-night/        # 暗色主题
│           ├── drawable/            # 12 个厂商 logo (vector) + launcher 图标
│           └── xml/                 # FileProvider / Widget / Backup
├── docs/
│   └── Nai2API-Tutorial.md          # 原始教程
├── gradle/wrapper/                  # Gradle wrapper
├── scripts/
│   └── bump-version.sh              # 版本号自增脚本
├── version.properties               # 版本号单一来源
├── build.gradle.kts                 # 顶层构建
├── settings.gradle.kts
├── gradlew / gradlew.bat
├── README.md                        # 中文说明
├── README.en.md                     # English
├── README.ja.md / .ko.md / .fr.md / .de.md / .es.md  # 其他 5 种语言
├── PRIVACY.md                       # 隐私政策
├── LICENSE                          # MIT
└── .gitignore
```

---

## 4. 版本号管理（重要！）

### 4.1 版本号格式

版本号存在 `version.properties`，有 4 个字段：

```properties
BUILD_NUMBER=20019   # 累计构建次数，每次 bump +1，永不动
MAJOR=2              # SemVer 主版本
MINOR=2              # SemVer 次版本
PATCH=0              # SemVer 补丁版本
```

### 4.2 安卓系统识别的版本

```kotlin
// app/build.gradle.kts
versionName = "2.2.0"                    // 纯 SemVer，安卓系统设置看到的
versionCode = 2 * 10000 + 2 * 100 + 0    // = 20200，用于升级判定
```

### 4.3 关于页显示的版本

通过 `BuildConfig` 暴露：
```kotlin
buildConfigField("int", "BUILD_NUMBER", "20019")
buildConfigField("String", "SEMVER", "2.2.0")
```

关于页用 `BuildConfig.SEMVER` + `BuildConfig.BUILD_NUMBER` 显示：
```
v2.2.0
构建 #20019
```

### 4.4 版本号自增逻辑

`scripts/bump-version.sh` 每次 CI 触发时执行：
- `BUILD_NUMBER` +1（永不动）
- `PATCH` +1，逢 9 进位到 `MINOR`
- `MINOR` 逢 9 进位到 `MAJOR`

示例：
```
20019/2.2.0 → 20020/2.2.1
20028/2.2.9 → 20029/2.3.0
20099/2.9.9 → 20100/3.0.0
```

### 4.5 手动修改版本号

直接编辑 `version.properties`，push 后下次 CI 会基于此值 bump。

⚠️ **不要把 BUILD_NUMBER 设太小**，否则 versionCode 可能小于历史值导致安卓拒绝覆盖安装。当前安全下限是 `20019`。

---

## 5. CI/CD 工作流

### 5.1 工作流文件

`.github/workflows/build.yml`

### 5.2 触发方式

**仅手动触发**（`workflow_dispatch`），不监听 push。

发版步骤：
1. 改代码 → push 到 main
2. 打开 https://github.com/ook826092-cloud/naigen-app/actions/workflows/build.yml
3. 点 "Run workflow" → 选 main 分支 → 点绿色按钮
4. 工作流自动：版本号 bump → 编译 release APK → 创建 GitHub Release

### 5.3 三个 Job

| Job | 作用 |
|---|---|
| **Bump Version** | 调 `bump-version.sh` 自增版本号 + commit 回仓库 |
| **Build APK** | 从 Secrets 解码 keystore → 编译 release APK → 重命名 + SHA256 |
| **Publish Release** | 创建 GitHub Release，tag = `v{SEMVER}`，上传 APK |

### 5.4 签名管理

**签名密钥不入库**，存储在 GitHub Actions Secrets：

| Secret 名 | 内容 |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | base64 编码的 PKCS12 keystore |
| `SIGNING_STORE_PASSWORD` | keystore 密码 |
| `SIGNING_KEY_ALIAS` | key alias |
| `SIGNING_KEY_PASSWORD` | key 密码 |

工作流解码 keystore 到 `/home/runner/.signing/naigen-release.jks`，通过环境变量传给 Gradle。

`build.gradle.kts` 的签名 fallback 顺序：
1. 环境变量（CI 注入）
2. 本地 `signing/keystore.properties`（开发自用，.gitignore 排除）
3. debug 签名

### 5.5 本地开发签名

如果需要在本地编译 release APK：
1. 在仓库根目录创建 `signing/` 目录
2. 放入 `naigen-release.jks`（从 GitHub Secrets base64 解码得到）
3. 创建 `keystore.properties`：
```properties
storeFile=../signing/naigen-release.jks
storePassword=你的密码
keyAlias=naigen
keyPassword=你的密码
```

`signing/` 目录已被 `.gitignore` 排除，不会入库。

---

## 6. 关键代码说明

### 6.1 Nai2API 调用链

```
GenerateScreen (UI)
  → GenerateViewModel.generate()
    → GenerationService.start()  (前台服务)
      → NaiRepository.generate()  (suspend)
        → NaiApiClient.createJob()  (POST /api/jobs)
        → NaiApiClient.pollJob()    (GET /api/jobs/:id, 每 2 秒)
        → NaiApiClient.downloadImage()  (GET imageUrl)
      → GenerationBus.publishResults()  (通知 ViewModel)
      → ImageSaver.savePrivate()  (落盘)
      → HistoryRepository.insert()  (写 Room)
```

### 6.2 风格路由（避免"全是 2.5d"bug）

`StyleRegistry.resolveArtistString(styleKey)` 三段路由：
1. 社区风格 `community:ID` → 取 `CommunityStyles.byId(id).artistString`
2. 内置预设 → 取 `ArtistPresets.get(key).artistString`
3. 都不在 → 原样返回（视为自定义画师串）

`customArtist` 字段非空时覆盖 `styleKey`，命中第 ③ 兜底分支。

### 6.3 厂商识别 + 保活跳转

`ManufacturerHelper` 用 `Build.BRAND` 区分：
- 小米 / 红米分开（BRAND = "xiaomi" vs "redmi"）
- vivo / iQOO 分开（BRAND 都是 "vivo"，但 MODEL 含 "iqoo"）

每个厂商配 12 个真实 logo drawable（`ic_manufacturer_*.xml`），从 SimpleIcons (CC0) 转换。

`launch()` 方法依次尝试多个候选 Intent，第一个失败的自动 fallback。

### 6.4 多语言切换

`LanguageScreen` 用 `AppCompatDelegate.setApplicationLocales()`：
- Android 13+ 走系统 Locale API
- Android 12 及以下走 AppCompat 模拟

`MainActivity` 必须继承 `AppCompatActivity`（不能用 ComponentActivity），且主题必须继承 `Theme.AppCompat.*`。

### 6.5 Shizuku 集成

`ShizukuHelper` 检测运行/授权状态，引导用户：
- 未安装 → 跳官网
- 已安装未启动 → 跳 Shizuku App
- 已启动未授权 → 弹权限对话框
- 已授权 → 显示 ✓

⚠️ Shizuku 的 `newProcess` 是 private API，不能直接调用。当前只做状态引导，不执行 shell 命令。

### 6.6 Room 数据库

```kotlin
@Database(
    entities = [HistoryEntity::class, FavoriteEntity::class, CustomStyleEntity::class],
    version = 3,
    exportSchema = false
)
```

- `HistoryEntity`：生成历史（含缩略图 ByteArray）
- `FavoriteEntity`：Prompt 收藏夹
- `CustomStyleEntity`：用户自定义风格

用 `fallbackToDestructiveMigration()`，升级时丢数据。如需保留数据，需写 Migration。

---

## 7. 常见开发任务

### 7.1 修 Bug

1. 定位问题文件
2. 修改代码
3. 本地验证（`./gradlew assembleDebug`）
4. `git commit -m "fix(xxx): 描述"`
5. `git push origin main`
6. 到 GitHub Actions 手动触发工作流
7. 等编译完成，下载新 APK 验证

### 7.2 加新功能

1. 在 `data/model/` 加数据类（加 `@Immutable` 注解）
2. 在 `data/repository/` 或 `data/db/` 加数据层
3. 在 `ui/screen/` 加 Compose 屏幕
4. 在 `ui/navigation/Dest.kt` 加路由
5. 在 `ui/navigation/AppNavGraph.kt` 注册 composable
6. 编译验证 → push → 触发 CI

### 7.3 加新厂商支持

1. 在 `ManufacturerHelper.Manufacturer` 枚举加新条目
2. 指定 `iconRes`（需要在 `res/drawable/` 加对应 logo）
3. 在 `intentsFor()` 加该厂商的 Intent 候选
4. 在 `detect()` 加识别逻辑
5. 在 `KeepAliveScreen` 的厂商列表里加特殊处理（如果有）

### 7.4 加新风格预设

**内置风格**：编辑 `data/styles/ArtistPresets.kt`，在 `ALL` 列表加 `StylePreset(...)`。

**社区风格**：编辑 `data/styles/CommunityStyles.kt`，在 `ALL` 列表加。

**自定义风格**：用户在 App 内「设置 → 风格管理 → +」添加，存 Room。

### 7.5 改 CI 工作流

编辑 `.github/workflows/build.yml`。注意：
- `paths-ignore: version.properties` 避免 bump commit 触发死循环
- artifact name 不能含 `/`（GitHub 限制）
- `env.HOME` 在 Actions 上下文不可用，直接写 `/home/runner/`

### 7.6 发版（手动）

1. 确认代码已 push 到 main
2. 打开 Actions 页面 → "Build & Release" → "Run workflow"
3. 选 main 分支，`skip_bump` 选 `false`
4. 等待 3 个 Job 全部 success（约 5-8 分钟）
5. 到 Releases 页面查看新版本

---

## 8. 历史踩坑记录（重要！）

### 8.1 Kotlin DSL 不允许 android {} 块内直接写 java.util.Properties

**错误**：`Unresolved reference: util`

**修复**：把版本解析逻辑提到 `plugins {}` 之后、`android {}` 之前，用 `data class + run {}` 包裹。

### 8.2 combine() 的 vararg lambda 不能用 Array 访问

**错误写法**（会 ClassCastException 直接崩）：
```kotlin
combine(f1, f2, f3, f4) { values ->
    values[0] as String  // ← 崩
}
```

**正确写法**：
```kotlin
combine(f1, f2, f3, f4) { a, b, c, d ->
    // 直接用具名参数
}
```

### 8.3 AppCompatActivity 必须用 AppCompat 主题

**错误**：Manifest 用 `android:Theme.Material.Light.NoActionBar`，MainActivity 继承 AppCompatActivity → 启动直接崩。

**修复**：主题改成继承 `Theme.AppCompat.Light.NoActionBar`。

### 8.4 Room KSP "references a type that is not present"

**原因**：`AppDatabase.kt` 的 `entities = [...]` 引用了 Entity 类但没 import。

**修复**：加 `import com.naigen.app.data.db.entities.CustomStyleEntity`。

### 8.5 GitHub Actions env.HOME 不可用

**错误**：`${{ format('{0}/.signing/...', env.HOME) }}` → 替换为空字符串 → 路径变成 `/.signing/...`

**修复**：直接写死 `/home/runner/.signing/naigen-release.jks`。

### 8.6 artifact name 不能含 `/`

**错误**：`The artifact name is not valid: apk-3/1.0.3. Contains Forward slash /`

**修复**：artifact name 用纯数字 `apk-{BUILD_NUMBER}`。

### 8.7 versionCode 降级导致无法覆盖安装

**原因**：versionCode 从 `major*10000+minor*100+patch` 改成 `BUILD_NUMBER`，如果 BUILD_NUMBER 比历史 versionCode 小，安卓识别为降级。

**修复**：把 BUILD_NUMBER 起点设为大于历史最高值（当前 20019 > 历史 20013）。

### 8.8 Shizuku.newProcess 是 private API

**错误**：`Cannot access 'newProcess': it is private in 'rikka/shizuku/Shizuku'`

**修复**：不直接调用 newProcess，改成状态引导模式（检测 + 跳转）。

### 8.9 ListRow 没有 icon 参数

`GroupedList.kt` 的 `ListRow` 只接收 `label / value / trailing`，没有 `icon`。如果要在行里显示 icon，要么自己写 Row，要么把 icon 放到 `trailing` 里。

### 8.10 Compose 的 Row 和自定义 ListRow 命名冲突

如果自定义组件叫 `Row`，会和 `androidx.compose.foundation.layout.Row` 冲突。解决：
- 自定义组件改名为 `ListRow`
- 或用全限定名 `androidx.compose.foundation.layout.Row`

---

## 9. 依赖清单

```kotlin
// Core
androidx.core:core-ktx:1.13.1
androidx.lifecycle:lifecycle-runtime-ktx:2.8.4
androidx.lifecycle:lifecycle-runtime-compose:2.8.4
androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4
androidx.activity:activity-compose:1.9.1
androidx.appcompat:appcompat:1.7.0  // 必需，用于 AppCompatDelegate

// Compose (BOM 2024.08.00)
androidx.compose:ui / material3 / foundation / material-icons-extended
androidx.navigation:navigation-compose:2.7.7

// 网络
com.squareup.okhttp3:okhttp:4.12.0
com.squareup.okhttp3:logging-interceptor:4.12.0
org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1

// 协程
org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1

// 图片
io.coil-kt:coil-compose:2.7.0

// 数据库
androidx.room:room-runtime:2.6.1 / room-ktx:2.6.1 (KSP)

// 偏好
androidx.datastore:datastore-preferences:1.1.1

// 后台
androidx.work:work-runtime-ktx:2.9.1

// Widget
androidx.glance:glance-appwidget:1.1.0 / glance-material3:1.1.0

// Shizuku
dev.rikka.shizuku:api:13.1.5 / provider:13.1.5
```

---

## 10. 权限清单

| 权限 | 用途 | 何时请求 |
|---|---|---|
| INTERNET | 调用 Nai2API | 安装即生效 |
| ACCESS_NETWORK_STATE | 检测网络状态 | 安装即生效 |
| FOREGROUND_SERVICE | 后台轮询 | 启动 GenerationService 时 |
| FOREGROUND_SERVICE_DATA_SYNC | 前台服务类型 | 启动 GenerationService 时 |
| POST_NOTIFICATIONS | 进度通知 | Android 13+ 首次启动弹窗 |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | 电池白名单 | 保活页跳转时 |
| READ_MEDIA_IMAGES | 相册保存 | Android 13+ |
| WRITE_EXTERNAL_STORAGE | 相册保存 | Android 9- |

---

## 11. GitHub Secrets 配置

在 https://github.com/ook826092-cloud/naigen-app/settings/secrets/actions 配置：

| Secret 名 | 值 |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | base64 编码的 PKCS12 keystore |
| `SIGNING_STORE_PASSWORD` | keystore 密码 |
| `SIGNING_KEY_ALIAS` | key alias (`naigen`) |
| `SIGNING_KEY_PASSWORD` | key 密码 |

**生成新 keystore**：
```bash
keytool -genkeypair \
  -keystore naigen-release.jks \
  -alias naigen \
  -keyalg RSA -keysize 2048 -validity 9125 \
  -storetype pkcs12 \
  -dname "CN=NaiGen, OU=Mobile App, O=NaiGen Project, L=Beijing, ST=Beijing, C=CN"

# base64 编码后存到 GitHub Secrets
base64 -w 0 naigen-release.jks
```

⚠️ 换 keystore 后用户必须卸载旧版才能装新版（签名不同）。

---

## 12. 给 AI 的注意事项

### 12.1 提交规范
```
fix(xxx): 修复描述
feat(xxx): 新功能描述
docs: 文档变更
ci: CI 变更
chore: 杂项
```

### 12.2 不要做的事
- ❌ 不要把 `signing/` 目录入库（含密钥）
- ❌ 不要在 `push` 触发工作流（会死循环，已改成手动）
- ❌ 不要用 `env.HOME` 在 workflow 表达式里
- ❌ 不要在 artifact name 里用 `/`
- ❌ 不要把 BUILD_NUMBER 写进 versionName（用户要求只在关于页显示）
- ❌ 不要用 `combine() { values -> values[0] as String }` 写法（会崩）
- ❌ 不要让 MainActivity 继承 ComponentActivity（多语言切换需要 AppCompatActivity）

### 12.3 必须做的事
- ✅ 改代码后先 `git pull --rebase origin main` 再 push
- ✅ push 后手动触发 Actions 工作流
- ✅ 监控工作流状态，失败就看日志修
- ✅ version.properties 改动后确认 BUILD_NUMBER > 20019（避免降级）
- ✅ 新增 Compose 屏幕时加 `@Immutable` 注解优化性能
- ✅ 新增 Room Entity 时在 `AppDatabase` 的 `entities = [...]` 里注册 + 加 import
- ✅ 新增 Drawable 资源时确认 `R.drawable.xxx` 能被 import

### 12.4 排查编译错误

1. 看 GitHub Actions 日志：`grep -E "^e: file|FAILED|Caused by" /tmp/build.log`
2. 常见错误：
   - `Unresolved reference` → 缺 import
   - `references a type that is not present` → Room Entity 没 import
   - `Cannot access ... it is private` → 调了 private API
   - `The artifact name is not valid` → artifact name 含特殊字符

### 12.5 联系方式

- GitHub Issues: https://github.com/ook826092-cloud/naigen-app/issues
- 仓库所有者: ook826092-cloud

---

## 13. 版本历史

| 版本 | 日期 | 主要变更 |
|---|---|---|
| v2.2.0 | 2026-07-15 | 修复启动闪退 + 版本号改造 + 关于页动态显示 |
| v2.1.x | 2026-07-15 | 多语言 + Shizuku + 隐私政策 + 教程上传 |
| v2.0.x | 2026-07-15 | 导航重构 + 自定义风格 + 厂商 logo + CI/CD |
| v1.0.0 | 2026-07-15 | 初版 |

完整 changelog: https://github.com/ook826092-cloud/naigen-app/releases

---

*本文档最后更新：2026-07-15 (v2.3.x) · 由 AI 辅助生成 · MIT License*

---

## 14. v2.3.x 重大变更（必读）

### 14.1 日志系统 v5

> 实现：`ui/screen/settings/logs/LogsScreen.kt`

**两 Tab 结构**：
- **应用日志**：文件列表模式（不是实时滚动条目）
  - 顶部「当前日志」条目：显示实时内存缓冲内容
  - 每天一个文件：`app_YYYYMMDD.log`（完整日志，不删除）
  - 自动剥离：`error_YYYYMMDD.log`（仅错误）、`warn_YYYYMMDD.log`（仅警告）
  - **重要**：完整日志和剥离文件共存，不要删除完整日志
  - 点击 → 详情页（完整页面，不是弹窗），有分享按钮
  - 长按 → 分享 / 导出（到 Downloads） / 删除

- **请求日志**：每条网络请求单独一个 TXT 文件
  - 文件名：`net_YYYYMMDD_HHmmss_NNN.txt`
  - 存在 `logs/net/` 目录
  - 内容：时间 / 方法 / URL / 耗时 / 请求头 / 请求体 / 响应头 / 响应体
  - 点击 → 详情页
  - 长按 → 分享 / 导出 / 删除

**AppLog 关键方法**：
```kotlin
AppLog.init(context)           // 初始化
AppLog.i(tag, message)         // 应用日志 INFO
AppLog.e(tag, message, throwable)  // 应用日志 ERROR（自动写 error 文件）
AppLog.network(method, url, requestHeaders, requestBody, responseCode, responseHeaders, responseBody, durationMs)  // 网络日志
AppLog.getAppFiles()           // 获取应用日志文件列表
AppLog.getNetworkFiles()       // 获取网络日志文件列表
AppLog.getFileContent(name, isNetwork)  // 读取文件内容
AppLog.formatAppEntry(entry)   // 格式化单条应用日志
```

**⚠️ 注意事项**：
- `writeAppFile()` 同时写 `app_` + `error_`/`warn_` 文件，**不是**从 app 文件中删除后写到 error 文件
- 网络日志每条请求一个文件，不要合并成一个文件
- LogsScreen 用 `LogDetailPage`（完整页面）显示内容，不用 `AlertDialog`

### 14.2 深色/浅色模式

> 实现：`ui/screen/settings/theme/ThemeScreen.kt`

- 设置 → 关于 → 深色模式
- 三选一：跟随系统 / 浅色 / 深色
- DataStore 持久化 `theme_mode` 字段
- `MainActivity` 通过 `settingsStore.themeMode` Flow 驱动 `NaiTheme(darkTheme = ...)`
- 切换后立即生效，无需重启

### 14.3 多语言

- 7 种语言 strings.xml：`values/`（中文默认）、`values-en/`、`values-ja/`、`values-ko/`、`values-fr/`、`values-de/`、`values-es/`
- 设置 → 关于 → 语言 → `AppCompatDelegate.setApplicationLocales()`
- `MainActivity` 必须继承 `AppCompatActivity`，主题必须继承 `Theme.AppCompat.*`
- 底部导航栏 + 设置页已迁移到 `stringResource()`
- **后续待做**：其他页面（生成页、相册页等）的 UI 字符串仍硬编码中文

### 14.4 版本号

- `versionName` = 纯 SemVer（如 `2.3.3`）— 安卓系统识别的
- `versionCode` = `major*10000 + minor*100 + patch`（如 `20303`）
- `BuildConfig.BUILD_NUMBER` = 累计构建次数（如 `20033`）— 只在关于页显示
- 关于页显示格式：`BUILD_NUMBER - 20000 / SEMVER`（如 `33 / 2.3.3`）
- 工作流 release notes 里也用 `DISPLAY_BUILD = BUILD - 20000`

### 14.5 页面切换动画

所有设置子页面都加了 `slideInHorizontally` / `slideOutHorizontally` 过渡动画。
新增子页面时记得加：
```kotlin
composable(
    SubDest.Xxx.route,
    enterTransition = { slideInHorizontally { it } },
    exitTransition = { slideOutHorizontally { -it / 3 } },
    popEnterTransition = { slideInHorizontally { -it / 3 } },
    popExitTransition = { slideOutHorizontally { it } }
) { XxxScreen(nav = nav) }
```

### 14.6 DropdownSelector 组件

用 `ExposedDropdownMenuBox` 实现的通用下拉菜单，解决所有 `OutlinedTextField.readOnly + clickable` 不响应问题。
```kotlin
DropdownSelector(
    label = "保留时间",
    options = listOf("不限制", "1 小时", "24 小时"),
    selectedIndex = 0,
    onSelected = { index -> ... }
)
```
**不要再用手写的 `OutlinedTextField.readOnly + clickable` 下拉菜单**，一定用 `DropdownSelector`。
