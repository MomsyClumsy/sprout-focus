package com.sprout.focus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sprout.focus.data.Gender

/**
 * Имя и обращение — одними и теми же полями в двух местах.
 *
 * Спрашивают об этом дважды: при знакомстве и потом на «Я», когда человек
 * передумал. Два экрана, собранные по отдельности, разъезжаются при первой
 * же правке — а тут расходиться будет не отступ, а сам вопрос (грабли №32).
 */
@Composable
fun VoiceFields(
    name: String,
    gender: Gender,
    onName: (String) -> Unit,
    onGender: (Gender) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            label = { Text("Имя") },
            placeholder = { Text("Марина") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))
        Text("Как о тебе говорить?", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // «Безлично» — такой же вариант, как два других, а не отказ:
            // приложение и без ответа умеет говорить целыми фразами
            GenderChip("ты сама", Gender.FEMININE, gender, onGender)
            GenderChip("ты сам", Gender.MASCULINE, gender, onGender)
            GenderChip("безлично", Gender.UNKNOWN, gender, onGender)
        }
    }
}

@Composable
private fun GenderChip(
    label: String,
    value: Gender,
    selected: Gender,
    onPick: (Gender) -> Unit,
) {
    FilterChip(
        selected = selected == value,
        onClick = { onPick(value) },
        label = { Text(label) },
    )
}
