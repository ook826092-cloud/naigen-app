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
import androidx.annotation.DrawableRes
import com.naigen.app.R

/**
 * 手机厂商识别 + 保活 Intent 跳转。
 *
 * v2.1 改造：
 *   - 小米 / 红米 分开识别（用 Build.BRAND 而非 MANUFACTURER）
 *   - 每个厂商配文字图标（首字 / 缩写），避免商标问题
 *   - 显示名 + 跳转入口分离
 */
object ManufacturerHelper {

    /**
     * 厂商枚举。displayName 用于 UI，iconRes 是编译进 APK 的真实 logo drawable。
     *
     * 图标来源：
     *   - 9 个直接拉自 SimpleIcons (CC0 协议，可商用)
     *   - Redmi 复用小米 logo 路径 + 红米品牌红色 (#FE0000)
     *   - iQOO 复用 vivo logo 路径 + iQOO 品牌蓝色 (#1B53BC)
     *   - Realme 自绘简化 R 圆形图标（SimpleIcons 无此品牌）
     */
    enum class Manufacturer(
        val displayName: String,
        @DrawableRes val iconRes: Int,
        val needsKeepAlive: Boolean,
        val tips: String
    ) {
        XIAOMI(
            "小米",
            R.drawable.ic_manufacturer_xiaomi,
            true,
            "MIUI 拦截后台最狠，必须同时开启「自启动」+「省电策略：无限制」+「锁屏后不清理」"
        ),
        REDMI(
            "Redmi 红米",
            R.drawable.ic_manufacturer_redmi,
            true,
            "Redmi (MIUI) 与小米同源，需开启「自启动」+「省电策略：无限制」+「锁屏后不清理」"
        ),
        HUAWEI(
            "华为",
            R.drawable.ic_manufacturer_huawei,
            true,
            "EMUI / HarmonyOS 需在「应用启动管理」关闭自动管理，手动允许自启动、关联启动、后台活动"
        ),
        HONOR(
            "荣耀",
            R.drawable.ic_manufacturer_honor,
            true,
            "MagicOS 与华为类似，需在「应用启动管理」关闭自动管理，三项全部手动允许"
        ),
        OPPO(
            "OPPO",
            R.drawable.ic_manufacturer_oppo,
            true,
            "ColorOS 需在「自启动管理」开启，并在「耗电保护」关闭本 App 的冻结"
        ),
        ONEPLUS(
            "一加",
            R.drawable.ic_manufacturer_oneplus,
            true,
            "OxygenOS 已合并 ColorOS，进入「自启动管理」开启，并在「电池优化」改为不优化"
        ),
        VIVO(
            "vivo",
            R.drawable.ic_manufacturer_vivo,
            true,
            "OriginOS / Funtouch OS 需在「后台弹出活动」+「自启动」两项都开启"
        ),
        IQOO(
            "iQOO",
            R.drawable.ic_manufacturer_iqoo,
            true,
            "iQOO 与 vivo 同源（OriginOS），需在「后台弹出活动」+「自启动」两项都开启"
        ),
        SAMSUNG(
            "三星",
            R.drawable.ic_manufacturer_samsung,
            true,
            "One UI 需在「电池」→「后台使用限制」关闭「深度休眠」，并将 App 加入「从未休眠的应用」"
        ),
        MEIZU(
            "魅族",
            R.drawable.ic_manufacturer_meizu,
            true,
            "Flyme 需在「后台管理」开启「保持后台运行」+「自启动」"
        ),
        REALME(
            "Realme",
            R.drawable.ic_manufacturer_realme,
            true,
            "realme UI 与 ColorOS 同源，进入「自启动管理」开启即可"
        ),
        NATIVE(
            "原生 Android",
            R.drawable.ic_manufacturer_android,
            false,
            "Pixel / LineageOS 等只需关闭电池优化即可，无需特殊保活"
        )
    }

