package com.naigen.app

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.naigen.app.service.NotificationService
import com.naigen.app.ui.navigation.AppNavGraph
import com.naigen.app.ui.theme.NaiTheme
import com.naigen.app.ui.theme.ThemeMode
import com.naigen.app.util.AppLog

class MainActivity : AppCompatActivity() {

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            AppLog.i("MainActivity", "POST_NOTIFICATIONS 已授权")
        } else {
            AppLog.w("MainActivity", "POST_NOTIFICATIONS 被拒绝！通知将不显示，用户需到设置手动开启")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        requestNotificationPermissionIfNeeded()
        // 诊断一次通知状态，便于排查「通知不显示」类问题
        NotificationService.diagnose(this)
        setContent {
            val app = application as NaiApplication
            val themeKey by app.settingsStore.themeMode.collectAsState(initial = "system")
            val dynamicColor by app.settingsStore.dynamicColor.collectAsState(initial = true)
            val themeMode = ThemeMode.fromKey(themeKey)

            // 同步给 AppCompatDelegate（影响 Activity 重建时的主题）
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            NaiTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavGraph()
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        NotificationService.requestPermissionIfNeeded(this, notifPermLauncher)
    }

    companion object {
        /**
         * 检查通知权限是否已授予（供其他组件在生成前调用）。
         */
        fun isNotificationPermissionGranted(context: android.content.Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
            return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
    }
}
