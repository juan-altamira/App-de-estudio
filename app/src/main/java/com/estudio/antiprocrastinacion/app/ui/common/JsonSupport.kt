package com.estudio.antiprocrastinacion.app.ui.common

import kotlinx.serialization.json.Json

val AppJson: Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = false
    }
