package com.estudio.antiprocrastinacion.app.domain.session

import androidx.room.withTransaction
import com.estudio.antiprocrastinacion.app.data.local.db.StudyDatabase
import com.estudio.antiprocrastinacion.app.domain.repository.ContentRepository
import com.estudio.antiprocrastinacion.app.domain.repository.EventRepository
import com.estudio.antiprocrastinacion.app.domain.repository.ProgressRepository
import com.estudio.antiprocrastinacion.app.domain.repository.SessionRepository
import com.estudio.antiprocrastinacion.app.domain.repository.SettingsRepository
import com.estudio.antiprocrastinacion.app.domain.scheduler.SchedulerService
import com.estudio.antiprocrastinacion.app.model.content.Item
import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.Node
import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.content.Surface
import com.estudio.antiprocrastinacion.app.model.event.AbandonEvent
import com.estudio.antiprocrastinacion.app.model.event.AbandonReason
import com.estudio.antiprocrastinacion.app.model.event.AttemptOutcome
import com.estudio.antiprocrastinacion.app.model.event.ReviewEvent
import com.estudio.antiprocrastinacion.app.model.event.SessionEvent
import com.estudio.antiprocrastinacion.app.model.event.SessionEventType
import com.estudio.antiprocrastinacion.app.model.state.ItemState
import com.estudio.antiprocrastinacion.app.model.state.PendingPhase
import com.estudio.antiprocrastinacion.app.model.state.SessionPromptKind
import com.estudio.antiprocrastinacion.app.model.state.StudyPrompt
import com.estudio.antiprocrastinacion.app.model.state.StudySession
import com.estudio.antiprocrastinacion.app.model.state.StudySessionPayload
import com.estudio.antiprocrastinacion.app.model.state.TopicPacket
import com.estudio.antiprocrastinacion.app.model.state.UserAnswer
import com.estudio.antiprocrastinacion.app.ui.common.IdProvider
import com.estudio.antiprocrastinacion.app.ui.common.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultSessionEngine @Inject constructor(
    private val schedulerService: SchedulerService,
    private val contentRepository: ContentRepository,
    private val progressRepository: ProgressRepository,
    private val sessionRepository: SessionRepository,
    private val eventRepository: EventRepository,
    private val settingsRepository: SettingsRepository,
    private val database: StudyDatabase,
    private val nodeStateUpdater: NodeStateUpdater,
    private val itemStateUpdater: ItemStateUpdater,
    private val idProvider: IdProvider,
    private val timeProvider: TimeProvider,
) : SessionEngine {
    override suspend fun startQuickSession(): StudyPrompt? {
        reusablePendingSession()?.let { return buildPromptForSession(it) }
        val packet = schedulerService.buildQuickPacket() ?: return null
        return startPacket(packet)
    }

    override suspend fun startNotificationSession(
        preferredUnitId: String?,
        preferredItemId: String?,
    ): StudyPrompt? {
        reusablePendingSession()?.let { return buildPromptForSession(it) }
        val packet =
            schedulerService.buildNotificationPacket(
                preferredUnitId = preferredUnitId,
                preferredItemId = preferredItemId,
            ) ?: return null
        return startPacket(packet)
    }

    override suspend fun startSocialGateSession(
        @Suppress("UNUSED_PARAMETER") goalCorrectCount: Int,
    ): StudyPrompt? {
        // Compatibilidad para callers antiguos: el gate social ya no tiene un carril propio ni fillers.
        // Siempre abre/reusa la sesión QUICK real de Tarjetas pendientes.
        return startQuickSession()
    }

    override suspend fun startDeepSession(unitId: String): StudyPrompt? {
        sessionRepository.getActiveSession(SessionMode.DEEP)?.let { return buildPromptForSession(it) }
        val packet = schedulerService.buildDeepPacket(unitId) ?: return null
        return startPacket(packet)
    }

    override suspend fun startDrainSession(unitId: String): StudyPrompt? {
        sessionRepository.getActiveSession(SessionMode.DRAIN)?.let { return buildPromptForSession(it) }
        val packet = schedulerService.buildDrainPacket(unitId) ?: return null
        return startPacket(packet)
    }

    override suspend fun terminateSession(sessionId: String): Boolean {
        val session = sessionRepository.getActiveSessionById(sessionId) ?: return false
        if (session.mode == SessionMode.QUICK) {
            return false
        }
        val now = timeProvider.now()
        database.withTransaction {
            sessionRepository.clearActiveSession(session.mode)
            recordSessionEvent(session, SessionEventType.SESSION_EXITED, null, now)
        }
        return true
    }

    override suspend fun resumeSession(sessionId: String): StudyPrompt? {
        val session = sessionRepository.getActiveSessionById(sessionId) ?: return null
        recordSessionEvent(
            session = session,
            type = SessionEventType.SESSION_RESUMED,
            payloadJson = null,
            now = timeProvider.now(),
        )
        return buildPromptForSession(session)
    }

    override suspend fun submitAnswer(
        sessionId: String,
        answer: UserAnswer,
    ): SessionTransition {
        val session = requireActiveSession(sessionId)
        val now = timeProvider.now()
        val item = requireCurrentItem(session)
        val node = requireCurrentNode(item)

        val transition =
            when {
                session.currentPromptKind == SessionPromptKind.BACK_MICRO -> handleBackMicroAnswer(session, answer, now)
                session.surface == Surface.SOCIAL_GATE -> handleSocialGateAnswer(session, node, item, answer, now)
                session.mode == SessionMode.QUICK -> handlePendingAnswer(session, node, item, answer, now)
                else -> handleManualAnswer(session, node, item, answer, now)
            }

        return transition
    }

    override suspend fun handleBackPressed(sessionId: String): BackPressResult {
        val session = requireActiveSession(sessionId)
        val now = timeProvider.now()
        if (session.isMicroPromptActive) {
            val exited = session.asResumableExitState(now)
            sessionRepository.saveActiveSession(exited)
            recordSessionEvent(exited, SessionEventType.SESSION_EXITED, null, now)
            return BackPressResult.Exit(sessionId)
        }

        if (session.isExitArmed && (session.exitArmedUntil ?: 0L) >= now) {
            val exited = session.asResumableExitState(now)
            sessionRepository.saveActiveSession(exited)
            recordSessionEvent(exited, SessionEventType.SESSION_EXITED, null, now)
            return BackPressResult.Exit(sessionId)
        }

        val microPrompt = createBackMicroPrompt(session, now)
        return if (microPrompt != null) {
            sessionRepository.saveActiveSession(microPrompt.session)
            recordSessionEvent(microPrompt.session, SessionEventType.BACK_MICRO_TRIGGERED, null, now)
            BackPressResult.ShowMicroPrompt(microPrompt)
        } else {
            val exited = session.asResumableExitState(now)
            sessionRepository.saveActiveSession(exited)
            recordSessionEvent(exited, SessionEventType.SESSION_EXITED, null, now)
            BackPressResult.Exit(sessionId)
        }
    }

    override suspend fun recordAbandon(
        sessionId: String,
        reason: AbandonReason,
    ) {
        val session = sessionRepository.getActiveSessionById(sessionId) ?: return
        val now = timeProvider.now()
        val updated = applyAbandonToSession(session, reason, now)
        sessionRepository.saveActiveSession(updated.asResumableExitState(now))
        recordSessionEvent(updated, SessionEventType.SESSION_EXITED, null, now)
    }

    override suspend fun handleInactivityTimeout(sessionId: String): BackPressResult {
        val session = sessionRepository.getActiveSessionById(sessionId) ?: return BackPressResult.Exit(sessionId)
        val now = timeProvider.now()
        val updated = applyAbandonToSession(session, AbandonReason.TIMEOUT, now)
        sessionRepository.saveActiveSession(updated.asResumableExitState(now))
        recordSessionEvent(updated, SessionEventType.SESSION_EXITED, null, now)
        return BackPressResult.Exit(sessionId)
    }

    // Devuelve la sesión QUICK activa SOLO si todavía se puede reutilizar. Si una sesión de pendientes
    // quedó con ítems ya no servibles (p. ej. su curso fue archivado después de crearse), la descarta
    // para que se reconstruya una fresca que excluye lo archivado → nunca se sirven tarjetas archivadas.
    private suspend fun reusablePendingSession(): StudySession? {
        val existing = sessionRepository.getActiveSession(SessionMode.QUICK) ?: return null
        if (existing.surface == Surface.SOCIAL_GATE) return existing // el gate maneja su propio contenido
        if (pendingSessionServable(existing)) return existing
        sessionRepository.clearActiveSession(SessionMode.QUICK)
        return null
    }

    private suspend fun pendingSessionServable(session: StudySession): Boolean {
        val ids =
            (listOfNotNull(session.currentItemId) + session.queueItemIds + session.payload.correctionQueueItemIds)
                .distinct()
        if (ids.isEmpty()) return true
        val schedulableNodeIds = contentRepository.getSchedulableNodes().map { it.nodeId }
        val servableItemIds = contentRepository.getItemsForNodes(schedulableNodeIds).map { it.itemId }.toSet()
        return ids.all { it in servableItemIds }
    }

    private suspend fun startPacket(packet: TopicPacket): StudyPrompt? {
        val now = timeProvider.now()
        var session =
            StudySession(
                sessionId = idProvider.newId(),
                mode = packet.sessionMode,
                surface = packet.surface,
                packetId = packet.packetId,
                topicUnitId = packet.topicUnitId,
                currentCourseTitle = packet.currentCourseTitle,
                currentTopicTitle = packet.currentTopicTitle,
                queueItemIds = packet.initialItemIds.drop(1),
                rescueQueueItemIds = packet.rescueItemIds,
                goalCorrectCount = packet.goalCorrectCount,
                currentNodeId = null,
                currentItemId = packet.initialItemIds.firstOrNull(),
                currentAttemptIndex = 1,
                currentPromptKind = packet.initialPromptKind,
                correctCount = 0,
                stepIndex = 0,
                startedAt = now,
                lastInteractionAt = now,
                isMicroPromptActive = false,
                isExitArmed = false,
                exitArmedUntil = null,
                payload = packet.payload,
            )
        database.withTransaction {
            session = recordRealExposureIfNeeded(session)
            sessionRepository.saveActiveSession(session)
            recordSessionEvent(session, SessionEventType.SESSION_STARTED, null, now)
        }
        val prompt = buildPromptForSession(session) ?: return null
        sessionRepository.saveActiveSession(prompt.session)
        return prompt
    }

    private suspend fun buildPromptForSession(session: StudySession): StudyPrompt? {
        val itemId = session.currentItemId ?: return null
        val item = contentRepository.getItem(itemId) ?: return null
        val node = contentRepository.getNode(item.nodeId) ?: return null
        val sessionWithNode = session.copy(currentNodeId = node.nodeId)
        // Cuando la tarjeta actual es un rescate, resolvemos los stems de las tarjetas reales aún falladas
        // que lo dispararon, para que la UI pueda explicar en "ver detalle" por qué apareció.
        val auxiliaryTriggerStems =
            if (session.currentPromptKind == SessionPromptKind.AUXILIARY_RESCUE) {
                session.payload.failCountsByItemId.keys.mapNotNull { contentRepository.getItem(it)?.stem }
            } else {
                emptyList()
            }
        // Barajamos las opciones SOLO para la presentación (copia), determinístico por
        // (sessionId, itemId, attemptIndex): estable dentro de la misma presentación, distinto en
        // cada review. No toca el Item guardado ni la calificación (cada opción lleva su isCorrect).
        val presentedItem =
            item.copy(
                options =
                    OptionOrder.shuffledForPresentation(
                        options = item.options,
                        sessionId = sessionWithNode.sessionId,
                        itemId = item.itemId,
                        attemptIndex = attemptIndexFor(item.itemId, sessionWithNode.payload),
                    ),
            )
        return StudyPrompt(
            session = sessionWithNode,
            node = node,
            item = presentedItem,
            promptIndex = promptIndexFor(sessionWithNode),
            pendingFailedItemCount = sessionWithNode.payload.correctionQueueItemIds.size,
            auxiliaryTriggerStems = auxiliaryTriggerStems,
        )
    }

    private suspend fun handlePendingAnswer(
        session: StudySession,
        node: Node,
        item: Item,
        answer: UserAnswer,
        now: Long,
    ): SessionTransition =
        if (session.currentPromptKind == SessionPromptKind.AUXILIARY_ANZUELO || session.currentPromptKind == SessionPromptKind.AUXILIARY_RESCUE) {
            handlePendingAuxiliaryAnswer(session, item, answer, now)
        } else {
            handlePendingRealAnswer(session, node, item, answer, now)
        }

    private suspend fun handlePendingAuxiliaryAnswer(
        session: StudySession,
        item: Item,
        answer: UserAnswer,
        now: Long,
    ): SessionTransition {
        val payload = session.payload
        val usedAuxiliaries = (payload.usedAuxiliaryItemIds + item.itemId).distinct()
        val vetoedAuxiliaries =
            if (answer.isCorrect) {
                payload.vetoedAuxiliaryItemIds
            } else {
                (payload.vetoedAuxiliaryItemIds + item.itemId).distinct()
            }
        val updatedPayload =
            payload.copy(
                consecutiveErrorCount = if (answer.isCorrect) 0 else payload.consecutiveErrorCount + 1,
                usedAuxiliaryItemIds = usedAuxiliaries,
                vetoedAuxiliaryItemIds = vetoedAuxiliaries,
                suspendedItemId = null,
                suspendedPromptKind = null,
            )
        // Sella la exposición auxiliar para que el cooldown la frene de reaparecer como auxiliar el mismo
        // día (no afecta la repetición espaciada).
        progressRepository.upsertItemState(
            itemStateUpdater.recordAuxiliaryExposure(item.itemId, progressRepository.getItemState(item.itemId), now),
        )
        val resumed = resumeAfterAuxiliary(session.copy(payload = updatedPayload, lastInteractionAt = now), now)
        return persistAndAdvance(resumed, now)
    }

    private suspend fun handlePendingRealAnswer(
        session: StudySession,
        node: Node,
        item: Item,
        answer: UserAnswer,
        now: Long,
    ): SessionTransition {
        val payload = session.payload
        val previousFailures = payload.failCountsByItemId[item.itemId] ?: 0

        if (answer.isCorrect) {
            val attemptOutcome =
                if (previousFailures == 0) {
                    AttemptOutcome.CORRECT_FIRST_TRY
                } else {
                    AttemptOutcome.CORRECT_AFTER_RESCUE
                }
            val updatedNodeState =
                nodeStateUpdater.applyAnswer(
                    current = progressRepository.getNodeState(node.nodeId),
                    item = item,
                    outcome = attemptOutcome,
                    latencyMs = answer.latencyMs,
                    surface = session.surface,
                    sessionMode = session.mode,
                    now = now,
                )
            val updatedFormatStat =
                nodeStateUpdater.updateFormatStat(
                    current = progressRepository.getFormatStats(node.nodeId).firstOrNull { it.format == item.format },
                    item = item,
                    success = true,
                    latencyMs = answer.latencyMs,
                )
            val updatedItemState =
                itemStateUpdater.applyCorrectAnswer(
                    itemId = item.itemId,
                    current = progressRepository.getItemState(item.itemId),
                    inSessionFailureCount = previousFailures,
                    now = now,
                )

            val updatedPayload =
                payload.copy(
                    correctionQueueItemIds = payload.correctionQueueItemIds.filterNot { it == item.itemId },
                    failCountsByItemId = payload.failCountsByItemId - item.itemId,
                    consecutiveErrorCount = 0,
                    resolvedRealItemIds = (payload.resolvedRealItemIds + item.itemId).distinct(),
                )

            val advanced =
                session.copy(
                    surface = normalizedPendingSurface(session.surface),
                    correctCount = session.correctCount + 1,
                    stepIndex = session.stepIndex + 1,
                    lastInteractionAt = now,
                    payload = updatedPayload,
                    isExitArmed = false,
                    exitArmedUntil = null,
                )

            database.withTransaction {
                progressRepository.upsertNodeState(updatedNodeState)
                progressRepository.upsertFormatStats(listOf(updatedFormatStat))
                progressRepository.upsertItemState(updatedItemState)
                recordReviewEvent(
                    session = session,
                    node = node,
                    item = item,
                    outcome = attemptOutcome,
                    latencyMs = answer.latencyMs,
                    now = now,
                )
            }

            return persistAndAdvance(advancePendingAfterResolvedReal(advanced), now)
        }

        val updatedNodeState =
            nodeStateUpdater.applyAnswer(
                current = progressRepository.getNodeState(node.nodeId),
                item = item,
                outcome = AttemptOutcome.INCORRECT,
                latencyMs = answer.latencyMs,
                surface = session.surface,
                sessionMode = session.mode,
                now = now,
            )
        val updatedFormatStat =
            nodeStateUpdater.updateFormatStat(
                current = progressRepository.getFormatStats(node.nodeId).firstOrNull { it.format == item.format },
                item = item,
                success = false,
                latencyMs = answer.latencyMs,
            )
        val nextFailureCount = previousFailures + 1
        val correctionQueue =
            when (session.payload.pendingPhase) {
                PendingPhase.CORRECTION -> rotateCorrectionQueue(session.payload.correctionQueueItemIds, item.itemId)
                else -> if (item.itemId in payload.correctionQueueItemIds) payload.correctionQueueItemIds else payload.correctionQueueItemIds + item.itemId
            }
        val updatedPayload =
            payload.copy(
                correctionQueueItemIds = correctionQueue,
                failCountsByItemId = payload.failCountsByItemId + (item.itemId to nextFailureCount),
                consecutiveErrorCount = payload.consecutiveErrorCount + 1,
            )
        val baseAdvanced =
            session.copy(
                surface = normalizedPendingSurface(session.surface),
                lastInteractionAt = now,
                payload = updatedPayload,
                isExitArmed = false,
                exitArmedUntil = null,
            )

        val updatedItemState = itemStateUpdater.applyFailureMark(item.itemId, progressRepository.getItemState(item.itemId), now)

        database.withTransaction {
            progressRepository.upsertNodeState(updatedNodeState)
            progressRepository.upsertFormatStats(listOf(updatedFormatStat))
            progressRepository.upsertItemState(updatedItemState)
            recordReviewEvent(
                session = session,
                node = node,
                item = item,
                outcome = AttemptOutcome.INCORRECT,
                latencyMs = answer.latencyMs,
                now = now,
            )
        }

        val withNextPrompt = advancePendingAfterIncorrect(baseAdvanced, now)
        return persistAndAdvance(withNextPrompt, now)
    }

    private suspend fun handleSocialGateAnswer(
        session: StudySession,
        node: Node,
        item: Item,
        answer: UserAnswer,
        now: Long,
    ): SessionTransition {
        if (session.currentPromptKind != SessionPromptKind.REAL_GATE) {
            // Legacy auxiliary gate prompt from older persisted sessions. New gates no longer create
            // fillers; if one is restored, it still has no spaced-repetition effect.
            // Sella la exposición auxiliar para el cooldown (no afecta la repetición espaciada).
            progressRepository.upsertItemState(
                itemStateUpdater.recordAuxiliaryExposure(item.itemId, progressRepository.getItemState(item.itemId), now),
            )
            return if (answer.isCorrect) {
                val advanced =
                    advanceSocialGate(
                        session.copy(
                            correctCount = session.correctCount + 1,
                            stepIndex = session.stepIndex + 1,
                            lastInteractionAt = now,
                            isExitArmed = false,
                            exitArmedUntil = null,
                        ),
                    )
                persistAndAdvance(advanced, now)
            } else {
                persistAndAdvance(
                    session.copy(
                        stepIndex = session.stepIndex + 1,
                        lastInteractionAt = now,
                        isExitArmed = false,
                        exitArmedUntil = null,
                    ),
                    now,
                )
            }
        }

        val payload = session.payload
        val previousFailures = payload.failCountsByItemId[item.itemId] ?: 0

        if (answer.isCorrect) {
            val outcome =
                if (previousFailures == 0) AttemptOutcome.CORRECT_FIRST_TRY else AttemptOutcome.CORRECT_AFTER_RESCUE
            val updatedNodeState =
                nodeStateUpdater.applyAnswer(
                    current = progressRepository.getNodeState(node.nodeId),
                    item = item,
                    outcome = outcome,
                    latencyMs = answer.latencyMs,
                    surface = Surface.SOCIAL_GATE,
                    sessionMode = SessionMode.QUICK,
                    now = now,
                )
            val updatedFormatStat =
                nodeStateUpdater.updateFormatStat(
                    current = progressRepository.getFormatStats(node.nodeId).firstOrNull { it.format == item.format },
                    item = item,
                    success = true,
                    latencyMs = answer.latencyMs,
                )
            val updatedItemState =
                itemStateUpdater.applyCorrectAnswer(
                    itemId = item.itemId,
                    current = progressRepository.getItemState(item.itemId),
                    inSessionFailureCount = previousFailures,
                    now = now,
                )
            val updatedPayload =
                payload.copy(
                    correctionQueueItemIds = payload.correctionQueueItemIds.filterNot { it == item.itemId },
                    failCountsByItemId = payload.failCountsByItemId - item.itemId,
                    resolvedRealItemIds = (payload.resolvedRealItemIds + item.itemId).distinct(),
                )
            val advanced =
                session.copy(
                    correctCount = session.correctCount + 1,
                    stepIndex = session.stepIndex + 1,
                    lastInteractionAt = now,
                    payload = updatedPayload,
                    isExitArmed = false,
                    exitArmedUntil = null,
                )
            database.withTransaction {
                progressRepository.upsertNodeState(updatedNodeState)
                progressRepository.upsertFormatStats(listOf(updatedFormatStat))
                progressRepository.upsertItemState(updatedItemState)
                recordReviewEvent(session = session, node = node, item = item, outcome = outcome, latencyMs = answer.latencyMs, now = now)
            }
            return persistAndAdvance(advanceSocialGate(advanced), now)
        }

        // Incorrect real gate answer: never unlocks the app and is re-queued to the end (correction
        // phase), mirroring Tarjetas pendientes. The gate stays locked until it is answered correctly.
        val updatedNodeState =
            nodeStateUpdater.applyAnswer(
                current = progressRepository.getNodeState(node.nodeId),
                item = item,
                outcome = AttemptOutcome.INCORRECT,
                latencyMs = answer.latencyMs,
                surface = Surface.SOCIAL_GATE,
                sessionMode = SessionMode.QUICK,
                now = now,
            )
        val updatedFormatStat =
            nodeStateUpdater.updateFormatStat(
                current = progressRepository.getFormatStats(node.nodeId).firstOrNull { it.format == item.format },
                item = item,
                success = false,
                latencyMs = answer.latencyMs,
            )
        val updatedItemState = itemStateUpdater.applyFailureMark(item.itemId, progressRepository.getItemState(item.itemId), now)
        val nextFailureCount = previousFailures + 1
        val correctionQueue =
            when (session.payload.pendingPhase) {
                PendingPhase.CORRECTION -> rotateCorrectionQueue(session.payload.correctionQueueItemIds, item.itemId)
                else -> if (item.itemId in payload.correctionQueueItemIds) payload.correctionQueueItemIds else payload.correctionQueueItemIds + item.itemId
            }
        val updatedPayload =
            payload.copy(
                correctionQueueItemIds = correctionQueue,
                failCountsByItemId = payload.failCountsByItemId + (item.itemId to nextFailureCount),
            )
        val advanced =
            session.copy(
                stepIndex = session.stepIndex + 1,
                lastInteractionAt = now,
                payload = updatedPayload,
                isExitArmed = false,
                exitArmedUntil = null,
            )
        database.withTransaction {
            progressRepository.upsertNodeState(updatedNodeState)
            progressRepository.upsertFormatStats(listOf(updatedFormatStat))
            progressRepository.upsertItemState(updatedItemState)
            recordReviewEvent(session = session, node = node, item = item, outcome = AttemptOutcome.INCORRECT, latencyMs = answer.latencyMs, now = now)
        }
        return persistAndAdvance(advanceSocialGate(advanced), now)
    }

    private suspend fun handleManualAnswer(
        session: StudySession,
        node: Node,
        item: Item,
        answer: UserAnswer,
        now: Long,
    ): SessionTransition {
        val previousFailures = session.payload.failCountsByItemId[item.itemId] ?: 0
        val outcome =
            when {
                answer.isCorrect && previousFailures == 0 -> AttemptOutcome.CORRECT_FIRST_TRY
                answer.isCorrect -> AttemptOutcome.CORRECT_AFTER_RESCUE
                else -> AttemptOutcome.INCORRECT
            }
        val updated = applyManualAnswerState(session, item.itemId, answer.isCorrect, now)

        database.withTransaction {
            recordReviewEvent(
                session = session,
                node = node,
                item = item,
                outcome = outcome,
                latencyMs = answer.latencyMs,
                now = now,
            )
        }

        return persistAndAdvance(updated, now)
    }

    private suspend fun handleBackMicroAnswer(
        session: StudySession,
        answer: UserAnswer,
        now: Long,
    ): SessionTransition {
        val payload = session.payload
        val restoredItemId = payload.suspendedItemId
        val restoredPromptKind = payload.suspendedPromptKind ?: SessionPromptKind.MANUAL
        val restored =
            session.copy(
                currentItemId = restoredItemId,
                currentNodeId = null,
                currentAttemptIndex = attemptIndexFor(restoredItemId, payload),
                currentPromptKind = restoredPromptKind,
                lastInteractionAt = now,
                isMicroPromptActive = false,
                isExitArmed = true,
                exitArmedUntil = now + (settingsRepository.getSettings().backExitWindowSeconds * 1_000L),
                payload =
                    payload.copy(
                        consecutiveErrorCount = if (answer.isCorrect) 0 else payload.consecutiveErrorCount + 1,
                        suspendedItemId = null,
                        suspendedPromptKind = null,
                    ),
            )
        return persistAndAdvance(restored, now, recordExposure = false)
    }

    private suspend fun persistAndAdvance(
        session: StudySession,
        now: Long,
        recordExposure: Boolean = true,
    ): SessionTransition {
        val currentItemId = session.currentItemId
        if (currentItemId == null) {
            return if (session.mode == SessionMode.QUICK && session.surface != Surface.SOCIAL_GATE) {
                val nextPacket = schedulerService.buildQuickPacket()
                if (nextPacket != null) {
                    var chained = session.retargetToPacket(nextPacket, now)
                    database.withTransaction {
                        chained = recordRealExposureIfNeeded(chained)
                        sessionRepository.saveActiveSession(chained)
                        recordSessionEvent(chained, SessionEventType.SESSION_STARTED, null, now)
                    }
                    SessionTransition.Advanced(buildPromptForSession(chained) ?: error("Prompt chaining failed for quick packet."))
                } else {
                    database.withTransaction {
                        sessionRepository.clearActiveSession(session.mode)
                        recordSessionEvent(session, SessionEventType.SESSION_COMPLETED, null, now)
                    }
                    SessionTransition.Completed(session.asResumableExitState(now))
                }
            } else {
                database.withTransaction {
                    sessionRepository.clearActiveSession(session.mode)
                    recordSessionEvent(session, SessionEventType.SESSION_COMPLETED, null, now)
                }
                SessionTransition.Completed(session.asResumableExitState(now))
            }
        }

        val prompt = buildPromptForSession(session) ?: error("Could not resolve prompt for item=$currentItemId")
        database.withTransaction {
            if (recordExposure) {
                recordRealExposureIfNeeded(prompt.session)
            }
            sessionRepository.saveActiveSession(prompt.session)
        }
        return SessionTransition.Advanced(prompt)
    }

    private suspend fun advancePendingAfterResolvedReal(session: StudySession): StudySession {
        val queue = session.queueItemIds
        if (queue.isNotEmpty()) {
            return session.copy(
                currentItemId = queue.first(),
                currentNodeId = null,
                currentAttemptIndex = attemptIndexFor(queue.first(), session.payload),
                currentPromptKind =
                    if (session.payload.pendingPhase == PendingPhase.CORRECTION) {
                        SessionPromptKind.REAL_CORRECTION
                    } else {
                        SessionPromptKind.REAL_PENDING
                    },
                queueItemIds = queue.drop(1),
            )
        }

        val correctionQueue = session.payload.correctionQueueItemIds
        return if (correctionQueue.isNotEmpty()) {
            val orderedCorrection = orderCorrectionQueue(correctionQueue)
            session.copy(
                currentItemId = orderedCorrection.firstOrNull(),
                currentNodeId = null,
                currentAttemptIndex = attemptIndexFor(orderedCorrection.firstOrNull(), session.payload),
                currentPromptKind = SessionPromptKind.REAL_CORRECTION,
                queueItemIds = orderedCorrection.drop(1),
                payload = session.payload.copy(pendingPhase = PendingPhase.CORRECTION),
            )
        } else {
            session.copy(
                currentItemId = null,
                currentNodeId = null,
                currentPromptKind = SessionPromptKind.MANUAL,
                queueItemIds = emptyList(),
                payload =
                    StudySessionPayload(
                        pendingPhase = PendingPhase.FIRST_PASS,
                    ),
            )
        }
    }

    private suspend fun advancePendingAfterIncorrect(
        session: StudySession,
        now: Long,
    ): StudySession {
        val rescueItemId =
            if (session.payload.consecutiveErrorCount >= 2) {
                selectPendingAuxiliary(session)
            } else {
                null
            }
        if (rescueItemId != null) {
            return session.copy(
                currentItemId = rescueItemId,
                currentNodeId = null,
                currentAttemptIndex = 1,
                currentPromptKind = SessionPromptKind.AUXILIARY_RESCUE,
                isMicroPromptActive = false,
                isExitArmed = false,
                exitArmedUntil = null,
            )
        }

        val queue = session.queueItemIds
        if (queue.isNotEmpty()) {
            return session.copy(
                currentItemId = queue.first(),
                currentNodeId = null,
                currentAttemptIndex = attemptIndexFor(queue.first(), session.payload),
                currentPromptKind =
                    if (session.payload.pendingPhase == PendingPhase.CORRECTION) {
                        SessionPromptKind.REAL_CORRECTION
                    } else {
                        SessionPromptKind.REAL_PENDING
                    },
                queueItemIds = queue.drop(1),
            )
        }

        val correctionQueue = orderCorrectionQueue(session.payload.correctionQueueItemIds)
        return session.copy(
            currentItemId = correctionQueue.firstOrNull(),
            currentNodeId = null,
            currentAttemptIndex = attemptIndexFor(correctionQueue.firstOrNull(), session.payload),
            currentPromptKind = SessionPromptKind.REAL_CORRECTION,
            queueItemIds = correctionQueue.drop(1),
            payload = session.payload.copy(pendingPhase = PendingPhase.CORRECTION),
        )
    }

    private suspend fun resumeAfterAuxiliary(
        session: StudySession,
        now: Long,
    ): StudySession {
        val queue = session.queueItemIds
        if (queue.isNotEmpty()) {
            return session.copy(
                currentItemId = queue.first(),
                currentNodeId = null,
                currentAttemptIndex = attemptIndexFor(queue.first(), session.payload),
                currentPromptKind =
                    if (session.payload.pendingPhase == PendingPhase.CORRECTION) {
                        SessionPromptKind.REAL_CORRECTION
                    } else {
                        SessionPromptKind.REAL_PENDING
                    },
                queueItemIds = queue.drop(1),
                isMicroPromptActive = false,
                isExitArmed = false,
                exitArmedUntil = null,
            )
        }

        val correctionQueue = orderCorrectionQueue(session.payload.correctionQueueItemIds)
        if (correctionQueue.isNotEmpty()) {
            return session.copy(
                currentItemId = correctionQueue.first(),
                currentNodeId = null,
                currentAttemptIndex = attemptIndexFor(correctionQueue.first(), session.payload),
                currentPromptKind = SessionPromptKind.REAL_CORRECTION,
                queueItemIds = correctionQueue.drop(1),
                payload = session.payload.copy(pendingPhase = PendingPhase.CORRECTION),
                isMicroPromptActive = false,
                isExitArmed = false,
                exitArmedUntil = null,
            )
        }

        return session.copy(
            currentItemId = null,
            currentNodeId = null,
            currentPromptKind = SessionPromptKind.MANUAL,
            queueItemIds = emptyList(),
            isMicroPromptActive = false,
            isExitArmed = false,
            exitArmedUntil = null,
        )
    }


    private suspend fun createBackMicroPrompt(
        session: StudySession,
        now: Long,
    ): StudyPrompt? {
        val currentItem = requireCurrentItem(session)
        val backMicroItem = selectBackMicroItem(session, currentItem) ?: return null
        val updated =
            session.copy(
                currentItemId = backMicroItem.itemId,
                currentNodeId = null,
                currentAttemptIndex = 1,
                currentPromptKind = SessionPromptKind.BACK_MICRO,
                lastInteractionAt = now,
                isMicroPromptActive = true,
                isExitArmed = false,
                exitArmedUntil = null,
                payload =
                    session.payload.copy(
                        suspendedItemId = currentItem.itemId,
                        suspendedPromptKind = session.currentPromptKind,
                    ),
            )
        return buildPromptForSession(updated)
    }

    private suspend fun selectBackMicroItem(
        session: StudySession,
        currentItem: Item,
    ): Item? {
        val nodeId = session.currentNodeId ?: currentItem.nodeId
        val items = contentRepository.getItemsForNode(nodeId)
        return items.firstOrNull { it.itemId != currentItem.itemId && Surface.BACK_MICRO in it.allowedSurfaces && isStrictFrictionOne(it) }
            ?: items.firstOrNull { it.itemId != currentItem.itemId && isStrictFrictionOne(it) }
            ?: currentItem.takeIf { isStrictFrictionOne(it) }
    }

    private suspend fun selectPendingAuxiliary(session: StudySession): String? {
        val payload = session.payload
        return session.rescueQueueItemIds.firstOrNull { itemId ->
            itemId !in payload.vetoedAuxiliaryItemIds && itemId !in payload.usedAuxiliaryItemIds
        }
    }

    private suspend fun applyAbandonToSession(
        session: StudySession,
        reason: AbandonReason,
        now: Long,
    ): StudySession {
        database.withTransaction {
            val unresolvedRealItemIds =
                when (session.surface) {
                    Surface.SOCIAL_GATE -> listOfNotNull(session.currentItemId).filter { session.currentPromptKind == SessionPromptKind.REAL_GATE }
                    else -> pendingUnresolvedRealItems(session)
                }
            unresolvedRealItemIds.distinct().forEach { itemId ->
                val item = contentRepository.getItem(itemId) ?: return@forEach
                val node = contentRepository.getNode(item.nodeId) ?: return@forEach
                if (session.mode == SessionMode.QUICK || session.surface == Surface.SOCIAL_GATE) {
                    val updatedItemState = itemStateUpdater.applyAbandon(item.itemId, progressRepository.getItemState(item.itemId), now)
                    val updatedNodeState =
                        nodeStateUpdater.applyAbandon(
                            current = progressRepository.getNodeState(node.nodeId),
                            nodeId = node.nodeId,
                            surface = session.surface,
                            sessionMode = session.mode,
                            reason = reason,
                            now = now,
                        )
                    progressRepository.upsertItemState(updatedItemState)
                    progressRepository.upsertNodeState(updatedNodeState)
                }
                eventRepository.recordAbandonEvent(
                    AbandonEvent(
                        eventId = idProvider.newId(),
                        sessionId = session.sessionId,
                        nodeId = node.nodeId,
                        itemId = item.itemId,
                        reason = reason,
                        occurredAt = now,
                    ),
                )
            }
            if (unresolvedRealItemIds.isEmpty()) {
                eventRepository.recordAbandonEvent(
                    AbandonEvent(
                        eventId = idProvider.newId(),
                        sessionId = session.sessionId,
                        nodeId = session.currentNodeId,
                        itemId = session.currentItemId,
                        reason = reason,
                        occurredAt = now,
                    ),
                )
            }
        }
        return session.copy(lastInteractionAt = now)
    }

    private fun pendingUnresolvedRealItems(session: StudySession): List<String> {
        val unresolved = session.payload.failCountsByItemId.keys.toMutableSet()
        val currentIsReal =
            session.currentPromptKind == SessionPromptKind.REAL_PENDING || session.currentPromptKind == SessionPromptKind.REAL_CORRECTION
        val currentItemId = session.currentItemId
        if (currentIsReal && currentItemId != null && currentItemId !in session.payload.resolvedRealItemIds) {
            unresolved += currentItemId
        }
        return unresolved.toList()
    }

    private suspend fun requireActiveSession(sessionId: String): StudySession {
        return requireNotNull(sessionRepository.getActiveSessionById(sessionId)) { "No active session for id=$sessionId" }
    }

    private suspend fun requireCurrentItem(session: StudySession): Item {
        val itemId = requireNotNull(session.currentItemId) { "Session ${session.sessionId} has no current item." }
        return requireNotNull(contentRepository.getItem(itemId)) { "Item $itemId not found." }
    }

    private suspend fun requireCurrentNode(item: Item): Node =
        requireNotNull(contentRepository.getNode(item.nodeId)) { "Node ${item.nodeId} not found." }

    private suspend fun recordRealExposureIfNeeded(session: StudySession): StudySession {
        val itemId = session.currentItemId ?: return session
        if (!session.currentPromptKind.isTrackedRealPrompt()) return session
        val updatedState = itemStateUpdater.recordRealExposure(itemId, progressRepository.getItemState(itemId))
        progressRepository.upsertItemState(updatedState)
        return session
    }

    private suspend fun recordReviewEvent(
        session: StudySession,
        node: Node,
        item: Item,
        outcome: AttemptOutcome,
        latencyMs: Long,
        now: Long,
    ) {
        eventRepository.recordReviewEvent(
            ReviewEvent(
                eventId = idProvider.newId(),
                sessionId = session.sessionId,
                nodeId = node.nodeId,
                itemId = item.itemId,
                answerOutcome = outcome,
                latencyMs = latencyMs,
                surface = session.surface,
                sessionMode = session.mode,
                attemptIndex = session.currentAttemptIndex,
                occurredAt = now,
            ),
        )
    }

    private suspend fun recordSessionEvent(
        session: StudySession,
        type: SessionEventType,
        payloadJson: String?,
        now: Long,
    ) {
        eventRepository.recordSessionEvent(
            SessionEvent(
                eventId = idProvider.newId(),
                sessionId = session.sessionId,
                packetId = session.packetId,
                topicUnitId = session.topicUnitId,
                type = type,
                payloadJson = payloadJson,
                occurredAt = now,
            ),
        )
    }

    private fun promptIndexFor(session: StudySession): Int =
        if (session.surface == Surface.SOCIAL_GATE) {
            session.correctCount + 1
        } else {
            session.stepIndex + 1
        }

    private fun attemptIndexFor(
        itemId: String?,
        payload: StudySessionPayload,
    ): Int = itemId?.let { (payload.failCountsByItemId[it] ?: 0) + 1 } ?: 1

    private fun rotateCorrectionQueue(
        queue: List<String>,
        itemId: String,
    ): List<String> {
        val withoutCurrent = queue.filterNot { it == itemId }
        return withoutCurrent + itemId
    }

    private suspend fun orderCorrectionQueue(queue: List<String>): List<String> {
        val distinctIds = queue.distinct()
        val itemsById = distinctIds.associateWith { itemId -> contentRepository.getItem(itemId) }
        return distinctIds.sortedWith(
            compareBy<String> { itemsById[it]?.frictionLevel ?: Int.MAX_VALUE }
                .thenBy { itemsById[it]?.let { item -> realPromptRoleOrder(item.itemRole) } ?: Int.MAX_VALUE }
                .thenBy { it },
        )
    }

    private fun normalizedPendingSurface(surface: Surface): Surface =
        if (surface == Surface.NOTIFICATION) {
            Surface.IN_APP_QUICK
        } else {
            surface
        }

    private fun isStrictFrictionOne(item: Item): Boolean =
        item.frictionLevel == 1 && item.format in STRICT_FRICTION_ONE_FORMATS

    private fun realPromptRoleOrder(role: com.estudio.antiprocrastinacion.app.model.content.ItemRole): Int =
        when (role) {
            com.estudio.antiprocrastinacion.app.model.content.ItemRole.CORE -> 0
            com.estudio.antiprocrastinacion.app.model.content.ItemRole.VARIANT -> 1
            com.estudio.antiprocrastinacion.app.model.content.ItemRole.TRAP -> 2
            com.estudio.antiprocrastinacion.app.model.content.ItemRole.INTEGRATION -> 3
            com.estudio.antiprocrastinacion.app.model.content.ItemRole.BOSS -> 4
            com.estudio.antiprocrastinacion.app.model.content.ItemRole.RESCUE -> 5
        }

    private fun StudySession.retargetToPacket(
        packet: TopicPacket,
        now: Long,
    ): StudySession =
        copy(
            mode = packet.sessionMode,
            surface = packet.surface,
            packetId = packet.packetId,
            topicUnitId = packet.topicUnitId,
            currentCourseTitle = packet.currentCourseTitle,
            currentTopicTitle = packet.currentTopicTitle,
            queueItemIds = packet.initialItemIds.drop(1),
            rescueQueueItemIds = packet.rescueItemIds,
            goalCorrectCount = packet.goalCorrectCount,
            currentNodeId = null,
            currentItemId = packet.initialItemIds.firstOrNull(),
            currentAttemptIndex = 1,
            currentPromptKind = packet.initialPromptKind,
            correctCount = 0,
            stepIndex = 0,
            lastInteractionAt = now,
            isMicroPromptActive = false,
            isExitArmed = false,
            exitArmedUntil = null,
            payload = packet.payload,
        )

    companion object {
        val STRICT_FRICTION_ONE_FORMATS =
            setOf(
                ItemFormat.TRUE_FALSE,
                ItemFormat.MULTIPLE_CHOICE,
                ItemFormat.CHOOSE_FALSE_STATEMENT,
                ItemFormat.MATCHING_SIMPLE,
            )
    }
}

