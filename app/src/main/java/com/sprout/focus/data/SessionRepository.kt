package com.sprout.focus.data

import android.content.Context
import com.sprout.focus.focusguard.FocusGuard
import com.sprout.focus.focusguard.GuardService
import com.sprout.focus.timer.FocusAlarm
import com.sprout.focus.timer.FocusNotifications
import com.sprout.focus.widget.SproutWidget

class SessionRepository(
    private val dao: SproutDao,
    private val context: Context,
    private val garden: GardenRepository,
    private val guard: GuardRepository,
) {
    val activeSession = dao.observeActiveSession()

    suspend fun start(task: Task?, mode: String, plannedSeconds: Int) {
        // Две сессии одновременно не бывает — старую закрываем как брошенную
        dao.getActiveSession()?.let { finishInternal(it, completed = false) }

        val now = System.currentTimeMillis()
        val id = dao.insertSession(
            Session(
                taskId = task?.id,
                mode = mode,
                plannedSeconds = plannedSeconds,
                startedAt = now,
            )
        )
        dao.insertEvent(
            Event(
                type = EventType.SESSION_STARTED,
                taskId = task?.id,
                at = now,
                payload = sessionPayload(id, mode, plannedSeconds)
            )
        )

        val endsAt = if (plannedSeconds > 0) now + plannedSeconds * 1000L else null
        if (endsAt != null) FocusAlarm.schedule(context, endsAt)

        // Сторож показывает то же самое уведомление, поэтому либо он, либо мы —
        // иначе в шторке останется вторая строка после его остановки.
        if (guardWanted()) {
            GuardService.start(context, task?.title ?: "Фокус", endsAt, now)
        } else {
            FocusNotifications.showRunning(context, task?.title ?: "Фокус", endsAt, now)
        }
        SproutWidget.refresh(context)
    }

    /**
     * Стоит ли поднимать сторожа.
     *
     * Три условия сразу: барьер включён, разрешения выданы, список не пуст.
     * Сервис без списка — это foreground service, который ничего не делает,
     * и уведомление, за которым ничего не стоит.
     */
    private suspend fun guardWanted(): Boolean =
        guard.enabled && FocusGuard.ready(context) && guard.blockedPackages().isNotEmpty()

    suspend fun pause() {
        val s = dao.getActiveSession() ?: return
        if (s.isPaused) return
        val now = System.currentTimeMillis()
        dao.updateSession(s.copy(pausedAt = now))
        dao.insertEvent(Event(type = EventType.SESSION_PAUSED, taskId = s.taskId, at = now))
        FocusAlarm.cancel(context)
        // Сначала сторож: он владеет уведомлением сессии, и снимать его
        // до остановки сервиса бесполезно — система покажет снова
        GuardService.stop(context)
        FocusNotifications.cancelRunning(context)
        SproutWidget.refresh(context)
    }

    suspend fun resume(taskTitle: String) {
        val s = dao.getActiveSession() ?: return
        val pausedAt = s.pausedAt ?: return
        val now = System.currentTimeMillis()
        val addedPause = ((now - pausedAt) / 1000).toInt()
        val updated = s.copy(pausedAt = null, pausedTotal = s.pausedTotal + addedPause)
        dao.updateSession(updated)
        dao.insertEvent(Event(type = EventType.SESSION_RESUMED, taskId = s.taskId, at = now))

        val endsAt = updated.endsAt(now)
        if (endsAt != null) FocusAlarm.schedule(context, endsAt)
        if (guardWanted()) {
            GuardService.start(context, taskTitle, endsAt, updated.startedAt)
        } else {
            FocusNotifications.showRunning(context, taskTitle, endsAt, updated.startedAt)
        }
        SproutWidget.refresh(context)
    }

    /** Завершение с отметкой. [completed] — дошла до конца или остановила раньше. */
    suspend fun finish(
        completed: Boolean,
        selfRating: Int? = null,
        interruptions: Int? = null,
        stoppedNote: String? = null,
    ) {
        val s = dao.getActiveSession() ?: return
        finishInternal(s, completed, selfRating, interruptions, stoppedNote)
    }

    private suspend fun finishInternal(
        s: Session,
        completed: Boolean,
        selfRating: Int? = null,
        interruptions: Int? = null,
        stoppedNote: String? = null,
    ) {
        val now = System.currentTimeMillis()
        val actual = s.elapsedSeconds(now)

        dao.updateSession(
            s.copy(
                endedAt = now,
                pausedAt = null,
                actualSeconds = actual,
                completed = completed,
                selfRating = selfRating,
                interruptions = interruptions,
                stoppedNote = stoppedNote?.trim()?.ifBlank { null },
            )
        )
        dao.insertEvent(
            Event(
                type = EventType.SESSION_ENDED,
                taskId = s.taskId,
                at = now,
                payload = """{"sessionId":${s.id},"mode":"${s.mode}",""" +
                        """"plannedSec":${s.plannedSeconds},"actualSec":$actual,""" +
                        """"completed":$completed,"rating":${selfRating ?: "null"},""" +
                        """"interruptions":${interruptions ?: "null"}}"""
            )
        )

        // Заметку «на чём остановилась» кладём в задачу — она понадобится при возврате
        if (!stoppedNote.isNullOrBlank() && s.taskId != null) {
            dao.getTask(s.taskId)?.let { dao.updateTask(it.copy(lastStoppedAt = stoppedNote.trim())) }
        }

        garden.onSessionFinished(actual)

        FocusAlarm.cancel(context)
        GuardService.stop(context)
        FocusNotifications.cancelAll(context)
        SproutWidget.refresh(context)
    }

    private fun sessionPayload(id: Long, mode: String, planned: Int) =
        """{"sessionId":$id,"mode":"$mode","plannedSec":$planned}"""
}
