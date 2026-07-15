package com.naigen.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateUtils {

    private val FILE_FMT = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val DISPLAY_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val RELATIVE_FMT = SimpleDateFormat("MM-dd HH:mm", Locale.US)

    fun fileDate(date: Date = Date()): String = FILE_FMT.format(date)

    fun display(date: Date = Date()): String = DISPLAY_FMT.format(date)

    fun display(ts: Long): String = DISPLAY_FMT.format(Date(ts))

    fun relative(ts: Long): String = RELATIVE_FMT.format(Date(ts))

    /**
     * 把毫秒时间戳格式化为 "30.5s" / "1m 5s" 形式，用于显示生成耗时。
     */
    fun duration(ms: Long): String {
        val totalSec = ms / 1000.0
        return if (totalSec < 60) {
            String.format(Locale.US, "%.1fs", totalSec)
        } else {
            val m = (totalSec / 60).toInt()
            val s = (totalSec % 60).toInt()
            "${m}m ${s}s"
        }
    }
}
