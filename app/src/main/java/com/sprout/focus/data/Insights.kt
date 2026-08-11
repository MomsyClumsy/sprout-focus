package com.sprout.focus.data

/**
 * Наблюдения о себе — расчётная часть экрана «Я».
 *
 * Как и [Reminder], этот файл не знает ни про Android, ни про базу: на вход
 * идут готовые факты, на выходе — текст карточки. Формулировки живут здесь же
 * намеренно. Тон — единственное, что в этом приложении нельзя проверить
 * запуском, зато рядом с расчётом его видно и можно закрыть обычным тестом.
 *
 * Общее правило: наблюдение показывается, только если за ним стоят данные.
 * Выдуманный вывод на трёх наблюдениях хуже честного «пока рано» — человек
 * поверит цифре и поменяет из-за неё поведение.
 */
object Insights {

    /** Окно, за которое считаем. Месяц — достаточно, чтобы период был «про сейчас». */
    const val WINDOW_DAYS = 30

    /**
     * Меньше этого числа отказов вывод о причине не делаем.
     *
     * Восьми хватает, чтобы «чаще всего» звучало честно. На пяти получалось
     * «чаще всего тебя останавливает размер задачи — 2 раза из 5»: формально
     * лидер, по сути — два случая. Приложение говорит уверенным тоном,
     * человек ему верит и меняет из-за этого поведение.
     */
    const val MIN_POSTPONES = 8

    /**
     * Во сколько раз лидер должен опережать следующую причину.
     *
     * Без этого 4 против 3 объявляется главной причиной, хотя это одно
     * лишнее нажатие разницы.
     */
    const val LEAD_FACTOR = 2

    /** Сколько сессий нужно в группе, чтобы её процент что-то значил. */
    const val MIN_SESSIONS_PER_GROUP = 4

    /** Разница меньше этой — шум, а не наблюдение. В процентных пунктах. */
    const val MIN_GAP_PERCENT = 15

    /** Отказ начать: причина и когда. */
    data class Postpone(val reason: String, val at: Long)

    /** Итог сессии. Нужны только план и дошла ли она до конца. */
    data class SessionOutcome(val plannedSeconds: Int, val completed: Boolean)

    /**
     * Карточка на экране «Я».
     *
     * [fact] — то, что случилось, цифрами. [meaning] — что из этого следует.
     * Разделены, потому что читаются по-разному: цифру человек проверяет,
     * вывод — примеряет.
     */
    data class Card(val kind: String, val fact: String, val meaning: String)

    const val KIND_REASON = "reason"
    const val KIND_DURATION = "duration"

    /**
     * Главная причина откладывания.
     *
     * Показываем только явного лидера: если первая и вторая причины идут
     * вплотную, никакого «главного» нет, и называть его — обманывать.
     */
    fun reasonCard(postpones: List<Postpone>): Card? {
        if (postpones.size < MIN_POSTPONES) return null

        val byReason = postpones.groupingBy { it.reason }.eachCount()
        val ranked = byReason.entries.sortedByDescending { it.value }
        val top = ranked.first()
        val runnerUp = ranked.getOrNull(1)?.value ?: 0
        if (top.value < runnerUp * LEAD_FACTOR) return null

        val name = reasonName(top.key) ?: return null
        return Card(
            kind = KIND_REASON,
            fact = "Чаще всего тебя останавливает $name — ${top.value} ${timesWord(top.value)} " +
                "из ${postpones.size} за месяц.",
            meaning = reasonMeaning(top.key),
        )
    }

    /** «раз» / «раза»: 1 раз, 2 раза, 5 раз, 12 раз, 22 раза. */
    fun timesWord(n: Int): String {
        val ones = n % 10
        val tens = n % 100
        return when {
            tens in 11..14 -> "раз"
            ones in 2..4 -> "раза"
            else -> "раз"
        }
    }

