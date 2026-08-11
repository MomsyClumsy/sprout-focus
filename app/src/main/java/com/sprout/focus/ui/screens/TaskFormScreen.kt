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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sprout.focus.data.PlanRule
import com.sprout.focus.data.Reminder
import com.sprout.focus.data.Task
import com.sprout.focus.data.TaskDraft

/**
 * Создание и правка задачи — одна и та же форма.
 *
 * Обязательны только название и первый шаг. План «если — то» лежит на виду,
 * но не обязателен: два принудительных поля — это трение ровно там, где
 * человек и так избегает. Зато позже аналитика честно сравнит,
 * насколько чаще начинаются задачи с планом.
 *
 * Править можно всё, включая название. Человек передумывает и делает
 * опечатки, а задача — это то, что он видит каждый день: невозможность
 * исправить свою же ошибку превращает её в ежедневный упрёк.
 *
 * [existing] = null — заводим новую.
 */
@Composable
fun TaskFormScreen(
    onSave: (TaskDraft) -> Unit,
    onCancel: () -> Unit,
    existing: Task? = null,
    /**
     * Обязателен ли план «если — то» и почему.
     *
     * Единственный случай, когда приложение просит заполнить лишнее поле:
     * либо идёт эксперимент, который это и проверяет, либо человек сам
     * оставил такой порядок после подтвердившейся недели. Причину экран
     * называет вслух — обязательное поле без объяснения читается как
     * придирка приложения.
     */
    planRule: PlanRule = PlanRule.NONE,
) {
    val planRequired = planRule != PlanRule.NONE

    // rememberSaveable, а не remember: набранный текст должен пережить
    // и поворот экрана, и уход в настройки за разрешением. Потерянный
    // черновик задачи — это ровно то отвлечение, ради которого человек
    // сюда и не вернётся (грабли №20)
    var title by rememberSaveable(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var firstStep by rememberSaveable(existing?.id) { mutableStateOf(existing?.firstStep.orEmpty()) }
    var ifTrigger by rememberSaveable(existing?.id) { mutableStateOf(existing?.ifTrigger.orEmpty()) }
    var minuteOfDay by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.remindMinuteOfDay)
    }
    var daysMask by rememberSaveable(existing?.id) {
        mutableIntStateOf(existing?.remindDaysMask ?: Reminder.ONE_OFF)
    }
    val canSave = title.isNotBlank() && firstStep.isNotBlank() &&
        (!planRequired || ifTrigger.isNotBlank())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            if (existing == null) "Новая задача" else "Задача",
            style = MaterialTheme.typography.headlineMedium,
        )
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
            when (planRule) {
                PlanRule.EXPERIMENT ->
                    "На эту неделю — обязательное поле: идёт эксперимент, который " +
                        "как раз это и проверяет"
                PlanRule.KEPT ->
                    "Обязательное поле: ты оставила так после эксперимента. " +
                        "Выключить можно на экране «Я»"
                PlanRule.NONE ->
                    "Необязательно, но задачи с таким планом начинают заметно чаще"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        PlanFields(
            ifTrigger = ifTrigger,
            onIfTrigger = { ifTrigger = it },
            firstStep = firstStep,
            minuteOfDay = minuteOfDay,
            onMinuteOfDay = { minuteOfDay = it },
            daysMask = daysMask,
            onDaysMask = { daysMask = it },
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                onSave(
                    TaskDraft(
                        title = title,
                        firstStep = firstStep,
                        ifTrigger = ifTrigger.ifBlank { null },
                        // Вторая половина плана больше не хранится: ею служит
                        // первый шаг. У старой задачи поле затрётся — и это
                        // правильно, иначе в уведомлении осталась бы фраза,
                        // которую человек уже нигде не видит и не может поправить
                        thenAction = null,
                        // copingPlan и whyItMatters форма больше не спрашивает.
                        // null тут значит «не трогать»: ответ на «зачем это мне»
                        // человек даёт в ветке «Не хочу», и правка названия
                        // задачи не должна его стирать
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
