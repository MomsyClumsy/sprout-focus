package com.sprout.focus

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.sprout.focus.data.EventType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Сквозной путь: от пустого экрана до записанной в базу сессии.
 *
 * Этапы проверялись поодиночке, и каждый по отдельности работал. Этот тест
 * про другое: что они работают **подряд** — задача заводится, становится
 * текущей, за неё садятся, сессия переживает паузу, а в базе после всего
 * этого оказывается ровно то, из чего потом считаются наблюдения.
 *
 * Проверка идёт и по экрану, и по базе. Экран может показать правдоподобное,
 * не записав ничего, — и заметить это можно только заглянув в базу.
 *
 * **Тест работает с настоящей базой приложения**, поэтому запускать его
 * можно на эмуляторе, но не на телефоне, где живут настоящие задачи.
 */
@RunWith(AndroidJUnit4::class)
class MainFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /**
     * Разрешение на уведомления выдаём заранее.
     *
     * Иначе первый же запуск встретит тест системным диалогом поверх экрана,
     * и он повиснет на кнопке, которой не видно. Человеку этот диалог нужен —
     * без уведомлений не будет ни отсчёта в шторке, ни сигнала об окончании.
     */
    @get:Rule
    val notifications: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            GrantPermissionRule.grant()
        }

    private val app get() = rule.activity.application as SproutApplication

    @Test
    fun задача_заводится_и_за_неё_садятся() {
        val title = "Проверка e2e ${System.currentTimeMillis()}"
        val step = "Открыть файл и написать заголовок"

        // --- завести задачу ---
        rule.onNodeWithText("Задачи").performClick()
        rule.waitUntil(TIMEOUT) {
            rule.countOf("Добавить задачу") > 0
        }
        rule.onAllNodesWithText("Добавить задачу").onFirst().performClick()

        rule.onNodeWithText("Что нужно сделать?").performTextInput(title)
        rule.onNodeWithText("Первый шаг").performTextInput(step)
        rule.onNodeWithText("Сохранить").performClick()

        // Задача видна в списке — и записана в базе
        rule.waitUntil(TIMEOUT) { rule.countOf(title) > 0 }
        val task = runBlocking { app.database.dao().newestActiveTask() }
        assertNotNull(task)
        assertEquals(title, task!!.title)

        // --- сделать текущей и сесть за неё ---
        rule.onNodeWithText(title).performClick()
        rule.onNodeWithText("Сегодня").performClick()

        rule.waitUntil(TIMEOUT) { rule.countOf("СЕЙЧАС") > 0 }
        rule.onNodeWithText(step).assertIsDisplayed()

        rule.onNodeWithText("15").performClick()
        rule.onNodeWithText("Начать · 15 мин").performClick()

        // --- сессия ---
        rule.waitUntil(TIMEOUT) { rule.countOf("Пауза") > 0 }

        val started = runBlocking { app.database.dao().getActiveSession() }
        assertNotNull("сессия не завелась", started)
        assertEquals(15 * 60, started!!.plannedSeconds)

        // Пауза и возвращение: таймер считает от часов, а не тикает в памяти,
        // и именно на паузе это ломалось бы заметнее всего
        rule.onNodeWithText("Пауза").performClick()
        rule.waitUntil(TIMEOUT) { rule.countOf("Продолжить") > 0 }
        assertTrue(runBlocking { app.database.dao().getActiveSession()!!.isPaused })

        rule.onNodeWithText("Продолжить").performClick()
        rule.waitUntil(TIMEOUT) { rule.countOf("Пауза") > 0 }

        // --- закончить раньше времени ---
        rule.onNodeWithText("Завершить").performClick()
        rule.waitUntil(TIMEOUT) { rule.countOf("Как пошло?") > 0 }
        rule.onNodeWithText("Готово").performClick()

        // --- что осталось в базе ---
        rule.waitUntil(TIMEOUT) {
            runBlocking { app.database.dao().getActiveSession() } == null
        }

        runBlocking {
            val dao = app.database.dao()
            val finished = dao.getSession(started.id)!!
            assertNotNull("сессия не закрыта", finished.endedAt)
            // Завершили руками, а не по звонку — значит не доведена до конца.
            // Разница между этими двумя случаями и есть то, из чего потом
            // считается доводимость на экране «Я»
            assertTrue("заход помечен доведённым, хотя его прервали", !finished.completed)

            val events = dao.observeEventsOfType(EventType.SESSION_STARTED, 0).first()
            assertTrue("нет события о начале захода", events.any { it.taskId == task.id })
        }
    }

    private companion object {
        /**
         * Сколько ждать появления элемента.
         *
         * Щедро: холодный старт Compose и первое открытие базы на слабой
         * машине занимают заметно больше, чем кажется, а тест, падающий
         * от занятого эмулятора, перестают запускать вовсе.
         */
        const val TIMEOUT = 10_000L
    }
}

/** Сколько на экране элементов с таким текстом. Ноль — значит ещё не появился. */
private fun ComposeTestRule.countOf(text: String): Int =
    onAllNodesWithText(text).fetchSemanticsNodes().size
