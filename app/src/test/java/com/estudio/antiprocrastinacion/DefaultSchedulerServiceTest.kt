package com.estudio.antiprocrastinacion

import android.net.Uri
import com.estudio.antiprocrastinacion.app.domain.repository.ContentRepository
import com.estudio.antiprocrastinacion.app.domain.repository.EventRepository
import com.estudio.antiprocrastinacion.app.domain.repository.ImportExecutionResult
import com.estudio.antiprocrastinacion.app.domain.repository.ImportValidationProfile
import com.estudio.antiprocrastinacion.app.domain.repository.ItemPerformanceSummary
import com.estudio.antiprocrastinacion.app.domain.repository.ProgressRepository
import com.estudio.antiprocrastinacion.app.domain.repository.SettingsRepository
import com.estudio.antiprocrastinacion.app.domain.repository.ValidationReport
import com.estudio.antiprocrastinacion.app.domain.scheduler.DefaultSchedulerService
import com.estudio.antiprocrastinacion.app.model.content.ContentOrigin
import com.estudio.antiprocrastinacion.app.model.content.ContentTree
import com.estudio.antiprocrastinacion.app.model.content.Course
import com.estudio.antiprocrastinacion.app.model.content.CourseWithUnits
import com.estudio.antiprocrastinacion.app.model.content.FacetType
import com.estudio.antiprocrastinacion.app.model.content.Item
import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.ItemOption
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
import com.estudio.antiprocrastinacion.app.model.event.AbandonEvent
import com.estudio.antiprocrastinacion.app.model.event.AbandonReason
import com.estudio.antiprocrastinacion.app.model.event.ImportEvent
import com.estudio.antiprocrastinacion.app.model.event.ReviewEvent
import com.estudio.antiprocrastinacion.app.model.event.SessionEvent
import com.estudio.antiprocrastinacion.app.model.state.AppSettings
import com.estudio.antiprocrastinacion.app.model.state.ItemState
import com.estudio.antiprocrastinacion.app.model.state.NodeFormatStat
import com.estudio.antiprocrastinacion.app.model.state.NodeState
import com.estudio.antiprocrastinacion.app.model.state.SessionPromptKind
import com.estudio.antiprocrastinacion.app.model.state.StudySession
import com.estudio.antiprocrastinacion.app.ui.common.IdProvider
import com.estudio.antiprocrastinacion.app.ui.common.TimeProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultSchedulerServiceTest {
    @Test
    fun `quick packet starts with friction one and bans boss integration early`() = runTest {
        val unit = UnitModel("u1", "c1", "Unidad", null, 1, 1, 1L)
        val node =
            Node(
                nodeId = "n1",
                courseId = "c1",
                unitId = "u1",
                outcomeIds = listOf("o1"),
                title = "Nodo",
                coreClaim = "Claim",
                type = NodeType.CONCEPT,
                weightExam = 0.9,
                prerequisites = emptyList(),
                facets = listOf(FacetType.DEFINICION_FUNCIONAL),
                mustKnow = listOf("mk"),
                commonErrors = listOf("ce"),
                minimumMasteryDefinition = "mm",
                surfaceEasyReady = true,
                surfaceEasyItemCount = 4,
                sourceRefs = emptyList(),
                version = 1,
                updatedAt = 1L,
                archivedCandidate = false,
                contentOrigin = ContentOrigin.IMPORTED,
            )
        val items =
            listOf(
                testItem("easy-core", node.nodeId, 1, ItemRole.CORE),
                testItem("easy-variant", node.nodeId, 1, ItemRole.VARIANT),
                testItem("boss", node.nodeId, 3, ItemRole.BOSS),
                testItem("integration", node.nodeId, 3, ItemRole.INTEGRATION),
            )
        val scheduler =
            DefaultSchedulerService(
                contentRepository = FakeContentRepository(nodes = listOf(node), items = items, units = listOf(unit)),
                progressRepository = FakeProgressRepository(),
                eventRepository = FakeEventRepository(),
                settingsRepository = FakeSettingsRepository(),
                idProvider = object : IdProvider { override fun newId(): String = "id" },
                timeProvider = object : TimeProvider { override fun now(): Long = 10_000L },
            )

        val packet = scheduler.buildQuickPacket()

        assertThat(packet).isNotNull()
        assertThat(packet!!.initialItemIds.first()).isEqualTo("easy-core")
        assertThat(packet.initialItemIds.take(2)).doesNotContain("boss")
        assertThat(packet.initialItemIds.take(2)).doesNotContain("integration")
    }

    @Test
    fun `in-app quick review includes open reveal cards after the easy ones`() = runTest {
        val unit = UnitModel("u1", "c1", "Unidad", null, 1, 1, 1L)
        val node = testNode("n1", "u1", weightExam = 0.9)
        val openSurfaces = listOf(Surface.IN_APP_QUICK, Surface.IN_APP_DEEP)
        val items =
            listOf(
                testItem("easy-1", node.nodeId, 1, ItemRole.CORE),
                testItem("easy-2", node.nodeId, 1, ItemRole.CORE),
                testItem("easy-3", node.nodeId, 1, ItemRole.CORE),
                testItem("easy-4", node.nodeId, 1, ItemRole.CORE),
                testItem("open-1", node.nodeId, 3, ItemRole.CORE, surfaces = openSurfaces, format = ItemFormat.ONE_SENTENCE_EXPLANATION),
                testItem("open-2", node.nodeId, 3, ItemRole.CORE, surfaces = openSurfaces, format = ItemFormat.ONE_SENTENCE_EXPLANATION),
            )
        val scheduler =
            DefaultSchedulerService(
                contentRepository = FakeContentRepository(nodes = listOf(node), items = items, units = listOf(unit)),
                progressRepository = FakeProgressRepository(),
                eventRepository = FakeEventRepository(),
                settingsRepository = FakeSettingsRepository(),
                idProvider = object : IdProvider { override fun newId(): String = "id" },
                timeProvider = object : TimeProvider { override fun now(): Long = 10_000L },
            )

        val packet = scheduler.buildQuickPacket()

        assertThat(packet).isNotNull()
        // Easy first…
        assertThat(packet!!.initialItemIds.first()).startsWith("easy-")
        // …but the open-recall cards are part of the same daily review, not hidden in deep only.
        assertThat(packet.initialItemIds).containsAtLeast("open-1", "open-2")
        assertThat(packet.goalCorrectCount).isEqualTo(6)
    }

    @Test
    fun `an easy card opens as anzuelo when the first due card is open and never drops it`() = runTest {
        val unit = UnitModel("u1", "c1", "Unidad", null, 1, 1, 1L)
        val node = testNode("n1", "u1", weightExam = 0.9)
        val items =
            listOf(
                // Easy card already mastered (not due) — must be reusable as a warm-up anzuelo.
                testItem("easy-mastered", node.nodeId, 1, ItemRole.CORE),
                testItem(
                    "open-due",
                    node.nodeId,
                    3,
                    ItemRole.CORE,
                    surfaces = listOf(Surface.IN_APP_QUICK, Surface.IN_APP_DEEP),
                    format = ItemFormat.ONE_SENTENCE_EXPLANATION,
                ),
            )
        val progressRepository =
            object : ProgressRepository {
                override suspend fun getNodeState(nodeId: String): NodeState? = null
                override suspend fun getAllNodeStates(): List<NodeState> = emptyList()
                override suspend fun getItemState(itemId: String): ItemState? = getAllItemStates().firstOrNull { it.itemId == itemId }
                override suspend fun getItemStates(itemIds: List<String>): List<ItemState> = getAllItemStates().filter { it.itemId in itemIds }
                override suspend fun getAllItemStates(): List<ItemState> =
                    listOf(ItemState(itemId = "easy-mastered", stage = 5, nextReviewAt = 99_999_999L))
                override suspend fun getAllFormatStats(): List<NodeFormatStat> = emptyList()
                override suspend fun upsertNodeState(nodeState: NodeState) = Unit
                override suspend fun getFormatStats(nodeId: String): List<NodeFormatStat> = emptyList()
                override suspend fun upsertFormatStats(stats: List<NodeFormatStat>) = Unit
                override suspend fun clearAllProgress() = Unit
                override suspend fun resetNodeProgress(nodeId: String) = Unit
            }
        val scheduler =
            DefaultSchedulerService(
                contentRepository = FakeContentRepository(nodes = listOf(node), items = items, units = listOf(unit)),
                progressRepository = progressRepository,
                eventRepository = FakeEventRepository(),
                settingsRepository = FakeSettingsRepository(),
                idProvider = object : IdProvider { override fun newId(): String = "id" },
                timeProvider = object : TimeProvider { override fun now(): Long = 10_000L },
            )

        val packet = scheduler.buildQuickPacket()

        assertThat(packet).isNotNull()
        // The not-due easy card becomes the low-friction opener (anzuelo)…
        assertThat(packet!!.initialPromptKind).isEqualTo(SessionPromptKind.AUXILIARY_ANZUELO)
        assertThat(packet.initialItemIds.first()).isEqualTo("easy-mastered")
        // …and the open card is still in the session (it never vanishes).
        assertThat(packet.initialItemIds).contains("open-due")
        // The easy card is the rescue/anzuelo pool even though it is plain CORE (no dedicated RESCUE role).
        assertThat(packet.rescueItemIds).contains("easy-mastered")
    }

    @Test
    fun `open card with no easy card available still opens directly so nothing vanishes`() = runTest {
        val unit = UnitModel("u1", "c1", "Unidad", null, 1, 1, 1L)
        val node = testNode("n1", "u1", weightExam = 0.9)
        val items =
            listOf(
                testItem(
                    "open-only",
                    node.nodeId,
                    3,
                    ItemRole.CORE,
                    surfaces = listOf(Surface.IN_APP_QUICK, Surface.IN_APP_DEEP),
                    format = ItemFormat.ONE_SENTENCE_EXPLANATION,
                ),
            )
        val scheduler =
            DefaultSchedulerService(
                contentRepository = FakeContentRepository(nodes = listOf(node), items = items, units = listOf(unit)),
                progressRepository = FakeProgressRepository(),
                eventRepository = FakeEventRepository(),
                settingsRepository = FakeSettingsRepository(),
                idProvider = object : IdProvider { override fun newId(): String = "id" },
                timeProvider = object : TimeProvider { override fun now(): Long = 10_000L },
            )

        val packet = scheduler.buildQuickPacket()

        assertThat(packet).isNotNull()
        assertThat(packet!!.initialItemIds).contains("open-only")
        assertThat(packet.initialPromptKind).isEqualTo(SessionPromptKind.REAL_PENDING)
    }

    @Test
    fun `easy cards form the rescue pool even without dedicated rescue role items`() = runTest {
        val unit = UnitModel("u1", "c1", "Unidad", null, 1, 1, 1L)
        val node = testNode("n1", "u1", weightExam = 0.9)
        val openSurfaces = listOf(Surface.IN_APP_QUICK, Surface.IN_APP_DEEP)
        val items =
            listOf(
                testItem("easy-1", node.nodeId, 1, ItemRole.CORE),
                testItem("easy-2", node.nodeId, 1, ItemRole.CORE),
                testItem("easy-3", node.nodeId, 1, ItemRole.CORE),
                testItem("easy-4", node.nodeId, 1, ItemRole.CORE),
                testItem("open-1", node.nodeId, 3, ItemRole.CORE, surfaces = openSurfaces, format = ItemFormat.ONE_SENTENCE_EXPLANATION),
            )
        val scheduler =
            DefaultSchedulerService(
                contentRepository = FakeContentRepository(nodes = listOf(node), items = items, units = listOf(unit)),
                progressRepository = FakeProgressRepository(),
                eventRepository = FakeEventRepository(),
                settingsRepository = FakeSettingsRepository(),
                idProvider = object : IdProvider { override fun newId(): String = "id" },
                timeProvider = object : TimeProvider { override fun now(): Long = 10_000L },
            )

        val packet = scheduler.buildQuickPacket()

        assertThat(packet).isNotNull()
        // The four easy friction-1 cards are the rescue breathers; the open card never is.
        assertThat(packet!!.rescueItemIds).containsAtLeast("easy-1", "easy-2", "easy-3", "easy-4")
        assertThat(packet.rescueItemIds).doesNotContain("open-1")
    }

    @Test
    fun `quick packet excludes rescue and covers another node before repeating same node`() = runTest {
        val unit = UnitModel("u1", "c1", "Unidad", null, 1, 1, 1L)
        val anchorNode = testNode("n-anchor", "u1", weightExam = 0.95)
        val otherNode = testNode("n-other", "u1", weightExam = 0.80)
        val items =
            listOf(
                testItem("anchor-core", anchorNode.nodeId, 1, ItemRole.CORE),
                testItem("anchor-variant", anchorNode.nodeId, 1, ItemRole.VARIANT),
                testItem("anchor-rescue", anchorNode.nodeId, 1, ItemRole.RESCUE),
                testItem("other-core", otherNode.nodeId, 1, ItemRole.CORE),
                testItem("other-variant", otherNode.nodeId, 2, ItemRole.VARIANT),
            )
        val scheduler =
            DefaultSchedulerService(
                contentRepository = FakeContentRepository(nodes = listOf(anchorNode, otherNode), items = items, units = listOf(unit)),
                progressRepository = FakeProgressRepository(),
                eventRepository = FakeEventRepository(),
                settingsRepository = FakeSettingsRepository(),
                idProvider = object : IdProvider { override fun newId(): String = "id" },
                timeProvider = object : TimeProvider { override fun now(): Long = 10_000L },
            )

        val packet = scheduler.buildQuickPacket()

        assertThat(packet).isNotNull()
        assertThat(packet!!.initialItemIds.take(2)).containsExactly("anchor-core", "other-core").inOrder()
        assertThat(packet.initialItemIds).doesNotContain("anchor-rescue")
        assertThat(packet.initialItemIds.indexOf("other-core")).isLessThan(packet.initialItemIds.indexOf("anchor-variant"))
    }

    @Test
    fun `notification packet prefers due easy items and stays inside notification easy pool`() = runTest {
        val unit = UnitModel("u1", "c1", "Unidad", null, 1, 1, 1L)
        val dueNode = testNode("n-due", "u1", weightExam = 0.9)
        val derivedNode = testNode("n-derived", "u1", weightExam = 0.95)
        val items =
            listOf(
                testItem("due-core", dueNode.nodeId, 1, ItemRole.CORE),
                testItem("due-variant", dueNode.nodeId, 1, ItemRole.VARIANT),
                testItem("derived-core", derivedNode.nodeId, 1, ItemRole.CORE),
                testItem("derived-trap", derivedNode.nodeId, 1, ItemRole.TRAP),
                testItem("derived-boss", derivedNode.nodeId, 1, ItemRole.BOSS),
            )
        val progressRepository =
            object : ProgressRepository {
                override suspend fun getNodeState(nodeId: String): NodeState? = null
                override suspend fun getAllNodeStates(): List<NodeState> =
                    listOf(
                        NodeState(nodeId = dueNode.nodeId, nextReviewAt = 0L),
                        NodeState(nodeId = derivedNode.nodeId, nextReviewAt = 99_999_999L),
                    )
                override suspend fun getItemState(itemId: String): ItemState? = getAllItemStates().firstOrNull { it.itemId == itemId }
                override suspend fun getItemStates(itemIds: List<String>): List<ItemState> = getAllItemStates().filter { it.itemId in itemIds }
                override suspend fun getAllItemStates(): List<ItemState> =
                    listOf(
                        ItemState(itemId = "due-core", stage = 1, nextReviewAt = 0L),
                        ItemState(itemId = "due-variant", stage = 1, nextReviewAt = 0L),
                        ItemState(itemId = "derived-core", stage = 1, nextReviewAt = 99_999_999L),
                        ItemState(itemId = "derived-trap", stage = 1, nextReviewAt = 99_999_999L),
                        ItemState(itemId = "derived-boss", stage = 1, nextReviewAt = 99_999_999L),
                    )
                override suspend fun getAllFormatStats(): List<NodeFormatStat> = emptyList()
                override suspend fun upsertNodeState(nodeState: NodeState) = Unit
                override suspend fun getFormatStats(nodeId: String): List<NodeFormatStat> = emptyList()
                override suspend fun upsertFormatStats(stats: List<NodeFormatStat>) = Unit
                override suspend fun clearAllProgress() = Unit
                override suspend fun resetNodeProgress(nodeId: String) = Unit
            }
        val scheduler =
            DefaultSchedulerService(
                contentRepository = FakeContentRepository(nodes = listOf(dueNode, derivedNode), items = items, units = listOf(unit)),
                progressRepository = progressRepository,
                eventRepository = FakeEventRepository(),
                settingsRepository = FakeSettingsRepository(),
                idProvider = object : IdProvider { override fun newId(): String = "id" },
                timeProvider = object : TimeProvider { override fun now(): Long = 10_000L },
            )

        val packet = scheduler.buildNotificationPacket()

        assertThat(packet).isNotNull()
        assertThat(packet!!.surface).isEqualTo(Surface.NOTIFICATION)
        assertThat(packet.initialItemIds.first()).isEqualTo("due-core")
        assertThat(packet.initialItemIds).containsNoneOf("derived-trap", "derived-boss")
    }

    @Test
    fun `notification packet returns null when there are no notification easy items`() = runTest {
        val unit = UnitModel("u1", "c1", "Unidad", null, 1, 1, 1L)
        val node = testNode("n1", "u1", weightExam = 0.9)
        val items =
            listOf(
                testItem("trap", node.nodeId, 2, ItemRole.TRAP, format = ItemFormat.ONE_SENTENCE_EXPLANATION),
                testItem("boss", node.nodeId, 3, ItemRole.BOSS, format = ItemFormat.FULL_RECONSTRUCTION),
            )

        val scheduler =
            DefaultSchedulerService(
                contentRepository = FakeContentRepository(nodes = listOf(node), items = items, units = listOf(unit)),
                progressRepository = FakeProgressRepository(),
                eventRepository = FakeEventRepository(),
                settingsRepository = FakeSettingsRepository(),
                idProvider = object : IdProvider { override fun newId(): String = "id" },
                timeProvider = object : TimeProvider { override fun now(): Long = 10_000L },
            )

        val packet = scheduler.buildNotificationPacket()

        assertThat(packet).isNull()
    }

    @Test
    fun `notification packet mirrors anzuelo when the first due real item is heavy`() = runTest {
        val unit = UnitModel("u1", "c1", "Unidad", null, 1, 1, 1L)
        val node = testNode("n-heavy", "u1", weightExam = 0.9)
        val items =
            listOf(
                testItem(
                    "heavy-real",
                    node.nodeId,
                    3,
                    ItemRole.CORE,
                    format = ItemFormat.FULL_RECONSTRUCTION,
                ),
                testItem(
                    "aux-rescue",
                    node.nodeId,
                    1,
                    ItemRole.RESCUE,
                    surfaces = listOf(Surface.IN_APP_QUICK, Surface.BACK_MICRO),
                ),
                testItem(
                    "future-easy-real",
                    node.nodeId,
                    1,
                    ItemRole.VARIANT,
                ),
            )
        val progressRepository =
            object : ProgressRepository {
                override suspend fun getNodeState(nodeId: String): NodeState? = null
                override suspend fun getAllNodeStates(): List<NodeState> = emptyList()
                override suspend fun getItemState(itemId: String): ItemState? = getAllItemStates().firstOrNull { it.itemId == itemId }
                override suspend fun getItemStates(itemIds: List<String>): List<ItemState> = getAllItemStates().filter { it.itemId in itemIds }
                override suspend fun getAllItemStates(): List<ItemState> =
                    listOf(
                        ItemState(itemId = "heavy-real", stage = 1, nextReviewAt = 0L),
                        ItemState(itemId = "future-easy-real", stage = 1, nextReviewAt = 99_999_999L),
                    )
                override suspend fun getAllFormatStats(): List<NodeFormatStat> = emptyList()
                override suspend fun upsertNodeState(nodeState: NodeState) = Unit
                override suspend fun getFormatStats(nodeId: String): List<NodeFormatStat> = emptyList()
                override suspend fun upsertFormatStats(stats: List<NodeFormatStat>) = Unit
                override suspend fun clearAllProgress() = Unit
                override suspend fun resetNodeProgress(nodeId: String) = Unit
            }
        val scheduler =
            DefaultSchedulerService(
                contentRepository = FakeContentRepository(nodes = listOf(node), items = items, units = listOf(unit)),
                progressRepository = progressRepository,
                eventRepository = FakeEventRepository(),
                settingsRepository = FakeSettingsRepository(),
                idProvider = object : IdProvider { override fun newId(): String = "id" },
                timeProvider = object : TimeProvider { override fun now(): Long = 10_000L },
            )

        val packet = scheduler.buildNotificationPacket()

        assertThat(packet).isNotNull()
        assertThat(packet!!.initialPromptKind).isEqualTo(SessionPromptKind.AUXILIARY_ANZUELO)
        assertThat(packet.initialItemIds.first()).isEqualTo("aux-rescue")
        assertThat(packet.initialItemIds).doesNotContain("future-easy-real")
    }

    @Test
    fun `pending packet prioritizes the more overdue unit via dueScore`() = runTest {
        val unitA = UnitModel("u-a", "c1", "Unidad A", null, 1, 1, 1L)
        val unitB = UnitModel("u-b", "c1", "Unidad B", null, 2, 1, 1L)
        val nodeA = testNode("n-a", "u-a", weightExam = 0.5)
        val nodeB = testNode("n-b", "u-b", weightExam = 0.5)
        val now = 1_000_000_000L
        val items =
            listOf(
                testItem("a1", nodeA.nodeId, 1, ItemRole.CORE),
                testItem("b1", nodeB.nodeId, 1, ItemRole.CORE),
            )
        // Misma fragilidad/fricción/peso en ambos nodos: lo único que difiere es cuán atrasada está la
        // tarjeta due (a1 hace 5 días, b1 hace 1 hora), así el resultado aísla el efecto del dueScore.
        val itemStates =
            listOf(
                ItemState(itemId = "a1", stage = 2, nextReviewAt = now - 5 * 24 * 60 * 60 * 1000L),
                ItemState(itemId = "b1", stage = 2, nextReviewAt = now - 60 * 60 * 1000L),
            )
        val progressRepository =
            object : ProgressRepository {
                override suspend fun getNodeState(nodeId: String): NodeState? = null
                override suspend fun getAllNodeStates(): List<NodeState> = emptyList()
                override suspend fun getAllItemStates(): List<ItemState> = itemStates
                override suspend fun getItemState(itemId: String): ItemState? = itemStates.firstOrNull { it.itemId == itemId }
                override suspend fun getAllFormatStats(): List<NodeFormatStat> = emptyList()
                override suspend fun upsertNodeState(nodeState: NodeState) = Unit
                override suspend fun getFormatStats(nodeId: String): List<NodeFormatStat> = emptyList()
                override suspend fun upsertFormatStats(stats: List<NodeFormatStat>) = Unit
                override suspend fun clearAllProgress() = Unit
                override suspend fun resetNodeProgress(nodeId: String) = Unit
            }
        val scheduler =
            DefaultSchedulerService(
                contentRepository = FakeContentRepository(nodes = listOf(nodeA, nodeB), items = items, units = listOf(unitA, unitB)),
                progressRepository = progressRepository,
                eventRepository = FakeEventRepository(),
                settingsRepository = FakeSettingsRepository(),
                idProvider = object : IdProvider { override fun newId(): String = "id" },
                timeProvider = object : TimeProvider { override fun now(): Long = now },
            )

        val packet = scheduler.buildQuickPacket()

        assertThat(packet).isNotNull()
        assertThat(packet!!.topicUnitId).isEqualTo("u-a")
        assertThat(packet.initialItemIds.first()).isEqualTo("a1")
    }

    @Test
    fun `social gate packet builds a full drain-like unit session and unlocks on the easy front of that order`() = runTest {
        val unit = UnitModel("u1", "c1", "Unidad", null, 1, 1, 1L)
        val dueNode = testNode("n-due", "u1", weightExam = 0.9)
        val otherNode = testNode("n-other", "u1", weightExam = 0.8)
        val items =
            listOf(
                testItem(
                    "due-core",
                    dueNode.nodeId,
                    1,
                    ItemRole.CORE,
                    surfaces = listOf(Surface.SOCIAL_GATE, Surface.IN_APP_DEEP),
                ),
                testItem(
                    "due-variant",
                    dueNode.nodeId,
                    1,
                    ItemRole.VARIANT,
                    surfaces = listOf(Surface.SOCIAL_GATE, Surface.IN_APP_DEEP),
                ),
                testItem(
                    "due-trap",
                    dueNode.nodeId,
                    2,
                    ItemRole.TRAP,
                    surfaces = listOf(Surface.IN_APP_DEEP),
                ),
                testItem(
                    "due-rescue",
                    dueNode.nodeId,
                    1,
                    ItemRole.RESCUE,
                    surfaces = listOf(Surface.SOCIAL_GATE, Surface.IN_APP_DEEP, Surface.BACK_MICRO),
                ),
                testItem(
                    "other-core",
                    otherNode.nodeId,
                    1,
                    ItemRole.CORE,
                    surfaces = listOf(Surface.IN_APP_DEEP),
                ),
                testItem(
                    "other-boss",
                    otherNode.nodeId,
                    3,
                    ItemRole.BOSS,
                    surfaces = listOf(Surface.IN_APP_DEEP),
                ),
            )
        val progressRepository =
            object : ProgressRepository {
                override suspend fun getNodeState(nodeId: String): NodeState? = null
                override suspend fun getAllNodeStates(): List<NodeState> =
                    listOf(
                        NodeState(nodeId = dueNode.nodeId, nextReviewAt = 0L),
                        NodeState(nodeId = otherNode.nodeId, nextReviewAt = 99_999L),
                    )

                override suspend fun getAllFormatStats(): List<NodeFormatStat> = emptyList()
                override suspend fun upsertNodeState(nodeState: NodeState) = Unit
                override suspend fun getFormatStats(nodeId: String): List<NodeFormatStat> = emptyList()
                override suspend fun upsertFormatStats(stats: List<NodeFormatStat>) = Unit
                override suspend fun clearAllProgress() = Unit
                override suspend fun resetNodeProgress(nodeId: String) = Unit
            }

        val scheduler =
            DefaultSchedulerService(
                contentRepository = FakeContentRepository(nodes = listOf(dueNode, otherNode), items = items, units = listOf(unit)),
                progressRepository = progressRepository,
                eventRepository =
                    FakeEventRepository(
                        itemPerformance =
                            mapOf(
                                "other-core" to performance("other-core", 2, 0),
                                "due-core" to performance("due-core", 4, 2),
                                "due-variant" to performance("due-variant", 5, 5),
                                "due-trap" to performance("due-trap", 3, 1),
                                "other-boss" to performance("other-boss", 2, 2),
                            ),
                    ),
                settingsRepository = FakeSettingsRepository(),
                idProvider = object : IdProvider { override fun newId(): String = "id" },
                timeProvider = object : TimeProvider { override fun now(): Long = 10_000L },
            )

        val packet = scheduler.buildSocialGatePacket(goalCorrectCount = 3)

        assertThat(packet).isNotNull()
        assertThat(packet!!.surface).isEqualTo(Surface.SOCIAL_GATE)
        assertThat(packet.sessionMode).isEqualTo(SessionMode.QUICK)
        assertThat(packet.nodeIds).containsExactly(dueNode.nodeId)
        assertThat(packet.initialItemIds).containsExactly("due-core", "due-variant").inOrder()
        assertThat(packet.rescueItemIds).isEmpty()
        assertThat(packet.goalCorrectCount).isEqualTo(2)
    }

    @Test
    fun `social gate packet does not cap real due debt at the old configured goal count`() = runTest {
        val unit = UnitModel("u1", "c1", "Unidad", null, 1, 1, 1L)
        val node = testNode("n-due", "u1", weightExam = 0.9)
        val realIds = listOf("g1", "g2", "g3", "g4", "g5")
        val items =
            realIds.map { id ->
                testItem(id, node.nodeId, 1, ItemRole.CORE, surfaces = listOf(Surface.SOCIAL_GATE, Surface.IN_APP_DEEP))
            }
        val progressRepository =
            object : ProgressRepository {
                override suspend fun getNodeState(nodeId: String): NodeState? = null
                override suspend fun getAllNodeStates(): List<NodeState> = listOf(NodeState(nodeId = node.nodeId, nextReviewAt = 0L))
                override suspend fun getAllFormatStats(): List<NodeFormatStat> = emptyList()
                override suspend fun upsertNodeState(nodeState: NodeState) = Unit
                override suspend fun getFormatStats(nodeId: String): List<NodeFormatStat> = emptyList()
                override suspend fun upsertFormatStats(stats: List<NodeFormatStat>) = Unit
                override suspend fun clearAllProgress() = Unit
                override suspend fun resetNodeProgress(nodeId: String) = Unit
            }
        val scheduler =
            DefaultSchedulerService(
                contentRepository = FakeContentRepository(nodes = listOf(node), items = items, units = listOf(unit)),
                progressRepository = progressRepository,
                eventRepository = FakeEventRepository(),
                settingsRepository = FakeSettingsRepository(),
                idProvider = object : IdProvider { override fun newId(): String = "id" },
                timeProvider = object : TimeProvider { override fun now(): Long = 10_000L },
            )

        val packet = scheduler.buildSocialGatePacket(goalCorrectCount = 3)

        assertThat(packet).isNotNull()
        // El gate legacy tampoco puede acotar artificialmente: si se usa, pregunta toda la deuda real
        // gate-apta que esté due. El flujo real de producción abre Tarjetas pendientes directamente.
        assertThat(packet!!.initialItemIds).containsExactlyElementsIn(realIds)
        assertThat(packet.goalCorrectCount).isEqualTo(5)
    }

    @Test
    fun `social gate packet never exposes rescue items as gate filler`() = runTest {
        val unit = UnitModel("u1", "c1", "Unidad", null, 1, 1, 1L)
        val node = testNode("n-due", "u1", weightExam = 0.9)
        val now = 10_000_000L
        val items =
            listOf(
                testItem("g1", node.nodeId, 1, ItemRole.CORE, surfaces = listOf(Surface.SOCIAL_GATE, Surface.IN_APP_DEEP)),
                testItem("rescue-fresh", node.nodeId, 1, ItemRole.RESCUE, surfaces = listOf(Surface.SOCIAL_GATE)),
                testItem("rescue-cooldown", node.nodeId, 1, ItemRole.RESCUE, surfaces = listOf(Surface.SOCIAL_GATE)),
            )
        // rescue-cooldown se respondió hace 30 min; con cooldownHours = 1.0 sigue en cooldown.
        val cooldownState =
            ItemState(itemId = "rescue-cooldown", stage = 2, lastReviewedAt = now - 30 * 60 * 1000L, nextReviewAt = now + 1_000L)
        val progressRepository =
            object : ProgressRepository {
                override suspend fun getNodeState(nodeId: String): NodeState? = null
                override suspend fun getAllNodeStates(): List<NodeState> = listOf(NodeState(nodeId = node.nodeId, nextReviewAt = 0L))
                override suspend fun getAllItemStates(): List<ItemState> = listOf(cooldownState)
                override suspend fun getItemState(itemId: String): ItemState? = if (itemId == cooldownState.itemId) cooldownState else null
                override suspend fun getAllFormatStats(): List<NodeFormatStat> = emptyList()
                override suspend fun upsertNodeState(nodeState: NodeState) = Unit
                override suspend fun getFormatStats(nodeId: String): List<NodeFormatStat> = emptyList()
                override suspend fun upsertFormatStats(stats: List<NodeFormatStat>) = Unit
                override suspend fun clearAllProgress() = Unit
                override suspend fun resetNodeProgress(nodeId: String) = Unit
            }
        val scheduler =
            DefaultSchedulerService(
                contentRepository = FakeContentRepository(nodes = listOf(node), items = items, units = listOf(unit)),
                progressRepository = progressRepository,
                eventRepository = FakeEventRepository(),
                settingsRepository = FakeSettingsRepository(),
                idProvider = object : IdProvider { override fun newId(): String = "id" },
                timeProvider = object : TimeProvider { override fun now(): Long = now },
            )

        val packet = scheduler.buildSocialGatePacket(goalCorrectCount = 1)

        assertThat(packet).isNotNull()
        assertThat(packet!!.initialItemIds).containsExactly("g1")
        assertThat(packet.rescueItemIds).isEmpty()
    }

    @Test
    fun `social gate packet excludes non due real items and does not add parallel fill`() = runTest {
        val unit = UnitModel("u1", "c1", "Unidad", null, 1, 1, 1L)
        val dueNode = testNode("n-due", "u1", weightExam = 0.9)
        val futureNode = testNode("n-future", "u1", weightExam = 0.95)
        val items =
            listOf(
                testItem(
                    "due-core",
                    dueNode.nodeId,
                    1,
                    ItemRole.CORE,
                    surfaces = listOf(Surface.SOCIAL_GATE, Surface.IN_APP_DEEP),
                ),
                testItem(
                    "future-core",
                    futureNode.nodeId,
                    1,
                    ItemRole.CORE,
                    surfaces = listOf(Surface.SOCIAL_GATE, Surface.IN_APP_DEEP),
                ),
                testItem(
                    "gate-fill",
                    dueNode.nodeId,
                    1,
                    ItemRole.RESCUE,
                    surfaces = listOf(Surface.SOCIAL_GATE, Surface.IN_APP_DEEP),
                ),
            )
        val progressRepository =
            object : ProgressRepository {
                override suspend fun getNodeState(nodeId: String): NodeState? = null
                override suspend fun getAllNodeStates(): List<NodeState> = emptyList()
                override suspend fun getItemState(itemId: String): ItemState? = getAllItemStates().firstOrNull { it.itemId == itemId }
                override suspend fun getItemStates(itemIds: List<String>): List<ItemState> = getAllItemStates().filter { it.itemId in itemIds }
                override suspend fun getAllItemStates(): List<ItemState> =
                    listOf(
                        ItemState(itemId = "due-core", stage = 1, nextReviewAt = 0L),
                        ItemState(itemId = "future-core", stage = 1, nextReviewAt = 99_999_999L),
                    )
                override suspend fun getAllFormatStats(): List<NodeFormatStat> = emptyList()
                override suspend fun upsertNodeState(nodeState: NodeState) = Unit
                override suspend fun getFormatStats(nodeId: String): List<NodeFormatStat> = emptyList()
                override suspend fun upsertFormatStats(stats: List<NodeFormatStat>) = Unit
                override suspend fun clearAllProgress() = Unit
                override suspend fun resetNodeProgress(nodeId: String) = Unit
            }
        val scheduler =
            DefaultSchedulerService(
                contentRepository = FakeContentRepository(nodes = listOf(dueNode, futureNode), items = items, units = listOf(unit)),
                progressRepository = progressRepository,
                eventRepository = FakeEventRepository(),
                settingsRepository = FakeSettingsRepository(),
                idProvider = object : IdProvider { override fun newId(): String = "id" },
                timeProvider = object : TimeProvider { override fun now(): Long = 10_000L },
            )

        val packet = scheduler.buildSocialGatePacket(goalCorrectCount = 3)

        assertThat(packet).isNotNull()
        assertThat(packet!!.initialItemIds).containsExactly("due-core")
        assertThat(packet.initialItemIds).doesNotContain("future-core")
        assertThat(packet.rescueItemIds).isEmpty()
    }

    @Test
    fun `social gate packet returns null when only filler style cards are available`() = runTest {
        val unit = UnitModel("u1", "c1", "Unidad", null, 1, 1, 1L)
        val node = testNode("n-due", "u1", weightExam = 0.9)
        val items =
            listOf(
                testItem("rescue-only", node.nodeId, 1, ItemRole.RESCUE, surfaces = listOf(Surface.SOCIAL_GATE)),
                testItem("future-core", node.nodeId, 1, ItemRole.CORE, surfaces = listOf(Surface.SOCIAL_GATE, Surface.IN_APP_DEEP)),
            )
        val progressRepository =
            object : ProgressRepository {
                override suspend fun getNodeState(nodeId: String): NodeState? = null
                override suspend fun getAllNodeStates(): List<NodeState> = emptyList()
                override suspend fun getItemState(itemId: String): ItemState? = getAllItemStates().firstOrNull { it.itemId == itemId }
                override suspend fun getItemStates(itemIds: List<String>): List<ItemState> = getAllItemStates().filter { it.itemId in itemIds }
                override suspend fun getAllItemStates(): List<ItemState> =
                    listOf(ItemState(itemId = "future-core", stage = 1, nextReviewAt = 99_999_999L))
                override suspend fun getAllFormatStats(): List<NodeFormatStat> = emptyList()
                override suspend fun upsertNodeState(nodeState: NodeState) = Unit
                override suspend fun getFormatStats(nodeId: String): List<NodeFormatStat> = emptyList()
                override suspend fun upsertFormatStats(stats: List<NodeFormatStat>) = Unit
                override suspend fun clearAllProgress() = Unit
                override suspend fun resetNodeProgress(nodeId: String) = Unit
            }
        val scheduler =
            DefaultSchedulerService(
                contentRepository = FakeContentRepository(nodes = listOf(node), items = items, units = listOf(unit)),
                progressRepository = progressRepository,
                eventRepository = FakeEventRepository(),
                settingsRepository = FakeSettingsRepository(),
                idProvider = object : IdProvider { override fun newId(): String = "id" },
                timeProvider = object : TimeProvider { override fun now(): Long = 10_000L },
            )

        val packet = scheduler.buildSocialGatePacket(goalCorrectCount = 3)

        assertThat(packet).isNull()
    }

    @Test
    fun `social gate packet has no auxiliary pool even when spare easy cards exist`() = runTest {
        val unit = UnitModel("u1", "c1", "Unidad", null, 1, 1, 1L)
        val node = testNode("n1", "u1", weightExam = 0.9)
        val gateSurfaces = listOf(Surface.SOCIAL_GATE, Surface.IN_APP_DEEP)
        val items =
            listOf(
                testItem("easy-due", node.nodeId, 1, ItemRole.CORE, surfaces = gateSurfaces),
                testItem("easy-spare", node.nodeId, 1, ItemRole.CORE, surfaces = gateSurfaces),
                // High-friction card that even carries the gate surface — it must never be an auxiliary.
                testItem("hard-gate", node.nodeId, 3, ItemRole.CORE, surfaces = gateSurfaces, format = ItemFormat.ONE_SENTENCE_EXPLANATION),
            )
        val progressRepository =
            object : ProgressRepository {
                override suspend fun getNodeState(nodeId: String): NodeState? = null
                override suspend fun getAllNodeStates(): List<NodeState> = emptyList()
                override suspend fun getItemState(itemId: String): ItemState? = getAllItemStates().firstOrNull { it.itemId == itemId }
                override suspend fun getItemStates(itemIds: List<String>): List<ItemState> = getAllItemStates().filter { it.itemId in itemIds }
                override suspend fun getAllItemStates(): List<ItemState> =
                    listOf(
                        ItemState(itemId = "easy-due", stage = 1, nextReviewAt = 0L),
                        ItemState(itemId = "easy-spare", stage = 1, nextReviewAt = 99_999_999L),
                    )
                override suspend fun getAllFormatStats(): List<NodeFormatStat> = emptyList()
                override suspend fun upsertNodeState(nodeState: NodeState) = Unit
                override suspend fun getFormatStats(nodeId: String): List<NodeFormatStat> = emptyList()
                override suspend fun upsertFormatStats(stats: List<NodeFormatStat>) = Unit
                override suspend fun clearAllProgress() = Unit
                override suspend fun resetNodeProgress(nodeId: String) = Unit
            }
        val scheduler =
            DefaultSchedulerService(
                contentRepository = FakeContentRepository(nodes = listOf(node), items = items, units = listOf(unit)),
                progressRepository = progressRepository,
                eventRepository = FakeEventRepository(),
                settingsRepository = FakeSettingsRepository(),
                idProvider = object : IdProvider { override fun newId(): String = "id" },
                timeProvider = object : TimeProvider { override fun now(): Long = 10_000L },
            )

        val packet = scheduler.buildSocialGatePacket(goalCorrectCount = 1)

        assertThat(packet).isNotNull()
        assertThat(packet!!.initialItemIds).containsExactly("easy-due")
        assertThat(packet.initialItemIds).containsNoneOf("easy-spare", "hard-gate")
        assertThat(packet.rescueItemIds).isEmpty()
    }

    @Test
    fun `deep packet prioritizes coverage then trap integration boss and excludes rescue`() = runTest {
        val unit = UnitModel("u1", "c1", "Consensus", null, 1, 1, 1L)
        val nodeA = testNode("n1", "u1", weightExam = 0.9)
        val nodeB = testNode("n2", "u1", weightExam = 0.8)
        val items =
            listOf(
                testItem("n1-core", nodeA.nodeId, 1, ItemRole.CORE),
                testItem("n1-variant", nodeA.nodeId, 2, ItemRole.VARIANT),
                testItem("n1-trap", nodeA.nodeId, 2, ItemRole.TRAP),
                testItem("n1-integration", nodeA.nodeId, 2, ItemRole.INTEGRATION),
                testItem("n1-boss", nodeA.nodeId, 3, ItemRole.BOSS),
                testItem("n1-rescue", nodeA.nodeId, 1, ItemRole.RESCUE),
                testItem("n2-core", nodeB.nodeId, 1, ItemRole.CORE),
                testItem("n2-variant", nodeB.nodeId, 2, ItemRole.VARIANT),
                testItem("n2-trap", nodeB.nodeId, 2, ItemRole.TRAP),
                testItem("n2-integration", nodeB.nodeId, 2, ItemRole.INTEGRATION),
                testItem("n2-boss", nodeB.nodeId, 3, ItemRole.BOSS),
                testItem("n2-rescue", nodeB.nodeId, 1, ItemRole.RESCUE),
            )

        val scheduler =
            DefaultSchedulerService(
                contentRepository = FakeContentRepository(nodes = listOf(nodeA, nodeB), items = items, units = listOf(unit)),
                progressRepository = FakeProgressRepository(),
                eventRepository = FakeEventRepository(),
                settingsRepository = FixedSettingsRepository(AppSettings(deepSessionQuestionTarget = 8)),
                idProvider = object : IdProvider { override fun newId(): String = "id" },
                timeProvider = object : TimeProvider { override fun now(): Long = 10_000L },
            )

        val packet = scheduler.buildDeepPacket("u1")

        assertThat(packet).isNotNull()
        assertThat(packet!!.sessionMode).isEqualTo(SessionMode.DEEP)
        assertThat(packet.initialItemIds).containsAtLeast("n1-core", "n2-core")
        assertThat(packet.initialItemIds).containsAtLeast("n1-trap", "n1-integration", "n1-boss")
        assertThat(packet.initialItemIds).doesNotContain("n1-rescue")
        assertThat(packet.initialItemIds).doesNotContain("n2-rescue")
    }

    @Test
    fun `deep packet falls back to variant when a node has no core item`() = runTest {
        val unit = UnitModel("u1", "c1", "Consensus", null, 1, 1, 1L)
        val nodeWithoutCore = testNode("n1", "u1", weightExam = 0.95)
        val nodeWithCore = testNode("n2", "u1", weightExam = 0.80)
        val items =
            listOf(
                testItem("n1-variant", nodeWithoutCore.nodeId, 2, ItemRole.VARIANT),
                testItem("n1-trap", nodeWithoutCore.nodeId, 2, ItemRole.TRAP),
                testItem("n1-integration", nodeWithoutCore.nodeId, 2, ItemRole.INTEGRATION),
                testItem("n1-boss", nodeWithoutCore.nodeId, 3, ItemRole.BOSS),
                testItem("n2-core", nodeWithCore.nodeId, 1, ItemRole.CORE),
                testItem("n2-trap", nodeWithCore.nodeId, 2, ItemRole.TRAP),
                testItem("n2-integration", nodeWithCore.nodeId, 2, ItemRole.INTEGRATION),
                testItem("n2-boss", nodeWithCore.nodeId, 3, ItemRole.BOSS),
            )

        val scheduler =
            DefaultSchedulerService(
                contentRepository = FakeContentRepository(nodes = listOf(nodeWithoutCore, nodeWithCore), items = items, units = listOf(unit)),
                progressRepository = FakeProgressRepository(),
                eventRepository = FakeEventRepository(),
                settingsRepository = FixedSettingsRepository(AppSettings(deepSessionQuestionTarget = 4)),
                idProvider = object : IdProvider { override fun newId(): String = "id" },
                timeProvider = object : TimeProvider { override fun now(): Long = 10_000L },
            )

        val packet = scheduler.buildDeepPacket("u1")

        assertThat(packet).isNotNull()
        assertThat(packet!!.initialItemIds).contains("n2-core")
        assertThat(packet.initialItemIds).contains("n1-variant")
        assertThat(packet.initialItemIds).doesNotContain("n1-rescue")
    }

    @Test
    fun `deep packet respects configured target size while preserving early coverage priorities`() = runTest {
        val unit = UnitModel("u1", "c1", "Consensus", null, 1, 1, 1L)
        val nodeA = testNode("n1", "u1", weightExam = 0.95)
        val nodeB = testNode("n2", "u1", weightExam = 0.90)
        val items =
            listOf(
                testItem("n1-core", nodeA.nodeId, 1, ItemRole.CORE),
                testItem("n1-trap", nodeA.nodeId, 2, ItemRole.TRAP),
                testItem("n1-integration", nodeA.nodeId, 2, ItemRole.INTEGRATION),
                testItem("n1-boss", nodeA.nodeId, 3, ItemRole.BOSS),
                testItem("n2-core", nodeB.nodeId, 1, ItemRole.CORE),
                testItem("n2-trap", nodeB.nodeId, 2, ItemRole.TRAP),
                testItem("n2-integration", nodeB.nodeId, 2, ItemRole.INTEGRATION),
                testItem("n2-boss", nodeB.nodeId, 3, ItemRole.BOSS),
            )

        val scheduler =
            DefaultSchedulerService(
                contentRepository = FakeContentRepository(nodes = listOf(nodeA, nodeB), items = items, units = listOf(unit)),
                progressRepository = FakeProgressRepository(),
                eventRepository = FakeEventRepository(),
                settingsRepository = FixedSettingsRepository(AppSettings(deepSessionQuestionTarget = 3)),
                idProvider = object : IdProvider { override fun newId(): String = "id" },
                timeProvider = object : TimeProvider { override fun now(): Long = 10_000L },
            )

        val packet = scheduler.buildDeepPacket("u1")

        assertThat(packet).isNotNull()
        assertThat(packet!!.initialItemIds).hasSize(3)
        assertThat(packet.initialItemIds).containsExactly("n1-core", "n2-core", "n1-trap").inOrder()
    }

    @Test
    fun `drain packet includes full normal bank and excludes rescue while prioritizing lower success ratio inside a difficulty bucket`() = runTest {
        val unit = UnitModel("u1", "c1", "Consensus", null, 1, 1, 1L)
        val node = testNode("n1", "u1", weightExam = 0.9)
        val items =
            listOf(
                testItem("core", node.nodeId, 1, ItemRole.CORE),
                testItem("variant", node.nodeId, 2, ItemRole.VARIANT),
                testItem("trap", node.nodeId, 2, ItemRole.TRAP),
                testItem("integration", node.nodeId, 3, ItemRole.INTEGRATION),
                testItem("boss", node.nodeId, 3, ItemRole.BOSS),
                testItem("rescue", node.nodeId, 1, ItemRole.RESCUE),
            )
        val scheduler =
            DefaultSchedulerService(
                contentRepository = FakeContentRepository(nodes = listOf(node), items = items, units = listOf(unit)),
                progressRepository = FakeProgressRepository(),
                eventRepository =
                    FakeEventRepository(
                        itemPerformance =
                            mapOf(
                                "core" to performance("core", 3, 3),
                                "variant" to performance("variant", 5, 5),
                                "trap" to performance("trap", 5, 1),
                                "integration" to performance("integration", 3, 1),
                                "boss" to performance("boss", 2, 2),
                            ),
                    ),
                settingsRepository = FakeSettingsRepository(),
                idProvider = object : IdProvider { override fun newId(): String = "id" },
                timeProvider = object : TimeProvider { override fun now(): Long = 10_000L },
            )

        val packet = scheduler.buildDrainPacket("u1")

        assertThat(packet).isNotNull()
        assertThat(packet!!.sessionMode).isEqualTo(SessionMode.DRAIN)
        assertThat(packet.initialItemIds).containsExactly("core", "variant", "trap", "integration", "boss").inOrder()
        assertThat(packet.initialItemIds).doesNotContain("rescue")
    }

    @Test
    fun `drain packet randomizes items with same difficulty and same ratio across builds`() = runTest {
        val unit = UnitModel("u1", "c1", "Consensus", null, 1, 1, 1L)
        val nodeA = testNode("n1", "u1", weightExam = 0.9)
        val nodeB = testNode("n2", "u1", weightExam = 0.8)
        val items =
            listOf(
                testItem("n1-core", nodeA.nodeId, 1, ItemRole.CORE),
                testItem("n2-core", nodeB.nodeId, 1, ItemRole.CORE),
                testItem("n1-variant", nodeA.nodeId, 2, ItemRole.VARIANT),
                testItem("n2-variant", nodeB.nodeId, 2, ItemRole.VARIANT),
                testItem("n1-trap", nodeA.nodeId, 2, ItemRole.TRAP),
                testItem("n2-trap", nodeB.nodeId, 2, ItemRole.TRAP),
                testItem("n1-integration", nodeA.nodeId, 3, ItemRole.INTEGRATION),
                testItem("n2-integration", nodeB.nodeId, 3, ItemRole.INTEGRATION),
                testItem("n1-boss", nodeA.nodeId, 3, ItemRole.BOSS),
                testItem("n2-boss", nodeB.nodeId, 3, ItemRole.BOSS),
                testItem("n1-rescue", nodeA.nodeId, 1, ItemRole.RESCUE),
                testItem("n2-rescue", nodeB.nodeId, 1, ItemRole.RESCUE),
            )
        val eventRepository =
            FakeEventRepository(
                itemPerformance =
                    mapOf(
                        "n1-core" to performance("n1-core", 2, 1),
                        "n2-core" to performance("n2-core", 2, 1),
                        "n1-variant" to performance("n1-variant", 2, 1),
                        "n2-variant" to performance("n2-variant", 2, 1),
                        "n1-trap" to performance("n1-trap", 2, 1),
                        "n2-trap" to performance("n2-trap", 2, 1),
                        "n1-integration" to performance("n1-integration", 2, 1),
                        "n2-integration" to performance("n2-integration", 2, 1),
                        "n1-boss" to performance("n1-boss", 2, 1),
                        "n2-boss" to performance("n2-boss", 2, 1),
                    ),
            )
        val schedulerA =
            DefaultSchedulerService(
                contentRepository = FakeContentRepository(nodes = listOf(nodeA, nodeB), items = items, units = listOf(unit)),
                progressRepository = FakeProgressRepository(),
                eventRepository = eventRepository,
                settingsRepository = FakeSettingsRepository(),
                idProvider = object : IdProvider { override fun newId(): String = "id" },
                timeProvider = object : TimeProvider { override fun now(): Long = 10_000L },
            )
        val schedulerB =
            DefaultSchedulerService(
                contentRepository = FakeContentRepository(nodes = listOf(nodeA, nodeB), items = items, units = listOf(unit)),
                progressRepository = FakeProgressRepository(),
                eventRepository = eventRepository,
                settingsRepository = FakeSettingsRepository(),
                idProvider = object : IdProvider { override fun newId(): String = "id" },
                timeProvider = object : TimeProvider { override fun now(): Long = 20_000L },
            )

        val packetA = schedulerA.buildDrainPacket("u1")
        val packetB = schedulerB.buildDrainPacket("u1")

        assertThat(packetA).isNotNull()
        assertThat(packetB).isNotNull()
        val firstDifficultyA = packetA!!.initialItemIds.take(2)
        val secondDifficultyA = packetA.initialItemIds.drop(2).take(4)
        val thirdDifficultyA = packetA.initialItemIds.takeLast(4)
        assertThat(firstDifficultyA).containsExactly("n1-core", "n2-core")
        assertThat(secondDifficultyA).containsExactly("n1-variant", "n2-variant", "n1-trap", "n2-trap")
        assertThat(thirdDifficultyA).containsExactly("n1-integration", "n2-integration", "n1-boss", "n2-boss")
        assertThat(packetA.initialItemIds).doesNotContain("n1-rescue")
        assertThat(packetA.initialItemIds).doesNotContain("n2-rescue")
        assertThat(packetB!!.initialItemIds.take(2)).containsExactly("n1-core", "n2-core")
        assertThat(packetA.initialItemIds).isNotEqualTo(packetB.initialItemIds)
    }
}

