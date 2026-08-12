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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sprout.focus.data.Phrases
import com.sprout.focus.data.Reminder
import com.sprout.focus.data.Task
import com.sprout.focus.ui.LocalVoice
import kotlinx.coroutines.delay

/**
 * Зацепка для первого шага и напоминание к ней.
 *
 * Один блок на два экрана: создание задачи и правка плана у существующей.
 * Иначе поля разъедутся — а это то самое место, где приложение должно
 * выглядеть одинаково, потому что человек сюда возвращается.
 *
 * **Две дорожки, а не одна кнопка.** «После чая» и «в 15:00» — разные
 * способы поймать момент, и приложение умеет их по-разному: время оно
 * знает и напомнит само, а про чай знать не может и не должно. Раньше
 * это делала одна ссылка «Напомнить в это время», и было неясно, что
 * будет, если её не трогать. Теперь выбор назван вслух, а под каждой
 * дорожкой написано, что именно произойдёт.
 *
 * **Поле по-прежнему одно.** Вторая половина плана — первый шаг задачи,
 * его человек уже написал строкой выше; спрашивать то же самое второй раз
 * значило бы придираться.
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

    // Режим держится отдельно от времени: между нажатием «В конкретное время»
    // и выбором часа время ещё пустое, а дорожка уже выбрана
    var byTime by rememberSaveable { mutableStateOf(minuteOfDay != null) }

    Column(modifier) {
        OutlinedTextField(
            value = ifTrigger,
            onValueChange = onIfTrigger,
            label = { Text("Сделаю первый шаг, если…") },
            // Пример — внутри поля, где его видно до ввода, а не под ним:
            // подпись снизу человек читает уже после того, как написал,
            // и на формулировку она не влияет
            placeholder = { Text("попью чай") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !byTime,
                onClick = {
                    byTime = false
                    onMinuteOfDay(null)
                    onDaysMask(Reminder.ONE_OFF)
                },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("После события") }

            SegmentedButton(
                selected = byTime,
                onClick = {
                    byTime = true
                    if (minuteOfDay == null) pickerOpen = true
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("В нужное время") }
        }

        Spacer(Modifier.height(8.dp))

        if (byTime) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = { pickerOpen = true },
                    label = { Text(minuteOfDay?.let(Reminder::formatTime) ?: "Выбрать время") }
                )
            }

            if (minuteOfDay != null) {
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
        } else {
            // Честно про то, чего приложение не умеет. Про «попью чай» оно
            // узнать не может: такого доступа у него нет, а был бы — это
            // слежка. Значит и обещать нечего, кроме как быть на виду
            Text(
                "Напоминания не будет: приложение не знает, когда это случится. " +
                    Phrases.startIsOnYou(LocalVoice.current),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Фраза целиком — пока человек её ещё может поправить. Вторую
        // половину он не писал, она подставлена из первого шага, и увидеть,
        // как это склеилось, важнее любого объяснения под полем
        PlanPreview(ifTrigger, firstStep, minuteOfDay.takeIf { byTime })
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

        val close = {
            pickerOpen = false
            // Ушёл, не выбрав время, — значит дорожка не выбрана тоже:
            // иначе экран остался бы с «В нужное время» и пустым временем
            if (minuteOfDay == null) byTime = false
        }

        Dialog(onDismissRequest = close) {
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
                        TextButton(onClick = close) { Text("Отмена") }
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

/**
 * План одной фразой, как он потом прочитается.
 *
 * Слова человека — курсивом: так видно, что приложение только связало
 * их «если — то», а не написало за него. Собирается из [Task.PlanParts],
 * чтобы не разъехаться с тем, что покажут карточка и уведомление.
 */
@Composable
private fun PlanPreview(ifTrigger: String, firstStep: String, minuteOfDay: Int?) {
    val parts = Task.planParts(ifTrigger, firstStep)
    if (parts == null && (minuteOfDay == null || firstStep.isBlank())) return

    val mine = SpanStyle(fontStyle = FontStyle.Italic)
    val text = buildAnnotatedString {
        if (parts != null) {
            append("Если ")
            withStyle(mine) { append(parts.trigger) }
            append(", то ")
            withStyle(mine) { append(parts.promise) }
        } else {
            append("В ${Reminder.formatTime(minuteOfDay!!)} напомню: ")
            withStyle(mine) { append(firstStep.trim()) }
        }
    }

    Spacer(Modifier.height(16.dp))
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (minuteOfDay != null && parts != null) {
                Text(
                    "Напомню в ${Reminder.formatTime(minuteOfDay)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
