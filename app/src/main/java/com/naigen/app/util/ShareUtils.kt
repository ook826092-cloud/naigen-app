package com.naigen.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * 系统分享工具。把生成的图片通过系统分享面板发给其他 App。
 *
 * Android 7+ 必须用 FileProvider 暴露 file:// 给其他 App。
 * 我们这里走两条路径：
 *   - 已存到相册的 content:// Uri 直接 share
 *   - 仅在 App 私有目录的文件，通过 FileProvider 转 content:// 后 share
 */
object ShareUtils {

    /**
     * 分享一段文字 + 图片（图片可选）。
     */
    fun share(context: Context, text: String, imageUri: Uri? = null) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (imageUri != null) "image/*" else "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (imageUri != null) {
                putExtra(Intent.EXTRA_STREAM, imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startActivity(Intent.createChooser(intent, "分享到…"))
    }

    /**
     * 把 App 私有目录里的图片转成可分享的 content:// Uri。
     * 需要在 AndroidManifest 里声明 FileProvider（这里直接用 androidx.core 的，
     * 因为大多数 App 默认就有；如果没有就需要补）。
     */
    fun privateFileToShareableUri(context: Context, relativePath: String): Uri? {
        val file = File(context.filesDir, relativePath)
        if (!file.exists()) return null
        return runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }.getOrNull()
    }
}
