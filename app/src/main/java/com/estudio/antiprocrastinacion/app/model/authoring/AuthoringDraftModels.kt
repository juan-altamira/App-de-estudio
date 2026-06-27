package com.estudio.antiprocrastinacion.app.model.authoring

import com.estudio.antiprocrastinacion.app.model.content.FacetType
import com.estudio.antiprocrastinacion.app.model.content.ItemFormat
import com.estudio.antiprocrastinacion.app.model.content.ItemRole
import com.estudio.antiprocrastinacion.app.model.content.NodeType
import kotlinx.serialization.Serializable

@Serializable
data class AuthoringDraftPackageDto(
    val packageKey: String,
    val schemaVersion: Int = 1,
    val sourceRefs: List<String> = emptyList(),
    val course: AuthoringCourseDraftDto,
    val units: List<AuthoringUnitDraftDto>,
)

@Serializable
data class AuthoringCourseDraftDto(
    val courseKey: String,
    val title: String,
    val description: String? = null,
)

@Serializable
data class AuthoringUnitDraftDto(
    val unitKey: String,
    val title: String,
    val description: String? = null,
    val orderIndex: Int = 1,
    val sourceRefs: List<String> = emptyList(),
    val outcomes: List<AuthoringOutcomeDraftDto>,
    val nodeDrafts: List<AuthoringNodeDraftDto>,
)

@Serializable
data class AuthoringOutcomeDraftDto(
    val outcomeKey: String,
    val title: String,
    val description: String? = null,
)

@Serializable
data class AuthoringNodeDraftDto(
    val nodeKey: String,
    val title: String,
    val coreClaim: String,
    val nodeTypeHint: NodeType,
    val outcomeKeys: List<String>,
    val weightExam: Double = 0.7,
    val prerequisites: List<String> = emptyList(),
    val facets: List<FacetType>,
    val mustKnow: List<String>,
    val commonErrors: List<String>,
    val minimumMasteryDefinition: String,
    val sourceRefs: List<String> = emptyList(),
    val items: List<AuthoringItemDraftDto>,
)

@Serializable
data class AuthoringItemDraftDto(
    val itemKey: String,
    val sourceOrder: Int? = null,
    val format: ItemFormat,
    val facet: FacetType,
    val roleHint: ItemRole,
    val stem: String,
    val options: List<AuthoringOptionDraftDto> = emptyList(),
    val correct: String,
    val feedback: String,
    val coversMustKnow: List<String>,
    val targetsCommonErrors: List<String> = emptyList(),
    val difficultySeed: Double? = null,
    val sourceRefs: List<String> = emptyList(),
)

@Serializable
data class AuthoringOptionDraftDto(
    val key: String,
    val text: String,
)
