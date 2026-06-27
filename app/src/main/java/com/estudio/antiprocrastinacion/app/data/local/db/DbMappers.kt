package com.estudio.antiprocrastinacion.app.data.local.db

import com.estudio.antiprocrastinacion.app.model.content.ContentOrigin
import com.estudio.antiprocrastinacion.app.model.content.Course
import com.estudio.antiprocrastinacion.app.model.content.FacetType
import com.estudio.antiprocrastinacion.app.model.content.Item
import com.estudio.antiprocrastinacion.app.model.content.ItemOverride
import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.ItemOption
import com.estudio.antiprocrastinacion.app.model.content.ItemRole
import com.estudio.antiprocrastinacion.app.model.content.ManualItemEdit
import com.estudio.antiprocrastinacion.app.model.content.Node
import com.estudio.antiprocrastinacion.app.model.content.NodeType
import com.estudio.antiprocrastinacion.app.model.content.Outcome
import com.estudio.antiprocrastinacion.app.model.content.SessionMode
import com.estudio.antiprocrastinacion.app.model.content.Surface
import com.estudio.antiprocrastinacion.app.model.content.UnitModel
import com.estudio.antiprocrastinacion.app.model.event.AbandonEvent
import com.estudio.antiprocrastinacion.app.model.event.AbandonReason
import com.estudio.antiprocrastinacion.app.model.event.AttemptOutcome
import com.estudio.antiprocrastinacion.app.model.event.ImportEvent
import com.estudio.antiprocrastinacion.app.model.event.ImportResultType
import com.estudio.antiprocrastinacion.app.model.event.ReviewEvent
import com.estudio.antiprocrastinacion.app.model.event.SessionEvent
import com.estudio.antiprocrastinacion.app.model.event.SessionEventType
import com.estudio.antiprocrastinacion.app.model.json.AppSettingsDto
import com.estudio.antiprocrastinacion.app.model.json.CourseDto
import com.estudio.antiprocrastinacion.app.model.json.ItemDto
import com.estudio.antiprocrastinacion.app.model.json.ItemOverrideDto
import com.estudio.antiprocrastinacion.app.model.json.ItemStateDto
import com.estudio.antiprocrastinacion.app.model.json.ItemOptionDto
import com.estudio.antiprocrastinacion.app.model.json.NodeFormatStatDto
import com.estudio.antiprocrastinacion.app.model.json.NodeStateDto
import com.estudio.antiprocrastinacion.app.model.json.NodeDto
import com.estudio.antiprocrastinacion.app.model.json.OutcomeDto
import com.estudio.antiprocrastinacion.app.model.json.SocialGateDailyStateDto
import com.estudio.antiprocrastinacion.app.model.json.SocialGateRuleDto
import com.estudio.antiprocrastinacion.app.model.json.SocialGateRuntimeStateDto
import com.estudio.antiprocrastinacion.app.model.json.StudySessionDto
import com.estudio.antiprocrastinacion.app.model.json.UnitDto
import com.estudio.antiprocrastinacion.app.model.state.AppSettings
import com.estudio.antiprocrastinacion.app.model.state.ItemReviewOutcome
import com.estudio.antiprocrastinacion.app.model.state.ItemState
import com.estudio.antiprocrastinacion.app.model.state.NodeFormatStat
import com.estudio.antiprocrastinacion.app.model.state.NodeState
import com.estudio.antiprocrastinacion.app.model.state.SessionPromptKind
import com.estudio.antiprocrastinacion.app.model.state.SocialGateDailyState
import com.estudio.antiprocrastinacion.app.model.state.SocialGateRule
import com.estudio.antiprocrastinacion.app.model.state.SocialGateRuntimePhase
import com.estudio.antiprocrastinacion.app.model.state.SocialGateRuntimeState
import com.estudio.antiprocrastinacion.app.model.state.StudySession
import com.estudio.antiprocrastinacion.app.socialgate.SocialGateSchedule
import com.estudio.antiprocrastinacion.app.ui.common.AppJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

private inline fun <reified T> encode(value: T): String = AppJson.encodeToString(value)

private inline fun <reified T> decode(raw: String): T = AppJson.decodeFromString(raw)

