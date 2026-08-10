package com.sprout.focus.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Всё, что экрану нужно знать про эксперимент. */
data class ExperimentState(
    /** Идёт прямо сейчас. null — не идёт ничего. */
    val running: Experiment? = null,
    /** Описание идущего или предложенного — смотря что есть. */
    val hypothesis: Experiments.Hypothesis? = null,
    /** Предложение начать. Появляется, только если в данных видно слабое место. */
    val offered: Boolean = false,
    val dayNumber: Int = 0,
    val done: Int = 0,
    val total: Int = 0,
) {
    val hasSomethingToShow: Boolean get() = hypothesis != null
}

/**
 * Эксперименты: что предложить, что идёт и как приложение из-за этого
 * меняет своё поведение.
 *
 * Граница та же, что у [InsightsRepository]: здесь знают про базу, но не
 * считают и не формулируют — всё это в [Experiments] и проверяется тестом
 * без устройства.
 */
class ExperimentRepository(private val dao: SproutDao) {

    /** Окно, за которое смотрим на человека. То же, что у наблюдений. */
    private fun windowStart(now: Long = System.currentTimeMillis()): Long =
        now - Insights.WINDOW_DAYS * DAY_MILLIS

    val running: Flow<Experiment?> = dao.observeRunningExperiment()

    fun state(): Flow<ExperimentState> {
        val since = windowStart()
        return combine(
            dao.observeRunningExperiment(),
            dao.observeFinishedSessions(since),
            dao.observeTasksCreatedSince(since),
        ) { experiment, sessions, tasks ->
            val now = System.currentTimeMillis()
            if (experiment == null) {
                val facts = facts(sessions, tasks, dao.triedHypotheses().toSet())
                val offer = Experiments.pick(facts)
                ExperimentState(hypothesis = offer, offered = offer != null)
            } else {
                val counted = count(experiment, sessions, tasks)
                ExperimentState(
                    running = experiment,
                    hypothesis = Experiments.byKey(
                        experiment.hypothesis,
                        facts(sessions, tasks, emptySet()),
                    ),
                    dayNumber = Experiments.dayNumber(experiment.startedAt, now),
                    done = counted.first,
                    total = counted.second,
                )
            }
        }
    }

    /**
     * Начать неделю.
     *
     * Базовый уровень считается **здесь и сейчас** и больше не пересчитывается:
     * иначе к концу недели «раньше у тебя было столько-то» сравнивалось бы
     * с самим собой, уже испорченным экспериментом.
     */
    suspend fun start(hypothesis: String): Long {
        dao.getRunningExperiment()?.let { return it.id }

        val now = System.currentTimeMillis()
        val since = windowStart(now)
        val sessions = dao.finishedSessions(since)
        val tasks = dao.tasksCreatedSince(since)

        val (part, whole) = when (hypothesis) {
            Experiments.IF_THEN -> dao.startedTaskCount(since) to tasks.size
            else -> {
                val planned = sessions.filter { it.plannedSeconds > 0 }
                planned.count { it.completed } to planned.size
            }
        }

        val id = dao.insertExperiment(
            Experiment(
                hypothesis = hypothesis,
                startedAt = now,
                endsAt = Experiments.endsAt(now),
                baselinePercent = Insights.percent(part, whole),
                baselineCount = whole,
            )
        )
        dao.insertEvent(
            Event(
                type = EventType.EXPERIMENT_STARTED,
                at = now,
                payload = """{"hypothesis":"$hypothesis","baseline":${Insights.percent(part, whole)}}""",
            )
        )
        return id
    }

    /**
     * Прервать.
     *
     * Без уговоров и без «ты уверена?»: неделя, которую человек тащит из
     * чувства долга, не даст честного результата — она даст ещё один повод
     * считать себя виноватой.
     */
    suspend fun stop() {
        val running = dao.getRunningExperiment() ?: return
        val now = System.currentTimeMillis()
        dao.updateExperiment(
            running.copy(endedAt = now, outcome = Experiments.OUTCOME_STOPPED)
        )
        dao.insertEvent(
            Event(
                type = EventType.EXPERIMENT_ENDED,
                at = now,
                payload = """{"hypothesis":"${running.hypothesis}","outcome":"${Experiments.OUTCOME_STOPPED}"}""",
            )
        )
    }

    // --- как эксперимент меняет поведение приложения ---

    /**
     * Сколько минут предлагать по умолчанию.
     *
     * null — эксперимент про длину не идёт, умолчание обычное. Предложение,
     * а не запрет: выбрать другую длину человек может как всегда, иначе это
     * была бы не проверка гипотезы, а наказание за несогласие.
     */
    suspend fun suggestedMinutes(): Int? =
        dao.getRunningExperiment()
            ?.takeIf { it.hypothesis == Experiments.SHORTER }
            ?.let { Experiments.SHORT_MINUTES }

    /** Просить ли план у новой задачи. */
    suspend fun planRequired(): Boolean =
        dao.getRunningExperiment()?.hypothesis == Experiments.IF_THEN

    private fun facts(sessions: List<Session>, tasks: List<Task>, tried: Set<String>): Experiments.Facts {
        val planned = sessions.filter { it.plannedSeconds > 0 }
        val long = planned.filter { it.plannedSeconds / 60 > Experiments.SHORT_MINUTES }
        return Experiments.Facts(
            sessions = planned.size,
            completedPercent = Insights.percent(planned.count { it.completed }, planned.size),
            longSessions = long.size,
            longMinutes = long
                .groupingBy { it.plannedSeconds / 60 }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key ?: 0,
            tasksCreated = tasks.size,
            tasksWithPlanPercent = Insights.percent(tasks.count { it.hasPlan }, tasks.size),
            tried = tried,
        )
    }

    /** Сколько наблюдений набралось с начала эксперимента: сделано и всего. */
    private fun count(
        experiment: Experiment,
        sessions: List<Session>,
        tasks: List<Task>,
    ): Pair<Int, Int> = when (experiment.hypothesis) {
        Experiments.IF_THEN -> {
            val since = tasks.filter { it.createdAt >= experiment.startedAt }
            val startedIds = sessions.mapNotNull { it.taskId }.toSet()
            since.count { it.id in startedIds } to since.size
        }
        else -> {
            val since = sessions.filter {
                it.startedAt >= experiment.startedAt && it.plannedSeconds > 0
            }
            since.count { it.completed } to since.size
        }
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
