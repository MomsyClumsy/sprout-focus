package com.sprout.focus.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sprout.focus.data.CantStartReason
import com.sprout.focus.data.Insights
import com.sprout.focus.data.MeState
import com.sprout.focus.data.Totals
import com.sprout.focus.ui.PlantArt
import com.sprout.focus.ui.theme.SproutTheme

/**
 * Экран «Я» — лента наблюдений.
 *
 * Не дашборд: графиков нет намеренно. График показывает, сколько ты сделала,
 * и на плохой неделе превращается в счёт к самой себе. Наблюдение говорит,
 * что с тобой происходит, — на плохой неделе оно нужнее.
 */
@Composable
fun MeScreen(state: MeState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Я", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        if (state.cards.isEmpty()) EmptyNote() else {
            state.cards.forEach { card ->
                InsightCard(card)
                Spacer(Modifier.height(12.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
        AllNumbers(state.totals)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun InsightCard(card: Insights.Card) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(card.fact, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                card.meaning,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Пустой экран тоже что-то говорит.
 *
 * Растение на стадии ростка вместо пустоты: ждать нечего страшного,
 * история просто ещё не набралась.
 */
@Composable
private fun EmptyNote() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PlantArt(stage = 1, size = 96.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            Insights.EMPTY_TEXT,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * «Все цифры» — свёрнуто по умолчанию.
 *
 * Числа без вывода легко читаются как оценка, поэтому они не встречают
 * человека первыми, но и не прячутся: захочешь проверить наблюдение —
 * вот из чего оно посчитано.
 */
@Composable
private fun AllNumbers(totals: Totals) {
    var expanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = if (expanded) "▾ Все цифры" else "▸ Все цифры",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 12.dp)
        )

        AnimatedVisibility(expanded) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "За ${Insights.WINDOW_DAYS} дней",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    NumberRow("Сессий", totals.sessions.toString())
                    NumberRow("Минут фокуса", totals.focusMinutes.toString())
                    NumberRow(
                        "Доведено до конца",
                        if (totals.sessions == 0) "—" else "${totals.completedPercent}%"
                    )

                    HorizontalDivider(Modifier.padding(vertical = 12.dp))

                    NumberRow("Задач создано", totals.tasksCreated.toString())
                    NumberRow("Задач завершено", totals.tasksCompleted.toString())
                    NumberRow("Отложено раз", totals.postponed.toString())
                }
            }
        }
    }
}

@Composable
private fun NumberRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private val sampleState = MeState(
    cards = listOf(
        Insights.Card(
            kind = Insights.KIND_REASON,
            fact = "Чаще всего тебя останавливает страх, что не получится — 11 раз из 18 за месяц.",
            meaning = Insights.reasonMeaning(CantStartReason.ANXIETY),
        ),
        Insights.Card(
            kind = Insights.KIND_DURATION,
            fact = "Сессии по 20 мин ты доводишь до конца в 85% случаев, по 45 — в 40%.",
            meaning = "Короткая сессия, которую ты закончила, продвигает дальше длинной, " +
                "которую бросила. Похоже, твой размер захода — 20 минут.",
        ),
    ),
    totals = Totals(
        sessions = 24, focusMinutes = 480, completedPercent = 71,
        tasksCreated = 9, tasksCompleted = 5, postponed = 18,
    ),
)

@Preview(name = "Я · наблюдения", showBackground = true)
@Composable
private fun MeWithCards() = SproutTheme(darkTheme = false) { MeScreen(sampleState) }

@Preview(name = "Я · пока рано", showBackground = true)
@Composable
private fun MeEmpty() = SproutTheme(darkTheme = false) { MeScreen(MeState()) }

@Preview(name = "Я · наблюдения · тёмная", showBackground = true)
@Composable
private fun MeDark() = SproutTheme(darkTheme = true) { MeScreen(sampleState) }
