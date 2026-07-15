package com.naigen.app.ui.navigation

/**
 * 6 个底部 Tab 路由。
 */
sealed class Dest(val route: String) {
    data object Generate : Dest("generate")
    data object History : Dest("history")
    data object Favorites : Dest("favorites")
    data object Styles : Dest("styles")
    data object KeepAlive : Dest("keepalive")
    data object Settings : Dest("settings")
}
