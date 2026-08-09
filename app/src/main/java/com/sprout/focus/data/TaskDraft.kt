package com.sprout.focus.data

/**
 * Заполненная форма новой задачи.
 *
 * Отдельный тип вместо восьми позиционных аргументов: половина из них —
 * необязательные строки, и перепутать местами «зачем это мне» и «если
 * захочется отвлечься» было бы слишком легко, а компилятор бы не заметил.
 */
data class TaskDraft(
    val title: String,
    val firstStep: String,
    val ifTrigger: String? = null,
    val thenAction: String? = null,
    val copingPlan: String? = null,
    val whyItMatters: String? = null,
    val remindMinuteOfDay: Int? = null,
    val remindDaysMask: Int = Reminder.ONE_OFF,
)
