package com.sprout.focus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверка обращения.
 *
 * Тут не «работает ли подстановка», а два обещания, которые приложение
 * даёт человеку в первую минуту знакомства. **Пропустить — законный выбор:**
 * тогда приложение говорит безлично, и ни одного «сама» проскочить не должно.
 * **Имя — не обязательное поле:** пустое остаётся пустым, а не превращается
 * в висящую запятую или в «друг».
 *
 * Ошибка здесь тихая: разработчик видит свой вариант фразы и не видит
 * двух остальных.
 */
class VoiceTest {

    /**
     * Слова, выдающие род.
     *
     * Ловушка настроена на окончания, а не на список слов: забудут именно
     * то слово, которого в списке нет. «Сама» и «сам» отдельно — они не
     * ловятся окончанием.
     */
    private val gendered = Regex(
        // Без \b: в Java он опознаёт только латиницу, и ловушка на кириллице
        // молча не срабатывала бы ни разу — см. проверку ниже
        // «Начала» в список не входит намеренно: в «с самого начала» это
        // не глагол, а такая фраза в приложении есть. Ловушка, ругающаяся
        // на верный текст, кончается тем, что её отключают целиком
        "(?<![а-яё])(сама|сам|села|оставила|оставил|закончила|бросила|" +
            "[а-яё]+(лась|ился|илась|алась))(?![а-яё])"
    )

    private fun genderedWordIn(phrase: String) = gendered.find(phrase.lowercase())?.value

    @Test
    fun `ловушка на род вообще срабатывает`() {
        // Проверка самой проверки: неработающая ловушка выглядит точно так же,
        // как чистый текст, и «безличность» подтверждалась бы сама собой
        assertEquals("сама", genderedWordIn("а начнёшь ты сама."))
        assertEquals("сам", genderedWordIn("Ты сам это оставил"))
        assertEquals("отвлекалась", genderedWordIn("Отвлекалась?"))
        assertEquals("остановилась", genderedWordIn("На чём остановилась?"))
        assertEquals("садилась", genderedWordIn("Раньше ты садилась за 40% задач."))
        assertNull(genderedWordIn("Дошли до конца: 0 из 1"))
        // «Самый» и «самом» — не про род, и ловиться не должны
        assertNull(genderedWordIn("с самого начала"))
    }

    @Test
    fun `безличный вариант действительно безличный`() {
        val neutral = Voice(name = "Марина", gender = Gender.UNKNOWN)
        Phrases.all(neutral).forEach { phrase ->
            assertNull(
                "В безличной фразе род: «${genderedWordIn(phrase)}» — «$phrase»",
                genderedWordIn(phrase),
            )
        }
    }

    @Test
    fun `у каждой родовой фразы все три вида разные`() {
        // Три одинаковых варианта означают, что фразу переписали безлично,
        // но забыли убрать say() — и следующая правка разъедется молча
        Phrases.all(Voice(gender = Gender.FEMININE))
            .zip(Phrases.all(Voice(gender = Gender.MASCULINE)))
            .zip(Phrases.all(Voice(gender = Gender.UNKNOWN)))
            .forEach { (pair, neutral) ->
                val (feminine, masculine) = pair
                assertNotEquals("«сама» и «сам» совпали: $feminine", feminine, masculine)
                assertNotEquals("«сама» и безличное совпали: $feminine", feminine, neutral)
                assertNotEquals("«сам» и безличное совпали: $masculine", masculine, neutral)
            }
    }

    @Test
    fun `женский и мужской варианты отличаются только родом`() {
        // Разойтись успевают именно смыслом: правят одну форму, вторую
        // забывают, и два человека читают в приложении разные обещания
        Phrases.all(Voice(gender = Gender.FEMININE))
            .zip(Phrases.all(Voice(gender = Gender.MASCULINE)))
            .forEach { (feminine, masculine) ->
                assertEquals(
                    "Формы разошлись не только родом:\n$feminine\n$masculine",
                    feminine.replace("сама", "сам").replace("оставила", "оставил"),
                    masculine,
                )
            }
    }

    @Test
    fun `без имени фраза остаётся как написана`() {
        assertEquals("Что мешает?", Voice().ask("Что мешает?"))
        assertEquals("Что мешает?", Voice(name = "").ask("Что мешает?"))
        assertEquals("Что мешает?", Voice(name = "   ").ask("Что мешает?"))
        assertEquals("20 минут фокуса", Voice(name = null).ask("20 минут фокуса"))
    }

    @Test
    fun `с именем фраза начинается с обращения и строчной буквы`() {
        assertEquals("Марина, что мешает?", Voice(name = "Марина").ask("Что мешает?"))
        assertEquals(
            "Марина, меньше минуты — тоже считается",
            Voice(name = "Марина").ask("Меньше минуты — тоже считается"),
        )
        // Фраза с цифры остаётся с цифры, а не теряет первый знак
        assertEquals("Марина, 20 минут фокуса", Voice(name = "Марина").ask("20 минут фокуса"))
    }

    @Test
    fun `пустое имя нигде не оставляет висящей запятой`() {
        listOf(null, "", "   ").forEach { name ->
            val voice = Voice(name = name)
            assertEquals("Обращение из пустого имени: «${voice.address}»", "", voice.address)
            assertNull(voice.nameOrNull)
            assertFalse(voice.isKnown)
            assertFalse(voice.ask("Что мешает?").contains(","))
        }
    }

    @Test
    fun `род без имени всё равно считается знакомством`() {
        // Человек мог назвать только обращение — это ответ, а не пропуск
        assertTrue(Voice(gender = Gender.MASCULINE).isKnown)
        assertTrue(Voice(name = "Марина").isKnown)
        assertFalse(Voice().isKnown)
    }

    @Test
    fun `неизвестный род в настройках читается как безличный`() {
        assertEquals(Gender.UNKNOWN, Gender.of(null))
        assertEquals(Gender.UNKNOWN, Gender.of(""))
        assertEquals(Gender.UNKNOWN, Gender.of("WOMAN"))
        assertEquals(Gender.FEMININE, Gender.of("FEMININE"))
    }
}
