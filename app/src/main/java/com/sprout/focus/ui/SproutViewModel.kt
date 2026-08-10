package com.sprout.focus.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sprout.focus.SproutApplication
import com.sprout.focus.data.CantStartResolution
import com.sprout.focus.data.Garden
import com.sprout.focus.data.GardenRepository
import com.sprout.focus.data.GuardRepository
import com.sprout.focus.data.InsightsRepository
import com.sprout.focus.data.InstalledApp
import com.sprout.focus.data.MeState
import com.sprout.focus.data.PlanRepository
import com.sprout.focus.data.Session
import com.sprout.focus.data.SessionRepository
import com.sprout.focus.data.Task
import com.sprout.focus.data.TaskDraft
import com.sprout.focus.data.TaskRepository
import com.sprout.focus.focusguard.FocusGuard
import com.sprout.focus.focusguard.QuietMode
import com.sprout.focus.plan.OpenRequest
import com.sprout.focus.ui.screens.GuardUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SproutViewModel(
    private val app: Application,
    private val repo: TaskRepository,
    private val sessions: SessionRepository,
    private val plans: PlanRepository,
    gardenRepo: GardenRepository,
    insights: InsightsRepository,
    private val guard: GuardRepository,
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

    // --- планы «если — то» ---

    fun savePlan(
        taskId: Long,
        ifTrigger: String?,
        thenAction: String?,
        minuteOfDay: Int?,
        daysMask: Int,
    ) = viewModelScope.launch {
        plans.savePlan(taskId, ifTrigger, thenAction, minuteOfDay, daysMask)
    }

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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as SproutApplication
                SproutViewModel(
                    app, app.repository, app.sessions, app.plans,
                    app.garden, app.insights, app.guard,
                )
            }
        }
    }
}
