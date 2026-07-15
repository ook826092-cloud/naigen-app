package com.naigen.app.ui.screen.settings.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.naigen.app.ui.components.GroupedList
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.naigen.app.ui.screen.settings.SettingsViewModel
import com.naigen.app.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.naigen.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(vm: SettingsViewModel = viewModel(), nav: NavController) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val currentMode = ThemeMode.fromKey(state.themeMode)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.theme_title) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                modifier = Modifier.windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars),
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                "选择主题模式",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                ThemeMode.entries.forEachIndexed { idx, mode ->
                    val icon = when (mode) {
                        ThemeMode.SYSTEM -> Icons.Outlined.SettingsBrightness
                        ThemeMode.LIGHT -> Icons.Outlined.LightMode
                        ThemeMode.DARK -> Icons.Outlined.DarkMode
                    }
                    ThemeOptionRow(
                        icon = icon,
                        title = mode.displayName,
                        isSelected = mode == currentMode,
                        isLast = idx == ThemeMode.entries.size - 1
                    ) {
                        scope.launch { vm.setThemeMode(mode.key) }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "切换后立即生效，无需重启 App。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun ThemeOptionRow(
    icon: ImageVector,
    title: String,
    isSelected: Boolean,
    isLast: Boolean = false,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Icon(Icons.Outlined.Check, contentDescription = "已选", tint = MaterialTheme.colorScheme.primary)
            }
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
