package com.naigen.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.naigen.app.MainActivity
import com.naigen.app.NaiApplication
import com.naigen.app.R
import com.naigen.app.util.AppLog
import org.json.JSONObject

/**
 * 厂商灵动岛 / 流体云 / 焦点通知统一适配器（v3）。
 *
 * 适配路径按优先级：
 *   1. 小米超级岛（澎湃OS 2/3）：`miui.focus.param`
 *   2. vivo 原子岛（OriginOS 4+）：`live.window.param`
 *   3. OPPO/一加 灵动岛（ColorOS 14+）：`op.activity.scene`
 *   4. 荣耀灵动胶囊（MagicOS 8+）：`magic.window.param`
 *   5. Android 16+ Notification.ProgressStyle（API 36+，通用路径）
 *   6. 标准进度通知（老安卓 / 不支持上岛的厂商）—— 最终兜底
 *
 * 关键设计：
 *   - 每条路径都用 runCatching 包裹，失败自动降级到下一条
 *   - extras 在 builder 阶段用 addExtras 设置（不在 build() 后修改，避免设备崩溃）
 *   - 所有厂商协议都通过 Bundle extras 传递，反射仅用于 ProgressStyle（API 36+ 类）
 *   - 厂商识别基于 Build.MANUFACTURER，保守匹配
 */
class IslandNotifier(private val context: Context) {

    private val vendor: Vendor = detectVendor()
    private val miuiFocusProtocol: Int by lazy { readMiuiFocusProtocol() }

