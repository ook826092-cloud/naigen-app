# 厂商「灵动岛」类能力适配研究（Android 进度通知）

> 适用范围：NaiGen 生成任务的实时进度通知（`GenerationService`）。
> 目标：在 Android 8.0 (API 26) ~ Android 16+ 上，让进度通知尽可能在厂商的
> 「灵动岛 / 超级岛 / 流体云 / 原子岛」等焦点区能力中正确呈现，同时老安卓
> 能优雅降级为普通进度通知。

---

## 1. 核心结论（先说人话）

**不要堆各厂商私有 extra key。**

各厂商的"岛"实现差异极大、且不公开统一的简单 key。Android 官方在
[「实时更新通知」指南](https://developer.android.com/develop/ui/views/notifications/live-update)
中明确警告：

> 自定义通知在不同 Android 版本和设备制造商之间的行为差异很大，因此难以
> 进行一致的测试并提供一致的用户体验。**避免使用自定义 RemoteViews / 私有 extra。**

所以 NaiGen 的主路径是 **标准 Android 进度通知规范**，让各厂商自己去识别呈现。
这带来最好的跨版本 / 跨厂商兼容性，老安卓也只是看不到"岛"但仍有进度条。

标准字段（代码中 `GenerationService.buildProgressNotification` 已实现）：
- `setOngoing(true)` —— 持续通知，不被滑动清除
- `setCategory(NotificationCompat.CATEGORY_PROGRESS)` —— 标记为进度通知
- `setProgress(total, current, indeterminate)` —— 进度条
- `setLocusId("naigen_generation")` —— Android 12+，关联"场景"，厂商焦点区靠此识别
- `setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)` —— Android 15+ 立即显示
- 前台服务 `foregroundServiceType = dataSync` + 带 type 的 `startForeground` 重载

---

## 2. 各厂商机制梳理

### 小米 / 红米 —— 超级岛（澎湃OS）
- **OS1**：仅"焦点通知"，无岛
- **OS2**：焦点通知 + 状态栏数据，无岛
- **OS3**（对应 Android 15/16 时代）：**真正支持超级岛**
- 官方协议：在 `Notification.extras` 放 **`miui.focus.param`**（JSON 字符串），
  内部结构含 `param_island.bigIslandArea` / `smallIslandArea`、`business`、`ticker`、`protocol`。
  还需 `miui.focus.pics` / `miui.focus.actions` 等（图片/动作映射）。
- ⚠️ **社区流传的 `miui.flags_is_foreground_service` / `miui.flags_progress_title`
  等 key 在小米官方文档中不存在**，属无效 hack。NaiGen 已移除这些，仅在
  OS3+ 尝试放置最小化 `miui.focus.param`（失败静默忽略）。
- 判定是否支持岛：`persist.sys.feature.island == "1"`（代码里直接尝试放置，
  非小米设备忽略该 extra 无副作用）。

### OPPO / 一加 —— 流体云（ColorOS 15+）
- 靠**系统级智慧推送**与识别 **ongoing + category=progress + LocusId** 通知呈现。
- 无公开的私有 extra key 清单；官方主推推送服务（Push SDK）。
- NaiGen 走标准字段即可被流体云识别。

### vivo / iQOO —— 原子岛
- 识别 **ongoing + category=progress** 的通知在原子岛呈现。
- 无需私有 extra；标准字段足够。

### 荣耀 / 魅族 / 三星 / 原生 Android
- 荣耀/魅族有类似焦点区能力，同样识别标准 progress 通知。
- 三星 / 原生 Android：标准进度通知 + LocusId，无"岛"但体验一致。

---

## 3. 版本适配矩阵

| Android 版本 | 标准进度通知 | LocusId | 小米超级岛 | 备注 |
|--------------|------------|---------|-----------|------|
| 8.0–11 (26–30) | ✅ | ❌（S 才引入） | ❌ | 普通进度通知 |
| 12–14 (31–34) | ✅ | ✅ | ❌（需 OS3） | 焦点区靠 LocusId |
| 15 (35) | ✅ | ✅ | 部分（OS3） | 前台服务须带 type，`FOREGROUND_SERVICE_IMMEDIATE` |
| 16 (36+) | ✅ | ✅ | ✅ | 启动后 **6 秒内**必须显示通知（强制） |

---

## 4. NaiGen 当前实现（代码层面）

`GenerationService.buildProgressNotification()`：
- 主路径：标准 Android 进度通知规范（跨厂商 / 跨版本兼容，老安卓降级）。
- Android 12+：加 `LocusId`，并在小米设备尝试 `miui.focus.param` 最小化协议。
- 任何私有 extra 写入失败都 `try/catch` 静默忽略，**绝不影响标准通知**。
- 前台服务：已用带 `FOREGROUND_SERVICE_TYPE_DATA_SYNC` 的 `startForeground` 重载
  （`startForegroundCompat`），满足 Android 15/16 规范。

### 进度条语义说明（原问题5「60s 固定值失真」）
`expectedTotalSec = 60` 是历史硬编码。真实生图时长依赖服务器（最长 180s）。
NaiGen 进度条目前：
- 单张：基于轮询秒数，`coerceAtMost(expectedTotalSec)`，超 60s 后停在满格；
- 并发：用 `variant+1 / variants` 计数进度（更合理）。
- 因真实耗时不可预估，建议长期使用「不确定进度」（`setProgress(0,0,true)`）
  或基于变体计数的进度，而非时间估算。本期保留接口、修正 coerce 防越界，
  未做大改（避免引入回归）。

---

## 5. 后续可优化
1. 进度条改"不确定进度"或变体计数进度，去除时间估算失真。
2. 小米 `miui.focus.param` 如需完整岛布局（图文/动作），需补 `miui.focus.pics`
   / `miui.focus.actions` 并实现 `bigIslandArea` 结构化布局（成本较高，按需）。
3. 建立真机矩阵测试（小米OS3 / OPPO ColorOS15 / vivo 等）验证焦点区呈现。
