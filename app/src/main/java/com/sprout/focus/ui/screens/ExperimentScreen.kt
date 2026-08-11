package com.sprout.focus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sprout.focus.data.Experiment
import com.sprout.focus.data.ExperimentState
import com.sprout.focus.data.Experiments
import com.sprout.focus.ui.theme.SproutTheme

/**
 * Эксперимент над собой.
 *
 * Приложение проверяет гипотезу на данных человека, а не даёт совет из
 * статьи. Разница принципиальная: совет говорит, как бывает у людей,
 * эксперимент — как оказалось у тебя.
 *
 * Условие соблюдает приложение, а не человек: на неделю оно меняет свои
 * умолчания. Спрашивать «ты сегодня выполнила?» здесь нельзя — это ещё
 * одно требование к тому, кто пришёл сюда, потому что не справляется
 * с уже имеющимися.
 */
@Composable
fun ExperimentScreen(
    state: ExperimentState,
    onStart: (String) -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    /** Итог прочитан. true — оставить изменение насовсем. */
    onResolve: (Boolean) -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("← Назад") }
        Spacer(Modifier.height(8.dp))
        Text("Эксперимент", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        val hypothesis = state.hypothesis
        when {
            hypothesis == null -> Text(
                Experiments.EMPTY_TEXT,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.running != null -> Running(state, hypothesis, onStop)

            state.finished != null -> Result(state.finished, hypothesis, onResolve)

            else -> Offer(hypothesis, onStart)
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Offer(hypothesis: Experiments.Hypothesis, onStart: (String) -> Unit) {
    Text(hypothesis.title, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(12.dp))
    Text(hypothesis.statement, style = MaterialTheme.typography.bodyLarge)

    Spacer(Modifier.height(20.dp))
    Block("Что изменится", hypothesis.change)
    Spacer(Modifier.height(10.dp))
    Block("Что посчитаем", hypothesis.measure)
    Spacer(Modifier.height(10.dp))
    Block(
        "Сколько это займёт",
        "${Experiments.DAYS} дней. Прервать можно в любой момент — на результат " +
            "прошлых недель это не влияет.",
    )

    Spacer(Modifier.height(24.dp))
    Button(
        onClick = { onStart(hypothesis.key) },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) { Text("Проверить ${Experiments.DAYS} дней") }
}

@Composable
private fun Running(
    state: ExperimentState,
    hypothesis: Experiments.Hypothesis,
    onStop: () -> Unit,
) {
    val experiment = state.running ?: return

    Text(hypothesis.title, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(12.dp))
    Text(hypothesis.statement, style = MaterialTheme.typography.bodyLarge)

    Spacer(Modifier.height(24.dp))
    Text(
        "День ${state.dayNumber} из ${Experiments.DAYS}",
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(Modifier.height(10.dp))
    DayDots(state.dayNumber)

    Spacer(Modifier.height(20.dp))
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                Experiments.progressText(state.done, state.total, experiment.hypothesis),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                baselineLine(experiment),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    Block("Что изменилось на эту неделю", hypothesis.change)

    Spacer(Modifier.height(24.dp))
    TextButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
        Text("Прервать", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Итог недели.
 *
 * Главное здесь — что итог бывает пустым. «Разницы не видно» и «данных
 * не хватило» показываются так же спокойно, как подтверждение: приложение,
 * которое умеет только подтверждать свои гипотезы, не проверяет их, а
 * уговаривает. Отрицательный результат — тоже ответ, и он экономит неделю
 * следующей проверке.
 *
 * Кнопка «Закрепить» есть только у подтвердившейся гипотезы. Предлагать
 * оставить навсегда то, что не сработало, — значит просить человека
 * поверить приложению вопреки его же данным.
 */
@Composable
private fun Result(
    experiment: Experiment,
    hypothesis: Experiments.Hypothesis,
    onResolve: (Boolean) -> Unit,
) {
    val outcome = experiment.outcome ?: Experiments.OUTCOME_NOT_ENOUGH
    val confirmed = outcome == Experiments.OUTCOME_CONFIRMED

    Text(hypothesis.title, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(4.dp))
    Text(
        "Неделя закончилась",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(20.dp))
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                Experiments.resultTitle(outcome),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                Experiments.resultNumbers(
                    hypothesis = experiment.hypothesis,
                    done = experiment.succeeded ?: 0,
                    total = experiment.observations ?: 0,
                    baselinePercent = experiment.baselinePercent,
                    baselineCount = experiment.baselineCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    Text(
        Experiments.resultMeaning(
            experiment.hypothesis,
            outcome,
            experiment.observations ?: 0,
        ),
        style = MaterialTheme.typography.bodyLarge,
    )

    Spacer(Modifier.height(24.dp))

    if (confirmed) {
        Block("Если закрепить", Experiments.keepText(experiment.hypothesis))
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onResolve(true) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) { Text("Закрепить") }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { onResolve(false) }, modifier = Modifier.fillMaxWidth()) {
            Text("Вернуть как было", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Button(
            onClick = { onResolve(false) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) { Text("Понятно") }
    }
}

/**
 * «Раньше у тебя было столько-то» — и сразу из скольких наблюдений.
 *
 * Процент без числа за ним читается как точный. Если базы почти нет,
 * честнее сказать об этом, чем показать цифру, которой не на что опереться.
 */
private fun baselineLine(experiment: Experiment): String =
    if (experiment.baselineCount < Experiments.MIN_OBSERVATIONS) {
        "Сравнивать пока особо не с чем: до этого набралось всего " +
            "${experiment.baselineCount} ${Experiments.sessionsWord(experiment.baselineCount)}."
    } else {
        Experiments.baselineText(experiment.baselinePercent, experiment.hypothesis)
    }

/** Неделя точками: сколько прошло, сколько осталось. Без процентов и полосок. */
@Composable
private fun DayDots(dayNumber: Int) {
    Row(Modifier.fillMaxWidth()) {
        repeat(Experiments.DAYS) { index ->
            val passed = index < dayNumber
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .padding(end = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (passed) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
            )
        }
    }
}

@Composable
private fun Block(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Карточка на экране «Я».
 *
 * Одна строка про то, что идёт или что можно проверить. Подробности —
 * на своём экране: лента наблюдений не должна превращаться в панель.
 */
@Composable
fun ExperimentCard(state: ExperimentState, onClick: () -> Unit) {
    val hypothesis = state.hypothesis ?: return
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                when {
                    state.running != null ->
                        "Идёт эксперимент · день ${state.dayNumber} из ${Experiments.DAYS}"
                    state.finished != null -> "Неделя закончилась"
                    else -> "Есть что проверить"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(hypothesis.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    state.running != null ->
                        Experiments.progressText(state.done, state.total, hypothesis.key)
                    // На карточке — только заголовок исхода. Разбор ждёт на
                    // своём экране: итог, прочитанный мельком в ленте, легко
                    // сводится к «получилось / не получилось»
                    state.finished != null -> Experiments.resultTitle(
                        state.finished.outcome ?: Experiments.OUTCOME_NOT_ENOUGH
                    ) + " · посмотреть"
                    else -> hypothesis.statement
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val offered = ExperimentState(
    hypothesis = Experiments.byKey(
        Experiments.SHORTER,
        Experiments.Facts(longMinutes = 45),
    ),
    offered = true,
)

private val running = ExperimentState(
    running = Experiment(
        id = 1,
        hypothesis = Experiments.SHORTER,
        startedAt = 0,
        endsAt = 0,
        baselinePercent = 43,
        baselineCount = 12,
    ),
    hypothesis = Experiments.byKey(Experiments.SHORTER, Experiments.Facts(longMinutes = 45)),
    dayNumber = 4,
    done = 3,
    total = 4,
)

@Preview(name = "Эксперимент · предложение", showBackground = true)
@Composable
private fun ExperimentOffer() = SproutTheme(darkTheme = false) {
    ExperimentScreen(offered, {}, {}, {})
}

@Preview(name = "Эксперимент · идёт", showBackground = true)
@Composable
private fun ExperimentRunning() = SproutTheme(darkTheme = true) {
    ExperimentScreen(running, {}, {}, {})
}

@Preview(name = "Эксперимент · нечего проверять", showBackground = true)
@Composable
private fun ExperimentEmpty() = SproutTheme(darkTheme = false) {
    ExperimentScreen(ExperimentState(), {}, {}, {})
}

private fun finishedWith(outcome: String, succeeded: Int, total: Int) = ExperimentState(
    finished = Experiment(
        id = 1,
        hypothesis = Experiments.SHORTER,
        startedAt = 0,
        endsAt = 0,
        baselinePercent = 43,
        baselineCount = 12,
        endedAt = 1,
        outcome = outcome,
        resultPercent = if (total == 0) 0 else succeeded * 100 / total,
        observations = total,
        succeeded = succeeded,
    ),
    hypothesis = Experiments.byKey(Experiments.SHORTER, Experiments.Facts(longMinutes = 45)),
)

@Preview(name = "Итог · подтвердилось", showBackground = true)
@Composable
private fun ExperimentConfirmed() = SproutTheme(darkTheme = false) {
    ExperimentScreen(finishedWith(Experiments.OUTCOME_CONFIRMED, 8, 10), {}, {}, {})
}

@Preview(name = "Итог · разницы не видно", showBackground = true)
@Composable
private fun ExperimentNoEffect() = SproutTheme(darkTheme = true) {
    ExperimentScreen(finishedWith(Experiments.OUTCOME_NO_EFFECT, 5, 11), {}, {}, {})
}

@Preview(name = "Итог · данных не хватило", showBackground = true)
@Composable
private fun ExperimentNotEnough() = SproutTheme(darkTheme = false) {
    ExperimentScreen(finishedWith(Experiments.OUTCOME_NOT_ENOUGH, 0, 1), {}, {}, {})
}
