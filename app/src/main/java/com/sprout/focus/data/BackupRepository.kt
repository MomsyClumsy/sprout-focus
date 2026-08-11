package com.sprout.focus.data

import android.content.Context
import com.sprout.focus.BuildConfig
import com.sprout.focus.widget.SproutWidget
import java.io.InputStream
import java.io.OutputStream
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Копия данных: собрать файл и восстановить из него.
 *
 * Знает про базу, настройки и файлы; ничего не считает и не формулирует —
 * та же граница, что у остальных репозиториев. Всё, что превращается
 * в текст и обратно, живёт в [Backup] и проверяется тестами.
 *
 * **Один файл, а не четыре.** Внутри `.zip` лежит `sprout.json`, из которого
 * идёт восстановление, и три таблицы CSV для человека. Системный диалог
 * сохраняет по одному файлу за раз, и четыре диалога подряд ради одной
 * копии — способ сделать так, чтобы копию не делали никогда.
 */
class BackupRepository(
    private val dao: SproutDao,
    private val guard: GuardRepository,
    private val experiments: ExperimentRepository,
    private val plans: PlanRepository,
    private val context: Context,
) {

    /** Имя файла по умолчанию: с датой, чтобы копии не затирали друг друга. */
    fun suggestedName(now: Long = System.currentTimeMillis()): String =
        "sprout-${isoDay(now)}.zip"

    suspend fun exportTo(out: OutputStream) {
        val data = collect()
        Backup.zoneOffset = { millis -> TimeZone.getDefault().getOffset(millis).toLong() }

        ZipOutputStream(out.buffered()).use { zip ->
            zip.write(Backup.ENTRY_JSON, Backup.encode(data))
            zip.write(Backup.ENTRY_TASKS_CSV, Backup.tasksCsv(data.tasks))
            zip.write(Backup.ENTRY_EVENTS_CSV, Backup.eventsCsv(data.events))
            zip.write(Backup.ENTRY_SESSIONS_CSV, Backup.sessionsCsv(data.sessions))
        }
    }

    /**
     * Прочитать файл, ничего не меняя.
     *
     * Отдельным шагом от [restore] намеренно: человеку сначала показывают,
     * что в файле, и только потом спрашивают, заменять ли этим свои данные.
     * Согласие вслепую на необратимое действие — не согласие.
     */
    fun read(input: InputStream): BackupData = Backup.decode(readJson(input))

    /**
     * Заменить всё, что есть в приложении, данными из копии.
     *
     * После замены обязательно переставить будильники и обновить виджет:
     * напоминания в базе теперь чужие, а те, что стояли в системе, — от
     * задач, которых больше нет. Забытый будильник сработает по пустоте,
     * а виджет останется показывать вчерашнюю задачу (грабли №11 и №15).
     */
    suspend fun restore(data: BackupData) {
        if (dao.getActiveSession() != null) {
            throw Backup.Broken("Сейчас идёт заход. Закончи его — и можно восстанавливать")
        }

        dao.replaceAll(data)

        guard.enabled = data.settings.barrierEnabled
        guard.quietEnabled = data.settings.quietEnabled
        experiments.setKept(Experiments.SHORTER, data.settings.keptShortFirst)
        experiments.setKept(Experiments.IF_THEN, data.settings.keptPlanRequired)

        plans.rescheduleAll()
        SproutWidget.refresh(context)
    }

    private suspend fun collect() = BackupData(
        schemaVersion = SCHEMA_VERSION,
        appVersion = BuildConfig.VERSION_NAME,
        exportedAt = System.currentTimeMillis(),
        settings = BackupSettings(
            barrierEnabled = guard.enabled,
            quietEnabled = guard.quietEnabled,
            keptShortFirst = experiments.keptChanges.value.shortLengthsFirst,
            keptPlanRequired = experiments.keptChanges.value.planAlwaysRequired,
        ),
        tasks = dao.allTasks(),
        events = dao.allEvents(),
        sessions = dao.allSessions(),
        garden = dao.getGarden(),
        grownPlants = dao.allGrownPlants(),
        blockedApps = dao.allBlockedApps(),
        experiments = dao.allExperiments(),
    )

    /**
     * Достать json из выбранного файла.
     *
     * Обычно это наш zip, но человек может ткнуть и в распакованный
     * `sprout.json` — он лежит рядом и выглядит как то, что нужно.
     * Отказывать в этом случае не за что.
     */
    private fun readJson(input: InputStream): String {
        val bytes = input.readBytes()
        if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            ZipInputStream(bytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == Backup.ENTRY_JSON) return zip.readBytes().decodeToString()
                    entry = zip.nextEntry
                }
            }
            throw Backup.Broken("В этом архиве нет копии Sprout")
        }
        return bytes.decodeToString()
    }

    private fun ZipOutputStream.write(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray())
        closeEntry()
    }

    private fun isoDay(millis: Long): String {
        val local = millis + TimeZone.getDefault().getOffset(millis)
        var days = Math.floorDiv(local, 86_400_000L)
        var year = 1970
        while (true) {
            val len = if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 366 else 365
            if (days < len) break
            days -= len
            year++
        }
        val leap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
        val lengths = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var month = 0
        while (days >= lengths[month]) {
            days -= lengths[month]
            month++
        }
        return "%04d-%02d-%02d".format(year, month + 1, days + 1)
    }
}
