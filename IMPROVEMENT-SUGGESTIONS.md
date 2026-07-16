# NaiGen 项目改进建议

> 评估时间：2026-07-16
> 评估范围：代码质量、架构、稳定性、安全性、测试、CI、依赖
> 适用版本：v2.3.x

---

## 概述

本项目是一个基于 NovelAI Diffusion 4.5 的 Android 原生 Kotlin 文生图客户端。整体架构清晰（Compose + Coroutines + Room + DataStore + OkHttp），代码注释详尽，单元测试覆盖了核心网络层。

以下建议按优先级分级，供后续迭代参考。

---

## 一、高优先级（建议尽快处理）

### 1. Room 数据库迁移策略存在数据丢失风险

**位置**：`app/src/main/java/com/naigen/app/data/db/AppDatabase.kt`

**问题**：

当前使用 `fallbackToDestructiveMigration()`，数据库 `version = 3`。**每次 schema 变更（加字段/加表）都会清空用户的历史记录和收藏夹**，对已发布 App 是不可接受的体验。

**建议**：

```kotlin
Room.databaseBuilder(
    context.applicationContext,
    AppDatabase::class.java,
    "naigen.db"
)
    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
    .fallbackToDestructiveMigrationOnDowngrade()  // 仅降级时丢数据
    .build()
```

同时：

- 把 `exportSchema = false` 改为 `true`
- 在 `app/build.gradle.kts` 配置 `room.schemaLocation`
- 把生成的 schema JSON 纳入版本控制，为后续迁移提供依据

---

### 2. ImageSaver 文件命名存在并发竞态

**位置**：`app/src/main/java/com/naigen/app/util/ImageSaver.kt` → `nextDatedName()`

**问题**：

`nextDatedName()` 通过扫描目录取最大编号 +1。在并发生成 N 张变体时（走 `NaiRepository.generateVariants` 的 `async { }.awaitAll()`），多个协程可能同时读到相同 `maxNo`，导致文件互相覆盖。

**建议**：

- 方案 A：在 `savePrivate` 内加 `synchronized` 锁
- 方案 B：用 `AtomicInteger` 维护进程内序号
- 方案 C：文件名直接拼 `System.currentTimeMillis()` 毫秒后缀

```kotlin
private fun nextDatedName(dir: File, ext: String): String {
    val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    val stamp = System.currentTimeMillis()
    return "${date}_${stamp}.$ext"
}
```

---

### 3. GenerationBus 持有大体积 ByteArray

**位置**：`app/src/main/java/com/naigen/app/service/GenerationBus.kt`

**问题**：

`_results: MutableStateFlow<List<GenResult>>` 会持有图片的 `ByteArray`（单张可达数 MB）。并发 6 张时可能常驻几十 MB 内存，且 ViewModel collect 后未主动清空。

**建议**：

- ViewModel 消费完结果后调用 `releaseResults()` 把 `_results.value` 置空
- 或 App 进入后台时清空
- 长期方案：`GenResult` 只持有文件路径，UI 层按需用 Coil 加载

```kotlin
fun releaseResults() {
    _results.value = emptyList()
}
```

---

## 二、中优先级（建议在下个迭代处理）

### 4. HttpLoggingInterceptor 与 AppLog.network 职责重叠

**位置**：`app/src/main/java/com/naigen/app/data/api/NaiApiClient.kt`

**问题**：

同时挂了 `HttpLoggingInterceptor(BASIC)` 和自定义 `AppLog.network()` 调用。两者职责重叠，BASIC 级别在生产 APK 里价值有限，反而多一次字符串拼接。

**建议**：

- debug 构建：启用 `HttpLoggingInterceptor(BODY)`
- release 构建：禁用 `HttpLoggingInterceptor`，仅依赖 `AppLog.network()`

```kotlin
val sharedClient: OkHttpClient by lazy {
    val builder = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // ...

    if (BuildConfig.DEBUG) {
        builder.addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
    }
    builder.build()
}
```

---

### 5. pollIntervalSec 公式与注释不一致

**位置**：`app/src/main/java/com/naigen/app/data/repository/NaiRepository.kt` → `pollIntervalSec()`

**问题**：

注释写「n=4 → 8s, n=5 → 16s」，但实际被 `MAX_POLL_INTERVAL_SEC = 5` 上限截断后永远是 5s，且 `(elapsedSec / 2)` 的近似让公式语义模糊。

**建议**：

直接用明确的退避序列，并加单测覆盖：

```kotlin
private val BACKOFF_SECONDS = listOf(1, 1, 1, 2, 3, 5, 5, 5, 5, 5)

private fun pollIntervalSec(pollCount: Int): Long {
    return BACKOFF_SECONDS.getOrElse(pollCount) { 5 }.toLong()
}
```

---

### 6. 已废弃常量应清理

**位置**：`NaiRepository.kt` → `POLL_INTERVAL_MS`

**问题**：

标注了 `@Deprecated` 但仍保留。项目无外部 SDK 消费者，保留意义不大。

**建议**：

确认无引用后直接删除。

---

### 7. READ_MEDIA_IMAGES 权限可能多余

**位置**：`app/src/main/AndroidManifest.xml`

**问题**：

声明了 `READ_MEDIA_IMAGES`，但代码里只看到 `ImageSaver.saveToGallery` 通过 `MediaStore.insert` **写**相册（Android 10+ 无需任何权限）。

**建议**：

- 全局 grep 确认是否存在 `contentResolver.query` 调用点读取相册
- 若无读取相册需求，移除该权限，减少用户授权焦虑

