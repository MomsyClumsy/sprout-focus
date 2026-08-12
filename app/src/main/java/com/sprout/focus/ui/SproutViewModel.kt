package com.sprout.focus.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sprout.focus.SproutApplication
import com.sprout.focus.data.Backup
import com.sprout.focus.data.BackupRepository
import com.sprout.focus.data.CantStartResolution
import com.sprout.focus.data.Garden
import com.sprout.focus.data.ExperimentRepository
import com.sprout.focus.data.ExperimentState
import com.sprout.focus.data.Experiments
import com.sprout.focus.data.GardenRepository
import com.sprout.focus.data.Gender
import com.sprout.focus.data.GuardRepository
import com.sprout.focus.data.InsightsRepository
import com.sprout.focus.data.InstalledApp
import com.sprout.focus.data.MeState
import com.sprout.focus.data.PlanRepository
import com.sprout.focus.data.PlanRule
import com.sprout.focus.data.ProfileRepository
import com.sprout.focus.data.Voice
import com.sprout.focus.data.WelcomeMode
import com.sprout.focus.data.Session
import com.sprout.focus.data.SessionRepository
import com.sprout.focus.data.Task
import com.sprout.focus.data.TaskDraft
import com.sprout.focus.data.TaskRepository
import com.sprout.focus.focusguard.FocusGuard
import com.sprout.focus.focusguard.QuietMode
import com.sprout.focus.plan.OpenRequest
import com.sprout.focus.ui.screens.BackupUiState
import com.sprout.focus.ui.screens.GuardUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SproutViewModel(
    private val app: Application,
    private val repo: TaskRepository,
    private val sessions: SessionRepository,
    private val plans: PlanRepository,
    gardenRepo: GardenRepository,
    insights: InsightsRepository,
    private val guard: GuardRepository,
    private val experiments: ExperimentRepository,
    private val backups: BackupRepository,
    private val profile: ProfileRepository,
) : ViewModel() {

    init {
        // Будильники живут в системе и теряются по причинам, на которые
        // приложение не влияет: перезагрузка, принудительная остановка,
        // экономия батареи у производителя. Ловить каждую причину дороже,
        // чем расставить всё заново при запуске — операция дешёвая,
        // а тихо пропавшее напоминание стоит доверия ко всему приложению.
        viewModelScope.launch { plans.rescheduleAll() }

        // По той же причине: тишина, включённая нами и не снятая из-за
        // потерянной сессии, иначе осталась бы навсегда — и молчащий
        // телефон человек связал бы с чем угодно, только не со Sprout
        viewModelScope.launch { sessions.syncQuiet() }

        // Неделя эксперимента могла кончиться, пока человек не заходил.
        // Итог считается по своим семи суткам и ждёт столько, сколько нужно:
        // посчитанный, но никем не увиденный результат — это ровно то,
        // ради чего затевался весь этап.
        viewModelScope.launch { experiments.finishIfOver() }
    }

    val garden: StateFlow<Garden?> = gardenRepo.garden
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val grownCount: StateFlow<Int> = gardenRepo.grownCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Экран «Я». Пересчитывается только пока на него смотрят: наблюдения
     * никому не нужны в фоне, а запросов за ними идёт сразу пять.
     */
    val me: StateFlow<MeState> = insights.state()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeState())

    val activeTasks: StateFlow<List<Task>> = repo.activeTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentTask: StateFlow<Task?> = repo.currentTask
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val activeSession: StateFlow<Session?> = sessions.activeSession
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Текущее время, тикает раз в секунду.
     * Таймер нигде не «крутится» — экран просто пересчитывает остаток
     * от момента старта, поэтому он не может разойтись с реальностью.
     */
    val now: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(500)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), System.currentTimeMillis())

    // --- задачи ---

    fun addTask(draft: TaskDraft) = viewModelScope.launch {
        val id = repo.addTask(draft)
        // Напоминание вешаем после вставки: до неё у задачи нет id,
        // а будильник адресуется именно по нему.
        if (draft.remindMinuteOfDay != null) {
            plans.savePlan(
                taskId = id,
                ifTrigger = draft.ifTrigger,
                thenAction = draft.thenAction,
                minuteOfDay = draft.remindMinuteOfDay,
                daysMask = draft.remindDaysMask,
            )
        }
    }

    /**
     * Сохранить правку задачи.
     *
     * Две записи, а не одна: текст задачи и напоминание живут в разных
     * репозиториях, потому что у второго есть будильник в системе.
     * [PlanRepository.savePlan] сам снимет старый будильник и поставит
     * новый — время могло поменяться.
     */
    fun editTask(id: Long, draft: TaskDraft) = viewModelScope.launch {
        repo.updateTask(id, draft)
        plans.savePlan(
            taskId = id,
            ifTrigger = draft.ifTrigger,
            thenAction = draft.thenAction,
            minuteOfDay = draft.remindMinuteOfDay,
            daysMask = draft.remindDaysMask,
        )
    }

    fun makeCurrent(id: Long) = viewModelScope.launch { repo.makeCurrent(id) }
    fun complete(id: Long) = viewModelScope.launch { repo.complete(id) }
    fun drop(id: Long) = viewModelScope.launch { repo.drop(id) }

    // --- «не могу начать» ---

    /** Причина записывается сразу: даже если разговор оборвётся, факт избегания сохранён. */
    fun recordCantStart(reason: String) = viewModelScope.launch {
        currentTask.value?.let { repo.recordCantStart(it.id, reason) }
    }

    fun resolveAndStart(reason: String, resolution: String, mode: String, seconds: Int) =
        viewModelScope.launch {
            currentTask.value?.let { repo.resolveCantStart(it.id, reason, resolution) }
            sessions.start(currentTask.value, mode, seconds)
        }

    /** Разбивка: маленький шаг становится текущей задачей, и сразу садимся за него. */
    fun splitAndStart(reason: String, step: String) = viewModelScope.launch {
        val parent = currentTask.value ?: return@launch
        repo.resolveCantStart(parent.id, reason, CantStartResolution.SPLIT)
        repo.addSubtask(parent.id, step)
        sessions.start(currentTask.value, Session.MODE_POMODORO, 10 * 60)
    }

    fun saveMeaningAndStart(reason: String, why: String) = viewModelScope.launch {
        val task = currentTask.value ?: return@launch
        repo.setWhyItMatters(task.id, why)
        repo.resolveCantStart(task.id, reason, CantStartResolution.FOUND_MEANING)
        sessions.start(currentTask.value, Session.MODE_POMODORO, 20 * 60)
    }

    fun dropFromCantStart(reason: String) = viewModelScope.launch {
        val task = currentTask.value ?: return@launch
        repo.resolveCantStart(task.id, reason, CantStartResolution.DROPPED)
        repo.drop(task.id)
    }

    fun postponeFromCantStart(reason: String) = viewModelScope.launch {
        currentTask.value?.let {
            repo.resolveCantStart(it.id, reason, CantStartResolution.POSTPONED)
        }
    }

    // --- барьер отвлечений ---

    /**
     * Состояние экрана настроек барьера.
     *
     * Разрешения не наблюдаются: их выдают в настройках системы, снаружи
     * приложения, и узнать об этом можно только спросив заново. Поэтому
     * экран перечитывает их при каждом возвращении — см. [refreshGuard].
     */
    private val _guard = MutableStateFlow(GuardUiState())
    val guardState: StateFlow<GuardUiState> = _guard

    init {
        viewModelScope.launch {
            guard.blockedApps.collect { list ->
                _guard.update { it.copy(blocked = list.map { app -> app.packageName }.toSet()) }
            }
        }
    }

    fun refreshGuard() = viewModelScope.launch {
        refreshGuardFlags().join()
        _guard.update {
            // Список приложений спрашиваем один раз: он меняется редко,
            // а обход установленных пакетов заметно не бесплатный
            it.copy(apps = it.apps.ifEmpty { guard.installedApps() })
        }
    }

    /**
     * Только тумблеры и разрешения, без обхода установленных пакетов.
     *
     * Нужно там, где состояние барьера лишь показывают, а не настраивают, —
     * на экране «не могу начать». Лезть в PackageManager в момент, когда
     * человек и так не может начать, незачем.
     */
    fun refreshGuardFlags() = viewModelScope.launch {
        _guard.update {
            it.copy(
                enabled = guard.enabled,
                hasUsageAccess = FocusGuard.hasUsageAccess(app),
                canDrawOverlay = FocusGuard.canDrawOverlay(app),
                quietEnabled = guard.quietEnabled,
                hasQuietAccess = QuietMode.hasPolicyAccess(app),
            )
        }
    }

    fun setGuardEnabled(value: Boolean) {
        guard.enabled = value
        _guard.update { it.copy(enabled = value) }
        refreshGuard()
    }

    fun setQuietEnabled(value: Boolean) {
        guard.quietEnabled = value
        _guard.update { it.copy(quietEnabled = value) }
        // Тумблер переключили посреди идущей сессии — реагируем сразу,
        // а не с её концом: это прямая просьба, а не настройка на потом
        viewModelScope.launch { sessions.syncQuiet() }
        refreshGuardFlags()
    }

    fun toggleBlockedApp(installed: InstalledApp, blocked: Boolean) = viewModelScope.launch {
        if (blocked) guard.add(installed) else guard.remove(installed.packageName)
    }

    // --- эксперименты над собой ---

    /**
     * Что идёт или что можно предложить.
     *
     * Как и наблюдения, считается только пока на него смотрят: выбор
     * гипотезы перебирает месяц сессий и задач, а в фоне он никому не нужен.
     */
    val experiment: StateFlow<ExperimentState> = experiments.state()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExperimentState())

    /**
     * Как эксперимент меняет поведение приложения.
     *
     * Отдельно от [experiment]: этим двум флагам нужен идущий эксперимент
     * и закреплённое с прошлых недель, а экраны «Сегодня» и «Новая задача»
     * не должны тянуть за собой пересчёт гипотез.
     *
     * Закреплённое действует так же, как эксперимент: подтвердившаяся
     * гипотеза, которая ничего не меняет, не стоила потраченной недели.
     */
    val shortSessionsOnly: StateFlow<Boolean> =
        combine(experiments.running, experiments.keptChanges) { running, kept ->
            running?.hypothesis == Experiments.SHORTER || kept.shortLengthsFirst
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Не просто «обязателен ли план», но и почему: экран называет причину вслух. */
    val planRule: StateFlow<PlanRule> =
        combine(experiments.running, experiments.keptChanges) { running, kept ->
            when {
                running?.hypothesis == Experiments.IF_THEN -> PlanRule.EXPERIMENT
                kept.planAlwaysRequired -> PlanRule.KEPT
                else -> PlanRule.NONE
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanRule.NONE)

    fun startExperiment(hypothesis: String) = viewModelScope.launch {
        experiments.start(hypothesis)
    }

    fun stopExperiment() = viewModelScope.launch { experiments.stop() }

    /** Итог прочитан. [keep] — оставить изменение насовсем. */
    fun resolveExperiment(keep: Boolean) = viewModelScope.launch {
        experiments.resolve(keep)
    }

    /** Тумблер закреплённого изменения на экране «Я». */
    fun setKeptChange(hypothesis: String, value: Boolean) =
        experiments.setKept(hypothesis, value)

    /**
     * Пришли из уведомления.
     *
     * Возвращает Job, чтобы экран дождался: пока задача не стала текущей,
     * разговор «не могу начать» откроется про другую задачу.
     */
    fun openFromReminder(target: String, taskId: Long) = viewModelScope.launch {
        repo.makeCurrent(taskId)
        if (target == OpenRequest.TARGET_CANT_START) plans.dismissed(taskId)
    }

    // --- сессии ---

    fun startSession(mode: String, plannedSeconds: Int) = viewModelScope.launch {
        sessions.start(currentTask.value, mode, plannedSeconds)
    }

    fun pauseSession() = viewModelScope.launch { sessions.pause() }

    fun resumeSession() = viewModelScope.launch {
        sessions.resume(currentTask.value?.title ?: "Фокус")
    }

    fun finishSession(
        completed: Boolean,
        rating: Int? = null,
        interruptions: Int? = null,
        note: String? = null,
    ) = viewModelScope.launch {
        sessions.finish(completed, rating, interruptions, note)
    }

    // --- знакомство и обращение ---

    private val _voice = MutableStateFlow(profile.voice)
    val voice: StateFlow<Voice> = _voice

    /**
     * Показывать ли знакомство и в каком виде.
     *
     * Обычный запуск отвечает синхронно: `metPerson` лежит в настройках,
     * и ждать нечего. [WelcomeMode.UNKNOWN] бывает только в первый запуск
     * после установки или обновления — там один запрос к базе, зато экран
     * не успевает моргнуть первой страницей и перескочить на четвёртую.
     */
    private val _welcome = MutableStateFlow(
        if (profile.metPerson) WelcomeMode.NONE else WelcomeMode.UNKNOWN
    )
    val welcome: StateFlow<WelcomeMode> = _welcome

    init {
        if (_welcome.value == WelcomeMode.UNKNOWN) viewModelScope.launch {
            // Первый запуск после обновления — не первый запуск приложения.
            // Тому, кто им уже пользуется, три страницы про замысел ни к чему:
            // ему нужен только вопрос, который появился в этой версии
            _welcome.value =
                if (repo.hasHistory()) WelcomeMode.NAME_ONLY else WelcomeMode.FULL
        }
    }

    fun saveVoice(name: String?, gender: Gender) {
        val voice = Voice(name = name?.trim()?.takeIf { it.isNotEmpty() }, gender = gender)
        profile.voice = voice
        _voice.value = voice
    }

    /**
     * Знакомство закончено — даже если человек ничего о себе не сказал.
     * Спрашивать во второй раз значило бы не услышать первый отказ.
     */
    fun finishWelcome() {
        profile.metPerson = true
        _welcome.value = WelcomeMode.NONE
    }

    // --- копия данных ---

    private val _backup = MutableStateFlow(BackupUiState())
    val backupState: StateFlow<BackupUiState> = _backup

    /**
     * Файл пишется через поток, который даёт система: приложение не знает,
     * куда его положат, и не должно знать. Ошибку здесь показываем целиком —
     * это то место, где человек имеет право знать, что копия не сделана.
     */
    fun exportBackup(uri: Uri) = viewModelScope.launch {
        _backup.update { it.copy(busy = true, message = null) }
        val result = runCatching {
            withContext(Dispatchers.IO) {
                app.contentResolver.openOutputStream(uri)?.use { backups.exportTo(it) }
                    ?: throw Backup.Broken("Не получилось открыть файл для записи")
            }
        }
        _backup.update {
            it.copy(
                busy = false,
                message = result.fold(
                    onSuccess = { "Копия сохранена" },
                    onFailure = { e -> e.message ?: "Копия не сохранилась" },
                )
            )
        }
    }

    /** Прочитать выбранный файл, ничего не меняя: решение — за человеком. */
    fun readBackup(uri: Uri) = viewModelScope.launch {
        _backup.update { it.copy(busy = true, message = null) }
        val result = runCatching {
            withContext(Dispatchers.IO) {
                app.contentResolver.openInputStream(uri)?.use { backups.read(it) }
                    ?: throw Backup.Broken("Не получилось открыть этот файл")
            }
        }
        _backup.update {
            it.copy(
                busy = false,
                pending = result.getOrNull(),
                message = result.exceptionOrNull()?.message,
            )
        }
    }

    fun confirmRestore() = viewModelScope.launch {
        val data = _backup.value.pending ?: return@launch
        _backup.update { it.copy(busy = true, pending = null) }
        val result = runCatching { backups.restore(data) }
        // Имя пришло из файла вместе с задачами, но экраны читают его отсюда:
        // без этой строки приложение обращалось бы по-старому до перезапуска
        _voice.value = profile.voice
        _backup.update {
            it.copy(
                busy = false,
                message = result.fold(
                    onSuccess = { "Данные восстановлены: ${data.summary}" },
                    onFailure = { e -> e.message ?: "Восстановить не получилось" },
                )
            )
        }
    }

    fun cancelRestore() {
        _backup.update { it.copy(pending = null) }
    }

    fun suggestedBackupName(): String = backups.suggestedName()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as SproutApplication
                SproutViewModel(
                    app, app.repository, app.sessions, app.plans,
                    app.garden, app.insights, app.guard, app.experiments, app.backups,
                    app.profile,
                )
            }
        }
    }
}
