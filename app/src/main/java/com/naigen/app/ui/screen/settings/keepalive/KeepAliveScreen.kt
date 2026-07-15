package com.naigen.app.ui.screen.settings.keepalive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import com.naigen.app.ui.components.GroupedList
import com.naigen.app.ui.components.ListRow
import com.naigen.app.util.keepalive.ManufacturerHelper
import com.naigen.app.util.keepalive.ManufacturerHelper.KeepAlivePage
import com.naigen.app.util.keepalive.ManufacturerHelper.Manufacturer

data class KeepAliveState(
    val manufacturer: Manufacturer,
    val batteryOptimized: Boolean,
    val notificationGranted: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeepAliveScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("后台保活") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp)
            ) {
                // 设备识别卡（带文字图标）
                DeviceCard(state.manufacturer)

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

                if (state.manufacturer.needsKeepAlive) {
                    KeepAliveSection("1. 自启动", "允许 App 在开机 / 后台被回收后自动拉起",
                        Icons.Outlined.PlayCircle) {
                        if (!ManufacturerHelper.launch(ctx as android.app.Activity, state.manufacturer, KeepAlivePage.AUTOSTART)) {
                            scope.launch { snackbarHostState.showSnackbar("未找到入口，请手动前往设置") }
                        }
                    }
                    KeepAliveSection(
                        "2. 电池 / 省电策略",
                        if (state.batteryOptimized) "当前：未加入白名单（生成中可能被杀）" else "当前：已加入白名单 ✓",
                        if (state.batteryOptimized) Icons.Outlined.BatterySaver else Icons.Outlined.BatteryFull,
                        done = !state.batteryOptimized
                    ) {
                        ManufacturerHelper.launch(ctx as android.app.Activity, state.manufacturer, KeepAlivePage.BATTERY_OPTIMIZATION)
                    }
                    if (state.manufacturer in listOf(
                            Manufacturer.VIVO, Manufacturer.IQOO,
                            Manufacturer.OPPO, Manufacturer.ONEPLUS, Manufacturer.REALME
                        )
                    ) {
                        KeepAliveSection("3. 后台弹出活动", "允许 App 在后台弹出通知和窗口",
                            Icons.Outlined.OpenInNew) {
                            ManufacturerHelper.launch(ctx as android.app.Activity, state.manufacturer, KeepAlivePage.BACKGROUND_POPUP)
                        }
                    }
                }

                val notifIdx = if (state.manufacturer.needsKeepAlive) {
                    if (state.manufacturer in listOf(
                            Manufacturer.VIVO, Manufacturer.IQOO,
                            Manufacturer.OPPO, Manufacturer.ONEPLUS, Manufacturer.REALME
                        )
                    ) "4" else "3"
                } else "1"
                KeepAliveSection(
                    "$notifIdx. 通知权限",
                    if (state.notificationGranted) "已开启 ✓" else "未开启（无法收到生成进度通知）",
                    Icons.Outlined.NotificationsActive,
                    done = state.notificationGranted
                ) {
                    ManufacturerHelper.launch(ctx as android.app.Activity, state.manufacturer, KeepAlivePage.NOTIFICATION)
                }

                GroupedList(title = "兜底入口") {
                    ListRow(
                        label = "应用详情页",
                        value = "如果上面的入口都跳不过去，点这里",
                        isLast = true,
                        onClick = {
                            ManufacturerHelper.launch(ctx as android.app.Activity, state.manufacturer, KeepAlivePage.APP_DETAIL)
                        }
                    )
                }

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
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 厂商文字图标（首字 / 缩写），避免商标问题
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    manufacturer.iconText,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
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
