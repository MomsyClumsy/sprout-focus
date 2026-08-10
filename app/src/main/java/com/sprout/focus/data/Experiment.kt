package com.sprout.focus.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Эксперимент над собой: неделя с изменённым поведением приложения.
 *
 * Единственное состояние в базе, которое не выводится из событий, — потому
 * что оно про будущее: приложение обещало неделю вести себя иначе, и это
 * обещание должно пережить перезапуск.
 *
 * [baselinePercent] заморожен на момент старта намеренно. Если считать его
 * заново каждый раз, цифра «раньше у тебя было 43%» поплывёт по ходу недели
 * и в итоге сравнится сама с собой. Рядом лежит [baselineCount] — из скольких
 * наблюдений она получена: процент без числа за ним легко принять за точный.
 */
@Entity(tableName = "experiments")
data class Experiment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val hypothesis: String,

    val startedAt: Long,
    val endsAt: Long,

    val baselinePercent: Int,
    val baselineCount: Int,

    /** Пока null — эксперимент идёт. */
    val endedAt: Long? = null,

    /** Чем кончилось: подтвердилась, разницы не видно, не хватило данных, прервали. */
    val outcome: String? = null,

    val resultPercent: Int? = null,
    val observations: Int? = null,
) {
    val isRunning: Boolean get() = endedAt == null
}
