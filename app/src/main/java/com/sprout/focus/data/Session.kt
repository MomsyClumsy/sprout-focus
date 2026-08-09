package com.sprout.focus.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Фокус-сессия.
 *
 * Таймер не крутится в памяти. Мы храним момент старта, а оставшееся время
 * всегда вычисляем от текущих часов. Поэтому сессия переживает сворачивание,
 * перезапуск приложения и даже перезагрузку телефона.
 */
@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val taskId: Long?,
    val mode: String,
    val plannedSeconds: Int,        // 0 — режим без заданной длины

    val startedAt: Long,
    val endedAt: Long? = null,

    val pausedAt: Long? = null,     // если не null — сейчас на паузе
    val pausedTotal: Int = 0,       // сколько всего простояли на паузе, сек

    val actualSeconds: Int? = null,
    val completed: Boolean = false, // дошла до конца, а неброшена

    val selfRating: Int? = null,    // 1 плохо / 2 никак / 3 хорошо
    val interruptions: Int? = null, // 0 нет / 1 пару раз / 2 постоянно
    val stoppedNote: String? = null // «на чём остановилась» — эффект Зейгарник
) {
    val isPaused: Boolean get() = pausedAt != null

    /** Сколько реально отработано к моменту [now]. */
    fun elapsedSeconds(now: Long): Int {
        val gross = (now - startedAt) / 1000
        val currentPause = pausedAt?.let { (now - it) / 1000 } ?: 0
        return (gross - pausedTotal - currentPause).coerceAtLeast(0).toInt()
    }

    /** Сколько осталось. Для режимов без плана вернёт null. */
    fun remainingSeconds(now: Long): Int? {
        if (plannedSeconds <= 0) return null
        return (plannedSeconds - elapsedSeconds(now)).coerceAtLeast(0)
    }

    /** Момент, когда сессия закончится. Нужен будильнику и хронометру уведомления. */
    fun endsAt(now: Long): Long? {
        if (plannedSeconds <= 0) return null
        val remaining = remainingSeconds(now) ?: return null
        return now + remaining * 1000L
    }

    companion object {
        const val MODE_POMODORO = "pomodoro"
        const val MODE_FLOWTIME = "flowtime"
        const val MODE_FREE = "free"
    }
}
