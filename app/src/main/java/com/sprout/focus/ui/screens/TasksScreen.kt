package com.sprout.focus.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sprout.focus.data.Reminder
import com.sprout.focus.data.Task

@Composable
fun TasksScreen(
    tasks: List<Task>,
    onAddTask: () -> Unit,
    onMakeCurrent: (Long) -> Unit,
    onComplete: (Long) -> Unit,
    onDrop: (Long) -> Unit,
    /** Открыть задачу целиком: название, первый шаг, план, напоминание. */
    onOpenTask: (Long) -> Unit,
) {
    if (tasks.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🌱", fontSize = 56.sp)
            Spacer(Modifier.height(16.dp))
            Text("Пока пусто", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Одна задача — уже начало",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAddTask) { Text("Добавить задачу") }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Задачи", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Нажми на задачу, чтобы её изменить",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }

        items(tasks, key = { it.id }) { task ->
            TaskRow(task, onMakeCurrent, onComplete, onDrop, onOpenTask)
        }

        item {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onAddTask,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) { Text("Добавить задачу") }
        }
    }
}

/**
 * Одна задача в списке.
 *
 * Нажатие на карточку открывает задачу, а не выбирает её текущей. Так
 * ожидается: тап по вещи показывает эту вещь. Выбор текущей — отдельная
 * кнопка, потому что это действие, а не переход, и делают его реже, чем
 * кажется при проектировании.
 */
@Composable
private fun TaskRow(
    task: Task,
    onMakeCurrent: (Long) -> Unit,
    onComplete: (Long) -> Unit,
    onDrop: (Long) -> Unit,
    onOpenTask: (Long) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenTask(task.id) },
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCurrent)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            if (task.isCurrent) {
                Text(
                    "СЕЙЧАС",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(task.title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                task.firstStep,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (task.hasPlan) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Если ${task.ifTrigger}, то я ${task.thenAction}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (task.hasReminder && task.remindMinuteOfDay != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Напомню в ${Reminder.formatTime(task.remindMinuteOfDay)} · " +
                            Reminder.formatDays(task.remindDaysMask),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!task.isCurrent) {
                    TextButton(onClick = { onMakeCurrent(task.id) }) { Text("Сейчас") }
                }
                TextButton(onClick = { onComplete(task.id) }) { Text("Готово") }
                // Отказаться от задачи — тоже результат, а не провал
                TextButton(onClick = { onDrop(task.id) }) {
                    Text("Не буду", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
