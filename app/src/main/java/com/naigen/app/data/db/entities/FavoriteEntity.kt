package com.naigen.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Prompt 收藏夹。用户保存常用提示词模板，一键填入。
 *
 * - [tag] 自由分类标签，例如 "角色" / "场景" / "负面词"
 */
@Entity(tableName = "favorites", indices = [
    androidx.room.Index(value = ["tag"]),
    androidx.room.Index(value = ["isNegative"])
])
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val tag: String = "",
    val isNegative: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
