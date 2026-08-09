package com.sprout.focus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверяем не столько арифметику, сколько молчание: когда наблюдения
 * показывать нельзя. Ошибка «посчитали неверно» видна глазом на экране,
 * ошибка «сделали вывод из трёх случаев» — нет.
 */
class InsightsTest {

    private fun postpones(vararg reasons: String) =
        reasons.mapIndexed { i, r -> Insights.Postpone(r, at = i.toLong()) }

    private fun sessions(minutes: Int, done: Int, dropped: Int) =
        List(done) { Insights.SessionOutcome(minutes * 60, completed = true) } +
            List(dropped) { Insights.SessionOutcome(minutes * 60, completed = false) }

    private fun times(reason: String, n: Int) = List(n) { reason }.toTypedArray()

    @Test
    fun `мало отказов — карточки нет`() {
        val few = postpones(*times(CantStartReason.ANXIETY, 5), CantStartReason.BOREDOM)
        assertEquals(6, few.size)
        assertNull(Insights.reasonCard(few))
    }

    @Test
    fun `ничья вверху — главной причины нет`() {
        val tie = postpones(
            *times(CantStartReason.ANXIETY, 5),
            *times(CantStartReason.BOREDOM, 5),
        )
        assertNull(Insights.reasonCard(tie))
    }

    /**
     * Случай, который поймался только на устройстве: два отказа против
     * одного — формально лидер, а по сути ничего.
     */
    @Test
    fun `лидер без отрыва — молчим`() {
        val narrow = postpones(
            *times(CantStartReason.ANXIETY, 5),
            *times(CantStartReason.BOREDOM, 4),
        )
        assertNull(Insights.reasonCard(narrow))
    }

    @Test
    fun `явный лидер — считаем и называем`() {
        val clear = postpones(
            *times(CantStartReason.ANXIETY, 6),
            *times(CantStartReason.BOREDOM, 2),
            CantStartReason.NO_ENERGY,
        )
        val card = Insights.reasonCard(clear)!!
        assertEquals(Insights.KIND_REASON, card.kind)
        assertTrue(card.fact, card.fact.contains("страх, что не получится"))
        assertTrue(card.fact, card.fact.contains("6 раз из 9"))
        assertTrue(card.meaning, card.meaning.contains("снижение планки"))
    }

    @Test
    fun `раз склоняется`() {
        assertEquals("раз", Insights.timesWord(1))
        assertEquals("раза", Insights.timesWord(2))
        assertEquals("раза", Insights.timesWord(4))
        assertEquals("раз", Insights.timesWord(5))
        assertEquals("раз", Insights.timesWord(11))
        assertEquals("раз", Insights.timesWord(14))
        assertEquals("раза", Insights.timesWord(22))
        assertEquals("раз", Insights.timesWord(25))
    }

    @Test
    fun `в тексте карточки слово согласовано с числом`() {
        val two = postpones(
            *times(CantStartReason.ANXIETY, 22),
            *times(CantStartReason.BOREDOM, 3),
        )
        assertTrue(Insights.reasonCard(two)!!.fact.contains("22 раза из 25"))
    }

    @Test
    fun `одна длина сессии — сравнивать не с чем`() {
        assertNull(Insights.durationCard(sessions(20, done = 8, dropped = 2)))
    }

    @Test
    fun `редкая длина в сравнение не идёт`() {
        // 45 минут запускались трижды — на таком числе процент ничего не значит
        val mixed = sessions(20, done = 8, dropped = 2) + sessions(45, done = 0, dropped = 3)
        assertNull(Insights.durationCard(mixed))
    }

    @Test
    fun `разница в пределах шума — карточки нет`() {
        val close = sessions(20, done = 5, dropped = 5) + sessions(45, done = 4, dropped = 6)
        assertNull(Insights.durationCard(close))
    }

    @Test
    fun `заметная разница — сравниваем лучшую с худшей`() {
        val gap = sessions(20, done = 9, dropped = 1) + sessions(45, done = 2, dropped = 6)
        val card = Insights.durationCard(gap)!!
        assertEquals(Insights.KIND_DURATION, card.kind)
        assertTrue(card.fact, card.fact.contains("по 20 мин"))
        assertTrue(card.fact, card.fact.contains("90%"))
        assertTrue(card.fact, card.fact.contains("по 45"))
        assertTrue(card.fact, card.fact.contains("25%"))
    }

    @Test
    fun `поток без плана в сравнении не участвует`() {
        val withFlow = sessions(20, done = 9, dropped = 1) + sessions(45, done = 2, dropped = 6) +
            List(10) { Insights.SessionOutcome(plannedSeconds = 0, completed = false) }
        val card = Insights.durationCard(withFlow)!!
        assertTrue(card.fact, !card.fact.contains("по 0"))
    }

    /**
     * Словарь тона из спецификации. Приложение не вправе предъявлять счёт —
     * человек и так пришёл сюда с чувством, что не справляется.
     */
    @Test
    fun `ни одна формулировка не упрекает`() {
        val forbidden = listOf("должна", "должен", "обещал", "провал", "лень", "не смогла", "опять")
        val texts = listOf(Insights.EMPTY_TEXT) +
            listOf(
                CantStartReason.ANXIETY, CantStartReason.BOREDOM, CantStartReason.NO_ENERGY,
                CantStartReason.TOO_BIG, CantStartReason.NO_MEANING, CantStartReason.DISTRACTED,
            ).flatMap { listOf(Insights.reasonName(it)!!, Insights.reasonMeaning(it)) }

        for (text in texts) {
            val lower = text.lowercase()
            for (word in forbidden) {
                assertTrue("«$word» в тексте: $text", !lower.contains(word))
            }
        }
    }

    @Test
    fun `payload читается как есть`() {
        val payload = """{"reason":"anxiety","resolvedWith":"split"}"""
        assertEquals("anxiety", Payload.string(payload, "reason"))
        assertEquals("split", Payload.string(payload, "resolvedWith"))
        assertNull(Payload.string(payload, "mode"))
        assertNull(Payload.string("""{"hasPlan":true}""", "hasPlan"))
    }
}
