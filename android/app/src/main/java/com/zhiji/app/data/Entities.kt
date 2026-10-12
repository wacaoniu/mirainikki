package com.zhiji.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 与服务端同构的记录实体；clientId 为同步幂等键，dirty 标记待推送 */
@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey val clientId: String,
    val type: String,            // mood | text | photo | habit_checkin | health
    val contentJson: String,     // 按类型不同的 JSON
    val tagsJson: String,        // ["tag1","tag2"]
    val entryDate: String,       // YYYY-MM-DD
    val createdAt: String,       // ISO8601 UTC
    val updatedAt: String,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val clientId: String,
    val name: String,
    val emoji: String = "✅",
    val color: String = "#7C9A6D",
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: String,
    val updatedAt: String,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)
