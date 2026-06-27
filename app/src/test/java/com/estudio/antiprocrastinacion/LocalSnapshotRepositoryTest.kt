package com.estudio.antiprocrastinacion

import com.estudio.antiprocrastinacion.app.data.local.db.asDto
import com.estudio.antiprocrastinacion.app.data.local.db.LocalSnapshotRepository
import com.estudio.antiprocrastinacion.app.domain.repository.ContentRepository
import com.estudio.antiprocrastinacion.app.domain.repository.ImportExecutionResult
import com.estudio.antiprocrastinacion.app.domain.repository.ImportValidationProfile
import com.estudio.antiprocrastinacion.app.domain.repository.ProgressRepository
import com.estudio.antiprocrastinacion.app.domain.repository.SessionRepository
import com.estudio.antiprocrastinacion.app.domain.repository.SettingsRepository
import com.estudio.antiprocrastinacion.app.domain.repository.SocialGateRepository
import com.estudio.antiprocrastinacion.app.domain.repository.ValidationReport
import com.estudio.antiprocrastinacion.app.model.content.ContentOrigin
import com.estudio.antiprocrastinacion.app.model.content.ContentTree
import com.estudio.antiprocrastinacion.app.model.content.Course
import com.estudio.antiprocrastinacion.app.model.content.CourseWithUnits
import com.estudio.antiprocrastinacion.app.model.content.FacetType
import com.estudio.antiprocrastinacion.app.model.content.Item
import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.ItemRole
import com.estudio.antiprocrastinacion.app.model.content.Node
import com.estudio.antiprocrastinacion.app.model.content.NodeDetail
import com.estudio.antiprocrastinacion.app.model.content.NodeType
import com.estudio.antiprocrastinacion.app.model.content.Outcome
import com.estudio.antiprocrastinacion.app.model.content.OutcomeWithNodes
import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.content.Surface
import com.estudio.antiprocrastinacion.app.model.content.UnitModel
import com.estudio.antiprocrastinacion.app.model.content.UnitWithOutcomes
import com.estudio.antiprocrastinacion.app.model.json.ContentPackageDto
import com.estudio.antiprocrastinacion.app.model.json.SyncSnapshotDto
import com.estudio.antiprocrastinacion.app.model.json.UserStatePackageDto
import com.estudio.antiprocrastinacion.app.model.state.AppSettings
import com.estudio.antiprocrastinacion.app.model.state.NodeFormatStat
import com.estudio.antiprocrastinacion.app.model.state.NodeState
import com.estudio.antiprocrastinacion.app.model.state.SocialGateDailyState
import com.estudio.antiprocrastinacion.app.model.state.SocialGateRule
import com.estudio.antiprocrastinacion.app.model.state.SocialGateRuntimePhase
import com.estudio.antiprocrastinacion.app.model.state.SocialGateRuntimeState
import com.estudio.antiprocrastinacion.app.model.state.StudySession
import com.estudio.antiprocrastinacion.app.ui.common.AppJson
import com.estudio.antiprocrastinacion.app.ui.common.TimeProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Test

