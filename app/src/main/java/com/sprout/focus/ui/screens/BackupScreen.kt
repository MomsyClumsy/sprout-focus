package com.sprout.focus.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sprout.focus.data.BackupData

/**
 * Копия данных: сохранить файл и восстановиться из него.
 *
 * Отдельный экран, а не строка в настройках: здесь живёт единственное
 * необратимое действие во всём приложении, и ему нужно место, чтобы
 * объясниться. Восстановление стирает то, что накоплено, — человек должен
 * увидеть, что именно он получит взамен, до того как согласится.
 */
@Composable
fun BackupScreen(
    state: BackupUiState,
    onExport: (Uri) -> Unit,
    onPick: (Uri) -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    suggestedName: String,
) {
    val saveFile = rememberLauncherForActivityResult(
        // Тип application/zip, а не */*: так системный диалог сам подставит
        // расширение и не спросит лишнего
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let(onExport) }

    val openFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onPick) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Копия данных", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Задачи, заходы, сад и вся история — в одном файле. Куда его положить, " +
                "решаешь ты: в память телефона, в облако, куда угодно. Само приложение " +
                "никуда ничего не отправляет — файл передаёт система.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { saveFile.launch(suggestedName) },
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) { Text("Сохранить копию") }

        Spacer(Modifier.height(8.dp))
        Text(
            "Внутри архива три таблицы CSV — их можно открыть в Excel и посчитать своё.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))

        OutlinedButton(
            onClick = { openFile.launch(arrayOf("application/zip", "application/json", "*/*")) },
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) { Text("Восстановить из файла") }

        Spacer(Modifier.height(8.dp))
        Text(
            "Заменит всё, что сейчас в приложении. Перед заменой покажу, что в файле.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.message?.let { message ->
            Spacer(Modifier.height(24.dp))
            Card(Modifier.fillMaxWidth()) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    // Единственное необратимое действие в приложении — единственный
    // диалог, который спрашивает дважды
    state.pending?.let { data ->
        AlertDialog(
            onDismissRequest = onCancelRestore,
            title = { Text("Заменить данные?") },
            text = {
                Column {
                    Text("В файле: ${data.summary}.")
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Всё, что сейчас в приложении, будет стёрто — задачи, история, " +
                            "серия и рост растения. Вернуть это будет неоткуда."
                    )
                }
            },
            confirmButton = { TextButton(onClick = onConfirmRestore) { Text("Заменить") } },
            dismissButton = { TextButton(onClick = onCancelRestore) { Text("Отмена") } },
        )
    }
}

/**
 * @param pending что лежит в выбранном файле — показывается до замены
 * @param message итог последнего действия: одной фразой, без подробностей,
 *   которые человеку всё равно нечем использовать
 */
data class BackupUiState(
    val busy: Boolean = false,
    val pending: BackupData? = null,
    val message: String? = null,
)
