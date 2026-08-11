package com.sprout.focus.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Миграции базы — на настоящем SQLite устройства.
 *
 * Самый дорогой из возможных отказов приложения: данные Sprout живут только
 * на телефоне и восстановить их неоткуда. `fallbackToDestructiveMigration`
 * убран совсем, поэтому неверная миграция роняет приложение на старте —
 * заметно сразу, но человеку от этого не легче.
 *
 * Тест поднимает базу такой, какой она была в версии 4 (до первой настоящей
 * миграции), кладёт в неё задачу, событие, сессию и сад — и открывает всё это
 * сегодняшним Room. Room при открытии сверяет схему с ожидаемой: если
 * миграция забыла колонку или поставила не тот тип, база не откроется вовсе.
 * А проверки после — про то, что данные при этом остались на месте.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private val name = "migration-test.db"

    // Имена тестов здесь без пробелов в обратных кавычках, в отличие от тестов
    // на JVM: пробел в имени метода до Android 11 не переживает превращения
    // в dex, а minSdk у приложения — 26
    @Before
    fun clean() {
        context.deleteDatabase(name)
    }

    @After
    fun cleanUp() {
        context.deleteDatabase(name)
    }

    @Test
    fun база_версии_4_доживает_до_текущей_без_потерь() {
        createVersion4()

        val db = Room.databaseBuilder(context, SproutDatabase::class.java, name)
            .addMigrations(*SproutDatabase.MIGRATIONS)
            .build()

        runBlocking {
            val dao = db.dao()

            val tasks = dao.observeActiveTasks().first()
            assertEquals(1, tasks.size)
            assertEquals("Найти подрядчика", tasks[0].title)
            assertEquals("Открыть список и позвонить первому", tasks[0].firstStep)

            // Поля, которых в версии 4 не было, должны получить умолчания,
            // а не мусор: маска дней — ноль, время напоминания — пусто
            assertEquals(0, tasks[0].remindDaysMask)
            assertEquals(null, tasks[0].remindMinuteOfDay)

            val garden = dao.getGarden()
            assertNotNull(garden)
            assertEquals(42, garden!!.points)
            assertEquals(3, garden.streak)

            val session = dao.getSession(1)
            assertNotNull(session)
            assertEquals(1200, session!!.plannedSeconds)

            // Таблицы, появившиеся в поздних миграциях, должны быть пустыми
            // и рабочими — а не отсутствующими
            assertEquals(0, dao.blockedPackages().size)
            assertEquals(null, dao.getRunningExperiment())
            assertEquals(0, dao.triedHypotheses().size)
        }

        db.close()
    }

    /**
     * База ровно такая, какой её создавал Room версии 4.
     *
     * Кавычки вокруг имён и порядок колонок здесь не украшение: миграции
     * дописывают колонки поверх этих таблиц, и расхождение всплывёт только
     * на настоящем устройстве человека.
     */
    private fun createVersion4() {
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)

        db.execSQL(
            "CREATE TABLE `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `firstStep` TEXT NOT NULL, `ifTrigger` TEXT, " +
                "`thenAction` TEXT, `copingPlan` TEXT, `whyItMatters` TEXT, " +
                "`parentTaskId` INTEGER, `status` TEXT NOT NULL, `isCurrent` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `completedAt` INTEGER, " +
                "`postponeCount` INTEGER NOT NULL, `lastStoppedAt` TEXT)"
        )
        db.execSQL(
            "CREATE TABLE `events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`type` TEXT NOT NULL, `at` INTEGER NOT NULL, `taskId` INTEGER, " +
                "`payload` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE `sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`taskId` INTEGER, `mode` TEXT NOT NULL, `plannedSeconds` INTEGER NOT NULL, " +
                "`startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `pausedAt` INTEGER, " +
                "`pausedTotal` INTEGER NOT NULL, `actualSeconds` INTEGER, " +
                "`completed` INTEGER NOT NULL, `selfRating` INTEGER, `interruptions` INTEGER, " +
                "`stoppedNote` TEXT)"
        )
        db.execSQL(
            "CREATE TABLE `garden` (`id` INTEGER NOT NULL, `points` INTEGER NOT NULL, " +
                "`plantStartedAt` INTEGER NOT NULL, `grownCount` INTEGER NOT NULL, " +
                "`streak` INTEGER NOT NULL, `lastActiveDay` TEXT, `freezesLeft` INTEGER NOT NULL, " +
                "`freezeMonth` TEXT NOT NULL, `growthDay` TEXT, `growthToday` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE TABLE `grown_plants` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`startedAt` INTEGER NOT NULL, `completedAt` INTEGER NOT NULL)"
        )
        // Room узнаёт «свою» базу по этой таблице. Без неё открытие считается
        // первым запуском, и никакой миграции не случится вовсе
        db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")

        db.execSQL(
            "INSERT INTO tasks (title, firstStep, status, isCurrent, createdAt, postponeCount) " +
                "VALUES ('Найти подрядчика', 'Открыть список и позвонить первому', 'active', 1, 1000, 2)"
        )
        db.execSQL(
            "INSERT INTO events (type, at, taskId, payload) " +
                "VALUES ('task_created', 1000, 1, '{}')"
        )
        db.execSQL(
            "INSERT INTO sessions (taskId, mode, plannedSeconds, startedAt, endedAt, " +
                "pausedTotal, actualSeconds, completed) " +
                "VALUES (1, 'pomodoro', 1200, 1000, 2000, 0, 1200, 1)"
        )
        db.execSQL(
            "INSERT INTO garden (id, points, plantStartedAt, grownCount, streak, " +
                "lastActiveDay, freezesLeft, freezeMonth, growthDay, growthToday) " +
                "VALUES (1, 42, 1000, 0, 3, '2026-08-01', 2, '2026-08', '2026-08-01', 20)"
        )

        db.version = 4
        db.close()
    }
}
