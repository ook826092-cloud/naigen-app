package com.naigen.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 图片落盘工具。
 *
 * 策略：
 *   - 默认存到 App 私有 filesDir/images/YYYYMMDD_NNN.png（无需任何权限）
 *   - 用户点"保存到相册"时，通过 MediaStore 写到 Pictures/NaiGen/（Android 10+ 自动 scoped storage）
 *
 * 缩略图：固定压到 256px 宽，JPEG 80 质量，存到 history 表的 thumbBytes 字段，
 * 用于历史列表预览，避免每次都全图解码大图导致 OOM。
 */
object ImageSaver {

    private const val PRIVATE_DIR = "images"
    private const val GALLERY_DIR = "NaiGen"

    /**
     * 命名锁。[nextDatedName] 内「扫描目录取最大编号 +1」不是原子操作，
     * 并发生成 N 张变体时（走 NaiRepository.generateVariants 的 async{}.awaitAll()）
     * 多个协程可能同时读到相同 maxNo 导致文件互相覆盖。
     *
     * [ImageSaver] 是进程内单例 object，[synchronized] 已足够保证进程内互斥。
     */
    private val namingLock = Any()

    /** App 私有目录 */
    private fun privateDir(context: Context): File =
        File(context.filesDir, PRIVATE_DIR).apply { if (!exists()) mkdirs() }

    /**
     * 保存原图到 App 私有目录。返回相对路径（用于 HistoryEntity.imagePath）。
     *
     * 文件命名：YYYYMMDD_NNN.png，对齐教程 §5.12 的命名规则。
     *
     * 线程安全：内部用 [namingLock] 保证序号分配的原子性。
     */
    fun savePrivate(context: Context, bytes: ByteArray): String {
        val dir = privateDir(context)
        val name = synchronized(namingLock) { nextDatedName(dir, "png") }
        File(dir, name).writeBytes(bytes)
        return "$PRIVATE_DIR/$name"
    }

    /**
     * 从相对路径读取 Bitmap（带降采样，避免 OOM）。
     */
    fun readPrivateBitmap(context: Context, relativePath: String, reqWidth: Int = 1024): Bitmap? {
        val file = File(context.filesDir, relativePath)
        if (!file.exists()) return null
        return runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
            var sample = 1
            while (boundsOpts.outWidth / sample > reqWidth * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        }.getOrNull()
    }

    /**
     * 生成缩略图字节。宽 256，等比缩放，JPEG 80。
     *
     * 性能优化：用 inSampleSize 一次性降采样解码，避免先把全图加载到内存再缩放导致 OOM。
     * 步骤：
     *   1. 第一次 decode 只读 bounds（inJustDecodeBounds=true）
     *   2. 根据 maxWidth 计算 inSampleSize
     *   3. 第二次 decode 出降采样后的 Bitmap
     *   4. 再 createScaledBitmap 精修到目标宽度
     */
    fun makeThumbnail(bytes: ByteArray, maxWidth: Int = 256): ByteArray {
        // 第一次：只读尺寸
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
        val srcW = boundsOpts.outWidth
        val srcH = boundsOpts.outHeight
        if (srcW <= 0 || srcH <= 0) return bytes

        // 计算 inSampleSize（必须是 2 的幂）
        var sample = 1
        while (srcW / sample > maxWidth * 2) sample *= 2

        // 第二次：降采样解码
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val sampled = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return bytes

        // 精修到目标宽度
        val ratio = sampled.height.toFloat() / sampled.width.toFloat()
        val w = maxWidth.coerceAtMost(sampled.width)
        val h = (w * ratio).toInt().coerceAtLeast(1)
        val scaled = if (w == sampled.width) sampled else Bitmap.createScaledBitmap(sampled, w, h, true)

        val out = java.io.ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        val result = out.toByteArray()
        if (scaled != sampled) scaled.recycle()
        sampled.recycle()
        return result
    }

    /**
     * 把私有目录里的图片复制到系统相册 Pictures/NaiGen/。
     *
     * - Android 10+：用 MediaStore.IS_PENDING 流程，无需任何权限
     * - Android 9 及以下：直接写 File，需要 WRITE_EXTERNAL_STORAGE 权限（已在 Manifest 声明，运行时由调用方请求）
     *
     * @return 写入成功后的 content:// Uri，可用于系统分享
     */
    fun saveToGallery(context: Context, bytes: ByteArray, mimeType: String = "image/png"): Uri? {
        val displayName = "NaiGen_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.png"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 走 MediaStore
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$GALLERY_DIR")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            // Android 9 及以下：直接写文件
            val pics = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val dir = File(pics, GALLERY_DIR).apply { if (!exists()) mkdirs() }
            val file = File(dir, displayName)
            FileOutputStream(file).use { it.write(bytes) }
            // 让相册扫描到
            android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType), null)
            Uri.fromFile(file)
        }
    }

    /**
     * 返回 YYYYMMDD_NNN.png 的下一个可用文件名（按现有最大编号 +1）。
     */
    private fun nextDatedName(dir: File, ext: String): String {
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        var maxNo = 0
        dir.listFiles()?.forEach { f ->
            val n = f.name
            if (n.startsWith("${date}_") && n.endsWith(".$ext")) {
                val mid = n.removePrefix("${date}_").removeSuffix(".$ext")
                mid.toIntOrNull()?.let { if (it > maxNo) maxNo = it }
            }
        }
        return "${date}_${(maxNo + 1).toString().padStart(3, '0')}.$ext"
    }
}
