package com.naigen.app.util.keepalive

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * 手机厂商识别 + 保活 Intent 跳转。
 *
 * 覆盖国内主流 8 大厂商 + 原生 Android：
 *   - 小米 / Redmi      MIUI
 *   - 华为              EMUI / HarmonyOS
 *   - 荣耀              MagicOS（独立后自成一派）
 *   - OPPO              ColorOS
 *   - 一加              OxygenOS（已合并到 ColorOS）
 *   - vivo / iQOO       OriginOS / Funtouch OS
 *   - 三星              One UI
 *   - 魅族              Flyme
 *   - Realme            realme UI
 *   - 原生 Android      Pixel / LineageOS
 *
 * 识别策略：
 *   1. [Build.MANUFACTURER]（最可靠）
 *   2. [Build.BRAND]
 *   3. 系统属性 ro.build.characteristics / ro.miui.ui.version.name 等（通过 SystemProperties 反射，部分设备可读）
 *
 * 跳转策略：
 *   - 每个厂商提供多个候选 Intent（不同 ROM 版本入口不同），逐个 try-resolve
 *   - 全部失败时回退到系统设置主页或应用详情页
 */
object ManufacturerHelper {

    /**
     * 厂商枚举。每个枚举值包含：
     *   - 显示名（用于 UI）
 *   - 是否需要保活引导（原生 Android 不需要专门引导）
     *   - 各类保活页面的 Intent 列表
     */
    enum class Manufacturer(
        val displayName: String,
        val needsKeepAlive: Boolean,
        val tips: String
    ) {
        XIAOMI(
            "小米 / Redmi",
            true,
            "MIUI 拦截后台最狠，必须同时开启「自启动」+「省电策略：无限制」+「锁屏后不清理」"
        ),
        HUAWEI(
            "华为",
            true,
            "EMUI / HarmonyOS 需在「应用启动管理」关闭自动管理，手动允许自启动、关联启动、后台活动"
        ),
        HONOR(
            "荣耀",
            true,
            "MagicOS 与华为类似，需在「应用启动管理」关闭自动管理，三项全部手动允许"
        ),
        OPPO(
            "OPPO / 一加",
            true,
            "ColorOS 需在「自启动管理」开启，并在「耗电保护」关闭本 App 的冻结"
        ),
        ONEPLUS(
            "一加",
            true,
            "OxygenOS 已合并 ColorOS，进入「自启动管理」开启，并在「电池优化」改为不优化"
        ),
        VIVO(
            "vivo / iQOO",
            true,
            "OriginOS / Funtouch OS 需在「后台弹出活动」+「自启动」两项都开启"
        ),
        SAMSUNG(
            "三星",
            true,
            "One UI 需在「电池」→「后台使用限制」关闭「深度休眠」，并将 App 加入「从未休眠的应用」"
        ),
        MEIZU(
            "魅族",
            true,
            "Flyme 需在「后台管理」开启「保持后台运行」+「自启动」"
        ),
        REALME(
            "Realme",
            true,
            "realme UI 与 ColorOS 同源，进入「自启动管理」开启即可"
        ),
        NATIVE(
            "原生 Android",
            false,
            "Pixel / LineageOS 等只需关闭电池优化即可，无需特殊保活"
        )
    }

    /**
     * 识别当前设备厂商。
     * 多重判定：先看 Build.MANUFACTURER，再看 Build.BRAND，最后看 SystemProperties。
     */
    fun detect(): Manufacturer {
        val mfr = (Build.MANUFACTURER ?: "").lowercase().trim()
        val brand = (Build.BRAND ?: "").lowercase().trim()

        return when {
            mfr.contains("xiaomi") || brand.contains("xiaomi") || brand.contains("redmi") ||
                hasSystemProperty("ro.miui.ui.version.name") -> Manufacturer.XIAOMI

            mfr.contains("honor") || brand.contains("honor") -> Manufacturer.HONOR

            mfr.contains("huawei") || brand.contains("huawei") ||
                hasSystemProperty("ro.build.version.emui") -> Manufacturer.HUAWEI

            mfr.contains("oppo") || brand.contains("oppo") ||
                hasSystemProperty("ro.build.version.opporom") -> Manufacturer.OPPO

            mfr.contains("oneplus") || brand.contains("oneplus") -> Manufacturer.ONEPLUS

            mfr.contains("vivo") || brand.contains("vivo") ||
                hasSystemProperty("ro.vivo.os.build.display.id") -> Manufacturer.VIVO

            mfr.contains("samsung") || brand.contains("samsung") -> Manufacturer.SAMSUNG

            mfr.contains("meizu") || brand.contains("meizu") -> Manufacturer.MEIZU

            mfr.contains("realme") || brand.contains("realme") -> Manufacturer.REALME

            else -> Manufacturer.NATIVE
        }
    }

