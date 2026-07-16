package com.naigen.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import com.naigen.app.util.AppLog

/**
 * 通知服务 —— 对标 kelivo 的 NotificationService。
 *
 * 集中管理：
 *   1. 通知渠道创建（Android 8.0+ 强制）
 *   2. Android 13+ POST_NOTIFICATIONS 运行时权限检查与请求
 *   3. 通知可用性诊断（用户报「通知不显示」时可一键导出诊断信息）
 *
 * 与 [IslandNotifier] 的分工：
 *   - NotificationService 负责「能不能发通知」（渠道 + 权限）
 *   - IslandNotifier 负责「发什么样的通知」（标准 / 灵动岛 / ProgressStyle）
 */
object NotificationService {

    private const val TAG = "NotificationService"

    /**
     * 创建所有通知渠道。应在 Application.onCreate 调用。
     *
     * 渠道设计（对标 kelivo 的单渠道 + 我们的多场景）：
     *   - CHANNEL_GENERATION (LOW)：前台服务进度，不发声，仅状态栏小图标
     *   - CHANNEL_RESULT (DEFAULT)：生成完成，发声 + 横幅
     *   - CHANNEL_ISLAND (DEFAULT, 静音)：灵动岛专用，视觉展示不发声音
     */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: run {
            AppLog.e(TAG, "NotificationManager 获取失败")
            return
        }

        // 1) 生成进度渠道（LOW，静音）
        createChannelIfNeeded(nm, Channel.GENERATION) {
            NotificationChannel(
                Channel.GENERATION.id,
                "生成进度",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示当前图像生成任务的轮询进度"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        }

        // 2) 生成结果渠道（DEFAULT，发声）
        createChannelIfNeeded(nm, Channel.RESULT) {
            NotificationChannel(
                Channel.RESULT.id,
                "生成结果",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "生成完成或失败时通知"
                enableVibration(true)
                enableLights(true)
            }
        }

        // 3) 灵动岛渠道（DEFAULT, 静音 —— 进度频繁不能吵用户）
        createChannelIfNeeded(nm, Channel.ISLAND) {
            NotificationChannel(
                Channel.ISLAND.id,
                "实时进度（灵动岛）",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "适配小米超级岛 / OPPO 流体云 / vivo 原子岛 / Android 16+ ProgressStyle"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        }

        AppLog.i(TAG, "通知渠道创建完成: ${Channel.entries.map { it.id }}")
    }

    private fun createChannelIfNeeded(
        nm: NotificationManager,
        channel: Channel,
        builder: () -> NotificationChannel
    ) {
        // 已存在则不重建，避免覆盖用户在系统设置里调过的重要性
        if (nm.getNotificationChannel(channel.id) != null) return
        runCatching { nm.createNotificationChannel(builder()) }
            .onFailure { AppLog.e(TAG, "创建渠道 ${channel.id} 失败", it) }
    }

    /**
     * 渠道标识。保持与 [com.naigen.app.NaiApplication] 的旧常量兼容。
     */
    enum class Channel(val id: String) {
        GENERATION("ch_generation"),
        RESULT("ch_result"),
        ISLAND("ch_island");

        companion object {
            fun fromId(id: String): Channel? = entries.firstOrNull { it.id == id }
        }
    }

    // ── 权限 ─────────────────────────────────────────────────────────────

    /**
     * 检查通知权限是否已授予。
     *
     * Android 13+ 需要 POST_NOTIFICATIONS 运行时权限；
     * Android 12 及以下默认有权限（只要用户没在系统设置里关掉通知）。
     */
    fun isPermissionGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // 老版本检查通知是否被用户在系统设置里整体关闭
            val nm = context.getSystemService(NotificationManager::class.java) ?: return true
            return nm.areNotificationsEnabled()
        }
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 请求 POST_NOTIFICATIONS 权限（Android 13+）。
     *
     * 对标 kelivo 的 ensureAndroidNotificationsPermission：
     *   - 已授权则跳过
     *   - 未授权则通过 [launcher] 弹系统对话框
     *   - 老版本无操作
     *
     * @param launcher Activity 注册的 RequestPermission launcher
     * @return true 表示发起了请求，false 表示已授权或老版本无需请求
     */
    fun requestPermissionIfNeeded(
        context: Context,
        launcher: ActivityResultLauncher<String>
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            AppLog.i(TAG, "API < 33，无需运行时通知权限")
            return false
        }
        if (isPermissionGranted(context)) {
            AppLog.i(TAG, "POST_NOTIFICATIONS 已授权")
            return false
        }
        AppLog.i(TAG, "POST_NOTIFICATIONS 未授权，弹出请求对话框")
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        return true
    }

    /**
     * 通知可用性诊断（用户报「通知不显示」时调用，写入日志便于排查）。
     */
    fun diagnose(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val granted = isPermissionGranted(context)
        val appEnabled = nm?.areNotificationsEnabled() ?: false
        AppLog.i(TAG, "诊断: permissionGranted=$granted, appNotificationsEnabled=$appEnabled, sdk=${Build.VERSION.SDK_INT}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            Channel.entries.forEach { ch ->
                val c = nm.getNotificationChannel(ch.id)
                AppLog.i(TAG, "  渠道 ${ch.id}: ${if (c == null) "未创建" else "importance=${c.importance}, enabled=${c.importance != NotificationManager.IMPORTANCE_NONE}"}")
            }
        }
        if (!granted) {
            AppLog.w(TAG, "通知权限未授予！用户需到 系统设置 → 应用 → NaiGen → 通知 开启")
        }
        if (!appEnabled) {
            AppLog.w(TAG, "应用通知被整体关闭！用户需到 系统设置 → 应用 → NaiGen → 通知 开启")
        }
    }
}
