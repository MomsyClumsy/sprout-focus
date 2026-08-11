package com.sprout.focus.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Что осталось от закончившихся экспериментов в поведении приложения.
 *
 * Живёт в обычных настройках, а не в базе: это состояние двух тумблеров,
 * а не данные о человеке. В таблице `experiments` при этом отмечено, какую
 * гипотезу закрепляли, — иначе тумблер, однажды выключенный, исчез бы
 * из виду навсегда, и включить его обратно было бы негде.
 */
data class KeptChanges(
    val shortLengthsFirst: Boolean = false,
    val planAlwaysRequired: Boolean = false,
)

/** Всё, что экрану нужно знать про эксперимент. */
data class ExperimentState(
    /** Идёт прямо сейчас. null — не идёт ничего. */
    val running: Experiment? = null,
    /** Неделя вышла, итог посчитан, а человек его ещё не видел. */
    val finished: Experiment? = null,
    /** Описание идущего, законченного или предложенного — смотря что есть. */
    val hypothesis: Experiments.Hypothesis? = null,
    /** Предложение начать. Появляется, только если в данных видно слабое место. */
    val offered: Boolean = false,
    val dayNumber: Int = 0,
    val done: Int = 0,
    val total: Int = 0,
    /** Гипотезы, изменения которых человек когда-либо закреплял. */
    val kept: Set<String> = emptySet(),
    val keptChanges: KeptChanges = KeptChanges(),
) {
    val hasSomethingToShow: Boolean get() = hypothesis != null
}

/**
 * Эксперименты: что предложить, что идёт, чем кончилось и как приложение
 * из-за этого меняет своё поведение.
 *
 * Граница та же, что у [InsightsRepository]: здесь знают про базу, но не
 * считают и не формулируют — всё это в [Experiments] и проверяется тестом
 * без устройства.
 */
class ExperimentRepository(private val dao: SproutDao, context: Context) {

    private val prefs = context.getSharedPreferences("experiments", Context.MODE_PRIVATE)

    /** Итог недели подводится один раз, кто бы за ним ни пришёл первым. */
    private val finishing = Mutex()

    private val _keptChanges = MutableStateFlow(
        KeptChanges(
            shortLengthsFirst = prefs.getBoolean(KEY_SHORT_FIRST, false),
            planAlwaysRequired = prefs.getBoolean(KEY_PLAN_REQUIRED, false),
        )
    )

    /** Что осталось от прошлых экспериментов. Меняется только руками человека. */
    val keptChanges: StateFlow<KeptChanges> = _keptChanges

    /** Окно, за которое смотрим на человека. То же, что у наблюдений. */
    private fun windowStart(now: Long = System.currentTimeMillis()): Long =
        now - Insights.WINDOW_DAYS * DAY_MILLIS

    val running: Flow<Experiment?> = dao.observeRunningExperiment()

