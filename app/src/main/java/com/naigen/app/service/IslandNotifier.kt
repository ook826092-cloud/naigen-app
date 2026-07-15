package com.naigen.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.naigen.app.MainActivity
import com.naigen.app.NaiApplication
import com.naigen.app.R
import com.naigen.app.util.AppLog
import org.json.JSONObject

/**
 * 厂商灵动岛 / 流体云 / 焦点通知统一适配器。
 *
 * 适配路径按优先级：
 *   1. 小米超级岛（澎湃OS 2/3）：通过 `miui.focus.param` 扩展 extra 接入官方模板
 *      —— 使用 OS2/OS3 兼容的「模板5：文本组件1 + 进度组件2」，含进度条
 *   2. Android 16+ Notification.ProgressStyle（API 36+）
 *      —— 原生进度通知样式，OPPO ColorOS 16 流体云、未来其他厂商自动兼容
 *   3. 标准进度通知（老安卓 / 不支持上岛的厂商）
 *      —— ongoing + CATEGORY_PROGRESS + LocusId + FOREGROUND_SERVICE_IMMEDIATE
 *
 * 关键修复点（vs 旧实现）：
 *   - `miui.focus.param` 必须用 `{"param_v2": {...}}` 顶层包裹，否则小米系统不识别
 *   - `protocol` 字段值 1（兼容协议），不是 OS 版本号
 *   - 调用方必须先查 `Settings.System#notification_focus_protocol` 判断设备是否支持
 *   - 通知渠道必须 IMPORTANCE_DEFAULT 或更高，否则焦点通知不显示
 *
 * 参考文档：
 *   - 小米超级岛开发指南: https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2131
 *   - 小米超级岛模板库:   小米超级岛通知模板库.pdf
 *   - Android 16 ProgressStyle:
 *     https://developer.android.com/about/versions/16/features/progress-centric-notifications
 *   - OPPO ColorOS 16 流体云兼容 Android 16 ProgressStyle API
 */
class IslandNotifier(private val context: Context) {

    /** 当前设备厂商分类 */
    private val vendor: Vendor = detectVendor()

    /** 小米超级岛协议版本（0=不支持，1=OS1，2=OS2 焦点通知，3=OS3 超级岛） */
    private val miuiFocusProtocol: Int by lazy { readMiuiFocusProtocol() }

    /** 通知 ID 与原 GenerationService 保持一致，便于无缝替换 */
    private val progressNotificationId: Int = 1001
    private val resultNotificationId: Int = 1002

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * 构建进度通知（前台服务用）。
     *
     * @param text 进度文案，例如 "生成中… 12s · job abc12345"
     * @param current 当前进度（0..total）
     * @param total   总进度
     * @param jobBrief 任务简短标识（用于小米超级岛 smallIslandArea）
     */
    fun buildProgressNotification(
        text: String,
        current: Int,
        total: Int,
        jobBrief: String = ""
    ): Notification {
        // 公共基础字段
        val pi = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val indeterminate = current <= 0
        val safeTotal = total.coerceAtLeast(1)
        val safeCurrent = current.coerceIn(0, safeTotal)

        // 路径 1：小米超级岛（澎湃 OS 2/3）
        if (vendor == Vendor.XIAOMI && miuiFocusProtocol in 2..3) {
            runCatching {
                return buildXiaomiIslandNotification(
                    text = text,
                    current = safeCurrent,
                    total = safeTotal,
                    indeterminate = indeterminate,
                    jobBrief = jobBrief,
                    contentIntent = pi
                )
            }.onFailure {
                AppLog.w("IslandNotifier", "小米超级岛构建失败，降级标准通知: ${it.message}")
            }
        }

        // 路径 2：Android 16+ ProgressStyle（OPPO ColorOS 16 流体云、原生 Android 16）
        if (Build.VERSION.SDK_INT >= API_LEVEL_ANDROID_16) {
            runCatching {
                return buildProgressStyleNotification(
                    text = text,
                    current = safeCurrent,
                    total = safeTotal,
                    indeterminate = indeterminate,
                    contentIntent = pi
                )
            }.onFailure {
                AppLog.w("IslandNotifier", "ProgressStyle 构建失败，降级标准通知: ${it.message}")
            }
        }

        // 路径 3：标准进度通知（兜底）
        return buildStandardProgressNotification(
            text = text,
            current = safeCurrent,
            total = safeTotal,
            indeterminate = indeterminate,
            contentIntent = pi
        )
    }

