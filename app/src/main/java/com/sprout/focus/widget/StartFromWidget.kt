package com.sprout.focus.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.sprout.focus.SproutApplication
import com.sprout.focus.data.Session

/**
 * Кнопка на виджете.
 *
 * Сессия заводится здесь же, без единого экрана: в этом весь смысл виджета.
 * Приложение при этом не открывается — телефон можно сразу отложить.
 */
class StartFromWidget : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val app = context.applicationContext as SproutApplication
        val task = app.database.dao().getCurrentTask() ?: return

        // Повторное нажатие по уже идущей сессии сбросило бы её и записало
        // как брошенную. Кнопки в этот момент на виджете нет, но нажатие
        // может доехать от прошлой отрисовки.
        if (app.database.dao().getActiveSession() != null) return

        app.sessions.start(task, Session.MODE_POMODORO, SproutWidget.DEFAULT_MINUTES * 60)
        SproutWidget.refresh(context)
    }
}
