package com.naigen.app.ui.screen.settings.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.naigen.app.BuildConfig
import com.naigen.app.ui.components.GroupedList
import com.naigen.app.ui.components.ListRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(nav: NavController) {
    val ctx = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // 应用图标 + 名称
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text("N", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                Text("NaiGen", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                // 系统识别的版本号（SemVer）
                Text(
                    "v${BuildConfig.SEMVER}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 构建次数（只在关于页显示，安卓系统看不到）
                Text(
                    "构建 #${BuildConfig.BUILD_NUMBER}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Android Nai2API 文生图客户端", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // 链接区
            GroupedList(title = "源码与文档") {
                ListRow(
                    label = "GitHub 仓库",
                    value = "ook826092-cloud/naigen-app",
                    onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ook826092-cloud/naigen-app")))
                    },
                    trailing = { LinkIcon() }
                )
                ListRow(
                    label = "Releases 下载页",
                    value = "最新 APK",
                    onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ook826092-cloud/naigen-app/releases/latest")))
                    },
                    trailing = { LinkIcon() }
                )
                ListRow(
                    label = "中文说明文档",
                    value = "README.md",
                    onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ook826092-cloud/naigen-app/blob/main/README.md")))
                    },
                    trailing = { LinkIcon() }
                )
                ListRow(
                    label = "English Docs",
                    value = "README.en.md",
                    onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ook826092-cloud/naigen-app/blob/main/README.en.md")))
                    },
                    trailing = { LinkIcon() }
                )
                ListRow(
                    label = "原始教程",
                    value = "docs/Nai2API-Tutorial.md",
                    onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ook826092-cloud/naigen-app/blob/main/docs/Nai2API-Tutorial.md")))
                    },
                    trailing = { LinkIcon() }
                )
                ListRow(
                    label = "隐私政策",
                    value = "PRIVACY.md",
                    onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ook826092-cloud/naigen-app/blob/main/PRIVACY.md")))
                    },
                    trailing = { LinkIcon() }
                )
                ListRow(
                    label = "Issues 反馈",
                    value = "提交 bug 或建议",
                    isLast = true,
                    onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ook826092-cloud/naigen-app/issues")))
                    },
                    trailing = { LinkIcon() }
                )
            }

            // 技术栈
            GroupedList(title = "技术栈") {
                ListRow(label = "UI 框架", value = "Jetpack Compose + Material 3")
                ListRow(label = "网络", value = "OkHttp + kotlinx.serialization")
                ListRow(label = "数据库", value = "Room 2.6")
                ListRow(label = "签名", value = "PKCS12 keystore (GitHub Secrets)")
                ListRow(label = "最低 SDK", value = "Android 8.0 (API 26)")
                ListRow(label = "目标 SDK", value = "Android 15 (API 35)", isLast = true)
            }

            // 致谢
            GroupedList(title = "致谢") {
                ListRow(
                    label = "Nai2API 教程",
                    value = "本应用基于该教程实现",
                    isLast = true,
                    onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ook826092-cloud/naigen-app")))
                    },
                    trailing = { LinkIcon() }
                )
            }

            Text(
                "NaiGen v${BuildConfig.SEMVER} · 构建 #${BuildConfig.BUILD_NUMBER}\nMIT License · Copyright © 2026 ook826092-cloud",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 16.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LinkIcon() {
    Icon(
        Icons.Outlined.OpenInNew,
        contentDescription = "打开",
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp)
    )
}