    private val progressNotificationId: Int = 1001
    private val resultNotificationId: Int = 1002

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        AppLog.i("IslandNotifier", "初始化: vendor=$vendor, miuiFocusProtocol=$miuiFocusProtocol, sdk=${Build.VERSION.SDK_INT}")
    }

    /**
     * 构建进度通知（前台服务用）。
     */
    fun buildProgressNotification(
        text: String,
        current: Int,
        total: Int,
        jobBrief: String = ""
    ): Notification {
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

        // 按厂商优先级尝试，失败降级
        val tried = mutableListOf<String>()

        // 路径 1：小米超级岛
        if (vendor == Vendor.XIAOMI && miuiFocusProtocol in 2..3) {
            runCatching {
                val notif = buildXiaomiIslandNotification(text, safeCurrent, safeTotal, indeterminate, jobBrief, pi)
                AppLog.d("IslandNotifier", "使用小米超级岛通知")
                return notif
            }.onFailure { tried += "xiaomi:${it.message}" }
        }

        // 路径 2：vivo 原子岛
        if (vendor == Vendor.VIVO) {
            runCatching {
                val notif = buildVivoAtomicIslandNotification(text, safeCurrent, safeTotal, indeterminate, jobBrief, pi)
                AppLog.d("IslandNotifier", "使用 vivo 原子岛通知")
                return notif
            }.onFailure { tried += "vivo:${it.message}" }
        }

        // 路径 3：OPPO/一加 灵动岛
        if (vendor == Vendor.OPPO) {
            runCatching {
                val notif = buildOppoLiveAlertNotification(text, safeCurrent, safeTotal, indeterminate, pi)
                AppLog.d("IslandNotifier", "使用 OPPO 灵动岛通知")
                return notif
            }.onFailure { tried += "oppo:${it.message}" }
        }

        // 路径 4：荣耀灵动胶囊
        if (vendor == Vendor.HONOR) {
            runCatching {
                val notif = buildHonorCapsuleNotification(text, safeCurrent, safeTotal, indeterminate, pi)
                AppLog.d("IslandNotifier", "使用荣耀灵动胶囊通知")
                return notif
            }.onFailure { tried += "honor:${it.message}" }
        }

        // 路径 5：Android 16+ ProgressStyle（通用）
        if (Build.VERSION.SDK_INT >= API_LEVEL_ANDROID_16) {
            runCatching {
                val notif = buildProgressStyleNotification(text, safeCurrent, safeTotal, indeterminate, pi)
                AppLog.d("IslandNotifier", "使用 ProgressStyle 通知")
                return notif
            }.onFailure { tried += "progressStyle:${it.message}" }
        }

        if (tried.isNotEmpty()) {
            AppLog.w("IslandNotifier", "厂商上岛路径全部失败，降级到标准通知: $tried")
        }

        // 路径 6：标准进度通知（最终兜底）
        return runCatching {
            buildStandardProgressNotification(text, safeCurrent, safeTotal, indeterminate, pi)
        }.getOrElse {
            AppLog.e("IslandNotifier", "标准通知构建也失败！用最小通知兜底", it)
            buildMinimalNotification(text, pi)
        }
    }

    fun notifyProgress(text: String, current: Int, total: Int, jobBrief: String = "") {
        runCatching {
            val notification = buildProgressNotification(text, current, total, jobBrief)
            notificationManager.notify(progressNotificationId, notification)
        }.onFailure {
            AppLog.e("IslandNotifier", "notifyProgress 失败", it)
        }
    }

    fun notifyDone(title: String, text: String) {
        runCatching {
            notificationManager.cancel(progressNotificationId)
            val pi = PendingIntent.getActivity(
                context, 1,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // 完成通知优先走厂商上岛 done 模板，再降级
            val builtNotif = when {
                vendor == Vendor.XIAOMI && miuiFocusProtocol in 2..3 ->
                    runCatching { buildXiaomiIslandDoneNotification(title, text, pi) }
                        .getOrElse { buildStandardDoneNotification(title, text, pi) }
                vendor == Vendor.VIVO ->
                    runCatching { buildVivoAtomicIslandDoneNotification(title, text, pi) }
                        .getOrElse { buildStandardDoneNotification(title, text, pi) }
                vendor == Vendor.OPPO ->
                    runCatching { buildOppoLiveAlertDoneNotification(title, text, pi) }
                        .getOrElse { buildStandardDoneNotification(title, text, pi) }
                vendor == Vendor.HONOR ->
                    runCatching { buildHonorCapsuleDoneNotification(title, text, pi) }
                        .getOrElse { buildStandardDoneNotification(title, text, pi) }
                else -> buildStandardDoneNotification(title, text, pi)
            }
            notificationManager.notify(resultNotificationId, builtNotif)
            AppLog.d("IslandNotifier", "done 通知已发送 (vendor=$vendor)")
        }.onFailure {
            AppLog.e("IslandNotifier", "notifyDone 失败", it)
        }
    }

    fun cancelAll() {
        notificationManager.cancel(progressNotificationId)
        notificationManager.cancel(resultNotificationId)
    }

    // ── 路径 1：小米超级岛 ────────────────────────────────────────────────────

    private fun buildXiaomiIslandNotification(
        text: String, current: Int, total: Int, indeterminate: Boolean,
        jobBrief: String, contentIntent: PendingIntent
    ): Notification {
        val percent = computePercent(current, total, indeterminate)
        val progressColor = "#FF007AFF"

        val paramV2 = JSONObject().apply {
            put("protocol", 1)
            put("business", BUSINESS_NAIGEN_GENERATION)
            put("enableFloat", true)
            put("updatable", true)
            put("ticker", text)
            put("aodTitle", context.getString(R.string.app_name) + " · 生成中")
            put("bigIslandArea", JSONObject().apply {
                put("title", context.getString(R.string.app_name))
                put("content", text)
                put("progress", JSONObject().apply {
                    put("progress", percent)
                    put("colorProgress", progressColor)
                    put("isAutoProgress", false)
                })
                put("picInfo", JSONObject().apply { put("type", 1) })
            })
            put("smallIslandArea", JSONObject().apply {
                put("title", context.getString(R.string.app_name))
                put("content", if (jobBrief.isNotBlank()) jobBrief else "$percent%")
                put("picInfo", JSONObject().apply { put("type", 1) })
            })
        }
        val focusParam = JSONObject().apply { put("param_v2", paramV2) }.toString()

        val extras = Bundle().apply { putString("miui.focus.param", focusParam) }
        return NotificationCompat.Builder(context, NaiApplication.CHANNEL_ISLAND)
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
            .addExtras(extras)
            .build()
    }

    private fun buildXiaomiIslandDoneNotification(
        title: String, text: String, contentIntent: PendingIntent
    ): Notification {
        val paramV2 = JSONObject().apply {
            put("protocol", 1)
            put("business", BUSINESS_NAIGEN_GENERATION)
            put("enableFloat", false)
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
        val focusParam = JSONObject().apply { put("param_v2", paramV2) }.toString()
        val extras = Bundle().apply { putString("miui.focus.param", focusParam) }
        return NotificationCompat.Builder(context, NaiApplication.CHANNEL_RESULT)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_app_placeholder)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addExtras(extras)
            .build()
    }

    // ── 路径 2：vivo 原子岛 ──────────────────────────────────────────────────
    //  vivo OriginOS 4+ 支持「原子岛」，通过 extras `live.window.param` 传 JSON。
    //  协议字段：businessID / floatComponentType / floatComponentData{title,content,progress}

    private fun buildVivoAtomicIslandNotification(
        text: String, current: Int, total: Int, indeterminate: Boolean,
        jobBrief: String, contentIntent: PendingIntent
    ): Notification {
        val percent = computePercent(current, total, indeterminate)
        val componentData = JSONObject().apply {
            put("title", context.getString(R.string.app_name))
            put("content", text)
            put("bottomText", if (jobBrief.isNotBlank()) jobBrief else "$percent%")
            put("progress", percent)
            put("progressColor", "#FF007AFF")
        }
        val liveParam = JSONObject().apply {
            put("businessID", BUSINESS_NAIGEN_GENERATION)
            put("floatComponentType", 1)  // 1 = 进度型
            put("floatComponentData", componentData)
        }.toString()

        val extras = Bundle().apply { putString("live.window.param", liveParam) }
        return NotificationCompat.Builder(context, NaiApplication.CHANNEL_ISLAND)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_app_placeholder)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setProgress(total, current.coerceAtMost(total), indeterminate)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addExtras(extras)
            .build()
    }

    private fun buildVivoAtomicIslandDoneNotification(
        title: String, text: String, contentIntent: PendingIntent
    ): Notification {
        val componentData = JSONObject().apply {
            put("title", title)
            put("content", text)
        }
        val liveParam = JSONObject().apply {
            put("businessID", BUSINESS_NAIGEN_GENERATION)
            put("floatComponentType", 4)  // 4 = 文本型
            put("floatComponentData", componentData)
        }.toString()
        val extras = Bundle().apply { putString("live.window.param", liveParam) }
        return NotificationCompat.Builder(context, NaiApplication.CHANNEL_RESULT)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_app_placeholder)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .addExtras(extras)
            .build()
    }

    // ── 路径 3：OPPO/一加 灵动岛（LiveAlert）──────────────────────────────────
    //  ColorOS 14+ 支持「灵动岛」，通过 extras `op.activity.scene` 传 JSON。
    //  协议字段：scene / sceneName / dynamicSceneParams{title, content, progress}

    private fun buildOppoLiveAlertNotification(
        text: String, current: Int, total: Int, indeterminate: Boolean,
        contentIntent: PendingIntent
    ): Notification {
        val percent = computePercent(current, total, indeterminate)
        val sceneParams = JSONObject().apply {
            put("title", context.getString(R.string.app_name))
            put("content", text)
            put("progress", percent)
            put("isProgress", true)
        }
        val sceneParam = JSONObject().apply {
            put("scene", "progress")
            put("sceneName", context.getString(R.string.app_name))
            put("dynamicSceneParams", sceneParams)
        }.toString()

        val extras = Bundle().apply { putString("op.activity.scene", sceneParam) }
        return NotificationCompat.Builder(context, NaiApplication.CHANNEL_ISLAND)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_app_placeholder)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setProgress(total, current.coerceAtMost(total), indeterminate)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addExtras(extras)
            .build()
    }

    private fun buildOppoLiveAlertDoneNotification(
        title: String, text: String, contentIntent: PendingIntent
    ): Notification {
        val sceneParams = JSONObject().apply {
            put("title", title)
            put("content", text)
        }
        val sceneParam = JSONObject().apply {
            put("scene", "text")
            put("sceneName", context.getString(R.string.app_name))
            put("dynamicSceneParams", sceneParams)
        }.toString()
        val extras = Bundle().apply { putString("op.activity.scene", sceneParam) }
        return NotificationCompat.Builder(context, NaiApplication.CHANNEL_RESULT)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_app_placeholder)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .addExtras(extras)
            .build()
    }

    // ── 路径 4：荣耀灵动胶囊 ────────────────────────────────────────────────
    //  MagicOS 8+ 支持「灵动胶囊」，通过 extras `magic.window.param` 传 JSON。
    //  协议字段：business / type / params{title, content, progress}

    private fun buildHonorCapsuleNotification(
        text: String, current: Int, total: Int, indeterminate: Boolean,
        contentIntent: PendingIntent
    ): Notification {
        val percent = computePercent(current, total, indeterminate)
        val params = JSONObject().apply {
            put("title", context.getString(R.string.app_name))
            put("content", text)
            put("progress", percent)
            put("isProgress", true)
        }
        val magicParam = JSONObject().apply {
            put("business", BUSINESS_NAIGEN_GENERATION)
            put("type", "progress")
            put("params", params)
        }.toString()

        val extras = Bundle().apply { putString("magic.window.param", magicParam) }
        return NotificationCompat.Builder(context, NaiApplication.CHANNEL_ISLAND)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_app_placeholder)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setProgress(total, current.coerceAtMost(total), indeterminate)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addExtras(extras)
            .build()
    }

    private fun buildHonorCapsuleDoneNotification(
        title: String, text: String, contentIntent: PendingIntent
    ): Notification {
        val params = JSONObject().apply {
            put("title", title)
            put("content", text)
        }
        val magicParam = JSONObject().apply {
            put("business", BUSINESS_NAIGEN_GENERATION)
            put("type", "text")
            put("params", params)
        }.toString()
        val extras = Bundle().apply { putString("magic.window.param", magicParam) }
        return NotificationCompat.Builder(context, NaiApplication.CHANNEL_RESULT)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_app_placeholder)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .addExtras(extras)
            .build()
    }

    // ── 路径 5：Android 16+ ProgressStyle ────────────────────────────────────

    private fun buildProgressStyleNotification(
        text: String, current: Int, total: Int, indeterminate: Boolean,
        contentIntent: PendingIntent
    ): Notification {
        val progressStyleClass = Class.forName("android.app.Notification\$ProgressStyle")
        val segmentClass = Class.forName("android.app.Notification\$ProgressStyle\$Segment")

        val progressStyle = progressStyleClass.getDeclaredConstructor().newInstance()
        val accentColor = Color.parseColor("#FF007AFF")
        val grayColor = Color.parseColor("#FFD1D1D6")
        val remaining = (total - current).coerceAtLeast(0)

        val doneSegment = segmentClass.getDeclaredConstructor(
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
        ).newInstance(current.coerceAtLeast(0), accentColor)
        val remainSegment = segmentClass.getDeclaredConstructor(
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
        ).newInstance(remaining, grayColor)

        progressStyleClass.getDeclaredMethod("setProgressSegments", List::class.java)
            .invoke(progressStyle, listOf(doneSegment, remainSegment))
        progressStyleClass.getDeclaredMethod("setProgress", Int::class.javaPrimitiveType)
            .invoke(progressStyle, current)

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

        progressStyleClass.getDeclaredMethod("setBuilder", Notification.Builder::class.java)
            .invoke(progressStyle, builder)
        return progressStyleClass.getDeclaredMethod("build").invoke(progressStyle) as Notification
    }

    // ── 路径 6：标准进度通知（最终兜底）──────────────────────────────────────

    private fun buildStandardProgressNotification(
        text: String, current: Int, total: Int, indeterminate: Boolean,
        contentIntent: PendingIntent
    ): Notification = NotificationCompat.Builder(context, NaiApplication.CHANNEL_GENERATION)
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
        .build()

    private fun buildStandardDoneNotification(
        title: String, text: String, contentIntent: PendingIntent
    ): Notification = NotificationCompat.Builder(context, NaiApplication.CHANNEL_RESULT)
        .setContentTitle(title)
        .setContentText(text)
        .setSmallIcon(R.drawable.ic_app_placeholder)
        .setContentIntent(contentIntent)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    private fun buildMinimalNotification(text: String, contentIntent: PendingIntent): Notification =
        NotificationCompat.Builder(context, NaiApplication.CHANNEL_GENERATION)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_app_placeholder)
            .setContentIntent(contentIntent)
            .build()

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

    private fun readMiuiFocusProtocol(): Int = runCatching {
        Settings.System.getInt(context.contentResolver, "notification_focus_protocol", 0)
    }.getOrDefault(0)

    private fun computePercent(current: Int, total: Int, indeterminate: Boolean): Int =
        if (indeterminate || total <= 0) 0
        else ((current.toLong() * 100) / total).toInt().coerceIn(0, 100)

    private companion object {
        const val API_LEVEL_ANDROID_16 = 36
        const val BUSINESS_NAIGEN_GENERATION = "naigen_generation"
    }
}
