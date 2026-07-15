# 厂商「灵动岛」类能力适配（实时进度通知）

> 适用范围：NaiGen 生成任务的实时进度通知（`GenerationService` + `IslandNotifier`）。
> 目标：在 Android 8.0 (API 26) ~ Android 16+ 上，让进度通知在厂商的
> 「超级岛 / 流体云 / 原子岛 / ProgressStyle」中正确呈现，老安卓优雅降级。

---

## 1. 适配架构（按优先级分流）

```
IslandNotifier.buildProgressNotification()
        │
        ├─ ① 小米超级岛（澎湃 OS 2/3）
        │     · vendor == XIAOMI && miuiFocusProtocol in 2..3
        │     · 走 miui.focus.param 模板5（文本+进度组件），含真实进度条
        │     · 必须用 CHANNEL_ISLAND（IMPORTANCE_DEFAULT）
        │
        ├─ ② Android 16+ Notification.ProgressStyle（API 36）
        │     · OPPO ColorOS 16 流体云、原生 Android 16 自动兼容
        │     · 走反射调用，避免 compileSdk=35 编译失败
        │
        └─ ③ 标准进度通知（兜底）
              · ongoing + CATEGORY_PROGRESS + setProgress + LocusId(S+) +
                FOREGROUND_SERVICE_IMMEDIATE
              · 老安卓 / 不支持上岛的厂商
```

任何上岛路径失败都会 `runCatching` 降级到下一路径，**绝不影响通知本身**。

---

## 2. 小米超级岛 —— 官方协议核心修正

### 旧实现为何无法上岛

```kotlin
// ❌ 旧代码（错误）
val focusParam = JSONObject().apply {
    put("business", "naigen_generation")
    put("ticker", text)
    put("protocol", 3)                      // 错：protocol=1 是兼容协议版本号
    put("param_island", JSONObject().apply { // 错：顶层应是 param_v2
        put("bigIslandArea", ...)
    })
}.toString()
notification.extras.putString("miui.focus.param", focusParam)
```

| 错误 | 后果 |
|------|------|
| 顶层少了 `param_v2` 包裹层 | 小米系统**根本识别不出**这是上岛通知 |
| `protocol` 值用 3 | 实际应为 `1`（OS2/OS3 兼容协议版本） |
| 用 `param_island` 字段 | 不在官方协议中，无效 |
| 没用模板5（文本+进度组件） | 生图场景最佳模板未启用 |
| 没查设备是否支持 | 不支持的设备浪费 extra |

### 新实现（[IslandNotifier.kt](file:///workspace/app/src/main/java/com/naigen/app/service/IslandNotifier.kt)）

```kotlin
// ✅ 新代码
val paramV2 = JSONObject().apply {
    put("protocol", 1)                              // OS2/OS3 兼容协议
    put("business", "naigen_generation")
    put("enableFloat", true)
    put("updatable", true)
    put("ticker", text)
    put("aodTitle", "NaiGen · 生成中")

    // 大岛（展开态）—— 文本组件1 + 进度组件2（模板5）
    put("bigIslandArea", JSONObject().apply {
        put("title", "NaiGen")
        put("content", text)
        put("progress", JSONObject().apply {
            put("progress", percent)              // 0..100
            put("colorProgress", "#FF007AFF")
            put("isAutoProgress", false)
        })
        put("picInfo", JSONObject().apply { put("type", 1) })
    })

    // 小岛（折叠态）
    put("smallIslandArea", JSONObject().apply {
        put("title", "NaiGen")
        put("content", "$percent%")
        put("picInfo", JSONObject().apply { put("type", 1) })
    })
}
val focusParam = JSONObject().apply {
    put("param_v2", paramV2)                       // 关键：必须包裹在 param_v2 下
}.toString()
notification.extras.putString("miui.focus.param", focusParam)
```

### 设备能力判定

```kotlin
// 通过 Settings.System 查小米焦点通知协议版本
val miuiFocusProtocol = Settings.System.getInt(
    contentResolver, "notification_focus_protocol", 0
)
// 0: 不支持
// 1: OS1 焦点通知
// 2: OS2 焦点通知（状态栏、通知中心、锁屏、息屏、小折叠外屏）
// 3: OS3 小米超级岛（含岛摘要态、岛展开态 + 焦点通知）
```

### 上线前必做（小米开发者侧）

1. 注册小米开发者账号并实名认证
2. 在开发者平台创建/上架应用
3. 配置 APK 签名证书指纹
4. 申请「焦点通知」权限（邮件申请，详见小米官方 Q&A）
5. 联调阶段：在「设备白名单管理」加入测试设备 IMEI
6. 通过审核后小米会灰度放量，约 7~15 天全量

> 参考：[小米超级岛开发指南](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2131) ｜
> [版本信息](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2141) ｜
> [展开态设计规范](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2142)

---

## 3. Android 16+ ProgressStyle（OPPO ColorOS 16 流体云）

