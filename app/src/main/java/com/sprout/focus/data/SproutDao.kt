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

    /** То же самое разово — виджету, который живёт в чужом процессе и не подписывается. */
    @Query("SELECT * FROM tasks WHERE isCurrent = 1 AND status = 'active' LIMIT 1")
    suspend fun getCurrentTask(): Task?

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

    // --- аналитика ---
    //
    // Всё за окно наблюдения и всё через Flow: экран «Я» должен меняться
    // сразу после сессии, а не при следующем запуске. Room сам пришлёт
    // новое значение, когда в таблицу что-то допишут.

    @Query("SELECT * FROM events WHERE type = :type AND at >= :since ORDER BY at")
    fun observeEventsOfType(type: String, since: Long): Flow<List<Event>>

    @Query("SELECT * FROM sessions WHERE endedAt IS NOT NULL AND startedAt >= :since ORDER BY startedAt")
    fun observeFinishedSessions(since: Long): Flow<List<Session>>

    /**
     * Сколько всего минут фокуса за окно.
     *
     * Считаем по actualSeconds, а не по плану: интересно отработанное время,
     * а не намерение. У брошенных сессий оно тоже записано.
     */
    @Query(
        "SELECT COALESCE(SUM(actualSeconds), 0) / 60 FROM sessions " +
            "WHERE endedAt IS NOT NULL AND startedAt >= :since"
    )
    fun observeFocusMinutes(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM events WHERE type = :type AND at >= :since")
    fun observeEventCount(type: String, since: Long): Flow<Int>

    // --- отвлекающие приложения ---

    @Query("SELECT * FROM blocked_apps ORDER BY label")
    fun observeBlockedApps(): Flow<List<BlockedApp>>

    /** Разово — сторожу, который живёт в сервисе и подписываться не может. */
    @Query("SELECT packageName FROM blocked_apps")
    suspend fun blockedPackages(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addBlockedApp(app: BlockedApp)

    @Query("DELETE FROM blocked_apps WHERE packageName = :packageName")
    suspend fun removeBlockedApp(packageName: String)

    // --- эксперименты над собой ---

    @Query("SELECT * FROM experiments WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeRunningExperiment(): Flow<Experiment?>

    /**
     * То же разово.
     *
     * Нужно там, где эксперимент меняет поведение приложения: заводя сессию
     * из виджета или из уведомления, подписываться не на что.
     */
    @Query("SELECT * FROM experiments WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getRunningExperiment(): Experiment?

    @Insert
    suspend fun insertExperiment(experiment: Experiment): Long

    @Update
    suspend fun updateExperiment(experiment: Experiment)

    /** Что уже проверялось: предлагать то же самое второй раз незачем. */
    @Query("SELECT DISTINCT hypothesis FROM experiments")
    suspend fun triedHypotheses(): List<String>

    // --- то, из чего выбирается гипотеза и считается ход ---

    @Query("SELECT * FROM sessions WHERE endedAt IS NOT NULL AND startedAt >= :since ORDER BY startedAt")
    suspend fun finishedSessions(since: Long): List<Session>

    @Query("SELECT * FROM tasks WHERE createdAt >= :since")
    suspend fun tasksCreatedSince(since: Long): List<Task>

    @Query("SELECT * FROM tasks WHERE createdAt >= :since")
    fun observeTasksCreatedSince(since: Long): Flow<List<Task>>

    /**
     * За сколько из этих задач человек садился хотя бы раз.
     *
     * Именно «садился», а не «доделал»: гипотеза про план «если — то»
     * проверяет начало, а не завершение — начать и есть самое трудное.
     */
    @Query(
        "SELECT COUNT(DISTINCT s.taskId) FROM sessions s " +
            "JOIN tasks t ON t.id = s.taskId WHERE t.createdAt >= :since"
    )
    suspend fun startedTaskCount(since: Long): Int
}
