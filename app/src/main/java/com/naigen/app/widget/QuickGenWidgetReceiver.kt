package com.naigen.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.naigen.app.MainActivity
import com.naigen.app.R

/**
 * 桌面小组件：一个简单的"快速生图"按钮，点击直接打开 App 并把上次的 prompt 填进去。
 *
 * 使用传统 RemoteViews 而非 Glance，原因是 Glance 在 Compose 1.0 阶段 API 仍不稳定，
 * 一个按钮的 widget 用 RemoteViews 反而更轻量稳定。
 */
class QuickGenWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_quick_gen)

            // 点击打开 MainActivity（已经会从 DataStore 读上次的 prompt 自动填入）
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_QUICK_GEN, true)
            }
            val pi = PendingIntent.getActivity(
                context, id, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)

            appWidgetManager.updateAppWidget(id, views)
        }
    }

    companion object {
        const val EXTRA_QUICK_GEN = "extra_quick_gen"

        fun forceUpdate(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, QuickGenWidgetReceiver::class.java))
            if (ids.isNotEmpty()) {
                val intent = Intent(context, QuickGenWidgetReceiver::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