fun CourseDto.asEntity(): CourseEntity =
    CourseEntity(
        courseId = courseId,
        title = title,
        description = description,
        version = version,
        updatedAt = updatedAt,
    )

fun UnitDto.asEntity(): UnitEntity =
    UnitEntity(
        unitId = unitId,
        courseId = courseId,
        title = title,
        description = description,
        orderIndex = orderIndex,
        version = version,
        updatedAt = updatedAt,
    )

fun OutcomeDto.asEntity(): OutcomeEntity =
    OutcomeEntity(
        outcomeId = outcomeId,
        unitId = unitId,
        title = title,
        description = description,
        version = version,
        updatedAt = updatedAt,
    )

fun NodeDto.asEntity(origin: ContentOrigin, archivedCandidate: Boolean = false): NodeEntity =
    NodeEntity(
        nodeId = nodeId,
        courseId = courseId,
        unitId = unitId,
        outcomeIdsJson = encode(outcomeIds),
        title = title,
        coreClaim = coreClaim,
        type = type.name,
        weightExam = weightExam,
        prerequisitesJson = encode(prerequisites),
        facetsJson = encode(facets),
        mustKnowJson = encode(mustKnow),
        commonErrorsJson = encode(commonErrors),
        minimumMasteryDefinition = minimumMasteryDefinition,
        surfaceEasyReady = surfaceEasyReady,
        surfaceEasyItemCount = surfaceEasyItemCount,
        sourceRefsJson = encode(sourceRefs),
        version = version,
        updatedAt = updatedAt,
        archivedCandidate = archivedCandidate,
        contentOrigin = origin.name,
    )

fun ItemDto.asEntity(origin: ContentOrigin): ItemEntity =
    ItemEntity(
        itemId = itemId,
        nodeId = nodeId,
        facet = facet.name,
        format = format.name,
        frictionLevel = frictionLevel,
        difficultySeed = difficultySeed,
        itemRole = itemRole.name,
        allowedSurfacesJson = encode(allowedSurfaces),
        cooldownHours = cooldownHours,
        stem = stem,
        correctAnswer = correctAnswer,
        feedbackShort = feedbackShort,
        coversMustKnowJson = encode(coversMustKnow),
        variantGroupId = variantGroupId,
        rescueGroupId = rescueGroupId,
        nodeComplexity = nodeComplexity,
        facetComplexity = facetComplexity,
        distractorSimilarity = distractorSimilarity,
        prerequisiteDepth = prerequisiteDepth,
        targetsErrorIdsJson = encode(targetsErrorIds),
        commonErrorSignalsJson = encode(commonErrorSignals),
        optionsJson = encode(options),
        version = version,
        updatedAt = updatedAt,
        sourceRefsJson = encode(sourceRefs),
        contentOrigin = origin.name,
    )

fun CourseEntity.asDomain(): Course =
    Course(
        courseId = courseId,
        title = title,
        description = description,
        version = version,
        updatedAt = updatedAt,
    )

fun Course.asDto(): CourseDto =
    CourseDto(
        courseId = courseId,
        title = title,
        description = description,
        version = version,
        updatedAt = updatedAt,
    )

fun UnitEntity.asDomain(): UnitModel =
    UnitModel(
        unitId = unitId,
        courseId = courseId,
        title = title,
        description = description,
        orderIndex = orderIndex,
        version = version,
        updatedAt = updatedAt,
    )

fun UnitModel.asDto(): UnitDto =
    UnitDto(
        unitId = unitId,
        courseId = courseId,
        title = title,
        description = description,
        orderIndex = orderIndex,
        version = version,
        updatedAt = updatedAt,
    )

fun OutcomeEntity.asDomain(): Outcome =
    Outcome(
        outcomeId = outcomeId,
        unitId = unitId,
        title = title,
        description = description,
        version = version,
        updatedAt = updatedAt,
    )

fun Outcome.asDto(): OutcomeDto =
    OutcomeDto(
        outcomeId = outcomeId,
        unitId = unitId,
        title = title,
        description = description,
        version = version,
        updatedAt = updatedAt,
    )

