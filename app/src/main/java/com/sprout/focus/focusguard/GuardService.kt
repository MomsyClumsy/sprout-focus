package com.sprout.focus.focusguard

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.sprout.focus.SproutApplication
import com.sprout.focus.timer.FocusNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Сторож отвлечений: смотрит, что на переднем плане, пока идёт сессия.
 *
 * Это единственный foreground service в приложении, и появился он неохотно.
 * Таймер сознательно обходится без сервиса — состояние лежит в базе, а время
 * считает система. Но узнать, какое приложение открыто **сейчас**, может
 * только живой процесс: система об этом не уведомляет, спросить можно лишь
 * опросом. Поэтому сервис живёт ровно столько, сколько идёт сессия, и только
 * если барьер включён.
 *
 * Своего уведомления у него нет: он берёт то же самое уведомление сессии
 * с обратным отсчётом, чтобы в шторке не появлялось второй строки.
 */
class GuardService : Service() {

    private val scope = CoroutineScope(SupervisorJob())
    private var watching: Job? = null

    /**
     * Пакеты, которым выдан короткий пропуск.
     *
     * Живёт в памяти сервиса намеренно: пропуск действует внутри одной
     * сессии и не должен переживать её. Записывать такое в базу — значит
     * копить состояние, которое потом придётся не забыть очистить.
     */
    private val passUntil = mutableMapOf<String, Long>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PASS -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE)
                if (pkg != null) {
                    passUntil[pkg] = System.currentTimeMillis() + FocusGuard.PASS_MINUTES * 60_000L
                }
                return START_STICKY
            }
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        val endsAt = intent?.getLongExtra(EXTRA_ENDS_AT, 0L)?.takeIf { it > 0 }
        val startedAt = intent?.getLongExtra(EXTRA_STARTED_AT, System.currentTimeMillis())
            ?: System.currentTimeMillis()

        startInForeground(title, endsAt, startedAt)
        if (watching == null) watching = scope.launch { watch() }
        return START_STICKY
    }

    private fun startInForeground(title: String, endsAt: Long?, startedAt: Long) {
        val notification = FocusNotifications.buildRunning(this, title, endsAt, startedAt)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                FocusNotifications.ID_RUNNING,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(FocusNotifications.ID_RUNNING, notification)
        }
    }

    private suspend fun watch() {
        val app = applicationContext as SproutApplication
        val guard = app.guard
        var lastSeen = ""

        while (scope.isActive) {
            val current = foregroundPackage()
            if (current != null && current != lastSeen) {
                lastSeen = current
                val blocked = guard.blockedPackages()
                val passed = passUntil[current]?.let { it > System.currentTimeMillis() } == true

                if (current in blocked && !passed) {
                    val session = app.database.dao().getActiveSession()
                    // Сессия могла кончиться между опросами — барьер тогда
                    // ни при чём, и показывать его было бы обманом
                    if (session != null && !session.isPaused) {
                        val task = session.taskId?.let { app.database.dao().getTask(it) }
                        guard.caught(current, session.taskId)
                        BarrierWindow.show(this, current, task?.title.orEmpty(), session.endsAt(System.currentTimeMillis()))
                    } else {
                        stopSelf()
                    }
                }
            } else if (current != null && current == packageName) {
                // Человек уже в Sprout — барьеру тут нечего делать
                BarrierWindow.hide()
            }
            delay(FocusGuard.POLL_INTERVAL_MS)
        }
    }

    /**
     * Какое приложение сейчас на переднем плане.
     *
     * Спрашиваем события, а не сводку: сводка обновляется с задержкой и
     * умеет соврать про «последнее использованное». Окно берём с запасом
     * в несколько секунд — событие могло случиться прямо между опросами.
     */
    private fun foregroundPackage(): String? {
        val usage = getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val events = usage.queryEvents(now - 10_000, now)
        val event = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                last = event.packageName
            }
        }
        return last
    }

    override fun onDestroy() {
        BarrierWindow.hide()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ENDS_AT = "endsAt"
        private const val EXTRA_STARTED_AT = "startedAt"
        const val EXTRA_PACKAGE = "package"

        const val ACTION_PASS = "com.sprout.focus.action.GUARD_PASS"
        const val ACTION_STOP = "com.sprout.focus.action.GUARD_STOP"

        fun start(context: Context, title: String, endsAt: Long?, startedAt: Long) {
            val intent = Intent(context, GuardService::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_ENDS_AT, endsAt ?: 0L)
                .putExtra(EXTRA_STARTED_AT, startedAt)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, GuardService::class.java).setAction(ACTION_STOP)
            )
        }

        /** Короткий пропуск: «мне правда нужно» — не повод спорить дальше. */
        fun pass(context: Context, packageName: String) {
            context.startService(
                Intent(context, GuardService::class.java)
                    .setAction(ACTION_PASS)
                    .putExtra(EXTRA_PACKAGE, packageName)
            )
        }
    }
}
