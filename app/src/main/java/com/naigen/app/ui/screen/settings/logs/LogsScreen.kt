package com.naigen.app.ui.screen.settings.logs

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.naigen.app.util.AppLog
import com.naigen.app.util.DateUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LogsScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var tab by remember { mutableStateOf(0) }
    var appFiles by remember { mutableStateOf(AppLog.getAppFiles()) }
    var networkSummaries by remember { mutableStateOf(AppLog.getNetworkSummaries()) }
    var showCurrentLog by remember { mutableStateOf(false) }
    var detailFile: Pair<String, Boolean>? by remember { mutableStateOf(null) }
    var detailNetwork: String? by remember { mutableStateOf(null) } // fileName
    var longPressFile: Pair<String, Boolean>? by remember { mutableStateOf(null) }
    var longPressCurrent by remember { mutableStateOf(false) }
    var showAutoDelete by remember { mutableStateOf(false) }

    // 自动删除设置
    var autoDeleteEnabled by remember { mutableStateOf(false) }
    var retentionDays by remember { mutableStateOf(0) } // 0=不限
    var maxStorageMB by remember { mutableStateOf(0) } // 0=不限

    // 当前日志详情页
    if (showCurrentLog) {
        LogDetailPage("当前日志",
            AppLog.getAppEntries().joinToString("\n") { AppLog.formatAppEntry(it) },
            { showCurrentLog = false },
            {
                val text = AppLog.getAppEntries().joinToString("\n") { AppLog.formatAppEntry(it) }
                val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("logs", text))
                scope.launch { snackbarHostState.showSnackbar("已复制") }
            })
        return
    }
    // 应用日志文件详情页
    detailFile?.let { (fileName, isNet) ->
        if (!isNet) {
            val content = AppLog.getFileContent(fileName, isNetwork = false)
            LogDetailPage(fileName, content.takeLast(20000), { detailFile = null }, {
                val file = AppLog.getFile(fileName, isNetwork = false)
                if (file != null) shareFile(ctx, file, fileName)
            })
            return
        }
    }
    // 网络日志详情页（4 子 Tab）
    detailNetwork?.let { fileName ->
        NetworkDetailPage(fileName, { detailNetwork = null }, ctx, snackbarHostState, scope)
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日志") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回") } },
                actions = {
                    IconButton(onClick = { showAutoDelete = true }) { Icon(Icons.Outlined.Settings, contentDescription = "自动删除") }
                    IconButton(onClick = { appFiles = AppLog.getAppFiles(); networkSummaries = AppLog.getNetworkSummaries() }) { Icon(Icons.Outlined.Refresh, contentDescription = "刷新") }
                    IconButton(onClick = { AppLog.clearAll(); appFiles = AppLog.getAppFiles(); networkSummaries = AppLog.getNetworkSummaries(); scope.launch { snackbarHostState.showSnackbar("已清空") } }) { Icon(Icons.Outlined.Delete, contentDescription = "清空", tint = MaterialTheme.colorScheme.error) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0; appFiles = AppLog.getAppFiles() }, text = { Text("应用日志") })
                Tab(selected = tab == 1, onClick = { tab = 1; networkSummaries = AppLog.getNetworkSummaries() }, text = { Text("请求日志 (${networkSummaries.size})") })
            }

            when (tab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            LogFileItem(Icons.Outlined.Description, MaterialTheme.colorScheme.primary,
                                "当前日志", "${AppLog.getAppEntries().size} 条 · 实时",
                                { showCurrentLog = true }, { longPressCurrent = true })
                        }
                        items(appFiles, key = { it.name }) { f ->
                            val (icon, color, label) = when (f.type) {
                                "error" -> Triple(Icons.Outlined.Warning, MaterialTheme.colorScheme.error, "错误")
                                "warn" -> Triple(Icons.Outlined.Info, Color(0xFFFF9800), "警告")
                                else -> Triple(Icons.Outlined.Schedule, MaterialTheme.colorScheme.primary, "日志")
                            }
                            LogFileItem(icon, color, f.name,
                                "${formatSize(f.size)} · ${DateUtils.display(f.modified)} · $label",
                                { detailFile = Pair(f.name, false) }, { longPressFile = Pair(f.name, false) })
                        }
                    }
                }
                1 -> {
                    if (networkSummaries.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("暂无网络请求", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(networkSummaries, key = { it.fileName }) { s ->
                                NetworkSummaryItem(s,
                                    { detailNetwork = s.fileName },
                                    { longPressFile = Pair(s.fileName, true) })
                            }
                        }
                    }
                }
            }
        }
    }

    // 长按操作弹窗
    longPressFile?.let { (fileName, isNet) ->
        AlertDialog(
            onDismissRequest = { longPressFile = null },
            title = { Text(fileName, style = MaterialTheme.typography.labelMedium) },
            text = { Text("选择操作") },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        val file = AppLog.getFile(fileName, isNetwork = isNet)
                        if (file != null) shareFile(ctx, file, fileName)
                        longPressFile = null
                    }) { Text("分享") }
                    TextButton(onClick = {
                        val file = AppLog.getFile(fileName, isNetwork = isNet)
                        if (file != null) { scope.launch { val ok = exportToDownloads(ctx, file, fileName); snackbarHostState.showSnackbar(if (ok) "已导出" else "导出失败") } }
                        longPressFile = null
                    }) { Text("导出") }
                    TextButton(onClick = {
                        AppLog.deleteFile(fileName, isNetwork = isNet)
                        appFiles = AppLog.getAppFiles(); networkSummaries = AppLog.getNetworkSummaries()
                        scope.launch { snackbarHostState.showSnackbar("已删除") }
                        longPressFile = null
                    }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(onClick = { longPressFile = null }) { Text("取消") } }
        )
    }
    if (longPressCurrent) {
        AlertDialog(
            onDismissRequest = { longPressCurrent = false },
            title = { Text("当前日志") }, text = { Text("选择操作") },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        val text = AppLog.getAppEntries().joinToString("\n") { AppLog.formatAppEntry(it) }
                        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("logs", text))
                        scope.launch { snackbarHostState.showSnackbar("已复制") }
                        longPressCurrent = false
                    }) { Text("复制") }
                    TextButton(onClick = { AppLog.clearAppBuffer(); longPressCurrent = false; scope.launch { snackbarHostState.showSnackbar("已清空") } }) { Text("清空", color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(onClick = { longPressCurrent = false }) { Text("取消") } }
        )
    }

    // 自动删除设置弹窗
    if (showAutoDelete) {
        AutoDeleteDialog(
            enabled = autoDeleteEnabled,
            retentionDays = retentionDays,
            maxStorageMB = maxStorageMB,
            onEnabledChange = { autoDeleteEnabled = it },
            onRetentionChange = { retentionDays = it },
            onStorageChange = { maxStorageMB = it },
            onConfirm = {
                if (autoDeleteEnabled) {
                    AppLog.maxAgeMs = if (retentionDays > 0) retentionDays * 24 * 3600 * 1000L else 0
                    AppLog.maxSizeBytes = if (maxStorageMB > 0) maxStorageMB * 1024 * 1024L else 0
                } else {
                    AppLog.maxAgeMs = 0
                    AppLog.maxSizeBytes = 0
                }
                showAutoDelete = false
                scope.launch { snackbarHostState.showSnackbar("自动删除已设置") }
            },
            onDismiss = { showAutoDelete = false }
        )
    }
}

