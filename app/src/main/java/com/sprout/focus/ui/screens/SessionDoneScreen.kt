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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Итог сессии.
 *
 * Все поля необязательны — это топливо для аналитики, а не экзамен.
 * «На чём остановилась» работает на эффект Зейгарник: записанная
 * середина мысли удешевляет возвращение к задаче завтра.
 */
@Composable
fun SessionDoneScreen(
    minutes: Int,
    completed: Boolean,
    onSave: (rating: Int?, interruptions: Int?, note: String?) -> Unit,
) {
    var rating by remember { mutableStateOf<Int?>(null) }
    var interruptions by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text("🌿", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            if (completed) "Сессия закончилась" else "Остановились",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (minutes < 1) "Меньше минуты — тоже считается" else "$minutes ${minuteWord(minutes)} фокуса",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(36.dp))

        Text("Как пошло?", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "😕", 2 to "😐", 3 to "🙂").forEach { (value, emoji) ->
                FilterChip(
                    selected = rating == value,
                    onClick = { rating = if (rating == value) null else value },
                    label = { Text(emoji, fontSize = 22.sp) }
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Text("Отвлекалась?", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "Нет", 1 to "Пару раз", 2 to "Постоянно").forEach { (value, label) ->
                FilterChip(
                    selected = interruptions == value,
                    onClick = { interruptions = if (interruptions == value) null else value },
                    label = { Text(label) }
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("На чём остановилась?") },
            supportingText = { Text("Запиши середину мысли — завтра будет легче вернуться") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { onSave(rating, interruptions, note.ifBlank { null }) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) { Text("Готово") }

        Spacer(Modifier.height(24.dp))
    }
}

private fun minuteWord(n: Int): String {
    val mod10 = n % 10
    val mod100 = n % 100
    return when {
        mod100 in 11..14 -> "минут"
        mod10 == 1 -> "минута"
        mod10 in 2..4 -> "минуты"
        else -> "минут"
    }
}