    /**
     * 识别当前设备厂商。
     * 用 Build.BRAND 区分小米/红米、vivo/iQOO（MANUFACTURER 都返回 "Xiaomi"/"vivo"）。
     */
    fun detect(): Manufacturer {
        val brand = (Build.BRAND ?: "").lowercase().trim()
        val mfr = (Build.MANUFACTURER ?: "").lowercase().trim()

        return when {
            // 小米 vs 红米：BRAND 区分
            brand.contains("xiaomi") && !brand.contains("redmi") -> Manufacturer.XIAOMI
            brand.contains("redmi") -> Manufacturer.REDMI

            // 荣耀 vs 华为
            brand.contains("honor") -> Manufacturer.HONOR
            brand.contains("huawei") -> Manufacturer.HUAWEI

            // 一加（BRAND 是 oneplus，不是 oppo）
            brand.contains("oneplus") -> Manufacturer.ONEPLUS
            brand.contains("oppo") -> Manufacturer.OPPO

            // vivo vs iQOO（iQOO 的 BRAND 仍是 vivo，但 model 含 iqoo）
            brand.contains("vivo") -> {
                val model = (Build.MODEL ?: "").lowercase()
                if (model.contains("iqoo")) Manufacturer.IQOO else Manufacturer.VIVO
            }

            brand.contains("samsung") -> Manufacturer.SAMSUNG
            brand.contains("meizu") -> Manufacturer.MEIZU
            brand.contains("realme") -> Manufacturer.REALME

            // 退化判定：用 MANUFACTURER + 系统属性
            else -> when {
                hasSystemProperty("ro.miui.ui.version.name") ->
                    if (brand.contains("redmi")) Manufacturer.REDMI else Manufacturer.XIAOMI
                hasSystemProperty("ro.build.version.emui") -> Manufacturer.HUAWEI
                hasSystemProperty("ro.build.version.opporom") -> Manufacturer.OPPO
                hasSystemProperty("ro.vivo.os.build.display.id") -> Manufacturer.VIVO
                else -> Manufacturer.NATIVE
            }
        }
    }

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

    enum class KeepAlivePage {
        AUTOSTART, BATTERY_OPTIMIZATION, BACKGROUND_POPUP, APP_DETAIL, NOTIFICATION
    }

    fun intentsFor(manufacturer: Manufacturer, page: KeepAlivePage): List<Intent> {
        val result: List<Intent> = when (manufacturer) {
            Manufacturer.XIAOMI, Manufacturer.REDMI -> when (page) {
                KeepAlivePage.AUTOSTART -> listOf(
                    Intent().setComponent(ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )),
                    Intent().setComponent(ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity"
                    )),
                    Intent().setComponent(ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
                    ))
                )
                KeepAlivePage.BATTERY_OPTIMIZATION -> listOf(
                    // HyperOS / MIUI 14+ 的新入口：直接进 app 详情页的省电策略
                    Intent().setComponent(ComponentName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                    )).apply {
                        putExtra("package_name", "com.naigen.app")
                        putExtra("package_label", "NaiGen")
                    },
                    // MIUI 13 入口
                    Intent().setComponent(ComponentName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity"
                    )),
                    // 系统应用详情页（一定能进，里面手动选「省电策略：无限制」）
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", "com.naigen.app", null)),
                    // 系统电池优化白名单
                    batteryOptimizationIntent()
                )
                KeepAlivePage.BACKGROUND_POPUP -> listOf(
                    Intent().setComponent(ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity"
                    )),
                    Intent().setComponent(ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
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

            Manufacturer.OPPO, Manufacturer.REALME -> when (page) {
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

            Manufacturer.VIVO, Manufacturer.IQOO -> when (page) {
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

    fun launch(activity: Activity, manufacturer: Manufacturer, page: KeepAlivePage): Boolean {
        val intents = intentsFor(manufacturer, page)
        for (intent in intents) {
            // 优先用 Shizuku 启动（可启动 exported=false 的 Activity）
            if (ShizukuHelper.isGranted()) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (ShizukuHelper.startActivity(activity, intent)) {
                    return true
                }
            }
            // Shizuku 不可用时，普通 startActivity
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

    private fun appDetailIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", "com.naigen.app", null))

    private fun batteryOptimizationIntent(): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.fromParts("package", "com.naigen.app", null))

    private fun notificationIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, "com.naigen.app")

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations("com.naigen.app")
    }

    fun isNotificationGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
