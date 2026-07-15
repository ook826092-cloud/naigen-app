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

    var entries by remember { mutableStateOf(AppLog.getEntries()) }
    var filter by remember { mutableStateOf(AppLog.Level.DEBUG) }
    var showFullFile by remember { mutableStateOf(false) }

    val filtered = remember(entries, filter) {
        if (filter == AppLog.Level.DEBUG) entries
        else entries.filter { it.level == filter }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("应用日志") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 分享整个日志文件
                    IconButton(onClick = {
                        val file = AppLog.getFile()
                        if (file != null && file.exists()) {
                            val uri = FileProvider.getUriForFile(
                                ctx,
                                "${ctx.packageName}.fileprovider",
                                file
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, "NaiGen app.log")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            ctx.startActivity(Intent.createChooser(shareIntent, "分享日志文件"))
                        } else {
                            scope.launch { snackbarHostState.showSnackbar("日志文件不存在") }
                        }
                    }) {
                        Icon(Icons.Outlined.Share, contentDescription = "分享日志文件")
                    }
                    // 清空
                    IconButton(onClick = {
                        AppLog.clearAll()
                        entries = AppLog.getEntries()
                        scope.launch { snackbarHostState.showSnackbar("日志已清空") }
                    }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "清空", tint = MaterialTheme.colorScheme.error)
                    }
                    // 刷新
                    IconButton(onClick = {
                        entries = AppLog.getEntries()
                    }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
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
            // 文件信息
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Description, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "app.log · ${formatSize(AppLog.getFileSize())} · ${entries.size} 条内存日志",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showFullFile = true }) { Text("查看完整文件") }
            }

            // 过滤器
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChipMini("全部", filter == AppLog.Level.DEBUG) { filter = AppLog.Level.DEBUG }
                FilterChipMini("网络", filter == AppLog.Level.NETWORK) { filter = AppLog.Level.NETWORK }
                FilterChipMini("信息", filter == AppLog.Level.INFO) { filter = AppLog.Level.INFO }
                FilterChipMini("警告", filter == AppLog.Level.WARN) { filter = AppLog.Level.WARN }
                FilterChipMini("错误", filter == AppLog.Level.ERROR) { filter = AppLog.Level.ERROR }
            }

            // 日志列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(filtered) { entry ->
                    LogEntryCard(entry)
                }
            }
        }
    }

    // 完整文件查看弹窗
    if (showFullFile) {
        val content = remember { AppLog.getFileContent() }
        AlertDialog(
            onDismissRequest = { showFullFile = false },
            title = { Text("app.log (${formatSize(AppLog.getFileSize())})", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                    item {
                        Text(
                            content.takeLast(20000),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        val file = AppLog.getFile()
                        if (file != null && file.exists()) {
                            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            ctx.startActivity(Intent.createChooser(shareIntent, "分享日志文件"))
                        }
                    }) { Text("分享") }
                    TextButton(onClick = { showFullFile = false }) { Text("关闭") }
                }
            }
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
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                levelText,
                style = MaterialTheme.typography.labelSmall,
                color = androidx.compose.ui.graphics.Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(levelColor)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(DateUtils.relative(entry.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(entry.tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            entry.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
        entry.throwable?.let { t ->
            Spacer(Modifier.height(2.dp))
            Text(
                t.stackTraceToString().take(300),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes} B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
    }
}