fun NodeEntity.asDomain(): Node =
    Node(
        nodeId = nodeId,
        courseId = courseId,
        unitId = unitId,
        outcomeIds = decode(outcomeIdsJson),
        title = title,
        coreClaim = coreClaim,
        type = NodeType.valueOf(type),
        weightExam = weightExam,
        prerequisites = decode(prerequisitesJson),
        facets = decode<List<FacetType>>(facetsJson),
        mustKnow = decode(mustKnowJson),
        commonErrors = decode(commonErrorsJson),
        minimumMasteryDefinition = minimumMasteryDefinition,
        surfaceEasyReady = surfaceEasyReady,
        surfaceEasyItemCount = surfaceEasyItemCount,
        sourceRefs = decode(sourceRefsJson),
        version = version,
        updatedAt = updatedAt,
        archivedCandidate = archivedCandidate,
        contentOrigin = ContentOrigin.valueOf(contentOrigin),
    )

fun Node.asDto(): NodeDto =
    NodeDto(
        nodeId = nodeId,
        courseId = courseId,
        unitId = unitId,
        outcomeIds = outcomeIds,
        title = title,
        coreClaim = coreClaim,
        type = type,
        weightExam = weightExam,
        prerequisites = prerequisites,
        facets = facets,
        mustKnow = mustKnow,
        commonErrors = commonErrors,
        minimumMasteryDefinition = minimumMasteryDefinition,
        surfaceEasyReady = surfaceEasyReady,
        surfaceEasyItemCount = surfaceEasyItemCount,
        sourceRefs = sourceRefs,
        version = version,
        updatedAt = updatedAt,
    )

fun ItemEntity.asDomain(): Item =
    Item(
        itemId = itemId,
        nodeId = nodeId,
        facet = FacetType.valueOf(facet),
        format = ItemFormat.valueOf(format),
        frictionLevel = frictionLevel,
        difficultySeed = difficultySeed,
        itemRole = ItemRole.valueOf(itemRole),
        allowedSurfaces = decode<List<Surface>>(allowedSurfacesJson),
        cooldownHours = cooldownHours,
        stem = stem,
        correctAnswer = correctAnswer,
        feedbackShort = feedbackShort,
        coversMustKnow = decode(coversMustKnowJson),
        variantGroupId = variantGroupId,
        rescueGroupId = rescueGroupId,
        nodeComplexity = nodeComplexity,
        facetComplexity = facetComplexity,
        distractorSimilarity = distractorSimilarity,
        prerequisiteDepth = prerequisiteDepth,
        targetsErrorIds = decode(targetsErrorIdsJson),
        commonErrorSignals = decode(commonErrorSignalsJson),
        options = decode<List<ItemOptionDto>>(optionsJson).map(ItemOptionDto::asDomain),
        version = version,
        updatedAt = updatedAt,
        sourceRefs = decode(sourceRefsJson),
        contentOrigin = ContentOrigin.valueOf(contentOrigin),
    )

private fun ItemOptionDto.asDomain(): ItemOption =
    ItemOption(
        id = id,
        text = text,
        isCorrect = isCorrect,
    )

private fun ItemOption.asDto(): ItemOptionDto =
    ItemOptionDto(
        id = id,
        text = text,
        isCorrect = isCorrect,
    )

fun Item.asDto(): ItemDto =
    ItemDto(
        itemId = itemId,
        nodeId = nodeId,
        facet = facet,
        format = format,
        frictionLevel = frictionLevel,
        difficultySeed = difficultySeed,
        itemRole = itemRole,
        allowedSurfaces = allowedSurfaces,
        cooldownHours = cooldownHours,
        stem = stem,
        correctAnswer = correctAnswer,
        feedbackShort = feedbackShort,
        coversMustKnow = coversMustKnow,
        variantGroupId = variantGroupId,
        rescueGroupId = rescueGroupId,
        nodeComplexity = nodeComplexity,
        facetComplexity = facetComplexity,
        distractorSimilarity = distractorSimilarity,
        prerequisiteDepth = prerequisiteDepth,
        targetsErrorIds = targetsErrorIds,
        commonErrorSignals = commonErrorSignals,
        options = options.map { it.asDto() },
        version = version,
        updatedAt = updatedAt,
        sourceRefs = sourceRefs,
    )

