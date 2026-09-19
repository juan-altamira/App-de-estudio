package com.estudio.antiprocrastinacion.app.data.local.db

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.estudio.antiprocrastinacion.app.domain.repository.ContentRepository
import com.estudio.antiprocrastinacion.app.domain.repository.EventRepository
import com.estudio.antiprocrastinacion.app.domain.repository.ImportExecutionResult
import com.estudio.antiprocrastinacion.app.domain.repository.ImportPreparationResult
import com.estudio.antiprocrastinacion.app.domain.repository.ImportPreparationService
import com.estudio.antiprocrastinacion.app.domain.repository.ImportValidationProfile
import com.estudio.antiprocrastinacion.app.domain.repository.ItemPerformanceSummary
import com.estudio.antiprocrastinacion.app.domain.repository.PreparedImportKind
import com.estudio.antiprocrastinacion.app.domain.repository.ProgressRepository
import com.estudio.antiprocrastinacion.app.domain.repository.SnapshotExecutionResult
import com.estudio.antiprocrastinacion.app.domain.repository.SnapshotRepository
import com.estudio.antiprocrastinacion.app.domain.repository.SettingsRepository
import com.estudio.antiprocrastinacion.app.domain.repository.SocialGateRepository
import com.estudio.antiprocrastinacion.app.domain.repository.SessionRepository
import com.estudio.antiprocrastinacion.app.domain.repository.ValidationMessage
import com.estudio.antiprocrastinacion.app.domain.repository.ValidationReport
import com.estudio.antiprocrastinacion.app.model.content.ContentOrigin
import com.estudio.antiprocrastinacion.app.model.content.ContentTree
import com.estudio.antiprocrastinacion.app.model.content.CourseWithUnits
import com.estudio.antiprocrastinacion.app.model.content.Item
import com.estudio.antiprocrastinacion.app.model.content.ItemOverride
import com.estudio.antiprocrastinacion.app.model.content.ManualItemEdit
import com.estudio.antiprocrastinacion.app.model.content.Node
import com.estudio.antiprocrastinacion.app.model.content.NodeDetail
import com.estudio.antiprocrastinacion.app.model.content.OutcomeWithNodes
import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.content.UnitModel
import com.estudio.antiprocrastinacion.app.model.content.UnitWithOutcomes
import com.estudio.antiprocrastinacion.app.model.event.AbandonEvent
import com.estudio.antiprocrastinacion.app.model.event.ImportEvent
import com.estudio.antiprocrastinacion.app.model.event.ImportResultType
import com.estudio.antiprocrastinacion.app.model.event.ReviewEvent
import com.estudio.antiprocrastinacion.app.model.event.SessionEvent
import com.estudio.antiprocrastinacion.app.model.json.ContentPackageDto
import com.estudio.antiprocrastinacion.app.model.json.SyncSnapshotDto
import com.estudio.antiprocrastinacion.app.model.json.UserStatePackageDto
import com.estudio.antiprocrastinacion.app.model.state.AppSettings
import com.estudio.antiprocrastinacion.app.model.state.ItemState
import com.estudio.antiprocrastinacion.app.model.state.NodeFormatStat
import com.estudio.antiprocrastinacion.app.model.state.NodeState
import com.estudio.antiprocrastinacion.app.model.state.SocialGateDailyState
import com.estudio.antiprocrastinacion.app.model.state.SocialGateRule
import com.estudio.antiprocrastinacion.app.model.state.SocialGateRuntimeState
import com.estudio.antiprocrastinacion.app.model.state.StudySession
import com.estudio.antiprocrastinacion.app.data.local.store.AppSettingsStore
import com.estudio.antiprocrastinacion.app.data.local.store.SocialGateStore
import com.estudio.antiprocrastinacion.app.data.importing.ContentImportRules
import com.estudio.antiprocrastinacion.app.socialgate.SocialGateCatalog
import com.estudio.antiprocrastinacion.app.socialgate.SocialGateSchedule
import com.estudio.antiprocrastinacion.app.ui.common.AppJson
import com.estudio.antiprocrastinacion.app.ui.common.IdProvider
import com.estudio.antiprocrastinacion.app.ui.common.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.time.Instant
import java.time.ZoneId