private fun SessionPromptKind.isTrackedRealPrompt(): Boolean =
    this == SessionPromptKind.REAL_PENDING ||
        this == SessionPromptKind.REAL_CORRECTION ||
        this == SessionPromptKind.REAL_GATE

internal fun StudySession.asResumableExitState(now: Long): StudySession =
    copy(
        lastInteractionAt = now,
        isMicroPromptActive = false,
        isExitArmed = false,
        exitArmedUntil = null,
        currentPromptKind =
            if (currentPromptKind == SessionPromptKind.BACK_MICRO) {
                payload.suspendedPromptKind ?: SessionPromptKind.MANUAL
            } else {
                currentPromptKind
            },
        currentItemId =
            if (currentPromptKind == SessionPromptKind.BACK_MICRO) {
                payload.suspendedItemId
            } else {
                currentItemId
            },
        payload =
            payload.copy(
                suspendedItemId = null,
                suspendedPromptKind = null,
            ),
    )

internal fun advanceSocialGate(session: StudySession): StudySession {
    val queue = session.queueItemIds
    if (queue.isNotEmpty()) {
        val nextItemId = queue.first()
        return session.copy(
            currentItemId = nextItemId,
            currentNodeId = null,
            currentAttemptIndex = manualAttemptIndexFor(nextItemId, session.payload),
            currentPromptKind = SessionPromptKind.REAL_GATE,
            queueItemIds = queue.drop(1),
        )
    }

    // First pass finished: any real gate questions answered incorrectly repeat now, and the gate does
    // not unlock until every one of them is answered correctly.
    val correctionQueue = session.payload.correctionQueueItemIds
    if (correctionQueue.isNotEmpty()) {
        val nextItemId = correctionQueue.first()
        return session.copy(
            currentItemId = nextItemId,
            currentNodeId = null,
            currentAttemptIndex = manualAttemptIndexFor(nextItemId, session.payload),
            currentPromptKind = SessionPromptKind.REAL_GATE,
            queueItemIds = correctionQueue.drop(1),
            payload = session.payload.copy(pendingPhase = PendingPhase.CORRECTION),
        )
    }

    return session.copy(currentItemId = null, currentNodeId = null, queueItemIds = emptyList())
}