fun NodeStateEntity.asDomain(): NodeState =
    NodeState(
        nodeId = nodeId,
        memoryScore = memoryScore,
        stabilityHours = stabilityHours,
        retrievability = retrievability,
        difficultyUser = difficultyUser,
        frictionUser = frictionUser,
        coverageScore = coverageScore,
        avgLatencyMs = avgLatencyMs,
        errorRate = errorRate,
        abandonRate = abandonRate,
        lastReviewedAt = lastReviewedAt,
        nextReviewAt = nextReviewAt,
        timesSeen = timesSeen,
        timesCorrectFirstTry = timesCorrectFirstTry,
        timesCorrectAfterRescue = timesCorrectAfterRescue,
        timesFailed = timesFailed,
        timesAbandoned = timesAbandoned,
        lastSurfaceUsed = lastSurfaceUsed?.let(Surface::valueOf),
        lastSessionMode = lastSessionMode?.let(SessionMode::valueOf),
    )

fun NodeState.asEntity(): NodeStateEntity =
    NodeStateEntity(
        nodeId = nodeId,
        memoryScore = memoryScore,
        stabilityHours = stabilityHours,
        retrievability = retrievability,
        difficultyUser = difficultyUser,
        frictionUser = frictionUser,
        coverageScore = coverageScore,
        avgLatencyMs = avgLatencyMs,
        errorRate = errorRate,
        abandonRate = abandonRate,
        lastReviewedAt = lastReviewedAt,
        nextReviewAt = nextReviewAt,
        timesSeen = timesSeen,
        timesCorrectFirstTry = timesCorrectFirstTry,
        timesCorrectAfterRescue = timesCorrectAfterRescue,
        timesFailed = timesFailed,
        timesAbandoned = timesAbandoned,
        lastSurfaceUsed = lastSurfaceUsed?.name,
        lastSessionMode = lastSessionMode?.name,
    )

fun NodeState.asDto(): NodeStateDto =
    NodeStateDto(
        nodeId = nodeId,
        memoryScore = memoryScore,
        stabilityHours = stabilityHours,
        retrievability = retrievability,
        difficultyUser = difficultyUser,
        frictionUser = frictionUser,
        coverageScore = coverageScore,
        avgLatencyMs = avgLatencyMs,
        errorRate = errorRate,
        abandonRate = abandonRate,
        lastReviewedAt = lastReviewedAt,
        nextReviewAt = nextReviewAt,
        timesSeen = timesSeen,
        timesCorrectFirstTry = timesCorrectFirstTry,
        timesCorrectAfterRescue = timesCorrectAfterRescue,
        timesFailed = timesFailed,
        timesAbandoned = timesAbandoned,
        lastSurfaceUsed = lastSurfaceUsed,
        lastSessionMode = lastSessionMode,
    )

fun NodeStateDto.asDomain(): NodeState =
    NodeState(
        nodeId = nodeId,
        memoryScore = memoryScore,
        stabilityHours = stabilityHours,
        retrievability = retrievability,
        difficultyUser = difficultyUser,
        frictionUser = frictionUser,
        coverageScore = coverageScore,
        avgLatencyMs = avgLatencyMs,
        errorRate = errorRate,
        abandonRate = abandonRate,
        lastReviewedAt = lastReviewedAt,
        nextReviewAt = nextReviewAt,
        timesSeen = timesSeen,
        timesCorrectFirstTry = timesCorrectFirstTry,
        timesCorrectAfterRescue = timesCorrectAfterRescue,
        timesFailed = timesFailed,
        timesAbandoned = timesAbandoned,
        lastSurfaceUsed = lastSurfaceUsed,
        lastSessionMode = lastSessionMode,
    )

fun ItemStateEntity.asDomain(): ItemState =
    ItemState(
        itemId = itemId,
        stage = stage,
        lastReviewedAt = lastReviewedAt,
        nextReviewAt = nextReviewAt,
        timesShown = timesSeen,
        timesFailed = timesFailed,
        timesCorrectFirstTry = timesCorrectFirstTry,
        timesRecoveredAfterFailure = timesRecoveredAfterFailure,
        timesAbandoned = timesAbandoned,
        lastOutcome = lastOutcome?.let(ItemReviewOutcome::valueOf),
    )

