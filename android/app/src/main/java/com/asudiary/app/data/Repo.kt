package com.asudiary.app.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/** 数据仓库：本地 Room 为真相源，后台与服务端 LWW 同步。
 *  注意：不加 fallbackToDestructiveMigration —— 升级 APK 时宁可启动失败也不静默清空用户日记；
 *  版本不匹配时按 Migration 显式迁移（见 AppDatabase.MIGRATIONS）。 */
class Repo(private val ctx: Context) {
    val db: AppDatabase = Room.databaseBuilder(ctx, AppDatabase::class.java, "asudiary.db")
        .addMigrations(*AppDatabase.MIGRATIONS)
        .build()
    val api = Api(ctx)
    private val entryDao = db.entryDao()
    private val habitDao = db.habitDao()

    // ---------- 查询 ----------
    fun recentFlow(): Flow<List<EntryEntity>> = entryDao.recent(todayLocal(), 300)
    fun todayFlow(): Flow<List<EntryEntity>> = entryDao.byDate(todayLocal())
    fun byDateFlow(date: String): Flow<List<EntryEntity>> = entryDao.byDate(date)
    fun habitsFlow(): Flow<List<HabitEntity>> = habitDao.active()
    fun allHabitsFlow(): Flow<List<HabitEntity>> = habitDao.all()

    // ---------- 写入 ----------
    private suspend fun insert(type: String, contentJson: String, tags: List<String>, entryDate: String): EntryEntity {
        val e = EntryEntity(
            clientId = UUID.randomUUID().toString(),
            type = type,
            contentJson = contentJson,
            tagsJson = C.tags(*tags.toTypedArray()),
            entryDate = entryDate,
            createdAt = nowISO(),
            updatedAt = nowISO(),
        )
        entryDao.upsert(e)
        return e
    }

    suspend fun addMood(score: Int, emotions: List<String>, note: String?, tags: List<String>, date: String = todayLocal()) =
        insert(C.TYPE_MOOD, C.encode(MoodContent(score, emotions, note)), tags, date)

    suspend fun addText(title: String?, body: String, tags: List<String>, date: String = todayLocal()) =
        insert(C.TYPE_TEXT, C.encode(TextContent(title?.takeIf { it.isNotBlank() }, body)), tags, date)

    suspend fun addPhoto(caption: String?, imageIds: List<String>, tags: List<String>, date: String = todayLocal()) =
        insert(C.TYPE_PHOTO, C.encode(PhotoContent(caption, imageIds)), tags, date)

    suspend fun addHabitCheckin(habit: HabitEntity, date: String = todayLocal()) =
        insert(C.TYPE_HABIT, C.encode(HabitCheckinContent(habit.clientId, habit.name)), emptyList(), date)

    suspend fun addHealth(sleepHours: Double?, weightKg: Double?, steps: Int?, tags: List<String>, date: String = todayLocal()) =
        insert(C.TYPE_HEALTH, C.encode(HealthContent(sleepHours, weightKg, steps)), tags, date)

    suspend fun addDream(body: String, emotions: List<String>, tags: List<String>, date: String = todayLocal()) =
        insert(C.TYPE_DREAM, C.encode(DreamContent(body, emotions)), tags, date)

    suspend fun deleteEntry(clientId: String) = entryDao.softDelete(clientId, nowISO())

    /** 编辑已有记录：更新内容并置脏，同步时以新时间戳覆盖服务端（LWW）。
     *  先 DELETE 再 INSERT（同事务）：确保 Room 的 Flow 感知到变化立即刷新 UI。 */
    suspend fun updateEntry(e: EntryEntity, contentJson: String, tags: List<String>, entryDate: String) {
        val updated = e.copy(
            contentJson = contentJson,
            tagsJson = C.tags(*tags.toTypedArray()),
            entryDate = entryDate,
            updatedAt = nowISO(),
            dirty = true,
        )
        db.withTransaction {
            entryDao.deleteByClientId(e.clientId)
            entryDao.upsert(updated)
        }
    }

