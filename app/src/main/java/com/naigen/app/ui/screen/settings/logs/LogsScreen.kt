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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.naigen.app.util.AppLog
import com.naigen.app.util.DateUtils
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.naigen.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var tab by remember { mutableStateOf(0) } // 0=实时日志, 1=日志文件
    var entries by remember { mutableStateOf(AppLog.getEntries()) }
    var logFiles by remember { mutableStateOf(AppLog.getLogFiles()) }
    var filter by remember { mutableStateOf(AppLog.Level.DEBUG) }
    var selectedFile by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    // 自动删除设置
    var maxAgeHours by remember { mutableStateOf(0) } // 0=不按时间删
    var maxSizeMB by remember { mutableStateOf(0) }   // 0=不按大小删

    val filtered = remember(entries, filter) {
        if (filter == AppLog.Level.DEBUG) entries
        else entries.filter { it.level == filter }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.logs_title)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "自动删除设置")
                    }
                    if (tab == 0) {
                        IconButton(onClick = {
                            val text = AppLog.exportAll()
                            val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                            cm?.setPrimaryClip(android.content.ClipData.newPlainText("logs", text))
                            scope.launch { snackbarHostState.showSnackbar("已复制 ${entries.size} 条日志") }
                        }) {
                            Icon(Icons.Outlined.Share, contentDescription = "导出")
                        }
                        IconButton(onClick = {
                            AppLog.clearBufferOnly()
                            entries = AppLog.getEntries()
                            scope.launch { snackbarHostState.showSnackbar("内存日志已清空") }
                        }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "清空", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        IconButton(onClick = {
                            AppLog.clearAll()
                            entries = AppLog.getEntries()
                            logFiles = AppLog.getLogFiles()
                            scope.launch { snackbarHostState.showSnackbar("所有日志已清空") }
                        }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "清空全部", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Tab 切换
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 },
                    text = { Text("实时日志 (${entries.size})") })
                Tab(selected = tab == 1, onClick = {
                    tab = 1
                    logFiles = AppLog.getLogFiles()
                }, text = { Text("日志文件 (${AppLog.getLogFileCount()})") })
            }

            if (tab == 0) {
                // ── 实时日志 ──
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChipMini("全部", filter == AppLog.Level.DEBUG) { filter = AppLog.Level.DEBUG }
                    FilterChipMini("网络", filter == AppLog.Level.NETWORK) { filter = AppLog.Level.NETWORK }
                    FilterChipMini("信息", filter == AppLog.Level.INFO) { filter = AppLog.Level.INFO }
                    FilterChipMini("警告", filter == AppLog.Level.WARN) { filter = AppLog.Level.WARN }
                    FilterChipMini("错误", filter == AppLog.Level.ERROR) { filter = AppLog.Level.ERROR }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered) { entry ->
                        LogEntryCard(entry)
                    }
                }
            } else {
                // ── 日志文件列表 ──
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "总大小: ${formatSize(AppLog.getTotalSize())} · ${logFiles.size} 个文件",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { logFiles = AppLog.getLogFiles() }) { Text("刷新") }
                }

                if (logFiles.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无日志文件", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logFiles, key = { it.name }) { logFile ->
                            LogFileCard(
                                logFile = logFile,
                                onClick = { selectedFile = logFile.name },
                                onShare = {
                                    val text = AppLog.getLogFileContent(logFile.name)
                                    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                                    cm?.setPrimaryClip(android.content.ClipData.newPlainText(logFile.name, text))
                                    scope.launch { snackbarHostState.showSnackbar("已复制 ${logFile.name}") }
                                },
                                onDelete = {
                                    AppLog.deleteFile(logFile.name)
                                    logFiles = AppLog.getLogFiles()
                                    scope.launch { snackbarHostState.showSnackbar("已删除 ${logFile.name}") }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // 文件内容查看弹窗
    selectedFile?.let { fileName ->
        val content = AppLog.getLogFileContent(fileName)
        AlertDialog(
            onDismissRequest = { selectedFile = null },
            title = { Text(fileName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    item {
                        Text(
                            content.take(5000),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                        cm?.setPrimaryClip(android.content.ClipData.newPlainText(fileName, content))
                        scope.launch { snackbarHostState.showSnackbar("已复制到剪贴板") }
                    }) { Text("复制") }
                    TextButton(onClick = { selectedFile = null }) { Text("关闭") }
                }
            }
        )
    }

    // 自动删除设置弹窗
    if (showSettings) {
        AutoDeleteSettingsDialog(
            maxAgeHours = maxAgeHours,
            maxSizeMB = maxSizeMB,
            onAgeChange = { maxAgeHours = it },
            onSizeChange = { maxSizeMB = it },
            onConfirm = {
                AppLog.maxAgeMs = if (maxAgeHours > 0) maxAgeHours * 3600_000L else 0
                AppLog.maxSizeBytes = if (maxSizeMB > 0) maxSizeMB * 1024 * 1024L else 0
                showSettings = false
                scope.launch { snackbarHostState.showSnackbar("自动删除已设置") }
            },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
private fun FilterChipMini(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LogEntryCard(entry: AppLog.Entry) {
    val levelColor = when (entry.level) {
        AppLog.Level.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
        AppLog.Level.INFO -> MaterialTheme.colorScheme.primary
        AppLog.Level.WARN -> androidx.compose.ui.graphics.Color(0xFFFF9800)
        AppLog.Level.ERROR -> MaterialTheme.colorScheme.error
        AppLog.Level.NETWORK -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
    }
    val levelText = when (entry.level) {
        AppLog.Level.DEBUG -> "D"
        AppLog.Level.INFO -> "I"
        AppLog.Level.WARN -> "W"
        AppLog.Level.ERROR -> "E"
        AppLog.Level.NETWORK -> "N"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                levelText,
                style = MaterialTheme.typography.labelSmall,
                color = androidx.compose.ui.graphics.Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(levelColor)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(DateUtils.relative(entry.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(entry.tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            entry.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
        entry.throwable?.let { t ->
            Spacer(Modifier.height(4.dp))
            Text(
                t.stackTraceToString().take(500),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LogFileCard(
    logFile: AppLog.LogFile,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var showActions by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showActions = true }
            )
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 崩溃文件用红色图标
            Icon(
                if (logFile.isCrash) Icons.Outlined.Warning else Icons.Outlined.Description,
                contentDescription = null,
                tint = if (logFile.isCrash) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    logFile.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
                Row {
                    Text(
                        DateUtils.display(logFile.createdTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatSize(logFile.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (logFile.isCrash) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "崩溃",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (showActions) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { onShare(); showActions = false }) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("导出", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = { onDelete(); showActions = false }) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                    Text("删除", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun AutoDeleteSettingsDialog(
    maxAgeHours: Int,
    maxSizeMB: Int,
    onAgeChange: (Int) -> Unit,
    onSizeChange: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val ageOptions = listOf(0 to "不按时间", 1 to "1 小时", 2 to "2 小时", 12 to "12 小时", 24 to "24 小时", 168 to "7 天", 336 to "14 天", 720 to "30 天")
    val sizeOptions = listOf(0 to "不按大小", 200 to "200 MB", 500 to "500 MB", 1024 to "1 GB", 2048 to "2 GB")
    var ageExpanded by remember { mutableStateOf(false) }
    var sizeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自动删除设置") },
        text = {
            Column {
                Text("时间逻辑", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Box {
                    OutlinedTextField(
                        value = ageOptions.find { it.first == maxAgeHours }?.second ?: "自定义",
                        onValueChange = { },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().clickable { ageExpanded = true },
                        trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null) }
                    )
                    DropdownMenu(expanded = ageExpanded, onDismissRequest = { ageExpanded = false }) {
                        ageOptions.forEach { (v, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { onAgeChange(v); ageExpanded = false })
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text("存储逻辑", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Box {
                    OutlinedTextField(
                        value = sizeOptions.find { it.first == maxSizeMB }?.second ?: "自定义",
                        onValueChange = { },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().clickable { sizeExpanded = true },
                        trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null) }
                    )
                    DropdownMenu(expanded = sizeExpanded, onDismissRequest = { sizeExpanded = false }) {
                        sizeOptions.forEach { (v, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { onSizeChange(v); sizeExpanded = false })
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "如果两个都选，按哪个先到删哪个。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes} B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
    }
}
