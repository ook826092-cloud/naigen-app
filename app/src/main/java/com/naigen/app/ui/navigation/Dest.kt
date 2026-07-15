package com.naigen.app.ui.navigation

/**
 * 底部 3 个 Tab 路由。
 * 其他页面（风格管理、后台保活、关于等）通过设置页栏目跳转进入。
 */
sealed class Dest(val route: String) {
    data object Generate : Dest("generate")
    data object Album : Dest("album")
    data object Settings : Dest("settings")
}

/**
 * 设置页内的子页面路由（不在底部 Tab 显示，通过点击栏目进入）。
 */
sealed class SubDest(val route: String) {
    data object ApiConfig : SubDest("settings/api")
    data object StyleManager : SubDest("settings/styles")
    data object KeepAlive : SubDest("settings/keepalive")
    data object About : SubDest("settings/about")
    data object Docs : SubDest("settings/docs")
}
