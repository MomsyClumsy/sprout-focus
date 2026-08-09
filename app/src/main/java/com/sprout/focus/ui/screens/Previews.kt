package com.sprout.focus.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.sprout.focus.data.Session
import com.sprout.focus.data.Task
import com.sprout.focus.ui.theme.SproutTheme

/**
 * Превью экранов для Android Studio.
 *
 * Рисуются прямо в редакторе, без запуска приложения и без эмулятора:
 * открой файл нужного экрана или этот, нажми «Split» или «Design» справа сверху.
 *
 * Каждый экран показан в светлой и тёмной теме.
 */

private val sampleTask = Task(
    id = 1,
    title = "Дописать отчёт по проекту",
    firstStep = "Открыть файл и написать заголовок",
    ifTrigger = "завтра в 10:00",
    thenAction = "открою файл",
    isCurrent = true,
)

private val sampleTasks = listOf(
    sampleTask,
    Task(
        id = 2,
        title = "Записаться к врачу",
        firstStep = "Найти номер в справочнике",
    ),
    Task(
        id = 3,
        title = "Разобрать почту",
        firstStep = "Открыть почту и удалить рекламу",
        ifTrigger = "после обеда",
        thenAction = "открою почту",
    ),
)

private val sampleSession = Session(
    id = 1,
    taskId = 1,
    mode = Session.MODE_POMODORO,
    plannedSeconds = 20 * 60,
    startedAt = 0L,
)

// --- Сегодня ---

@Preview(name = "Сегодня · светлая", showBackground = true)
@Composable
private fun TodayLight() = SproutTheme(darkTheme = false) {
    TodayScreen(sampleTask, true, 2, 5, {}, {}, { _, _ -> }, {})
}

@Preview(name = "Сегодня · тёмная", showBackground = true)
@Composable
private fun TodayDark() = SproutTheme(darkTheme = true) {
    TodayScreen(sampleTask, true, 2, 5, {}, {}, { _, _ -> }, {})
}

@Preview(name = "Сегодня · пусто", showBackground = true)
@Composable
private fun TodayEmpty() = SproutTheme(darkTheme = false) {
    TodayScreen(null, false, 0, 0, {}, {}, { _, _ -> }, {})
}

@Preview(name = "Сегодня · задача не выбрана", showBackground = true)
@Composable
private fun TodayNothingChosen() = SproutTheme(darkTheme = false) {
    TodayScreen(null, true, 1, 3, {}, {}, { _, _ -> }, {})
}

// --- Задачи ---

@Preview(name = "Задачи · светлая", showBackground = true)
@Composable
private fun TasksLight() = SproutTheme(darkTheme = false) {
    TasksScreen(sampleTasks, {}, {}, {}, {}, { _, _, _, _, _ -> })
}

@Preview(name = "Задачи · тёмная", showBackground = true)
@Composable
private fun TasksDark() = SproutTheme(darkTheme = true) {
    TasksScreen(sampleTasks, {}, {}, {}, {}, { _, _, _, _, _ -> })
}

@Preview(name = "Задачи · пусто", showBackground = true)
@Composable
private fun TasksEmpty() = SproutTheme(darkTheme = false) {
    TasksScreen(emptyList(), {}, {}, {}, {}, { _, _, _, _, _ -> })
}

// --- Создание задачи ---

@Preview(name = "Новая задача", showBackground = true)
@Composable
private fun AddTask() = SproutTheme(darkTheme = false) {
    AddTaskScreen({}, {})
}

// --- Сессия ---

@Preview(name = "Сессия идёт", showBackground = true)
@Composable
private fun SessionRunning() = SproutTheme(darkTheme = true) {
    SessionScreen(
        session = sampleSession,
        now = 3 * 60 * 1000L,   // прошло 3 минуты
        taskTitle = sampleTask.title,
        firstStep = sampleTask.firstStep,
        onPause = {}, onResume = {}, onFinish = {}, onTimeUp = {}
    )
}

@Preview(name = "Сессия · пауза", showBackground = true)
@Composable
private fun SessionPaused() = SproutTheme(darkTheme = true) {
    SessionScreen(
        session = sampleSession.copy(pausedAt = 5 * 60 * 1000L),
        now = 6 * 60 * 1000L,
        taskTitle = sampleTask.title,
        firstStep = sampleTask.firstStep,
        onPause = {}, onResume = {}, onFinish = {}, onTimeUp = {}
    )
}

// --- Итог сессии ---

@Preview(name = "Итог сессии", showBackground = true)
@Composable
private fun SessionDone() = SproutTheme(darkTheme = false) {
    SessionDoneScreen(minutes = 20, completed = true, onSave = { _, _, _ -> })
}

@Preview(name = "Итог · остановились рано", showBackground = true)
@Composable
private fun SessionDoneEarly() = SproutTheme(darkTheme = true) {
    SessionDoneScreen(minutes = 4, completed = false, onSave = { _, _, _ -> })
}
