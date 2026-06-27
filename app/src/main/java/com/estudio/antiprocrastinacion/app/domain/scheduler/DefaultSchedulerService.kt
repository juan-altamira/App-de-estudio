package com.estudio.antiprocrastinacion.app.domain.scheduler

import com.estudio.antiprocrastinacion.app.domain.repository.ContentRepository
import com.estudio.antiprocrastinacion.app.domain.repository.EventRepository
import com.estudio.antiprocrastinacion.app.domain.repository.ProgressRepository
import com.estudio.antiprocrastinacion.app.domain.repository.SettingsRepository
import com.estudio.antiprocrastinacion.app.model.content.ContentOrigin
import com.estudio.antiprocrastinacion.app.model.content.Item
import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.ItemRole
import com.estudio.antiprocrastinacion.app.model.content.Node
import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.content.Surface
import com.estudio.antiprocrastinacion.app.model.state.ItemState
import com.estudio.antiprocrastinacion.app.model.state.NodeState
import com.estudio.antiprocrastinacion.app.model.state.PendingPhase
import com.estudio.antiprocrastinacion.app.model.state.SessionPromptKind
import com.estudio.antiprocrastinacion.app.model.state.SessionScoring
import com.estudio.antiprocrastinacion.app.model.state.StudySessionPayload
import com.estudio.antiprocrastinacion.app.model.state.TopicPacket
import com.estudio.antiprocrastinacion.app.ui.common.IdProvider
import com.estudio.antiprocrastinacion.app.ui.common.TimeProvider
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class DefaultSchedulerService @Inject constructor(
    private val contentRepository: ContentRepository,
    private val progressRepository: ProgressRepository,
    private val eventRepository: EventRepository,
    private val settingsRepository: SettingsRepository,
    private val idProvider: IdProvider,
    private val timeProvider: TimeProvider,
) : SchedulerService {
    override suspend fun buildQuickPacket(
        preferredUnitId: String?,
        preferredItemId: String?,
    ): TopicPacket? =
        buildPendingPacket(
            surface = Surface.IN_APP_QUICK,
            preferredUnitId = preferredUnitId,
            preferredItemId = preferredItemId,
        )

    override suspend fun buildNotificationPacket(
        preferredUnitId: String?,
        preferredItemId: String?,
    ): TopicPacket? =
        buildPendingPacket(
            surface = Surface.NOTIFICATION,
            preferredUnitId = preferredUnitId,
            preferredItemId = preferredItemId,
        )

    override suspend fun buildSocialGatePacket(
        @Suppress("UNUSED_PARAMETER") goalCorrectCount: Int,
    ): TopicPacket? {
        val now = timeProvider.now()
        val activeNodes = contentRepository.getSchedulableNodes()
        if (activeNodes.isEmpty()) return null

        val tree = contentRepository.getContentTree()
        val courseTitles = tree.courses.associate { it.course.courseId to it.course.title }
        val unitTitles = tree.courses.flatMap { course -> course.units.map { unit -> unit.unit.unitId to unit.unit.title } }.toMap()
        val allItems = contentRepository.getItemsForNodes(activeNodes.map { it.nodeId })
        val itemStates = progressRepository.getAllItemStates().associateBy { it.itemId }
        val nodeById = activeNodes.associateBy { it.nodeId }

        val realGateItems =
            allItems
                .filter { item ->
                    isRealItem(item) &&
                        Surface.SOCIAL_GATE in item.allowedSurfaces &&
                        isStrictFrictionOne(item)
                }

        val pendingReal = realGateItems.filter { item -> isDue(itemStates[item.itemId], now) }

        if (pendingReal.isEmpty()) return null

        // Legacy packet builder kept for older callers/tests. The runtime gate now opens/reuses the real
        // QUICK session, but this builder must still be real-only: no future cards and no gate-only fillers.
        val orderedRealQueue =
            buildOrderedSocialGateRealQueue(
                pendingReal = pendingReal,
                nodeById = nodeById,
            )
        val firstItem = orderedRealQueue.firstOrNull() ?: return null
        val firstNode = nodeById[firstItem.nodeId] ?: return null

        return TopicPacket(
            packetId = idProvider.newId(),
            topicUnitId = firstNode.unitId,
            currentCourseTitle = courseTitles[firstNode.courseId].orEmpty(),
            currentTopicTitle = unitTitles[firstNode.unitId].orEmpty(),
            surface = Surface.SOCIAL_GATE,
            sessionMode = SessionMode.QUICK,
            nodeIds = orderedRealQueue.map(Item::nodeId).distinct(),
            initialItemIds = orderedRealQueue.map(Item::itemId),
            rescueItemIds = emptyList(),
            goalCorrectCount = orderedRealQueue.size,
            priorityScore = 1.0,
            initialPromptKind = SessionPromptKind.REAL_GATE,
            payload = StudySessionPayload(pendingPhase = PendingPhase.FIRST_PASS),
        )
    }

    override suspend fun buildDeepPacket(unitId: String): TopicPacket? {
        val nodes = contentRepository.getSchedulableNodesByUnit(unitId)
        if (nodes.isEmpty()) return null

        val states = progressRepository.getAllNodeStates().associateBy { it.nodeId }
        val recentTopics = eventRepository.getRecentTopicIds()
        val recentAbandons =
            eventRepository
                .getAbandonEventsSince(timeProvider.now() - DAYS_14_MS)
                .groupingBy { it.nodeId.orEmpty() }
                .eachCount()
        val importedExists = contentRepository.hasRealImportedContent()
        val settings = settingsRepository.getSettings()
        val titles = titlesForUnit(unitId)

        val candidates =
            nodes.mapNotNull { node ->
                val items = contentRepository.getItemsForNode(node.nodeId).forDeep()
                if (items.isEmpty()) return@mapNotNull null
                // Modo profundo es estudio manual de una unidad elegida: no rankeamos por atraso, así que
                // mantenemos el peso de due completo (dueScore = 1.0) pasando el horizonte.
                val scoring = computeScoring(node, states[node.nodeId], recentAbandons[node.nodeId] ?: 0, recentTopics, importedExists, overdueHours = DUE_SCORE_HORIZON_HOURS)
                ScoredNode(node, scoring, items)
            }
        if (candidates.isEmpty()) return null

        val prioritizedNodes = candidates.sortedByDescending { it.scoring.priority }
        val chosenItems = linkedSetOf<Item>()
        val targetSize = settings.deepSessionQuestionTarget

        fun addIfPossible(item: Item?) {
            if (item != null && chosenItems.size < targetSize) {
                chosenItems.add(item)
            }
        }

        for (candidate in prioritizedNodes) {
            addIfPossible(candidate.items.firstOrNull { it.itemRole == ItemRole.CORE })
            if (chosenItems.size >= targetSize) break
        }
        for (candidate in prioritizedNodes) {
            if (chosenItems.size >= targetSize) break
            if (candidate.items.none { it.itemRole == ItemRole.CORE }) {
                addIfPossible(candidate.items.firstOrNull { it.itemRole == ItemRole.VARIANT })
            }
        }
        for (candidate in prioritizedNodes) {
            addIfPossible(candidate.items.firstOrNull { it.itemRole == ItemRole.TRAP })
            if (chosenItems.size >= targetSize) break
        }
        for (candidate in prioritizedNodes) {
            addIfPossible(candidate.items.firstOrNull { it.itemRole == ItemRole.INTEGRATION })
            if (chosenItems.size >= targetSize) break
        }
        for (candidate in prioritizedNodes) {
            addIfPossible(candidate.items.firstOrNull { it.itemRole == ItemRole.BOSS })
            if (chosenItems.size >= targetSize) break
        }
        for (candidate in prioritizedNodes) {
            for (item in candidate.items.sortedWith(compareBy<Item> { deepFillRoleOrder(it.itemRole) }.thenBy { it.frictionLevel })) {
                addIfPossible(item)
                if (chosenItems.size >= targetSize) break
            }
            if (chosenItems.size >= targetSize) break
        }

        val initialItems =
            chosenItems
                .toList()
                .sortedWith(compareBy<Item> { it.frictionLevel }.thenBy { drainRoleOrder(it.itemRole) })
        val firstItem = initialItems.firstOrNull() ?: return null
        return TopicPacket(
            packetId = idProvider.newId(),
            topicUnitId = unitId,
            currentCourseTitle = titles.courseTitle,
            currentTopicTitle = titles.unitTitle,
            surface = Surface.IN_APP_DEEP,
            sessionMode = SessionMode.DEEP,
            nodeIds = prioritizedNodes.map { it.node.nodeId },
            initialItemIds = initialItems.map(Item::itemId),
            rescueItemIds = emptyList(),
            goalCorrectCount = initialItems.size,
            priorityScore = prioritizedNodes.first().scoring.priority,
            initialPromptKind = SessionPromptKind.MANUAL,
            payload = StudySessionPayload(pendingPhase = PendingPhase.FIRST_PASS),
        )
    }

    override suspend fun buildDrainPacket(unitId: String): TopicPacket? {
        val nodes = contentRepository.getSchedulableNodesByUnit(unitId)
        if (nodes.isEmpty()) return null
        val itemIds = buildFullUnitOrderedItemIds(unitId, socialGateMode = false)
        if (itemIds.isEmpty()) return null
        val titles = titlesForUnit(unitId)
        return TopicPacket(
            packetId = idProvider.newId(),
            topicUnitId = unitId,
            currentCourseTitle = titles.courseTitle,
            currentTopicTitle = titles.unitTitle,
            surface = Surface.IN_APP_DEEP,
            sessionMode = SessionMode.DRAIN,
            nodeIds = nodes.map(Node::nodeId),
            initialItemIds = itemIds,
            rescueItemIds = emptyList(),
            goalCorrectCount = itemIds.size,
            priorityScore = 1.0,
            initialPromptKind = SessionPromptKind.MANUAL,
            payload = StudySessionPayload(pendingPhase = PendingPhase.FIRST_PASS),
        )
    }

    override suspend fun getAvailableUnits(): List<DeepModeUnitOption> {
        val tree = contentRepository.getContentTree()
        val counts = contentRepository.getSchedulableNodes().groupBy { it.unitId }.mapValues { it.value.size }
        return tree.courses.flatMap { course ->
            course.units.mapNotNull { unit ->
                val count = counts[unit.unit.unitId] ?: 0
                if (count == 0) return@mapNotNull null
                DeepModeUnitOption(
                    unitId = unit.unit.unitId,
                    courseTitle = course.course.title,
                    unitTitle = unit.unit.title,
                    availableNodeCount = count,
                )
            }
        }
    }

    private suspend fun buildPendingPacket(
        surface: Surface,
        preferredUnitId: String?,
        preferredItemId: String?,
    ): TopicPacket? {
        val now = timeProvider.now()
        val nodes = contentRepository.getSchedulableNodes()
        if (nodes.isEmpty()) return null

        val allItems = contentRepository.getItemsForNodes(nodes.map { it.nodeId })
        val itemStates = progressRepository.getAllItemStates().associateBy { it.itemId }
        val nodeStates = progressRepository.getAllNodeStates().associateBy { it.nodeId }
        val recentTopics = eventRepository.getRecentTopicIds()
        val recentAbandons =
            eventRepository
                .getAbandonEventsSince(timeProvider.now() - DAYS_14_MS)
                .groupingBy { it.nodeId.orEmpty() }
                .eachCount()
        val importedExists = contentRepository.hasRealImportedContent()
        val tree = contentRepository.getContentTree()
        val courseTitles = tree.courses.associate { it.course.courseId to it.course.title }
        val unitTitles = tree.courses.flatMap { course -> course.units.map { unit -> unit.unit.unitId to unit.unit.title } }.toMap()

        val nodeCandidates =
            nodes.mapNotNull { node ->
                val realPendingItems =
                    allItems
                        .asSequence()
                        .filter { it.nodeId == node.nodeId }
                        .filter { isRealPendingQuickItem(it, itemStates[it.itemId], now) }
                        .sortedWith(compareBy<Item> { it.frictionLevel }.thenBy { realRoleOrder(it.itemRole) })
                        .toList()
                if (realPendingItems.isEmpty()) return@mapNotNull null
                val scoring =
                    computeScoring(
                        node = node,
                        state = nodeStates[node.nodeId],
                        recentAbandons = recentAbandons[node.nodeId] ?: 0,
                        recentTopics = recentTopics,
                        importedExists = importedExists,
                        overdueHours = maxOverdueHours(realPendingItems, itemStates, now),
                    )
                PendingNodeCandidate(node, scoring, realPendingItems)
            }
        if (nodeCandidates.isEmpty()) return null

        val unitPlans =
            nodeCandidates
                .groupBy { it.node.unitId }
                .values
                .mapNotNull { unitCandidates ->
                    buildPendingUnitPlan(
                        unitCandidates = unitCandidates,
                        allItems = allItems,
                        courseTitles = courseTitles,
                        unitTitles = unitTitles,
                        surface = surface,
                        itemStates = itemStates,
                        now = now,
                    )
                }
        if (unitPlans.isEmpty()) return null

        val selectedPlan =
            selectPendingUnitPlan(
                plans = unitPlans,
                preferredUnitId = preferredUnitId,
                preferredItemId = preferredItemId,
            ) ?: return null

        return TopicPacket(
            packetId = idProvider.newId(),
            topicUnitId = selectedPlan.unitId,
            currentCourseTitle = selectedPlan.courseTitle,
            currentTopicTitle = selectedPlan.unitTitle,
            surface = surface,
            sessionMode = SessionMode.QUICK,
            nodeIds = selectedPlan.nodeIds,
            initialItemIds = selectedPlan.initialItemIds,
            rescueItemIds = selectedPlan.rescueItemIds,
            goalCorrectCount = selectedPlan.goalCorrectCount,
            priorityScore = selectedPlan.priorityScore,
            initialPromptKind = selectedPlan.initialPromptKind,
            payload =
                StudySessionPayload(
                    pendingPhase = PendingPhase.FIRST_PASS,
                ),
        )
    }

    private fun buildPendingUnitPlan(
        unitCandidates: List<PendingNodeCandidate>,
        allItems: List<Item>,
        courseTitles: Map<String, String>,
        unitTitles: Map<String, String>,
        surface: Surface,
        itemStates: Map<String, ItemState>,
        now: Long,
    ): PendingUnitPlan? {
        val orderedCandidates =
            unitCandidates.sortedWith(
                compareBy<PendingNodeCandidate> { it.items.first().frictionLevel }
                    .thenByDescending { it.scoring.priority },
            )
        val anchor = orderedCandidates.maxByOrNull { it.scoring.priority } ?: return null
        val unitNodeIds = orderedCandidates.map { it.node.nodeId }
        val unitItems = allItems.filter { it.nodeId in unitNodeIds }
        val realPendingItems =
            orderedCandidates
                .flatMap { it.items }
                .distinctBy(Item::itemId)
                .sortedWith(compareBy<Item> { it.frictionLevel }.thenBy { realRoleOrder(it.itemRole) })
        if (realPendingItems.isEmpty()) return null

        val realPendingIds = realPendingItems.map(Item::itemId).toSet()
        // Easy (strict friction-1) in-app cards of this unit are the frustration-breaking rescue/anzuelo
        // pool: dedicated RESCUE-role cards first, then ordinary easy cards. They are shown as auxiliary
        // prompts only, so they never affect spaced repetition. This is what lets a unit made of easy +
        // open-recall cards (with no dedicated RESCUE items) still rescue the user after repeated errors.
        // Cards engaged within their cooldownHours are excluded so a card just answered does not reappear
        // as anzuelo/rescate the same day (bug del punto 1).
        val easyCards =
            unitItems
                .filter { isStrictFrictionOne(it) && Surface.IN_APP_QUICK in it.allowedSurfaces && !isInCooldown(it, itemStates[it.itemId], now) }
                .distinctBy(Item::itemId)
                .sortedWith(
                    compareBy<Item> { if (it.itemRole == ItemRole.RESCUE) 0 else 1 }
                        .thenBy { it.stem.lowercase(Locale.US) },
                )

        val firstRealItem = realPendingItems.first()
        val needsAnzuelo = !isStrictFrictionOne(firstRealItem)
        // Anzuelo: open on an easy card that is not itself due (so we never pre-show real debt). If none
        // exists, behaviour depends on the surface: the in-app daily review still shows the real card
        // (open-recall content must never silently vanish), while external low-friction surfaces
        // (notification/alarm/widget) skip the unit instead of pushing a heavy card cold.
        val anzuelo = if (needsAnzuelo) easyCards.firstOrNull { it.itemId !in realPendingIds } else null
        val firstPrompt =
            anzuelo
                ?: if (needsAnzuelo && surface != Surface.IN_APP_QUICK) {
                    return null
                } else {
                    firstRealItem
                }

        return PendingUnitPlan(
            unitId = anchor.node.unitId,
            courseTitle = courseTitles[anchor.node.courseId].orEmpty(),
            unitTitle = unitTitles[anchor.node.unitId].orEmpty(),
            nodeIds = unitNodeIds,
            initialItemIds =
                if (firstPrompt.itemId == firstRealItem.itemId) {
                    realPendingItems.map(Item::itemId)
                } else {
                    listOf(firstPrompt.itemId) + realPendingItems.map(Item::itemId)
                },
            // Rescue pool stays available regardless of due-ness: by the time a rescue fires (repeated
            // errors on a real card) the easy cards have already been answered, so re-showing one as a
            // low-friction breather is harmless and never double-counts in spaced repetition.
            rescueItemIds = easyCards.map(Item::itemId),
            goalCorrectCount = realPendingItems.size,
            priorityScore = anchor.scoring.priority,
            initialPromptKind =
                if (firstPrompt.itemId == firstRealItem.itemId) {
                    SessionPromptKind.REAL_PENDING
                } else {
                    SessionPromptKind.AUXILIARY_ANZUELO
                },
            firstPromptItemId = firstPrompt.itemId,
        )
    }

    private fun selectPendingUnitPlan(
        plans: List<PendingUnitPlan>,
        preferredUnitId: String?,
        preferredItemId: String?,
    ): PendingUnitPlan? {
        val preferredByItem =
            preferredItemId?.let { itemId ->
                plans.firstOrNull { plan ->
                    (preferredUnitId == null || plan.unitId == preferredUnitId) &&
                        plan.firstPromptItemId == itemId
                }
            }
        if (preferredByItem != null) return preferredByItem
        val preferredByUnit = preferredUnitId?.let { unitId -> plans.firstOrNull { it.unitId == unitId } }
        return preferredByUnit ?: plans.maxByOrNull { it.priorityScore }
    }

    private suspend fun titlesForUnit(unitId: String): UnitTitles {
        val tree = contentRepository.getContentTree()
        tree.courses.forEach { course ->
            course.units.firstOrNull { it.unit.unitId == unitId }?.let { unit ->
                return UnitTitles(courseTitle = course.course.title, unitTitle = unit.unit.title)
            }
        }
        return UnitTitles(courseTitle = "", unitTitle = unitId)
    }

    private fun buildOrderedSocialGateRealQueue(
        pendingReal: List<Item>,
        nodeById: Map<String, Node>,
    ): List<Item> {
        val seed = timeProvider.now()
        val pendingByUnit = pendingReal.groupBy { nodeById.getValue(it.nodeId).unitId }
        val unitIds = pendingByUnit.keys.toList()
        if (unitIds.isEmpty()) return emptyList()

        val unitsByCourse =
            unitIds.groupBy { unitId ->
                val anyItem = pendingByUnit.getValue(unitId).first()
                nodeById.getValue(anyItem.nodeId).courseId
            }

        val orderedCourseIds =
            unitsByCourse.entries
                .sortedWith(compareByDescending<Map.Entry<String, List<String>>> { entry ->
                    entry.value.sumOf { unitId -> pendingByUnit[unitId]?.size ?: 0 }
                }.thenBy { it.key })
                .map { it.key }

        val orderedUnitIds =
            orderedCourseIds.flatMap { courseId ->
                unitsByCourse.getValue(courseId)
                    .sortedWith(compareByDescending<String> { unitId -> pendingByUnit[unitId]?.size ?: 0 }.thenBy { it })
                    .shuffled(Random(seed xor courseId.hashCode().toLong()))
            }

        return orderedUnitIds.flatMap { unitId ->
            pendingByUnit[unitId].orEmpty()
                .sortedWith(compareBy<Item> { it.frictionLevel }.thenBy { realRoleOrder(it.itemRole) })
        }
    }

    private suspend fun buildFullUnitOrderedItemIds(
        unitId: String,
        socialGateMode: Boolean,
    ): List<String> {
        val unitNodes = contentRepository.getSchedulableNodesByUnit(unitId)
        if (unitNodes.isEmpty()) return emptyList()
        val candidates =
            contentRepository.getItemsForNodes(unitNodes.map { it.nodeId })
                .filter { item ->
                    item.itemRole != ItemRole.RESCUE &&
                        if (socialGateMode) {
                            Surface.SOCIAL_GATE in item.allowedSurfaces || Surface.IN_APP_DEEP in item.allowedSurfaces
                        } else {
                            Surface.IN_APP_DEEP in item.allowedSurfaces
                        }
                }
        if (candidates.isEmpty()) return emptyList()

        val performance = eventRepository.getItemPerformance(candidates.map { it.itemId })
        val randomBaseSeed = timeProvider.now() xor unitId.hashCode().toLong() xor if (socialGateMode) 0x51A7EL else 0x0D0A1AL

        return candidates
            .groupBy { it.frictionLevel }
            .toSortedMap()
            .flatMap { (frictionLevel, sameFrictionItems) ->
                sameFrictionItems
                    .groupBy { performanceBucket(performance[it.itemId]?.successRatio) }
                    .toSortedMap()
                    .flatMap { (bucket, bucketItems) ->
                        bucketItems.shuffled(Random(randomBaseSeed xor frictionLevel.toLong() xor bucket.hashCode().toLong()))
                    }
            }
            .sortedWith(compareBy<Item> { it.frictionLevel }.thenBy { drainRoleOrder(it.itemRole) })
            .map(Item::itemId)
            .distinct()
    }

    private fun computeScoring(
        node: Node,
        state: NodeState?,
        recentAbandons: Int,
        recentTopics: List<String>,
        importedExists: Boolean,
        overdueHours: Double,
    ): SessionScoring {
        // dueScore refleja cuán atrasado está el tema (fórmula del doc): clamp(overdueHours / 72). Un tema
        // recién due aporta poco por este término y se apoya en fragilidad/fricción; uno muy atrasado lo
        // domina. Se calcula con el reloj autoritativo (ItemState), no con el NodeState "decorativo".
        val dueScore = (overdueHours / DUE_SCORE_HORIZON_HOURS).coerceIn(0.0, 1.0)
        val normalizedState = state ?: NodeState(nodeId = node.nodeId)
        val fragilityScore = (1.0 - normalizedState.memoryScore).coerce01()
        val frictionScore = normalizedState.frictionUser.coerce01()
        val abandonmentScore = (recentAbandons / 3.0).coerce01()
        val recentTopicPenalty =
            when (node.unitId) {
                recentTopics.getOrNull(0) -> 1.0
                recentTopics.getOrNull(1) -> 0.5
                else -> 0.0
            }

        var priority = (0.40 * dueScore) + (0.25 * fragilityScore) + (0.20 * frictionScore) + (0.15 * abandonmentScore) - (0.20 * recentTopicPenalty)
        if (importedExists && node.contentOrigin == ContentOrigin.DEMO) {
            priority -= 0.15
        }
        priority += node.weightExam.coerceIn(0.0, 1.0) * 0.10

        return SessionScoring(
            dueScore = dueScore,
            fragilityScore = fragilityScore,
            frictionScore = frictionScore,
            abandonmentScore = abandonmentScore,
            recentTopicPenalty = recentTopicPenalty,
            priority = priority,
        )
    }

    private fun List<Item>.forDeep(): List<Item> =
        filter { Surface.IN_APP_DEEP in it.allowedSurfaces && it.itemRole != ItemRole.RESCUE }
            .sortedWith(compareBy<Item> { drainRoleOrder(it.itemRole) }.thenBy { it.frictionLevel })

    private fun isRealPendingQuickItem(
        item: Item,
        state: ItemState?,
        now: Long,
    ): Boolean =
        isRealItem(item) &&
            Surface.IN_APP_QUICK in item.allowedSurfaces &&
            isDue(state, now)

    private fun isDue(
        state: ItemState?,
        now: Long,
    ): Boolean =
        state == null || state.stage == 0 || state.nextReviewAt == null || state.nextReviewAt <= now

    // Una tarjeta está "en cooldown" como auxiliar si fue tocada (real o auxiliar) hace menos de su
    // cooldownHours. Se usa solo para excluirla de los pools auxiliares (anzuelo/rescate/relleno), nunca
    // de la cola real. Esto evita que una tarjeta ya respondida hoy reaparezca como auxiliar el mismo día.
    private fun isInCooldown(
        item: Item,
        state: ItemState?,
        now: Long,
    ): Boolean {
        if (item.cooldownHours <= 0.0) return false
        val last = state?.lastReviewedAt ?: return false
        return now - last < (item.cooldownHours * HOUR_MS).toLong()
    }

    // Horas de atraso del ítem más atrasado entre los due reales del nodo, según el reloj autoritativo
    // (ItemState). Un ítem due sin nextReviewAt (stage 0 / nuevo) cuenta como recién due (0 h de atraso).
    private fun maxOverdueHours(
        items: List<Item>,
        itemStates: Map<String, ItemState>,
        now: Long,
    ): Double =
        items.maxOfOrNull { item ->
            val nextReviewAt = itemStates[item.itemId]?.nextReviewAt ?: return@maxOfOrNull 0.0
            (now - nextReviewAt).coerceAtLeast(0L).toDouble() / HOUR_MS
        } ?: 0.0

    private fun isRealItem(item: Item): Boolean = item.itemRole != ItemRole.RESCUE

    private fun isStrictFrictionOne(item: Item): Boolean =
        item.frictionLevel == 1 &&
            item.format in STRICT_FRICTION_ONE_FORMATS

    private fun realRoleOrder(role: ItemRole): Int =
        when (role) {
            ItemRole.CORE -> 0
            ItemRole.VARIANT -> 1
            ItemRole.TRAP -> 2
            ItemRole.INTEGRATION -> 3
            ItemRole.BOSS -> 4
            ItemRole.RESCUE -> 5
        }

    private fun drainRoleOrder(role: ItemRole): Int =
        when (role) {
            ItemRole.CORE -> 0
            ItemRole.VARIANT -> 1
            ItemRole.TRAP -> 2
            ItemRole.INTEGRATION -> 3
            ItemRole.BOSS -> 4
            ItemRole.RESCUE -> 5
        }

    private fun deepFillRoleOrder(role: ItemRole): Int =
        when (role) {
            ItemRole.TRAP -> 0
            ItemRole.INTEGRATION -> 1
            ItemRole.BOSS -> 2
            ItemRole.VARIANT -> 3
            ItemRole.CORE -> 4
            ItemRole.RESCUE -> 5
        }

    private fun performanceBucket(successRatio: Double?): String =
        when (successRatio) {
            null -> "0.5000"
            else -> "%.4f".format(Locale.US, successRatio)
        }

    private fun Double.coerce01(): Double = coerceIn(0.0, 1.0)

    private data class ScoredNode(
        val node: Node,
        val scoring: SessionScoring,
        val items: List<Item>,
    )

    private data class PendingNodeCandidate(
        val node: Node,
        val scoring: SessionScoring,
        val items: List<Item>,
    )

    private data class PendingUnitPlan(
        val unitId: String,
        val courseTitle: String,
        val unitTitle: String,
        val nodeIds: List<String>,
        val initialItemIds: List<String>,
        val rescueItemIds: List<String>,
        val goalCorrectCount: Int,
        val priorityScore: Double,
        val initialPromptKind: SessionPromptKind,
        val firstPromptItemId: String,
    )

    private data class UnitTitles(
        val courseTitle: String,
        val unitTitle: String,
    )

    private companion object {
        const val HOUR_MS = 60 * 60 * 1000L
        const val DAYS_14_MS = 14 * 24 * HOUR_MS
        // Horizonte de la fórmula del doc dueScore = clamp(overdueHours / 72): 72 h de atraso = due máximo.
        const val DUE_SCORE_HORIZON_HOURS = 72.0
        val STRICT_FRICTION_ONE_FORMATS =
            setOf(
                ItemFormat.TRUE_FALSE,
                ItemFormat.MULTIPLE_CHOICE,
                ItemFormat.CHOOSE_FALSE_STATEMENT,
                ItemFormat.MATCHING_SIMPLE,
            )
    }
}
