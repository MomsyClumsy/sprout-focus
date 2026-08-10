package com.sprout.focus.focusguard

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings

/**
 * Что нужно барьеру, чтобы работать, и как это спросить.
 *
 * Оба разрешения — «особые»: их не выдают диалогом, человек включает их
 * руками в настройках системы. Поэтому здесь только проверка и открытие
 * нужного экрана; объяснение, зачем это, живёт на экране настроек рядом
 * с переключателем.
 *
 * AccessibilityService дал бы то же самое одним разрешением — и заодно
 * доступ ко всему содержимому чужих экранов. Отказ от него сознательный:
 * приложение про доверие не может просить право читать переписку.
 */
object FocusGuard {

    /** Сколько длится «пропустить»: короткий перерыв, а не отмена сессии. */
    const val PASS_MINUTES = 2

    /** Как часто сторож смотрит, что на переднем плане. */
    const val POLL_INTERVAL_MS = 1_000L

    /** Видим ли мы, какое приложение открыто. */
    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Можем ли мы нарисовать барьер поверх чужого приложения. */
    fun canDrawOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun ready(context: Context): Boolean = hasUsageAccess(context) && canDrawOverlay(context)

    fun openUsageAccessSettings(context: Context) {
        // Экран общий для всех приложений: система не даёт открыть его
        // сразу на нужной строке. Поэтому на своём экране мы честно пишем,
        // что там придётся найти Sprout самой.
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openOverlaySettings(context: Context) {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
