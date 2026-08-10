package com.sprout.focus.timer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sprout.focus.MainActivity
import com.sprout.focus.R

object FocusNotifications {

    private const val CHANNEL_RUNNING = "focus_running"
    private const val CHANNEL_DONE = "focus_done"

    const val ID_RUNNING = 101
    const val ID_DONE = 102

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        // Идёт сессия — тихий канал, чтобы не выдёргивать из работы
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RUNNING,
                "Идёт сессия",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Обратный отсчёт во время фокус-сессии"
                setShowBadge(false)
            }
        )

        // Сессия закончилась — со звуком и вибрацией
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DONE,
                "Сессия закончилась",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Сигнал об окончании фокус-сессии"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 150, 200)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
        )
    }

    /**
     * Уведомление во время сессии.
     *
     * Обратный отсчёт рисует сама система: мы отдаём ей момент окончания,
     * а она обновляет цифры без нашего участия. Ни таймеров, ни сервисов.
     */
    fun showRunning(context: Context, taskTitle: String, endsAt: Long?, startedAt: Long) {
        notify(context, ID_RUNNING, buildRunning(context, taskTitle, endsAt, startedAt))
    }

    /**
     * То же уведомление, но собранное отдельно.
     *
     * Его же показывает сторож отвлечений: foreground service обязан иметь
     * уведомление, а два одинаковых в шторке — шум. Поэтому сервис берёт
     * это и тот же [ID_RUNNING], и в шторке остаётся одна строка
     * с обратным отсчётом.
     */
    fun buildRunning(
        context: Context,
        taskTitle: String,
        endsAt: Long?,
        startedAt: Long,
    ): android.app.Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_tab_garden)
            .setContentTitle(taskTitle.ifBlank { "Фокус" })
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp(context))
            .setUsesChronometer(true)

        if (endsAt != null) {
            builder.setWhen(endsAt)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setChronometerCountDown(true)
            }
        } else {
            builder.setWhen(startedAt)   // режимы без плана — считаем вверх
        }

        return builder.build()
    }

    fun showFinished(context: Context) {
        val builder = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_tab_garden)
            .setContentTitle("Сессия закончилась")
            .setContentText("Загляни, чтобы отметить, как прошло")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(context))
        notify(context, ID_DONE, builder.build())
    }

    fun cancelRunning(context: Context) =
        NotificationManagerCompat.from(context).cancel(ID_RUNNING)

    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_RUNNING)
        NotificationManagerCompat.from(context).cancel(ID_DONE)
    }

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
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
