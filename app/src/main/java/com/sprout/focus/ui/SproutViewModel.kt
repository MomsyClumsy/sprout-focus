package com.sprout.focus.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sprout.focus.SproutApplication
import com.sprout.focus.data.CantStartResolution
import com.sprout.focus.data.Garden
import com.sprout.focus.data.GardenRepository
import com.sprout.focus.data.InsightsRepository
import com.sprout.focus.data.MeState
import com.sprout.focus.data.PlanRepository
import com.sprout.focus.data.Session
import com.sprout.focus.data.SessionRepository
import com.sprout.focus.data.Task
import com.sprout.focus.data.TaskDraft
import com.sprout.focus.data.TaskRepository
import com.sprout.focus.plan.OpenRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SproutViewModel(
    private val repo: TaskRepository,
    private val sessions: SessionRepository,
    private val plans: PlanRepository,
    gardenRepo: GardenRepository,
    insights: InsightsRepository,
) : ViewModel() {

    init {
        // Будильники живут в системе и теряются по причинам, на которые
        // приложение не влияет: перезагрузка, принудительная остановка,
        // экономия батареи у производителя. Ловить каждую причину дороже,
        // чем расставить всё заново при запуске — операция дешёвая,
        // а тихо пропавшее напоминание стоит доверия ко всему приложению.
        viewModelScope.launch { plans.rescheduleAll() }
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
                    app.repository, app.sessions, app.plans, app.garden, app.insights
                )
            }
        }
    }
}
