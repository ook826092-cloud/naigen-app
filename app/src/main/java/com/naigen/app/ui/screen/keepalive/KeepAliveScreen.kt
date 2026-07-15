package com.naigen.app.ui.screen.keepalive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.naigen.app.ui.components.GroupedList
import com.naigen.app.ui.components.ListRow
import com.naigen.app.util.keepalive.ManufacturerHelper
import com.naigen.app.util.keepalive.ManufacturerHelper.KeepAlivePage
import com.naigen.app.util.keepalive.ManufacturerHelper.Manufacturer

/**
 * 保活引导页状态。ViewModel 太重，这里直接用 remember + context 计算。
 */
data class KeepAliveState(
    val manufacturer: Manufacturer,
    val batteryOptimized: Boolean,      // true = 还被电池优化限制（需要引导）
    val notificationGranted: Boolean    // true = 通知权限已开
)

@Composable
fun KeepAliveScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 状态会在 onResume 时刷新（通过 lifecycle event）
    var refreshKey by remember { mutableStateOf(0) }
    val state by remember(refreshKey) {
        derivedStateOf {
            KeepAliveState(
                manufacturer = ManufacturerHelper.detect(),
                batteryOptimized = !ManufacturerHelper.isBatteryOptimizationIgnored(ctx),
                notificationGranted = ManufacturerHelper.isNotificationGranted(ctx)
            )
        }
    }

    // 用 lifecycle observer 触发刷新
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // ── 大标题 ──
            Text(
                "后台保活",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)
            )

            // ── 设备识别卡 ──
            DeviceCard(state.manufacturer)

            // ── 厂商专属提示 ──
            if (state.manufacturer.needsKeepAlive) {
                Text(
                    state.manufacturer.tips,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            } else {
                Text(
                    "你的设备是原生 Android，只需关闭电池优化即可。本 App 在前台服务保活下可稳定运行。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            // ── 各项保活入口 ──
            if (state.manufacturer.needsKeepAlive) {
                KeepAliveSection(
                    title = "1. 自启动",
                    description = "允许 App 在开机 / 后台被回收后自动拉起",
                    icon = Icons.Outlined.PlayCircle,
                    onOpen = {
                        if (!ManufacturerHelper.launch(
                                ctx as android.app.Activity,
                                state.manufacturer,
                                KeepAlivePage.AUTOSTART
                            )
                        ) {
                            scope.launch { snackbarHostState.showSnackbar("未找到入口，请手动前往设置") }
                        }
                    }
                )
                KeepAliveSection(
                    title = "2. 电池 / 省电策略",
                    description = if (state.batteryOptimized)
                        "当前：未加入白名单（生成中可能被杀）"
                    else "当前：已加入白名单 ✓",
                    icon = if (state.batteryOptimized) Icons.Outlined.BatterySaver else Icons.Outlined.BatteryFull,
                    done = !state.batteryOptimized,
                    onOpen = {
                        ManufacturerHelper.launch(
                            ctx as android.app.Activity,
                            state.manufacturer,
                            KeepAlivePage.BATTERY_OPTIMIZATION
                        )
                    }
                )
                // vivo / OPPO 专有：后台弹窗
                if (state.manufacturer == Manufacturer.VIVO ||
                    state.manufacturer == Manufacturer.OPPO ||
                    state.manufacturer == Manufacturer.ONEPLUS ||
                    state.manufacturer == Manufacturer.REALME
                ) {
                    KeepAliveSection(
                        title = "3. 后台弹出活动",
                        description = "允许 App 在后台弹出通知和窗口",
                        icon = Icons.Outlined.OpenInNew,
                        onOpen = {
                            ManufacturerHelper.launch(
                                ctx as android.app.Activity,
                                state.manufacturer,
                                KeepAlivePage.BACKGROUND_POPUP
                            )
                        }
                    )
                }
            }

            // ── 通知权限（所有厂商通用） ──
            val notifIdx = if (state.manufacturer.needsKeepAlive) {
                if (state.manufacturer == Manufacturer.VIVO ||
                    state.manufacturer == Manufacturer.OPPO ||
                    state.manufacturer == Manufacturer.ONEPLUS ||
                    state.manufacturer == Manufacturer.REALME
                ) "4" else "3"
            } else "1"
            KeepAliveSection(
                title = "$notifIdx. 通知权限",
                description = if (state.notificationGranted) "已开启 ✓" else "未开启（无法收到生成进度通知）",
                icon = Icons.Outlined.NotificationsActive,
                done = state.notificationGranted,
                onOpen = {
                    ManufacturerHelper.launch(
                        ctx as android.app.Activity,
                        state.manufacturer,
                        KeepAlivePage.NOTIFICATION
                    )
                }
            )

            // ── 兜底入口：应用详情页 ──
            GroupedList(title = "兜底入口") {
                ListRow(
                    label = "应用详情页",
                    value = "如果上面的入口都跳不过去，点这里",
                    isLast = true,
                    onClick = {
                        ManufacturerHelper.launch(
                            ctx as android.app.Activity,
                            state.manufacturer,
                            KeepAlivePage.APP_DETAIL
                        )
                    }
                )
            }

            // ── Android 15/16 说明 ──
            GroupedList(
                title = "Android 15 / 16 特别说明",
                footer = "Android 15 起强制前台服务必须声明 foregroundServiceType，本 App 已声明 dataSync 类型。" +
                    "Android 16 进一步要求前台服务在 6 秒内显示进度通知，本 App 已通过 NotificationCompat.Builder + setProgress 实现。" +
                    "只要保活设置正确，后台生成图片将完整运行。"
            ) {
                ListRow(label = "前台服务类型", value = "dataSync")
                ListRow(label = "通知渠道", value = "ch_generation (低优) + ch_result (默认)")
                ListRow(label = "进度更新频率", value = "每 2 秒（与轮询同步）", isLast = true)
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun DeviceCard(manufacturer: Manufacturer) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "识别到设备品牌",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    manufacturer.displayName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun KeepAliveSection(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    done: Boolean = false,
    onOpen: () -> Unit
) {
    GroupedList {
        ListRow(
            label = title,
            value = if (done) "已完成" else null,
            isLast = true,
            onClick = onOpen,
            trailing = {
                Icon(
                    if (done) Icons.Outlined.CheckCircle else icon,
                    contentDescription = null,
                    tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        )
    }
}
