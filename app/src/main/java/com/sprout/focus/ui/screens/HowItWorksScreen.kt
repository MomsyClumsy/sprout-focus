package com.sprout.focus.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sprout.focus.ui.theme.SproutTheme

/**
 * Как устроено — короткая инструкция по интерфейсу.
 *
 * Знакомство при первом запуске говорит про замысел, а этот экран — про
 * то, куда жать. Разница нарочная: замысел читают один раз и на свежую
 * голову, а «где это лежит» спрашивают потом и по конкретному поводу.
 *
 * Не подсказками поверх экранов: они появляются, когда человек занят чем-то
 * своим, и закрываются раньше, чем прочитаны. Здесь текст лежит и ждёт,
 * пока за ним придут, — и всегда на одном месте.
 *
 * Разделы названы вкладками приложения, а не темами: человек приходит
 * сюда с экраном перед глазами и ищет ту же надпись, которую видит внизу.
 */
@Composable
fun HowItWorksScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Как устроено", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Четыре вкладки внизу и один разговор на случай, когда не идёт.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Section(
            "Сегодня",
            "Одна задача — та, за которую садишься сейчас. Под ней первый шаг " +
                "и выбор длины захода: 15, 20, 25, 45 минут или «Поток» без " +
                "таймера. Кнопка «Не могу начать» — рядом со «Старт», и это " +
                "равноправный ответ, а не отказ.\n\n" +
                "Тап по карточке открывает задачу целиком — там же и правится."
        )

        Section(
            "Когда не идёт",
            "«Не могу начать» спрашивает, что именно мешает: страшно, скучно, " +
                "нет сил, слишком большая, не вижу смысла, отвлекаюсь. Каждый " +
                "ответ ведёт к действию — разбить задачу, начать на десять минут, " +
                "вспомнить, зачем она, включить барьер.\n\n" +
                "Причина записывается сразу, даже если разговор оборвётся. Из этих " +
                "записей потом получаются наблюдения на «Я»."
        )

        Section(
            "Задачи",
            "Плоский список без папок и категорий. У каждой задачи обязателен " +
                "первый шаг — настолько маленький, чтобы отказаться было неловко.\n\n" +
                "Первый шаг можно привязать к моменту: «после события» (попью чай, " +
                "закончится созвон) или «в нужное время» — тогда придёт напоминание. " +
                "Приложение не знает, когда закончится созвон, поэтому в первом " +
                "случае план просто на виду, а напоминания не будет."
        )

        Section(
            "Сад",
            "Растение растёт от минут фокуса и проходит пять стадий примерно " +
                "за неделю. Серия считает дни подряд, но пропуск ничего не " +
                "обнуляет: сначала тратится заморозка, они восстанавливаются " +
                "каждый месяц.\n\n" +
                "Выросшие растения остаются в саду."
        )

        Section(
            "Я",
            "Наблюдения о себе: что чаще всего останавливает, какая длина " +
                "захода доводится до конца. Пока данных мало, здесь честно " +
                "написано «пока рано» — выводы из трёх случаев хуже, чем " +
                "их отсутствие. «Все цифры» показывают, из чего наблюдение " +
                "посчитано.\n\n" +
                "Отсюда же: барьер отвлечений и тишина на время захода, копия " +
                "данных в файл, эксперименты над собой и этот экран."
        )

        Section(
            "Эксперименты",
            "Когда истории накопится достаточно, приложение предложит гипотезу " +
                "про тебя — например, что короткие заходы доводятся чаще. " +
                "На неделю оно само меняет своё поведение, а в конце показывает " +
                "итог и спрашивает, закреплять ли изменение. Прервать можно " +
                "в любой день."
        )

        Section(
            "Чего приложение не делает",
            "Не выходит в интернет — у него нет такого разрешения. Ничего " +
                "никуда не отправляет, аккаунтов нет, данные лежат только " +
                "на телефоне. Поэтому копия данных в файл — единственный способ " +
                "их не потерять, и она на «Я».\n\n" +
                "И не считает, сколько не сделано."
        )

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Назад", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String, body: String) {
    Spacer(Modifier.height(20.dp))
    HorizontalDivider()
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview(name = "Как устроено", showBackground = true)
@Composable
private fun HowItWorksLight() = SproutTheme(darkTheme = false) { HowItWorksScreen {} }

@Preview(name = "Как устроено · тёмная", showBackground = true)
@Composable
private fun HowItWorksDark() = SproutTheme(darkTheme = true) { HowItWorksScreen {} }
