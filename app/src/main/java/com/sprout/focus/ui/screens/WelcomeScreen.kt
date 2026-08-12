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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sprout.focus.data.Gender
import com.sprout.focus.ui.PlantArt

/**
 * Знакомство при первом запуске.
 *
 * Три экрана, и ни один ничего не требует: пропустить можно с любого.
 * Приложение против прокрастинации не вправе начинать знакомство
 * с формы, которую надо заполнить, — это ровно то чувство, из-за
 * которого откладывают.
 *
 * Последний экран спрашивает имя и обращение. Не «зарегистрируйся»,
 * а «как тебя звать»: аккаунтов здесь нет и не будет, данные остаются
 * на телефоне, и сказать об этом надо прямо здесь, а не в мелком тексте.
 */
@Composable
fun WelcomeScreen(
    onDone: (name: String?, gender: Gender) -> Unit,
    /**
     * Начать сразу с вопроса об имени.
     *
     * Так знакомство встречает того, кто приложением уже пользуется:
     * после обновления ему нужен только новый вопрос, а не рассказ
     * про замысел, который он проверил на себе за месяц.
     */
    nameOnly: Boolean = false,
) {
    var page by rememberSaveable { mutableIntStateOf(if (nameOnly) NAME_PAGE else 0) }
    var name by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf(Gender.UNKNOWN) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))
        PlantArt(stage = if (page == 0) 0 else page, size = 120.dp)
        Spacer(Modifier.height(32.dp))

        when (page) {
            0 -> Page(
                title = "Это Sprout",
                body = "Приложение для тех дней, когда дело простое, а начать невозможно.\n\n" +
                    "Оно не подгоняет и не считает, сколько не сделано, — оно помогает " +
                    "сделать первый шаг и замечает, что тебе мешает."
            )

            1 -> Page(
                title = "Одна задача за раз",
                body = "На главном экране всегда одна задача и один вопрос: начать сейчас " +
                    "или сказать, что не получается.\n\n" +
                    "Если не получается — это тоже ответ. За ним не выговор, а разбор: " +
                    "что именно мешает и что с этим сделать прямо сейчас."
            )

            2 -> Page(
                title = "Всё остаётся на телефоне",
                body = "Ни аккаунтов, ни сервера: у приложения нет даже разрешения выходить " +
                    "в интернет. Задачи и причины избегания не покидают телефон.\n\n" +
                    "Копию своих данных можно сохранить в файл — на «Я», в разделе «Копия данных»."
            )

            else -> {
                Text("Как тебя зовут?", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    // Тому, кто приложением уже пользуется, надо объяснить,
                    // откуда взялся вопрос: экран появился после обновления,
                    // и «как раньше» здесь — обещание, а не оговорка
                    if (nameOnly)
                        "Sprout научился обращаться по имени. Можно пропустить — " +
                            "тогда он будет говорить безлично, как раньше."
                    else
                        "Чтобы приложение обращалось к тебе, а не к «пользователю». " +
                            "Можно пропустить — тогда оно будет говорить безлично.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))

                // Те же поля, что на экране «Как обращаться»: спрашивают
                // об этом дважды, а вопрос должен быть один
                VoiceFields(
                    name = name,
                    gender = gender,
                    onName = { name = it },
                    onGender = { gender = it },
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = {
                if (page < NAME_PAGE) page++
                else onDone(name, gender)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) { Text(if (page < NAME_PAGE) "Дальше" else "Начать") }

        Spacer(Modifier.height(8.dp))
        // Пропустить можно с любого экрана и без единого вопроса: знакомство,
        // которое нельзя прервать, — первое требование приложения к человеку
        TextButton(
            onClick = { onDone(name.takeIf { page == NAME_PAGE }, gender) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (page < NAME_PAGE) "Пропустить" else "Не сейчас",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** Страница с именем — последняя, и единственная, куда можно войти сразу. */
private const val NAME_PAGE = 3

@Composable
private fun Page(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(12.dp))
    Text(
        body,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