    fun state(): Flow<ExperimentState> {
        val since = windowStart()
        return combine(
            dao.observeExperiments(),
            dao.observeFinishedSessions(since),
            dao.observeTasksCreatedSince(since),
            _keptChanges,
        ) { experiments, sessions, tasks, changes ->
            val now = System.currentTimeMillis()
            val running = experiments.firstOrNull { it.isRunning }
            val kept = experiments.filter { it.kept }.map { it.hypothesis }.toSet()
            val base = ExperimentState(kept = kept, keptChanges = changes)

            // Неделя может выйти, пока приложение открыто. Подводим итог тут
            // же: база изменится, и поток придёт сюда ещё раз — уже с готовым
            // результатом. Без этого итог ждал бы следующего запуска.
            if (running != null && Experiments.isOver(running.startedAt, now)) {
                finishIfOver()
                return@combine base.copy(running = running)
            }

            val unresolved = experiments.firstOrNull { it.needsResult }

            when {
                running != null -> {
                    val counted = count(running, sessions, tasks)
                    base.copy(
                        running = running,
                        hypothesis = Experiments.byKey(
                            running.hypothesis,
                            facts(sessions, tasks, emptySet()),
                        ),
                        dayNumber = Experiments.dayNumber(running.startedAt, now),
                        done = counted.first,
                        total = counted.second,
                    )
                }

                unresolved != null -> base.copy(
                    finished = unresolved,
                    hypothesis = Experiments.byKey(
                        unresolved.hypothesis,
                        facts(sessions, tasks, emptySet()),
                    ),
                )

                // После итога — тишина на несколько дней. Следующая гипотеза
                // никуда не денется, а приложение, предлагающее новую неделю
                // сразу после закрытой, само становится требованием.
                pausedUntil(experiments) > now -> base

                else -> {
                    val offer = Experiments.pick(facts(sessions, tasks, experiments.map { it.hypothesis }.toSet()))
                    base.copy(hypothesis = offer, offered = offer != null)
                }
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
     * Подвести итог, если неделя вышла.
     *
     * Вызывается при каждом запуске приложения — и оттуда же, где считается
     * состояние экрана. Человек мог не заходить неделю после конца: итог
     * ждёт его столько, сколько нужно, но считается строго по своим семи
     * суткам. Заходы, случившиеся после конца недели, к эксперименту
     * отношения не имеют, и записывать их в его результат нельзя.
     *
     * Замок нужен именно потому, что вызовов два. Дублировать восстановление
     * в приложении принято — так расставляются будильники и возвращается
     * тишина, — но там повтор ничего не портит. Здесь он пишет второе
     * событие о том же конце недели, и оба вызова успевают прочитать
     * «эксперимент ещё идёт» до того, как первый из них это исправит.
     */
    suspend fun finishIfOver(): Unit = finishing.withLock {
        val running = dao.getRunningExperiment() ?: return@withLock
        val now = System.currentTimeMillis()
        if (!Experiments.isOver(running.startedAt, now)) return@withLock

        // Считаем от старта эксперимента, а не за окно наблюдений: человек
        // мог вернуться через месяц, и тогда его неделя в окно уже не попадёт
        val sessions = dao.finishedSessions(running.startedAt)
        val tasks = dao.tasksCreatedSince(running.startedAt)
        val (done, total) = count(running, sessions, tasks)

        val result = Experiments.result(
            baselinePercent = running.baselinePercent,
            baselineCount = running.baselineCount,
            done = done,
            total = total,
        )

        dao.updateExperiment(
            running.copy(
                // Кончилось тогда, когда вышла неделя, а не когда человек
                // открыл приложение: иначе эксперимент задним числом
                // растянулся бы на всё время его отсутствия
                endedAt = running.endsAt,
                outcome = result.outcome,
                resultPercent = result.resultPercent,
                observations = result.observations,
                succeeded = done,
            )
        )
        dao.insertEvent(
            Event(
                type = EventType.EXPERIMENT_ENDED,
                at = now,
                payload = """{"hypothesis":"${running.hypothesis}","outcome":"${result.outcome}",""" +
                    """"result":${result.resultPercent},"observations":${result.observations}}""",
            )
        )
    }

    /**
     * Человек посмотрел итог и решил.
     *
     * [keep] — оставить изменение насовсем. Закрепление меняет настройку
     * по-настоящему, иначе подтвердившаяся гипотеза не значила бы ничего;
     * но тумблер остаётся на виду и выключается в одно касание — обещание
     * недели не должно превращаться в дверь без ручки.
     */
    suspend fun resolve(keep: Boolean): Unit = finishing.withLock {
        // Тот же замок: два быстрых нажатия на «Закрепить» — это два
        // независимых чтения «итог ещё не закрыт» и две записи о решении
        val finished = dao.unresolvedExperiment() ?: return@withLock
        val now = System.currentTimeMillis()
        dao.updateExperiment(finished.copy(resolvedAt = now, kept = keep))
        if (keep) setKept(finished.hypothesis, true)
        dao.insertEvent(
            Event(
                type = EventType.EXPERIMENT_RESOLVED,
                at = now,
                payload = """{"hypothesis":"${finished.hypothesis}",""" +
                    """"outcome":"${finished.outcome}","kept":$keep}""",
            )
        )
    }

    /**
     * Прервать.
     *
     * Без уговоров и без «ты уверена?»: неделя, которую человек тащит из
     * чувства долга, не даст честного результата — она даст ещё один повод
     * считать себя виноватой. Итог у прерванной недели не показывается:
     * человек уже сказал, что не хочет её продолжать, и разбор был бы
     * возвращением к тому же разговору.
     */
    suspend fun stop() {
        val running = dao.getRunningExperiment() ?: return
        val now = System.currentTimeMillis()
        dao.updateExperiment(
            running.copy(
                endedAt = now,
                outcome = Experiments.OUTCOME_STOPPED,
                resolvedAt = now,
            )
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
        dao.getRunningExperiment()?.hypothesis == Experiments.IF_THEN ||
            _keptChanges.value.planAlwaysRequired

    fun setKept(hypothesis: String, value: Boolean) {
        when (hypothesis) {
            Experiments.IF_THEN -> {
                prefs.edit().putBoolean(KEY_PLAN_REQUIRED, value).apply()
                _keptChanges.update { it.copy(planAlwaysRequired = value) }
            }
            Experiments.SHORTER -> {
                prefs.edit().putBoolean(KEY_SHORT_FIRST, value).apply()
                _keptChanges.update { it.copy(shortLengthsFirst = value) }
            }
        }
    }

    /** До какого момента новых гипотез не предлагаем. */
    private fun pausedUntil(experiments: List<Experiment>): Long =
        experiments.mapNotNull { it.resolvedAt }.maxOrNull()
            ?.plus(Experiments.PAUSE_DAYS * DAY_MILLIS)
            ?: 0L

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

    /**
     * Сколько наблюдений набралось за неделю эксперимента: сделано и всего.
     *
     * Границы недели соблюдаются с обеих сторон. Верхняя важна не меньше
     * нижней: у человека, вернувшегося через три дня после конца, в итог
     * иначе попали бы заходы, к эксперименту не имевшие отношения.
     */
    private fun count(
        experiment: Experiment,
        sessions: List<Session>,
        tasks: List<Task>,
    ): Pair<Int, Int> {
        val week = experiment.startedAt until experiment.endsAt
        val within = sessions.filter { it.startedAt in week }
        return when (experiment.hypothesis) {
            Experiments.IF_THEN -> {
                val since = tasks.filter { it.createdAt in week }
                val startedIds = within.mapNotNull { it.taskId }.toSet()
                since.count { it.id in startedIds } to since.size
            }
            else -> {
                val planned = within.filter { it.plannedSeconds > 0 }
                planned.count { it.completed } to planned.size
            }
        }
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        const val KEY_SHORT_FIRST = "kept_short_first"
        const val KEY_PLAN_REQUIRED = "kept_plan_required"
    }
}