---

### 8. ShizukuProvider 的 exported 配置

**位置**：`app/src/main/AndroidManifest.xml`

**问题**：

`ShizukuProvider` 设了 `android:exported="true"` + `INTERACT_ACROSS_USERS_FULL` 权限。这是 Shizuku 官方要求，但建议按 Shizuku 当前文档限定到更具体的权限，避免 lint 警告。

---

## 三、测试与 CI（中优先级）

### 9. 测试覆盖有明显空白

**当前覆盖**：`NaiApiClient` / `NaiRepository` / `StylesLogic` / `RedactToken`

**缺失的关键路径**：

| 模块 | 缺失测试点 | 风险等级 |
|---|---|---|
| `SettingsStore` | token 从 DataStore → EncryptedSharedPreferences 的迁移 | 高（敏感数据） |
| `ImageSaver.makeThumbnail` | 降采样边界（空字节、超大图、宽高为 0） | 中 |
| `pollIntervalSec` | 退避序列 | 低 |
| `GenerationBus` | 状态机流转 | 中 |

**建议**：

`SettingsStore` 迁移逻辑是高风险路径，建议用 Robolectric 测一遍完整迁移流程。

---

### 10. CI 缺少覆盖率上报与 lint 阻塞

**位置**：`.github/workflows/build.yml`

**问题**：

- lint 设了 `continue-on-error: true`，长期会让 lint 失效
- 无覆盖率上报，PR 无法直观看到测试影响

**建议**：

- 引入 [Kover](https://github.com/Kotlin/kotlinx-kover) 生成覆盖率报告并上传到 PR 评论
- 把 lint 警告分批清理，目标半年内改成阻塞式

---

### 11. 缺少 instrumented 测试

**问题**：

仅有纯 JVM 单测。关键的前台服务启动 / 通知渠道 / Room 真实数据库行为无覆盖。

**建议**：

加 `androidTest`（用 `androidx.test:runner` + 真机或 Robolectric），重点覆盖：

- `GenerationService` 启动与取消
- 通知渠道创建
- Room 真实读写

---

## 四、依赖与构建（低优先级）

### 12. 依赖版本偏旧

| 依赖 | 当前版本 | 建议目标 |
|---|---|---|
| AGP | 8.5.2 | 8.7+ |
| Kotlin | 2.0.0 | 2.0.20+ |
| Compose BOM | 2024.08.00 | 2024.12+ |
| Room | 2.6.1 | 2.7.x |
| Gradle | 8.9 | 8.11+ |
| coil-compose | 2.7.0 | 评估 3.x |

**建议**：升级前先跑一遍单测 + lint，分批升级避免一次性引入多个兼容性问题。

---

### 13. debug 构建的混淆开关

**位置**：`app/build.gradle.kts`

**建议**：

- 确认 debug 的 `isMinifyEnabled = false` 时，能通过临时开关测混淆场景
- debug 构建可加 `isDebuggable = true`（已默认）

---

## 五、代码质量细节（低优先级）

### 14. 全限定名调用过多

**位置**：`app/src/main/java/com/naigen/app/service/GenerationService.kt`

**问题**：

大量使用 `com.naigen.app.util.AppLog.i(...)`、`com.naigen.app.data.repository.NaiRepository.MAX_VARIANTS` 等全限定名，影响可读性。

**建议**：

统一在文件头 import，让代码更简洁。

---

### 15. GenResult / GenImage 的 @Stable 说明值得讨论

**位置**：`app/src/main/java/com/naigen/app/data/model/Models.kt`

**问题**：

注释解释了为何用 `@Stable` 而非 `@Immutable`（因 `ByteArray` 可变）。这是合理的妥协，但更彻底的方案是：

- 把 `bytes` 改成不可变包装（如自定义 `ImmutableBytes(val value: ByteArray)` 并加 `@Immutable`）
- 或直接在落盘后只存 `path`，UI 层按需用 Coil 加载，完全避免 `ByteArray` 在 Compose 树中流转

---

### 16. 文档与代码不同步

**位置**：`AI-DEV-GUIDE.md`

**问题**：

项目结构图描述了 `screen/album/`、`screen/settings/api/` 等，但未提到实际存在的：

- `screen/settings/theme/ThemeScreen.kt`
- `screen/settings/logs/LogsScreen.kt`

**建议**：

- 在 CI 里加一个简单脚本对比 `ui/screen/` 实际目录与文档目录树
- 或在文档头部明确「结构图仅展示主要模块，完整结构以源码为准」

---

## 建议优先落地的 3 件事

如果时间有限，优先做：

1. **第 1 项（Room 迁移）** — 防止下次加字段时用户数据全丢，这是上线后最难挽回的事故
2. **第 2 项（文件命名竞态）** — 并发生成是核心卖点，这个 bug 一旦触发用户会丢图
3. **第 9 项（SettingsStore 迁移测试）** — token 是最敏感数据，迁移逻辑出错会让用户直接无法生图

---

## 附录：改进路径建议

```
Phase 1（1-2 周内）
├── Room 迁移改造 + schema 导出
├── ImageSaver 并发竞态修复
└── GenerationBus 内存释放

Phase 2（1 个月内）
├── SettingsStore 迁移测试
├── pollIntervalSec 重构 + 测试
├── HttpLoggingInterceptor 分流
└── 权限清单清理

Phase 3（季度迭代）
├── 依赖版本升级
├── Kover 覆盖率接入
├── instrumented 测试补齐
└── lint 阻塞化
```

---

*本文档由代码审查生成，供项目维护者参考。*