    /**
     * 反射读取 SystemProperties.get(name)，失败返回 null。
     * 不直接调 android.os.SystemProperties 因为是 hide API。
     */
    private fun hasSystemProperty(name: String): Boolean {
        return try {
            val cls = Class.forName("android.os.SystemProperties")
            val m = cls.getMethod("get", String::class.java)
            val v = m.invoke(null, name) as? String
            v != null && v.isNotBlank()
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * 保活页面类型。
     *  - AUTOSTART          自启动管理
     *  - BATTERY_OPTIMIZATION 电池优化（或厂商版的省电策略）
     *  - BACKGROUND_POPUP   后台弹出活动（vivo 专有）
     *  - APP_DETAIL         应用详情页（兜底）
     *  - NOTIFICATION       通知权限
     */
    enum class KeepAlivePage {
        AUTOSTART, BATTERY_OPTIMIZATION, BACKGROUND_POPUP, APP_DETAIL, NOTIFICATION
    }

    /**
     * 获取指定厂商、指定页面的候选 Intent 列表。
     *
     * 多个 Intent 是因为同一厂商的不同 ROM 版本入口可能不同（如 MIUI 12 vs 14）。
     * 调用方应逐个 try，第一个 resolveActivity 成功的就用。
     */
    fun intentsFor(manufacturer: Manufacturer, page: KeepAlivePage): List<Intent> {
        val result: List<Intent> = when (manufacturer) {
            Manufacturer.XIAOMI -> when (page) {
                KeepAlivePage.AUTOSTART -> listOf(
                    Intent().setComponent(ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )),
                    Intent().setComponent(ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity"
                    ))
                )
                KeepAlivePage.BATTERY_OPTIMIZATION -> listOf(
                    Intent().setComponent(ComponentName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                    )),
                    Intent().setComponent(ComponentName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity"
                    ))
                )
                KeepAlivePage.BACKGROUND_POPUP -> listOf(
                    Intent().setComponent(ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity"
                    ))
                )
                KeepAlivePage.APP_DETAIL -> listOf(appDetailIntent())
                KeepAlivePage.NOTIFICATION -> listOf(notificationIntent())
            }

            Manufacturer.HUAWEI -> when (page) {
                KeepAlivePage.AUTOSTART -> listOf(
                    Intent().setComponent(ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )),
                    Intent().setComponent(ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"
                    ))
                )
                KeepAlivePage.BATTERY_OPTIMIZATION -> listOf(
                    Intent().setComponent(ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.power.ui.HwPowerManagerActivity"
                    )),
                    Intent().setComponent(ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"
                    ))
                )
                KeepAlivePage.BACKGROUND_POPUP -> listOf(
                    Intent().setComponent(ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.addviewmonitor.AddViewMonitorActivity"
                    ))
                )
                KeepAlivePage.APP_DETAIL -> listOf(appDetailIntent())
                KeepAlivePage.NOTIFICATION -> listOf(notificationIntent())
            }

            Manufacturer.HONOR -> when (page) {
                KeepAlivePage.AUTOSTART -> listOf(
                    Intent().setComponent(ComponentName(
                        "com.hihonor.systemmanager",
                        "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )),
                    // 老版本荣耀走华为包名
                    Intent().setComponent(ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    ))
                )
                KeepAlivePage.BATTERY_OPTIMIZATION -> listOf(
                    Intent().setComponent(ComponentName(
                        "com.hihonor.systemmanager",
                        "com.hihonor.systemmanager.power.ui.HwPowerManagerActivity"
                    )),
                    Intent().setComponent(ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.power.ui.HwPowerManagerActivity"
                    ))
                )
                KeepAlivePage.BACKGROUND_POPUP -> listOf(
                    Intent().setComponent(ComponentName(
                        "com.hihonor.systemmanager",
                        "com.hihonor.systemmanager.addviewmonitor.AddViewMonitorActivity"
                    ))
                )
                KeepAlivePage.APP_DETAIL -> listOf(appDetailIntent())
                KeepAlivePage.NOTIFICATION -> listOf(notificationIntent())
            }

            Manufacturer.OPPO -> when (page) {
                KeepAlivePage.AUTOSTART -> listOf(
                    Intent().setComponent(ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )),
                    Intent().setComponent(ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"
                    )),
                    Intent().setComponent(ComponentName(
                        "com.oppo.safe",
                        "com.oppo.safe.permission.startup.StartupAppListActivity"
                    ))
                )
                KeepAlivePage.BATTERY_OPTIMIZATION -> listOf(
                    Intent().setComponent(ComponentName(
                        "com.coloros.oppoguardelf",
                        "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"
                    )),
                    Intent().setComponent(ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.powerinfo.PowerUsageModelActivity"
                    ))
                )
                KeepAlivePage.BACKGROUND_POPUP -> listOf(
                    Intent().setComponent(ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.floatwindow.FloatWindowListActivity"
                    ))
                )
                KeepAlivePage.APP_DETAIL -> listOf(appDetailIntent())
                KeepAlivePage.NOTIFICATION -> listOf(notificationIntent())
            }

            Manufacturer.ONEPLUS -> when (page) {
                KeepAlivePage.AUTOSTART -> listOf(
                    // 一加 11+ 已合并到 ColorOS 包名
                    Intent().setComponent(ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )),
                    Intent().setComponent(ComponentName(
                        "com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                    ))
                )
                KeepAlivePage.BATTERY_OPTIMIZATION -> listOf(
                    Intent().setComponent(ComponentName(
                        "com.coloros.oppoguardelf",
                        "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"
                    )),
                    batteryOptimizationIntent()
                )
                KeepAlivePage.BACKGROUND_POPUP -> listOf(
                    Intent().setComponent(ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.floatwindow.FloatWindowListActivity"
                    ))
                )
                KeepAlivePage.APP_DETAIL -> listOf(appDetailIntent())
                KeepAlivePage.NOTIFICATION -> listOf(notificationIntent())
            }

            Manufacturer.VIVO -> when (page) {
                KeepAlivePage.AUTOSTART -> listOf(
                    Intent().setComponent(ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )),
                    Intent().setComponent(ComponentName(
                        "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                    ))
                )
                KeepAlivePage.BATTERY_OPTIMIZATION -> listOf(
                    Intent().setComponent(ComponentName(
                        "com.vivo.abe",
                        "com.vivo.abe.reservation.FlightModeActivity"
                    )),
                    Intent().setComponent(ComponentName(
                        "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
                    )),
                    batteryOptimizationIntent()
                )
                KeepAlivePage.BACKGROUND_POPUP -> listOf(
                    Intent().setComponent(ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    ))
                )
                KeepAlivePage.APP_DETAIL -> listOf(appDetailIntent())
                KeepAlivePage.NOTIFICATION -> listOf(notificationIntent())
            }

            Manufacturer.SAMSUNG -> when (page) {
                KeepAlivePage.AUTOSTART -> listOf(
                    Intent().setComponent(ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.lool.activity.applist.AppListActivity"
                    )),
                    Intent().setComponent(ComponentName(
                        "com.samsung.android.sm",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"
                    ))
                )
                KeepAlivePage.BATTERY_OPTIMIZATION -> listOf(
                    Intent().setComponent(ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.lool.activity.applist.AppListActivity"
                    )),
                    batteryOptimizationIntent()
                )
                KeepAlivePage.BACKGROUND_POPUP -> emptyList<Intent>()
                KeepAlivePage.APP_DETAIL -> listOf(appDetailIntent())
                KeepAlivePage.NOTIFICATION -> listOf(notificationIntent())
            }

            Manufacturer.MEIZU -> when (page) {
                KeepAlivePage.AUTOSTART -> listOf(
                    Intent().setComponent(ComponentName(
                        "com.meizu.safe",
                        "com.meizu.safe.permission.SmartBGActivity"
                    )),
                    Intent().setComponent(ComponentName(
                        "com.meizu.safe",
                        "com.meizu.safe.permission.PermissionMainActivity"
                    ))
                )
                KeepAlivePage.BATTERY_OPTIMIZATION -> listOf(
                    Intent().setComponent(ComponentName(
                        "com.meizu.safe",
                        "com.meizu.safe.powerui.PowerAppPermissionActivity"
                    )),
                    batteryOptimizationIntent()
                )
                KeepAlivePage.BACKGROUND_POPUP -> listOf(
                    Intent().setComponent(ComponentName(
                        "com.meizu.safe",
                        "com.meizu.safe.permission.FloatWindowActivity"
                    ))
                )
                KeepAlivePage.APP_DETAIL -> listOf(appDetailIntent())
                KeepAlivePage.NOTIFICATION -> listOf(notificationIntent())
            }

            Manufacturer.REALME -> when (page) {
                KeepAlivePage.AUTOSTART -> listOf(
                    Intent().setComponent(ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )),
                    Intent().setComponent(ComponentName(
                        "com.oplus.safe",
                        "com.oplus.safe.permission.startup.StartupAppListActivity"
                    ))
                )
                KeepAlivePage.BATTERY_OPTIMIZATION -> listOf(
                    Intent().setComponent(ComponentName(
                        "com.coloros.oppoguardelf",
                        "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"
                    )),
                    batteryOptimizationIntent()
                )
                KeepAlivePage.BACKGROUND_POPUP -> listOf(
                    Intent().setComponent(ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.floatwindow.FloatWindowListActivity"
                    ))
                )
                KeepAlivePage.APP_DETAIL -> listOf(appDetailIntent())
                KeepAlivePage.NOTIFICATION -> listOf(notificationIntent())
            }

            Manufacturer.NATIVE -> when (page) {
                KeepAlivePage.AUTOSTART -> emptyList<Intent>()
                KeepAlivePage.BATTERY_OPTIMIZATION -> listOf(batteryOptimizationIntent())
                KeepAlivePage.BACKGROUND_POPUP -> emptyList<Intent>()
                KeepAlivePage.APP_DETAIL -> listOf(appDetailIntent())
                KeepAlivePage.NOTIFICATION -> listOf(notificationIntent())
            }
        }
        return result
    }

    /**
     * 调起指定页面。返回 true 表示成功跳转，false 表示所有候选都失败。
     *
     * 调用方应在 false 时给用户提示「未识别到该 ROM 的设置入口，请手动到设置中查找」。
     */
    fun launch(activity: Activity, manufacturer: Manufacturer, page: KeepAlivePage): Boolean {
        val intents = intentsFor(manufacturer, page)
        for (intent in intents) {
            if (!tryResolve(activity, intent)) continue
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val ok = runCatching { activity.startActivity(intent) }.isSuccess
            if (ok) return true
        }
        return false
    }

    private fun tryResolve(ctx: Context, intent: Intent): Boolean {
        return runCatching {
            val pm = ctx.packageManager
            intent.resolveActivity(pm) != null
        }.getOrDefault(false)
    }

    /**
     * 系统应用详情页 Intent。所有 ROM 都支持，是兜底入口。
     */
    private fun appDetailIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", "com.naigen.app", null))

    /**
     * 系统电池优化页（Android 6+ 标准 API）。
     */
    private fun batteryOptimizationIntent(): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.fromParts("package", "com.naigen.app", null))

    /**
     * 应用通知设置页。
     */
    private fun notificationIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, "com.naigen.app")

    /**
     * 判断本 App 是否已被加入电池优化白名单（即未被电池优化限制）。
     * 用于在保活引导页显示「已开启」状态。
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations("com.naigen.app")
    }

    /**
     * 判断通知权限是否已授予（Android 13+ 需运行时申请 POST_NOTIFICATIONS）。
     */
    fun isNotificationGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
