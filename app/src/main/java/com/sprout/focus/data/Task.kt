package com.sprout.focus.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Задача.
 *
 * Ключевое поле — [firstStep]. Оно обязательно и не может быть пустым:
 * микростарт («открыть файл», а не «сделать отчёт») — то, что реально
 * снижает порог входа. Задача без первого шага — это просто пожелание.
 *
 * [ifTrigger] — зацепка, после которой человек возьмётся за первый шаг:
 * «попью чай», «сяду за стол». План не обязателен, но лежит на виду:
 * по мета-анализам это самый сильный приём против откладывания.
 * Позже аналитика сравнит задачи с планом и без.
 *
 * Второй половины у плана больше нет: ею всегда служит [firstStep].
 * Отдельное поле «…то я» спрашивало ровно то, что человек написал строкой
 * выше, — и экран из-за этого читался как придирка. [thenAction] остался
 * только ради задач, заведённых до этой правки.
 */
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val title: String,
    val firstStep: String,

    val ifTrigger: String? = null,
    val thenAction: String? = null,

    val copingPlan: String? = null,     // «если захочется отвлечься, то я…»
    val whyItMatters: String? = null,   // прояснение ценности

    /** Родитель, если задача появилась при разбивке слишком большой. */
    val parentTaskId: Long? = null,

    val status: String = STATUS_ACTIVE,
    val isCurrent: Boolean = false,     // та самая одна задача на экране «Сегодня»

    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,

    val postponeCount: Int = 0,
    val lastStoppedAt: String? = null,  // «на чём остановилась» — эффект Зейгарник

    /**
     * Время напоминания — минуты от полуночи. null означает, что план
     * записан, но телефон о нём молчит.
     *
     * Время лежит отдельно от [ifTrigger] намеренно: триггер остаётся
     * живой фразой («после того как налью кофе»), а напоминанию нужна
     * зацепка, которую можно поставить будильником.
     */
    val remindMinuteOfDay: Int? = null,

    /**
     * Дни недели битовой маской: бит 0 — понедельник, бит 6 — воскресенье.
     * Ноль значит разовое напоминание: сработает один раз и погаснет.
     */
    @ColumnInfo(defaultValue = "0")
    val remindDaysMask: Int = 0,

    /**
     * Когда сработает в следующий раз, в миллисекундах.
     *
     * Хранится вычисленным, а не выводится каждый раз заново: планировщику
     * и загрузке после перезагрузки достаточно одного запроса
     * «у кого напоминание не null», без разбора масок.
     * null — напоминания нет либо разовое уже отработало.
     */
    val remindNextAt: Long? = null,
) {
    /** План есть, если названа зацепка. Что делать — это первый шаг. */
    val hasPlan: Boolean get() = !ifTrigger.isNullOrBlank()

    val hasReminder: Boolean get() = remindNextAt != null

    /**
     * Что сказать в уведомлении — всегда первый шаг.
     *
     * У задач, заведённых до отмены поля «…то я», в [thenAction] лежит своя
     * формулировка, и соблазн показывать её велик: человек писал её сам.
     * Но поправить её ему уже нечем — поля на экране нет. Обещание, которое
     * приложение повторяет каждый день, а изменить нельзя, хуже, чем
     * формулировка чуть менее удачная, но своя и правимая.
     */
    val promise: String get() = firstStep

    /**
     * План одной строкой — везде, где его видит человек: карточка «Сегодня»,
     * список задач, уведомление по триггеру.
     *
     * Формулировка живёт в одном месте намеренно. Разъехавшиеся тексты об
     * одном и том же читаются как два разных обещания, а человек возвращается
     * к своему плану каждый день.
     */
    val planLine: String? get() = planLine(ifTrigger, promise)

    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_DONE = "done"
        const val STATUS_DROPPED = "dropped"

        /**
         * План по частям: слова человека отдельно от слов приложения.
         *
         * Форма выделяет первые курсивом, чтобы план читался как одна мысль,
         * а не как два заполненных поля. Части и склейка живут в одном месте:
         * разъехаться они могут только здесь, и здесь же это ловит тест.
         */
        data class PlanParts(val trigger: String, val promise: String) {
            val text: String get() = "Если $trigger, то $promise"
        }

        /**
         * Та же строка, но для формы, где задачи ещё нет: человек видит
         * свой план целиком до того, как нажмёт «Сохранить».
         *
         * «Если — то» целиком, а не тире между половинами: связка читается
         * как одна мысль, а не как два поля, поставленные рядом. Слова при
         * этом остаются авторскими — приложение не склоняет чужой текст
         * и не переписывает «открыть» в «открою».
         */
        fun planLine(ifTrigger: String?, promise: String): String? =
            planParts(ifTrigger, promise)?.text

        /**
         * Та же фраза, но по частям: слова человека отдельно от слов
         * приложения. Форма выделяет первые курсивом, чтобы план читался
         * как одна мысль, а не как два заполненных поля.
         *
         * Части и склейка живут вместе намеренно: разъехаться они могут
         * только здесь, и здесь же это ловит тест.
         */
        fun planParts(ifTrigger: String?, promise: String): PlanParts? =
            ifTrigger?.trim()?.takeIf { it.isNotEmpty() && promise.isNotBlank() }?.let { trigger ->
                PlanParts(
                    trigger = lowerFirst(trigger),
                    promise = lowerFirst(promise.trim()),
                )
            }

        /**
         * Опустить первую букву — но не у аббревиатур.
         *
         * «Открыть документ» в середине фразы выглядит опечаткой, а «PDF»,
         * превращённый в «pDF», — тем более. Слово целиком из заглавных
         * человек написал нарочно.
         */
        private fun lowerFirst(text: String): String {
            val firstWord = text.substringBefore(' ')
            if (firstWord.length > 1 && firstWord == firstWord.uppercase()) return text
            return text.replaceFirstChar(Char::lowercaseChar)
        }
    }
}