class LocalSnapshotRepositoryTest {
    @Test
    fun `export user state package preserves local state without content`() = runTest {
        val contentRepository = FakeSnapshotContentRepository(sampleSnapshotTree())
        val progressRepository =
            FakeSnapshotProgressRepository(
                nodeStates = listOf(sampleNodeState("node-imported")),
                formatStats = listOf(sampleFormatStat("node-imported")),
            )
        val sessionRepository = FakeSnapshotSessionRepository(sampleStudySession())
        val settingsRepository = FakeSnapshotSettingsRepository(sampleSettings())
        val socialGateRepository = FakeSnapshotSocialGateRepository(sampleSocialGateRules(), sampleSocialGateDailyStates(), sampleSocialGateRuntimeState())
        val repository =
            LocalSnapshotRepository(
                context = null,
                contentRepository = contentRepository,
                progressRepository = progressRepository,
                sessionRepository = sessionRepository,
                settingsRepository = settingsRepository,
                socialGateRepository = socialGateRepository,
                timeProvider = FixedTimeProvider(1234L),
            )

        val userStatePackage = repository.exportUserStatePackage()

        assertThat(userStatePackage.exportedAt).isEqualTo(1234L)
        assertThat(userStatePackage.nodeStates).hasSize(1)
        assertThat(userStatePackage.nodeFormatStats).hasSize(1)
        assertThat(userStatePackage.archivedNodeIds).containsExactly("node-demo")
        assertThat(userStatePackage.activeSession?.sessionId).isEqualTo("session-1")
        assertThat(userStatePackage.activeSessions.map { it.sessionId }).containsExactly("session-1")
        assertThat(userStatePackage.appSettings.seedAppliedVersion).isEqualTo("demo-v1")
        assertThat(userStatePackage.socialGateRules.map { it.packageName }).containsExactly("com.instagram.android")
        assertThat(userStatePackage.socialGateRules.single().windowStartMinutes).isEqualTo(6 * 60)
        assertThat(userStatePackage.socialGateRules.single().windowEndMinutes).isEqualTo(22 * 60)
        assertThat(userStatePackage.socialGateDailyStates.single().solvedCount).isEqualTo(2)
        assertThat(userStatePackage.socialGateRuntimeState?.phase).isEqualTo("ACTIVE_GATE")
        assertThat(userStatePackage.socialGateRuntimeState?.activeGateSessionId).isEqualTo("session-1")
        assertThat(userStatePackage.socialGateRuntimeState?.gateUnlockBaselineCorrectCount).isEqualTo(2)
        assertThat(userStatePackage.socialGateRuntimeState?.gateUnlockRequiredCorrectAnswers).isEqualTo(3)
    }

    @Test
    fun `export snapshot groups content by origin and preserves local state`() = runTest {
        val contentRepository = FakeSnapshotContentRepository(sampleSnapshotTree())
        val progressRepository =
            FakeSnapshotProgressRepository(
                nodeStates = listOf(sampleNodeState("node-imported")),
                formatStats = listOf(sampleFormatStat("node-imported")),
            )
        val sessionRepository = FakeSnapshotSessionRepository(sampleStudySession())
        val settingsRepository = FakeSnapshotSettingsRepository(sampleSettings())
        val socialGateRepository = FakeSnapshotSocialGateRepository(sampleSocialGateRules(), sampleSocialGateDailyStates(), sampleSocialGateRuntimeState())
        val repository =
            LocalSnapshotRepository(
                context = null,
                contentRepository = contentRepository,
                progressRepository = progressRepository,
                sessionRepository = sessionRepository,
                settingsRepository = settingsRepository,
                socialGateRepository = socialGateRepository,
                timeProvider = FixedTimeProvider(1234L),
            )

        val snapshot = repository.exportSyncSnapshot()

        assertThat(snapshot.snapshotVersion).isEqualTo(1)
        assertThat(snapshot.contentPackages).hasSize(2)
        assertThat(snapshot.contentPackages.map { it.origin }).containsExactly(ContentOrigin.DEMO, ContentOrigin.IMPORTED)
        assertThat(snapshot.userStatePackage.nodeStates).hasSize(1)
        assertThat(snapshot.userStatePackage.nodeFormatStats).hasSize(1)
        assertThat(snapshot.userStatePackage.archivedNodeIds).containsExactly("node-demo")
        assertThat(snapshot.userStatePackage.activeSession?.sessionId).isEqualTo("session-1")
        assertThat(snapshot.userStatePackage.activeSessions.map { it.sessionId }).containsExactly("session-1")
        assertThat(snapshot.userStatePackage.appSettings.seedAppliedVersion).isEqualTo("demo-v1")
        assertThat(snapshot.userStatePackage.socialGateRules.map { it.packageName }).containsExactly("com.instagram.android")
        assertThat(snapshot.userStatePackage.socialGateRules.single().windowStartMinutes).isEqualTo(6 * 60)
        assertThat(snapshot.userStatePackage.socialGateRules.single().windowEndMinutes).isEqualTo(22 * 60)
        assertThat(snapshot.userStatePackage.socialGateDailyStates.single().solvedCount).isEqualTo(2)
    }

