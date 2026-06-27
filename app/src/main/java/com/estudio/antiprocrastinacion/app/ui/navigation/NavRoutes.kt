package com.estudio.antiprocrastinacion.app.ui.navigation

object NavRoutes {
    const val BOOT = "boot"
    const val HOME = "home"
    const val QUICK_COMPLETE = "quick_complete"
    const val CONTENT = "content"
    const val IMPORT = "import"
    const val EDITABLE_IMPORT = "editable_import"
    const val MANUAL_BUILDER = "manual_builder"
    const val UPCOMING_REVIEWS = "upcoming_reviews"
    const val NOTIFICATION_SETTINGS = "notification_settings"
    const val SOCIAL_GATE_SETTINGS = "social_gate_settings"
    const val DEEP = "deep"
    const val DRAIN = "drain"
    const val STUDY = "study"

    fun study(sessionId: String): String = "$STUDY/$sessionId"
}
