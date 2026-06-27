package com.estudio.antiprocrastinacion.app.ui.common

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface TimeProvider {
    fun now(): Long
}

@Singleton
class DefaultTimeProvider @Inject constructor() : TimeProvider {
    override fun now(): Long = System.currentTimeMillis()
}

interface IdProvider {
    fun newId(): String
}

@Singleton
class DefaultIdProvider @Inject constructor() : IdProvider {
    override fun newId(): String = UUID.randomUUID().toString()
}

fun Double.normalized(): Double = coerceIn(0.0, 1.0)