// ── 网络日志概览列表项 ─────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun NetworkSummaryItem(
    summary: AppLog.NetworkSummary,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val okColor = if (summary.responseCode in 200..299) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(summary.method, style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(okColor).padding(horizontal = 6.dp, vertical = 2.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(summary.url.take(60), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 1)
            Row {
                Text(DateUtils.relative(summary.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (summary.responseCode > 0) { Spacer(Modifier.width(6.dp)); Text("→ ${summary.responseCode}", style = MaterialTheme.typography.labelSmall, color = okColor) }
                if (summary.durationMs > 0) { Spacer(Modifier.width(6.dp)); Text("${summary.durationMs}ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

// ── 网络日志详情页（4 子 Tab）──────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkDetailPage(
    fileName: String,
    onBack: () -> Unit,
    ctx: android.content.Context,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope
) {
    var subTab by remember { mutableStateOf(0) }
    val summary = remember { AppLog.getNetworkSummaries().find { it.fileName == fileName } }
    val detail = remember { AppLog.getNetworkFileDetail(fileName) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${summary?.method ?: ""} ${summary?.responseCode ?: 0}", style = MaterialTheme.typography.labelMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回") } },
                actions = {
                    IconButton(onClick = {
                        val file = AppLog.getFile(fileName, isNetwork = true)
                        if (file != null) shareFile(ctx, file, fileName)
                    }) { Icon(Icons.Outlined.Share, contentDescription = "分享") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // 概览
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("URL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(summary?.url ?: "", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                Row {
                    Text("时间: ${summary?.let { DateUtils.display(it.timestamp) } ?: ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text("耗时: ${summary?.durationMs ?: 0}ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            // 4 Tab
            TabRow(selectedTabIndex = subTab) {
                Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("请求头", style = MaterialTheme.typography.labelSmall) })
                Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("请求体", style = MaterialTheme.typography.labelSmall) })
                Tab(selected = subTab == 2, onClick = { subTab = 2 }, text = { Text("响应头", style = MaterialTheme.typography.labelSmall) })
                Tab(selected = subTab == 3, onClick = { subTab = 3 }, text = { Text("响应体", style = MaterialTheme.typography.labelSmall) })
            }
            val content = when (subTab) {
                0 -> detail.requestHeaders
                1 -> detail.requestBody
                2 -> detail.responseHeaders
                3 -> detail.responseBody
                else -> ""
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentPadding = PaddingValues(16.dp)
            ) {
                item { Text(content, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface) }
            }
        }
    }
}

// ── 自动删除设置弹窗 ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoDeleteDialog(
    enabled: Boolean,
    retentionDays: Int,
    maxStorageMB: Int,
    onEnabledChange: (Boolean) -> Unit,
    onRetentionChange: (Int) -> Unit,
    onStorageChange: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dayOptions = listOf(0 to "不限制", 1 to "1 天", 3 to "3 天", 7 to "7 天", 14 to "14 天", 30 to "30 天")
    val storageOptions = listOf(0 to "不限制", 100 to "100 MB", 200 to "200 MB", 500 to "500 MB", 1024 to "1 GB", 2048 to "2 GB")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自动删除设置") },
        text = {
            Column {
                // 总开关
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用自动删除", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = onEnabledChange)
                }
                if (enabled) {
                    Spacer(Modifier.height(12.dp))
                    Text("时间逻辑", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    com.naigen.app.ui.components.DropdownSelector(
                        label = "保留天数",
                        options = dayOptions.map { it.second },
                        selectedIndex = dayOptions.indexOfFirst { it.first == retentionDays }.coerceAtLeast(0),
                        onSelected = { idx -> onRetentionChange(dayOptions[idx].first) }
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("存储逻辑", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    com.naigen.app.ui.components.DropdownSelector(
                        label = "最大存储",
                        options = storageOptions.map { it.second },
                        selectedIndex = storageOptions.indexOfFirst { it.first == maxStorageMB }.coerceAtLeast(0),
                        onSelected = { idx -> onStorageChange(storageOptions[idx].first) }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("两个都选时按存储优先执行", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ── 应用日志文件列表项 ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LogFileItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

// ── 日志详情页 ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogDetailPage(title: String, content: String, onBack: () -> Unit, onShare: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回") } },
                actions = { IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, contentDescription = "分享") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
            contentPadding = PaddingValues(16.dp)
        ) {
            item { Text(content, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface) }
        }
    }
}

// ── 工具函数 ───────────────────────────────────────────────────────────

private fun shareFile(ctx: android.content.Context, file: java.io.File, fileName: String) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_SUBJECT, fileName); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    ctx.startActivity(Intent.createChooser(intent, "分享 $fileName"))
}

private fun exportToDownloads(ctx: android.content.Context, srcFile: java.io.File, fileName: String): Boolean {
    return try {
        val resolver = ctx.contentResolver
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/NaiGen")
            }
        }
        val collection = android.provider.MediaStore.Files.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return false
        resolver.openOutputStream(uri)?.use { out -> srcFile.inputStream().use { it.copyTo(out) } } ?: return false
        true
    } catch (e: Exception) { false }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes} B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
}
