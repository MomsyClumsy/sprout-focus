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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sprout.focus.data.Gender
import com.sprout.focus.data.Phrases
import com.sprout.focus.data.Voice
import com.sprout.focus.ui.theme.SproutTheme

/**
 * Как обращаться — то же, что спрашивали при знакомстве.
 *
 * Отдельным экраном, потому что передумать здесь законно: имя написали
 * с ошибкой, обращение выбрали наспех или вообще пропустили знакомство,
 * а теперь захотелось. Ответ, который нельзя изменить, — это не ответ,
 * а анкета.
 */
@Composable
fun ProfileScreen(
    voice: Voice,
    onSave: (name: String?, gender: Gender) -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(voice.name.orEmpty()) }
    var gender by rememberSaveable { mutableStateOf(voice.gender) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Как обращаться", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Имя звучит только внутри приложения. В уведомлениях его нет: " +
                "они видны на экране блокировки, и туда личное не идёт.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))

        VoiceFields(
            name = name,
            gender = gender,
            onName = { name = it },
            onGender = { gender = it },
        )

        // Как это зазвучит — прямо здесь, на живой фразе из приложения.
        // Выбор между «сама», «сам» и «безлично» иначе приходится
        // проверять, разглядывая экраны: сам по себе он ничего не обещает
        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Так это будет звучать",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                val preview = Voice(name = name.trim().takeIf { it.isNotEmpty() }, gender = gender)
                Text(preview.ask("Что мешает?"), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    Phrases.startIsOnYou(preview),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                onSave(name.trim().takeIf { it.isNotEmpty() }, gender)
                onBack()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) { Text("Сохранить") }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Отмена", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Как обращаться", showBackground = true)
@Composable
private fun ProfileNamed() = SproutTheme(darkTheme = false) {
    ProfileScreen(Voice(name = "Марина", gender = Gender.FEMININE), { _, _ -> }, {})
}

@androidx.compose.ui.tooling.preview.Preview(name = "Как обращаться · пусто", showBackground = true)
@Composable
private fun ProfileEmpty() = SproutTheme(darkTheme = false) {
    ProfileScreen(Voice(), { _, _ -> }, {})
}
