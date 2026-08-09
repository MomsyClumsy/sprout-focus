package com.sprout.focus.data

import android.content.Context
import com.sprout.focus.timer.FocusAlarm
import com.sprout.focus.timer.FocusNotifications

class SessionRepository(
    private val dao: SproutDao,
    private val context: Context,
    private val garden: GardenRepository,
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
        FocusNotifications.showRunning(context, task?.title ?: "Фокус", endsAt, now)
    }

    suspend fun pause() {
        val s = dao.getActiveSession() ?: return
        if (s.isPaused) return
        val now = System.currentTimeMillis()
        dao.updateSession(s.copy(pausedAt = now))
        dao.insertEvent(Event(type = EventType.SESSION_PAUSED, taskId = s.taskId, at = now))
        FocusAlarm.cancel(context)
        FocusNotifications.cancelRunning(context)
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
        FocusNotifications.showRunning(context, taskTitle, endsAt, updated.startedAt)
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
        FocusNotifications.cancelAll(context)
    }

    private fun sessionPayload(id: Long, mode: String, planned: Int) =
        """{"sessionId":$id,"mode":"$mode","plannedSec":$planned}"""
}
