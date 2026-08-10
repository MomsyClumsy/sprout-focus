package com.sprout.focus.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Установленное приложение, которое можно отметить отвлекающим. */
data class InstalledApp(val packageName: String, val label: String)

/**
 * Список отвлекающих приложений и включатель барьера.
 *
 * Сам список лежит в базе, а «включено ли» — в обычных настройках:
 * это не событие и не данные о человеке, а состояние одного тумблера.
 * В таблицу событий оно не просится, а заводить ради него таблицу
 * настроек — плодить сущность на одно поле.
 */
class GuardRepository(private val dao: SproutDao, private val context: Context) {

    private val prefs = context.getSharedPreferences("guard", Context.MODE_PRIVATE)

    val blockedApps: Flow<List<BlockedApp>> = dao.observeBlockedApps()

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /**
     * Включать ли «не беспокоить» на время сессии.
     *
     * Отдельный тумблер, а не часть барьера: барьер держит от ухода
     * в телефон, тишина — от того, что телефон позовёт первым. Человеку
     * может понадобиться одно без другого.
     */
    var quietEnabled: Boolean
        get() = prefs.getBoolean(KEY_QUIET, false)
        set(value) = prefs.edit().putBoolean(KEY_QUIET, value).apply()

    suspend fun blockedPackages(): Set<String> = dao.blockedPackages().toSet()

    suspend fun add(app: InstalledApp) =
        dao.addBlockedApp(BlockedApp(app.packageName, app.label))

    suspend fun remove(packageName: String) = dao.removeBlockedApp(packageName)

    /**
     * Что вообще установлено.
     *
     * Спрашиваем только те приложения, у которых есть значок в лаунчере:
     * системную обвязку блокировать бессмысленно, а разрешение
     * QUERY_ALL_PACKAGES при этом не нужно — хватает объявленного
     * в манифесте запроса на MAIN/LAUNCHER.
     */
    suspend fun installedApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** Барьер показан: рука сама открыла ленту. */
    suspend fun caught(packageName: String, taskId: Long?) {
        dao.insertEvent(
            Event(
                type = EventType.DISTRACTION_CAUGHT,
                taskId = taskId,
                payload = """{"package":"$packageName"}""",
            )
        )
    }

    suspend fun answered(packageName: String, taskId: Long?, returned: Boolean) {
        dao.insertEvent(
            Event(
                type = if (returned) EventType.DISTRACTION_RETURNED else EventType.DISTRACTION_PASSED,
                taskId = taskId,
                payload = """{"package":"$packageName"}""",
            )
        )
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_QUIET = "quiet_enabled"
    }
}
