package com.naigen.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 生成历史记录。
 *
 * - [imagePath] 图片存到 app 私有 filesDir 的相对路径
 * - [imageUrl]  来自 Nai2API 的可访问 URL（用于分享与内部跟踪）
 * - [bytes]     缩略图字节数组（小尺寸预览，避免每次开列表都全图解码）
 */
@Entity(tableName = "history", indices = [
    androidx.room.Index(value = ["createdAt"]),
    androidx.room.Index(value = ["styleKey"])
])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prompt: String,
    val negativePrompt: String,
    val styleKey: String,
    val styleName: String,
    val sizeKey: String,
    val sizeCost: Int,
    val imageUrl: String,
    val imagePath: String,
    val thumbBytes: ByteArray,
    val generationTimeMs: Long,
    val success: Boolean,
    val errorMessage: String?,
    val createdAt: Long = System.currentTimeMillis()
) {
    // Room 用 ByteArray 需要手动 override equals/hashCode 以避免数据类默认实现
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HistoryEntity) return false
        return id == other.id && createdAt == other.createdAt
    }
    override fun hashCode(): Int = id.hashCode()
}
