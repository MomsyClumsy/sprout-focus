package com.sprout.focus.data

/**
 * Эксперименты над собой — расчётная часть.
 *
 * Как [Insights] и [Reminder], этот файл не знает ни про Android, ни про базу.
 * Формулировки живут здесь же: тон — единственное, что нельзя проверить
 * запуском, зато рядом с расчётом его видно и можно закрыть тестом.
 *
 * Главное правило этапа: **приложение меняет своё поведение, а не требует
 * от человека нового**. Эксперимент «ставь сложную задачу первой» проверить
 * нечем — приложение не знает, выполнила ли его человек, и в итоге спросило
 * бы. А вопрос «ты сегодня соблюдала?» — это ещё одно требование к тому,
 * кто пришёл сюда, потому что не справляется с уже имеющимися.
 *
 * Второе правило: гипотезу предлагаем, только увидев слабое место в данных.
 * Эксперимент, предложенный «просто так», — выдумка, а человек поверит
 * и потратит на неё неделю.
 */
object Experiments {

    /** Сколько длится эксперимент. */
    const val DAYS = 7

    /**
     * Сколько наблюдений нужно, чтобы вывод что-то значил.
     *
     * Тот же порядок, что у порогов экрана «Я»: там четыре сессии в группе.
     * Здесь пять — вывод громче, он предлагает поменять привычку.
     */
    const val MIN_OBSERVATIONS = 5

    /** Разница меньше этой — шум, а не результат. В процентных пунктах. */
    const val MIN_GAP_PERCENT = 15

    /** Длина «короткого» захода. Совпадает с умолчанием приложения. */
    const val SHORT_MINUTES = 20

    /** Выше этого доводимость и так хорошая — улучшать нечего. */
    const val GOOD_ENOUGH_PERCENT = 85

    /** Меньше этой доли задач с планом — есть что проверять. */
    const val FEW_PLANS_PERCENT = 50

    const val SHORTER = "shorter_sessions"
    const val IF_THEN = "if_then_plan"

    const val OUTCOME_CONFIRMED = "confirmed"
    const val OUTCOME_NO_EFFECT = "no_effect"
    const val OUTCOME_NOT_ENOUGH = "not_enough"
    const val OUTCOME_STOPPED = "stopped"

    /**
     * Что приложение знает о человеке на момент выбора гипотезы.
     *
     * Всё за то же окно, что и наблюдения на экране «Я».
     */
    data class Facts(
        val sessions: Int = 0,
        val completedPercent: Int = 0,
        /**
         * Сколько раз человек садился на заход длиннее короткого.
         *
         * Смотрим именно на них, а не на самую частую длину: пока проверка
         * шла по «типичной», слабое место пряталось. У человека с двадцатью
         * заходами по 20 минут и восемью по 45 самая частая длина — 20,
         * и приложение молчало, хотя длинные доводились вдвое хуже.
         */
        val longSessions: Int = 0,
        /** Самая частая из длинных длин, минут. Для формулировки. */
        val longMinutes: Int = 0,
        val tasksCreated: Int = 0,
        val tasksWithPlanPercent: Int = 0,
        /** Гипотезы, которые уже проверялись: повторять то же самое незачем. */
        val tried: Set<String> = emptySet(),
    )

    /**
     * Гипотеза целиком, человеческим языком.
     *
     * [statement] — что проверяем, [change] — что приложение сделает само,
     * [measure] — что будет посчитано. Три разные вещи, и человек имеет
     * право видеть все три до того, как согласится на неделю.
     */
    data class Hypothesis(
        val key: String,
        val title: String,
        val statement: String,
        val change: String,
        val measure: String,
    )

    /**
     * Что предложить проверить.
     *
     * null — значит нечего: данных мало или слабых мест не видно. Это
     * нормальный ответ, а не ошибка; карточка тогда не появляется вовсе.
     */
    fun pick(facts: Facts): Hypothesis? {
        val candidates = listOfNotNull(
            shorter(facts).takeIf { fitsShorter(facts) },
            ifThen(facts).takeIf { fitsIfThen(facts) },
        ).filter { it.key !in facts.tried }

        // Когда подходят обе, берём ту, где наблюдения набираются быстрее:
        // сессий за неделю случается больше, чем новых задач, и вывод
        // придёт раньше. Незанятая гипотеза никуда не денется.
        return candidates.firstOrNull()
    }