    suspend fun todayCheckinDates(): Set<String> = checkinDatesOn(todayLocal())

    suspend fun checkinDatesOn(date: String): Set<String> = entryDao.checkinsOn(date).mapNotNull { e ->
        try { C.decode<HabitCheckinContent>(e.contentJson).habitId } catch (_: Exception) { null }
    }.toSet()

    /** 最近 200 条记录里出现的高频标签（供一键点选） */
    suspend fun recentTags(max: Int = 12): List<String> {
        val freq = LinkedHashMap<String, Int>()
        for (raw in entryDao.recentTagsRaw()) {
            for (t in C.decodeTags(raw)) freq[t] = (freq[t] ?: 0) + 1
        }
        return freq.entries.sortedByDescending { it.value }.take(max).map { it.key }
    }

    // ---------- 习惯 ----------
    suspend fun addHabit(name: String, emoji: String) {
        habitDao.upsert(
            HabitEntity(
                clientId = UUID.randomUUID().toString(),
                name = name, emoji = emoji,
                createdAt = nowISO(), updatedAt = nowISO(),
            )
        )
    }

    suspend fun deleteHabit(h: HabitEntity) {
        habitDao.upsert(h.copy(deleted = true, updatedAt = nowISO()))
    }

    // ---------- 备份 / 恢复 ----------

    /** 导出全量备份 JSON（与网页端「全量备份」同构：entries + habits） */
    suspend fun exportBackupJson(): String {
        val entries = entryDao.allEntries().map { e ->
            buildString {
                append("{\"clientId\":\"").append(e.clientId).append("\",\"type\":\"").append(e.type)
                append("\",\"content\":").append(e.contentJson)
                append(",\"tags\":").append(e.tagsJson)
                append(",\"entryDate\":\"").append(e.entryDate).append("\",\"createdAt\":\"").append(e.createdAt)
                append("\",\"updatedAt\":\"").append(e.updatedAt).append("\",\"deleted\":").append(e.deleted)
                append(",\"dirty\":").append(e.dirty).append("}")
            }
        }
        val habits = habitDao.allHabits().map { h ->
            buildString {
                append("{\"clientId\":\"").append(h.clientId).append("\",\"name\":\"").append(h.name.replace("\\", "\\\\").replace("\"", "\\\""))
                append("\",\"emoji\":\"").append(h.emoji).append("\",\"color\":\"").append(h.color)
                append("\",\"archived\":").append(h.archived)
                append(",\"sortOrder\":").append(h.sortOrder)
                append(",\"createdAt\":\"").append(h.createdAt).append("\",\"updatedAt\":\"").append(h.updatedAt)
                append("\",\"deleted\":").append(h.deleted).append(",\"dirty\":").append(h.dirty).append("}")
            }
        }
        return "{\"exportedAt\":\"${nowISO()}\",\"app\":\"asudiary-android\",\"entries\":[${entries.joinToString(",")}],\"habits\":[${habits.joinToString(",")}]}"
    }

    /** 从全量备份恢复（LWW：只接受比本地新或不存在的记录；不清空现有数据，可安全重复执行） */
    suspend fun restoreBackupJson(text: String): Pair<Int, Int> {
        val obj = C.json.parseToJsonElement(text).jsonObject
        var restoredE = 0
        var restoredH = 0
        for (el in obj["entries"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())) {
            val o = el.jsonObject
            val entity = entryFromJson(o) ?: continue
            val local = entryDao.byClientId(entity.clientId)
            if (local == null || local.updatedAt < entity.updatedAt) {
                entryDao.upsert(entity.copy(dirty = true))
                restoredE++
            }
        }
        for (el in obj["habits"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())) {
            val o = el.jsonObject
            val entity = habitFromJson(o) ?: continue
            val local = habitDao.byClientId(entity.clientId)
            if (local == null || local.updatedAt < entity.updatedAt) {
                habitDao.upsert(entity.copy(dirty = true))
                restoredH++
            }
        }
        return restoredE to restoredH
    }

