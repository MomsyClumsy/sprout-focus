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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sprout.focus.data.InstalledApp
import com.sprout.focus.ui.theme.SproutTheme

/** Всё, что экрану нужно знать о состоянии барьера. */
data class GuardUiState(
    val enabled: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val canDrawOverlay: Boolean = false,
    val apps: List<InstalledApp> = emptyList(),
    val blocked: Set<String> = emptySet(),
)

/**
 * Настройки барьера отвлечений.
 *
 * Экран построен как разговор, а не как форма: сначала честно сказано,
 * что приложение будет делать и чего оно **не** делает, потом разрешения,
 * и только потом список. Человек, который включает слежение за собой,
 * имеет право сначала понять, за что расплачивается.
 */
@Composable
fun GuardScreen(
    state: GuardUiState,
    onToggle: (Boolean) -> Unit,
    onGrantUsage: () -> Unit,
    onGrantOverlay: () -> Unit,
    onToggleApp: (InstalledApp, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onBack, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("← Назад")
            }
            Spacer(Modifier.height(8.dp))
            Text("Барьер отвлечений", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Пока идёт сессия, при открытии выбранных приложений Sprout покажет " +
                    "экран с напоминанием, за чем ты села. Пройти можно всегда — " +
                    "барьер нужен, чтобы это перестало происходить на автопилоте.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Sprout видит только название открытого приложения — не то, " +
                    "что в нём происходит. Список никуда не отправляется.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Включить барьер", style = MaterialTheme.typography.titleMedium)
                Switch(checked = state.enabled, onCheckedChange = onToggle)
            }

            if (state.enabled) {
                Spacer(Modifier.height(12.dp))
                if (!state.hasUsageAccess) {
                    PermissionCard(
                        title = "Нужен доступ к статистике приложений",
                        body = "Без него Sprout не знает, что открыто поверх него. " +
                            "Система откроет общий список — найди в нём Sprout.",
                        action = "Открыть настройки",
                        onClick = onGrantUsage,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (!state.canDrawOverlay) {
                    PermissionCard(
                        title = "Нужно разрешение рисовать поверх приложений",
                        body = "Иначе барьер некуда показать.",
                        action = "Открыть настройки",
                        onClick = onGrantOverlay,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (state.hasUsageAccess && state.canDrawOverlay) {
                    Text(
                        "Разрешения выданы. Отметь приложения, которые уводят тебя " +
                            "чаще всего — обычно их два-три, а не двадцать.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
        }

        if (state.enabled && state.hasUsageAccess && state.canDrawOverlay) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.apps, key = { it.packageName }) { app ->
                    val checked = app.packageName in state.blocked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onToggleApp(app, !checked) }
                            .padding(horizontal = 24.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { onToggleApp(app, it) })
                        Spacer(Modifier.height(0.dp))
                        Text(app.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        } else {
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionCard(title: String, body: String, action: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

private val sampleApps = listOf(
    InstalledApp("com.instagram.android", "Instagram"),
    InstalledApp("com.google.android.youtube", "YouTube"),
    InstalledApp("org.telegram.messenger", "Telegram"),
)

@Preview(name = "Барьер · выключен", showBackground = true)
@Composable
private fun GuardOff() = SproutTheme(darkTheme = false) {
    GuardScreen(GuardUiState(), {}, {}, {}, { _, _ -> }, {})
}

@Preview(name = "Барьер · нет разрешений", showBackground = true)
@Composable
private fun GuardNoPermissions() = SproutTheme(darkTheme = false) {
    GuardScreen(GuardUiState(enabled = true), {}, {}, {}, { _, _ -> }, {})
}

@Preview(name = "Барьер · список", showBackground = true)
@Composable
private fun GuardList() = SproutTheme(darkTheme = false) {
    GuardScreen(
        GuardUiState(
            enabled = true,
            hasUsageAccess = true,
            canDrawOverlay = true,
            apps = sampleApps,
            blocked = setOf("com.instagram.android"),
        ),
        {}, {}, {}, { _, _ -> }, {},
    )
}
