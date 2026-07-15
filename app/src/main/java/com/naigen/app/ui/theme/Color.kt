package com.naigen.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * iOS 极简风配色：
 *   - 大面积白/浅灰，纯黑文字
 *   - 主色用 iOS 系统蓝 #007AFF（点击态、选中态、链接）
 *   - 分割线 #E5E5EA（iOS 标准 separator color）
 *   - 强调红 #FF3B30（删除、错误）
 *
 * 暗色模式镜像：纯黑底 + 浅灰文字 + iOS 蓝。
 */
object Colors {
    // Light
    val L_BG_PRIMARY = Color(0xFFFFFFFF)      // 卡片/列表背景
    val L_BG_SECONDARY = Color(0xFFF2F2F7)    // 分组背景、输入框底
    val L_BG_TERTIARY = Color(0xFFFFFFFF)
    val L_TEXT_PRIMARY = Color(0xFF000000)
    val L_TEXT_SECONDARY = Color(0xFF3C3C43).copy(alpha = 0.6f)
    val L_SEPARATOR = Color(0xFFE5E5EA)
    val L_ACCENT = Color(0xFF007AFF)
    val L_DANGER = Color(0xFFFF3B30)
    val L_SUCCESS = Color(0xFF34C759)

    // Dark
    val D_BG_PRIMARY = Color(0xFF000000)
    val D_BG_SECONDARY = Color(0xFF1C1C1E)
    val D_BG_TERTIARY = Color(0xFF2C2C2E)
    val D_TEXT_PRIMARY = Color(0xFFFFFFFF)
    val D_TEXT_SECONDARY = Color(0xFFEBEBF5).copy(alpha = 0.6f)
    val D_SEPARATOR = Color(0xFF38383A)
    val D_ACCENT = Color(0xFF0A84FF)
    val D_DANGER = Color(0xFFFF453A)
    val D_SUCCESS = Color(0xFF30D158)
}
