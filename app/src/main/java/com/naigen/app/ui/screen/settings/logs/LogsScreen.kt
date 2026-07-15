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
import androidx.compose.ui.res.stringResource
import com.naigen.app.R

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LogsScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var tab by remember { mutableStateOf(0) }
    var appFiles by remember { mutableStateOf(AppLog.getAppFiles()) }
    var networkEntries by remember { mutableStateOf(AppLog.getNetworkEntries()) }
    var networkFiles by remember { mutableStateOf(AppLog.getNetworkFiles()) }

    var detailFile: Pair<String, Boolean>? by remember { mutableStateOf(null) }
    var showCurrentLog by remember { mutableStateOf(false) }
    var detailNetwork: AppLog.NetworkEntry? by remember { mutableStateOf(null) }
    var longPressFile: Pair<String, Boolean>? by remember { mutableStateOf(null) }
    var longPressCurrent by remember { mutableStateOf(false) }
    var longPressNetwork: AppLog.NetworkEntry? by remember { mutableStateOf(null) }

    // 详情页覆盖
    if (showCurrentLog) {
        LogDetailPage("当前日志",
            AppLog.getAppEntries().joinToString("\n") { AppLog.formatAppEntry(it) },
            { showCurrentLog = false },
            {
                val text = AppLog.getAppEntries().joinToString("\n") { AppLog.formatAppEntry(it) }
                val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("logs", text))
                scope.launch { snackbarHostState.showSnackbar("已复制到剪贴板") }
            })
        return
    }
    detailFile?.let { (fileName, isNet) ->
        val content = AppLog.getFileContent(fileName, isNetwork = isNet)
        LogDetailPage(fileName,
            if (content.length > 30000) content.takeLast(30000) else content,
            { detailFile = null },
            {
                val file = AppLog.getFile(fileName, isNetwork = isNet)
                if (file != null) shareFile(ctx, file, fileName)
            })
        return
    }
    detailNetwork?.let { entry ->
        NetworkDetailPage(entry, { detailNetwork = null }, ctx, snackbarHostState, scope)
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.logs_title) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回") } },
                actions = {
                    IconButton(onClick = { appFiles = AppLog.getAppFiles(); networkFiles = AppLog.getNetworkFiles() }) { Icon(Icons.Outlined.Refresh, contentDescription = "刷新") }
                    IconButton(onClick = { AppLog.clearAll(); appFiles = AppLog.getAppFiles(); networkFiles = AppLog.getNetworkFiles(); scope.launch { snackbarHostState.showSnackbar("已清空") } }) { Icon(Icons.Outlined.Delete, contentDescription = "清空", tint = MaterialTheme.colorScheme.error) }
                },
                modifier = Modifier.windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0; appFiles = AppLog.getAppFiles() }, text = { Text(stringResource(R.string.logs_tab_app) })
                Tab(selected = tab == 1, onClick = { tab = 1; networkFiles = AppLog.getNetworkFiles() }, text = { Text("请求日志 (${networkFiles.size})") })
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
                    if (networkFiles.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.logs_no_network), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(networkFiles, key = { it.name }) { f ->
                                NetworkFileItem(
                                    name = f.name,
                                    size = f.size,
                                    modified = f.modified,
                                    onClick = {
                                        // 先从内存找 NetworkEntry（有4子Tab），找不到用文件内容
                                        val entry = AppLog.getNetworkEntries().find { it.fileName == f.name }
                                        if (entry != null) detailNetwork = entry
                                        else detailFile = Pair(f.name, true)
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

    // 长按弹窗
    longPressFile?.let { (fileName, isNet) ->
        ActionDialog(fileName, ctx, scope, snackbarHostState,
            { longPressFile = null; appFiles = AppLog.getAppFiles() },
            { longPressFile = null },
            fileName, isNet)
    }
    if (longPressCurrent) {
        AlertDialog(
            onDismissRequest = { longPressCurrent = false },
            title = { Text(stringResource(R.string.logs_current) }, text = { Text(stringResource(R.string.logs_select_action) },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        val text = AppLog.getAppEntries().joinToString("\n") { AppLog.formatAppEntry(it) }
                        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("logs", text))
                        scope.launch { snackbarHostState.showSnackbar("已复制") }
                        longPressCurrent = false
                    }) { Text(stringResource(R.string.logs_copy) }
                    TextButton(onClick = { AppLog.clearAppBuffer(); longPressCurrent = false; scope.launch { snackbarHostState.showSnackbar("已清空") } }) { Text(stringResource(R.string.logs_cleared), color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(onClick = { longPressCurrent = false }) { Text(stringResource(R.string.common_cancel) } }
        )
    }
    longPressNetwork?.let { entry ->
        AlertDialog(
            onDismissRequest = { longPressNetwork = null },
            title = { Text(entry.url.take(50), style = MaterialTheme.typography.labelMedium) },
            text = { Text(stringResource(R.string.logs_select_action) },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        val file = AppLog.getFile(entry.fileName, isNetwork = true)
                        if (file != null) shareFile(ctx, file, entry.fileName)
                        longPressNetwork = null
                    }) { Text(stringResource(R.string.logs_share) }
                    TextButton(onClick = {
                        val file = AppLog.getFile(entry.fileName, isNetwork = true)
                        if (file != null) { scope.launch { val ok = exportToDownloads(ctx, file, entry.fileName); snackbarHostState.showSnackbar(if (ok) "已导出" else "导出失败") } }
                        longPressNetwork = null
                    }) { Text(stringResource(R.string.logs_export) }
                    TextButton(onClick = { AppLog.deleteFile(entry.fileName, isNetwork = true); networkEntries = AppLog.getNetworkEntries(); longPressNetwork = null; scope.launch { snackbarHostState.showSnackbar("已删除") } }) { Text(stringResource(R.string.logs_deleted), color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(onClick = { longPressNetwork = null }) { Text(stringResource(R.string.common_cancel) } }
        )
    }
}

// ── 网络请求文件列表项 ─────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun NetworkFileItem(
    name: String,
    size: Long,
    modified: Long,
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
        Icon(Icons.Outlined.Cloud, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
            Text("${formatSize(size)} · ${DateUtils.display(modified)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

// ── 网络请求详情页（完整页面 + 4 Tab）──────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkDetailPage(
    entry: AppLog.NetworkEntry,
    onBack: () -> Unit,
    ctx: android.content.Context,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope
) {
    var subTab by remember { mutableStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${entry.method} ${entry.responseCode}", style = MaterialTheme.typography.labelMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回") } },
                actions = {
                    IconButton(onClick = {
                        val file = AppLog.getFile(entry.fileName, isNetwork = true)
                        if (file != null) shareFile(ctx, file, entry.fileName)
                    }) { Icon(Icons.Outlined.Share, contentDescription = "分享") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // 概览
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("URL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(entry.url, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                Row {
                    Text("时间: ${DateUtils.display(entry.timestamp)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text("耗时: ${entry.durationMs}ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            // 内容（代码风格：等宽字体 + 深色背景）
            val content = when (subTab) {
                0 -> if (entry.requestHeaders.isEmpty()) "(空)" else entry.requestHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                1 -> if (entry.requestBody.isBlank()) "(空)" else entry.requestBody
                2 -> if (entry.responseHeaders.isEmpty()) "(空)" else entry.responseHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                3 -> if (entry.responseBody.isBlank()) "(空)" else entry.responseBody
                else -> ""
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    Text(content, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

// ── 应用日志文件列表项 ─────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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

// ── 日志详情页（完整页面）──────────────────────────────────────────────

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
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
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

// ── 操作弹窗 ───────────────────────────────────────────────────────────

@Composable
private fun ActionDialog(
    label: String,
    ctx: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onActionDone: () -> Unit,
    onDismiss: () -> Unit,
    fileName: String,
    isNet: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label, style = MaterialTheme.typography.labelMedium) },
        text = { Text(stringResource(R.string.logs_select_action) },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    val file = AppLog.getFile(fileName, isNetwork = isNet)
                    if (file != null) shareFile(ctx, file, fileName)
                    onDismiss()
                }) { Text(stringResource(R.string.logs_share) }
                TextButton(onClick = {
                    val file = AppLog.getFile(fileName, isNetwork = isNet)
                    if (file != null) { scope.launch { val ok = exportToDownloads(ctx, file, fileName); snackbarHostState.showSnackbar(if (ok) "已导出" else "导出失败") } }
                    onDismiss()
                }) { Text(stringResource(R.string.logs_export) }
                TextButton(onClick = { AppLog.deleteFile(fileName, isNetwork = isNet); onActionDone(); onDismiss() }) { Text(stringResource(R.string.logs_deleted), color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel) } }
    )
}

// ── 工具函数 ───────────────────────────────────────────────────────────

private fun shareFile(ctx: android.content.Context, file: File, fileName: String) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_SUBJECT, fileName); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    ctx.startActivity(Intent.createChooser(intent, "分享 $fileName"))
}

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
