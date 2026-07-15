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

    var tab by remember { mutableStateOf(0) } // 0=应用日志, 1=网络日志
    var appEntries by remember { mutableStateOf(AppLog.getAppEntries()) }
    var networkFiles by remember { mutableStateOf(AppLog.getNetworkFiles()) }
    var filter by remember { mutableStateOf(AppLog.Level.DEBUG) }
    var selectedNetwork by remember { mutableStateOf<AppLog.NetworkEntry?>(null) }

    val filteredApp = remember(appEntries, filter) {
        when (filter) {
            AppLog.Level.WARN -> appEntries.filter { it.level == AppLog.Level.WARN || it.level == AppLog.Level.ERROR }
            AppLog.Level.ERROR -> appEntries.filter { it.level == AppLog.Level.ERROR }
            else -> appEntries
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日志") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回") } },
                actions = {
                    IconButton(onClick = { appEntries = AppLog.getAppEntries(); networkFiles = AppLog.getNetworkFiles() }) { Icon(Icons.Outlined.Refresh, contentDescription = "刷新") }
                    IconButton(onClick = { AppLog.clearAll(); appEntries = AppLog.getAppEntries(); networkFiles = AppLog.getNetworkFiles(); scope.launch { snackbarHostState.showSnackbar("已清空") } }) { Icon(Icons.Outlined.Delete, contentDescription = "清空", tint = MaterialTheme.colorScheme.error) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("应用日志") })
                Tab(selected = tab == 1, onClick = { tab = 1; networkFiles = AppLog.getNetworkFiles() }, text = { Text("网络日志 (${networkFiles.size})") })
            }

            when (tab) {
                0 -> {
                    // 应用日志：过滤器 全部/警告/错误
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChipMini("全部", filter == AppLog.Level.DEBUG) { filter = AppLog.Level.DEBUG }
                        FilterChipMini("警告", filter == AppLog.Level.WARN) { filter = AppLog.Level.WARN }
                        FilterChipMini("错误", filter == AppLog.Level.ERROR) { filter = AppLog.Level.ERROR }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filteredApp) { entry -> AppLogEntryCard(entry) }
                    }
                }
                1 -> {
                    // 网络日志：文件列表
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${networkFiles.size} 个请求文件 · ${formatSize(AppLog.getTotalSize())}",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (networkFiles.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无网络请求", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(networkFiles, key = { it.name }) { f ->
                                NetworkFileCard(
                                    info = f,
                                    onClick = {
                                        // 找到对应的 NetworkEntry
                                        val entry = AppLog.getNetworkEntries().find { it.fileName == f.name }
                                        if (entry != null) selectedNetwork = entry
                                    },
                                    onShare = {
                                        val file = AppLog.getFile(f.name, isNetwork = true)
                                        if (file != null) {
                                            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                                            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_SUBJECT, f.name); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                                            ctx.startActivity(Intent.createChooser(intent, "分享 ${f.name}"))
                                        }
                                    },
                                    onDelete = { AppLog.deleteFile(f.name, isNetwork = true); networkFiles = AppLog.getNetworkFiles(); scope.launch { snackbarHostState.showSnackbar("已删除") } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 网络请求详情弹窗：4 个子 Tab
    selectedNetwork?.let { entry ->
        NetworkDetailDialog(entry) { selectedNetwork = null }
    }
}

@Composable
private fun NetworkDetailDialog(entry: AppLog.NetworkEntry, onDismiss: () -> Unit) {
    var subTab by remember { mutableStateOf(0) } // 0=请求头 1=请求体 2=响应头 3=响应体
    val ctx = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("${entry.method} ${entry.responseCode}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(entry.url.take(80), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (entry.durationMs > 0) Text("${entry.durationMs}ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 4 个子 Tab
                TabRow(selectedTabIndex = subTab) {
                    Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("请求头", style = MaterialTheme.typography.labelSmall) })
                    Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("请求体", style = MaterialTheme.typography.labelSmall) })
                    Tab(selected = subTab == 2, onClick = { subTab = 2 }, text = { Text("响应头", style = MaterialTheme.typography.labelSmall) })
                    Tab(selected = subTab == 3, onClick = { subTab = 3 }, text = { Text("响应体", style = MaterialTheme.typography.labelSmall) })
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    item {
                        val content = when (subTab) {
                            0 -> {
                                if (entry.requestHeaders.isEmpty()) "(空)" else entry.requestHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                            }
                            1 -> if (entry.requestBody.isBlank()) "(空)" else entry.requestBody
                            2 -> {
                                if (entry.responseHeaders.isEmpty()) "(空)" else entry.responseHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                            }
                            3 -> if (entry.responseBody.isBlank()) "(空)" else entry.responseBody
                            else -> ""
                        }
                        Text(
                            content,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    val file = AppLog.getFile(entry.fileName, isNetwork = true)
                    if (file != null) {
                        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                        ctx.startActivity(Intent.createChooser(intent, "分享"))
                    }
                }) { Text("分享 TXT") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

@Composable
private fun FilterChipMini(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AppLogEntryCard(entry: AppLog.Entry) {
    val levelColor = when (entry.level) {
        AppLog.Level.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
        AppLog.Level.INFO -> MaterialTheme.colorScheme.primary
        AppLog.Level.WARN -> androidx.compose.ui.graphics.Color(0xFFFF9800)
        AppLog.Level.ERROR -> MaterialTheme.colorScheme.error
    }
    val levelText = when (entry.level) { AppLog.Level.DEBUG -> "D", AppLog.Level.INFO -> "I", AppLog.Level.WARN -> "W", AppLog.Level.ERROR -> "E" }
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surface).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(levelText, style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(levelColor).padding(horizontal = 4.dp, vertical = 1.dp))
            Spacer(Modifier.width(6.dp))
            Text(DateUtils.relative(entry.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(entry.tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(2.dp))
        Text(entry.message, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
        entry.throwable?.let { t ->
            Spacer(Modifier.height(2.dp))
            Text(t.stackTraceToString().take(300), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun NetworkFileCard(
    info: AppLog.LogFileInfo,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var showActions by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = { showActions = true }).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(info.name, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                Row {
                    Text(DateUtils.display(info.modified), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(formatSize(info.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (showActions) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { onShare(); showActions = false }) { Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("分享") }
                TextButton(onClick = { onDelete(); showActions = false }) { Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(4.dp)); Text("删除", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes} B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
}
