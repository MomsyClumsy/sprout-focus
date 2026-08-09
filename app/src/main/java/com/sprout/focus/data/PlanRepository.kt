package com.sprout.focus.data

import android.content.Context
import com.sprout.focus.plan.PlanAlarm

/**
 * Планы «если — то» и напоминания к ним.
 *
 * Отделено от [TaskRepository], потому что здесь появляется вторая
 * зависимость — будильники системы. Правило простое: состояние
 * напоминания в базе и будильник в системе меняются только здесь
 * и только вместе.
 */
class PlanRepository(
    private val dao: SproutDao,
    private val context: Context,
) {

    val remindingTasks = dao.observeTasksWithReminder()

    /**
     * Сохранить план и время напоминания.
     *
     * [minuteOfDay] = null означает «план записан, но телефон молчит»:
     * так тоже можно, план сам по себе работает, просто слабее.
     */
    suspend fun savePlan(
        taskId: Long,
        ifTrigger: String?,
        thenAction: String?,
        minuteOfDay: Int?,
        daysMask: Int,
    ) {
        val task = dao.getTask(taskId) ?: return
        val nextAt = minuteOfDay?.let { Reminder.nextAt(it, daysMask) }

        dao.updateTask(
            task.copy(
                ifTrigger = ifTrigger?.trim()?.ifBlank { null },
                thenAction = thenAction?.trim()?.ifBlank { null },
                remindMinuteOfDay = minuteOfDay,
                remindDaysMask = daysMask,
                remindNextAt = nextAt,
            )
        )

        // Старый будильник снимаем всегда: время могло поменяться,
        // а два будильника на одну задачу дадут два уведомления.
        PlanAlarm.cancel(context, taskId)

        if (minuteOfDay != null && nextAt != null) {
            PlanAlarm.schedule(context, taskId, nextAt)
            dao.insertEvent(
                Event(
                    type = EventType.REMINDER_SET,
                    taskId = taskId,
                    payload = """{"at":"${Reminder.formatTime(minuteOfDay)}","daysMask":$daysMask}"""
                )
            )
        } else if (task.remindNextAt != null) {
            dao.insertEvent(Event(type = EventType.REMINDER_CLEARED, taskId = taskId))
        }
    }

    /**
     * Напоминание сработало.
     *
     * Событие пишется до показа уведомления: если что-то сорвётся дальше,
     * факт «время пришло» всё равно останется в базе. Разница между
     * «напомнили» и «это помогло» — главная цифра, которую даст этот этап.
     */
    suspend fun onFired(taskId: Long): Task? {
        val task = dao.getTask(taskId) ?: return null
        val minute = task.remindMinuteOfDay ?: return null

        // Задачу могли закрыть или бросить, пока будильник ждал.
        if (task.status != Task.STATUS_ACTIVE) {
            PlanAlarm.cancel(context, taskId)
            dao.updateTask(task.copy(remindNextAt = null))
            return null
        }

        val firedAt = System.currentTimeMillis()
        dao.insertEvent(
            Event(
                type = EventType.REMINDER_FIRED,
                taskId = taskId,
                at = firedAt,
                payload = """{"at":"${Reminder.formatTime(minute)}","daysMask":${task.remindDaysMask}}"""
            )
        )

        val next = Reminder.afterFiring(minute, task.remindDaysMask, firedAt)
        dao.updateTask(task.copy(remindNextAt = next))
        if (next != null) PlanAlarm.schedule(context, taskId, next)

        return task
    }

    /** Нажали «Начать» — напоминание довело до работы. */
    suspend fun accepted(taskId: Long) {
        dao.insertEvent(Event(type = EventType.REMINDER_ACCEPTED, taskId = taskId))
    }

    /**
     * Нажали «Не могу».
     *
     * Это не провал, а ответ, и его важно отличать от молчания: молчание
     * означает, что уведомление вообще прошло мимо, а здесь человек
     * его увидел и пришёл разбираться.
     */
    suspend fun dismissed(taskId: Long) {
        dao.insertEvent(Event(type = EventType.REMINDER_DISMISSED, taskId = taskId))
    }

    /**
     * Расставить будильники заново — после перезагрузки или переустановки.
     *
     * Момент мог пройти, пока телефон был выключен. Показывать напоминание
     * задним числом бессмысленно: приём работает привязкой к моменту,
     * а не текстом. Поэтому пропущенное переносится на следующее
     * подходящее время, включая разовое — тихо забыть то, о чём человек
     * просил напомнить, хуже, чем напомнить на день позже.
     */
    suspend fun rescheduleAll() {
        val now = System.currentTimeMillis()
        for (task in dao.tasksWithReminder()) {
            val minute = task.remindMinuteOfDay ?: continue
            val at =
                if ((task.remindNextAt ?: 0L) > now) task.remindNextAt
                else Reminder.nextAt(minute, task.remindDaysMask, now)

            if (at == null) {
                dao.updateTask(task.copy(remindNextAt = null))
                continue
            }
            if (at != task.remindNextAt) dao.updateTask(task.copy(remindNextAt = at))
            PlanAlarm.schedule(context, task.id, at)
        }
    }
}
