package com.naigen.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.naigen.app.ui.screen.favorites.FavoritesScreen
import com.naigen.app.ui.screen.generate.GenerateScreen
import com.naigen.app.ui.screen.history.HistoryScreen
import com.naigen.app.ui.screen.keepalive.KeepAliveScreen
import com.naigen.app.ui.screen.settings.SettingsScreen
import com.naigen.app.ui.screen.stylepicker.StylePickerScreen

/**
 * 顶部导航数据。
 */
private data class Tab(val dest: Dest, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Dest.Generate, "生成", Icons.Outlined.AutoAwesome),
    Tab(Dest.History, "历史", Icons.Outlined.History),
    Tab(Dest.Favorites, "收藏", Icons.Outlined.Bookmark),
    Tab(Dest.Styles, "风格", Icons.Outlined.Brush),
    Tab(Dest.KeepAlive, "保活", Icons.Outlined.Shield),
    Tab(Dest.Settings, "设置", Icons.Outlined.Settings)
)

@Composable
fun AppNavGraph() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp
            ) {
                TABS.forEach { tab ->
                    val selected = current?.hierarchy?.any { it.route == tab.dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(tab.dest.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Dest.Generate.route,
            modifier = Modifier.padding(inner)
        ) {
            composable(Dest.Generate.route) { GenerateScreen() }
            composable(Dest.History.route) { HistoryScreen() }
            composable(Dest.Favorites.route) { FavoritesScreen() }
            composable(Dest.Styles.route) { StylePickerScreen() }
            composable(Dest.KeepAlive.route) { KeepAliveScreen() }
            composable(Dest.Settings.route) { SettingsScreen() }
        }
    }
}