private class FakeContentRepository(
    private val nodes: List<Node>,
    private val items: List<Item>,
    private val units: List<UnitModel>,
) : ContentRepository {
    override suspend fun seedDemoIfNeeded() = Unit
    override suspend fun importContentPackage(
        rawJson: String,
        sourceLabel: String,
        validationProfile: ImportValidationProfile,
    ) = ImportExecutionResult("pkg", false, ValidationReport(emptyList(), emptyList()))
    override suspend fun importContentPackage(uri: Uri) = ImportExecutionResult("pkg", false, ValidationReport(emptyList(), emptyList()))
    override suspend fun prepareContentPackage(uri: Uri) = throw UnsupportedOperationException()
    override suspend fun getContentTree(): ContentTree =
        ContentTree(
            courses =
                listOf(
                    CourseWithUnits(
                        course = Course("c1", "Curso", null, 1, 1L),
                        units =
                            units.map { unit ->
                                UnitWithOutcomes(
                                    unit = unit,
                                    outcomes =
                                        listOf(
                                            OutcomeWithNodes(
                                                outcome = Outcome("o1-${unit.unitId}", unit.unitId, "Outcome", null, 1, 1L),
                                                nodes = nodes.filter { it.unitId == unit.unitId },
                                            ),
                                        ),
                                )
                            },
                    ),
                ),
        )

    override suspend fun getUnits(): List<UnitModel> = units
    override suspend fun getSchedulableNodes(): List<Node> = nodes
    override suspend fun getSchedulableNodesByUnit(unitId: String): List<Node> = nodes.filter { it.unitId == unitId }
    override suspend fun getNode(nodeId: String): Node? = nodes.firstOrNull { it.nodeId == nodeId }
    override suspend fun getNodeDetail(nodeId: String, includeArchivedItems: Boolean): NodeDetail? =
        getNode(nodeId)?.let { NodeDetail(it, items.filter { item -> item.nodeId == nodeId }) }
    override suspend fun getItem(itemId: String, includeArchived: Boolean): Item? = items.firstOrNull { it.itemId == itemId }
    override suspend fun getItemsForNode(nodeId: String, includeArchived: Boolean): List<Item> = items.filter { it.nodeId == nodeId }
    override suspend fun getItemsForNodes(nodeIds: List<String>, includeArchived: Boolean): List<Item> = items.filter { it.nodeId in nodeIds }
    override suspend fun setArchivedCandidate(nodeId: String, archived: Boolean) = Unit
    override suspend fun hasRealImportedContent(): Boolean = true
    override suspend fun getContentPackage(rawJson: String) = throw UnsupportedOperationException()
}

