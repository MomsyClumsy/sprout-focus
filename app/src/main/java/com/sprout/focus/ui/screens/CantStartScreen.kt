package com.sprout.focus.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sprout.focus.data.CantStartReason
import com.sprout.focus.data.CantStartResolution
import com.sprout.focus.data.Session
import com.sprout.focus.ui.theme.SproutTheme

/**
 * «Не могу начать» — ядро приложения.
 *
 * Прокрастинация это регуляция эмоций, а не тайм-менеджмент: человек избегает
 * не работы, а неприятного чувства от неё. Поэтому здесь спрашивают, что
 * именно мешает, и каждый ответ ведёт к ДЕЙСТВИЮ — уменьшает задачу или
 * меняет среду, — а не к совету.
 *
 * Ни один ответ не приводит к упрёку. Отказ от задачи тоже засчитывается
 * как результат.
 */
@Composable
fun CantStartScreen(
    postponeCount: Int,
    onPicked: (reason: String) -> Unit,
    onStartSession: (reason: String, resolution: String, mode: String, seconds: Int) -> Unit,
    onSplit: (reason: String, step: String) -> Unit,
    onFoundMeaning: (reason: String, why: String) -> Unit,
    onDrop: (reason: String) -> Unit,
    onPostpone: (reason: String) -> Unit,
    onClose: () -> Unit,
    barrierReady: Boolean = false,
    onOpenGuard: () -> Unit = {},
) {
    // rememberSaveable, а не remember: из ветки «Отвлекаюсь» человек уходит
    // в настройки барьера, и обычное состояние по дороге теряется — «Назад»
    // возвращал бы его к выбору причины, заставляя объясняться заново.
    // Заодно переживает поворот экрана и смену темы.
    var reason by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        if (reason == null) {
            Chooser(postponeCount) {
                reason = it
                onPicked(it)
            }
        } else {
            Response(
                reason = reason!!,
                onBack = { reason = null },
                onStartSession = onStartSession,
                onSplit = onSplit,
                onFoundMeaning = onFoundMeaning,
                onDrop = onDrop,
                onPostpone = onPostpone,
                barrierReady = barrierReady,
                onOpenGuard = onOpenGuard,
            )
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("Закрыть", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }
}

private data class ReasonOption(val key: String, val emoji: String, val label: String)

private val options = listOf(
    ReasonOption(CantStartReason.ANXIETY, "😰", "Боюсь, что не получится"),
    ReasonOption(CantStartReason.BOREDOM, "😑", "Скучно"),
    ReasonOption(CantStartReason.NO_ENERGY, "🥱", "Нет сил"),
    // Лицо, а не спираль: пять из шести пунктов — жёлтые лица,
    // синяя спираль выпадала из ряда и из палитры
    ReasonOption(CantStartReason.TOO_BIG, "😵‍💫", "Слишком большая"),
    ReasonOption(CantStartReason.NO_MEANING, "😤", "Не хочу, бессмысленно"),
    ReasonOption(CantStartReason.DISTRACTED, "📱", "Отвлекаюсь"),
)

@Composable
private fun Chooser(postponeCount: Int, onPick: (String) -> Unit) {
    Text("Что мешает?", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(6.dp))
    Text(
        "Здесь нет неправильных ответов",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (postponeCount >= 2) {
        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Text(
                "Эта задача откладывается уже $postponeCount ${timesWord(postponeCount)}. " +
                        "Обычно это значит, что она просто слишком большая.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(14.dp)
            )
        }
    }

    Spacer(Modifier.height(20.dp))

    options.forEach { option ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .clickable { onPick(option.key) },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(option.emoji, fontSize = 26.sp)
                Spacer(Modifier.fillMaxWidth(0.0f))
                Text(
                    option.label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun Response(
    reason: String,
    onBack: () -> Unit,
    onStartSession: (String, String, String, Int) -> Unit,
    onSplit: (String, String) -> Unit,
    onFoundMeaning: (String, String) -> Unit,
    onDrop: (String) -> Unit,
    onPostpone: (String) -> Unit,
    barrierReady: Boolean,
    onOpenGuard: () -> Unit,
) {
    when (reason) {

        // Снижаем планку: страх провала лечится разрешением сделать плохо
        CantStartReason.ANXIETY -> Simple(
            title = "Сделай самую плохую версию",
            body = "Её никто не увидит. Десять минут на черновик — этого хватит, " +
                    "чтобы сдвинуться с места. Улучшить всегда можно потом.",
            actionLabel = "Черновик · 10 минут",
            onAction = {
                onStartSession(reason, CantStartResolution.STARTED_SMALLER, Session.MODE_POMODORO, 10 * 60)
            },
            onBack = onBack
        )

        // Temptation bundling: приятное доступно только вместе с нужным
        CantStartReason.BOREDOM -> Simple(
            title = "Добавь к скучному приятное",
            body = "Включи музыку или подкаст — но только на время сессии, не после. " +
                    "В исследовании доступ к аудиокнигам исключительно в спортзале " +
                    "поднял посещаемость на 51%.",
            actionLabel = "Начать · 15 минут",
            onAction = {
                onStartSession(reason, CantStartResolution.STARTED_SMALLER, Session.MODE_POMODORO, 15 * 60)
            },
            onBack = onBack
        )

        // Правило пяти минут: обязательство маленькое, продолжать не обязана
        CantStartReason.NO_ENERGY -> Simple(
            title = "Тогда только пять минут",
            body = "Без обязательства продолжать. Обычно этого хватает, чтобы втянуться — " +
                    "а если не хватит, пять минут всё равно больше нуля.",
            actionLabel = "Начать · 5 минут",
            onAction = {
                onStartSession(reason, CantStartResolution.STARTED_SMALLER, Session.MODE_POMODORO, 5 * 60)
            },
            secondaryLabel = "Перенести на потом",
            onSecondary = { onPostpone(reason) },
            onBack = onBack
        )

        CantStartReason.TOO_BIG -> WithInput(
            title = "Назови самое первое действие",
            body = "Не всю задачу, а один шаг. Настолько маленький, чтобы отказаться " +
                    "было неловко: «открыть файл», «найти номер».",
            label = "Первое действие",
            actionLabel = "Это и сделаю",
            onAction = { onSplit(reason, it) },
            onBack = onBack
        )

        CantStartReason.NO_MEANING -> WithInput(
            title = "Зачем это тебе?",
            body = "Если ответа нет — возможно, задачу правда не надо делать. " +
                    "Отказаться это тоже решение, а не провал.",
            label = "Затем, что…",
            actionLabel = "Сохранить и начать",
            onAction = { onFoundMeaning(reason, it) },
            secondaryLabel = "Не буду это делать",
            onSecondary = { onDrop(reason) },
            onBack = onBack
        )

        // Единственная ветка, где ответ уже встроен в приложение. Если барьер
        // настроен — про него говорится как про свершившийся факт, и человек
        // сразу начинает. Если нет — начать всё равно можно первой кнопкой:
        // настройки здесь предлагаются, но не встают на пути. Человек,
        // который пришёл сюда, и так не может начать; отправить его вместо
        // работы заполнять форму значит дать ему ещё один способ отложить.
        CantStartReason.DISTRACTED -> if (barrierReady) {
            Simple(
                title = "Барьер уже включён",
                body = "Как только начнётся сессия, Sprout остановит тебя " +
                        "на выбранных приложениях и напомнит, за чем ты села.\n\n" +
                        "Осталось убрать телефон из поля зрения — чтобы " +
                        "отвлечься, должно потребоваться усилие.",
                actionLabel = "Начать · 20 минут",
                onAction = {
                    onStartSession(reason, CantStartResolution.STARTED_SMALLER, Session.MODE_POMODORO, 20 * 60)
                },
                onBack = onBack
            )
        } else {
            Simple(
                title = "Уберём отвлечения",
                body = "Убери телефон из поля зрения — чтобы отвлечься, должно " +
                        "потребоваться усилие.\n\n" +
                        "Sprout умеет останавливать тебя на приложениях, которые " +
                        "уводят чаще всего. Пока барьер не настроен.",
                actionLabel = "Начать · 20 минут",
                onAction = {
                    onStartSession(reason, CantStartResolution.STARTED_SMALLER, Session.MODE_POMODORO, 20 * 60)
                },
                secondaryLabel = "Настроить барьер",
                onSecondary = onOpenGuard,
                onBack = onBack
            )
        }
    }
}

@Composable
private fun Simple(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    onBack: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(12.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(32.dp))
    Button(
        onClick = onAction,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) { Text(actionLabel) }
    if (secondaryLabel != null && onSecondary != null) {
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) { Text(secondaryLabel) }
    }
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Другая причина") }
}

@Composable
private fun WithInput(
    title: String,
    body: String,
    label: String,
    actionLabel: String,
    onAction: (String) -> Unit,
    onBack: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    var text by remember { mutableStateOf("") }

    Text(title, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(12.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = { onAction(text) },
        enabled = text.isNotBlank(),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) { Text(actionLabel) }
    if (secondaryLabel != null && onSecondary != null) {
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) { Text(secondaryLabel) }
    }
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Другая причина") }
}

private fun timesWord(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m100 in 11..14 -> "раз"
        m10 == 1 -> "раз"
        m10 in 2..4 -> "раза"
        else -> "раз"
    }
}

@Preview(name = "Не могу начать · выбор", showBackground = true)
@Composable
private fun CantStartChooser() = SproutTheme(darkTheme = false) {
    CantStartScreen(0, {}, { _, _, _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, {})
}

@Preview(name = "Не могу начать · откладывалась", showBackground = true)
@Composable
private fun CantStartRepeated() = SproutTheme(darkTheme = true) {
    CantStartScreen(3, {}, { _, _, _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, {})
}
