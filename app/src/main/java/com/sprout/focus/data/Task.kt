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
 * [ifTrigger] + [thenAction] — план «если…, то я…». Не обязателен, но
 * лежит на виду: по мета-анализам это самый сильный приём против
 * откладывания. Позже аналитика сравнит задачи с планом и без.
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
    val hasPlan: Boolean get() = !ifTrigger.isNullOrBlank() && !thenAction.isNullOrBlank()

    val hasReminder: Boolean get() = remindNextAt != null

    /** Что сказать в уведомлении. План точнее первого шага, если он есть. */
    val promise: String get() = thenAction?.takeIf { it.isNotBlank() } ?: firstStep

    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_DONE = "done"
        const val STATUS_DROPPED = "dropped"
    }
}