    /**
     * Длина сессии и доводимость.
     *
     * Сравниваем самую и наименее доводимую длину. Сессии без плана
     * («Поток») не участвуют: у них нет конца, который можно не дойти.
     */
    fun durationCard(sessions: List<SessionOutcome>): Card? {
        val groups = sessions
            .filter { it.plannedSeconds > 0 }
            .groupBy { it.plannedSeconds / 60 }
            .filterValues { it.size >= MIN_SESSIONS_PER_GROUP }
        if (groups.size < 2) return null

        val rates = groups
            .map { (minutes, group) -> minutes to percent(group.count { it.completed }, group.size) }
            .sortedByDescending { it.second }
        val (bestMinutes, bestRate) = rates.first()
        val (worstMinutes, worstRate) = rates.last()
        if (bestRate - worstRate < MIN_GAP_PERCENT) return null

        return Card(
            kind = KIND_DURATION,
            fact = "Сессии по $bestMinutes мин ты доводишь до конца в $bestRate% случаев, по $worstMinutes — в $worstRate%.",
            // Безлично: «которую ты закончила» пришлось бы держать в трёх
            // родах, а вывод от этого не меняется — он про заходы, не про то,
            // кто их делал
            meaning = "Короткий заход, доведённый до конца, продвигает дальше длинного, " +
                "брошенного на середине. Похоже, твой размер захода — $bestMinutes минут.",
        )
    }

    fun cards(postpones: List<Postpone>, sessions: List<SessionOutcome>): List<Card> =
        listOfNotNull(reasonCard(postpones), durationCard(sessions))

    /**
     * Что делать, когда карточек нет.
     *
     * Не «недостаточно данных» — это язык отчёта, а не разговора. И никаких
     * «начни пользоваться активнее»: приложение против прокрастинации не
     * вправе добавлять ещё одно требование.
     */
    const val EMPTY_TEXT = "Пока рано делать выводы. Наблюдения появятся сами, когда наберётся история."

    fun percent(part: Int, whole: Int): Int =
        if (whole == 0) 0 else Math.round(part * 100f / whole)

    /** Названия в винительном падеже: подставляются в «тебя останавливает …». */
    fun reasonName(reason: String): String? = when (reason) {
        CantStartReason.ANXIETY -> "страх, что не получится"
        CantStartReason.BOREDOM -> "скука"
        CantStartReason.NO_ENERGY -> "нехватка сил"
        CantStartReason.TOO_BIG -> "размер задачи"
        CantStartReason.NO_MEANING -> "отсутствие смысла"
        CantStartReason.DISTRACTED -> "отвлечение"
        else -> null
    }

    /**
     * Вывод из причины.
     *
     * Каждый говорит, какой приём работает при этой эмоции, — потому что
     * они разные. Таймер помогает от скуки и бесполезен при тревоге:
     * человеку, который боится результата, лишние двадцать минут наедине
     * с задачей ничего не меняют.
     */
    fun reasonMeaning(reason: String): String = when (reason) {
        CantStartReason.ANXIETY ->
            "Значит помогают не таймеры, а снижение планки: сделать плохую версию, которую никто не увидит."
        CantStartReason.BOREDOM ->
            "Скука лечится не дисциплиной, а компанией: музыка или подкаст, включённые только на время работы."
        CantStartReason.NO_ENERGY ->
            "Это разговор про ресурс, а не про характер. Работает правило пяти минут — без обещания продолжать."
        CantStartReason.TOO_BIG ->
            "Задача велика не для тебя, а для одного захода. Помогает назвать одно первое действие."
        CantStartReason.NO_MEANING ->
            "Это вопрос не воли, а смысла. Стоит записать, зачем это тебе, — или честно отказаться."
        CantStartReason.DISTRACTED ->
            "Дело в обстановке, а не в тебе: телефон подальше, «Не беспокоить» — и захода хватит."
        else -> ""
    }
}