internal fun applyManualAnswerState(
    session: StudySession,
    itemId: String,
    isCorrect: Boolean,
    answeredAt: Long,
): StudySession {
    val payload = session.payload.withManualDefaults()
    val previousFailures = payload.failCountsByItemId[itemId] ?: 0
    val updatedPayload =
        if (isCorrect) {
            payload.copy(
                correctionQueueItemIds = payload.correctionQueueItemIds.filterNot { it == itemId },
                failCountsByItemId = payload.failCountsByItemId - itemId,
            )
        } else {
            val nextFailureCount = previousFailures + 1
            val correctionQueue =
                when (payload.pendingPhase) {
                    PendingPhase.CORRECTION -> rotateManualCorrectionQueue(payload.correctionQueueItemIds, itemId)
                    PendingPhase.FIRST_PASS -> {
                        if (itemId in payload.correctionQueueItemIds) {
                            payload.correctionQueueItemIds
                        } else {
                            payload.correctionQueueItemIds + itemId
                        }
                    }
                    null -> payload.correctionQueueItemIds + itemId
                }
            payload.copy(
                correctionQueueItemIds = correctionQueue,
                failCountsByItemId = payload.failCountsByItemId + (itemId to nextFailureCount),
            )
        }

    val updatedSession =
        session.copy(
            currentNodeId = null,
            currentAttemptIndex = 1,
            currentPromptKind = SessionPromptKind.MANUAL,
            correctCount = session.correctCount + if (isCorrect) 1 else 0,
            stepIndex = session.stepIndex + 1,
            lastInteractionAt = answeredAt,
            payload = updatedPayload,
            isExitArmed = false,
            exitArmedUntil = null,
        )
    return advanceManualSession(updatedSession)
}

