package com.estudio.antiprocrastinacion.app.domain.repository

import android.net.Uri
import com.estudio.antiprocrastinacion.app.model.content.ContentTree
import com.estudio.antiprocrastinacion.app.model.content.Item
import com.estudio.antiprocrastinacion.app.model.content.ItemOverride
import com.estudio.antiprocrastinacion.app.model.content.ManualItemEdit
import com.estudio.antiprocrastinacion.app.model.content.Node
import com.estudio.antiprocrastinacion.app.model.content.NodeDetail
import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.content.UnitModel
import com.estudio.antiprocrastinacion.app.model.event.AbandonEvent
import com.estudio.antiprocrastinacion.app.model.event.ImportEvent
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
import kotlinx.coroutines.flow.Flow

interface ContentRepository {
    suspend fun seedDemoIfNeeded()
    suspend fun importContentPackage(
        rawJson: String,
        sourceLabel: String,
        validationProfile: ImportValidationProfile = ImportValidationProfile.PEDAGOGICAL_NODE,
    ): ImportExecutionResult
    suspend fun importContentPackage(uri: Uri): ImportExecutionResult
    suspend fun prepareContentPackage(uri: Uri): ImportPreparationResult
    suspend fun getContentTree(): ContentTree
    suspend fun getUnits(): List<UnitModel>
    suspend fun getSchedulableNodes(): List<Node>
    suspend fun getSchedulableNodesByUnit(unitId: String): List<Node>
    suspend fun getNode(nodeId: String): Node?
    suspend fun getNodeDetail(nodeId: String, includeArchivedItems: Boolean = false): NodeDetail?
    suspend fun getItem(itemId: String, includeArchived: Boolean = false): Item?
    suspend fun getItemsForNode(nodeId: String, includeArchived: Boolean = false): List<Item>
    suspend fun getItemsForNodes(nodeIds: List<String>, includeArchived: Boolean = false): List<Item>
    suspend fun setArchivedCandidate(nodeId: String, archived: Boolean)
    suspend fun getAllItemOverrides(): List<ItemOverride> = emptyList()
    suspend fun setItemArchived(itemId: String, archived: Boolean) = Unit
    suspend fun updateItemManualEdit(edit: ManualItemEdit) = Unit
    suspend fun hasRealImportedContent(): Boolean
    suspend fun getContentPackage(rawJson: String): ContentPackageDto
}

interface ProgressRepository {
    suspend fun getNodeState(nodeId: String): NodeState?
    suspend fun getAllNodeStates(): List<NodeState>
    suspend fun getItemState(itemId: String): ItemState? = null
    suspend fun getItemStates(itemIds: List<String>): List<ItemState> = emptyList()
    suspend fun getAllItemStates(): List<ItemState> = emptyList()
    suspend fun getAllFormatStats(): List<NodeFormatStat>
    suspend fun upsertNodeState(nodeState: NodeState)
    suspend fun upsertItemState(itemState: ItemState) = Unit
    suspend fun upsertItemStates(itemStates: List<ItemState>) = Unit
    suspend fun getFormatStats(nodeId: String): List<NodeFormatStat>
    suspend fun upsertFormatStats(stats: List<NodeFormatStat>)
    suspend fun clearAllProgress()
    suspend fun resetNodeProgress(nodeId: String)
}

interface SessionRepository {
    suspend fun getActiveSession(mode: SessionMode): StudySession?
    suspend fun getActiveSessionById(sessionId: String): StudySession?
    suspend fun getAllActiveSessions(): List<StudySession>
    suspend fun saveActiveSession(session: StudySession)
    suspend fun clearActiveSession(mode: SessionMode)
    suspend fun clearAllActiveSessions()

    suspend fun getMostRecentActiveSession(): StudySession? =
        getAllActiveSessions().maxByOrNull(StudySession::lastInteractionAt)
}

interface EventRepository {
    suspend fun recordReviewEvent(event: ReviewEvent)
    suspend fun recordSessionEvent(event: SessionEvent)
    suspend fun recordAbandonEvent(event: AbandonEvent)
    suspend fun recordImportEvent(event: ImportEvent)
    suspend fun getRecentTopicIds(): List<String>
    suspend fun getAbandonEventsSince(since: Long): List<AbandonEvent>
    suspend fun getLatestImportEvent(packageId: String): ImportEvent?
    suspend fun getItemPerformance(itemIds: List<String>): Map<String, ItemPerformanceSummary> = emptyMap()
    suspend fun getHistoricalItemAttemptsBefore(itemIds: List<String>, before: Long): Map<String, Int> = emptyMap()
    suspend fun getSessionScheduledSuccessCount(sessionId: String, itemId: String): Int = 0
    suspend fun getPendingFailedScheduledItemCount(sessionId: String): Int = 0
}

