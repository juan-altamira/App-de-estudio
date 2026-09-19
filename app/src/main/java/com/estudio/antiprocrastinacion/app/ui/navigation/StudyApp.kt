package com.estudio.antiprocrastinacion.app.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.estudio.antiprocrastinacion.StudyApplication
import com.estudio.antiprocrastinacion.app.ui.common.StudyTheme
import com.estudio.antiprocrastinacion.app.ui.content.ContentScreen
import com.estudio.antiprocrastinacion.app.ui.content.ContentViewModel
import com.estudio.antiprocrastinacion.app.ui.content.EditableImportScreen
import com.estudio.antiprocrastinacion.app.ui.content.EditableImportViewModel
import com.estudio.antiprocrastinacion.app.ui.content.ImportScreen
import com.estudio.antiprocrastinacion.app.ui.content.ManualBuilderScreen
import com.estudio.antiprocrastinacion.app.ui.content.ManualBuilderViewModel
import com.estudio.antiprocrastinacion.app.ui.review.UpcomingReviewsScreen
import com.estudio.antiprocrastinacion.app.ui.review.UpcomingReviewsViewModel
import com.estudio.antiprocrastinacion.app.ui.home.HomeEffect
import com.estudio.antiprocrastinacion.app.ui.home.HomeScreen
import com.estudio.antiprocrastinacion.app.ui.home.HomeViewModel
import com.estudio.antiprocrastinacion.app.ui.home.QuickCompleteScreen
import com.estudio.antiprocrastinacion.app.ui.settings.NotificationSettingsScreen
import com.estudio.antiprocrastinacion.app.ui.settings.NotificationSettingsViewModel
import com.estudio.antiprocrastinacion.app.ui.settings.SocialGateSettingsScreen
import com.estudio.antiprocrastinacion.app.ui.settings.SocialGateSettingsViewModel
import com.estudio.antiprocrastinacion.app.notification.consumeNotificationLaunchRequest
import com.estudio.antiprocrastinacion.app.socialgate.getSocialGateGuardStatus
import com.estudio.antiprocrastinacion.app.socialgate.overlaySettingsIntent
import com.estudio.antiprocrastinacion.app.socialgate.usageAccessSettingsIntent
import com.estudio.antiprocrastinacion.app.socialgate.SocialGateActivityHost
import com.estudio.antiprocrastinacion.app.socialgate.SocialGateOverlayScreen
import com.estudio.antiprocrastinacion.app.socialgate.SocialGateRedirectBus
import com.estudio.antiprocrastinacion.app.socialgate.SocialGateServiceController
import com.estudio.antiprocrastinacion.app.ui.study.deep.DeepStudyConfigScreen
import com.estudio.antiprocrastinacion.app.ui.study.deep.DeepStudyViewModel
import com.estudio.antiprocrastinacion.app.ui.study.quick.QuickStudyEffect
import com.estudio.antiprocrastinacion.app.ui.study.quick.QuickStudyScreen
import com.estudio.antiprocrastinacion.app.ui.study.quick.QuickStudyViewModel

