package com.sprout.focus.plan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sprout.focus.MainActivity
import com.sprout.focus.R
import com.sprout.focus.data.Task

/**
 * Уведомление по плану «если — то».
 *
 * Здесь приложение делает то, ради чего затевалось: подаёт голос в момент
 * исполнения, а не планирования. Поэтому обе кнопки ведут к действию,
 * и ни одна не предлагает «напомнить позже» — отложить и так можно,
 * просто смахнув уведомление, и это честнее, чем кнопка, которая
 * притворяется решением.
 */
object PlanNotifications {

    private const val CHANNEL = "plan_trigger"

    /** Своя полка идентификаторов: у сессии заняты 101 и 102. */
    private const val ID_BASE = 200

    /** Столько же, сколько таймер по умолчанию на экране «Сегодня». */
    const val DEFAULT_MINUTES = 20

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "Напоминания по плану",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Приходит в момент, который ты сама назначила"
                enableVibration(true)
            }
        )
    }

    fun showReminder(context: Context, task: Task) {
        // Текст повторяет план и ничего не предъявляет: «ты обещала»
        // и «ты не выполнила» — ровно те формулировки, которые запрещены
        // словарём тона. Напоминание должно возвращать намерение,
        // а не выставлять счёт.
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_tab_garden)
            .setContentTitle(task.title)
            .setContentText(task.promise)
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullPlan(task)))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openActivity(context, task.id, OpenRequest.TARGET_TASK, 40_000))
            .addAction(0, "Начать $DEFAULT_MINUTES мин", startAction(context, task.id))
            .addAction(
                0, "Не могу",
                openActivity(context, task.id, OpenRequest.TARGET_CANT_START, 30_000)
            )

        notify(context, ID_BASE + task.id.toInt(), builder.build())
    }

    fun cancel(context: Context, taskId: Long) =
        NotificationManagerCompat.from(context).cancel(ID_BASE + taskId.toInt())

    private fun fullPlan(task: Task): String = task.planLine ?: task.firstStep

    /**
     * «Начать» ничего не открывает: сессия заводится прямо в получателе,
     * а обратный отсчёт появляется в шторке. Приложение, которое ради
     * начала работы разворачивает себя на весь экран, само становится
     * отвлечением.
     */
    private fun startAction(context: Context, taskId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            20_000 + taskId.toInt(),
            Intent(context, PlanActionReceiver::class.java)
                .setAction(PlanActionReceiver.ACTION_START)
                .setData(Uri.parse("sprout://reminder-start/$taskId"))
                .putExtra(PlanAlarm.EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun openActivity(
        context: Context,
        taskId: Long,
        target: String,
        requestBase: Int,
    ): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestBase + taskId.toInt(),
            Intent(context, MainActivity::class.java)
                .setData(Uri.parse("sprout://open/$target/$taskId"))
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(OpenRequest.EXTRA_OPEN, target)
                .putExtra(OpenRequest.EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Разрешение на уведомления может быть не выдано — тогда просто молчим. */
    private fun notify(context: Context, id: Int, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }
}
