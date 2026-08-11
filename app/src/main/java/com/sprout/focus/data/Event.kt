package com.sprout.focus.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Событие.
 *
 * Всё, что происходит в приложении, пишется сюда и никогда не редактируется.
 * Это позволит потом посчитать любую аналитику задним числом — в том числе ту,
 * которую мы сейчас не придумали. Состояние выводится из событий, а не наоборот.
 */
@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val at: Long = System.currentTimeMillis(),
    val taskId: Long? = null,
    val payload: String = "",
)

object EventType {
    const val TASK_CREATED = "task_created"
    const val TASK_SELECTED = "task_selected"
    const val TASK_EDITED = "task_edited"
    const val TASK_COMPLETED = "task_completed"
    const val TASK_DROPPED = "task_dropped"

    const val SESSION_STARTED = "session_started"
    const val SESSION_PAUSED = "session_paused"
    const val SESSION_RESUMED = "session_resumed"
    const val SESSION_ENDED = "session_ended"

    /** Момент избегания — ради него всё и затевалось. */
    const val TASK_POSTPONED = "task_postponed"
    const val CANT_START_RESOLVED = "cant_start_resolved"

    /**
     * Напоминания по плану «если — то».
     *
     * FIRED пишется отдельно от ACCEPTED и DISMISSED намеренно: разница
     * между «телефон напомнил» и «это помогло начать» — и есть то, ради чего
     * этап затевался. Без FIRED невозможно посчитать, сколько раз
     * напоминание прошло мимо: молчание в базе неотличимо от неслучившегося.
     */
    /**
     * Отвлечение во время сессии.
     *
     * CAUGHT пишется в момент, когда барьер показан, — то есть каждый раз,
     * когда рука сама открыла ленту. Ответ пишется отдельно: «вернулась»
     * и «прошла мимо барьера» — это разные вещи, и разница между ними
     * покажет, работает ли мягкий барьер вообще.
     */
    const val DISTRACTION_CAUGHT = "distraction_caught"
    const val DISTRACTION_RETURNED = "distraction_returned"
    const val DISTRACTION_PASSED = "distraction_passed"

    /**
     * Эксперименты над собой.
     *
     * Само состояние живёт в таблице `experiments` — это единственное, что
     * не выводится из событий. Но начало и конец пишутся и сюда: иначе
     * прошлое приложения будет объяснимо только наполовину, а вопрос
     * «а не в эксперименте ли дело?» встанет к любой цифре задним числом.
     */
    const val EXPERIMENT_STARTED = "experiment_started"
    const val EXPERIMENT_ENDED = "experiment_ended"

    /**
     * Человек прочитал итог и решил, закреплять ли изменение.
     *
     * Отдельно от ENDED, потому что это разные моменты: неделя кончается
     * сама по часам, а решение принимает человек — иногда через три дня.
     * И только по этому событию видно, что итог вообще был кем-то увиден.
     */
    const val EXPERIMENT_RESOLVED = "experiment_resolved"

    const val REMINDER_SET = "reminder_set"
    const val REMINDER_CLEARED = "reminder_cleared"
    const val REMINDER_FIRED = "reminder_fired"
    const val REMINDER_ACCEPTED = "reminder_accepted"
    const val REMINDER_DISMISSED = "reminder_dismissed"
}

/**
 * Достать поле из payload.
 *
 * Payload — маленький плоский JSON, который мы же и собираем. Читать его
 * разбором строки, а не `json_extract` в запросе: минимальная поддерживаемая
 * версия Android — восьмая, а в её системном SQLite функций JSON может
 * не быть вовсе. Тащить ради одного поля библиотеку тоже не за чем.
 */
object Payload {

    /** Значение строкового поля: `{"reason":"anxiety"}` → `anxiety`. */
    fun string(payload: String, key: String): String? {
        val marker = "\"$key\":\""
        val from = payload.indexOf(marker)
        if (from < 0) return null
        val start = from + marker.length
        val end = payload.indexOf('"', start)
        if (end < 0) return null
        return payload.substring(start, end)
    }
}
