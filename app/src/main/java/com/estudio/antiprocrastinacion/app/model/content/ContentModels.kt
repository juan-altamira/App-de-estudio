package com.estudio.antiprocrastinacion.app.model.content

data class Course(
    val courseId: String,
    val title: String,
    val description: String?,
    val version: Int,
    val updatedAt: Long,
)

data class UnitModel(
    val unitId: String,
    val courseId: String,
    val title: String,
    val description: String?,
    val orderIndex: Int,
    val version: Int,
    val updatedAt: Long,
)

data class Outcome(
    val outcomeId: String,
    val unitId: String,
    val title: String,
    val description: String?,
    val version: Int,
    val updatedAt: Long,
)

data class Node(
    val nodeId: String,
    val courseId: String,
    val unitId: String,
    val outcomeIds: List<String>,
    val title: String,
    val coreClaim: String,
    val type: NodeType,
    val weightExam: Double,
    val prerequisites: List<String>,
    val facets: List<FacetType>,
    val mustKnow: List<String>,
    val commonErrors: List<String>,
    val minimumMasteryDefinition: String,
    val surfaceEasyReady: Boolean,
    val surfaceEasyItemCount: Int,
    val sourceRefs: List<String>,
    val version: Int,
    val updatedAt: Long,
    val archivedCandidate: Boolean,
    val contentOrigin: ContentOrigin,
)

data class ItemOption(
    val id: String,
    val text: String,
    val isCorrect: Boolean = false,
)

data class Item(
    val itemId: String,
    val nodeId: String,
    val facet: FacetType,
    val format: ItemFormat,
    val frictionLevel: Int,
    val difficultySeed: Double,
    val itemRole: ItemRole,
    val allowedSurfaces: List<Surface>,
    val cooldownHours: Double,
    val stem: String,
    val correctAnswer: String,
    val feedbackShort: String,
    val coversMustKnow: List<String>,
    val variantGroupId: String?,
    val rescueGroupId: String?,
    val nodeComplexity: Double?,
    val facetComplexity: Double?,
    val distractorSimilarity: Double?,
    val prerequisiteDepth: Double?,
    val targetsErrorIds: List<String>,
    val commonErrorSignals: List<String>,
    val options: List<ItemOption>,
    val version: Int,
    val updatedAt: Long,
    val sourceRefs: List<String>,
    val contentOrigin: ContentOrigin,
    val archivedManual: Boolean = false,
    val editedManual: Boolean = false,
)

data class ItemOverride(
    val itemId: String,
    val archived: Boolean = false,
    val stemOverride: String? = null,
    val correctAnswerOverride: String? = null,
    val optionsOverride: List<ItemOption>? = null,
)

data class ManualItemEdit(
    val itemId: String,
    val stem: String,
    val correctAnswer: String,
    val options: List<ItemOption>? = null,
)

data class ContentTree(
    val courses: List<CourseWithUnits>,
)

data class CourseWithUnits(
    val course: Course,
    val units: List<UnitWithOutcomes>,
)

data class UnitWithOutcomes(
    val unit: UnitModel,
    val outcomes: List<OutcomeWithNodes>,
)

data class OutcomeWithNodes(
    val outcome: Outcome,
    val nodes: List<Node>,
)

data class NodeDetail(
    val node: Node,
    val items: List<Item>,
)
