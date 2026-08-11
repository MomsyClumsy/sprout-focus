package com.sprout.focus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * План одной фразой.
 *
 * Эту строку человек видит каждый день в трёх местах — карточка «Сегодня»,
 * список задач, уведомление, — и ещё раз в форме, пока набирает. Поэтому
 * проверяется не только склейка, но и то, что форма и карточка собирают
 * её из одного и того же.
 */
class TaskPlanTest {

    private fun task(trigger: String?, firstStep: String) =
        Task(title = "Задача", firstStep = firstStep, ifTrigger = trigger)

    @Test
    fun `план читается как одна фраза`() {
        assertEquals(
            "Если попью чай, то открою файл и напишу заголовок",
            task("попью чай", "открою файл и напишу заголовок").planLine
        )
    }

    @Test
    fun `заглавная буква не остаётся в середине фразы`() {
        assertEquals(
            "Если сяду за стол, то открыть документ",
            task("Сяду за стол", "Открыть документ").planLine
        )
    }

    @Test
    fun `аббревиатуру не портим`() {
        assertEquals(
            "Если открою ноутбук, то PDF отправить клиенту",
            task("Открою ноутбук", "PDF отправить клиенту").planLine
        )
    }

    @Test
    fun `без зацепки плана нет`() {
        assertNull(task(null, "открою файл").planLine)
        assertNull(task("", "открою файл").planLine)
        assertNull(task("   ", "открою файл").planLine)
    }

    @Test
    fun `форма и карточка собирают одну и ту же фразу`() {
        // В форме — по частям, чтобы выделить слова человека курсивом;
        // на карточке — строкой. Разъехаться они не должны
        val parts = Task.planParts("попью чай", "открою файл")
        assertEquals(task("попью чай", "открою файл").planLine, parts?.text)
    }

    @Test
    fun `лишние пробелы по краям не попадают во фразу`() {
        assertEquals(
            "Если попью чай, то открою файл",
            task("  попью чай  ", "  открою файл  ").planLine
        )
    }

    @Test
    fun `у задачи без плана вторая половина всё равно первый шаг`() {
        // promise используется в уведомлении и тогда, когда зацепки нет
        assertEquals("Открыть документ", task(null, "Открыть документ").promise)
    }
}
