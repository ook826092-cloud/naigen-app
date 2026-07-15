package com.naigen.app.ui.screen.settings.language

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavController
import androidx.compose.ui.res.stringResource
import com.naigen.app.R

/**
 * 多语言切换页。
 *
 * 用 AppCompatDelegate.setApplicationLocales 实现，Android 13+ 自动用系统 Locale API，
 * Android 12 及以下用 AppCompat 的 LocaleManager。
 *
 * 7 种语言对应 7 个 README 文档：
 *   zh / en / ja / ko / fr / de / es
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(nav: NavController) {
    val languages = remember {
        listOf(
            "system" to "跟随系统",
            "zh" to "简体中文",
            "en" to "English",
            "ja" to "日本語",
            "ko" to "한국어",
            "fr" to "Français",
            "de" to "Deutsch",
            "es" to "Español"
        )
    }

    // 当前选中的语言
    val currentLocale = AppCompatDelegate.getApplicationLocales()
    val currentTag = remember {
        if (currentLocale.isEmpty) "system"
        else currentLocale.toLanguageTags().split(",").firstOrNull()?.substringBefore("-") ?: "system"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lang_title)) },
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
                "选择 App 界面语言",
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
                languages.forEachIndexed { idx, (tag, displayName) ->
                    val isSelected = tag == currentTag
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val locales = if (tag == "system") {
                                    LocaleListCompat.getEmptyLocaleList()
                                } else {
                                    LocaleListCompat.forLanguageTags(tag)
                                }
                                AppCompatDelegate.setApplicationLocales(locales)
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = "已选",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (idx < languages.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "切换语言后 App 会自动重启以应用设置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Android 13+ 使用系统标准的多语言 API，可在系统设置中直接管理；Android 12 及以下由 AppCompat 模拟。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
