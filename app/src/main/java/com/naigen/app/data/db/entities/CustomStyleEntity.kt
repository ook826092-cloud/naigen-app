package com.naigen.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户自定义风格。和内置 / 社区风格用相同的 artistString 字段格式。
 *
 * - [key] UI 引用 key，格式 "custom_<id>"，避免与 community:ID 冲突
 * - [name] 用户起的名字
 * - [artistString] 画师串（用户填写）
 * - [positivePrefix] / [negativePrompt] 可选
 */
@Entity(tableName = "custom_styles")
data class CustomStyleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val artistString: String,
    val positivePrefix: String = "",
    val negativePrompt: String = "",
    val steps: Int = 28,
    val scale: Double = 6.0,
    val cfg: Double = 0.0,
    val sampler: String = "k_dpmpp_2m_sde",
    val createdAt: Long = System.currentTimeMillis()
)
