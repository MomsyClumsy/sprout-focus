package com.sprout.focus.plan

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.sprout.focus.SproutApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Будильник на момент напоминания.
 *
 * Устроен так же, как будильник конца сессии, но по одному на задачу.
 * Точность здесь не роскошь: напоминание по плану «если — то» работает
 * ровно потому, что бьёт в назначенный момент. Пришедшее на десять минут
 * позже — это уже не тот приём, а просто ещё одно уведомление.
 */
object PlanAlarm {

    const val EXTRA_TASK_ID = "taskId"

    /** Свой диапазон кодов, чтобы не столкнуться с будильником сессии (7001). */
    private const val REQUEST_BASE = 10_000

    fun schedule(context: Context, taskId: Long, atMillis: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, taskId)

        val canBeExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else true

        try {
            if (canBeExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    fun cancel(context: Context, taskId: Long) {
        context.getSystemService(AlarmManager::class.java)
            ?.cancel(pendingIntent(context, taskId))
    }

    /**
     * У PendingIntent extras не участвуют в сравнении: два намерения с разными
     * taskId, но одинаковым кодом и данными, считались бы одним и тем же,
     * и напоминания затирали бы друг друга. Различаем и кодом, и адресом.
     */
    private fun pendingIntent(context: Context, taskId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_BASE + taskId.toInt(),
            Intent(context, PlanReceiver::class.java)
                .setData(Uri.parse("sprout://reminder/$taskId"))
                .putExtra(EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

/** Наступил момент напоминания. */
class PlanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(PlanAlarm.EXTRA_TASK_ID, -1L)
        if (taskId <= 0) return

        // Показать уведомление нужно после похода в базу, а onReceive
        // не умеет ждать. goAsync даёт около десяти секунд — с запасом.
        val pending = goAsync()
        val app = context.applicationContext as SproutApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val task = app.plans.onFired(taskId)
                if (task != null) PlanNotifications.showReminder(context, task)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Будильники не переживают перезагрузку — их надо расставить заново.
 *
 * Переустановку приложения тоже: во время разработки это происходит
 * по несколько раз в день, и без этого напоминания молча пропадают.
 */
class PlanBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pending = goAsync()
        val app = context.applicationContext as SproutApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.plans.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
