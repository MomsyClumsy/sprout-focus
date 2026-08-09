package com.sprout.focus.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Когда сработает напоминание.
 *
 * Вся арифметика времени собрана здесь и не знает ни про Android, ни про базу:
 * её можно посчитать в голове и проверить обычным тестом.
 */
object Reminder {

    /** Маска без единого дня — напоминание разовое. */
    const val ONE_OFF = 0

    /** Все дни недели: понедельник … воскресенье, в порядке для показа. */
    val WEEK: List<DayOfWeek> = listOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
    )

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun isSet(mask: Int, day: DayOfWeek): Boolean = mask and bit(day) != 0

    fun maskOf(days: Collection<DayOfWeek>): Int = days.fold(0) { acc, d -> acc or bit(d) }

    fun daysOf(mask: Int): List<DayOfWeek> = WEEK.filter { isSet(mask, it) }

    /**
     * Ближайший момент после [after], когда должно прийти напоминание.
     *
     * Для разовой маски это ближайшее наступление такого времени: сегодня,
     * если оно ещё впереди, иначе завтра. Для повторяющейся — ближайший
     * подходящий день недели.
     *
     * Возвращает null, только если маска задана, но пуста после проверки —
     * то есть напоминание ставить некуда.
     */
    fun nextAt(
        minuteOfDay: Int,
        daysMask: Int,
        after: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        val fromDate = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()

        // Восьми дней хватает: за неделю встретится любой выставленный день,
        // а лишний день закрывает случай «сегодня подходит, но время прошло».
        for (i in 0..7) {
            val date = fromDate.plusDays(i.toLong())
            if (daysMask != ONE_OFF && !isSet(daysMask, date.dayOfWeek)) continue

            // ZonedDateTime.of сам разбирается с переводом часов: если такого
            // времени в этот день не существует, он сдвигает вперёд.
            val at = ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli()
            if (at > after) return at
        }
        return null
    }

    /**
     * Когда напоминание уже сработало.
     *
     * Разовое гаснет — возвращается null. Повторяющееся переезжает
     * на следующий подходящий день.
     */
    fun afterFiring(
        minuteOfDay: Int,
        daysMask: Int,
        firedAt: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? =
        if (daysMask == ONE_OFF) null else nextAt(minuteOfDay, daysMask, firedAt, zone)

    /** «10:00» для показа. */
    fun formatTime(minuteOfDay: Int): String =
        "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    /**
     * «пн вт ср чт пт», «каждый день», «по выходным» или «один раз».
     * Короткие формулировки нужны, чтобы строка помещалась на карточке задачи.
     */
    fun formatDays(mask: Int): String = when (mask) {
        ONE_OFF -> "один раз"
        0b1111111 -> "каждый день"
        0b0011111 -> "по будням"
        0b1100000 -> "по выходным"
        else -> daysOf(mask).joinToString(" ") { shortName(it) }
    }

    fun shortName(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "пн"
        DayOfWeek.TUESDAY -> "вт"
        DayOfWeek.WEDNESDAY -> "ср"
        DayOfWeek.THURSDAY -> "чт"
        DayOfWeek.FRIDAY -> "пт"
        DayOfWeek.SATURDAY -> "сб"
        DayOfWeek.SUNDAY -> "вс"
    }
}