@Singleton
class LocalContentRepository @Inject constructor(
    @param:ApplicationContext
    private val context: Context,
    private val database: StudyDatabase,
    private val importPreparationService: ImportPreparationService,
    private val eventRepository: EventRepository,
    private val settingsStore: AppSettingsStore,
    private val idProvider: IdProvider,
    private val timeProvider: TimeProvider,
) : ContentRepository, ContentRecoveryWriter {
    private val contentDao = database.contentDao()

    /**
     * Recuperación: upsert verbatim del contenido perdido, SIN validación, SIN eventos de import y SIN
     * archivado de siblings. No usar para imports normales (ese camino sí archiva); esto solo reinserta
     * exactamente lo que el respaldo tenía, sin tocar nada más.
     */
    override suspend fun restoreContentForRecovery(packages: List<ContentPackageDto>) {
        if (packages.isEmpty()) return
        database.withTransaction {
            packages.forEach { pkg ->
                contentDao.upsertCourses(pkg.courses.map { it.asEntity() })
                contentDao.upsertUnits(pkg.units.map { it.asEntity() })
                contentDao.upsertOutcomes(pkg.outcomes.map { it.asEntity() })
                contentDao.upsertNodes(pkg.nodes.map { it.asEntity(origin = pkg.origin, archivedCandidate = false) })
                contentDao.upsertItems(pkg.items.map { it.asEntity(origin = pkg.origin) })
            }
        }
    }

    override suspend fun seedDemoIfNeeded() {
        val raw = readAssetSeed()
        val contentPackage = getContentPackage(raw)
        val latest = eventRepository.getLatestImportEvent(contentPackage.packageId)
        val hasAnyContent = contentDao.getNodes().isNotEmpty()
        val shouldImport =
            when {
                !hasAnyContent -> true
                latest == null -> false
                latest.seedVersion != contentPackage.seedVersion -> true
                else -> false
            }
        if (shouldImport) {
            importContentPackage(raw, "seed")
        }
        repairDerivedItemSurfaces()
    }

    /**
     * One-time, idempotent self-heal: re-derives each item's allowed surfaces from (format, role) and
     * rewrites only the rows that drifted. This fixes content compiled before open-recall cards were
     * allowed into the daily review, so already-loaded "ver respuesta" cards start appearing in
     * Tarjetas pendientes without the user reloading anything. Only the surfaces column is touched;
     * progress (item/node state) lives in separate tables and is never affected.
     */
    private suspend fun repairDerivedItemSurfaces() {
        val entities = contentDao.getAllItems()
        if (entities.isEmpty()) return
        val repaired =
            entities.mapNotNull { entity ->
                val item = entity.asDomain()
                ContentImportRules
                    .repairedSurfacesOrNull(item.format, item.itemRole, item.allowedSurfaces)
                    ?.let { fixed -> entity.copy(allowedSurfacesJson = AppJson.encodeToString(fixed)) }
            }
        if (repaired.isNotEmpty()) {
            contentDao.upsertItems(repaired)
        }
    }

    suspend fun importContentPackage(
        rawJson: String,
        sourceLabel: String,
    ): ImportExecutionResult =
        importContentPackage(
            rawJson = rawJson,
            sourceLabel = sourceLabel,
            validationProfile = ImportValidationProfile.PEDAGOGICAL_NODE,
        )

    override suspend fun importContentPackage(
        rawJson: String,
        sourceLabel: String,
        validationProfile: ImportValidationProfile,
    ): ImportExecutionResult {
        val prepared = importPreparationService.prepare(rawJson, sourceLabel, validationProfile)
        val contentPackage = prepared.contentPackage
        val report = prepared.report
        val effectiveValidationProfile = prepared.validationProfile
        if (contentPackage == null || !prepared.canImport) {
            eventRepository.recordImportEvent(
                ImportEvent(
                    eventId = idProvider.newId(),
                    packageId = prepared.packageId,
                    schemaVersion = prepared.schemaVersion,
                    seedVersion = contentPackage?.seedVersion,
                    contentHash = contentPackage?.contentHash ?: sourceLabel,
                    result = ImportResultType.FAILURE,
                    createdAt = timeProvider.now(),
                ),
            )
            return ImportExecutionResult(
                packageId = prepared.packageId,
                imported = false,
                report = report,
            )
        }

        val origin = contentPackage.origin
        database.withTransaction {
            val nodesByOrigin = contentDao.getNodesByOrigin(origin.name)
            val incomingNodeIds = contentPackage.nodes.map { it.nodeId }.toSet()
            val affectedCourseIds = contentPackage.courses.map { it.courseId }.toSet()

            contentDao.upsertCourses(contentPackage.courses.map { it.asEntity() })
            contentDao.upsertUnits(contentPackage.units.map { it.asEntity() })
            contentDao.upsertOutcomes(contentPackage.outcomes.map { it.asEntity() })
            contentDao.upsertNodes(contentPackage.nodes.map { it.asEntity(origin = origin, archivedCandidate = false) })
            contentDao.upsertItems(contentPackage.items.map { it.asEntity(origin = origin) })

            nodeIdsToArchiveOnImport(
                existingNodesOfOrigin = nodesByOrigin.map { ArchivableNodeRef(nodeId = it.nodeId, courseId = it.courseId) },
                affectedCourseIds = affectedCourseIds,
                incomingNodeIds = incomingNodeIds,
                profile = effectiveValidationProfile,
            ).forEach { nodeId ->
                contentDao.setArchivedCandidate(nodeId, true)
            }
        }

        settingsStore.update { settings ->
            if (origin == ContentOrigin.DEMO) {
                settings.copy(
                    seedAppliedVersion = contentPackage.seedVersion,
                    seedPackageId = contentPackage.packageId,
                )
            } else {
                settings
            }
        }

        eventRepository.recordImportEvent(
            ImportEvent(
                eventId = idProvider.newId(),
                packageId = contentPackage.packageId,
                schemaVersion = contentPackage.schemaVersion,
                seedVersion = contentPackage.seedVersion,
                contentHash = contentPackage.contentHash ?: sourceLabel,
                result = if (report.authoringWarnings.isEmpty()) ImportResultType.SUCCESS else ImportResultType.WARNING,
                createdAt = timeProvider.now(),
            ),
        )

        return ImportExecutionResult(
            packageId = contentPackage.packageId,
            imported = true,
            report = report,
        )
    }

    override suspend fun importContentPackage(uri: Uri): ImportExecutionResult {
        val rawJson = readUriText(uri)
        return importContentPackage(rawJson, uri.toString())
    }

    override suspend fun prepareContentPackage(uri: Uri): ImportPreparationResult =
        runCatching { readUriText(uri) }.fold(
            onSuccess = { rawJson -> importPreparationService.prepare(rawJson, uri.toString()) },
            onFailure = { error ->
                ImportPreparationResult(
                    sourceLabel = uri.toString(),
                    kind = PreparedImportKind.INVALID,
                    packageId = uri.toString(),
                    schemaVersion = 0,
                    contentPackage = null,
                    contentPackageJson = null,
                    report =
                        ValidationReport(
                            structuralErrors =
                                listOf(
                                    ValidationMessage(
                                        code = "file_read_error",
                                        message = "No se pudo leer el archivo seleccionado: ${error.message ?: "error desconocido"}",
                                    ),
                                ),
                            authoringWarnings = emptyList(),
                        ),
                )
            },
        )

    override suspend fun getContentTree(): ContentTree {
        val courses = contentDao.getCourses().map { it.asDomain() }
        val units = contentDao.getUnits().map { it.asDomain() }
        val outcomes = contentDao.getOutcomes().map { it.asDomain() }
        val nodes = contentDao.getNodes().map { it.asDomain() }

        return ContentTree(
            courses = courses.map { course ->
                CourseWithUnits(
                    course = course,
                    units = units
                        .filter { it.courseId == course.courseId }
                        .map { unit ->
                            UnitWithOutcomes(
                                unit = unit,
                                outcomes = outcomes
                                    .filter { it.unitId == unit.unitId }
                                    .map { outcome ->
                                        OutcomeWithNodes(
                                            outcome = outcome,
                                            nodes = nodes.filter { outcome.outcomeId in it.outcomeIds },
                                        )
                                    },
                            )
                        },
                )
            },
        )
    }

    override suspend fun getUnits(): List<UnitModel> = contentDao.getUnits().map { it.asDomain() }

    override suspend fun getSchedulableNodes(): List<Node> =
        contentDao.getSchedulableNodes().map { it.asDomain() }

    override suspend fun getSchedulableNodesByUnit(unitId: String): List<Node> =
        contentDao.getSchedulableNodesByUnit(unitId).map { it.asDomain() }

    override suspend fun getNode(nodeId: String): Node? = contentDao.getNodeById(nodeId)?.asDomain()

    override suspend fun getNodeDetail(
        nodeId: String,
        includeArchivedItems: Boolean,
    ): NodeDetail? {
        val node = contentDao.getNodeById(nodeId)?.asDomain() ?: return null
        val items = mergeItems(contentDao.getItemsForNode(nodeId), includeArchivedItems)
        return NodeDetail(node = node, items = items)
    }

    override suspend fun getItem(
        itemId: String,
        includeArchived: Boolean,
    ): Item? = mergeItem(contentDao.getItemById(itemId), includeArchived)

    override suspend fun getItemsForNode(
        nodeId: String,
        includeArchived: Boolean,
    ): List<Item> = mergeItems(contentDao.getItemsForNode(nodeId), includeArchived)

    override suspend fun getItemsForNodes(
        nodeIds: List<String>,
        includeArchived: Boolean,
    ): List<Item> =
        if (nodeIds.isEmpty()) emptyList() else mergeItems(contentDao.getItemsForNodes(nodeIds), includeArchived)

    override suspend fun setArchivedCandidate(nodeId: String, archived: Boolean) {
        contentDao.setArchivedCandidate(nodeId, archived)
    }

    override suspend fun getAllItemOverrides(): List<ItemOverride> =
        contentDao.getAllItemOverrides().map { it.asDomain() }

    override suspend fun setItemArchived(
        itemId: String,
        archived: Boolean,
    ) {
        val current = contentDao.getItemOverrideById(itemId)?.asDomain()
        val next = (current ?: ItemOverride(itemId = itemId)).copy(archived = archived)
        if (!next.archived && next.stemOverride == null && next.correctAnswerOverride == null && next.optionsOverride == null) {
            contentDao.deleteItemOverride(itemId)
        } else {
            contentDao.upsertItemOverrides(listOf(next.asEntity()))
        }
    }

    override suspend fun updateItemManualEdit(edit: ManualItemEdit) {
        val current = contentDao.getItemOverrideById(edit.itemId)?.asDomain()
        contentDao.upsertItemOverrides(listOf(edit.asOverride(current).asEntity()))
    }

    override suspend fun hasRealImportedContent(): Boolean =
        contentDao.countActiveNodesByOrigin(ContentOrigin.IMPORTED.name) > 0

    override suspend fun getContentPackage(rawJson: String): ContentPackageDto =
        AppJson.decodeFromString(rawJson)

    private fun readUriText(uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("No se pudo leer el archivo seleccionado.")

    private suspend fun mergeItem(
        entity: ItemEntity?,
        includeArchived: Boolean,
    ): Item? {
        if (entity == null) return null
        val override = contentDao.getItemOverrideById(entity.itemId)?.asDomain()
        val merged = entity.asDomain().applyOverride(override)
        return merged.takeIf { includeArchived || !it.archivedManual }
    }

    private suspend fun mergeItems(
        entities: List<ItemEntity>,
        includeArchived: Boolean,
    ): List<Item> {
        if (entities.isEmpty()) return emptyList()
        val overrides =
            contentDao
                .getItemOverrides(entities.map { it.itemId })
                .map { it.asDomain() }
                .associateBy(ItemOverride::itemId)
        return entities
            .map { entity -> entity.asDomain().applyOverride(overrides[entity.itemId]) }
            .filter { includeArchived || !it.archivedManual }
    }

    private fun readAssetSeed(): String =
        context.assets.open("seed/demo_content.json").bufferedReader().use { it.readText() }
}

internal fun safeDecodeContentPackage(rawJson: String): Result<ContentPackageDto> =
    runCatching { AppJson.decodeFromString<ContentPackageDto>(rawJson) }

internal fun invalidJsonImportResult(
    sourceLabel: String,
    error: Throwable,
): ImportExecutionResult =
    ImportExecutionResult(
        packageId = sourceLabel,
        imported = false,
        report =
            ValidationReport(
                structuralErrors =
                    listOf(
                        ValidationMessage(
                            code = "json_parse_error",
                            message = "El JSON no se pudo parsear: ${error.message ?: "formato inválido"}",
                        ),
                    ),
                authoringWarnings = emptyList(),
            ),
    )

@Singleton
class LocalProgressRepository @Inject constructor(
    private val nodeStateDao: com.estudio.antiprocrastinacion.app.data.local.dao.NodeStateDao,
) : ProgressRepository {
    override suspend fun getNodeState(nodeId: String): NodeState? = nodeStateDao.getNodeState(nodeId)?.asDomain()

    override suspend fun getAllNodeStates(): List<NodeState> = nodeStateDao.getAllNodeStates().map { it.asDomain() }

    override suspend fun getItemState(itemId: String): ItemState? = nodeStateDao.getItemState(itemId)?.asDomain()

    override suspend fun getItemStates(itemIds: List<String>): List<ItemState> =
        if (itemIds.isEmpty()) emptyList() else nodeStateDao.getItemStates(itemIds).map { it.asDomain() }

    override suspend fun getAllItemStates(): List<ItemState> = nodeStateDao.getAllItemStates().map { it.asDomain() }

    override suspend fun getAllFormatStats(): List<NodeFormatStat> = nodeStateDao.getAllFormatStats().map { it.asDomain() }

    override suspend fun upsertNodeState(nodeState: NodeState) {
        nodeStateDao.upsertNodeState(nodeState.asEntity())
    }

    override suspend fun upsertItemState(itemState: ItemState) {
        nodeStateDao.upsertItemState(itemState.asEntity())
    }

    override suspend fun upsertItemStates(itemStates: List<ItemState>) {
        nodeStateDao.upsertItemStates(itemStates.map { it.asEntity() })
    }

    override suspend fun getFormatStats(nodeId: String): List<NodeFormatStat> =
        nodeStateDao.getFormatStats(nodeId).map { it.asDomain() }

    override suspend fun upsertFormatStats(stats: List<NodeFormatStat>) {
        nodeStateDao.upsertFormatStats(stats.map { it.asEntity() })
    }

    override suspend fun clearAllProgress() {
        nodeStateDao.deleteAllNodeStates()
        nodeStateDao.deleteAllItemStates()
        nodeStateDao.deleteAllFormatStats()
    }

    override suspend fun resetNodeProgress(nodeId: String) {
        nodeStateDao.deleteNodeState(nodeId)
        nodeStateDao.deleteItemStatesForNode(nodeId)
        nodeStateDao.deleteFormatStats(nodeId)
    }
}

@Singleton
class LocalSessionRepository @Inject constructor(
    private val sessionDao: com.estudio.antiprocrastinacion.app.data.local.dao.SessionDao,
    private val timeProvider: TimeProvider,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : SessionRepository {
    override suspend fun getActiveSession(mode: SessionMode): StudySession? {
        val session = sessionDao.getActiveSession(mode.name)?.asDomain() ?: return null
        return if (retainIfResumable(session)) session else null
    }

    override suspend fun getActiveSessionById(sessionId: String): StudySession? {
        val session = sessionDao.getActiveSessionById(sessionId)?.asDomain() ?: return null
        return if (retainIfResumable(session)) session else null
    }

    override suspend fun getAllActiveSessions(): List<StudySession> =
        sessionDao.getActiveSessions().map(ActiveSessionEntity::asDomain).filter { session ->
            retainIfResumable(session)
        }

    override suspend fun saveActiveSession(session: StudySession) {
        sessionDao.clearActiveSession(session.mode.name)
        sessionDao.upsertActiveSession(session.asEntity())
    }

    override suspend fun clearActiveSession(mode: SessionMode) {
        sessionDao.clearActiveSession(mode.name)
    }

    override suspend fun clearAllActiveSessions() {
        sessionDao.clearActiveSessions()
    }

    private suspend fun retainIfResumable(session: StudySession): Boolean {
        if (!session.isExpiredQuickSession(timeProvider.now(), zoneId)) {
            return true
        }
        sessionDao.clearActiveSession(SessionMode.QUICK.name)
        return false
    }
}

@Singleton
class LocalEventRepository @Inject constructor(
    private val eventDao: com.estudio.antiprocrastinacion.app.data.local.dao.EventDao,
) : EventRepository {
    override suspend fun recordReviewEvent(event: ReviewEvent) {
        eventDao.insertReviewEvent(event.asEntity())
    }

    override suspend fun recordSessionEvent(event: SessionEvent) {
        eventDao.insertSessionEvent(event.asEntity())
    }

    override suspend fun recordAbandonEvent(event: AbandonEvent) {
        eventDao.insertAbandonEvent(event.asEntity())
    }

    override suspend fun recordImportEvent(event: ImportEvent) {
        eventDao.insertImportEvent(event.asEntity())
    }

    override suspend fun getRecentTopicIds(): List<String> = eventDao.getRecentTopicIds()

    override suspend fun getAbandonEventsSince(since: Long): List<AbandonEvent> =
        eventDao.getAbandonEventsSince(since).map { it.asDomain() }

    override suspend fun getLatestImportEvent(packageId: String): ImportEvent? =
        eventDao.getLatestImportEvent(packageId)?.asDomain()

    override suspend fun getItemPerformance(itemIds: List<String>): Map<String, ItemPerformanceSummary> {
        if (itemIds.isEmpty()) return emptyMap()
        return eventDao.getItemPerformanceRows(itemIds).associate { row ->
            row.itemId to ItemPerformanceSummary(
                itemId = row.itemId,
                attempts = row.attempts,
                successes = row.successes,
            )
        }
    }

    override suspend fun getHistoricalItemAttemptsBefore(itemIds: List<String>, before: Long): Map<String, Int> {
        if (itemIds.isEmpty()) return emptyMap()
        return eventDao.getHistoricalAttemptRows(itemIds, before).associate { row ->
            row.itemId to row.attempts
        }
    }

    override suspend fun getSessionScheduledSuccessCount(sessionId: String, itemId: String): Int =
        eventDao.getSessionScheduledSuccessCount(sessionId, itemId)

    override suspend fun getPendingFailedScheduledItemCount(sessionId: String): Int =
        eventDao.getPendingFailedScheduledItemCount(sessionId)
}

@Singleton
class DefaultSettingsRepository @Inject constructor(
    private val store: AppSettingsStore,
) : SettingsRepository {
    override fun observeSettings(): Flow<AppSettings> = store.settings

    override suspend fun getSettings(): AppSettings = store.getCurrent()

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        store.update(transform)
    }
}

@Singleton
class LocalSocialGateRepository @Inject constructor(
    private val store: SocialGateStore,
) : SocialGateRepository {
    private fun ensureActiveRules(rules: List<SocialGateRule>): List<SocialGateRule> {
        val byPackage = rules.associateBy { it.packageName }
        val supported = SocialGateCatalog.supportedApps.map { app ->
            val existing = byPackage[app.packageName]
            SocialGateSchedule.normalizeRule(existing ?: SocialGateCatalog.defaultRule(app))
        }
        val others = rules.filterNot { rule -> SocialGateCatalog.supportedApps.any { it.packageName == rule.packageName } }
            .map(SocialGateSchedule::normalizeRule)
        return (supported + others).sortedBy { it.displayName.lowercase() }
    }

    override fun observeRules(): Flow<List<SocialGateRule>> =
        store.snapshot
            .map { snapshot -> ensureActiveRules(snapshot.rules.map { it.asDomain() }) }
            .distinctUntilChanged()

    override suspend fun getRules(): List<SocialGateRule> =
        ensureActiveRules(store.getCurrent().rules.map { it.asDomain() })

    override suspend fun getRule(packageName: String): SocialGateRule? =
        getRules().firstOrNull { it.packageName == packageName }

    override suspend fun upsertRule(rule: SocialGateRule) {
        val normalized = SocialGateSchedule.normalizeRule(rule)
        store.update { current ->
            val updatedRules =
                ensureActiveRules(
                    current.rules
                        .map { it.asDomain() }
                        .filterNot { it.packageName == normalized.packageName } + normalized,
                )
            current.copy(
                rules = updatedRules.map { it.asDto() },
            )
        }
    }

    override suspend fun getDailyStates(): List<SocialGateDailyState> =
        store.getCurrent().dailyStates.map { it.asDomain() }

    override suspend fun incrementSolvedCount(packageName: String, localDate: String, solvedAt: Long) {
        store.update { current ->
            val updated =
                current.dailyStates
                    .map { it.asDomain() }
                    .toMutableList()
                    .apply {
                        val existingIndex = indexOfFirst { it.packageName == packageName && it.localDate == localDate }
                        if (existingIndex >= 0) {
                            val previous = get(existingIndex)
                            set(
                                existingIndex,
                                previous.copy(
                                    solvedCount = previous.solvedCount + 1,
                                    lastSolvedAt = solvedAt,
                                ),
                            )
                        } else {
                            add(
                                SocialGateDailyState(
                                    packageName = packageName,
                                    localDate = localDate,
                                    solvedCount = 1,
                                    lastSolvedAt = solvedAt,
                                ),
                            )
                        }
                    }
            current.copy(
                dailyStates = updated.sortedWith(compareBy(SocialGateDailyState::localDate, SocialGateDailyState::packageName)).map { it.asDto() },
            )
        }
    }

    override suspend fun getRuntimeState(): SocialGateRuntimeState =
        store.getCurrent().runtimeState.asDomain()

    override suspend fun updateRuntimeState(transform: (SocialGateRuntimeState) -> SocialGateRuntimeState) {
        store.update { current ->
            current.copy(runtimeState = transform(current.runtimeState.asDomain()).asDto())
        }
    }

    override suspend fun replaceState(
        rules: List<SocialGateRule>,
        dailyStates: List<SocialGateDailyState>,
        runtimeState: SocialGateRuntimeState,
    ) {
        val activeRules = ensureActiveRules(rules)
        store.update {
            it.copy(
                rules = activeRules.map { rule -> rule.asDto() },
                dailyStates = dailyStates.map { state -> state.asDto() },
                runtimeState = runtimeState.asDto(),
            )
        }
    }
}

@Singleton
class LocalSnapshotRepository @Inject constructor(
    @param:ApplicationContext
    private val context: Context?,
    private val contentRepository: ContentRepository,
    private val progressRepository: ProgressRepository,
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    private val socialGateRepository: SocialGateRepository,
    private val timeProvider: TimeProvider,
) : SnapshotRepository {
    override suspend fun exportUserStatePackage(): UserStatePackageDto {
        val exportedAt = timeProvider.now()
        val contentTree = contentRepository.getContentTree()
        return UserStatePackageDto(
            exportedAt = exportedAt,
            nodeStates = progressRepository.getAllNodeStates().map { it.asDto() },
            itemStates = progressRepository.getAllItemStates().map { it.asDto() },
            itemOverrides = contentRepository.getAllItemOverrides().map { it.asDto() },
            nodeFormatStats = progressRepository.getAllFormatStats().map { it.asDto() },
            archivedNodeIds =
                contentTree.courses
                    .flatMap { it.units }
                    .flatMap { it.outcomes }
                    .flatMap { it.nodes }
                    .filter { it.archivedCandidate }
                    .map { it.nodeId }
                    .sorted(),
            activeSession = sessionRepository.getMostRecentActiveSession()?.asDto(),
            activeSessions = sessionRepository.getAllActiveSessions().map { it.asDto() },
            appSettings = settingsRepository.getSettings().asDto(),
            socialGateRules = socialGateRepository.getRules().map { it.asDto() },
            socialGateDailyStates = socialGateRepository.getDailyStates().map { it.asDto() },
            socialGateRuntimeState = socialGateRepository.getRuntimeState().asDto(),
        )
    }

    override suspend fun exportSyncSnapshot(): SyncSnapshotDto {
        val exportedAt = timeProvider.now()
        val contentPackages = buildSnapshotContentPackages(exportedAt)
        return SyncSnapshotDto(
            snapshotVersion = 1,
            exportedAt = exportedAt,
            contentPackages = contentPackages,
            userStatePackage = exportUserStatePackage(),
        )
    }

    override suspend fun exportUserStatePackage(uri: Uri): SnapshotExecutionResult {
        val userStatePackage = exportUserStatePackage()
        val contentResolver = context?.contentResolver ?: error("Context requerido para exportar user_state_package a Uri.")
        contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write(AppJson.encodeToString(userStatePackage))
        } ?: error("No se pudo abrir el destino del user_state_package.")

        return SnapshotExecutionResult(
            sourceLabel = uri.toString(),
            success = true,
            message = "user_state_package exportado.",
            contentPackageCount = 0,
            nodeStateCount = userStatePackage.nodeStates.size,
            formatStatCount = userStatePackage.nodeFormatStats.size + userStatePackage.itemStates.size + userStatePackage.itemOverrides.size,
            archivedNodeCount = userStatePackage.archivedNodeIds.size,
            restoredActiveSession = userStatePackage.activeSessions.isNotEmpty() || userStatePackage.activeSession != null,
        )
    }

    override suspend fun exportSyncSnapshot(uri: Uri): SnapshotExecutionResult {
        val snapshot = exportSyncSnapshot()
        val contentResolver = context?.contentResolver ?: error("Context requerido para exportar snapshot a Uri.")
        contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write(AppJson.encodeToString(snapshot))
        } ?: error("No se pudo abrir el destino del snapshot.")

        return SnapshotExecutionResult(
            sourceLabel = uri.toString(),
            success = true,
            message = "Snapshot exportado.",
            contentPackageCount = snapshot.normalizedContentPackages().size,
            nodeStateCount = snapshot.userStatePackage.nodeStates.size,
            formatStatCount = snapshot.userStatePackage.nodeFormatStats.size + snapshot.userStatePackage.itemStates.size + snapshot.userStatePackage.itemOverrides.size,
            archivedNodeCount = snapshot.userStatePackage.archivedNodeIds.size,
            restoredActiveSession = snapshot.userStatePackage.activeSessions.isNotEmpty() || snapshot.userStatePackage.activeSession != null,
        )
    }

    override suspend fun importSyncSnapshot(rawJson: String, sourceLabel: String): SnapshotExecutionResult {
        val snapshot =
            runCatching { AppJson.decodeFromString<SyncSnapshotDto>(rawJson) }.getOrElse { error ->
                return SnapshotExecutionResult(
                    sourceLabel = sourceLabel,
                    success = false,
                    message = "El snapshot no se pudo parsear: ${error.message ?: "formato inválido"}",
                    contentPackageCount = 0,
                    nodeStateCount = 0,
                    formatStatCount = 0,
                    archivedNodeCount = 0,
                    restoredActiveSession = false,
                )
            }
        val contentPackages = snapshot.normalizedContentPackages()
        if (contentPackages.isEmpty()) {
            return SnapshotExecutionResult(
                sourceLabel = sourceLabel,
                success = false,
                message = "El snapshot no trae content_package válido.",
                contentPackageCount = 0,
                nodeStateCount = 0,
                formatStatCount = 0,
                archivedNodeCount = 0,
                restoredActiveSession = false,
            )
        }

        contentPackages.forEach { contentPackage ->
            val result =
                contentRepository.importContentPackage(
                    rawJson = AppJson.encodeToString(contentPackage),
                    sourceLabel = "snapshot:${contentPackage.packageId}",
                )
            if (!result.imported) {
                return SnapshotExecutionResult(
                    sourceLabel = sourceLabel,
                    success = false,
                    message = "No se pudo importar content_package ${contentPackage.packageId}.",
                    contentPackageCount = contentPackages.size,
                    nodeStateCount = snapshot.userStatePackage.nodeStates.size,
                    formatStatCount = snapshot.userStatePackage.nodeFormatStats.size + snapshot.userStatePackage.itemStates.size + snapshot.userStatePackage.itemOverrides.size,
                    archivedNodeCount = snapshot.userStatePackage.archivedNodeIds.size,
                    restoredActiveSession = false,
                )
            }
        }

        val activeSessions = snapshot.userStatePackage.normalizedActiveSessions().map { it.asDomain() }
        restoreUserState(snapshot.userStatePackage)

        return SnapshotExecutionResult(
            sourceLabel = sourceLabel,
            success = true,
            message = "Snapshot importado.",
            contentPackageCount = contentPackages.size,
            nodeStateCount = snapshot.userStatePackage.nodeStates.size,
            formatStatCount = snapshot.userStatePackage.nodeFormatStats.size + snapshot.userStatePackage.itemStates.size + snapshot.userStatePackage.itemOverrides.size,
            archivedNodeCount = snapshot.userStatePackage.archivedNodeIds.size,
            restoredActiveSession = activeSessions.isNotEmpty(),
        )
    }

    override suspend fun importSyncSnapshot(uri: Uri): SnapshotExecutionResult {
        val contentResolver = context?.contentResolver ?: error("Context requerido para importar snapshot desde Uri.")
        val rawJson =
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("No se pudo leer el archivo seleccionado.")
        return importSyncSnapshot(rawJson, uri.toString())
    }

    override suspend fun importUserStatePackage(rawJson: String, sourceLabel: String): SnapshotExecutionResult {
        val userStatePackage =
            runCatching { AppJson.decodeFromString<UserStatePackageDto>(rawJson) }.getOrElse { error ->
                return SnapshotExecutionResult(
                    sourceLabel = sourceLabel,
                    success = false,
                    message = "El user_state_package no se pudo parsear: ${error.message ?: "formato inválido"}",
                    contentPackageCount = 0,
                    nodeStateCount = 0,
                    formatStatCount = 0,
                    archivedNodeCount = 0,
                    restoredActiveSession = false,
                )
            }

        restoreUserState(userStatePackage)

        return SnapshotExecutionResult(
            sourceLabel = sourceLabel,
            success = true,
            message = "user_state_package importado.",
            contentPackageCount = 0,
            nodeStateCount = userStatePackage.nodeStates.size,
            formatStatCount = userStatePackage.nodeFormatStats.size + userStatePackage.itemStates.size + userStatePackage.itemOverrides.size,
            archivedNodeCount = userStatePackage.archivedNodeIds.size,
            restoredActiveSession = userStatePackage.activeSessions.isNotEmpty() || userStatePackage.activeSession != null,
        )
    }

    override suspend fun importUserStatePackage(uri: Uri): SnapshotExecutionResult {
        val contentResolver = context?.contentResolver ?: error("Context requerido para importar user_state_package desde Uri.")
        val rawJson =
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("No se pudo leer el archivo seleccionado.")
        return importUserStatePackage(rawJson, uri.toString())
    }

    private suspend fun buildSnapshotContentPackages(exportedAt: Long): List<ContentPackageDto> {
        val contentTree = contentRepository.getContentTree()
        val allNodes =
            contentTree.courses
                .flatMap { it.units }
                .flatMap { it.outcomes }
                .flatMap { it.nodes }
        return allNodes
            .groupBy { it.contentOrigin }
            .entries
            .sortedBy { it.key.name }
            .map { (origin, nodesForOrigin) ->
                val nodeIds = nodesForOrigin.map { it.nodeId }
                val items = contentRepository.getItemsForNodes(nodeIds, includeArchived = true).filter { it.contentOrigin == origin }
                val courseIds = nodesForOrigin.map { it.courseId }.toSet()
                val unitIds = nodesForOrigin.map { it.unitId }.toSet()
                val outcomeIds = nodesForOrigin.flatMap { it.outcomeIds }.toSet()
                val courses = contentTree.courses.map { it.course }.filter { it.courseId in courseIds }
                val units = contentTree.courses.flatMap { it.units }.map { it.unit }.filter { it.unitId in unitIds }
                val outcomes =
                    contentTree.courses
                        .flatMap { it.units }
                        .flatMap { it.outcomes }
                        .map { it.outcome }
                        .filter { it.outcomeId in outcomeIds }

                ContentPackageDto(
                    packageId = "snapshot_${origin.name.lowercase()}_$exportedAt",
                    schemaVersion = 1,
                    seedVersion = if (origin == ContentOrigin.DEMO) settingsRepository.getSettings().seedAppliedVersion else null,
                    generatedAt = exportedAt,
                    contentHash = null,
                    origin = origin,
                    courses = courses.map { it.asDto() },
                    units = units.map { it.asDto() },
                    outcomes = outcomes.map { it.asDto() },
                    nodes = nodesForOrigin.map { it.asDto() },
                    items = items.map { it.asDto() },
                )
            }
    }

    private suspend fun restoreUserState(userStatePackage: UserStatePackageDto) {
        progressRepository.clearAllProgress()
        userStatePackage.nodeStates.forEach { nodeState ->
            progressRepository.upsertNodeState(nodeState.asDomain())
        }
        userStatePackage.itemStates.forEach { itemState ->
            progressRepository.upsertItemState(itemState.asDomain())
        }
        userStatePackage.itemOverrides.forEach { overrideDto ->
            val override = overrideDto.asDomain()
            contentRepository.setItemArchived(override.itemId, override.archived)
            if (override.stemOverride != null || override.correctAnswerOverride != null || override.optionsOverride != null) {
                contentRepository.updateItemManualEdit(
                    ManualItemEdit(
                        itemId = override.itemId,
                        stem = override.stemOverride ?: contentRepository.getItem(override.itemId, includeArchived = true)?.stem.orEmpty(),
                        correctAnswer = override.correctAnswerOverride ?: contentRepository.getItem(override.itemId, includeArchived = true)?.correctAnswer.orEmpty(),
                        options = override.optionsOverride,
                    ),
                )
            }
        }
        userStatePackage.nodeFormatStats
            .groupBy { it.nodeId }
            .values
            .forEach { stats ->
                progressRepository.upsertFormatStats(stats.map { it.asDomain() })
            }

        settingsRepository.updateSettings { userStatePackage.appSettings.asDomain() }
        socialGateRepository.replaceState(
            rules = userStatePackage.socialGateRules.map { it.asDomain() },
            dailyStates = userStatePackage.socialGateDailyStates.map { it.asDomain() },
            runtimeState = userStatePackage.socialGateRuntimeState?.asDomain() ?: SocialGateRuntimeState(),
        )

        val archivedIds = userStatePackage.archivedNodeIds.toSet()
        val currentNodes =
            contentRepository.getContentTree()
                .courses
                .flatMap { it.units }
                .flatMap { it.outcomes }
                .flatMap { it.nodes }
        currentNodes.forEach { node ->
            contentRepository.setArchivedCandidate(node.nodeId, node.nodeId in archivedIds)
        }

        val activeSessions = userStatePackage.normalizedActiveSessions().map { it.asDomain() }
        sessionRepository.clearAllActiveSessions()
        activeSessions.forEach { session ->
            sessionRepository.saveActiveSession(session)
        }
    }
}

private fun SyncSnapshotDto.normalizedContentPackages(): List<ContentPackageDto> =
    contentPackages.ifEmpty { contentPackage?.let(::listOf).orEmpty() }

private fun UserStatePackageDto.normalizedActiveSessions(): List<com.estudio.antiprocrastinacion.app.model.json.StudySessionDto> =
    activeSessions.ifEmpty { activeSession?.let(::listOf).orEmpty() }

private fun StudySession.isExpiredQuickSession(
    now: Long,
    zoneId: ZoneId,
): Boolean {
    if (mode != SessionMode.QUICK) {
        return false
    }
    val sessionDate = Instant.ofEpochMilli(startedAt).atZone(zoneId).toLocalDate()
    val currentDate = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
    return sessionDate != currentDate
}
