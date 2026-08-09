package com.sprout.focus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sprout.focus.data.Session

/** Экран идущей сессии. Ничего лишнего — только время и на чём сосредоточиться. */
@Composable
fun SessionScreen(
    session: Session,
    now: Long,
    taskTitle: String,
    firstStep: String,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onTimeUp: () -> Unit,
) {
    val remaining = session.remainingSeconds(now)
    val elapsed = session.elapsedSeconds(now)

    // Плановое время вышло — уводим на экран итога
    LaunchedEffect(remaining) {
        if (remaining != null && remaining <= 0) onTimeUp()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (taskTitle.isNotBlank()) {
            Text(
                taskTitle,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(40.dp))
        }

        Text(
            text = formatTime(remaining ?: elapsed),
            fontSize = 64.sp,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                session.isPaused -> "На паузе"
                remaining == null -> "Идёт"
                else -> "🌿 растёт"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (firstStep.isNotBlank()) {
            Spacer(Modifier.height(40.dp))
            Text(
                "Первый шаг",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(firstStep, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(56.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = if (session.isPaused) onResume else onPause,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Text(if (session.isPaused) "Продолжить" else "Пауза")
            }
            Button(
                onClick = onFinish,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Text("Завершить")
            }
        }
    }
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
