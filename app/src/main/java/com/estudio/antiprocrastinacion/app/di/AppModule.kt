package com.estudio.antiprocrastinacion.app.di

import android.content.Context
import androidx.room.Room
import com.estudio.antiprocrastinacion.app.data.importing.DefaultImportValidator
import com.estudio.antiprocrastinacion.app.data.importing.DefaultImportPreparationService
import com.estudio.antiprocrastinacion.app.data.local.dao.ContentDao
import com.estudio.antiprocrastinacion.app.data.local.dao.EventDao
import com.estudio.antiprocrastinacion.app.data.local.dao.NodeStateDao
import com.estudio.antiprocrastinacion.app.data.local.dao.SessionDao
import com.estudio.antiprocrastinacion.app.data.local.db.DefaultSettingsRepository
import com.estudio.antiprocrastinacion.app.data.local.db.LocalContentRepository
import com.estudio.antiprocrastinacion.app.data.local.db.LocalEventRepository
import com.estudio.antiprocrastinacion.app.data.local.db.LocalProgressRepository
import com.estudio.antiprocrastinacion.app.data.local.db.LocalSessionRepository
import com.estudio.antiprocrastinacion.app.data.local.db.LocalSnapshotRepository
import com.estudio.antiprocrastinacion.app.data.local.db.StudyDatabase
import com.estudio.antiprocrastinacion.app.data.local.store.AppSettingsStore
import com.estudio.antiprocrastinacion.app.domain.repository.ContentRepository
import com.estudio.antiprocrastinacion.app.domain.repository.EventRepository
import com.estudio.antiprocrastinacion.app.domain.repository.ImportValidator
import com.estudio.antiprocrastinacion.app.domain.repository.ImportPreparationService
import com.estudio.antiprocrastinacion.app.domain.repository.ProgressRepository
import com.estudio.antiprocrastinacion.app.domain.repository.SettingsRepository
import com.estudio.antiprocrastinacion.app.domain.repository.SnapshotRepository
import com.estudio.antiprocrastinacion.app.domain.repository.SessionRepository
import com.estudio.antiprocrastinacion.app.domain.scheduler.DefaultSchedulerService
import com.estudio.antiprocrastinacion.app.domain.scheduler.SchedulerService
import com.estudio.antiprocrastinacion.app.domain.session.DefaultSessionEngine
import com.estudio.antiprocrastinacion.app.domain.session.SessionEngine
import com.estudio.antiprocrastinacion.app.ui.common.DefaultIdProvider
import com.estudio.antiprocrastinacion.app.ui.common.DefaultTimeProvider
import com.estudio.antiprocrastinacion.app.ui.common.IdProvider
import com.estudio.antiprocrastinacion.app.ui.common.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PersistenceModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): StudyDatabase =
        Room.databaseBuilder(context, StudyDatabase::class.java, "study.db")
            .build()

    @Provides
    fun provideContentDao(database: StudyDatabase): ContentDao = database.contentDao()

    @Provides
    fun provideNodeStateDao(database: StudyDatabase): NodeStateDao = database.nodeStateDao()

    @Provides
    fun provideSessionDao(database: StudyDatabase): SessionDao = database.sessionDao()

    @Provides
    fun provideEventDao(database: StudyDatabase): EventDao = database.eventDao()

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): AppSettingsStore = AppSettingsStore(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun bindContentRepository(impl: LocalContentRepository): ContentRepository

    @Binds
    abstract fun bindProgressRepository(impl: LocalProgressRepository): ProgressRepository

    @Binds
    abstract fun bindSessionRepository(impl: LocalSessionRepository): SessionRepository

    @Binds
    abstract fun bindEventRepository(impl: LocalEventRepository): EventRepository

    @Binds
    abstract fun bindSettingsRepository(impl: DefaultSettingsRepository): SettingsRepository

    @Binds
    abstract fun bindSnapshotRepository(impl: LocalSnapshotRepository): SnapshotRepository

    @Binds
    abstract fun bindImportValidator(impl: DefaultImportValidator): ImportValidator

    @Binds
    abstract fun bindImportPreparationService(impl: DefaultImportPreparationService): ImportPreparationService

    @Binds
    abstract fun bindSchedulerService(impl: DefaultSchedulerService): SchedulerService

    @Binds
    abstract fun bindSessionEngine(impl: DefaultSessionEngine): SessionEngine

    @Binds
    abstract fun bindTimeProvider(impl: DefaultTimeProvider): TimeProvider

    @Binds
    abstract fun bindIdProvider(impl: DefaultIdProvider): IdProvider
}