private class FakeProgressRepository : ProgressRepository {
    override suspend fun getNodeState(nodeId: String): NodeState? = null
    override suspend fun getAllNodeStates(): List<NodeState> = emptyList()
    override suspend fun getAllFormatStats(): List<NodeFormatStat> = emptyList()
    override suspend fun upsertNodeState(nodeState: NodeState) = Unit
    override suspend fun getFormatStats(nodeId: String): List<NodeFormatStat> = emptyList()
    override suspend fun upsertFormatStats(stats: List<NodeFormatStat>) = Unit
    override suspend fun clearAllProgress() = Unit
    override suspend fun resetNodeProgress(nodeId: String) = Unit
}

private class FakeEventRepository(
    private val itemPerformance: Map<String, ItemPerformanceSummary> = emptyMap(),
) : EventRepository {
    override suspend fun recordReviewEvent(event: ReviewEvent) = Unit
    override suspend fun recordSessionEvent(event: SessionEvent) = Unit
    override suspend fun recordAbandonEvent(event: AbandonEvent) = Unit
    override suspend fun recordImportEvent(event: ImportEvent) = Unit
    override suspend fun getRecentTopicIds(): List<String> = emptyList()
    override suspend fun getAbandonEventsSince(since: Long): List<AbandonEvent> = emptyList()
    override suspend fun getLatestImportEvent(packageId: String): ImportEvent? = null
    override suspend fun getItemPerformance(itemIds: List<String>): Map<String, ItemPerformanceSummary> =
        itemPerformance.filterKeys { it in itemIds }
}

