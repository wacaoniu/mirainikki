package com.zhiji.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries WHERE deleted = 0 AND entryDate <= :date ORDER BY entryDate DESC, createdAt DESC LIMIT :limit")
    fun recent(date: String, limit: Int): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE deleted = 0 AND entryDate = :date ORDER BY createdAt DESC")
    fun byDate(date: String): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE deleted = 0 AND type = 'habit_checkin' AND entryDate = :date")
    suspend fun checkinsOn(date: String): List<EntryEntity>

    @Query("SELECT tagsJson FROM entries WHERE deleted = 0 AND tagsJson != '[]' ORDER BY createdAt DESC LIMIT 200")
    suspend fun recentTagsRaw(): List<String>

    @Query("SELECT * FROM entries WHERE dirty = 1 LIMIT 500")
    suspend fun dirtyEntries(): List<EntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<EntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: EntryEntity)

    @Query("DELETE FROM entries WHERE clientId = :clientId")
    suspend fun deleteByClientId(clientId: String)

    @Query("SELECT * FROM entries WHERE clientId = :clientId")
    suspend fun byClientId(clientId: String): EntryEntity?

    @Query("UPDATE entries SET deleted = 1, dirty = 1, updatedAt = :now WHERE clientId = :clientId")
    suspend fun softDelete(clientId: String, now: String)

    @Query("SELECT COUNT(*) FROM entries WHERE dirty = 1")
    suspend fun dirtyCount(): Int

    @Query("UPDATE entries SET dirty = 0 WHERE clientId IN (:ids)")
    suspend fun markClean(ids: List<String>)
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE deleted = 0 AND archived = 0 ORDER BY sortOrder, createdAt")
    fun active(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE deleted = 0 ORDER BY sortOrder, createdAt")
    fun all(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE dirty = 1 LIMIT 200")
    suspend fun dirtyHabits(): List<HabitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(habit: HabitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(habits: List<HabitEntity>)

    @Query("SELECT * FROM habits WHERE clientId = :clientId")
    suspend fun byClientId(clientId: String): HabitEntity?

    @Query("SELECT COUNT(*) FROM habits WHERE dirty = 1")
    suspend fun dirtyCount(): Int

    @Query("UPDATE habits SET dirty = 0 WHERE clientId IN (:ids)")
    suspend fun markClean(ids: List<String>)
}

@Database(entities = [EntryEntity::class, HabitEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun habitDao(): HabitDao
}