@Composable
fun StudyApp() {
    StudyTheme {
        val app = LocalContext.current.applicationContext as StudyApplication
        val container = app.container
        val navController = rememberNavController()
        val activity = LocalActivity.current
        val gatePrompt by SocialGateActivityHost.promptState.collectAsStateWithLifecycle()

        LaunchedEffect(activity) {
            SocialGateActivityHost.returnToPreviousApp.collect {
                activity?.moveTaskToBack(true)
            }
        }
        // Atrás no descarta un gate activo. Home/Recientes tampoco lo resuelven: el servicio
        // vuelve a traer esta misma Activity al frente mientras la deuda siga viva.
        BackHandler(enabled = gatePrompt != null) {}

        Box(modifier = Modifier.fillMaxSize()) {
            Surface(modifier = Modifier.fillMaxSize()) {
            // ── Global observer for social gate redirections ──
            // This runs regardless of which composable is active (BOOT, HOME, STUDY, etc.)
            val redirectSessionId by SocialGateRedirectBus.pendingSessionId.collectAsStateWithLifecycle()
            LaunchedEffect(redirectSessionId) {
                val sessionId = redirectSessionId ?: return@LaunchedEffect
                android.util.Log.d("SocialGate", "StudyApp: redirecting to session=$sessionId currentRoute=${navController.currentDestination?.route}")
                SocialGateRedirectBus.consume()
                navController.navigate(NavRoutes.study(sessionId)) {
                    popUpTo(NavRoutes.HOME) { inclusive = false }
                    launchSingleTop = true
                }
                android.util.Log.d("SocialGate", "StudyApp: navigation completed to study/$sessionId")
            }

                NavHost(
                    navController = navController,
                    startDestination = NavRoutes.BOOT,
                    modifier = Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background),
                ) {
                composable(NavRoutes.BOOT) {
                    val viewModel: AppLaunchViewModel = viewModel(factory = container.appLaunchViewModelFactory())
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    val activity = LocalActivity.current
                    val notificationLaunchRequest = remember(activity) { activity?.intent?.consumeNotificationLaunchRequest() }
                    LaunchedEffect(notificationLaunchRequest) {
                        viewModel.refresh(notificationLaunchRequest)
                    }
                    LaunchedEffect(state.isLoading, state.target) {
                        if (state.isLoading) return@LaunchedEffect
                        when (val target = state.target) {
                            AppLaunchTarget.Home ->
                                navController.navigate(NavRoutes.HOME) {
                                    popUpTo(NavRoutes.BOOT) { inclusive = true }
                                    launchSingleTop = true
                                }

                            is AppLaunchTarget.Study ->
                                navController.navigate(NavRoutes.study(target.sessionId)) {
                                    popUpTo(NavRoutes.BOOT) { inclusive = true }
                                    launchSingleTop = true
                                }

                            null -> Unit
                        }
                    }
                }
                composable(NavRoutes.HOME) {
                    val viewModel: HomeViewModel = viewModel(factory = container.homeViewModelFactory())
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    val lifecycleOwner = LocalLifecycleOwner.current
                    LaunchedEffect(Unit) {
                        viewModel.refresh()
                    }
                    LaunchedEffect(Unit) {
                        viewModel.effects.collect { effect ->
                            when (effect) {
                                is HomeEffect.NavigateToStudy -> navController.navigate(NavRoutes.study(effect.sessionId))
                                HomeEffect.NavigateToQuickComplete -> navController.navigate(NavRoutes.QUICK_COMPLETE) { launchSingleTop = true }
                                HomeEffect.NavigateToContent -> navController.navigate(NavRoutes.CONTENT)
                                HomeEffect.NavigateToImport -> navController.navigate(NavRoutes.IMPORT)
                                HomeEffect.NavigateToUpcomingReviews -> navController.navigate(NavRoutes.UPCOMING_REVIEWS)
                                HomeEffect.NavigateToNotificationSettings -> navController.navigate(NavRoutes.NOTIFICATION_SETTINGS)
                                HomeEffect.NavigateToSocialGateSettings -> navController.navigate(NavRoutes.SOCIAL_GATE_SETTINGS)
                                HomeEffect.NavigateToDeep -> navController.navigate(NavRoutes.DEEP)
                                HomeEffect.NavigateToDrain -> navController.navigate(NavRoutes.DRAIN)
                            }
                        }
                    }
                    DisposableEffect(lifecycleOwner, viewModel) {
                        val observer =
                            LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_RESUME) {
                                    viewModel.refresh()
                                }
                            }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }
                    HomeScreen(
                        state = state,
                        onStartQuick = viewModel::startQuick,
                        onOpenContent = viewModel::openContent,
                        onOpenImport = viewModel::openImport,
                        onOpenUpcomingReviews = viewModel::openUpcomingReviews,
                        onOpenNotificationSettings = viewModel::openNotificationSettings,
                        onOpenSocialGateSettings = viewModel::openSocialGateSettings,
                        onOpenDeep = viewModel::openDeep,
                        onOpenDrain = viewModel::openDrain,
                        onAcknowledgeRecoveryNotice = viewModel::acknowledgeRecoveryNotice,
                    )
                }
                composable(NavRoutes.QUICK_COMPLETE) {
                    QuickCompleteScreen(
                        onBack = { navController.popBackStack() },
                        onContinue = { navController.popBackStack() },
                    )
                }
                composable(NavRoutes.CONTENT) {
                    val viewModel: ContentViewModel = viewModel(factory = container.contentViewModelFactory())
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    val snapshotExportLauncher =
                        rememberLauncherForActivityResult(CreateDocument("application/json")) { uri: Uri? ->
                            uri?.let(viewModel::exportSnapshot)
                        }
                    ContentScreen(
                        state = state,
                        onBack = { navController.popBackStack() },
                        onExportSnapshotClick = { snapshotExportLauncher.launch("study-sync-snapshot.json") },
                        onRefresh = viewModel::refresh,
                        onArchiveFilterChange = viewModel::updateArchiveFilter,
                        onConfirmReset = viewModel::confirmReset,
                        onDismissReset = viewModel::dismissReset,
                        onRequestCourseReset = viewModel::requestCourseReset,
                        onRequestUnitReset = viewModel::requestUnitReset,
                        onToggleCourseArchive = viewModel::toggleCourseArchive,
                        onToggleUnitArchive = viewModel::toggleUnitArchive,
                        onToggleItemArchive = viewModel::toggleItemArchive,
                        onOpenItemEdit = viewModel::openItemEdit,
                        onDismissItemEdit = viewModel::dismissItemEdit,
                        onConfirmItemEdit = viewModel::confirmItemEdit,
                        onPendingItemEditStemChange = viewModel::updatePendingItemEditStem,
                        onPendingItemEditCorrectAnswerChange = viewModel::updatePendingItemEditCorrectAnswer,
                        onPendingItemEditOptionsTextChange = viewModel::updatePendingItemEditOptionsText,
                    )
                }
                composable(NavRoutes.IMPORT) {
                    val viewModel: ContentViewModel = viewModel(factory = container.contentViewModelFactory())
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    val launcher =
                        rememberLauncherForActivityResult(OpenDocument()) { uri: Uri? ->
                            uri?.let(viewModel::importUri)
                        }
                    val snapshotImportLauncher =
                        rememberLauncherForActivityResult(OpenDocument()) { uri: Uri? ->
                            uri?.let(viewModel::importSnapshot)
                        }
                    val snapshotExportLauncher =
                        rememberLauncherForActivityResult(CreateDocument("application/json")) { uri: Uri? ->
                            uri?.let(viewModel::exportSnapshot)
                        }
                    val userStateImportLauncher =
                        rememberLauncherForActivityResult(OpenDocument()) { uri: Uri? ->
                            uri?.let(viewModel::importUserStatePackage)
                        }
                    val userStateExportLauncher =
                        rememberLauncherForActivityResult(CreateDocument("application/json")) { uri: Uri? ->
                            uri?.let(viewModel::exportUserStatePackage)
                        }
                    ImportScreen(
                        state = state,
                        onBack = { navController.popBackStack() },
                        onImportClick = { launcher.launch(arrayOf("application/json")) },
                        onImportSnapshotClick = { snapshotImportLauncher.launch(arrayOf("application/json")) },
                        onExportSnapshotClick = { snapshotExportLauncher.launch("study-sync-snapshot.json") },
                        onImportUserStateClick = { userStateImportLauncher.launch(arrayOf("application/json")) },
                        onExportUserStateClick = { userStateExportLauncher.launch("study-user-state-package.json") },
                        onOpenEditableImport = { navController.navigate(NavRoutes.EDITABLE_IMPORT) },
                        onOpenManualBuilder = { navController.navigate(NavRoutes.MANUAL_BUILDER) },
                        onOpenManualImport = viewModel::openManualImport,
                        onCloseManualImport = viewModel::closeManualImport,
                        onManualImportTextChange = viewModel::updateManualImportText,
                        onClearManualImportText = viewModel::clearManualImportText,
                        onRequestManualImportConfirmation = viewModel::requestManualImportConfirmation,
                        onDismissManualImportConfirmation = viewModel::dismissManualImportConfirmation,
                        onConfirmManualImport = viewModel::confirmManualImport,
                    )
                }
                composable(NavRoutes.EDITABLE_IMPORT) {
                    val viewModel: EditableImportViewModel = viewModel(factory = container.editableImportViewModelFactory())
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    EditableImportScreen(
                        state = state,
                        onBack = { navController.popBackStack() },
                        onBackToSource = viewModel::backToSource,
                        onSourceTextChange = viewModel::updateSourceText,
                        onAnalyzeSource = viewModel::analyzeSource,
                        onPrepareDraft = viewModel::prepareEditedDraft,
                        onClearSource = viewModel::clearSource,
                        onConfirmImport = viewModel::confirmImport,
                        onCourseTitleChange = viewModel::updateCourseTitle,
                        onUnitTitleChange = viewModel::updateUnitTitle,
                        onQuestionFormatChange = viewModel::updateQuestionFormat,
                        onQuestionStemChange = viewModel::updateQuestionStem,
                        onQuestionCorrectAnswerChange = viewModel::updateQuestionCorrectAnswer,
                        onQuestionFeedbackChange = viewModel::updateQuestionFeedback,
                        onQuestionCorrectedConfusionChange = viewModel::updateQuestionCorrectedConfusion,
                        onOptionTextChange = viewModel::updateOptionText,
                        onMarkOptionCorrect = viewModel::markOptionCorrect,
                        onAddOption = viewModel::addQuestionOption,
                        onRemoveOption = viewModel::removeQuestionOption,
                        onAddQuestion = viewModel::addQuestion,
                        onRemoveQuestion = viewModel::removeQuestion,
                    )
                }
                composable(NavRoutes.UPCOMING_REVIEWS) {
                    val viewModel: UpcomingReviewsViewModel = viewModel(factory = container.upcomingReviewsViewModelFactory())
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    UpcomingReviewsScreen(
                        state = state,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(NavRoutes.MANUAL_BUILDER) {
                    val viewModel: ManualBuilderViewModel = viewModel(factory = container.manualBuilderViewModelFactory())
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    ManualBuilderScreen(
                        state = state,
                        viewModel = viewModel,
                        onExit = { navController.popBackStack() },
                    )
                }
                composable(NavRoutes.NOTIFICATION_SETTINGS) {
                    val viewModel: NotificationSettingsViewModel = viewModel(factory = container.notificationSettingsViewModelFactory())
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    val context = LocalContext.current
                    val lifecycleOwner = LocalLifecycleOwner.current
                    var notificationPermissionGranted by remember {
                        mutableStateOf(
                            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
                        )
                    }
                    DisposableEffect(lifecycleOwner, context) {
                        val observer =
                            LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_RESUME) {
                                    notificationPermissionGranted =
                                        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
                                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                                }
                            }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }
                    val permissionLauncher =
                        rememberLauncherForActivityResult(RequestPermission()) { granted ->
                            notificationPermissionGranted = granted
                            if (granted) {
                                viewModel.updateNotificationsEnabled(true)
                            }
                        }
                    NotificationSettingsScreen(
                        state = state,
                        notificationPermissionGranted = notificationPermissionGranted,
                        onBack = { navController.popBackStack() },
                        onRequestNotificationPermission = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.updateNotificationsEnabled(true)
                            }
                        },
                        onNotificationsEnabledChange = viewModel::updateNotificationsEnabled,
                        onNotificationsPerDayChange = viewModel::updateNotificationsPerDay,
                        onNotificationWindowStartMinutesChange = viewModel::updateNotificationWindowStartMinutes,
                        onNotificationWindowEndMinutesChange = viewModel::updateNotificationWindowEndMinutes,
                    )
                }
                composable(NavRoutes.SOCIAL_GATE_SETTINGS) {
                    val viewModel: SocialGateSettingsViewModel = viewModel(factory = container.socialGateSettingsViewModelFactory())
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    val context = LocalContext.current
                    val lifecycleOwner = LocalLifecycleOwner.current
                    var guardStatus by remember { mutableStateOf(getSocialGateGuardStatus(context)) }
                    LaunchedEffect(Unit) {
                        viewModel.refreshStatus()
                        SocialGateServiceController.ensureRunning(context)
                    }
                    DisposableEffect(lifecycleOwner, context) {
                        val observer =
                            LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_RESUME) {
                                    SocialGateServiceController.ensureRunning(context)
                                    guardStatus = getSocialGateGuardStatus(context)
                                    viewModel.refreshStatus()
                                }
                            }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }
                    SocialGateSettingsScreen(
                        state = state,
                        guardStatus = guardStatus,
                        onBack = { navController.popBackStack() },
                        onOpenUsageAccessSettings = { context.startActivity(usageAccessSettingsIntent()) },
                        onOpenOverlaySettings = { context.startActivity(overlaySettingsIntent(context)) },
                        onMaxTriggersPerDayChange = viewModel::updateMaxTriggersPerDay,
                        onWindowStartMinutesChange = viewModel::updateWindowStartMinutes,
                        onWindowEndMinutesChange = viewModel::updateWindowEndMinutes,
                    )
                }
                composable(NavRoutes.DEEP) {
                    val viewModel: DeepStudyViewModel = viewModel(factory = container.deepStudyViewModelFactory())
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    LaunchedEffect(Unit) {
                        viewModel.effects.collect { sessionId ->
                            navController.navigate(NavRoutes.study(sessionId))
                        }
                    }
                    DeepStudyConfigScreen(
                        title = "Modo profundo",
                        mode = com.estudio.antiprocrastinacion.app.model.content.SessionMode.DEEP,
                        state = state,
                        onBack = { navController.popBackStack() },
                        onRefresh = viewModel::refresh,
                        onStart = viewModel::startDeep,
                    )
                }
                composable(NavRoutes.DRAIN) {
                    val viewModel: DeepStudyViewModel = viewModel(factory = container.deepStudyViewModelFactory())
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    LaunchedEffect(Unit) {
                        viewModel.effects.collect { sessionId ->
                            navController.navigate(NavRoutes.study(sessionId))
                        }
                    }
                    DeepStudyConfigScreen(
                        title = "Vaciar",
                        mode = com.estudio.antiprocrastinacion.app.model.content.SessionMode.DRAIN,
                        state = state,
                        onBack = { navController.popBackStack() },
                        onRefresh = viewModel::refresh,
                        onStart = viewModel::startDrain,
                    )
                }
                composable(
                    route = "${NavRoutes.STUDY}/{sessionId}",
                    arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
                ) {
                    val viewModel: QuickStudyViewModel = viewModel(factory = container.quickStudyViewModelFactory())
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    val sessionId = it.arguments?.getString("sessionId").orEmpty()
                    val lifecycleOwner = LocalLifecycleOwner.current
                    // Reload on every ON_RESUME (the registry replays it on first subscribe, covering the
                    // initial load too). The social gate can consume or advance this session while the app
                    // is in background; resuming with a stale prompt must re-sync or exit, never crash.
                    DisposableEffect(lifecycleOwner, viewModel, sessionId) {
                        val observer =
                            LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_RESUME) {
                                    viewModel.load(sessionId)
                                }
                            }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }
                    LaunchedEffect(Unit) {
                        viewModel.effects.collect { effect ->
                            when (effect) {
                                QuickStudyEffect.ExitToHome -> navController.exitStudyToHome()
                                is QuickStudyEffect.ExitToModeMenu ->
                                    navController.exitStudyToMode(
                                        when (effect.mode) {
                                            com.estudio.antiprocrastinacion.app.model.content.SessionMode.DEEP -> NavRoutes.DEEP
                                            com.estudio.antiprocrastinacion.app.model.content.SessionMode.DRAIN -> NavRoutes.DRAIN
                                            com.estudio.antiprocrastinacion.app.model.content.SessionMode.QUICK -> NavRoutes.HOME
                                        },
                                    )
                            }
                        }
                    }
                    QuickStudyScreen(
                        state = state,
                        onSubmitAnswer = viewModel::submitAnswer,
                        onRevealAndSelfAssess = viewModel::submitSelfAssessment,
                        onRevealAnswer = viewModel::revealAnswer,
                        onBackPressed = viewModel::onBackPressed,
                        onTimeout = viewModel::handleTimeout,
                        onContinueAfterFeedback = viewModel::continueAfterFeedback,
                        onRetryCurrentPrompt = viewModel::retryCurrentPrompt,
                        onRequestTerminateSession = viewModel::requestTerminateSession,
                        onConfirmTerminateSession = viewModel::confirmTerminateSession,
                        onDismissTerminateSessionConfirmation = viewModel::dismissTerminateSessionConfirmation,
                    )
                }
                }
            }

            gatePrompt?.let { state ->
                SocialGateOverlayScreen(
                    state = state,
                    onSubmitAnswer = SocialGateActivityHost::submitAnswer,
                    onRevealAnswer = SocialGateActivityHost::revealAnswer,
                    onContinueAfterFeedback = SocialGateActivityHost::continueAfterFeedback,
                    onUseEscape = SocialGateActivityHost::useEscape,
                )
            }
        }
    }
}

private fun NavHostController.exitStudyToHome() {
    if (!popBackStack(NavRoutes.HOME, false)) {
        navigate(NavRoutes.HOME) {
            popUpTo(graph.id) { inclusive = false }
            launchSingleTop = true
        }
    }
}

private fun NavHostController.exitStudyToMode(route: String) {
    if (!popBackStack(route, false)) {
        navigate(route) {
            popUpTo(graph.id) { inclusive = false }
            launchSingleTop = true
        }
    }
}
