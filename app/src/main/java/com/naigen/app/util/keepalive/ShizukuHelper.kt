package com.naigen.app.util.keepalive

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Shizuku 辅助工具。
 *
 * Shizuku 允许 App 在不 root 的情况下使用 ADB 级别权限。
 * 用户需要：
 *   1. 安装 Shizuku App（https://shizuku.rikka.app/）
 *   2. 通过 ADB 或 Root 启动 Shizuku 服务
 *   3. 在 Shizuku App 里授权本 App
 *
 * 本 App 主要用 Shizuku 做：
 *   - 启动厂商的隐藏设置 Activity（exported=false 的）
 *   - 一键加入电池优化白名单
 *
 * 因为 Shizuku 的 newProcess 是 private API，本类不直接执行 shell 命令，
 * 而是检测状态 + 引导用户授权 + 让 KeepAliveScreen 走正常 startActivity
 * （Shizuku 服务运行时，本 App 的 startActivity 已经能跳到部分隐藏 Activity）。
 */
object ShizukuHelper {

    /** Shizuku 服务是否已启动 */
    fun isRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
    }

    /** Shizuku 是否已授权本 App */
    fun isGranted(): Boolean {
        if (!isRunning()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    /** Shizuku App 是否已安装 */
    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 请求 Shizuku 权限。
     * 会弹出 Shizuku 的权限对话框。
     */
    fun requestPermission(requestCode: Int = 0) {
        if (!isRunning()) return
        try {
            if (!isGranted()) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (_: Throwable) {}
    }

    /**
     * 跳转到 Shizuku App（如果已安装），或跳转到官网下载。
     */
    fun openShizuku(context: Context) {
        val intent = if (isInstalled(context)) {
            // 跳到 Shizuku App 主页
            Intent(Intent.ACTION_MAIN).apply {
                setClassName("moe.shizuku.privileged.api", "moe.shizuku.manager.MainActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            // 跳到官网
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://shizuku.rikka.app/"))
        }
        runCatching { context.startActivity(intent) }
    }

    /**
     * 通过 Shizuku 把 App 加入电池优化白名单。
     *
     * 实现：通过 Shizuku 的 IActivityManager binder 代理调用
     * `dumpsys deviceidle whitelist +<pkg>`。
     *
     * 因为 Shizuku.newProcess 是 private API，这里用更稳定的方式：
     * 通过 IBinder 直接调 IActivityManager 的隐藏方法。
     *
     * 注意：本方法仍为占位实现，待 Shizuku 13+ 提供更稳定的公开 API 后完善。
     * 当前作为引导用户去系统设置手动操作的状态指示器。
     */
    fun addToBatteryWhitelist(context: Context): Boolean {
        if (!isGranted()) return false
        // 实际实现需要通过 ShizukuBinderWrapper 包装 IBinder
        // 这里返回 false 让 UI 引导用户去系统设置手动操作
        return false
    }
}