    fun byKey(key: String, facts: Facts = Facts()): Hypothesis? = when (key) {
        SHORTER -> shorter(facts)
        IF_THEN -> ifThen(facts)
        else -> null
    }

    private fun fitsShorter(f: Facts): Boolean =
        f.sessions >= MIN_OBSERVATIONS &&
            f.longSessions >= MIN_OBSERVATIONS &&
            f.completedPercent < GOOD_ENOUGH_PERCENT

    private fun fitsIfThen(f: Facts): Boolean =
        f.tasksCreated >= MIN_OBSERVATIONS &&
            f.tasksWithPlanPercent < FEW_PLANS_PERCENT

    private fun shorter(f: Facts) = Hypothesis(
        key = SHORTER,
        title = "Короткие заходы",
        statement = if (f.longMinutes > 0) {
            "Ты регулярно берёшь заходы по ${f.longMinutes} минут. Проверим, " +
                "не будешь ли ты доходить до конца чаще, если заход будет $SHORT_MINUTES."
        } else {
            "Проверим, не будешь ли ты доходить до конца чаще, если заход будет $SHORT_MINUTES минут."
        },
        change = "Неделю в выборе длины сразу будут короткие заходы — 15, $SHORT_MINUTES и 25 минут. " +
            "45 и «Поток» никуда не денутся: они будут под кнопкой «Ещё». " +
            "Это лишнее движение, а не запрет.",
        measure = "Считаем, какая доля заходов дошла до конца.",
    )

    private fun ifThen(f: Facts) = Hypothesis(
        key = IF_THEN,
        title = "План на первый шаг",
        statement = "Проверим, чаще ли ты садишься за задачу, у которой с самого начала " +
            "есть план «если — то».",
        change = "Неделю у новой задачи нужно будет заполнить план. Это самый сильный " +
            "из проверенных приёмов против откладывания, и он занимает одну строку.",
        measure = "Считаем, за какую долю новых задач ты села хотя бы раз.",
    )

    /**
     * Какой сегодня день эксперимента, от 1 до [DAYS].
     *
     * Считаем сутками от момента старта, а не по календарю: неделя,
     * начатая в среду вечером, кончается в среду вечером — так честнее,
     * чем обрубать первый день до нескольких часов.
     */
    fun dayNumber(startedAt: Long, now: Long): Int {
        val passed = ((now - startedAt) / DAY_MILLIS).toInt()
        return (passed + 1).coerceIn(1, DAYS)
    }

    fun endsAt(startedAt: Long): Long = startedAt + DAYS * DAY_MILLIS

    fun isOver(startedAt: Long, now: Long): Boolean = now >= endsAt(startedAt)

    /**
     * Что показать про ход эксперимента.
     *
     * Долями, а не процентами: «3 из 4» человек проверяет, а «75%» на
     * четырёх наблюдениях выглядит точнее, чем есть на самом деле.
     */
    fun progressText(done: Int, total: Int, hypothesis: String): String = when {
        total == 0 && hypothesis == IF_THEN -> "Пока новых задач не было."
        total == 0 -> "Пока заходов не было."
        // Конструкция намеренно безличная: «0 заходов из 1 дошли до конца»
        // спотыкается на согласовании ровно там, где чисел меньше всего, —
        // то есть в первые дни, когда на эту строку и смотрят
        hypothesis == IF_THEN -> "Задач, за которые ты села: $done из $total"
        else -> "Дошли до конца: $done из $total"
    }

    /** «Обычно у тебя …» — цифра до начала эксперимента, чтобы было с чем сравнить. */
    fun baselineText(baselinePercent: Int, hypothesis: String): String = when (hypothesis) {
        IF_THEN -> "Раньше ты садилась за $baselinePercent% задач."
        else -> "Раньше до конца доходило $baselinePercent% заходов."
    }

    const val EMPTY_TEXT =
        "Пока рано что-то проверять. Гипотеза появится сама, когда наберётся история."

    /** 1 заход, 2 захода, 5 заходов. */
    fun sessionsWord(n: Int): String = plural(n, "заход", "захода", "заходов")

    /** 1 задачу, 2 задачи, 5 задач. */
    fun tasksWord(n: Int): String = plural(n, "задачу", "задачи", "задач")

    private fun plural(n: Int, one: String, few: String, many: String): String {
        val ones = n % 10
        val tens = n % 100
        return when {
            tens in 11..14 -> many
            ones == 1 -> one
            ones in 2..4 -> few
            else -> many
        }
    }

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
}
