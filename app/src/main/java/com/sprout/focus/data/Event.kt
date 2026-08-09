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
    const val REMINDER_SET = "reminder_set"
    const val REMINDER_CLEARED = "reminder_cleared"
    const val REMINDER_FIRED = "reminder_fired"
    const val REMINDER_ACCEPTED = "reminder_accepted"
    const val REMINDER_DISMISSED = "reminder_dismissed"
}
