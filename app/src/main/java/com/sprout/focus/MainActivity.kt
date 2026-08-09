package com.sprout.focus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sprout.focus.plan.OpenRequest
import com.sprout.focus.ui.SproutApp
import com.sprout.focus.ui.theme.SproutTheme

class MainActivity : ComponentActivity() {

    /**
     * Что открыть по просьбе уведомления.
     *
     * Живёт в состоянии, а не в поле: намерение приходит и при первом
     * запуске, и в уже открытое приложение через onNewIntent, а экран
     * должен отреагировать в обоих случаях одинаково.
     */
    private var opening by mutableStateOf<OpenRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        opening = OpenRequest.from(intent)
        setContent {
            SproutTheme {
                SproutApp(
                    opening = opening,
                    onOpeningHandled = { opening = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        opening = OpenRequest.from(intent)
    }
}