OPPO ColorOS 16 的流体云已完整接入 Android 16 的 `Notification.ProgressStyle` API。
**只要应用遵循 Google 实时活动 API 规范即可直接上岛。**

### 实现方式

由于 `compileSdk = 35`，无法直接引用 `Notification.ProgressStyle`（API 36 类）。
采用**反射调用**：

```kotlin
if (Build.VERSION.SDK_INT >= 36) {
    val progressStyleClass = Class.forName("android.app.Notification\$ProgressStyle")
    val progressStyle = progressStyleClass.getDeclaredConstructor().newInstance()
    // setProgressSegments([Segment(done, accentColor), Segment(remaining, grayColor)])
    // setProgress(current)
    // setBuilder(builder)
    // build() → Notification
}
```

升级 `compileSdk = 36` 后可改为直接调用，无需反射。

### 官方模板字段

| 参数 | 含义 |
|------|------|
| A 背景图标 | 一般为 App 图标 |
| B 头部标题 | 一般为 App 名称 |
| D 通知标题 | 进度标题 |
| E 通知内容 | 进度文案 |
| F 进度条 | `setProgressSegments` + `setProgress` |
| G 操作按钮 | 可选 |

参考：[Android 16 以进度为主轴的通知](https://developer.android.com/about/versions/16/features/progress-centric-notifications) ｜
[Jetpack Compose 实时更新通知](https://developer.android.com/develop/ui/compose/notifications/live-update)

---

## 4. 各厂商机制总结

| 厂商 | 能力名称 | 接入方式 | 本 App 实现 |
|------|---------|---------|------------|
| 小米 / 红米 | 超级岛（澎湃OS 2/3） | `miui.focus.param` JSON 协议 | ✅ 模板5 + 进度组件 |
| OPPO / 一加 | 流体云（ColorOS 14+） | ColorOS 16 走 ProgressStyle；14/15 走意图共享 SDK | ✅ API 36+ 自动适配 |
| vivo / iQOO | 原子岛（OriginOS 5+） | 厂商私有协议（未完全公开），部分兼容标准进度通知 | ⚠️ 标准通知降级 |
| 荣耀 | 灵动胶囊（MagicOS 8+） | 基于 YOYO 建议，第三方需单独适配 | ⚠️ 标准通知降级 |
| 三星 / 原生 | 无岛 | 标准 ProgressStyle / 进度通知 | ✅ 标准通知 |

---

## 5. 版本适配矩阵

| Android 版本 | 标准进度通知 | LocusId | 小米超级岛 | ProgressStyle | 备注 |
|--------------|------------|---------|-----------|---------------|------|
| 8.0–11 (26–30) | ✅ | ❌ | ❌ | ❌ | 普通进度通知 |
| 12–14 (31–34) | ✅ | ✅ | OS2 焦点通知 | ❌ | LocusId 识别场景 |
| 15 (35) | ✅ | ✅ | OS3 超级岛 | ❌ | `FOREGROUND_SERVICE_IMMEDIATE` |
| 16 (36+) | ✅ | ✅ | OS3 超级岛 | ✅ | 启动 6 秒内必须显示通知 |

---

## 6. 通知渠道说明

```kotlin
// NaiApplication.kt
CHANNEL_GENERATION  // IMPORTANCE_LOW   —— 老安卓标准进度通知
CHANNEL_RESULT      // IMPORTANCE_DEFAULT —— 完成结果通知
CHANNEL_ISLAND      // IMPORTANCE_DEFAULT —— 上岛专用（无声音，仅视觉）
```

小米超级岛要求渠道 `IMPORTANCE_DEFAULT` 或更高才能上岛，
但进度频繁更新会扰民，所以 `CHANNEL_ISLAND` 显式禁用了声音和震动。

---

## 7. 代码文件索引

| 文件 | 职责 |
|------|------|
| [IslandNotifier.kt](file:///workspace/app/src/main/java/com/naigen/app/service/IslandNotifier.kt) | 厂商分流 + 上岛 JSON 构建 + ProgressStyle 反射 |
| [GenerationService.kt](file:///workspace/app/src/main/java/com/naigen/app/service/GenerationService.kt) | 前台服务，委托 IslandNotifier 构建通知 |
| [NaiApplication.kt](file:///workspace/app/src/main/java/com/naigen/app/NaiApplication.kt) | 三个通知渠道初始化 |

---

## 8. 后续可优化

1. **小米超级岛图片资源**：当前 `picInfo.type=1` 用系统默认桌面图标，
   可改为自定义图标资源（需上传到小米开发者平台）
2. **小米超级岛动作按钮**：可加 `miui.focus.actions` 提供取消按钮
3. **ProgressStyle 升级到直接调用**：升级 `compileSdk = 36` 后移除反射
4. **vivo / 荣耀接入**：等厂商公开第三方接入协议后补充
5. **真机矩阵测试**：小米 OS3 / OPPO ColorOS 16 / vivo OriginOS 5 验证
