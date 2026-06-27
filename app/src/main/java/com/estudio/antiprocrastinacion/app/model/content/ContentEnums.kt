package com.estudio.antiprocrastinacion.app.model.content

import kotlinx.serialization.Serializable

@Serializable
enum class Surface {
    ALARM,
    NOTIFICATION,
    WIDGET,
    SOCIAL_GATE,
    IN_APP_QUICK,
    IN_APP_DEEP,
    BACK_MICRO,
}

@Serializable
enum class SessionMode {
    QUICK,
    DEEP,
    DRAIN,
}

@Serializable
enum class NodeType {
    CONCEPT,
    PROCESS,
    COMPARISON,
    INTEGRATION,
    BOSS,
}

@Serializable
enum class FacetType {
    DEFINICION_FUNCIONAL,
    PROCESO_PASO_A_PASO,
    DIFERENCIA_ENTRE_CONCEPTOS,
    CAUSA_EFECTO,
    ESCENARIO_APLICADO,
    CASO_LIMITE,
    ERROR_TIPICO,
    INTEGRACION_CON_OTRO_NODO,
}

@Serializable
enum class ItemFormat {
    TRUE_FALSE,
    MULTIPLE_CHOICE,
    CHOOSE_FALSE_STATEMENT,
    MATCHING_SIMPLE,
    FILL_ONE_WORD,
    ORDER_STEPS_SHORT,
    FILL_SHORT_BLANK,
    MINI_SCENARIO_MCQ,
    CONNECT_CONCEPTS,
    ONE_SENTENCE_EXPLANATION,
    COMPARE_A_VS_B,
    WHAT_HAPPENS_IF_X,
    FULL_RECONSTRUCTION,
    LONG_PROCESS_EXPLANATION,
    CASE_WITHOUT_HINTS,
}

@Serializable
enum class ItemRole {
    CORE,
    VARIANT,
    TRAP,
    INTEGRATION,
    BOSS,
    RESCUE,
}

@Serializable
enum class ContentOrigin {
    DEMO,
    IMPORTED,
}
