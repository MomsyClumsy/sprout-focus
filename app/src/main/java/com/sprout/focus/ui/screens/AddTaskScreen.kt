package com.sprout.focus.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sprout.focus.data.Reminder
import com.sprout.focus.data.TaskDraft

/**
 * Создание задачи.
 *
 * Обязательны только название и первый шаг. План «если — то» лежит на виду,
 * но не обязателен: два принудительных поля — это трение ровно там, где
 * человек и так избегает. Зато позже аналитика честно сравнит,
 * насколько чаще начинаются задачи с планом.
 */
@Composable
fun AddTaskScreen(
    onSave: (TaskDraft) -> Unit,
    onCancel: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var firstStep by remember { mutableStateOf("") }
    var ifTrigger by remember { mutableStateOf("") }
    var thenAction by remember { mutableStateOf("") }
    var coping by remember { mutableStateOf("") }
    var why by remember { mutableStateOf("") }
    var minuteOfDay by remember { mutableStateOf<Int?>(null) }
    var daysMask by remember { mutableIntStateOf(Reminder.ONE_OFF) }
    var extrasOpen by remember { mutableStateOf(false) }

    val canSave = title.isNotBlank() && firstStep.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Новая задача", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Что нужно сделать?") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false
        )

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = firstStep,
            onValueChange = { firstStep = it },
            label = { Text("Первый шаг") },
            supportingText = {
                Text("Настолько маленький, чтобы отказаться было неловко. Например: «открыть файл и написать заголовок»")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        Text("Когда сделаешь первый шаг?", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Необязательно, но задачи с таким планом начинают заметно чаще",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

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

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = { extrasOpen = !extrasOpen }) {
            Text(if (extrasOpen) "Свернуть" else "Ещё два вопроса")
        }

        if (extrasOpen) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = coping,
                onValueChange = { coping = it },
                label = { Text("Если захочется отвлечься, то я…") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = why,
                onValueChange = { why = it },
                label = { Text("Зачем это мне") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                onSave(
                    TaskDraft(
                        title = title,
                        firstStep = firstStep,
                        ifTrigger = ifTrigger.ifBlank { null },
                        thenAction = thenAction.ifBlank { null },
                        copingPlan = coping.ifBlank { null },
                        whyItMatters = why.ifBlank { null },
                        remindMinuteOfDay = minuteOfDay,
                        remindDaysMask = daysMask,
                    )
                )
            },
            enabled = canSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Сохранить")
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Отмена")
        }
        Spacer(Modifier.height(24.dp))
    }
}