fun ItemState.asEntity(): ItemStateEntity =
    ItemStateEntity(
        itemId = itemId,
        stage = stage,
        lastReviewedAt = lastReviewedAt,
        nextReviewAt = nextReviewAt,
        timesSeen = timesShown,
        timesFailed = timesFailed,
        timesCorrectFirstTry = timesCorrectFirstTry,
        timesRecoveredAfterFailure = timesRecoveredAfterFailure,
        timesAbandoned = timesAbandoned,
        lastOutcome = lastOutcome?.name,
    )

fun ItemState.asDto(): ItemStateDto =
    ItemStateDto(
        itemId = itemId,
        stage = stage,
        lastReviewedAt = lastReviewedAt,
        nextReviewAt = nextReviewAt,
        timesShown = timesShown,
        timesFailed = timesFailed,
        timesCorrectFirstTry = timesCorrectFirstTry,
        timesRecoveredAfterFailure = timesRecoveredAfterFailure,
        timesAbandoned = timesAbandoned,
        lastOutcome = lastOutcome,
    )

fun ItemStateDto.asDomain(): ItemState =
    ItemState(
        itemId = itemId,
        stage = stage,
        lastReviewedAt = lastReviewedAt,
        nextReviewAt = nextReviewAt,
        timesShown = timesShown,
        timesFailed = timesFailed,
        timesCorrectFirstTry = timesCorrectFirstTry,
        timesRecoveredAfterFailure = timesRecoveredAfterFailure,
        timesAbandoned = timesAbandoned,
        lastOutcome = lastOutcome,
    )

fun ItemOverrideEntity.asDomain(): ItemOverride =
    ItemOverride(
        itemId = itemId,
        archived = archived,
        stemOverride = stemOverride,
        correctAnswerOverride = correctAnswerOverride,
        optionsOverride = optionsJsonOverride?.let { raw ->
            decode<List<ItemOptionDto>>(raw).map(ItemOptionDto::asDomain)
        },
    )

fun ItemOverride.asEntity(): ItemOverrideEntity =
    ItemOverrideEntity(
        itemId = itemId,
        archived = archived,
        stemOverride = stemOverride,
        correctAnswerOverride = correctAnswerOverride,
        optionsJsonOverride = optionsOverride?.let(::encode),
    )

fun ItemOverride.asDto(): ItemOverrideDto =
    ItemOverrideDto(
        itemId = itemId,
        archived = archived,
        stemOverride = stemOverride,
        correctAnswerOverride = correctAnswerOverride,
        optionsOverride = optionsOverride?.map(ItemOption::asDto),
    )

fun ItemOverrideDto.asDomain(): ItemOverride =
    ItemOverride(
        itemId = itemId,
        archived = archived,
        stemOverride = stemOverride,
        correctAnswerOverride = correctAnswerOverride,
        optionsOverride = optionsOverride?.map(ItemOptionDto::asDomain),
    )

fun Item.applyOverride(override: ItemOverride?): Item {
    if (override == null) return this
    val overriddenOptions = override.optionsOverride ?: options
    val overriddenCorrectAnswer = override.correctAnswerOverride ?: correctAnswer
    return copy(
        stem = override.stemOverride ?: stem,
        correctAnswer = overriddenCorrectAnswer,
        options =
            overriddenOptions.map { option ->
                if (overriddenOptions.any(ItemOption::isCorrect)) {
                    option
                } else {
                    option.copy(isCorrect = option.text == overriddenCorrectAnswer || option.id == overriddenCorrectAnswer)
                }
            },
        archivedManual = override.archived,
        editedManual = override.stemOverride != null || override.correctAnswerOverride != null || override.optionsOverride != null,
    )
}

fun ManualItemEdit.asOverride(current: ItemOverride?): ItemOverride =
    ItemOverride(
        itemId = itemId,
        archived = current?.archived ?: false,
        stemOverride = stem,
        correctAnswerOverride = correctAnswer,
        optionsOverride = options,
    )

