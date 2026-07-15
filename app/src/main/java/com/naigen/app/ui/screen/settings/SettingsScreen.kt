package com.naigen.app.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.naigen.app.ui.navigation.SubDest
import androidx.compose.ui.res.stringResource
import com.naigen.app.R

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
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 12.dp)
        )

        // ── API ──
        SettingsGroup(title = "API") {
            SettingRow(
                icon = Icons.Outlined.Key,
                title = "API 服务商",
                subtitle = if (state.token.isBlank()) "Nai2API · 未配置 Token" else "Nai2API · 已配置 (${state.token.take(8)}…)",
                isLast = true,
                onClick = { nav.navigate(SubDest.ApiConfig.route) }
            )
        }

        // ── 风格 ──
        SettingsGroup(title = "画风") {
            SettingRow(
                icon = Icons.Outlined.Palette,
                title = "风格管理",
                subtitle = "内置 7 + 社区 29 + 自定义",
                isLast = true,
                onClick = { nav.navigate(SubDest.StyleManager.route) }
            )
        }

        // ── 性能 ──
        SettingsGroup(title = "性能") {
            SettingRow(
                icon = Icons.Outlined.Shield,
                title = "后台保活",
                subtitle = "厂商识别 + 自启动配置",
                isLast = true,
                onClick = { nav.navigate(SubDest.KeepAlive.route) }
            )
        }

        // ── 关于 ──
        SettingsGroup(title = "关于") {
            SettingRow(
                icon = Icons.Outlined.Public,
                title = "语言",
                subtitle = "App 界面语言（7 种）",
                onClick = { nav.navigate(SubDest.Language.route) }
            )
            SettingRow(
                icon = Icons.Outlined.Description,
                title = "应用日志",
                subtitle = "查看运行日志和网络请求",
                onClick = { nav.navigate(SubDest.Logs.route) }
            )
            SettingRow(
                icon = Icons.Outlined.Info,
                title = "关于本应用",
                subtitle = "版本、源码、隐私政策",
                onClick = { nav.navigate(SubDest.About.route) }
            )
            SettingRow(
                icon = Icons.Outlined.MenuBook,
                title = "说明文档",
                subtitle = "GitHub 仓库 README（7 种语言）",
                isLast = true,
                onClick = { nav.navigate(SubDest.Docs.route) }
            )
        }

        Text(
            "NaiGen · ${state.lastStyleKey} · MIT License",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 16.dp),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 一个分组容器：标题 + 圆角白底卡片
 */
@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, bottom = 6.dp, top = 12.dp)
    )
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        content()
    }
}

/**
 * 一行设置项：左边 icon，中间 title + subtitle，右边箭头
 */
@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isLast: Boolean = false,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        if (!isLast) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 50.dp, end = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}