    @Test
    fun `import snapshot restores progress archived flags and active session exactly`() = runTest {
        val importedTree = sampleSnapshotTree()
        val contentRepository = FakeSnapshotContentRepository(importedTree)
        val progressRepository = FakeSnapshotProgressRepository()
        val sessionRepository = FakeSnapshotSessionRepository()
        val settingsRepository = FakeSnapshotSettingsRepository(sampleSettings())
        val socialGateRepository = FakeSnapshotSocialGateRepository()
        val repository =
            LocalSnapshotRepository(
                context = null,
                contentRepository = contentRepository,
                progressRepository = progressRepository,
                sessionRepository = sessionRepository,
                settingsRepository = settingsRepository,
                socialGateRepository = socialGateRepository,
                timeProvider = FixedTimeProvider(1234L),
            )

        val rawJson = AppJson.encodeToString(sampleSyncSnapshot())
        val result = repository.importSyncSnapshot(rawJson, "snapshot:test")

        assertThat(result.success).isTrue()
        assertThat(contentRepository.importedPackageIds).containsExactly("demo-pack", "imported-pack").inOrder()
        assertThat(progressRepository.clearedAllProgress).isTrue()
        assertThat(progressRepository.upsertedNodeStates.map { it.nodeId }).containsExactly("node-imported")
        assertThat(progressRepository.upsertedFormatStats.map { it.nodeId }).containsExactly("node-imported")
        assertThat(contentRepository.archiveOperations).containsExactly(
            "node-demo:true",
            "node-imported:false",
        )
        assertThat(sessionRepository.savedSession?.sessionId).isEqualTo("session-1")
        assertThat(settingsRepository.current.quickSessionTargetDefault).isEqualTo(7)
        assertThat(socialGateRepository.rules.map { it.packageName }).containsExactly("com.instagram.android")
        assertThat(socialGateRepository.rules.single().windowStartMinutes).isEqualTo(6 * 60)
        assertThat(socialGateRepository.rules.single().windowEndMinutes).isEqualTo(22 * 60)
        assertThat(socialGateRepository.dailyStates.single().solvedCount).isEqualTo(2)
        assertThat(socialGateRepository.runtimeState.phase).isEqualTo(SocialGateRuntimePhase.ACTIVE_GATE)
        assertThat(socialGateRepository.runtimeState.activeGateSessionId).isEqualTo("session-1")
        assertThat(socialGateRepository.runtimeState.gateUnlockBaselineCorrectCount).isEqualTo(2)
        assertThat(socialGateRepository.runtimeState.gateUnlockRequiredCorrectAnswers).isEqualTo(3)
    }

    @Test
    fun `import user state package restores progress archived flags and active session without importing content`() = runTest {
        val contentRepository = FakeSnapshotContentRepository(sampleSnapshotTree())
        val progressRepository = FakeSnapshotProgressRepository()
        val sessionRepository = FakeSnapshotSessionRepository()
        val settingsRepository = FakeSnapshotSettingsRepository(sampleSettings())
        val socialGateRepository = FakeSnapshotSocialGateRepository()
        val repository =
            LocalSnapshotRepository(
                context = null,
                contentRepository = contentRepository,
                progressRepository = progressRepository,
                sessionRepository = sessionRepository,
                settingsRepository = settingsRepository,
                socialGateRepository = socialGateRepository,
                timeProvider = FixedTimeProvider(1234L),
            )

        val rawJson = AppJson.encodeToString(sampleUserStatePackage())
        val result = repository.importUserStatePackage(rawJson, "user-state:test")

        assertThat(result.success).isTrue()
        assertThat(contentRepository.importedPackageIds).isEmpty()
        assertThat(progressRepository.clearedAllProgress).isTrue()
        assertThat(progressRepository.upsertedNodeStates.map { it.nodeId }).containsExactly("node-imported")
        assertThat(progressRepository.upsertedFormatStats.map { it.nodeId }).containsExactly("node-imported")
        assertThat(contentRepository.archiveOperations).containsExactly(
            "node-demo:true",
            "node-imported:false",
        )
        assertThat(sessionRepository.savedSession?.sessionId).isEqualTo("session-1")
        assertThat(settingsRepository.current.quickSessionTargetDefault).isEqualTo(7)
        assertThat(socialGateRepository.rules.map { it.packageName }).containsExactly("com.instagram.android")
        assertThat(socialGateRepository.rules.single().windowStartMinutes).isEqualTo(6 * 60)
        assertThat(socialGateRepository.rules.single().windowEndMinutes).isEqualTo(22 * 60)
        assertThat(socialGateRepository.dailyStates.single().solvedCount).isEqualTo(2)
        assertThat(socialGateRepository.runtimeState.phase).isEqualTo(SocialGateRuntimePhase.ACTIVE_GATE)
        assertThat(socialGateRepository.runtimeState.activeGateSessionId).isEqualTo("session-1")
        assertThat(socialGateRepository.runtimeState.gateUnlockBaselineCorrectCount).isEqualTo(2)
        assertThat(socialGateRepository.runtimeState.gateUnlockRequiredCorrectAnswers).isEqualTo(3)
    }
}

