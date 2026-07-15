package com.naigen.app.ui.screen.settings.docs

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.naigen.app.ui.components.GroupedList
import com.naigen.app.ui.components.ListRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocsScreen(nav: NavController) {
    val ctx = LocalContext.current
    val base = "https://github.com/ook826092-cloud/naigen-app/blob/main"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("说明文档") },
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
            Text(
                "选择语言查看 README，全部托管在 GitHub 仓库",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            GroupedList(title = "多语言文档") {
                listOf(
                    "简体中文" to "$base/README.md",
                    "English" to "$base/README.en.md",
                    "日本語" to "$base/README.ja.md",
                    "한국어" to "$base/README.ko.md",
                    "Français" to "$base/README.fr.md",
                    "Deutsch" to "$base/README.de.md",
                    "Español" to "$base/README.es.md"
                ).forEachIndexed { idx, (lang, url) ->
                    ListRow(
                        label = lang,
                        value = "README${url.substringAfterLast("/README")}",
                        isLast = idx == 6,
                        onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                        trailing = {
                            Icon(
                                Icons.Outlined.OpenInNew,
                                contentDescription = "打开",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            GroupedList(title = "其他文档") {
                ListRow(
                    label = "AI 辅助开发指南",
                    value = "AI-DEV-GUIDE.md",
                    onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$base/AI-DEV-GUIDE.md"))) },
                    trailing = {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                )
                ListRow(
                    label = "隐私政策",
                    value = "PRIVACY.md",
                    onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$base/PRIVACY.md"))) },
                    trailing = {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                )
                ListRow(
                    label = "LICENSE",
                    value = "MIT 协议全文",
                    onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$base/LICENSE"))) },
                    trailing = {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                )
                ListRow(
                    label = "工作流定义",
                    value = ".github/workflows/build.yml",
                    isLast = true,
                    onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$base/.github/workflows/build.yml"))) },
                    trailing = {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                )
            }
        }
    }
}
