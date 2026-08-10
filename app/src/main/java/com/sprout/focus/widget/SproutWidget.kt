package com.sprout.focus.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.sprout.focus.MainActivity
import com.sprout.focus.SproutApplication
import com.sprout.focus.data.Session
import com.sprout.focus.data.Task
import java.time.Instant
import java.time.ZoneId

/**
 * Виджет на домашний экран.
 *
 * Смысл ровно один: начать, не открывая приложение. Приложение, которое ради
 * начала работы разворачивает себя на весь экран, само становится
 * отвлечением — по той же причине кнопка «Начать» в уведомлении заводит
 * сессию прямо в получателе.
 *
 * Рисует виджет чужой процесс — лаунчер. Поэтому здесь нет ни ViewModel,
 * ни подписок: состояние читается из базы один раз при каждой перерисовке,
 * а перерисовку заказывает приложение через [refresh].
 */
object SproutWidget : GlanceAppWidget() {

    /** Длина сессии с виджета. Та же, что по умолчанию в приложении. */
    const val DEFAULT_MINUTES = 20

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = (context.applicationContext as SproutApplication).database.dao()

        // Первые значения читаем разово: без них при каждой отрисовке
        // мелькнуло бы «Задача не выбрана», пока не придёт первое значение
        // из базы.
        val firstTask = dao.getCurrentTask()
        val firstSession = dao.getActiveSession()

        provideContent {
            // Дальше — подписка, а не разовое чтение. Прочитанное до
            // provideContent замирает навсегда: пока отрисовка жива,
            // обновление виджета её только пересобирает, а данные остаются
            // те же. Именно так виджет и продолжал предлагать «20 минут»
            // поверх уже идущей сессии.
            val task by dao.observeCurrentTask().collectAsState(initial = firstTask)
            val session by dao.observeActiveSession().collectAsState(initial = firstSession)

            GlanceTheme(colors = SproutGlanceColors) {
                Body(task, session)
            }
        }
    }

    /**
     * Перерисовать виджет.
     *
     * Вызывается отовсюду, где меняется то, что на нём написано. Виджет,
     * показывающий вчерашнюю задачу, хуже отсутствующего: по нему принимают
     * решение, не открывая приложение.
     */
    suspend fun refresh(context: Context) {
        updateAll(context.applicationContext)
    }

    @Composable
    private fun Body(task: Task?, session: Session?) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp)
                .padding(16.dp)
                // Тап мимо кнопки открывает приложение — обычное ожидание
                // от виджета и единственный путь, когда задачи ещё нет.
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        ) {
            when {
                task == null -> Invitation()
                session != null -> Running(task, session)
                else -> Ready(task)
            }
        }
    }

    /** Задачи нет. Ничего не просим — просто говорим, что здесь пусто. */
    @Composable
    private fun Invitation() {
        Text(
            text = "Задача не выбрана",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = "Нажми, чтобы открыть Sprout",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }

    /** Задача есть, сессии нет — главный случай, ради которого всё это. */
    @Composable
    private fun Ready(task: Task) {
        Text(
            text = task.title,
            maxLines = 2,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(GlanceModifier.height(2.dp))
        // Первый шаг важнее названия: начинают именно с него
        Text(
            text = task.firstStep,
            maxLines = 2,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(GlanceModifier.height(10.dp))
        StartButton()
    }

    /**
     * Сессия идёт.
     *
     * Показываем время окончания, а не обратный отсчёт: виджеты обновляются
     * редко, и тикающий счётчик в них быстро расходится с реальностью.
     * Точный отсчёт и так идёт в шторке, где его рисует сама система.
     */
    @Composable
    private fun Running(task: Task, session: Session) {
        val endsAt = session.endsAt(System.currentTimeMillis())
        Text(
            text = task.title,
            maxLines = 2,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = when {
                session.isPaused -> "На паузе"
                endsAt != null -> "Идёт работа · до ${formatTime(endsAt)}"
                else -> "Идёт работа"
            },
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
        )
    }

    @Composable
    private fun StartButton() {
        // Не Button: у него своя форма и свой цвет текста, а нужен тот же
        // скруглённый контейнер, что у карточек в приложении.
        Text(
            text = "$DEFAULT_MINUTES минут",
            style = TextStyle(
                color = GlanceTheme.colors.onPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(GlanceTheme.colors.primary)
                .cornerRadius(20.dp)
                .padding(vertical = 10.dp)
                .clickable(actionRunCallback<StartFromWidget>()),
        )
    }

    private fun formatTime(at: Long): String {
        val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalTime()
        return "%02d:%02d".format(time.hour, time.minute)
    }
}
