package com.estudio.antiprocrastinacion.app.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import com.estudio.antiprocrastinacion.app.data.importing.AuthoringDraftCompiler
import com.estudio.antiprocrastinacion.app.data.importing.AuthoringDraftValidator
import com.estudio.antiprocrastinacion.app.data.importing.DefaultImportValidator
import com.estudio.antiprocrastinacion.app.data.importing.DefaultImportPreparationService
import com.estudio.antiprocrastinacion.app.data.importing.EditableImportPreparationService
import com.estudio.antiprocrastinacion.app.data.importing.NaturalTextDraftExtractor
import com.estudio.antiprocrastinacion.app.data.importing.ReviewedEditableDraftValidator
import com.estudio.antiprocrastinacion.app.data.importing.ReviewedQuestionBankCompiler
import com.estudio.antiprocrastinacion.app.data.local.db.CourseRecoveryStore
import com.estudio.antiprocrastinacion.app.data.local.db.DefaultSettingsRepository
import com.estudio.antiprocrastinacion.app.data.local.db.LocalContentRepository
import com.estudio.antiprocrastinacion.app.data.local.db.RecoveryNoticeStore
import com.estudio.antiprocrastinacion.app.data.local.db.LocalEventRepository
import com.estudio.antiprocrastinacion.app.data.local.db.LocalProgressRepository
import com.estudio.antiprocrastinacion.app.data.local.db.LocalSessionRepository
import com.estudio.antiprocrastinacion.app.data.local.db.LocalSocialGateRepository
import com.estudio.antiprocrastinacion.app.data.local.db.LocalSnapshotRepository
import com.estudio.antiprocrastinacion.app.data.local.db.StudyDatabase
import com.estudio.antiprocrastinacion.app.data.local.store.AppSettingsStore
import com.estudio.antiprocrastinacion.app.data.local.store.SocialGateStore
import com.estudio.antiprocrastinacion.app.domain.scheduler.DefaultSchedulerService
import com.estudio.antiprocrastinacion.app.domain.session.DefaultSessionEngine
import com.estudio.antiprocrastinacion.app.domain.session.ItemStateUpdater
import com.estudio.antiprocrastinacion.app.domain.session.NodeStateUpdater
import com.estudio.antiprocrastinacion.app.notification.CognitiveNotificationPlanner
import com.estudio.antiprocrastinacion.app.notification.CognitiveNotificationScheduler
import com.estudio.antiprocrastinacion.app.notification.WorkManagerCognitiveNotificationScheduler
import com.estudio.antiprocrastinacion.app.socialgate.SocialGateCoordinator
import com.estudio.antiprocrastinacion.app.ui.common.DefaultIdProvider
import com.estudio.antiprocrastinacion.app.ui.common.DefaultTimeProvider
import com.estudio.antiprocrastinacion.app.ui.content.ContentViewModel
import com.estudio.antiprocrastinacion.app.ui.content.EditableImportViewModel
import com.estudio.antiprocrastinacion.app.ui.content.ManualBuilderViewModel
import com.estudio.antiprocrastinacion.app.ui.review.UpcomingReviewsViewModel
import com.estudio.antiprocrastinacion.app.ui.home.HomeViewModel
import com.estudio.antiprocrastinacion.app.ui.navigation.AppLaunchViewModel
import com.estudio.antiprocrastinacion.app.ui.settings.NotificationSettingsViewModel
import com.estudio.antiprocrastinacion.app.ui.settings.SocialGateSettingsViewModel
import com.estudio.antiprocrastinacion.app.ui.study.deep.DeepStudyViewModel
import com.estudio.antiprocrastinacion.app.ui.study.quick.QuickStudyViewModel