fun NodeFormatStatEntity.asDomain(): NodeFormatStat =
    NodeFormatStat(
        nodeId = nodeId,
        format = ItemFormat.valueOf(format),
        attempts = attempts,
        successes = successes,
        avgLatencyMs = avgLatencyMs,
    )

fun NodeFormatStat.asEntity(): NodeFormatStatEntity =
    NodeFormatStatEntity(
        nodeId = nodeId,
        format = format.name,
        attempts = attempts,
        successes = successes,
        avgLatencyMs = avgLatencyMs,
    )

fun NodeFormatStat.asDto(): NodeFormatStatDto =
    NodeFormatStatDto(
        nodeId = nodeId,
        format = format,
        attempts = attempts,
        successes = successes,
        avgLatencyMs = avgLatencyMs,
    )

fun NodeFormatStatDto.asDomain(): NodeFormatStat =
    NodeFormatStat(
        nodeId = nodeId,
        format = format,
        attempts = attempts,
        successes = successes,
        avgLatencyMs = avgLatencyMs,
    )

fun StudySession.asEntity(): ActiveSessionEntity =
    ActiveSessionEntity(
        sessionId = sessionId,
        mode = mode.name,
        surface = surface.name,
        packetId = packetId,
        topicUnitId = topicUnitId,
        currentCourseTitle = currentCourseTitle,
        currentTopicTitle = currentTopicTitle,
        queueJson = encode(queueItemIds),
        rescueQueueJson = encode(rescueQueueItemIds),
        goalCorrectCount = goalCorrectCount,
        currentNodeId = currentNodeId,
        currentItemId = currentItemId,
        currentAttemptIndex = currentAttemptIndex,
        currentPromptKind = currentPromptKind.name,
        correctCount = correctCount,
        stepIndex = stepIndex,
        startedAt = startedAt,
        lastInteractionAt = lastInteractionAt,
        isMicroPromptActive = isMicroPromptActive,
        isExitArmed = isExitArmed,
        exitArmedUntil = exitArmedUntil,
        payloadJson = encode(payload),
    )

fun ActiveSessionEntity.asDomain(): StudySession =
    StudySession(
        sessionId = sessionId,
        mode = SessionMode.valueOf(mode),
        surface = Surface.valueOf(surface),
        packetId = packetId,
        topicUnitId = topicUnitId,
        currentCourseTitle = currentCourseTitle,
        currentTopicTitle = currentTopicTitle,
        queueItemIds = decode(queueJson),
        rescueQueueItemIds = decode(rescueQueueJson),
        goalCorrectCount = goalCorrectCount,
        currentNodeId = currentNodeId,
        currentItemId = currentItemId,
        currentAttemptIndex = currentAttemptIndex,
        currentPromptKind = SessionPromptKind.valueOf(currentPromptKind),
        correctCount = correctCount,
        stepIndex = stepIndex,
        startedAt = startedAt,
        lastInteractionAt = lastInteractionAt,
        isMicroPromptActive = isMicroPromptActive,
        isExitArmed = isExitArmed,
        exitArmedUntil = exitArmedUntil,
        payload = decode(payloadJson),
    )

fun StudySession.asDto(): StudySessionDto =
    StudySessionDto(
        sessionId = sessionId,
        mode = mode,
        surface = surface,
        packetId = packetId,
        topicUnitId = topicUnitId,
        currentCourseTitle = currentCourseTitle,
        currentTopicTitle = currentTopicTitle,
        queueItemIds = queueItemIds,
        rescueQueueItemIds = rescueQueueItemIds,
        goalCorrectCount = goalCorrectCount,
        currentNodeId = currentNodeId,
        currentItemId = currentItemId,
        currentAttemptIndex = currentAttemptIndex,
        currentPromptKind = currentPromptKind,
        correctCount = correctCount,
        stepIndex = stepIndex,
        startedAt = startedAt,
        lastInteractionAt = lastInteractionAt,
        isMicroPromptActive = isMicroPromptActive,
        isExitArmed = isExitArmed,
        exitArmedUntil = exitArmedUntil,
        payload = payload,
    )

