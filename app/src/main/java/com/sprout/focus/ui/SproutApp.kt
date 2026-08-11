package com.sprout.focus.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sprout.focus.R
import com.sprout.focus.data.PlanRule
import com.sprout.focus.focusguard.FocusGuard
import com.sprout.focus.focusguard.QuietMode
import com.sprout.focus.plan.OpenRequest
import com.sprout.focus.ui.screens.BackupScreen
import com.sprout.focus.ui.screens.TaskFormScreen
import com.sprout.focus.ui.screens.CantStartScreen
import com.sprout.focus.ui.screens.ExperimentScreen
import com.sprout.focus.ui.screens.GardenScreen
import com.sprout.focus.ui.screens.GuardScreen
import com.sprout.focus.ui.screens.MeScreen
import com.sprout.focus.ui.screens.SessionDoneScreen
import com.sprout.focus.ui.screens.SessionScreen
import com.sprout.focus.ui.screens.TasksScreen
import com.sprout.focus.ui.screens.TodayScreen

private const val TODAY = "today"
private const val TASKS = "tasks"
private const val GARDEN = "garden"
private const val ME = "me"
private const val ADD = "add"
private const val EDIT = "edit"

/** Маршрут правки — с аргументом, поэтому у него два вида: шаблон и адрес. */
private const val EDIT_ROUTE = "$EDIT/{taskId}"
private const val SESSION = "session"
private const val SESSION_DONE = "session_done"
private const val CANT_START = "cant_start"
private const val GUARD = "guard"
private const val EXPERIMENT = "experiment"
private const val BACKUP = "backup"

private data class Tab(
    val route: String,
    @param:DrawableRes val icon: Int,
    val label: String
)

// Иконки контурные, монохромные — цвет берут из темы.
// Солнце, росток и диаграмма живут в одном визуальном мире.
private val tabs = listOf(
    Tab(TODAY, R.drawable.ic_tab_today, "Сегодня"),
    Tab(TASKS, R.drawable.ic_tab_tasks, "Задачи"),
    Tab(GARDEN, R.drawable.ic_tab_garden, "Сад"),
    Tab(ME, R.drawable.ic_tab_me, "Я"),
)

// Именно шаблон, а не «edit»: currentRoute у экрана с аргументом выглядит
// как «edit/{taskId}», и по короткому имени сравнение молча не сработает —
// нижняя панель останется на экране, который задуман полноэкранным
private val fullScreenRoutes = setOf(ADD, EDIT_ROUTE, SESSION, SESSION_DONE, CANT_START)

