package com.sprout.focus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Проверка копии данных.
 *
 * Тут проверяется не «работает ли выгрузка», а два обещания, на которых
 * держится доверие к ней: **ничего не теряется** по дороге туда и обратно,
 * и **чужой или испорченный файл не доходит до базы**. Второе важнее:
 * восстановление стирает всё, что было, и ошибка на этом шаге необратима.
 */
class BackupTest {

    private fun sample() = BackupData(
        appVersion = "1.2",
        exportedAt = 1_760_000_000_000,
        settings = BackupSettings(
            barrierEnabled = true,
            quietEnabled = false,
            keptShortFirst = true,
            keptPlanRequired = false,
        ),
        tasks = listOf(
            Task(
                id = 1,
                title = "Дописать отчёт",
                firstStep = "Открыть документ",
                ifTrigger = "попью чай",
                status = Task.STATUS_ACTIVE,
                isCurrent = true,
                createdAt = 1_759_000_000_000,
                postponeCount = 2,
                remindMinuteOfDay = 600,
                remindDaysMask = 0b0011111,
                remindNextAt = 1_760_100_000_000,
                lastStoppedAt = "на третьем абзаце",
            ),
            Task(
                id = 2,
                title = "Задача без плана",
                firstStep = "Первый шаг",
                createdAt = 1_759_500_000_000,
                status = Task.STATUS_DONE,
                completedAt = 1_759_900_000_000,
            ),
        ),
        events = listOf(
            Event(id = 1, type = "task_created", at = 1_759_000_000_000, taskId = 1),
            Event(
                id = 2, type = "task_postponed", at = 1_759_100_000_000, taskId = 1,
                payload = """{"reason":"anxiety"}"""
            ),
        ),
        sessions = listOf(
            Session(
                id = 1, taskId = 1, mode = Session.MODE_POMODORO, plannedSeconds = 1200,
                startedAt = 1_759_200_000_000, endedAt = 1_759_201_200_000,
                actualSeconds = 1200, completed = true, selfRating = 3, pausedTotal = 60,
            ),
        ),
        garden = Garden(
            points = 42, plantStartedAt = 1_759_000_000_000, grownCount = 1,
            streak = 3, lastActiveDay = "2026-08-11", freezesLeft = 1,
            freezeMonth = "2026-08", growthDay = "2026-08-11", growthToday = 20,
        ),
        grownPlants = listOf(
            GrownPlant(id = 1, startedAt = 1_758_000_000_000, completedAt = 1_759_000_000_000)
        ),
        blockedApps = listOf(
            BlockedApp(packageName = "com.android.chrome", label = "Chrome", addedAt = 1_759_000_000_000)
        ),
        experiments = listOf(
            Experiment(
                id = 1, hypothesis = Experiments.SHORTER,
                startedAt = 1_759_000_000_000, endsAt = 1_759_604_800_000,
                baselinePercent = 58, baselineCount = 33,
                endedAt = 1_759_604_800_000, outcome = "confirmed",
                resultPercent = 80, observations = 10, succeeded = 8,
                resolvedAt = 1_759_700_000_000, kept = true,
            )
        ),
    )

    @Test
    fun `туда и обратно — ничего не теряется`() {
        val before = sample()
        val after = Backup.decode(Backup.encode(before))

        assertEquals(before.tasks, after.tasks)
        assertEquals(before.events, after.events)
        assertEquals(before.sessions, after.sessions)
        assertEquals(before.garden, after.garden)
        assertEquals(before.grownPlants, after.grownPlants)
        assertEquals(before.blockedApps, after.blockedApps)
        assertEquals(before.experiments, after.experiments)
        assertEquals(before.settings, after.settings)
        assertEquals(before.schemaVersion, after.schemaVersion)
    }

    @Test
    fun `пустые поля остаются пустыми, а не превращаются в пустую строку`() {
        val task = Backup.decode(Backup.encode(sample())).tasks[1]
        assertNull(task.ifTrigger)
        assertNull(task.thenAction)
        assertNull(task.remindMinuteOfDay)
        assertNull(task.remindNextAt)
        assertNull(task.lastStoppedAt)
    }

    @Test
    fun `пустая база выгружается и читается`() {
        val after = Backup.decode(Backup.encode(BackupData()))
        assertTrue(after.tasks.isEmpty())
        assertTrue(after.events.isEmpty())
        assertNull(after.garden)
    }

    @Test
    fun `чужой файл не принимается`() {
        broken("{}")
        broken("""{"format":"other-app","formatVersion":1}""")
        broken("совсем не json")
        broken("")
    }

