package com.naigen.app.ui.screen.settings.logs

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

    var tab by remember { mutableStateOf(0) } // 0=应用日志, 1=请求日志
    var appFiles by remember { mutableStateOf(AppLog.getAppFiles()) }
    var networkFiles by remember { mutableStateOf(AppLog.getNetworkFiles()) }

    // 详情页状态：fileName + isNetwork，null 表示不显示详情页
    var detailFile by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    // 当前日志详情
    var showCurrentLog by remember { mutableStateOf(false) }
    // 长按操作
    var longPressFile by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var longPressCurrent by remember { mutableStateOf(false) }

    // 如果有详情页或当前日志，覆盖显示
    if (showCurrentLog) {
        LogDetailPage(
            title = "当前日志",
            content = AppLog.getAppEntries().joinToString("\n") { AppLog.formatAppEntry(it) },
            onBack = { showCurrentLog = false },
            onShare = {
                val text = AppLog.getAppEntries().joinToString("\n") { AppLog.formatAppEntry(it) }
                val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("logs", text))
                scope.launch { snackbarHostState.showSnackbar("已复制到剪贴板") }
            }
        )
        return
    }

    detailFile?.let { (fileName, isNet) ->
        val content = AppLog.getFileContent(fileName, isNetwork = isNet)
        LogDetailPage(
            title = fileName,
            content = if (content.length > 30000) content.takeLast(30000) else content,
            onBack = { detailFile = null },
            onShare = {
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
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日志") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回") } },
                actions = {
                    IconButton(onClick = { appFiles = AppLog.getAppFiles(); networkFiles = AppLog.getNetworkFiles() }) { Icon(Icons.Outlined.Refresh, contentDescription = "刷新") }
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
            // Tab 栏
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0; appFiles = AppLog.getAppFiles() }, text = { Text("应用日志") })
                Tab(selected = tab == 1, onClick = { tab = 1; networkFiles = AppLog.getNetworkFiles() }, text = { Text("请求日志") })
            }

            when (tab) {
                0 -> {
                    // 应用日志：文件列表
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 当前日志（实时内存缓冲）
                        item {
                            LogFileItem(
                                icon = Icons.Outlined.Description,
                                iconColor = MaterialTheme.colorScheme.primary,
                                title = "当前日志",
                                subtitle = "${AppLog.getAppEntries().size} 条 · 实时",
                                onClick = { showCurrentLog = true },
                                onLongPress = { longPressCurrent = true }
                            )
                        }
                        // 每日日志文件
                        items(appFiles, key = { it.name }) { f ->
                            val (icon, color, label) = when (f.type) {
                                "error" -> Triple(Icons.Outlined.Warning, MaterialTheme.colorScheme.error, "错误")
                                "warn" -> Triple(Icons.Outlined.Info, Color(0xFFFF9800), "警告")
                                else -> Triple(Icons.Outlined.Schedule, MaterialTheme.colorScheme.primary, "日志")
                            }
                            LogFileItem(
                                icon = icon,
                                iconColor = color,
                                title = f.name,
                                subtitle = "${formatSize(f.size)} · ${DateUtils.display(f.modified)} · $label",
                                onClick = { detailFile = Pair(f.name, false) },
                                onLongPress = { longPressFile = Pair(f.name, false) }
                            )
                        }
                    }
                }
                1 -> {
                    // 请求日志：文件列表
                    if (networkFiles.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("暂无网络请求", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(networkFiles, key = { it.name }) { f ->
                                LogFileItem(
                                    icon = Icons.Outlined.Cloud,
                                    iconColor = Color(0xFF4CAF50),
                                    title = f.name,
                                    subtitle = "${formatSize(f.size)} · ${DateUtils.display(f.modified)}",
                                    onClick = { detailFile = Pair(f.name, true) },
                                    onLongPress = { longPressFile = Pair(f.name, true) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 长按操作弹窗 — 文件
    longPressFile?.let { (fileName, isNet) ->
        AlertDialog(
            onDismissRequest = { longPressFile = null },
            title = { Text(fileName, style = MaterialTheme.typography.labelMedium) },
            text = { Text("选择操作") },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        val file = AppLog.getFile(fileName, isNetwork = isNet)
                        if (file != null) {
                            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_SUBJECT, fileName); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                            ctx.startActivity(Intent.createChooser(intent, "分享 $fileName"))
                        }
                        longPressFile = null
                    }) { Text("分享") }
                    TextButton(onClick = {
                        val file = AppLog.getFile(fileName, isNetwork = isNet)
                        if (file != null) {
                            scope.launch {
                                val ok = exportToDownloads(ctx, file, fileName)
                                snackbarHostState.showSnackbar(if (ok) "已导出到 Downloads/NaiGen/$fileName" else "导出失败")
                            }
                        }
                        longPressFile = null
                    }) { Text("导出") }
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

    // 长按操作弹窗 — 当前日志
    if (longPressCurrent) {
        AlertDialog(
            onDismissRequest = { longPressCurrent = false },
            title = { Text("当前日志") },
            text = { Text("选择操作") },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        val text = AppLog.getAppEntries().joinToString("\n") { AppLog.formatAppEntry(it) }
                        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("logs", text))
                        scope.launch { snackbarHostState.showSnackbar("已复制到剪贴板") }
                        longPressCurrent = false
                    }) { Text("复制") }
                    TextButton(onClick = {
                        AppLog.clearAppBuffer()
                        scope.launch { snackbarHostState.showSnackbar("当前日志已清空") }
                        longPressCurrent = false
                    }) { Text("清空", color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(onClick = { longPressCurrent = false }) { Text("取消") } }
        )
    }
}

// ── 文件列表项（截图风格）──────────────────────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LogFileItem(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
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

// ── 日志详情页（完整页面，不是弹窗）────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogDetailPage(
    title: String,
    content: String,
    onBack: () -> Unit,
    onShare: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回") } },
                actions = { IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, contentDescription = "分享") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Text(
                    content,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ── 工具函数 ───────────────────────────────────────────────────────────

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
        resolver.openOutputStream(uri)?.use { out -> srcFile.inputStream().use { it.copyTo(out) } } ?: return false
        true
    } catch (e: Exception) { false }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes} B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
}
