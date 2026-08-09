package com.sprout.focus.plan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sprout.focus.SproutApplication
import com.sprout.focus.data.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Кнопка «Начать» в уведомлении.
 *
 * Сессия заводится без единого экрана: задача становится текущей, таймер
 * стартует, и в шторке уведомление напоминания сменяется обратным отсчётом.
 * Дальше телефон можно отложить — приложение открывать незачем.
 */
class PlanActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_START = "com.sprout.focus.action.REMINDER_START"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_START) return
        val taskId = intent.getLongExtra(PlanAlarm.EXTRA_TASK_ID, -1L)
        if (taskId <= 0) return

        PlanNotifications.cancel(context, taskId)

        val pending = goAsync()
        val app = context.applicationContext as SproutApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val task = app.database.dao().getTask(taskId) ?: return@launch
                app.plans.accepted(taskId)
                app.repository.makeCurrent(taskId)
                app.sessions.start(
                    task,
                    Session.MODE_POMODORO,
                    PlanNotifications.DEFAULT_MINUTES * 60
                )
            } finally {
                pending.finish()
            }
        }
    }
}
