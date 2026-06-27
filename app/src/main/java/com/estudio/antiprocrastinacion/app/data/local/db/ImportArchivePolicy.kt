package com.estudio.antiprocrastinacion.app.data.local.db

import com.estudio.antiprocrastinacion.app.domain.repository.ImportValidationProfile

/**
 * Minimal reference to an already-persisted node, used to decide whether an import
 * should archive it. Kept free of Room types so the decision stays a pure, unit-testable function.
 */
internal data class ArchivableNodeRef(
    val nodeId: String,
    val courseId: String,
)

/**
 * Full-package imports (DEMO seed, pedagogical authoring packages) carry "republish" semantics:
 * a node of the same origin that lives in an affected course but is absent from the incoming
 * package was intentionally removed and must stop being scheduled (archived as candidate).
 *
 * Incremental imports (explicit additive content packages and the reviewed question-bank / manual
 * builder path) are purely additive: the user is adding questions/units, never republishing a whole
 * course. Archiving sibling nodes there would silently remove other units of the same course from
 * spaced repetition. So for additive profiles this returns an empty list.
 */
internal fun additiveImport(profile: ImportValidationProfile): Boolean =
    profile == ImportValidationProfile.ADDITIVE_CONTENT_PACKAGE ||
        profile == ImportValidationProfile.REVIEWED_QUESTION_BANK

internal fun nodeIdsToArchiveOnImport(
    existingNodesOfOrigin: List<ArchivableNodeRef>,
    affectedCourseIds: Set<String>,
    incomingNodeIds: Set<String>,
    profile: ImportValidationProfile,
): List<String> =
    if (additiveImport(profile)) {
        emptyList()
    } else {
        existingNodesOfOrigin
            .filter { it.courseId in affectedCourseIds && it.nodeId !in incomingNodeIds }
            .map { it.nodeId }
    }