fun StudySessionDto.asDomain(): StudySession =
    StudySession(
        sessionId = sessionId,
        mode = mode,
        surface = surface,
        packetId = packetId,
        topicUnitId = topicUnitId,
        currentCourseTitle = currentCourseTitle,
        currentTopicTitle = currentTopicTitle,
        queueItemIds = queueItemIds,
        rescueQueueItemIds = rescueQueueItemIds,
        goalCorrectCount = goalCorrectCount,
        currentNodeId = currentNodeId,
        currentItemId = currentItemId,
        currentAttemptIndex = currentAttemptIndex,
        currentPromptKind = currentPromptKind,
        correctCount = correctCount,
        stepIndex = stepIndex,
        startedAt = startedAt,
        lastInteractionAt = lastInteractionAt,
        isMicroPromptActive = isMicroPromptActive,
        isExitArmed = isExitArmed,
        exitArmedUntil = exitArmedUntil,
        payload = payload,
    )

fun AppSettings.asDto(): AppSettingsDto =
    AppSettingsDto(
        quickSessionTargetDefault = quickSessionTargetDefault,
        deepSessionQuestionTarget = deepSessionQuestionTarget,
        backExitWindowSeconds = backExitWindowSeconds,
        cognitiveNotificationsEnabled = cognitiveNotificationsEnabled,
        cognitiveNotificationsPerDay = cognitiveNotificationsPerDay,
        cognitiveNotificationWindowStartMinutes = cognitiveNotificationWindowStartMinutes,
        cognitiveNotificationWindowEndMinutes = cognitiveNotificationWindowEndMinutes,
        seedAppliedVersion = seedAppliedVersion,
        seedPackageId = seedPackageId,
        demoContentEnabled = demoContentEnabled,
    )

fun AppSettingsDto.asDomain(): AppSettings =
    AppSettings(
        quickSessionTargetDefault = quickSessionTargetDefault,
        deepSessionQuestionTarget = deepSessionQuestionTarget,
        backExitWindowSeconds = backExitWindowSeconds,
        cognitiveNotificationsEnabled = cognitiveNotificationsEnabled,
        cognitiveNotificationsPerDay = cognitiveNotificationsPerDay,
        cognitiveNotificationWindowStartMinutes = cognitiveNotificationWindowStartMinutes,
        cognitiveNotificationWindowEndMinutes = cognitiveNotificationWindowEndMinutes,
        seedAppliedVersion = seedAppliedVersion,
        seedPackageId = seedPackageId,
        demoContentEnabled = demoContentEnabled,
    )

fun SocialGateRule.asDto(): SocialGateRuleDto =
    SocialGateSchedule.normalizeRule(this).let { rule ->
        SocialGateRuleDto(
            packageName = rule.packageName,
            displayName = rule.displayName,
            enabled = rule.enabled,
            maxTriggersPerDay = rule.maxTriggersPerDay,
            requiredCorrectAnswers = rule.requiredCorrectAnswers,
            windowStartMinutes = rule.windowStartMinutes,
            windowEndMinutes = rule.windowEndMinutes,
        )
    }

fun SocialGateRuleDto.asDomain(): SocialGateRule =
    SocialGateSchedule.normalizeRule(
        SocialGateRule(
            packageName = packageName,
            displayName = displayName,
            enabled = enabled,
            maxTriggersPerDay = maxTriggersPerDay,
            requiredCorrectAnswers = requiredCorrectAnswers,
            windowStartMinutes = windowStartMinutes,
            windowEndMinutes = windowEndMinutes,
        ),
    )

fun SocialGateDailyState.asDto(): SocialGateDailyStateDto =
    SocialGateDailyStateDto(
        packageName = packageName,
        localDate = localDate,
        solvedCount = solvedCount,
        lastSolvedAt = lastSolvedAt,
    )

fun SocialGateDailyStateDto.asDomain(): SocialGateDailyState =
    SocialGateDailyState(
        packageName = packageName,
        localDate = localDate,
        solvedCount = solvedCount,
        lastSolvedAt = lastSolvedAt,
    )

