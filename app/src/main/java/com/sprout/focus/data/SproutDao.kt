package com.sprout.focus.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SproutDao {

    @Query("SELECT * FROM tasks WHERE status = 'active' ORDER BY createdAt DESC")
    fun observeActiveTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isCurrent = 1 AND status = 'active' LIMIT 1")
    fun observeCurrentTask(): Flow<Task?>

    @Query("SELECT COUNT(*) FROM tasks WHERE isCurrent = 1 AND status = 'active'")
    suspend fun countCurrent(): Int

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTask(id: Long): Task?

    @Query("SELECT * FROM tasks WHERE status = 'active' ORDER BY createdAt DESC LIMIT 1")
    suspend fun newestActiveTask(): Task?

    @Insert
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Query("UPDATE tasks SET isCurrent = 0")
    suspend fun clearCurrentFlag()

    @Query("UPDATE tasks SET isCurrent = 1 WHERE id = :id")
    suspend fun setCurrentFlag(id: Long)

    /** Текущая задача всегда ровно одна — снимаем флаг со всех, ставим одной. */
    @Transaction
    suspend fun makeCurrent(id: Long) {
        clearCurrentFlag()
        setCurrentFlag(id)
    }

    @Query("UPDATE tasks SET status = :status, completedAt = :at, isCurrent = 0 WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, at: Long?)

    // --- напоминания ---

    /**
     * Все задачи, у которых стоит напоминание.
     *
     * Нужен и планировщику, и восстановлению после перезагрузки: одним
     * запросом видно, какие будильники должны стоять прямо сейчас.
     */
    @Query("SELECT * FROM tasks WHERE remindNextAt IS NOT NULL AND status = 'active' ORDER BY remindNextAt")
    suspend fun tasksWithReminder(): List<Task>

    @Query("SELECT * FROM tasks WHERE remindNextAt IS NOT NULL AND status = 'active' ORDER BY remindNextAt")
    fun observeTasksWithReminder(): Flow<List<Task>>

    @Insert
    suspend fun insertEvent(event: Event)

    // --- сессии ---

    @Query("SELECT * FROM sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveSession(): Flow<Session?>

    @Query("SELECT * FROM sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveSession(): Session?

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSession(id: Long): Session?

    @Insert
    suspend fun insertSession(session: Session): Long

    @Update
    suspend fun updateSession(session: Session)

    // --- сад ---

    @Query("SELECT * FROM garden WHERE id = 1")
    fun observeGarden(): Flow<Garden?>

    @Query("SELECT * FROM garden WHERE id = 1")
    suspend fun getGarden(): Garden?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGarden(garden: Garden)

    @Insert
    suspend fun insertGrownPlant(plant: GrownPlant)

    @Query("SELECT COUNT(*) FROM grown_plants")
    fun observeGrownCount(): Flow<Int>
}
