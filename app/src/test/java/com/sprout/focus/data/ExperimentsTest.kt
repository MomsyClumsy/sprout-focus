package com.sprout.focus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Как и у наблюдений, проверяем в основном молчание: когда предлагать
 * эксперимент нельзя. Неделя, потраченная на выдуманную гипотезу, стоит
 * дороже, чем неделя без гипотезы вообще.
 */
class ExperimentsTest {

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `пустая история — предлагать нечего`() {
        assertNull(Experiments.pick(Experiments.Facts()))
    }

    @Test
    fun `мало сессий — про длину молчим`() {
        val facts = Experiments.Facts(
            sessions = 4,
            completedPercent = 40,
            longSessions = 4,
            longMinutes = 45,
        )
        assertNull(Experiments.pick(facts))
    }

    @Test
    fun `длинных заходов почти не бывает — менять нечего`() {
        val facts = Experiments.Facts(
            sessions = 12,
            completedPercent = 50,
            longSessions = 1,
            longMinutes = 45,
        )
        assertNull(Experiments.pick(facts))
    }

    @Test
    fun `доводимость и так хорошая — улучшать нечего`() {
        val facts = Experiments.Facts(
            sessions = 12,
            completedPercent = 90,
            longSessions = 8,
            longMinutes = 45,
        )
        assertNull(Experiments.pick(facts))
    }

    @Test
    fun `длинные заходы и низкая доводимость — есть что проверить`() {
        val facts = Experiments.Facts(
            sessions = 12,
            completedPercent = 40,
            longSessions = 8,
            longMinutes = 45,
        )
        val picked = Experiments.pick(facts)!!
        assertEquals(Experiments.SHORTER, picked.key)
        assertTrue(picked.statement, picked.statement.contains("45"))
        assertTrue(picked.statement, picked.statement.contains("20"))
    }

    /**
     * Случай с живых данных: двадцать заходов по 20 минут и восемь по 45,
     * причём длинные доводятся вдвое хуже. Пока критерий смотрел на самую
     * частую длину, он видел «20» и молчал — слабое место пряталось
     * ровно за тем, что человек в основном всё делает правильно.
     */
    @Test
    fun `частые короткие заходы не прячут редкие длинные`() {
        val facts = Experiments.Facts(
            sessions = 28,
            completedPercent = 54,
            longSessions = 8,
            longMinutes = 45,
        )
        assertEquals(Experiments.SHORTER, Experiments.pick(facts)!!.key)
    }

    @Test
    fun `уже проверяли — второй раз не предлагаем`() {
        val facts = Experiments.Facts(
            sessions = 12,
            completedPercent = 40,
            longSessions = 8,
            longMinutes = 45,
            tried = setOf(Experiments.SHORTER),
        )
        assertNull(Experiments.pick(facts))
    }

    @Test
    fun `планов мало — предлагаем проверить план`() {
        val facts = Experiments.Facts(
            tasksCreated = 8,
            tasksWithPlanPercent = 25,
        )
        assertEquals(Experiments.IF_THEN, Experiments.pick(facts)!!.key)
    }

    @Test
    fun `планы и так почти везде — проверять нечего`() {
        val facts = Experiments.Facts(
            tasksCreated = 8,
            tasksWithPlanPercent = 75,
        )
        assertNull(Experiments.pick(facts))
    }

    @Test
    fun `когда подходят обе — берём ту, где данные наберутся быстрее`() {
        val facts = Experiments.Facts(
            sessions = 12,
            completedPercent = 40,
            longSessions = 8,
            longMinutes = 45,
            tasksCreated = 8,
            tasksWithPlanPercent = 25,
        )
        assertEquals(Experiments.SHORTER, Experiments.pick(facts)!!.key)
    }

    @Test
    fun `проверенная гипотеза уступает место непроверенной`() {
        val facts = Experiments.Facts(
            sessions = 12,
            completedPercent = 40,
            longSessions = 8,
            longMinutes = 45,
            tasksCreated = 8,
            tasksWithPlanPercent = 25,
            tried = setOf(Experiments.SHORTER),
        )
        assertEquals(Experiments.IF_THEN, Experiments.pick(facts)!!.key)
    }

