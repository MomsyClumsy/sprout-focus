package com.sprout.focus.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Сырые числа раздела «Все цифры». Без выводов — просто что было. */
data class Totals(
    val sessions: Int = 0,
    val focusMinutes: Int = 0,
    val completedPercent: Int = 0,
    val tasksCreated: Int = 0,
    val tasksCompleted: Int = 0,
    val postponed: Int = 0,
    /**
     * Барьер отвлечений. Показывается только если он вообще срабатывал:
     * строчка «Отвлечений поймано: 0» у человека без барьера — это упрёк
     * за то, чего он не включал.
     */
    val distractionsCaught: Int = 0,
    val distractionsReturned: Int = 0,
)

/** Всё содержимое экрана «Я» одним снимком. */
data class MeState(
    val cards: List<Insights.Card> = emptyList(),
    val totals: Totals = Totals(),
)

/**
 * Достаёт из базы то, из чего [Insights] делает наблюдения.
 *
 * Граница проходит здесь: репозиторий знает про базу и про формат payload,
 * но не считает и не формулирует. Всё, что можно посчитать в голове, живёт
 * в [Insights] и проверяется тестом без устройства.
 */
class InsightsRepository(private val dao: SproutDao) {

    /** Начало окна наблюдения. Считается на момент подписки, этого достаточно. */
    private fun windowStart(): Long =
        System.currentTimeMillis() - Insights.WINDOW_DAYS * 24L * 60 * 60 * 1000

    fun state(): Flow<MeState> {
        val since = windowStart()

        val postpones = dao.observeEventsOfType(EventType.TASK_POSTPONED, since)
            .map { events ->
                events.mapNotNull { e ->
                    Payload.string(e.payload, "reason")?.let { Insights.Postpone(it, e.at) }
                }
            }

        val sessions = dao.observeFinishedSessions(since)

        val counts = combine(
            dao.observeEventCount(EventType.TASK_CREATED, since),
            dao.observeEventCount(EventType.TASK_COMPLETED, since),
            dao.observeEventCount(EventType.DISTRACTION_CAUGHT, since),
            dao.observeEventCount(EventType.DISTRACTION_RETURNED, since),
        ) { created, completedTasks, caught, returned ->
            listOf(created, completedTasks, caught, returned)
        }

        return combine(
            postpones,
            sessions,
            dao.observeFocusMinutes(since),
            counts,
        ) { reasons, finished, minutes, byType ->
            val (created, completedTasks, caught, returned) = byType
            val outcomes = finished.map {
                Insights.SessionOutcome(it.plannedSeconds, it.completed)
            }
            MeState(
                cards = Insights.cards(reasons, outcomes),
                totals = Totals(
                    sessions = finished.size,
                    focusMinutes = minutes,
                    completedPercent = Insights.percent(finished.count { it.completed }, finished.size),
                    tasksCreated = created,
                    tasksCompleted = completedTasks,
                    postponed = reasons.size,
                    distractionsCaught = caught,
                    distractionsReturned = returned,
                ),
            )
        }
    }
}