    /**
     * 更新进度通知（前台服务运行期间调用）。
     */
    fun notifyProgress(text: String, current: Int, total: Int, jobBrief: String = "") {
        val notification = buildProgressNotification(text, current, total, jobBrief)
        notificationManager.notify(progressNotificationId, notification)
    }

    /**
     * 任务完成 / 失败时通知。会先取消进度通知，再发一条普通通知。
     */
    fun notifyDone(title: String, text: String) {
        notificationManager.cancel(progressNotificationId)

        val pi = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 小米超级岛：done 阶段也走岛通知（用 minimal 模板，无进度条）
        if (vendor == Vendor.XIAOMI && miuiFocusProtocol in 2..3) {
            runCatching {
                val notif = buildXiaomiIslandDoneNotification(title, text, pi)
                notificationManager.notify(resultNotificationId, notif)
                return
            }.onFailure {
                AppLog.w("IslandNotifier", "小米超级岛 done 构建失败，降级: ${it.message}")
            }
        }

        val notif = NotificationCompat.Builder(context, NaiApplication.CHANNEL_RESULT)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_app_placeholder)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager.notify(resultNotificationId, notif)
    }

    /** 取消所有通知（任务被取消时调用） */
    fun cancelAll() {
        notificationManager.cancel(progressNotificationId)
        notificationManager.cancel(resultNotificationId)
    }

    // ── 路径 1：小米超级岛 ────────────────────────────────────────────────────

    /**
     * 构建小米超级岛进度通知。
     *
     * 使用官方「模板5：文本组件1 + 进度组件2」（OS2/OS3 兼容）：
     *   - 文本组件1：主要文本 + 次要文本
     *   - 进度组件2：当前进度 + 进度条颜色
     *
     * JSON 顶层结构（关键修复点）：
     * ```
     * {
     *   "param_v2": {
     *     "protocol": 1,                    // 协议版本（1=OS2/OS3 兼容）
     *     "business": "naigen_generation",  // 业务场景标识
     *     "enableFloat": true,              // 启用浮窗
     *     "updatable": true,                // 允许更新
     *     "ticker": "...",                  // 状态栏 ticker 文本
     *     "aodTitle": "...",                // 息屏标题
     *     "bigIslandArea": { ... },         // 大岛展开态
     *     "smallIslandArea": { ... }        // 小岛折叠态
     *   }
     * }
     * ```
     */
    private fun buildXiaomiIslandNotification(
        text: String,
        current: Int,
        total: Int,
        indeterminate: Boolean,
        jobBrief: String,
        contentIntent: PendingIntent
    ): Notification {
        // 进度百分比（0..100）
        val percent = if (indeterminate || total <= 0) 0
        else ((current.toLong() * 100) / total).toInt().coerceIn(0, 100)

        // 进度条颜色：iOS 系统蓝
        val progressColor = "#FF007AFF"

        val paramV2 = JSONObject().apply {
            put("protocol", 1)                              // OS2/OS3 兼容协议
            put("business", BUSINESS_NAIGEN_GENERATION)
            put("enableFloat", true)
            put("updatable", true)
            put("ticker", text)                             // 状态栏胶囊文本
            put("aodTitle", context.getString(R.string.app_name) + " · 生成中")

            // 大岛（展开态）—— 文本组件1 + 进度组件2
            put("bigIslandArea", JSONObject().apply {
                put("title", context.getString(R.string.app_name))
                put("content", text)
                // 进度组件2：当前进度 + 进度条颜色
                put("progress", JSONObject().apply {
                    put("progress", percent)
                    put("colorProgress", progressColor)
                    put("isAutoProgress", false)
                })
                // 图标（取桌面图标，系统默认）
                put("picInfo", JSONObject().apply {
                    put("type", 1)
                })
            })

            // 小岛（折叠态）—— 极简文本
            put("smallIslandArea", JSONObject().apply {
                put("title", context.getString(R.string.app_name))
                put("content", if (jobBrief.isNotBlank()) jobBrief else "$percent%")
                put("picInfo", JSONObject().apply {
                    put("type", 1)
                })
            })
        }

        // 完整 JSON 包装在 param_v2 下
        val focusParam = JSONObject().apply {
            put("param_v2", paramV2)
        }.toString()

        AppLog.d("IslandNotifier", "miui.focus.param:\n$focusParam")

        // 基础通知（必须 IMPORTANCE_DEFAULT 或更高才能上岛）
        val builder = NotificationCompat.Builder(context, NaiApplication.CHANNEL_ISLAND)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_app_placeholder)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setProgress(total, current.coerceAtMost(total), indeterminate)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        val notification = builder.build()

        // Android 12+ 加 LocusId（各厂商岛识别持续场景的标准字段）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            notification.extras.putString("android.locusId", LOCUS_ID_GENERATION)
        }

        // 写入 miui.focus.param —— 这是上岛的关键 extra
        notification.extras.putString("miui.focus.param", focusParam)

        return notification
    }

    /**
     * 小米超级岛 done 通知（任务完成 / 失败时）。
     * 不带进度条，只用基础模板展示最终状态。
     */
    private fun buildXiaomiIslandDoneNotification(
        title: String,
        text: String,
        contentIntent: PendingIntent
    ): Notification {
        val paramV2 = JSONObject().apply {
            put("protocol", 1)
            put("business", BUSINESS_NAIGEN_GENERATION)
            put("enableFloat", false)   // 完成后不再常驻岛
            put("updatable", false)
            put("ticker", title)
            put("aodTitle", title)

            put("bigIslandArea", JSONObject().apply {
                put("title", title)
                put("content", text)
                put("picInfo", JSONObject().apply { put("type", 1) })
            })
            put("smallIslandArea", JSONObject().apply {
                put("title", title)
                put("content", "")
                put("picInfo", JSONObject().apply { put("type", 1) })
            })
        }
        val focusParam = JSONObject().apply {
            put("param_v2", paramV2)
        }.toString()

        val builder = NotificationCompat.Builder(context, NaiApplication.CHANNEL_RESULT)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_app_placeholder)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.extras.putString("android.locusId", LOCUS_ID_GENERATION)
        }
        builder.extras.putString("miui.focus.param", focusParam)
        return builder
    }

    // ── 路径 2：Android 16+ ProgressStyle ────────────────────────────────────

    /**
     * 使用 Android 16 (API 36) 引入的 [Notification.ProgressStyle] 构建通知。
     *
     * OPPO ColorOS 16 流体云、未来其他兼容厂商会基于此样式自动上岛。
     * 老版本 Android 会因反射找不到类而抛出 NoClassDefFoundError，
     * 调用方已通过 SDK_INT 检查避免此路径，但额外 runCatching 兜底。
     */
    private fun buildProgressStyleNotification(
        text: String,
        current: Int,
        total: Int,
        indeterminate: Boolean,
        contentIntent: PendingIntent
    ): Notification {
        // 反射调用 Notification.ProgressStyle，避免在低 SDK 上类加载失败
        val progressStyleClass = Class.forName("android.app.Notification\$ProgressStyle")
        val segmentClass = Class.forName("android.app.Notification\$ProgressStyle\$Segment")

        // new ProgressStyle()
        val progressStyle = progressStyleClass.getDeclaredConstructor().newInstance()

        // setProgressSegments([Segment(current, accentColor), Segment(remaining, grayColor)])
        // 用两段颜色对比表达进度
        val accentColor = Color.parseColor("#FF007AFF")
        val grayColor = Color.parseColor("#FFD1D1D6")
        val remaining = (total - current).coerceAtLeast(0)

        val doneSegment = segmentClass.getDeclaredConstructor(
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
        ).newInstance(current.coerceAtLeast(0), accentColor)
        val remainSegment = segmentClass.getDeclaredConstructor(
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
        ).newInstance(remaining, grayColor)

        val segmentsList = listOf(doneSegment, remainSegment)
        val setProgressSegments = progressStyleClass.getDeclaredMethod(
            "setProgressSegments", List::class.java
        )
        setProgressSegments.invoke(progressStyle, segmentsList)

        // setProgress(current) —— 决定 tracker 图标位置
        val setProgressMethod = progressStyleClass.getDeclaredMethod(
            "setProgress", Int::class.javaPrimitiveType
        )
        setProgressMethod.invoke(progressStyle, current)

        // 构建基础 Notification
        @Suppress("DEPRECATION")
        val builder = Notification.Builder(context, NaiApplication.CHANNEL_ISLAND)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_app_placeholder)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)

        // progressStyle.setBuilder(builder)
        val setBuilderMethod = progressStyleClass.getDeclaredMethod("setBuilder", Notification.Builder::class.java)
        setBuilderMethod.invoke(progressStyle, builder)

        // 调用 progressStyle.build() 得到最终 Notification
        val buildMethod = progressStyleClass.getDeclaredMethod("build")
        val notification = buildMethod.invoke(progressStyle) as Notification

        // Android 12+ LocusId
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            notification.extras.putString("android.locusId", LOCUS_ID_GENERATION)
        }

        return notification
    }

    // ── 路径 3：标准进度通知（兜底） ─────────────────────────────────────────

    private fun buildStandardProgressNotification(
        text: String,
        current: Int,
        total: Int,
        indeterminate: Boolean,
        contentIntent: PendingIntent
    ): Notification {
        val builder = NotificationCompat.Builder(context, NaiApplication.CHANNEL_GENERATION)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_app_placeholder)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setProgress(total, current.coerceAtMost(total), indeterminate)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        val notification = builder.build()

        // Android 12+ 加 LocusId：各厂商岛 / 焦点区识别持续场景的标准字段
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            notification.extras.putString("android.locusId", LOCUS_ID_GENERATION)
        }

        return notification
    }

    // ── 厂商识别 ──────────────────────────────────────────────────────────

    private enum class Vendor { XIAOMI, OPPO, VIVO, HONOR, OTHER }

    private fun detectVendor(): Vendor {
        val manufacturer = (Build.MANUFACTURER ?: "").lowercase()
        val brand = (Build.BRAND ?: "").lowercase()
        return when {
            manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
                brand.contains("redmi") -> Vendor.XIAOMI
            manufacturer.contains("oppo") || brand.contains("oppo") ||
                brand.contains("oneplus") || brand.contains("realme") -> Vendor.OPPO
            manufacturer.contains("vivo") || brand.contains("vivo") ||
                brand.contains("iqoo") -> Vendor.VIVO
            manufacturer.contains("honor") || brand.contains("honor") -> Vendor.HONOR
            else -> Vendor.OTHER
        }
    }

    /**
     * 读取小米焦点通知协议版本。
     *
     * Settings.System 设置项 `notification_focus_protocol`：
     *   - 0: 不支持
     *   - 1: OS1 焦点通知
     *   - 2: OS2 焦点通知（状态栏、通知中心、锁屏、息屏、小折叠外屏）
     *   - 3: OS3 小米超级岛（含岛摘要态、岛展开态 + 焦点通知）
     *
     * 读取失败（非小米设备 / 无 READ_WRITE_SETTINGS 权限）返回 0。
     */
    private fun readMiuiFocusProtocol(): Int {
        return runCatching {
            Settings.System.getInt(
                context.contentResolver,
                "notification_focus_protocol",
                0
            )
        }.getOrDefault(0)
    }

    private companion object {
        const val API_LEVEL_ANDROID_16 = 36
        const val LOCUS_ID_GENERATION = "naigen_generation"
        const val BUSINESS_NAIGEN_GENERATION = "naigen_generation"
    }
}
