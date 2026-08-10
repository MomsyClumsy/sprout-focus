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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sprout.focus.data.Session
import com.sprout.focus.data.Task
import com.sprout.focus.ui.PlantArt

/**
 * Главный экран. Показываем ровно ОДНУ задачу — список живёт на своей вкладке.
 */
@Composable
fun TodayScreen(
    currentTask: Task?,
    hasOtherTasks: Boolean,
    stage: Int,
    streak: Int,
    onAddTask: () -> Unit,
    onPickTask: () -> Unit,
    onStart: (mode: String, plannedSeconds: Int) -> Unit,
    onCantStart: () -> Unit,
    /** Идёт эксперимент про короткие заходы: длинные — на шаг дальше. */
    shortOnly: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        when {
            currentTask != null ->
                CurrentTask(
                    currentTask, stage, streak, onStart, onCantStart, onPickTask,
                    hasOtherTasks, shortOnly,
                )
            hasOtherTasks -> NothingChosen(stage, onPickTask)
            else -> Empty(onAddTask)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PlantHeader(stage: Int, streak: Int) {
    PlantArt(stage = stage, size = 120.dp)
    Spacer(Modifier.height(8.dp))
    Text(
        text = when {
            streak <= 0 -> "Всё впереди"
            streak == 1 -> "Первый день"
            else -> "Серия: $streak ${dayWord(streak)}"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun CurrentTask(
    task: Task,
    stage: Int,
    streak: Int,
    onStart: (mode: String, plannedSeconds: Int) -> Unit,
    onCantStart: () -> Unit,
    onPickTask: () -> Unit,
    hasOtherTasks: Boolean,
    shortOnly: Boolean,
) {
    // 20 минут по умолчанию. Классические 25 в РКИ 2025 быстрее растили
    // усталость, а жёсткая структура сильнее роняла мотивацию, чем свобода.
    var minutes by remember { mutableIntStateOf(20) }
    val isFlow = minutes == 0

    PlantHeader(stage, streak)

    Spacer(Modifier.height(32.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "СЕЙЧАС",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text(task.title, style = MaterialTheme.typography.titleLarge)

            Spacer(Modifier.height(16.dp))
            Text(
                "Первый шаг",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(task.firstStep, style = MaterialTheme.typography.bodyLarge)

            if (task.hasPlan) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Если ${task.ifTrigger}, то я ${task.thenAction}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (task.lastStoppedAt != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Остановилась на: ${task.lastStoppedAt}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(Modifier.height(20.dp))

    // Выбор длины — рядом с кнопкой, а не в настройках.
    // Автономия в выборе сохраняет мотивацию лучше внешней структуры.
    //
    // Пока идёт эксперимент про короткие заходы, длинные убраны на один шаг
    // дальше — но не убраны совсем. Это тот же мягкий барьер, что и у
    // отвлечений: пройти можно всегда, просто перестаёт получаться на
    // автопилоте. Запрет здесь дал бы не проверку гипотезы, а наказание
    // за несогласие с ней.
    var showAll by remember { mutableStateOf(false) }
    val lengths = if (shortOnly && !showAll) listOf(15, 20, 25) else listOf(15, 20, 25, 45, 0)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        lengths.forEach { m ->
            FilterChip(
                selected = minutes == m,
                onClick = { minutes = m },
                label = { Text(if (m == 0) "Поток" else "$m") }
            )
        }
        if (shortOnly && !showAll) {
            FilterChip(
                selected = false,
                onClick = { showAll = true },
                label = { Text("Ещё") }
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    Button(
        onClick = {
            if (isFlow) onStart(Session.MODE_FLOWTIME, 0)
            else onStart(Session.MODE_POMODORO, minutes * 60)
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Text(
            if (isFlow) "Начать" else "Начать · $minutes мин",
            style = MaterialTheme.typography.labelLarge
        )
    }

    Spacer(Modifier.height(8.dp))

    // Кнопка не спрятана намеренно: ей должно быть не стыдно воспользоваться
    TextButton(onClick = onCantStart) { Text("Не могу начать") }

    if (hasOtherTasks) {
        TextButton(onClick = onPickTask) {
            Text("Другая задача", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NothingChosen(stage: Int, onPickTask: () -> Unit) {
    Spacer(Modifier.height(32.dp))
    PlantArt(stage = stage, size = 120.dp)
    Spacer(Modifier.height(20.dp))
    Text("С чего начнём?", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    Text(
        "Выбери одну задачу — остальные подождут",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(28.dp))
    Button(
        onClick = onPickTask,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) { Text("Выбрать задачу") }
}

@Composable
private fun Empty(onAddTask: () -> Unit) {
    Spacer(Modifier.height(32.dp))
    // Семя: ещё ничего не выросло, и это нормально
    PlantArt(stage = 0, size = 120.dp)
    Spacer(Modifier.height(20.dp))
    Text("Пока пусто", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    Text(
        "Одна задача — уже начало",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(28.dp))
    Button(
        onClick = onAddTask,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) { Text("Добавить задачу") }
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
