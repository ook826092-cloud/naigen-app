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
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LogsScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var tab by remember { mutableStateOf(0) } // 0=应用日志, 1=网络日志
    var appFiles by remember { mutableStateOf(AppLog.getAppFiles()) }
    var networkFiles by remember { mutableStateOf(AppLog.getNetworkFiles()) }
    var selectedFile: Pair<String, Boolean>? by remember { mutableStateOf(null) }
    var longPressFile: Pair<String, Boolean>? by remember { mutableStateOf(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日志") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回") } },
                actions = {
                    IconButton(onClick = {
                        appFiles = AppLog.getAppFiles()
                        networkFiles = AppLog.getNetworkFiles()
                    }) { Icon(Icons.Outlined.Refresh, contentDescription = "刷新") }
                    IconButton(onClick = {
                        AppLog.clearAll()
                        appFiles = AppLog.getAppFiles()
                        networkFiles = AppLog.getNetworkFiles()
                        scope.launch { snackbarHostState.showSnackbar("已清空") }
                    }) { Icon(Icons.Outlined.Delete, contentDescription = "清空", tint = MaterialTheme.colorScheme.error) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0; appFiles = AppLog.getAppFiles() }, text = { Text("应用日志") })
                Tab(selected = tab == 1, onClick = { tab = 1; networkFiles = AppLog.getNetworkFiles() }, text = { Text("网络日志 (${networkFiles.size})") })
            }

            // 总大小
            Text(
                "总大小: ${formatSize(AppLog.getTotalSize())}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            when (tab) {
                0 -> {
                    // 应用日志：文件列表
                    if (appFiles.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("暂无日志文件", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(appFiles, key = { it.name }) { f ->
                                AppLogFileCard(
                                    info = f,
                                    onClick = { selectedFile = Pair(f.name, false) },
                                    onLongPress = { longPressFile = Pair(f.name, false) }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // 网络日志：文件列表
                    if (networkFiles.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("暂无网络请求", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(networkFiles, key = { it.name }) { f ->
                                NetworkLogFileCard(
                                    info = f,
                                    onClick = {
                                        // 找到对应的 NetworkEntry
                                        val entry = AppLog.getNetworkEntries().find { it.fileName == f.name }
                                        if (entry != null) {
                                            selectedFile = Pair(f.name, true)
                                        }
                                    },
                                    onLongPress = { longPressFile = Pair(f.name, true) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 文件内容查看弹窗
    selectedFile?.let { (fileName, isNet) ->
        if (isNet) {
            // 网络日志：4 子 Tab 弹窗
            val entry = AppLog.getNetworkEntries().find { it.fileName == fileName }
            if (entry != null) {
                NetworkDetailDialog(entry) { selectedFile = null }
            } else {
                // 找不到内存条目，直接显示文件内容
                val content = remember { AppLog.getFileContent(fileName, isNetwork = true) }
                FileContentDialog(fileName, content) { selectedFile = null }
            }
        } else {
            // 应用日志：直接显示文件内容
            val content = remember { AppLog.getFileContent(fileName, isNetwork = false) }
            FileContentDialog(fileName, content.takeLast(20000)) { selectedFile = null }
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
                    // 分享
                    TextButton(onClick = {
                        val file = AppLog.getFile(fileName, isNetwork = isNet)
                        if (file != null) {
                            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, fileName)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            ctx.startActivity(Intent.createChooser(intent, "分享 $fileName"))
                        }
                        longPressFile = null
                    }) { Text("分享") }
                    // 导出（复制到 Downloads）
                    TextButton(onClick = {
                        val file = AppLog.getFile(fileName, isNetwork = isNet)
                        if (file != null) {
                            scope.launch {
                                val ok = exportToDownloads(ctx, file, fileName)
                                snackbarHostState.showSnackbar(if (ok) "已导出到 Downloads/$fileName" else "导出失败")
                            }
                        }
                        longPressFile = null
                    }) { Text("导出") }
                    // 删除
                    TextButton(onClick = {
                        AppLog.deleteFile(fileName, isNetwork = isNet)
                        appFiles = AppLog.getAppFiles()
                        networkFiles = AppLog.getNetworkFiles()
                        scope.launch { snackbarHostState.showSnackbar("已删除") }
                        longPressFile = null
                    }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(onClick = { longPressFile = null }) { Text("取消") } }
        )
    }
}

/**
 * 导出文件到 Downloads 目录（不通过分享面板）
 */
private fun exportToDownloads(ctx: android.content.Context, srcFile: File, fileName: String): Boolean {
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
        resolver.openOutputStream(uri)?.use { out ->
            srcFile.inputStream().use { it.copyTo(out) }
        } ?: return false
        true
    } catch (e: Exception) {
        false
    }
}

// ── 应用日志文件卡片 ─────────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AppLogFileCard(
    info: AppLog.LogFileInfo,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val typeIcon = when (info.type) {
        "error" -> Icons.Outlined.Warning
        "warn" -> Icons.Outlined.Info
        else -> Icons.Outlined.Description
    }
    val typeColor = when (info.type) {
        "error" -> MaterialTheme.colorScheme.error
        "warn" -> androidx.compose.ui.graphics.Color(0xFFFF9800)
        else -> MaterialTheme.colorScheme.primary
    }
    val typeLabel = when (info.type) {
        "error" -> "错误"
        "warn" -> "警告"
        "app" -> "日志"
        else -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(typeIcon, contentDescription = null, tint = typeColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(info.name, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                Row {
                    Text(DateUtils.display(info.modified), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(formatSize(info.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(typeLabel, style = MaterialTheme.typography.labelSmall, color = typeColor, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text("长按分享/导出/删除", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

// ── 网络日志文件卡片 ─────────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun NetworkLogFileCard(
    info: AppLog.LogFileInfo,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Cloud, contentDescription = null, tint = androidx.compose.ui.graphics.Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
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
        Text("长按分享/导出/删除", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes} B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
}

/**
 * 普通文件内容查看弹窗
 */
@Composable
private fun FileContentDialog(
    fileName: String,
    content: String,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(fileName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                item {
                    Text(content, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    val file = AppLog.getFile(fileName, isNetwork = false)
                    if (file != null) {
                        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, fileName)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(Intent.createChooser(intent, "分享 $fileName"))
                    }
                }) { Text("分享") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

/**
 * 网络请求详情弹窗：4 个子 Tab（请求头/请求体/响应头/响应体）
 */
@Composable
private fun NetworkDetailDialog(entry: AppLog.NetworkEntry, onDismiss: () -> Unit) {
    var subTab by remember { mutableStateOf(0) }
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
                            0 -> if (entry.requestHeaders.isEmpty()) "(空)" else entry.requestHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                            1 -> if (entry.requestBody.isBlank()) "(空)" else entry.requestBody
                            2 -> if (entry.responseHeaders.isEmpty()) "(空)" else entry.responseHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                            3 -> if (entry.responseBody.isBlank()) "(空)" else entry.responseBody
                            else -> ""
                        }
                        Text(content, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
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
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(Intent.createChooser(intent, "分享"))
                    }
                }) { Text("分享 TXT") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}