    @Test
    fun `копия из будущей версии не принимается`() {
        val fromFuture = Backup.encode(sample())
            .replace("\"schemaVersion\": 8", "\"schemaVersion\": 99")
        val message = broken(fromFuture)
        // Человеку надо сказать, что делать, а не что случилось
        assertTrue(message, message.contains("Обнови"))
    }

    @Test
    fun `наш файл без данных внутри не принимается`() {
        // Формат тот, но задача без обязательных полей: пустить такое в базу
        // нельзя — восстановление стирает всё, что было
        broken(
            """{"format":"sprout-backup","formatVersion":1,"schemaVersion":8,
               "tasks":[{"id":1,"title":"Есть"}]}"""
        )
    }

    private fun broken(text: String): String {
        try {
            Backup.decode(text)
            fail("Файл приняли, а не должны были: $text")
            return ""
        } catch (e: Backup.Broken) {
            assertTrue(
                "Человеку нечего прочитать: «${e.message}»",
                (e.message?.length ?: 0) > 10
            )
            return e.message.orEmpty()
        }
    }

    // --- CSV ---

    @Test
    fun `в csv точка с запятой и кавычки не ломают строку`() {
        val csv = Backup.tasksCsv(
            listOf(
                Task(
                    id = 1,
                    title = "Отчёт; и ещё \"кавычки\"",
                    firstStep = "Открыть\nдокумент",
                    createdAt = 1_760_000_000_000,
                )
            )
        )
        val lines = csv.trim().lines()
        assertEquals(2, lines.count { it.startsWith("1;") || it.startsWith("﻿id") })
        assertTrue(csv, csv.contains("\"Отчёт; и ещё \"\"кавычки\"\"\""))
    }

    @Test
    fun `в csv дата читается человеком`() {
        val csv = Backup.eventsCsv(
            listOf(Event(id = 1, type = "session_started", at = 1_760_000_000_000))
        )
        // 1760000000000 = 9 октября 2025, 08:53 UTC
        assertTrue(csv, csv.contains("2025-10-09 08:53"))
    }

    @Test
    fun `дата в csv показывается в местном времени`() {
        Backup.zoneOffset = { 3 * 60 * 60 * 1000L }   // Москва
        try {
            val csv = Backup.eventsCsv(
                listOf(Event(id = 1, type = "session_started", at = 1_760_000_000_000))
            )
            // Заход в 11:53 по-местному не должен превратиться в 08:53
            assertTrue(csv, csv.contains("2025-10-09 11:53"))
        } finally {
            Backup.zoneOffset = { 0 }
        }
    }

    @Test
    fun `сдвиг пояса может перевести запись на другую дату`() {
        Backup.zoneOffset = { 3 * 60 * 60 * 1000L }
        try {
            // 2025-10-09 22:13 UTC — по Москве это уже десятое
            val csv = Backup.eventsCsv(listOf(Event(id = 1, type = "x", at = 1_760_048_000_000)))
            assertTrue(csv, csv.contains("2025-10-10 01:13"))
        } finally {
            Backup.zoneOffset = { 0 }
        }
    }

    @Test
    fun `в csv пустая дата остаётся пустой`() {
        val csv = Backup.tasksCsv(listOf(Task(id = 1, title = "Т", firstStep = "Ш", createdAt = 0)))
        assertTrue(csv, csv.contains("1;Т;Ш;;active;;;"))
    }

    // --- то, что увидит человек перед заменой данных ---

    @Test
    fun `в описании копии числа согласованы`() {
        assertEquals(
            "1 задача, 1 заход, 1 событие",
            BackupData(
                tasks = listOf(Task(title = "т", firstStep = "ш")),
                sessions = listOf(Session(taskId = null, mode = "pomodoro", plannedSeconds = 0, startedAt = 0)),
                events = listOf(Event(type = "x")),
            ).summary
        )
        assertEquals(
            "2 задачи, 3 захода, 4 события",
            BackupData(
                tasks = List(2) { Task(title = "т", firstStep = "ш") },
                sessions = List(3) { Session(taskId = null, mode = "p", plannedSeconds = 0, startedAt = 0) },
                events = List(4) { Event(type = "x") },
            ).summary
        )
        assertEquals(
            "11 задач, 12 заходов, 25 событий",
            BackupData(
                tasks = List(11) { Task(title = "т", firstStep = "ш") },
                sessions = List(12) { Session(taskId = null, mode = "p", plannedSeconds = 0, startedAt = 0) },
                events = List(25) { Event(type = "x") },
            ).summary
        )
        assertEquals("0 задач, 0 заходов, 0 событий", BackupData().summary)
    }
}
