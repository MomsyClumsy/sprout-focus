package com.sprout.focus.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class GardenRepository(private val dao: SproutDao) {

    val garden: Flow<Garden?> = dao.observeGarden()
    val grownCount: Flow<Int> = dao.observeGrownCount()

    private suspend fun ensure(): Garden =
        dao.getGarden() ?: Garden().also { dao.upsertGarden(it) }

    /**
     * Начисление после сессии.
     *
     * Растёт от минут фокуса, а не от числа задач: иначе выгодно резать работу
     * на мелкие кусочки ради очков — болезнь, на которой сгорела Todoist Karma.
     *
     * Даже сессия короче минуты даёт очко: начатая сессия уже засчитывается,
     * потому что награждаем усилие, а не только результат.
     */
    suspend fun onSessionFinished(actualSeconds: Int, today: LocalDate = LocalDate.now()) {
        var g = ensure()
        val day = today.toString()
        val month = day.substring(0, 7)

        if (g.freezeMonth != month) {
            g = g.copy(freezesLeft = Growth.FREEZES_PER_MONTH, freezeMonth = month)
        }
        if (g.growthDay != day) {
            g = g.copy(growthDay = day, growthToday = 0)
        }

        val allowed = (Growth.DAILY_CAP - g.growthToday).coerceAtLeast(0)
        val minutes = (actualSeconds / 60).coerceAtLeast(1)
        val gained = minOf(minutes, allowed)

        var points = g.points + gained
        var grown = g.grownCount
        val now = System.currentTimeMillis()
        var plantStartedAt = if (g.plantStartedAt == 0L) now else g.plantStartedAt

        // Растение выросло — уходит в коллекцию, начинается новое.
        // Остаток очков переносим, чтобы усилие не пропало.
        if (points >= Growth.FULL) {
            dao.insertGrownPlant(GrownPlant(startedAt = plantStartedAt, completedAt = now))
            grown += 1
            points -= Growth.FULL
            plantStartedAt = now
        }

        val (streak, freezes) = nextStreak(g, day)

        dao.upsertGarden(
            g.copy(
                points = points,
                grownCount = grown,
                plantStartedAt = plantStartedAt,
                growthToday = g.growthToday + gained,
                growthDay = day,
                streak = streak,
                freezesLeft = freezes,
                lastActiveDay = day,
            )
        )
    }

    /**
     * Серия с прощением.
     *
     * Пропуск не обнуляет всё: сначала тратятся заморозки. Обнуление серии —
     * главная причина, по которой люди бросают такие приложения, поэтому
     * даже когда заморозки кончились, счёт начинается заново без упрёка.
     */
    private fun nextStreak(g: Garden, today: String): Pair<Int, Int> {
        val last = g.lastActiveDay ?: return 1 to g.freezesLeft
        if (last == today) return g.streak.coerceAtLeast(1) to g.freezesLeft

        val gap = ChronoUnit.DAYS.between(LocalDate.parse(last), LocalDate.parse(today)).toInt()
        if (gap <= 1) return (g.streak + 1) to g.freezesLeft

        val missed = gap - 1
        return if (missed <= g.freezesLeft) {
            (g.streak + 1) to (g.freezesLeft - missed)
        } else {
            1 to g.freezesLeft
        }
    }
}
