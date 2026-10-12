package com.zhiji.app.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/** 数据仓库：本地 Room 为真相源，后台与服务端 LWW 同步 */
class Repo(private val ctx: Context) {
    val db: AppDatabase = Room.databaseBuilder(ctx, AppDatabase::class.java, "zhiji.db")
        .fallbackToDestructiveMigration()
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

    /** 编辑已有记录：更新内容并置脏，同步时以新时间戳覆盖服务端（LWW） */
    suspend fun updateEntry(e: EntryEntity, contentJson: String, tags: List<String>, entryDate: String) {
        entryDao.upsert(
            e.copy(
                contentJson = contentJson,
                tagsJson = C.tags(*tags.toTypedArray()),
                entryDate = entryDate,
                updatedAt = nowISO(),
                dirty = true,
            )
        )
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
                val o = e.jsonObject
                val entity = entryFromJson(o)
                val local = entryDao.byClientId(entity.clientId)
                if (local == null || local.updatedAt < entity.updatedAt) {
                    entryDao.upsert(entity)
                    pulled++
                }
            }
            for (h in resp["habits"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())) {
                val o = h.jsonObject
                val entity = habitFromJson(o)
                val local = habitDao.byClientId(entity.clientId)
                if (local == null || local.updatedAt < entity.updatedAt) {
                    habitDao.upsert(entity)
                    pulled++
                }
            }
            (resp["serverTime"] as? JsonPrimitive)?.content?.let { api.lastPull = it }
            return SyncResult(dirtyE.size + dirtyH.size, pulled)
    }

    private fun entryFromJson(o: JsonObject): EntryEntity = EntryEntity(
        clientId = o["clientId"]!!.jsonPrimitive.content,
        type = o["type"]!!.jsonPrimitive.content,
        contentJson = o["content"]!!.toString(),
        tagsJson = o["tags"]?.toString() ?: "[]",
        entryDate = o["entryDate"]!!.jsonPrimitive.content,
        createdAt = o["createdAt"]!!.jsonPrimitive.content,
        updatedAt = o["updatedAt"]!!.jsonPrimitive.content,
        deleted = (o["deleted"] as? JsonPrimitive)?.content == "true",
        dirty = false,
    )

    private fun habitFromJson(o: JsonObject): HabitEntity = HabitEntity(
        clientId = o["clientId"]!!.jsonPrimitive.content,
        name = o["name"]!!.jsonPrimitive.content,
        emoji = o["emoji"]?.jsonPrimitive?.content ?: "✅",
        color = o["color"]?.jsonPrimitive?.content ?: "#7C9A6D",
        archived = (o["archived"] as? JsonPrimitive)?.content == "true",
        sortOrder = (o["sortOrder"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
        createdAt = o["createdAt"]?.jsonPrimitive?.content ?: nowISO(),
        updatedAt = o["updatedAt"]?.jsonPrimitive?.content ?: nowISO(),
        deleted = (o["deleted"] as? JsonPrimitive)?.content == "true",
        dirty = false,
    )

    companion object {
        @Volatile private var instance: Repo? = null
        fun get(ctx: Context): Repo = instance ?: synchronized(this) {
            instance ?: Repo(ctx.applicationContext).also { instance = it }
        }
    }
}
