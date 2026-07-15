package com.naigen.app.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.naigen.app.ui.navigation.SubDest
import com.naigen.app.ui.components.GroupedList
import com.naigen.app.ui.components.ListRow

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel(), nav: NavController) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        Text(
            "设置",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 12.dp)
        )

        // ── API 配置 ──
        GroupedList(title = "API") {
            SettingItem(
                icon = Icons.Outlined.Key,
                title = "API Token",
                subtitle = if (state.token.isBlank()) "未配置" else "已配置 (${state.token.take(8)}…)",
                onClick = { nav.navigate(SubDest.ApiConfig.route) }
            )
            SettingItem(
                icon = Icons.Outlined.Info,
                title = "API 地址",
                subtitle = state.baseUrl,
                isLast = true,
                onClick = { nav.navigate(SubDest.ApiConfig.route) }
            )
        }

        // ── 风格管理 ──
        GroupedList(title = "画风") {
            SettingItem(
                icon = Icons.Outlined.Palette,
                title = "风格管理",
                subtitle = "内置 7 + 社区 29 + 自定义",
                isLast = true,
                onClick = { nav.navigate(SubDest.StyleManager.route) }
            )
        }

        // ── 后台保活 ──
        GroupedList(title = "性能") {
            SettingItem(
                icon = Icons.Outlined.Shield,
                title = "后台保活",
                subtitle = "厂商识别 + 自启动配置",
                isLast = true,
                onClick = { nav.navigate(SubDest.KeepAlive.route) }
            )
        }

        // ── 关于 ──
        GroupedList(title = "关于") {
            SettingItem(
                icon = Icons.Outlined.Info,
                title = "关于本应用",
                subtitle = "版本、源码、许可证",
                onClick = { nav.navigate(SubDest.About.route) }
            )
            SettingItem(
                icon = Icons.Outlined.MenuBook,
                title = "说明文档",
                subtitle = "GitHub 仓库 README（7 种语言）",
                isLast = true,
                onClick = { nav.navigate(SubDest.Docs.route) }
            )
        }

        Text(
            "NaiGen · v2.1.0 · MIT License",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isLast: Boolean = false,
    onClick: () -> Unit
) {
    ListRow(
        label = title,
        value = null,
        isLast = isLast,
        onClick = onClick,
        trailing = {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
    Text(
        subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 10.dp, top = -4.dp)
    )
}
