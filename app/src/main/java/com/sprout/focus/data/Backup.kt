package com.sprout.focus.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Копия всех данных: сборка файла и разбор обратно.
 *
 * Без Android и без базы — только превращение объектов в текст и текста
 * в объекты. Поэтому проверяется обычными тестами, а не на устройстве:
 * ошибка здесь стоит дороже любой другой в приложении. Данные Sprout
 * живут в одном экземпляре на телефоне, и файл копии — единственное,
 * из чего их можно восстановить.
 *
 * **Что в файле:** `sprout.json` — всё, из чего восстанавливается состояние.
 * Рядом кладутся три CSV (задачи, события, сессии) — их приложение не читает,
 * они для человека: открыть в Excel и посчитать своё.
 *
 * **Версий две, и это разные вещи.** [FORMAT_VERSION] — версия самого файла
 * копии; [BackupData.schemaVersion] — версия схемы базы, из которой копия
 * снята. Файл из будущей версии приложения разбирать нельзя: в нём могут быть
 * поля, о которых эта версия не знает, и «восстановление» тихо потеряло бы их.
 */
object Backup {

    const val FORMAT = "sprout-backup"
    const val FORMAT_VERSION = 1

    /** Имена файлов внутри архива. */
    const val ENTRY_JSON = "sprout.json"
    const val ENTRY_TASKS_CSV = "tasks.csv"
    const val ENTRY_EVENTS_CSV = "events.csv"
    const val ENTRY_SESSIONS_CSV = "sessions.csv"

    /**
     * Не техническая ошибка, а то, что будет показано человеку.
     * Поэтому текст — законченная фраза без слов «сбой» и «неверный формат».
     */
    class Broken(message: String) : Exception(message)

    fun encode(data: BackupData): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("formatVersion", FORMAT_VERSION)
        root.put("schemaVersion", data.schemaVersion)
        root.put("appVersion", data.appVersion)
        root.put("exportedAt", data.exportedAt)

        root.put("settings", JSONObject().apply {
            put("barrierEnabled", data.settings.barrierEnabled)
            put("quietEnabled", data.settings.quietEnabled)
            put("keptShortFirst", data.settings.keptShortFirst)
            put("keptPlanRequired", data.settings.keptPlanRequired)
        })

        root.put("tasks", data.tasks.jsonArray(::taskJson))
        root.put("events", data.events.jsonArray(::eventJson))
        root.put("sessions", data.sessions.jsonArray(::sessionJson))
        root.put("garden", data.garden?.let(::gardenJson) ?: JSONObject.NULL)
        root.put("grownPlants", data.grownPlants.jsonArray(::grownJson))
        root.put("blockedApps", data.blockedApps.jsonArray(::blockedJson))
        root.put("experiments", data.experiments.jsonArray(::experimentJson))