private class FakeSnapshotContentRepository(
    private var tree: ContentTree,
) : ContentRepository {
    val importedPackageIds = mutableListOf<String>()
    val archiveOperations = mutableListOf<String>()
    private val itemsByNodeId =
        sampleSnapshotItems().groupBy { it.nodeId }

    override suspend fun seedDemoIfNeeded() = Unit

    override suspend fun importContentPackage(
        rawJson: String,
        sourceLabel: String,
        validationProfile: ImportValidationProfile,
    ): ImportExecutionResult {
        val contentPackage = AppJson.decodeFromString<ContentPackageDto>(rawJson)
        importedPackageIds += contentPackage.packageId
        return ImportExecutionResult(contentPackage.packageId, true, ValidationReport(emptyList(), emptyList()))
    }

    override suspend fun importContentPackage(uri: android.net.Uri): ImportExecutionResult =
        error("Not used in unit test")

    override suspend fun prepareContentPackage(uri: android.net.Uri) =
        error("Not used in unit test")

    override suspend fun getContentTree(): ContentTree = tree
    override suspend fun getUnits(): List<UnitModel> = tree.courses.flatMap { it.units }.map { it.unit }
    override suspend fun getSchedulableNodes(): List<Node> = emptyList()
    override suspend fun getSchedulableNodesByUnit(unitId: String): List<Node> = emptyList()
    override suspend fun getNode(nodeId: String): Node? = null
    override suspend fun getNodeDetail(nodeId: String, includeArchivedItems: Boolean): NodeDetail? = null
    override suspend fun getItem(itemId: String, includeArchived: Boolean): Item? = null
    override suspend fun getItemsForNode(nodeId: String, includeArchived: Boolean): List<Item> = itemsByNodeId[nodeId].orEmpty()
    override suspend fun getItemsForNodes(nodeIds: List<String>, includeArchived: Boolean): List<Item> = nodeIds.flatMap { itemsByNodeId[it].orEmpty() }

    override suspend fun setArchivedCandidate(nodeId: String, archived: Boolean) {
        archiveOperations += "$nodeId:$archived"
    }

    override suspend fun hasRealImportedContent(): Boolean = true
    override suspend fun getContentPackage(rawJson: String): ContentPackageDto = AppJson.decodeFromString(rawJson)
}

private class FakeSnapshotProgressRepository(
    private val nodeStates: List<NodeState> = emptyList(),
    private val formatStats: List<NodeFormatStat> = emptyList(),
) : ProgressRepository {
    var clearedAllProgress = false
    val upsertedNodeStates = mutableListOf<NodeState>()
    val upsertedFormatStats = mutableListOf<NodeFormatStat>()

    override suspend fun getNodeState(nodeId: String): NodeState? = nodeStates.firstOrNull { it.nodeId == nodeId }
    override suspend fun getAllNodeStates(): List<NodeState> = nodeStates
    override suspend fun getAllFormatStats(): List<NodeFormatStat> = formatStats
    override suspend fun upsertNodeState(nodeState: NodeState) {
        upsertedNodeStates += nodeState
    }

    override suspend fun getFormatStats(nodeId: String): List<NodeFormatStat> = formatStats.filter { it.nodeId == nodeId }

    override suspend fun upsertFormatStats(stats: List<NodeFormatStat>) {
        upsertedFormatStats += stats
    }

    override suspend fun clearAllProgress() {
        clearedAllProgress = true
    }

    override suspend fun resetNodeProgress(nodeId: String) = Unit
}

