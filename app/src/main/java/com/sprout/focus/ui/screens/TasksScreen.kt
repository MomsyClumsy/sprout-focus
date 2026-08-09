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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.sp
import com.sprout.focus.data.Reminder
import com.sprout.focus.data.Task

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    tasks: List<Task>,
    onAddTask: () -> Unit,
    onMakeCurrent: (Long) -> Unit,
    onComplete: (Long) -> Unit,
    onDrop: (Long) -> Unit,
    onSavePlan: (taskId: Long, ifTrigger: String?, thenAction: String?, minuteOfDay: Int?, daysMask: Int) -> Unit,
) {
    // Какую задачу правим. Держим id, а не саму задачу: список приходит
    // из базы заново после каждого сохранения, и объект бы устарел.
    var editingId by remember { mutableStateOf<Long?>(null) }
    val editing = tasks.firstOrNull { it.id == editingId }

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
                "Нажми на задачу, чтобы поставить её на «Сегодня»",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }

        items(tasks, key = { it.id }) { task ->
            TaskRow(task, onMakeCurrent, onComplete, onDrop, onEditPlan = { editingId = task.id })
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

    if (editing != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { editingId = null },
            sheetState = sheetState
        ) {
            PlanSheet(
                task = editing,
                onSave = { ifTrigger, thenAction, minuteOfDay, daysMask ->
                    onSavePlan(editing.id, ifTrigger, thenAction, minuteOfDay, daysMask)
                    editingId = null
                },
                onCancel = { editingId = null }
            )
        }
    }
}

/**
 * Правка плана и напоминания у существующей задачи.
 *
 * Полного редактора задачи нет намеренно: менять формулировку задачи
 * и её первый шаг — отдельный разговор, а вот вернуться и назначить время
 * нужно постоянно. Без этого пять уже заведённых задач навсегда остались бы
 * без напоминаний.
 */
@Composable
private fun PlanSheet(
    task: Task,
    onSave: (ifTrigger: String?, thenAction: String?, minuteOfDay: Int?, daysMask: Int) -> Unit,
    onCancel: () -> Unit,
) {
    var ifTrigger by remember(task.id) { mutableStateOf(task.ifTrigger.orEmpty()) }
    var thenAction by remember(task.id) { mutableStateOf(task.thenAction.orEmpty()) }
    var minuteOfDay by remember(task.id) { mutableStateOf(task.remindMinuteOfDay) }
    var daysMask by remember(task.id) { mutableIntStateOf(task.remindDaysMask) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
    ) {
        Text(task.title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Когда сделаешь первый шаг?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        PlanFields(
            ifTrigger = ifTrigger,
            onIfTrigger = { ifTrigger = it },
            thenAction = thenAction,
            onThenAction = { thenAction = it },
            minuteOfDay = minuteOfDay,
            onMinuteOfDay = { minuteOfDay = it },
            daysMask = daysMask,
            onDaysMask = { daysMask = it },
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                onSave(
                    ifTrigger.ifBlank { null },
                    thenAction.ifBlank { null },
                    minuteOfDay,
                    daysMask
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) { Text("Сохранить") }

        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
    }
}

@Composable
private fun TaskRow(
    task: Task,
    onMakeCurrent: (Long) -> Unit,
    onComplete: (Long) -> Unit,
    onDrop: (Long) -> Unit,
    onEditPlan: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onMakeCurrent(task.id) },
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
                TextButton(onClick = { onComplete(task.id) }) { Text("Готово") }
                TextButton(onClick = onEditPlan) {
                    Text(if (task.hasReminder) "Напоминание" else "Напомнить")
                }
                // Отказаться от задачи — тоже результат, а не провал
                TextButton(onClick = { onDrop(task.id) }) {
                    Text("Не буду", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