        return root.toString(2)
    }

    fun decode(text: String): BackupData {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw Broken("Этот файл не похож на копию Sprout")
        }

        if (root.optString("format") != FORMAT) {
            throw Broken("Этот файл не похож на копию Sprout")
        }
        val formatVersion = root.optInt("formatVersion", 0)
        if (formatVersion > FORMAT_VERSION) {
            throw Broken(
                "Копия сделана более новой версией приложения. " +
                    "Обнови Sprout и попробуй снова"
            )
        }
        val schemaVersion = root.optInt("schemaVersion", 0)
        if (schemaVersion > SCHEMA_VERSION) {
            throw Broken(
                "Копия сделана более новой версией приложения. " +
                    "Обнови Sprout и попробуй снова"
            )
        }

        return try {
            BackupData(
                schemaVersion = schemaVersion,
                appVersion = root.optString("appVersion"),
                exportedAt = root.optLong("exportedAt"),
                settings = root.optJSONObject("settings").let { s ->
                    BackupSettings(
                        barrierEnabled = s?.optBoolean("barrierEnabled") ?: false,
                        quietEnabled = s?.optBoolean("quietEnabled") ?: false,
                        keptShortFirst = s?.optBoolean("keptShortFirst") ?: false,
                        keptPlanRequired = s?.optBoolean("keptPlanRequired") ?: false,
                    )
                },
                tasks = root.list("tasks", ::taskOf),
                events = root.list("events", ::eventOf),
                sessions = root.list("sessions", ::sessionOf),
                garden = root.optJSONObject("garden")?.let(::gardenOf),
                grownPlants = root.list("grownPlants", ::grownOf),
                blockedApps = root.list("blockedApps", ::blockedOf),
                experiments = root.list("experiments", ::experimentOf),
            )
        } catch (e: JSONException) {
            // Файл наш, но внутри чего-то не хватает. Пустить такое
            // в базу нельзя: восстановление затирает всё, что было
            throw Broken("Копия повреждена: в ней не хватает данных")
        }
    }

    // --- CSV для человека ---

    /**
     * Сдвиг часового пояса для дат в CSV.
     *
     * Даты в базе — обычные метки времени, то есть UTC. Человек, открывший
     * файл, читает их как своё местное время, и заход в 21:00 превращается
     * в 18:00 без единого предупреждения. Поэтому смещение передаётся снаружи:
     * функцией, а не готовым числом, — оно разное летом и зимой, и брать
     * его надо на момент каждой записи, а не на момент выгрузки.
     *
     * Здесь по умолчанию ноль, чтобы этот файл ничего не знал про систему
     * и проверялся обычными тестами.
     */
    var zoneOffset: (Long) -> Long = { 0 }

    fun tasksCsv(tasks: List<Task>): String = csv(
        listOf(
            "id", "название", "первый шаг", "зацепка", "статус", "текущая",
            "создана", "завершена", "отложена раз", "напоминание", "дни",
        ),
        tasks.map {
            listOf(
                it.id, it.title, it.firstStep, it.ifTrigger.orEmpty(), it.status,
                if (it.isCurrent) "да" else "", isoOrEmpty(it.createdAt),
                it.completedAt?.let(::isoOrEmpty).orEmpty(), it.postponeCount,
                it.remindMinuteOfDay?.let(Reminder::formatTime).orEmpty(),
                if (it.remindMinuteOfDay == null) "" else Reminder.formatDays(it.remindDaysMask),
            )
        }
    )

    fun eventsCsv(events: List<Event>): String = csv(
        listOf("id", "когда", "что", "задача", "подробности"),
        events.map { listOf(it.id, isoOrEmpty(it.at), it.type, it.taskId ?: "", it.payload) }
    )

    fun sessionsCsv(sessions: List<Session>): String = csv(
        listOf(
            "id", "задача", "режим", "запланировано мин", "начата", "закончена",
            "фактически мин", "доведён до конца", "пауза сек", "оценка", "отвлечения",
        ),
        sessions.map {
            listOf(
                it.id, it.taskId ?: "", it.mode, it.plannedSeconds / 60,
                isoOrEmpty(it.startedAt), it.endedAt?.let(::isoOrEmpty).orEmpty(),
                it.actualSeconds?.let { s -> s / 60 } ?: "",
                if (it.completed) "да" else "нет", it.pausedTotal,
                it.selfRating ?: "", it.interruptions ?: "",
            )
        }
    )

    /**
     * Разделитель — точка с запятой, а не запятая.
     *
     * Excel в русской локали читает CSV именно так, а с запятой сваливает
     * всю строку в один столбец. Файл делается для того, чтобы его открыли,
     * а не чтобы он был правильным по спецификации.
     */
    private fun csv(header: List<String>, rows: List<List<Any>>): String {
        val sb = StringBuilder()
        // BOM: без него Excel открывает кириллицу кракозябрами
        sb.append('﻿')
        sb.append(header.joinToString(";") { escape(it) }).append("\r\n")
        rows.forEach { row ->
            sb.append(row.joinToString(";") { escape(it.toString()) }).append("\r\n")
        }
        return sb.toString()
    }

    private fun escape(value: String): String =
        if (value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /**
     * Дата в виде, который читается и человеком, и Excel.
     * Считается вручную: тут нет Android, а тащить форматтер ради
     * одной строки незачем.
     */
    private fun isoOrEmpty(millisUtc: Long): String {
        if (millisUtc <= 0) return ""
        val millis = millisUtc + zoneOffset(millisUtc)
        var days = Math.floorDiv(millis, 86_400_000L)
        val msOfDay = Math.floorMod(millis, 86_400_000L)
        var year = 1970
        while (true) {
            val len = if (isLeap(year)) 366 else 365
            if (days < len) break
            days -= len
            year++
        }
        val lengths = intArrayOf(31, if (isLeap(year)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var month = 0
        while (days >= lengths[month]) {
            days -= lengths[month]
            month++
        }
        val day = days + 1
        val hour = msOfDay / 3_600_000
        val minute = (msOfDay / 60_000) % 60
        return "%04d-%02d-%02d %02d:%02d".format(year, month + 1, day, hour, minute)
    }

    private fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0

    // --- сущности в JSON ---

    private fun taskJson(t: Task) = JSONObject().apply {
        put("id", t.id)
        put("title", t.title)
        put("firstStep", t.firstStep)
        putOrNull("ifTrigger", t.ifTrigger)
        putOrNull("thenAction", t.thenAction)
        putOrNull("copingPlan", t.copingPlan)
        putOrNull("whyItMatters", t.whyItMatters)
        putOrNull("parentTaskId", t.parentTaskId)
        put("status", t.status)
        put("isCurrent", t.isCurrent)
        put("createdAt", t.createdAt)
        putOrNull("completedAt", t.completedAt)
        put("postponeCount", t.postponeCount)
        putOrNull("lastStoppedAt", t.lastStoppedAt)
        putOrNull("remindMinuteOfDay", t.remindMinuteOfDay)
        put("remindDaysMask", t.remindDaysMask)
        putOrNull("remindNextAt", t.remindNextAt)
    }

    private fun taskOf(o: JSONObject) = Task(
        id = o.getLong("id"),
        title = o.getString("title"),
        firstStep = o.getString("firstStep"),
        ifTrigger = o.stringOrNull("ifTrigger"),
        thenAction = o.stringOrNull("thenAction"),
        copingPlan = o.stringOrNull("copingPlan"),
        whyItMatters = o.stringOrNull("whyItMatters"),
        parentTaskId = o.longOrNull("parentTaskId"),
        status = o.getString("status"),
        isCurrent = o.optBoolean("isCurrent"),
        createdAt = o.getLong("createdAt"),
        completedAt = o.longOrNull("completedAt"),
        postponeCount = o.optInt("postponeCount"),
        lastStoppedAt = o.stringOrNull("lastStoppedAt"),
        remindMinuteOfDay = o.intOrNull("remindMinuteOfDay"),
        remindDaysMask = o.optInt("remindDaysMask"),
        remindNextAt = o.longOrNull("remindNextAt"),
    )

    private fun eventJson(e: Event) = JSONObject().apply {
        put("id", e.id)
        put("type", e.type)
        put("at", e.at)
        putOrNull("taskId", e.taskId)
        put("payload", e.payload)
    }

    private fun eventOf(o: JSONObject) = Event(
        id = o.getLong("id"),
        type = o.getString("type"),
        at = o.getLong("at"),
        taskId = o.longOrNull("taskId"),
        payload = o.optString("payload"),
    )

    private fun sessionJson(s: Session) = JSONObject().apply {
        put("id", s.id)
        putOrNull("taskId", s.taskId)
        put("mode", s.mode)
        put("plannedSeconds", s.plannedSeconds)
        put("startedAt", s.startedAt)
        putOrNull("endedAt", s.endedAt)
        putOrNull("pausedAt", s.pausedAt)
        put("pausedTotal", s.pausedTotal)
        putOrNull("actualSeconds", s.actualSeconds)
        put("completed", s.completed)
        putOrNull("selfRating", s.selfRating)
        putOrNull("interruptions", s.interruptions)
        putOrNull("stoppedNote", s.stoppedNote)
    }

    private fun sessionOf(o: JSONObject) = Session(
        id = o.getLong("id"),
        taskId = o.longOrNull("taskId"),
        mode = o.getString("mode"),
        plannedSeconds = o.optInt("plannedSeconds"),
        startedAt = o.getLong("startedAt"),
        endedAt = o.longOrNull("endedAt"),
        pausedAt = o.longOrNull("pausedAt"),
        pausedTotal = o.optInt("pausedTotal"),
        actualSeconds = o.intOrNull("actualSeconds"),
        completed = o.optBoolean("completed"),
        selfRating = o.intOrNull("selfRating"),
        interruptions = o.intOrNull("interruptions"),
        stoppedNote = o.stringOrNull("stoppedNote"),
    )

    private fun gardenJson(g: Garden) = JSONObject().apply {
        put("id", g.id)
        put("points", g.points)
        put("plantStartedAt", g.plantStartedAt)
        put("grownCount", g.grownCount)
        put("streak", g.streak)
        putOrNull("lastActiveDay", g.lastActiveDay)
        put("freezesLeft", g.freezesLeft)
        put("freezeMonth", g.freezeMonth)
        putOrNull("growthDay", g.growthDay)
        put("growthToday", g.growthToday)
    }

    private fun gardenOf(o: JSONObject) = Garden(
        id = o.optInt("id", 1),
        points = o.optInt("points"),
        plantStartedAt = o.optLong("plantStartedAt"),
        grownCount = o.optInt("grownCount"),
        streak = o.optInt("streak"),
        lastActiveDay = o.stringOrNull("lastActiveDay"),
        freezesLeft = o.optInt("freezesLeft"),
        freezeMonth = o.optString("freezeMonth"),
        growthDay = o.stringOrNull("growthDay"),
        growthToday = o.optInt("growthToday"),
    )

    private fun grownJson(p: GrownPlant) = JSONObject().apply {
        put("id", p.id)
        put("startedAt", p.startedAt)
        put("completedAt", p.completedAt)
    }

    private fun grownOf(o: JSONObject) = GrownPlant(
        id = o.getLong("id"),
        startedAt = o.getLong("startedAt"),
        completedAt = o.getLong("completedAt"),
    )

    private fun blockedJson(b: BlockedApp) = JSONObject().apply {
        put("packageName", b.packageName)
        put("label", b.label)
        put("addedAt", b.addedAt)
    }

    private fun blockedOf(o: JSONObject) = BlockedApp(
        packageName = o.getString("packageName"),
        label = o.getString("label"),
        addedAt = o.optLong("addedAt"),
    )

    private fun experimentJson(e: Experiment) = JSONObject().apply {
        put("id", e.id)
        put("hypothesis", e.hypothesis)
        put("startedAt", e.startedAt)
        put("endsAt", e.endsAt)
        put("baselinePercent", e.baselinePercent)
        put("baselineCount", e.baselineCount)
        putOrNull("endedAt", e.endedAt)
        putOrNull("outcome", e.outcome)
        putOrNull("resultPercent", e.resultPercent)
        putOrNull("observations", e.observations)
        putOrNull("succeeded", e.succeeded)
        putOrNull("resolvedAt", e.resolvedAt)
        put("kept", e.kept)
    }

    private fun experimentOf(o: JSONObject) = Experiment(
        id = o.getLong("id"),
        hypothesis = o.getString("hypothesis"),
        startedAt = o.getLong("startedAt"),
        endsAt = o.getLong("endsAt"),
        baselinePercent = o.optInt("baselinePercent"),
        baselineCount = o.optInt("baselineCount"),
        endedAt = o.longOrNull("endedAt"),
        outcome = o.stringOrNull("outcome"),
        resultPercent = o.intOrNull("resultPercent"),
        observations = o.intOrNull("observations"),
        succeeded = o.intOrNull("succeeded"),
        resolvedAt = o.longOrNull("resolvedAt"),
        kept = o.optBoolean("kept"),
    )

    // --- мелочи, из-за которых JSONObject неудобен ---

    private fun <T> List<T>.jsonArray(map: (T) -> JSONObject): JSONArray =
        JSONArray().also { arr -> forEach { arr.put(map(it)) } }

    private fun <T> JSONObject.list(name: String, map: (JSONObject) -> T): List<T> {
        val arr = optJSONArray(name) ?: return emptyList()
        return (0 until arr.length()).map { map(arr.getJSONObject(it)) }
    }

    /** `put(name, null)` у JSONObject удаляет ключ, а нам нужен явный null. */
    private fun JSONObject.putOrNull(name: String, value: Any?) {
        put(name, value ?: JSONObject.NULL)
    }

    private fun JSONObject.stringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }

    private fun JSONObject.longOrNull(name: String): Long? =
        if (isNull(name)) null else optLong(name)

    private fun JSONObject.intOrNull(name: String): Int? =
        if (isNull(name)) null else optInt(name)
}

/** Версия схемы базы, из которой снята копия. Держать в согласии с `SproutDatabase`. */
const val SCHEMA_VERSION = 8

data class BackupSettings(
    val barrierEnabled: Boolean = false,
    val quietEnabled: Boolean = false,
    val keptShortFirst: Boolean = false,
    val keptPlanRequired: Boolean = false,
)

data class BackupData(
    val schemaVersion: Int = SCHEMA_VERSION,
    val appVersion: String = "",
    val exportedAt: Long = 0,
    val settings: BackupSettings = BackupSettings(),
    val tasks: List<Task> = emptyList(),
    val events: List<Event> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val garden: Garden? = null,
    val grownPlants: List<GrownPlant> = emptyList(),
    val blockedApps: List<BlockedApp> = emptyList(),
    val experiments: List<Experiment> = emptyList(),
) {
    /** Что показать человеку до того, как он согласится заменить свои данные. */
    val summary: String
        get() = "${tasks.size} ${plural(tasks.size, "задача", "задачи", "задач")}, " +
            "${sessions.size} ${plural(sessions.size, "заход", "захода", "заходов")}, " +
            "${events.size} ${plural(events.size, "событие", "события", "событий")}"

    private fun plural(n: Int, one: String, few: String, many: String): String {
        val mod100 = n % 100
        if (mod100 in 11..14) return many
        return when (n % 10) {
            1 -> one
            2, 3, 4 -> few
            else -> many
        }
    }
}
