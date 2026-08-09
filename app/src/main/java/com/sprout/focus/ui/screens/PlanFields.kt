package com.sprout.focus.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sprout.focus.data.Reminder

/**
 * План «если — то» и напоминание к нему.
 *
 * Один блок на два экрана: создание задачи и правка плана у существующей.
 * Иначе поля разъедутся — а это то самое место, где приложение должно
 * выглядеть одинаково, потому что человек сюда возвращается.
 *
 * Триггер остаётся свободным текстом: «после того как налью кофе» — такой же
 * законный план, как «в десять утра», и по исследованиям привязка к событию
 * работает даже надёжнее, чем к часам. Время лежит отдельным полем и нужно
 * только затем, чтобы телефону было за что зацепиться.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanFields(
    ifTrigger: String,
    onIfTrigger: (String) -> Unit,
    thenAction: String,
    onThenAction: (String) -> Unit,
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
            label = { Text("Если…") },
            placeholder = { Text("налью кофе утром") },
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

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = thenAction,
            onValueChange = onThenAction,
            label = { Text("…то я") },
            placeholder = { Text("открою файл отчёта") },
            modifier = Modifier.fillMaxWidth()
        )
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
                    TimePicker(state = state)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
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
