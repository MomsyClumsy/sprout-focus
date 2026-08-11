package com.sprout.focus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sprout.focus.data.Reminder
import com.sprout.focus.data.Task
import kotlinx.coroutines.delay

/**
 * Зацепка для первого шага и напоминание к ней.
 *
 * Один блок на два экрана: создание задачи и правка плана у существующей.
 * Иначе поля разъедутся — а это то самое место, где приложение должно
 * выглядеть одинаково, потому что человек сюда возвращается.
 *
 * **Поле одно.** Раньше их было два — «Если…» и «…то я», — и второе просило
 * написать то же самое, что человек уже назвал первым шагом строкой выше.
 * Вопрос «когда сделаешь первый шаг?» при этом стоял над полями про действие,
 * так что экран спрашивал одно, а просил другое.
 *
 * Порядок «сделаю — если» взят из трекеров привычек и по-русски читается
 * легче классического «если — то»: обещание идёт первым, а зацепка
 * договаривает фразу. Сама зацепка остаётся свободным текстом: «попью чай» —
 * такой же законный план, как «в десять утра», и по исследованиям привязка
 * к событию работает даже надёжнее, чем к часам. Время лежит отдельным
 * полем и нужно только затем, чтобы телефону было за что зацепиться.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanFields(
    ifTrigger: String,
    onIfTrigger: (String) -> Unit,
    /** Первый шаг задачи: он же вторая половина плана, показывается в итоге. */
    firstStep: String,
    minuteOfDay: Int?,
    onMinuteOfDay: (Int?) -> Unit,
    daysMask: Int,
    onDaysMask: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerOpen by remember { mutableStateOf(false) }

    Column(modifier) {
        OutlinedTextField(
            value = ifTrigger,
            onValueChange = onIfTrigger,
            label = { Text("Сделаю первый шаг, если…") },
            placeholder = { Text("попью чай") },
            // Примеры, а не одно объяснение: зацепка — вещь, которую проще
            // узнать в чужой, чем придумать с нуля. Все четыре про обычный
            // день и ни одна не про «правильный распорядок»
            supportingText = {
                Text("Например: попью чай · сяду за стол · закончится созвон · отведу ребёнка")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        if (minuteOfDay == null) {
            TextButton(onClick = { pickerOpen = true }) {
                Text("Напомнить в это время")
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = { pickerOpen = true },
                    label = { Text(Reminder.formatTime(minuteOfDay)) }
                )
                TextButton(onClick = { onMinuteOfDay(null); onDaysMask(Reminder.ONE_OFF) }) {
                    Text("Убрать", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Reminder.WEEK.forEach { day ->
                    FilterChip(
                        selected = Reminder.isSet(daysMask, day),
                        onClick = { onDaysMask(daysMask xor Reminder.bit(day)) },
                        label = { Text(Reminder.shortName(day)) }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                if (daysMask == Reminder.ONE_OFF)
                    "Ни один день не выбран — напомню один раз, в ближайшее такое время"
                else
                    "Напомню ${Reminder.formatDays(daysMask)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Фраза целиком — пока человек её ещё может поправить.
        // Вторую половину он не писал, она подставлена, и увидеть,
        // как это склеилось, важнее любого объяснения под полем
        Task.planLine(ifTrigger, firstStep)?.let { plan ->
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (minuteOfDay == null) "Будет на виду"
                        else "Скажу в ${Reminder.formatTime(minuteOfDay)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("«$plan»", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    if (pickerOpen) {
        // Десять утра по умолчанию: разумнее полуночи, которую предложил бы
        // пустой пикер, и не требует крутить колесо через полсуток.
        val start = minuteOfDay ?: (10 * 60)
        val state = rememberTimePickerState(
            initialHour = start / 60,
            initialMinute = start % 60,
            is24Hour = true,
        )

        // Цифрами — по умолчанию. Человек знает, что ему нужно «15:17»,
        // и на циферблате ему приходится это время искать: сначала попасть
        // в час, потом в минуту с шагом в градус. Циферблат остаётся рядом,
        // кнопкой, — он удобен, когда время выбирают, а не вспоминают.
        var byKeyboard by rememberSaveable { mutableStateOf(true) }
        val hourFocus = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current

        Dialog(onDismissRequest = { pickerOpen = false }) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Во сколько напомнить?", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))

                    if (byKeyboard) {
                        // Клавиатуру поднимаем сами. Поле часов иначе ждёт
                        // тапа: диалог открылся, цифры видны, а набирать
                        // их не получается — и человек решает, что ввод
                        // сломан, хотя не хватало одного касания
                        Box(Modifier.focusRequester(hourFocus)) { TimeInput(state = state) }
                        LaunchedEffect(Unit) {
                            // Пауза на укладку диалога: до неё запрос фокуса
                            // уходит в никуда
                            delay(150)
                            runCatching { hourFocus.requestFocus() }
                            keyboard?.show()
                        }
                    } else {
                        TimePicker(state = state)
                    }

                    // Переключатель отдельной строкой: втроём в одном ряду
                    // кнопки не помещаются, и «Готово» переносится по слогам
                    Row(Modifier.fillMaxWidth()) {
                        TextButton(onClick = { byKeyboard = !byKeyboard }) {
                            Text(
                                if (byKeyboard) "Выбрать на циферблате" else "Ввести цифрами",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { pickerOpen = false }) { Text("Отмена") }
                        TextButton(onClick = {
                            onMinuteOfDay(state.hour * 60 + state.minute)
                            pickerOpen = false
                        }) { Text("Готово") }
                    }
                }
            }
        }
    }
}
