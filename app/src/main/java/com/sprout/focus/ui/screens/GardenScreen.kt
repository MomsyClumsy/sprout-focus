package com.sprout.focus.ui.screens

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sprout.focus.data.Garden
import com.sprout.focus.data.Growth
import com.sprout.focus.ui.PlantArt
import com.sprout.focus.ui.theme.SproutTheme

private val stageNames = listOf("Семя", "Росток", "Побег", "Растение", "Цветение")

@Composable
fun GardenScreen(garden: Garden?, grownCount: Int) {
    val g = garden ?: Garden()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        PlantArt(stage = g.stage, size = 160.dp)

        Spacer(Modifier.height(12.dp))
        Text(stageNames[g.stage], style = MaterialTheme.typography.titleLarge)

        Spacer(Modifier.height(20.dp))

        val toNext = Growth.toNextStage(g.points)
        if (toNext != null) {
            val from = Growth.THRESHOLDS[g.stage]
            val to = Growth.THRESHOLDS[g.stage + 1]
            val progress = ((g.points - from).toFloat() / (to - from)).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Ещё $toNext ${minuteWordGarden(toNext)} до следующей стадии",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                "Растение готово",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(28.dp))

        InfoCard(
            title = if (g.streak > 0) "Серия: ${g.streak} ${dayWord(g.streak)}" else "Серия ещё не начата",
            body = buildString {
                append("Заморозки: ")
                append("●".repeat(g.freezesLeft))
                append("○".repeat((Growth.FREEZES_PER_MONTH - g.freezesLeft).coerceAtLeast(0)))
                append("  ")
                append(g.freezesLeft)
                append(" из ")
                append(Growth.FREEZES_PER_MONTH)
                append("\nПропуск дня не обнуляет серию — сначала тратится заморозка. Они восстанавливаются каждый месяц.")
            }
        )

        Spacer(Modifier.height(12.dp))

        InfoCard(
            title = if (grownCount > 0) "Выросло растений: $grownCount" else "Пока ни одного выросшего",
            body = if (grownCount > 0)
                "Каждое выросшее растение остаётся в коллекции."
            else
                "Растение вырастает примерно за неделю, если заниматься по 20 минут в день."
        )

        Spacer(Modifier.height(12.dp))

        if (g.growthToday > 0) {
            InfoCard(
                title = "Сегодня: ${g.growthToday} ${minuteWordGarden(g.growthToday)}",
                body = if (g.growthToday >= Growth.DAILY_CAP)
                    "Дневной предел роста достигнут. Работать можно сколько угодно — просто растение сегодня уже выросло на максимум."
                else
                    "Дневной предел роста — ${Growth.DAILY_CAP} минут, чтобы один долгий день не обесценил остальные."
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun minuteWordGarden(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m100 in 11..14 -> "минут"
        m10 == 1 -> "минута"
        m10 in 2..4 -> "минуты"
        else -> "минут"
    }
}

private fun dayWord(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m100 in 11..14 -> "дней"
        m10 == 1 -> "день"
        m10 in 2..4 -> "дня"
        else -> "дней"
    }
}

@Preview(name = "Сад · семя", showBackground = true)
@Composable
private fun GardenSeed() = SproutTheme(darkTheme = false) {
    GardenScreen(Garden(), 0)
}

@Preview(name = "Сад · побег", showBackground = true)
@Composable
private fun GardenGrowing() = SproutTheme(darkTheme = false) {
    GardenScreen(
        Garden(points = 52, streak = 5, freezesLeft = 1, growthToday = 20, lastActiveDay = "2026-08-07"),
        2
    )
}

@Preview(name = "Сад · цветение · тёмная", showBackground = true)
@Composable
private fun GardenBloom() = SproutTheme(darkTheme = true) {
    GardenScreen(Garden(points = 140, streak = 12, growthToday = 45), 3)
}
