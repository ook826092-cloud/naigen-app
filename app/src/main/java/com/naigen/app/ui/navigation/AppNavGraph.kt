package com.naigen.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.naigen.app.ui.screen.album.AlbumScreen
import com.naigen.app.ui.screen.generate.GenerateScreen
import com.naigen.app.ui.screen.settings.SettingsScreen
import com.naigen.app.ui.screen.settings.about.AboutScreen
import com.naigen.app.ui.screen.settings.api.ApiConfigScreen
import com.naigen.app.ui.screen.settings.docs.DocsScreen
import com.naigen.app.ui.screen.settings.keepalive.KeepAliveScreen
import com.naigen.app.ui.screen.settings.language.LanguageScreen
import com.naigen.app.ui.screen.settings.styles.StyleManagerScreen

private data class Tab(val dest: Dest, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Dest.Generate, "生成", Icons.Outlined.AutoAwesome),
    Tab(Dest.Album, "相册", Icons.Outlined.PhotoLibrary),
    Tab(Dest.Settings, "设置", Icons.Outlined.Settings)
)

@Composable
fun AppNavGraph() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination

    // 底部 Tab 只在 3 个主页面显示，子页面隐藏
    val showBottomBar = current?.route in TABS.map { it.dest.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
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
        }
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Dest.Generate.route,
            modifier = Modifier.padding(inner)
        ) {
            // ── 3 个主 Tab ──
            composable(Dest.Generate.route) { GenerateScreen() }
            composable(Dest.Album.route) { AlbumScreen() }
            composable(Dest.Settings.route) { SettingsScreen(nav = nav) }

            // ── 设置子页面 ──
            composable(SubDest.ApiConfig.route) { ApiConfigScreen(nav = nav) }
            composable(SubDest.StyleManager.route) { StyleManagerScreen(nav = nav) }
            composable(SubDest.KeepAlive.route) { KeepAliveScreen(nav = nav) }
            composable(SubDest.Language.route) { LanguageScreen(nav = nav) }
            composable(SubDest.About.route) { AboutScreen(nav = nav) }
            composable(SubDest.Docs.route) { DocsScreen(nav = nav) }
        }
    }
}