class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext

    // Nombre de archivo de la base. En producción es "study.db". La instrumentación lo sobreescribe
    // (vía StudyInstrumentationRunner) con una base aislada para que NINGÚN test pueda tocar los datos
    // reales del usuario. Es un get() para resolverse al construir la base, sin depender del orden.
    private val databaseName: String
        get() = databaseNameOverride ?: DEFAULT_DATABASE_NAME
    private val timeProvider = DefaultTimeProvider()
    private val idProvider = DefaultIdProvider()
    private val validator = DefaultImportValidator()
    private val authoringDraftValidator = AuthoringDraftValidator()
    private val authoringDraftCompiler = AuthoringDraftCompiler(authoringDraftValidator)
    private val naturalTextDraftExtractor = NaturalTextDraftExtractor()
    private val reviewedEditableDraftValidator = ReviewedEditableDraftValidator()
    private val reviewedQuestionBankCompiler = ReviewedQuestionBankCompiler()
    private val editableImportPreparationService =
        EditableImportPreparationService(
            draftValidator = reviewedEditableDraftValidator,
            compiler = reviewedQuestionBankCompiler,
            importValidator = validator,
            timeProvider = timeProvider,
        )
    private val importPreparationService =
        DefaultImportPreparationService(
            importValidator = validator,
            authoringDraftCompiler = authoringDraftCompiler,
            timeProvider = timeProvider,
        )
    private val database: StudyDatabase =
        Room.databaseBuilder(appContext, StudyDatabase::class.java, databaseName)
            .addMigrations(StudyDatabase.MIGRATION_1_2, StudyDatabase.MIGRATION_2_3)
            .build()
    private val settingsStore = AppSettingsStore(appContext)
    private val socialGateStore = SocialGateStore(appContext)

    private val eventRepository = LocalEventRepository(database.eventDao())
    private val contentRepository =
        LocalContentRepository(
            context = appContext,
            database = database,
            importPreparationService = importPreparationService,
            eventRepository = eventRepository,
            settingsStore = settingsStore,
            idProvider = idProvider,
            timeProvider = timeProvider,
        )
    private val progressRepository = LocalProgressRepository(database.nodeStateDao())
    private val sessionRepository = LocalSessionRepository(database.sessionDao(), timeProvider)
    private val settingsRepository = DefaultSettingsRepository(settingsStore)
    val socialGateRepository = LocalSocialGateRepository(socialGateStore)
    private val snapshotRepository =
        LocalSnapshotRepository(
            context = appContext,
            contentRepository = contentRepository,
            progressRepository = progressRepository,
            sessionRepository = sessionRepository,
            settingsRepository = settingsRepository,
            socialGateRepository = socialGateRepository,
            timeProvider = timeProvider,
        )
    // Bajo instrumentación el directorio de respaldo TAMBIÉN se aísla: así ningún test puede leer, pisar
    // ni "recuperar" el respaldo real del usuario (filesDir no se aísla solo; sí el nombre de la base).
    private val recoveryBackupDir =
        java.io.File(appContext.filesDir, if (databaseNameOverride == null) "course-recovery" else "course-recovery-test")
    val courseRecoveryStore =
        CourseRecoveryStore(
            backupDir = recoveryBackupDir,
            snapshotRepository = snapshotRepository,
            contentRepository = contentRepository,
            contentRecoveryWriter = contentRepository,
            progressRepository = progressRepository,
            timeProvider = timeProvider,
        )
    private val recoveryNoticeStore = RecoveryNoticeStore(java.io.File(recoveryBackupDir, "recovery-notice.json"))
    private val nodeStateUpdater = NodeStateUpdater()
    private val itemStateUpdater = ItemStateUpdater()
    private val schedulerService =
        DefaultSchedulerService(
            contentRepository = contentRepository,
            progressRepository = progressRepository,
            eventRepository = eventRepository,
            settingsRepository = settingsRepository,
            idProvider = idProvider,
            timeProvider = timeProvider,
        )
    val cognitiveNotificationScheduler: CognitiveNotificationScheduler =
        WorkManagerCognitiveNotificationScheduler(
            context = appContext,
            contentRepository = contentRepository,
            settingsRepository = settingsRepository,
            sessionRepository = sessionRepository,
            schedulerService = schedulerService,
            planner = CognitiveNotificationPlanner(),
            timeProvider = timeProvider,
        )
    private val sessionEngine =
        DefaultSessionEngine(
            schedulerService = schedulerService,
            contentRepository = contentRepository,
            progressRepository = progressRepository,
            sessionRepository = sessionRepository,
            eventRepository = eventRepository,
            settingsRepository = settingsRepository,
            database = database,
            nodeStateUpdater = nodeStateUpdater,
            itemStateUpdater = itemStateUpdater,
            idProvider = idProvider,
            timeProvider = timeProvider,
        )
    val socialGateCoordinator =
        SocialGateCoordinator(
            socialGateRepository = socialGateRepository,
            sessionRepository = sessionRepository,
            sessionEngine = sessionEngine,
            timeProvider = timeProvider,
        )

    fun homeViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                HomeViewModel(
                    contentRepository = contentRepository,
                    sessionRepository = sessionRepository,
                    sessionEngine = sessionEngine,
                    courseRecoveryStore = courseRecoveryStore,
                    recoveryNoticeStore = recoveryNoticeStore,
                )
            }
        }

    fun appLaunchViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                AppLaunchViewModel(
                    sessionRepository = sessionRepository,
                    sessionEngine = sessionEngine,
                )
            }
        }

    fun contentViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                ContentViewModel(
                    contentRepository = contentRepository,
                    importPreparationService = importPreparationService,
                    progressRepository = progressRepository,
                    snapshotRepository = snapshotRepository,
                    cognitiveNotificationScheduler = cognitiveNotificationScheduler,
                )
            }
        }

    fun editableImportViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                EditableImportViewModel(
                    contentRepository = contentRepository,
                    extractor = naturalTextDraftExtractor,
                    preparationService = editableImportPreparationService,
                )
            }
        }

    fun upcomingReviewsViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                UpcomingReviewsViewModel(
                    contentRepository = contentRepository,
                    progressRepository = progressRepository,
                    timeProvider = timeProvider,
                )
            }
        }

    fun manualBuilderViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                ManualBuilderViewModel(
                    contentRepository = contentRepository,
                    preparationService = editableImportPreparationService,
                )
            }
        }

    fun notificationSettingsViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                NotificationSettingsViewModel(
                    settingsRepository = settingsRepository,
                    cognitiveNotificationScheduler = cognitiveNotificationScheduler,
                )
            }
        }

    fun socialGateSettingsViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                SocialGateSettingsViewModel(
                    appContext = appContext,
                    socialGateRepository = socialGateRepository,
                )
            }
        }

    fun deepStudyViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                DeepStudyViewModel(
                    schedulerService = schedulerService,
                    sessionEngine = sessionEngine,
                )
            }
        }

    fun quickStudyViewModelFactory(): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                QuickStudyViewModel(sessionEngine = sessionEngine)
            }
        }

    companion object {
        const val DEFAULT_DATABASE_NAME = "study.db"

        /**
         * Sobreescritura del nombre de la base SOLO para tests instrumentados. La pone
         * StudyInstrumentationRunner antes de que arranque la app, de modo que toda la instrumentación
         * use una base aislada y jamás toque "study.db" (los datos reales del usuario). En producción
         * queda en null. @Volatile porque la setea el hilo del runner y la lee el hilo principal de la app.
         */
        @Volatile
        @JvmStatic
        var databaseNameOverride: String? = null
    }
}