data class ItemPerformanceSummary(
    val itemId: String,
    val attempts: Int,
    val successes: Int,
) {
    val successRatio: Double =
        if (attempts <= 0) {
            0.5
        } else {
            successes.toDouble() / attempts.toDouble()
        }
}

interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>
    suspend fun getSettings(): AppSettings
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings)
}

interface ImportValidator {
    suspend fun validate(
        contentPackage: ContentPackageDto,
        profile: ImportValidationProfile = ImportValidationProfile.PEDAGOGICAL_NODE,
    ): ValidationReport
}

interface ImportPreparationService {
    suspend fun prepare(
        rawJson: String,
        sourceLabel: String,
        validationProfile: ImportValidationProfile = ImportValidationProfile.PEDAGOGICAL_NODE,
    ): ImportPreparationResult
}

enum class ImportValidationProfile {
    PEDAGOGICAL_NODE,
    ADDITIVE_CONTENT_PACKAGE,
    REVIEWED_QUESTION_BANK,
}

enum class PreparedImportKind {
    CONTENT_PACKAGE,
    AUTHORING_DRAFT,
    INVALID,
}

data class ImportPreparationResult(
    val sourceLabel: String,
    val kind: PreparedImportKind,
    val packageId: String,
    val schemaVersion: Int,
    val contentPackage: ContentPackageDto?,
    val contentPackageJson: String?,
    val report: ValidationReport,
    val validationProfile: ImportValidationProfile = ImportValidationProfile.PEDAGOGICAL_NODE,
) {
    val canImport: Boolean = contentPackage != null && report.canImport
}

interface SnapshotRepository {
    suspend fun exportSyncSnapshot(): SyncSnapshotDto
    suspend fun exportSyncSnapshot(uri: Uri): SnapshotExecutionResult
    suspend fun importSyncSnapshot(rawJson: String, sourceLabel: String): SnapshotExecutionResult
    suspend fun importSyncSnapshot(uri: Uri): SnapshotExecutionResult
    suspend fun exportUserStatePackage(): UserStatePackageDto
    suspend fun exportUserStatePackage(uri: Uri): SnapshotExecutionResult
    suspend fun importUserStatePackage(rawJson: String, sourceLabel: String): SnapshotExecutionResult
    suspend fun importUserStatePackage(uri: Uri): SnapshotExecutionResult
}

interface SocialGateRepository {
    fun observeRules(): Flow<List<SocialGateRule>>
    suspend fun getRules(): List<SocialGateRule>
    suspend fun getRule(packageName: String): SocialGateRule?
    suspend fun upsertRule(rule: SocialGateRule)
    suspend fun getDailyStates(): List<SocialGateDailyState>
    suspend fun incrementSolvedCount(packageName: String, localDate: String, solvedAt: Long)
    suspend fun getRuntimeState(): SocialGateRuntimeState
    suspend fun updateRuntimeState(transform: (SocialGateRuntimeState) -> SocialGateRuntimeState)
    suspend fun replaceState(
        rules: List<SocialGateRule>,
        dailyStates: List<SocialGateDailyState>,
        runtimeState: SocialGateRuntimeState,
    )
}

data class ValidationMessage(
    val code: String,
    val message: String,
    val path: String? = null,
    val expected: String? = null,
    val actual: String? = null,
    val hint: String? = null,
)

data class ValidationReport(
    val structuralErrors: List<ValidationMessage>,
    val authoringWarnings: List<ValidationMessage>,
) {
    val canImport: Boolean = structuralErrors.isEmpty()
}

data class ImportExecutionResult(
    val packageId: String,
    val imported: Boolean,
    val report: ValidationReport,
)

data class SnapshotExecutionResult(
    val sourceLabel: String,
    val success: Boolean,
    val message: String,
    val contentPackageCount: Int,
    val nodeStateCount: Int,
    val formatStatCount: Int,
    val archivedNodeCount: Int,
    val restoredActiveSession: Boolean,
)