    private fun entryFromJson(o: JsonObject): EntryEntity? = runCatching {
        EntryEntity(
            clientId = o["clientId"]!!.jsonPrimitive.content,
            type = o["type"]!!.jsonPrimitive.content,
            contentJson = o["content"]!!.toString(),
            tagsJson = o["tags"]?.toString() ?: "[]",
            entryDate = o["entryDate"]!!.jsonPrimitive.content,
            createdAt = o["createdAt"]?.jsonPrimitive?.content ?: nowISO(),
            updatedAt = o["updatedAt"]?.jsonPrimitive?.content ?: nowISO(),
            deleted = o["deleted"]?.jsonPrimitive?.content == "true",
        )
    }.getOrNull()

    private fun habitFromJson(o: JsonObject): HabitEntity? = runCatching {
        HabitEntity(
            clientId = o["clientId"]!!.jsonPrimitive.content,
            name = o["name"]!!.jsonPrimitive.content,
            emoji = o["emoji"]?.jsonPrimitive?.content ?: "✅",
            color = o["color"]?.jsonPrimitive?.content ?: "#7C9A6D",
            archived = o["archived"]?.jsonPrimitive?.content == "true",
            sortOrder = o["sortOrder"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            createdAt = o["createdAt"]?.jsonPrimitive?.content ?: nowISO(),
            updatedAt = o["updatedAt"]?.jsonPrimitive?.content ?: nowISO(),
            deleted = o["deleted"]?.jsonPrimitive?.content == "true",
        )
    }.getOrNull()

    // ---------- 同步 ----------
    data class SyncResult(val pushed: Int, val pulled: Int, val error: String? = null)

    suspend fun syncOnce(): SyncResult {
        if (!api.configured) return SyncResult(0, 0, "未配置服务器或未登录")
        return try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { syncOnceInner() }
        } catch (ex: Exception) {
            android.util.Log.w("SDJ", "syncOnce error", ex)
            SyncResult(0, 0, ex.message ?: "未知错误")
        }
    }

    private suspend fun syncOnceInner(): SyncResult {
        // 推送本地变更（已在 IO 线程）
        val dirtyE = entryDao.dirtyEntries()
        val dirtyH = habitDao.dirtyHabits()
        if (dirtyE.isNotEmpty() || dirtyH.isNotEmpty()) {
            val pushResp = api.push(dirtyE, dirtyH)
            android.util.Log.d("SDJ", "push accepted=${(pushResp["accepted"] as? JsonPrimitive)?.content} skipped=${(pushResp["skipped"] as? JsonPrimitive)?.content}")
            entryDao.markClean(dirtyE.map { it.clientId })
            habitDao.markClean(dirtyH.map { it.clientId })
        }
            // 拉取远端变更（LWW：远端更新才覆盖本地）
            val resp = api.pull(api.lastPull)
            var pulled = 0
            for (e in resp["entries"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())) {
                val entity = entryFromJson(e.jsonObject) ?: continue
                val local = entryDao.byClientId(entity.clientId)
                if (local == null || local.updatedAt < entity.updatedAt) {
                    entryDao.upsert(entity)
                    pulled++
                }
            }
            for (h in resp["habits"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())) {
                val entity = habitFromJson(h.jsonObject) ?: continue
                val local = habitDao.byClientId(entity.clientId)
                if (local == null || local.updatedAt < entity.updatedAt) {
                    habitDao.upsert(entity)
                    pulled++
                }
            }
            (resp["serverTime"] as? JsonPrimitive)?.content?.let { api.lastPull = it }
            return SyncResult(dirtyE.size + dirtyH.size, pulled)
    }

    companion object {
        @Volatile private var instance: Repo? = null
        fun get(ctx: Context): Repo = instance ?: synchronized(this) {
            instance ?: Repo(ctx.applicationContext).also { instance = it }
        }
    }
}
