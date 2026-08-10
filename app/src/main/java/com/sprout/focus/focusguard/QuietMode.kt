package com.sprout.focus.focusguard

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * «Не беспокоить» на время сессии.
 *
 * Барьер держит человека от того, чтобы уйти в телефон самому. Тишина
 * закрывает вторую половину: телефон, который зовёт первым. Приёмы разные,
 * поэтому и включаются они по отдельности.
 *
 * Глушим системным режимом «приоритетные», а не «полная тишина»: звонки
 * от избранных и повторные проходят по правилам, которые человек уже
 * настроил себе в системе. Приложение против прокрастинации не вправе
 * брать на себя риск съесть настоящий звонок — цена ошибки здесь
 * несопоставима с ценой одного отвлечения.
 *
 * Разрешение особое: диалогом не выдаётся, человек включает доступ руками
 * в настройках системы. Без него всё просто молчит — как и барьер.
 */
object QuietMode {

    /**
     * Помним, что тишину включили мы.
     *
     * Лежит в настройках, а не в памяти: сессия переживает и перезапуск
     * приложения, и перезагрузку телефона, а снимать чужую тишину нельзя
     * ни при каких обстоятельствах. Файл общий с барьером — заводить
     * второй ради одного флага незачем.
     */
    private const val PREFS = "guard"
    private const val KEY_OURS = "quiet_ours"

    fun hasPolicyAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)
            ?.isNotificationPolicyAccessGranted == true

    fun openPolicySettings(context: Context) {
        // Экран общий для всех приложений: открыть его сразу на нужной
        // строке система не даёт — то же, что и с доступом к статистике.
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * Включить тишину, если её ещё нет.
     *
     * Если человек уже сам сидит в «не беспокоить», не трогаем ничего
     * и не ставим метку: иначе на конце сессии мы вернули бы звук,
     * которого он не просил.
     */
    fun enter(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!nm.isNotificationPolicyAccessGranted) return
        if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return

        try {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        } catch (_: SecurityException) {
            return
        }
        mark(context, true)
    }

    /**
     * Вернуть звук — но только свой.
     *
     * Метки нет — тишину включали не мы, и делать с ней нечего. Метка есть,
     * но режим с тех пор сменился руками — человек уже распорядился сам,
     * и наше вмешательство только испортило бы его выбор.
     */
    fun leave(context: Context) {
        if (!isOurs(context)) return
        mark(context, false)

        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!nm.isNotificationPolicyAccessGranted) return
        if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_PRIORITY) return

        try {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        } catch (_: SecurityException) {
        }
    }

    fun isOurs(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_OURS, false)

    private fun mark(context: Context, ours: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_OURS, ours)
            .apply()
    }
}
