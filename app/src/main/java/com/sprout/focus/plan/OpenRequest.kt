package com.sprout.focus.plan

import android.content.Intent

/**
 * С чем приложение открыли из уведомления.
 *
 * Отдельный тип, а не разбор Intent прямо в экране: намерение приходит
 * и в onCreate, и в onNewIntent, а обработать его надо один раз.
 */
data class OpenRequest(val target: String, val taskId: Long) {

    companion object {
        const val EXTRA_OPEN = "com.sprout.focus.open"
        const val EXTRA_TASK_ID = "com.sprout.focus.taskId"

        /** Открыть задачу на «Сегодня» — она станет текущей. */
        const val TARGET_TASK = "task"

        /** Открыть разговор о том, что мешает начать. */
        const val TARGET_CANT_START = "cant_start"

        fun from(intent: Intent?): OpenRequest? {
            val target = intent?.getStringExtra(EXTRA_OPEN) ?: return null
            val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
            if (taskId <= 0) return null
            return OpenRequest(target, taskId)
        }
    }
}
