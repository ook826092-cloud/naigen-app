package com.naigen.app.ui.screen.settings.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var entries by remember { mutableStateOf(AppLog.getEntries()) }
    var filter by remember { mutableStateOf(AppLog.Level.DEBUG) }

    // 过滤后的条目
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
                    IconButton(onClick = {
                        scope.launch {
                            val text = AppLog.exportAll()
                            val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                            cm?.setPrimaryClip(android.content.ClipData.newPlainText("logs", text))
                            snackbarHostState.showSnackbar("已复制 ${entries.size} 条日志到剪贴板")
                        }
                    }) {
                        Icon(Icons.Outlined.Share, contentDescription = "导出")
                    }
                    IconButton(onClick = {
                        AppLog.clear()
                        entries = AppLog.getEntries()
                        scope.launch { snackbarHostState.showSnackbar("日志已清空") }
                    }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "清空", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // 过滤器
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip("全部", filter == AppLog.Level.DEBUG) { filter = AppLog.Level.DEBUG; entries = AppLog.getEntries() }
                FilterChip("网络", filter == AppLog.Level.NETWORK) { filter = AppLog.Level.NETWORK; entries = AppLog.getEntries() }
                FilterChip("信息", filter == AppLog.Level.INFO) { filter = AppLog.Level.INFO; entries = AppLog.getEntries() }
                FilterChip("警告", filter == AppLog.Level.WARN) { filter = AppLog.Level.WARN; entries = AppLog.getEntries() }
                FilterChip("错误", filter == AppLog.Level.ERROR) { filter = AppLog.Level.ERROR; entries = AppLog.getEntries() }
            }

            // 刷新按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${filtered.size} 条 · 持久化到 filesDir/logs/app.log",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { entries = AppLog.getEntries() }) {
                    Text("刷新")
                }
            }

            // 日志列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filtered) { entry ->
                    LogEntryCard(entry)
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
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
            Text(
                DateUtils.relative(entry.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                entry.tag,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
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
