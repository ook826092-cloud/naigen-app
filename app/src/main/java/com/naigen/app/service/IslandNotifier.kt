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
import androidx.core.content.LocusId
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
 *   2. Android 16+ Notification.ProgressStyle（API 36+）
 *   3. 标准进度通知（老安卓 / 不支持上岛的厂商）—— 兜底，确保至少有通知
 *
 * 关键修复（v2）：
 *   - extras 在 builder 阶段用 addExtras 设置，不再在 build() 后修改（避免某些设备 extras 不可变导致崩溃）
 *   - LocusId 用正式 API setLocusId，不再手动塞 extras
 *   - buildStandardProgressNotification 也加 runCatching 保护
 *   - 整个 buildProgressNotification 加外层 runCatching 兜底
 *   - 加强诊断日志：记录 vendor、miuiFocusProtocol、走了哪个路径
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

        // 路径 1：小米超级岛（澎湃 OS 2/3）
        if (vendor == Vendor.XIAOMI && miuiFocusProtocol in 2..3) {
            runCatching {
                val notif = buildXiaomiIslandNotification(
                    text = text, current = safeCurrent, total = safeTotal,
                    indeterminate = indeterminate, jobBrief = jobBrief, contentIntent = pi
                )
                AppLog.d("IslandNotifier", "使用小米超级岛通知")
                return notif
            }.onFailure {
                AppLog.w("IslandNotifier", "小米超级岛构建失败，降级: ${it.message}")
            }
        }

        // 路径 2：Android 16+ ProgressStyle
        if (Build.VERSION.SDK_INT >= API_LEVEL_ANDROID_16) {
            runCatching {
                val notif = buildProgressStyleNotification(
                    text = text, current = safeCurrent, total = safeTotal,
                    indeterminate = indeterminate, contentIntent = pi
                )
                AppLog.d("IslandNotifier", "使用 ProgressStyle 通知")
                return notif
            }.onFailure {
                AppLog.w("IslandNotifier", "ProgressStyle 构建失败，降级: ${it.message}")
            }
        }

        // 路径 3：标准进度通知（兜底）—— 也加 runCatching 保护
        return runCatching {
            buildStandardProgressNotification(
                text = text, current = safeCurrent, total = safeTotal,
                indeterminate = indeterminate, contentIntent = pi
            )
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

            // 小米超级岛 done
            if (vendor == Vendor.XIAOMI && miuiFocusProtocol in 2..3) {
                runCatching {
                    val notif = buildXiaomiIslandDoneNotification(title, text, pi)
                    notificationManager.notify(resultNotificationId, notif)
                    AppLog.d("IslandNotifier", "done 走小米超级岛")
                    return
                }.onFailure {
                    AppLog.w("IslandNotifier", "小米超级岛 done 失败，降级: ${it.message}")
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
            AppLog.d("IslandNotifier", "done 走标准通知")
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
        val percent = if (indeterminate || total <= 0) 0
        else ((current.toLong() * 100) / total).toInt().coerceIn(0, 100)
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
        AppLog.d("IslandNotifier", "miui.focus.param (len=${focusParam.length}): $focusParam")

        // 关键修复：extras 在 builder 阶段设置，而不是 build() 后修改
        val extras = Bundle().apply {
            putString("miui.focus.param", focusParam)
        }

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
            .addExtras(extras)

        // Android 12+ 用正式 API 设置 LocusId
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setLocusId(LocusId(LOCUS_ID_GENERATION))
        }

        return builder.build()
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

        val extras = Bundle().apply {
            putString("miui.focus.param", focusParam)
        }

        val builder = NotificationCompat.Builder(context, NaiApplication.CHANNEL_RESULT)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_app_placeholder)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addExtras(extras)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setLocusId(LocusId(LOCUS_ID_GENERATION))
        }

        return builder.build()
    }

    // ── 路径 2：Android 16+ ProgressStyle ────────────────────────────────────

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

        val segmentsList = listOf(doneSegment, remainSegment)
        progressStyleClass.getDeclaredMethod("setProgressSegments", List::class.java)
            .invoke(progressStyle, segmentsList)
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
        val notification = progressStyleClass.getDeclaredMethod("build").invoke(progressStyle) as Notification
        return notification
    }

    // ── 路径 3：标准进度通知（兜底） ─────────────────────────────────────────

    private fun buildStandardProgressNotification(
        text: String, current: Int, total: Int, indeterminate: Boolean,
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setLocusId(LocusId(LOCUS_ID_GENERATION))
        }

        return builder.build()
    }

    /**
     * 最小通知（所有路径都失败时的绝对兜底）。
     * 只设置最小必需字段，确保 startForeground 不崩溃。
     */
    private fun buildMinimalNotification(text: String, contentIntent: PendingIntent): Notification {
        return NotificationCompat.Builder(context, NaiApplication.CHANNEL_GENERATION)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_app_placeholder)
            .setContentIntent(contentIntent)
            .build()
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

    private fun readMiuiFocusProtocol(): Int {
        return runCatching {
            val v = Settings.System.getInt(
                context.contentResolver,
                "notification_focus_protocol",
                0
            )
            AppLog.i("IslandNotifier", "notification_focus_protocol = $v")
            v
        }.getOrDefault(0)
    }

    private companion object {
        const val API_LEVEL_ANDROID_16 = 36
        const val LOCUS_ID_GENERATION = "naigen_generation"
        const val BUSINESS_NAIGEN_GENERATION = "naigen_generation"
    }
}