private class FakeSnapshotSessionRepository(
    private val activeSession: StudySession? = null,
) : SessionRepository {
    var savedSession: StudySession? = null
    var cleared = false

    override suspend fun getActiveSession(mode: SessionMode): StudySession? =
        activeSession?.takeIf { it.mode == mode }

    override suspend fun getActiveSessionById(sessionId: String): StudySession? =
        activeSession?.takeIf { it.sessionId == sessionId }

    override suspend fun getAllActiveSessions(): List<StudySession> = listOfNotNull(activeSession)

    override suspend fun saveActiveSession(session: StudySession) {
        savedSession = session
    }

    override suspend fun clearActiveSession(mode: SessionMode) {
        cleared = true
    }

    override suspend fun clearAllActiveSessions() {
        cleared = true
    }
}

private class FakeSnapshotSettingsRepository(
    var current: AppSettings,
) : SettingsRepository {
    override fun observeSettings(): Flow<AppSettings> = flowOf(current)
    override suspend fun getSettings(): AppSettings = current
    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        current = transform(current)
    }
}

private class FakeSnapshotSocialGateRepository(
    initialRules: List<SocialGateRule> = emptyList(),
    initialDailyStates: List<SocialGateDailyState> = emptyList(),
    initialRuntimeState: SocialGateRuntimeState = SocialGateRuntimeState(),
) : SocialGateRepository {
    var rules: List<SocialGateRule> = initialRules
    var dailyStates: List<SocialGateDailyState> = initialDailyStates
    var runtimeState: SocialGateRuntimeState = initialRuntimeState

    override fun observeRules(): Flow<List<SocialGateRule>> = flowOf(rules)

    override suspend fun getRules(): List<SocialGateRule> = rules

    override suspend fun getRule(packageName: String): SocialGateRule? = rules.firstOrNull { it.packageName == packageName }

    override suspend fun upsertRule(rule: SocialGateRule) {
        rules =
            rules
                .filterNot { it.packageName == rule.packageName }
                .plus(rule)
                .sortedBy { it.packageName }
    }

    override suspend fun getDailyStates(): List<SocialGateDailyState> = dailyStates

    override suspend fun incrementSolvedCount(packageName: String, localDate: String, solvedAt: Long) {
        val current = dailyStates.firstOrNull { it.packageName == packageName && it.localDate == localDate }
        dailyStates =
            dailyStates
                .filterNot { it.packageName == packageName && it.localDate == localDate }
                .plus(
                    SocialGateDailyState(
                        packageName = packageName,
                        localDate = localDate,
                        solvedCount = (current?.solvedCount ?: 0) + 1,
                        lastSolvedAt = solvedAt,
                    ),
                )
                .sortedBy { it.packageName + it.localDate }
    }

    override suspend fun getRuntimeState(): SocialGateRuntimeState = runtimeState

    override suspend fun updateRuntimeState(transform: (SocialGateRuntimeState) -> SocialGateRuntimeState) {
        runtimeState = transform(runtimeState)
    }

    override suspend fun replaceState(
        rules: List<SocialGateRule>,
        dailyStates: List<SocialGateDailyState>,
        runtimeState: SocialGateRuntimeState,
    ) {
        this.rules = rules
        this.dailyStates = dailyStates
        this.runtimeState = runtimeState
    }
}

private class FixedTimeProvider(
    private val now: Long,
) : TimeProvider {
    override fun now(): Long = now
}

private fun sampleSyncSnapshot(): SyncSnapshotDto =
    SyncSnapshotDto(
        snapshotVersion = 1,
        exportedAt = 1234L,
        contentPackages =
            listOf(
                sampleContentPackage("demo-pack", ContentOrigin.DEMO, listOf(sampleNode("node-demo", ContentOrigin.DEMO, archived = true))),
                sampleContentPackage("imported-pack", ContentOrigin.IMPORTED, listOf(sampleNode("node-imported", ContentOrigin.IMPORTED, archived = false))),
            ),
        userStatePackage = sampleUserStatePackage(),
    )

