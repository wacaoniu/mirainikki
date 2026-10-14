package com.mirainikki.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class MoodContent(val score: Int, val emotions: List<String> = emptyList(), val note: String? = null)

@Serializable
data class TextContent(val title: String? = null, val body: String? = null)

/**
 * 照片记录：本地优先。
 * - localFiles[i] 是手机上的本地文件路径（不联网也能立即显示）
 * - localFileIds[i] 是它与服务端图片 id 的对应关系，"" 表示还没上传
 * - imageIds 是服务端全部图片 id（同步/电脑端用）
 */
@Serializable
data class PhotoContent(
    val caption: String? = null,
    val imageIds: List<String> = emptyList(),
    val localFiles: List<String> = emptyList(),
    val localFileIds: List<String> = emptyList(),
) {
    /** 还没上传的本地照片数 */
    val pendingCount: Int get() = localFiles.indices.count { i -> (localFileIds.getOrNull(i) ?: "").isEmpty() }
    /** 全部照片都已在服务端 */
    val allUploaded: Boolean get() = pendingCount == 0
}

@Serializable
data class HabitCheckinContent(val habitId: String, val habitName: String? = null)

@Serializable
data class HealthContent(val sleepHours: Double? = null, val weightKg: Double? = null, val steps: Int? = null)

@Serializable
data class DreamContent(val body: String = "", val emotions: List<String> = emptyList())

object C {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    const val TYPE_MOOD = "mood"
    const val TYPE_TEXT = "text"
    const val TYPE_PHOTO = "photo"
    const val TYPE_HABIT = "habit_checkin"
    const val TYPE_HEALTH = "health"
    const val TYPE_DREAM = "dream"

    inline fun <reified T> encode(v: T): String = json.encodeToString(v)
    inline fun <reified T> decode(s: String): T = json.decodeFromString(s)
    fun tags(vararg ts: String): String = json.encodeToString(ts.filter { it.isNotBlank() })
    fun decodeTags(s: String): List<String> = try { json.decodeFromString<List<String>>(s) } catch (_: Exception) { emptyList() }
}

fun nowISO(): String = java.time.Instant.now().toString()

fun todayLocal(): String {
    val d = java.time.LocalDate.now()
    return "%04d-%02d-%02d".format(d.year, d.monthValue, d.dayOfMonth)
}
