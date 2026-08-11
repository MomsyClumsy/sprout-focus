package com.sprout.focus.data

import android.content.Context
import com.sprout.focus.widget.SproutWidget

/**
 * [context] нужен ровно за одним: попросить виджет перерисоваться.
 * Виджет показывает текущую задачу, а меняется она здесь.
 */
class TaskRepository(private val dao: SproutDao, private val context: Context) {

    val activeTasks = dao.observeActiveTasks()
    val currentTask = dao.observeCurrentTask()

    /** Возвращает id созданной задачи — он нужен, чтобы повесить напоминание. */
    suspend fun addTask(draft: TaskDraft): Long {
        val task = Task(
            title = draft.title.trim(),
            firstStep = draft.firstStep.trim(),
            ifTrigger = draft.ifTrigger?.trim()?.ifBlank { null },
            thenAction = draft.thenAction?.trim()?.ifBlank { null },
            copingPlan = draft.copingPlan?.trim()?.ifBlank { null },
            whyItMatters = draft.whyItMatters?.trim()?.ifBlank { null },
        )
        val id = dao.insertTask(task)
        dao.insertEvent(
            Event(
                type = EventType.TASK_CREATED,
                taskId = id,
                payload = """{"hasPlan":${task.hasPlan},"titleLen":${task.title.length}}"""
            )
        )
        // Первая задача сразу становится текущей — иначе главный экран
        // останется пустым и придётся делать лишний шаг.
        if (dao.countCurrent() == 0) makeCurrent(id)
        return id
    }

    /**
     * Правка задачи.
     *
     * Меняется всё, кроме плана и напоминания: те живут в [PlanRepository],
     * потому что вместе с ними меняется будильник в системе. Здесь только
     * текст — и он же виден на виджете, поэтому виджет просим перерисоваться.
     *
     * Событие пишем без содержимого правки: что человек переписал название,
     * знать полезно, а хранить обе версии его формулировок — уже слежка.
     *
     * Полей, которых нет в форме, правка не касается. «Зачем это мне»
     * человек отвечает в ветке «Не хочу», и его ответ не должен исчезать
     * оттого, что он потом зашёл поправить название задачи.
     */
    suspend fun updateTask(id: Long, draft: TaskDraft) {
        val task = dao.getTask(id) ?: return
        dao.updateTask(
            task.copy(
                title = draft.title.trim(),
                firstStep = draft.firstStep.trim(),
                copingPlan = draft.copingPlan?.trim()?.ifBlank { null } ?: task.copingPlan,
                whyItMatters = draft.whyItMatters?.trim()?.ifBlank { null } ?: task.whyItMatters,
            )
        )
        dao.insertEvent(Event(type = EventType.TASK_EDITED, taskId = id))
        SproutWidget.refresh(context)
    }

    suspend fun makeCurrent(id: Long) {
        dao.makeCurrent(id)
        dao.insertEvent(Event(type = EventType.TASK_SELECTED, taskId = id))
        SproutWidget.refresh(context)
    }

    suspend fun complete(id: Long) {
        val at = System.currentTimeMillis()
        dao.setStatus(id, Task.STATUS_DONE, at)
        dao.insertEvent(Event(type = EventType.TASK_COMPLETED, taskId = id, at = at))
        promoteNextIfNeeded()
    }

    /** Отказ от задачи — тоже результат, а не провал. Пишем как отдельное событие. */
    suspend fun drop(id: Long) {
        val at = System.currentTimeMillis()
        dao.setStatus(id, Task.STATUS_DROPPED, at)
        dao.insertEvent(Event(type = EventType.TASK_DROPPED, taskId = id, at = at))
        promoteNextIfNeeded()
    }

    /**
     * Пользователь нажал «Не могу начать» и назвал причину.
     *
     * Пишем сразу, не дожидаясь, чем закончится разговор: даже если человек
     * закроет приложение, сам факт избегания и его причина уже сохранены.
     */
    suspend fun recordCantStart(taskId: Long, reason: String) {
        val task = dao.getTask(taskId) ?: return
        dao.updateTask(task.copy(postponeCount = task.postponeCount + 1))
        dao.insertEvent(
            Event(
                type = EventType.TASK_POSTPONED,
                taskId = taskId,
                payload = """{"reason":"$reason","postponeCount":${task.postponeCount + 1}}"""
            )
        )
    }

    /** Чем закончился разговор — чтобы понимать, какие приёмы срабатывают. */
    suspend fun resolveCantStart(taskId: Long, reason: String, resolution: String) {
        dao.insertEvent(
            Event(
                type = EventType.CANT_START_RESOLVED,
                taskId = taskId,
                payload = """{"reason":"$reason","resolvedWith":"$resolution"}"""
            )
        )
    }

    /** Разбивка: маленький кусок становится отдельной задачей и текущей. */
    suspend fun addSubtask(parentId: Long, step: String) {
        val text = step.trim()
        if (text.isEmpty()) return
        val id = dao.insertTask(
            Task(
                title = text,
                firstStep = text,
                parentTaskId = parentId,
            )
        )
        dao.insertEvent(
            Event(
                type = EventType.TASK_CREATED,
                taskId = id,
                payload = """{"fromSplit":true,"parentId":$parentId}"""
            )
        )
        makeCurrent(id)
    }

    suspend fun setWhyItMatters(taskId: Long, why: String) {
        val task = dao.getTask(taskId) ?: return
        dao.updateTask(task.copy(whyItMatters = why.trim().ifBlank { null }))
    }

    private suspend fun promoteNextIfNeeded() {
        if (dao.countCurrent() > 0) return
        // Берём самую свежую из оставшихся, чтобы экран «Сегодня» не опустел
        val next = dao.newestActiveTask()
        if (next == null) {
            // Задач не осталось — виджету тоже надо об этом узнать,
            // иначе он продолжит предлагать начать завершённую задачу
            SproutWidget.refresh(context)
            return
        }
        makeCurrent(next.id)
    }
}