private fun sampleUserStatePackage(): UserStatePackageDto =
    UserStatePackageDto(
        exportedAt = 1234L,
        nodeStates = listOf(sampleNodeState("node-imported").asDto()),
        nodeFormatStats = listOf(sampleFormatStat("node-imported").asDto()),
        archivedNodeIds = listOf("node-demo"),
        activeSession = sampleStudySession().asDto(),
        activeSessions = listOf(sampleStudySession().asDto()),
        appSettings = sampleSettings().copy(quickSessionTargetDefault = 7).asDto(),
        socialGateRules = sampleSocialGateRules().map { it.asDto() },
        socialGateDailyStates = sampleSocialGateDailyStates().map { it.asDto() },
        socialGateRuntimeState = sampleSocialGateRuntimeState().asDto(),
    )

private fun sampleSnapshotTree(): ContentTree =
    ContentTree(
        courses =
            listOf(
                CourseWithUnits(
                    course = Course("course-demo", "Demo", null, 1, 1L),
                    units =
                        listOf(
                            UnitWithOutcomes(
                                unit = UnitModel("unit-demo", "course-demo", "Demo Unit", null, 1, 1, 1L),
                                outcomes =
                                    listOf(
                                        OutcomeWithNodes(
                                            outcome = Outcome("outcome-demo", "unit-demo", "Demo Outcome", null, 1, 1L),
                                            nodes = listOf(sampleNode("node-demo", ContentOrigin.DEMO, archived = true)),
                                        ),
                                    ),
                            ),
                        ),
                ),
                CourseWithUnits(
                    course = Course("course-imported", "Imported", null, 1, 1L),
                    units =
                        listOf(
                            UnitWithOutcomes(
                                unit = UnitModel("unit-imported", "course-imported", "Imported Unit", null, 1, 1, 1L),
                                outcomes =
                                    listOf(
                                        OutcomeWithNodes(
                                            outcome = Outcome("outcome-imported", "unit-imported", "Imported Outcome", null, 1, 1L),
                                            nodes = listOf(sampleNode("node-imported", ContentOrigin.IMPORTED, archived = false)),
                                        ),
                                    ),
                            ),
                        ),
                ),
            ),
    )

private fun sampleSnapshotItems(): List<Item> =
    listOf(
        sampleItem("item-demo", "node-demo", ContentOrigin.DEMO),
        sampleItem("item-imported", "node-imported", ContentOrigin.IMPORTED),
    )

private fun sampleContentPackage(
    packageId: String,
    origin: ContentOrigin,
    nodes: List<Node>,
): ContentPackageDto {
    val course = if (origin == ContentOrigin.DEMO) Course("course-demo", "Demo", null, 1, 1L) else Course("course-imported", "Imported", null, 1, 1L)
    val unit = if (origin == ContentOrigin.DEMO) UnitModel("unit-demo", "course-demo", "Demo Unit", null, 1, 1, 1L) else UnitModel("unit-imported", "course-imported", "Imported Unit", null, 1, 1, 1L)
    val outcome = if (origin == ContentOrigin.DEMO) Outcome("outcome-demo", "unit-demo", "Demo Outcome", null, 1, 1L) else Outcome("outcome-imported", "unit-imported", "Imported Outcome", null, 1, 1L)
    val items = nodes.mapIndexed { index, node -> sampleItem("item-$packageId-$index", node.nodeId, origin) }
    return ContentPackageDto(
        packageId = packageId,
        schemaVersion = 1,
        generatedAt = 1234L,
        contentHash = null,
        origin = origin,
        courses = listOf(course.asDto()),
        units = listOf(unit.asDto()),
        outcomes = listOf(outcome.asDto()),
        nodes = nodes.map { it.asDto() },
        items = items.map { it.asDto() },
    )
}

private fun sampleNode(
    nodeId: String,
    origin: ContentOrigin,
    archived: Boolean,
): Node =
    Node(
        nodeId = nodeId,
        courseId = if (origin == ContentOrigin.DEMO) "course-demo" else "course-imported",
        unitId = if (origin == ContentOrigin.DEMO) "unit-demo" else "unit-imported",
        outcomeIds = listOf(if (origin == ContentOrigin.DEMO) "outcome-demo" else "outcome-imported"),
        title = nodeId,
        coreClaim = "claim",
        type = NodeType.CONCEPT,
        weightExam = 0.8,
        prerequisites = emptyList(),
        facets = listOf(FacetType.DEFINICION_FUNCIONAL),
        mustKnow = listOf("must"),
        commonErrors = listOf("error"),
        minimumMasteryDefinition = "mastery",
        surfaceEasyReady = true,
        surfaceEasyItemCount = 4,
        sourceRefs = listOf("manual"),
        version = 1,
        updatedAt = 1L,
        archivedCandidate = archived,
        contentOrigin = origin,
    )