private fun performance(itemId: String, attempts: Int, successes: Int): ItemPerformanceSummary =
    ItemPerformanceSummary(itemId = itemId, attempts = attempts, successes = successes)

private class FakeSettingsRepository : SettingsRepository {
    override fun observeSettings(): Flow<AppSettings> = flowOf(AppSettings())
    override suspend fun getSettings(): AppSettings = AppSettings()
    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) = Unit
}

private class FixedSettingsRepository(
    private val settings: AppSettings,
) : SettingsRepository {
    override fun observeSettings(): Flow<AppSettings> = flowOf(settings)
    override suspend fun getSettings(): AppSettings = settings
    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) = Unit
}

private fun testNode(nodeId: String, unitId: String, weightExam: Double): Node =
    Node(
        nodeId = nodeId,
        courseId = "c1",
        unitId = unitId,
        outcomeIds = listOf("o1-$unitId"),
        title = "Nodo $nodeId",
        coreClaim = "Claim",
        type = NodeType.CONCEPT,
        weightExam = weightExam,
        prerequisites = emptyList(),
        facets = listOf(FacetType.DEFINICION_FUNCIONAL),
        mustKnow = listOf("mk"),
        commonErrors = listOf("ce"),
        minimumMasteryDefinition = "mm",
        surfaceEasyReady = true,
        surfaceEasyItemCount = 4,
        sourceRefs = emptyList(),
        version = 1,
        updatedAt = 1L,
        archivedCandidate = false,
        contentOrigin = ContentOrigin.IMPORTED,
    )