    @Test
    fun `день считается сутками от старта и не выходит за неделю`() {
        val start = 1_000_000L
        assertEquals(1, Experiments.dayNumber(start, start))
        assertEquals(1, Experiments.dayNumber(start, start + day - 1))
        assertEquals(2, Experiments.dayNumber(start, start + day))
        assertEquals(7, Experiments.dayNumber(start, start + 6 * day))
        assertEquals(7, Experiments.dayNumber(start, start + 30 * day))
    }

    @Test
    fun `неделя кончается через семь суток`() {
        val start = 1_000_000L
        assertTrue(!Experiments.isOver(start, start + 6 * day))
        assertTrue(Experiments.isOver(start, start + 7 * day))
    }

    /**
     * На малых числах процент выглядит точнее, чем есть. Пока эксперимент
     * идёт, показываем доли — их человек может проверить сам.
     */
    @Test
    fun `ход показывается долями, а не процентами`() {
        val text = Experiments.progressText(3, 4, Experiments.SHORTER)
        assertTrue(text, text.contains("3 из 4"))
        assertTrue(text, !text.contains("%"))
    }

    /** Ноль и единица — те числа, на которых формулировки обычно и ломаются. */
    @Test
    fun `строка хода не спотыкается на нуле и единице`() {
        assertEquals("Дошли до конца: 0 из 1", Experiments.progressText(0, 1, Experiments.SHORTER))
        assertEquals("Дошли до конца: 1 из 1", Experiments.progressText(1, 1, Experiments.SHORTER))
        assertEquals(
            "Задач, за которые ты села: 0 из 1",
            Experiments.progressText(0, 1, Experiments.IF_THEN),
        )
    }

    @Test
    fun `нечего считать — так и говорим`() {
        assertEquals("Пока заходов не было.", Experiments.progressText(0, 0, Experiments.SHORTER))
        assertEquals("Пока новых задач не было.", Experiments.progressText(0, 0, Experiments.IF_THEN))
    }

    @Test
    fun `слова согласованы с числом`() {
        assertEquals("заход", Experiments.sessionsWord(1))
        assertEquals("захода", Experiments.sessionsWord(3))
        assertEquals("заходов", Experiments.sessionsWord(5))
        assertEquals("заходов", Experiments.sessionsWord(11))
        assertEquals("захода", Experiments.sessionsWord(22))
        assertEquals("задачу", Experiments.tasksWord(1))
        assertEquals("задачи", Experiments.tasksWord(2))
        assertEquals("задач", Experiments.tasksWord(7))
    }

    /**
     * Тот же словарь, что и у наблюдений. Эксперимент говорит о человеке
     * ещё увереннее — и тем более не вправе предъявлять ему счёт.
     */
    @Test
    fun `ни одна формулировка не упрекает`() {
        val forbidden = listOf("должна", "должен", "обещал", "провал", "лень", "не смогла", "опять")
        val facts = Experiments.Facts(longMinutes = 45)
        val texts = listOf(Experiments.EMPTY_TEXT) +
            listOf(Experiments.SHORTER, Experiments.IF_THEN).flatMap { key ->
                val h = Experiments.byKey(key, facts)!!
                listOf(h.title, h.statement, h.change, h.measure) +
                    Experiments.baselineText(43, key) +
                    Experiments.progressText(3, 5, key)
            }

        for (text in texts) {
            val lower = text.lowercase()
            for (word in forbidden) {
                assertTrue("«$word» в тексте: $text", !lower.contains(word))
            }
        }
    }

    @Test
    fun `у каждой гипотезы сказано, что изменится и что посчитаем`() {
        for (key in listOf(Experiments.SHORTER, Experiments.IF_THEN)) {
            val h = Experiments.byKey(key)!!
            assertNotNull(h.change)
            assertTrue(h.change.isNotBlank())
            assertTrue(h.measure.isNotBlank())
        }
    }
}