@Composable
fun SproutApp(
    opening: OpenRequest? = null,
    onOpeningHandled: () -> Unit = {},
    vm: SproutViewModel = viewModel(factory = SproutViewModel.Factory),
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: TODAY

    val tasks by vm.activeTasks.collectAsState()
    val currentTask by vm.currentTask.collectAsState()
    val session by vm.activeSession.collectAsState()
    val now by vm.now.collectAsState()
    val garden by vm.garden.collectAsState()
    val grownCount by vm.grownCount.collectAsState()
    val me by vm.me.collectAsState()
    val experiment by vm.experiment.collectAsState()
    val shortOnly by vm.shortSessionsOnly.collectAsState()
    val planRule by vm.planRule.collectAsState()

    // Дошла ли сессия до конца или её остановили раньше — нужно экрану итога
    var finishedNaturally by remember { mutableStateOf(false) }

    // Уведомления нужны для обратного отсчёта в шторке и сигнала об окончании
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }
        LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    // Пришли по напоминанию. Ждём, пока задача станет текущей: иначе
    // разговор «не могу начать» откроется про другую задачу.
    LaunchedEffect(opening) {
        val request = opening ?: return@LaunchedEffect
        vm.openFromReminder(request.target, request.taskId).join()
        if (request.target == OpenRequest.TARGET_CANT_START) {
            navController.navigate(CANT_START)
        } else {
            navController.popBackStack(TODAY, false)
        }
        onOpeningHandled()
    }

    // Сессия появилась (в том числе после перезапуска приложения) — показываем её
    LaunchedEffect(session?.id) {
        val s = session
        if (s != null && currentRoute != SESSION && currentRoute != SESSION_DONE) {
            navController.navigate(SESSION)
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute !in fullScreenRoutes) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(painterResource(tab.icon), contentDescription = null)
                            },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TODAY,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(TODAY) {
                TodayScreen(
                    currentTask = currentTask,
                    hasOtherTasks = tasks.any { !it.isCurrent },
                    stage = garden?.stage ?: 0,
                    streak = garden?.streak ?: 0,
                    onAddTask = { navController.navigate(ADD) },
                    onPickTask = { navController.navigate(TASKS) },
                    onStart = { mode, planned ->
                        finishedNaturally = false
                        vm.startSession(mode, planned)
                    },
                    onCantStart = { navController.navigate(CANT_START) },
                    onOpenTask = { navController.navigate("$EDIT/$it") },
                    shortOnly = shortOnly,
                )
            }
            composable(TASKS) {
                TasksScreen(
                    tasks = tasks,
                    onAddTask = { navController.navigate(ADD) },
                    onMakeCurrent = { vm.makeCurrent(it) },
                    onComplete = { vm.complete(it) },
                    onDrop = { vm.drop(it) },
                    onOpenTask = { navController.navigate("$EDIT/$it") },
                )
            }
            composable(GARDEN) { GardenScreen(garden, grownCount) }
            composable(ME) {
                MeScreen(
                    state = me,
                    onOpenGuard = { navController.navigate(GUARD) },
                    onOpenBackup = { navController.navigate(BACKUP) },
                    experiment = experiment,
                    onOpenExperiment = { navController.navigate(EXPERIMENT) },
                    onToggleKept = { hypothesis, value -> vm.setKeptChange(hypothesis, value) },
                )
            }

            composable(EXPERIMENT) {
                ExperimentScreen(
                    state = experiment,
                    onStart = { vm.startExperiment(it) },
                    onStop = { vm.stopExperiment() },
                    onBack = { navController.popBackStack() },
                    // Итог прочитан — экрану больше нечего показывать,
                    // и человек возвращается туда, откуда пришёл
                    onResolve = { keep ->
                        vm.resolveExperiment(keep)
                        navController.popBackStack()
                    },
                )
            }

            composable(BACKUP) {
                val backup by vm.backupState.collectAsState()
                BackupScreen(
                    state = backup,
                    onExport = { vm.exportBackup(it) },
                    onPick = { vm.readBackup(it) },
                    onConfirmRestore = { vm.confirmRestore() },
                    onCancelRestore = { vm.cancelRestore() },
                    suggestedName = vm.suggestedBackupName(),
                )
            }

            composable(GUARD) {
                val guard by vm.guardState.collectAsState()

                // Разрешения выдаются в настройках системы, снаружи приложения,
                // и уведомить нас об этом некому — спросить можно только заново.
                // Причём именно на возвращении: заход на экран случается один
                // раз, а из настроек человек приходит обратно в тот же самый,
                // и экран так и остался бы с просьбой выдать уже выданное.
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) vm.refreshGuard()
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                GuardScreen(
                    state = guard,
                    onToggle = { vm.setGuardEnabled(it) },
                    onGrantUsage = { FocusGuard.openUsageAccessSettings(context) },
                    onGrantOverlay = { FocusGuard.openOverlaySettings(context) },
                    onToggleApp = { app, checked -> vm.toggleBlockedApp(app, checked) },
                    onToggleQuiet = { vm.setQuietEnabled(it) },
                    onGrantQuiet = { QuietMode.openPolicySettings(context) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(ADD) {
                TaskFormScreen(
                    onSave = { draft ->
                        vm.addTask(draft)
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() },
                    planRule = planRule,
                )
            }

            composable(
                route = EDIT_ROUTE,
                arguments = listOf(navArgument("taskId") { type = NavType.LongType }),
            ) { entry ->
                val taskId = entry.arguments?.getLong("taskId") ?: 0L
                val task = tasks.firstOrNull { it.id == taskId }
                if (task == null) {
                    // Задачу могли завершить или бросить с другого экрана,
                    // пока эта была открыта: возвращаемся молча
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    TaskFormScreen(
                        onSave = { draft ->
                            vm.editTask(taskId, draft)
                            navController.popBackStack()
                        },
                        onCancel = { navController.popBackStack() },
                        existing = task,
                        // У уже созданной задачи план не требуем даже во время
                        // эксперимента: он про то, как заводят новые задачи,
                        // а не про право поправить опечатку в старой
                        planRule = PlanRule.NONE,
                    )
                }
            }

            composable(CANT_START) {
                // Любой исход возвращает на «Сегодня»: если запустилась сессия,
                // экран сессии откроется сам по появлению активной сессии.
                val back = { navController.popBackStack(TODAY, false); Unit }

                // Ветка «Отвлекаюсь» говорит разное в зависимости от того,
                // настроен барьер или нет. Разрешения могли выдать или отнять
                // снаружи приложения, поэтому спрашиваем заново на каждом
                // заходе — но без обхода установленных пакетов: он тут не нужен
                val guard by vm.guardState.collectAsState()
                LaunchedEffect(Unit) { vm.refreshGuardFlags() }

                CantStartScreen(
                    postponeCount = currentTask?.postponeCount ?: 0,
                    onPicked = { vm.recordCantStart(it) },
                    onStartSession = { reason, resolution, mode, seconds ->
                        finishedNaturally = false
                        vm.resolveAndStart(reason, resolution, mode, seconds)
                        back()
                    },
                    onSplit = { reason, step ->
                        finishedNaturally = false
                        vm.splitAndStart(reason, step)
                        back()
                    },
                    onFoundMeaning = { reason, why ->
                        finishedNaturally = false
                        vm.saveMeaningAndStart(reason, why)
                        back()
                    },
                    onDrop = { vm.dropFromCantStart(it); back() },
                    onPostpone = { vm.postponeFromCantStart(it); back() },
                    onClose = back,
                    barrierReady = guard.barrierReady,
                    // Настройки открываются поверх разговора, а не вместо него:
                    // «Назад» возвращает в ту же ветку, из которой ушли
                    onOpenGuard = { navController.navigate(GUARD) },
                )
            }

            composable(SESSION) {
                val s = session
                if (s == null) {
                    LaunchedEffect(Unit) { navController.popBackStack(TODAY, false) }
                } else {
                    SessionScreen(
                        session = s,
                        now = now,
                        taskTitle = currentTask?.title.orEmpty(),
                        firstStep = currentTask?.firstStep.orEmpty(),
                        onPause = { vm.pauseSession() },
                        onResume = { vm.resumeSession() },
                        onFinish = {
                            finishedNaturally = false
                            navController.navigate(SESSION_DONE)
                        },
                        onTimeUp = {
                            finishedNaturally = true
                            navController.navigate(SESSION_DONE)
                        }
                    )
                }
            }

            composable(SESSION_DONE) {
                val s = session
                val minutes = s?.elapsedSeconds(now)?.div(60) ?: 0
                SessionDoneScreen(
                    minutes = minutes,
                    completed = finishedNaturally,
                    onSave = { rating, interruptions, note ->
                        vm.finishSession(finishedNaturally, rating, interruptions, note)
                        navController.popBackStack(TODAY, false)
                    }
                )
            }
        }
    }
}