private fun testItem(
    id: String,
    nodeId: String,
    friction: Int,
    role: ItemRole,
    surfaces: List<Surface> = listOf(Surface.IN_APP_QUICK, Surface.IN_APP_DEEP, Surface.BACK_MICRO, Surface.NOTIFICATION),
    format: ItemFormat = ItemFormat.MULTIPLE_CHOICE,
): Item =
    Item(
        itemId = id,
        nodeId = nodeId,
        facet = FacetType.DEFINICION_FUNCIONAL,
        format = format,
        frictionLevel = friction,
        difficultySeed = 0.2,
        itemRole = role,
        allowedSurfaces = surfaces,
        cooldownHours = 1.0,
        stem = id,
        correctAnswer = "ok",
        feedbackShort = "fb",
        coversMustKnow = listOf("mk"),
        variantGroupId = null,
        rescueGroupId = null,
        nodeComplexity = null,
        facetComplexity = null,
        distractorSimilarity = null,
        prerequisiteDepth = null,
        targetsErrorIds = emptyList(),
        commonErrorSignals = emptyList(),
        options = listOf(ItemOption("a", "ok", true)),
        version = 1,
        updatedAt = 1L,
        sourceRefs = emptyList(),
        contentOrigin = ContentOrigin.IMPORTED,
    )