fun SocialGateRuntimeState.asDto(): SocialGateRuntimeStateDto =
    SocialGateRuntimeStateDto(
        phase = phase.name,
        targetPackageName = targetPackageName,
        activeGateSessionId = activeGateSessionId,
        gateUnlockBaselineCorrectCount = gateUnlockBaselineCorrectCount,
        gateUnlockRequiredCorrectAnswers = gateUnlockRequiredCorrectAnswers,
        unlockTokenPackageName = unlockTokenPackageName,
        unlockTokenIssuedAt = unlockTokenIssuedAt,
        lastForegroundPackageName = lastForegroundPackageName,
        lastForegroundChangedAt = lastForegroundChangedAt,
        escapeUsesAt = escapeUsesAt,
    )

fun SocialGateRuntimeStateDto.asDomain(): SocialGateRuntimeState =
    SocialGateRuntimeState(
        phase = runCatching { SocialGateRuntimePhase.valueOf(phase) }.getOrDefault(SocialGateRuntimePhase.IDLE),
        targetPackageName = targetPackageName,
        activeGateSessionId = activeGateSessionId,
        gateUnlockBaselineCorrectCount = gateUnlockBaselineCorrectCount,
        gateUnlockRequiredCorrectAnswers = gateUnlockRequiredCorrectAnswers,
        unlockTokenPackageName = unlockTokenPackageName,
        unlockTokenIssuedAt = unlockTokenIssuedAt,
        lastForegroundPackageName = lastForegroundPackageName,
        lastForegroundChangedAt = lastForegroundChangedAt,
        escapeUsesAt = escapeUsesAt,
    )

fun ReviewEvent.asEntity(): ReviewEventEntity =
    ReviewEventEntity(
        eventId = eventId,
        sessionId = sessionId,
        nodeId = nodeId,
        itemId = itemId,
        answerOutcome = answerOutcome.name,
        latencyMs = latencyMs,
        surface = surface.name,
        sessionMode = sessionMode.name,
        attemptIndex = attemptIndex,
        occurredAt = occurredAt,
    )

fun ReviewEventEntity.asDomain(): ReviewEvent =
    ReviewEvent(
        eventId = eventId,
        sessionId = sessionId,
        nodeId = nodeId,
        itemId = itemId,
        answerOutcome = AttemptOutcome.valueOf(answerOutcome),
        latencyMs = latencyMs,
        surface = Surface.valueOf(surface),
        sessionMode = SessionMode.valueOf(sessionMode),
        attemptIndex = attemptIndex,
        occurredAt = occurredAt,
    )

fun SessionEvent.asEntity(): SessionEventEntity =
    SessionEventEntity(
        eventId = eventId,
        sessionId = sessionId,
        packetId = packetId,
        topicUnitId = topicUnitId,
        type = type.name,
        payloadJson = payloadJson,
        occurredAt = occurredAt,
    )

fun SessionEventEntity.asDomain(): SessionEvent =
    SessionEvent(
        eventId = eventId,
        sessionId = sessionId,
        packetId = packetId,
        topicUnitId = topicUnitId,
        type = SessionEventType.valueOf(type),
        payloadJson = payloadJson,
        occurredAt = occurredAt,
    )

fun AbandonEvent.asEntity(): AbandonEventEntity =
    AbandonEventEntity(
        eventId = eventId,
        sessionId = sessionId,
        nodeId = nodeId,
        itemId = itemId,
        reason = reason.name,
        occurredAt = occurredAt,
    )

fun AbandonEventEntity.asDomain(): AbandonEvent =
    AbandonEvent(
        eventId = eventId,
        sessionId = sessionId,
        nodeId = nodeId,
        itemId = itemId,
        reason = AbandonReason.valueOf(reason),
        occurredAt = occurredAt,
    )

fun ImportEvent.asEntity(): ImportEventEntity =
    ImportEventEntity(
        eventId = eventId,
        packageId = packageId,
        schemaVersion = schemaVersion,
        seedVersion = seedVersion,
        contentHash = contentHash,
        result = result.name,
        createdAt = createdAt,
    )

fun ImportEventEntity.asDomain(): ImportEvent =
    ImportEvent(
        eventId = eventId,
        packageId = packageId,
        schemaVersion = schemaVersion,
        seedVersion = seedVersion,
        contentHash = contentHash,
        result = ImportResultType.valueOf(result),
        createdAt = createdAt,
    )
