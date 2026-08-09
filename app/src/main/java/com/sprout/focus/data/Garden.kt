package com.sprout.focus.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Состояние сада: текущее растение и серия.
 *
 * Строка всегда одна (id = 1).
 */
@Entity(tableName = "garden")
data class Garden(
    @PrimaryKey val id: Int = 1,

    /** Очки роста текущего растения. Одна минута фокуса — одно очко. */
    val points: Int = 0,
    val plantStartedAt: Long = 0L,
    val grownCount: Int = 0,

    val streak: Int = 0,
    val lastActiveDay: String? = null,      // yyyy-MM-dd

    /** Прощение серии: две заморозки в месяц, восстанавливаются 1-го числа. */
    val freezesLeft: Int = Growth.FREEZES_PER_MONTH,
    val freezeMonth: String = "",           // yyyy-MM

    /** Дневной потолок роста, чтобы один марафон не обесценил остальные дни. */
    val growthDay: String? = null,
    val growthToday: Int = 0,
) {
    val stage: Int get() = Growth.stage(points)
}

/** Выросшее растение уходит в коллекцию. */
@Entity(tableName = "grown_plants")
data class GrownPlant(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val completedAt: Long,
)

object Growth {
    /**
     * Пять стадий. При ~20 минутах фокуса в день растение вырастает примерно
     * за неделю — как раз перекрывает четвёртую неделю, когда новизна выгорает.
     */
    val THRESHOLDS = listOf(0, 15, 40, 80, 140)
    const val FULL = 140
    const val DAILY_CAP = 45
    const val FREEZES_PER_MONTH = 2

    fun stage(points: Int): Int =
        THRESHOLDS.indexOfLast { points >= it }.coerceIn(0, THRESHOLDS.lastIndex)

    /** Сколько очков до следующей стадии. null — растение уже готово. */
    fun toNextStage(points: Int): Int? =
        THRESHOLDS.firstOrNull { it > points }?.minus(points)
}