private fun advanceManualSession(session: StudySession): StudySession {
    val payload = session.payload.withManualDefaults()
    val queue = session.queueItemIds
    if (queue.isNotEmpty()) {
        val nextItemId = queue.first()
        return session.copy(
            currentItemId = nextItemId,
            currentNodeId = null,
            currentAttemptIndex = manualAttemptIndexFor(nextItemId, payload),
            currentPromptKind = SessionPromptKind.MANUAL,
            queueItemIds = queue.drop(1),
            payload = payload,
        )
    }

    val correctionQueue = payload.correctionQueueItemIds
    if (correctionQueue.isNotEmpty()) {
        val nextItemId = correctionQueue.first()
        return session.copy(
            currentItemId = nextItemId,
            currentNodeId = null,
            currentAttemptIndex = manualAttemptIndexFor(nextItemId, payload),
            currentPromptKind = SessionPromptKind.MANUAL,
            queueItemIds = correctionQueue.drop(1),
            payload = payload.copy(pendingPhase = PendingPhase.CORRECTION),
        )
    }

    return session.copy(
        currentItemId = null,
        currentNodeId = null,
        currentAttemptIndex = 1,
        currentPromptKind = SessionPromptKind.MANUAL,
        queueItemIds = emptyList(),
        payload = payload.copy(pendingPhase = PendingPhase.FIRST_PASS),
    )
}

private fun StudySessionPayload.withManualDefaults(): StudySessionPayload =
    if (pendingPhase != null) {
        this
    } else {
        copy(pendingPhase = PendingPhase.FIRST_PASS)
    }

private fun manualAttemptIndexFor(
    itemId: String?,
    payload: StudySessionPayload,
): Int = itemId?.let { (payload.failCountsByItemId[it] ?: 0) + 1 } ?: 1

private fun rotateManualCorrectionQueue(
    queue: List<String>,
    itemId: String,
): List<String> {
    val withoutCurrent = queue.filterNot { it == itemId }
    return withoutCurrent + itemId
}