private fun sampleItem(
    itemId: String,
    nodeId: String,
    origin: ContentOrigin,
): Item =
    Item(
        itemId = itemId,
        nodeId = nodeId,
        facet = FacetType.DEFINICION_FUNCIONAL,
        format = ItemFormat.MULTIPLE_CHOICE,
        frictionLevel = 1,
        difficultySeed = 0.2,
        itemRole = ItemRole.CORE,
        allowedSurfaces = listOf(Surface.IN_APP_QUICK),
        cooldownHours = 12.0,
        stem = "stem",
        correctAnswer = "answer",
        feedbackShort = "feedback",
        coversMustKnow = listOf("must"),
        variantGroupId = null,
        rescueGroupId = null,
        nodeComplexity = null,
        facetComplexity = null,
        distractorSimilarity = null,
        prerequisiteDepth = null,
        targetsErrorIds = emptyList(),
        commonErrorSignals = emptyList(),
        options = emptyList(),
        version = 1,
        updatedAt = 1L,
        sourceRefs = listOf("manual"),
        contentOrigin = origin,
    )

private fun sampleNodeState(nodeId: String): NodeState =
    NodeState(nodeId = nodeId, memoryScore = 0.7, timesSeen = 5, timesCorrectFirstTry = 4)

private fun sampleFormatStat(nodeId: String): NodeFormatStat =
    NodeFormatStat(nodeId = nodeId, format = ItemFormat.MULTIPLE_CHOICE, attempts = 5, successes = 4, avgLatencyMs = 1200.0)

private fun sampleStudySession(): StudySession =
    StudySession(
        sessionId = "session-1",
        mode = SessionMode.QUICK,
        surface = Surface.IN_APP_QUICK,
        packetId = "packet-1",
        topicUnitId = "unit-imported",
        currentTopicTitle = "Imported Unit",
        queueItemIds = listOf("item-imported"),
        rescueQueueItemIds = emptyList(),
        goalCorrectCount = 4,
        currentNodeId = "node-imported",
        currentItemId = "item-imported",
        currentAttemptIndex = 1,
        correctCount = 2,
        stepIndex = 2,
        startedAt = 10L,
        lastInteractionAt = 20L,
        isMicroPromptActive = false,
        isExitArmed = false,
        exitArmedUntil = null,
    )

private fun sampleSettings(): AppSettings =
    AppSettings(
        quickSessionTargetDefault = 4,
        deepSessionQuestionTarget = 12,
        backExitWindowSeconds = 8,
        seedAppliedVersion = "demo-v1",
        seedPackageId = "demo-pack",
        demoContentEnabled = true,
    )

private fun sampleSocialGateRules(): List<SocialGateRule> =
    listOf(
        SocialGateRule(
            packageName = "com.instagram.android",
            displayName = "Instagram",
            enabled = true,
            maxTriggersPerDay = 3,
            requiredCorrectAnswers = 2,
            windowStartMinutes = 6 * 60,
            windowEndMinutes = 22 * 60,
        ),
    )

private fun sampleSocialGateDailyStates(): List<SocialGateDailyState> =
    listOf(
        SocialGateDailyState(
            packageName = "com.instagram.android",
            localDate = "2026-04-14",
            solvedCount = 2,
            lastSolvedAt = 1234L,
        ),
    )

private fun sampleSocialGateRuntimeState(): SocialGateRuntimeState =
    SocialGateRuntimeState(
        phase = SocialGateRuntimePhase.ACTIVE_GATE,
        targetPackageName = "com.instagram.android",
        activeGateSessionId = "session-1",
        gateUnlockBaselineCorrectCount = 2,
        gateUnlockRequiredCorrectAnswers = 3,
        lastForegroundPackageName = "com.instagram.android",
        lastForegroundChangedAt = 1234L,
    )
