package com.estudio.antiprocrastinacion

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import com.estudio.antiprocrastinacion.app.di.AppContainer

/**
 * Runner de instrumentación que AÍSLA toda la instrumentación de la base real del usuario.
 *
 * Antes de que arranque la app bajo prueba, sobreescribe el nombre de archivo de la base para que la
 * app use una base de test descartable ("study-instrumentation-test.db") en lugar de "study.db". Así
 * NINGÚN test instrumentado —ni siquiera los que arrancan la app real (MainActivity/StudyApplication) y
 * disparan el sembrado del demo— puede leer, modificar ni borrar los cursos/progreso reales del usuario.
 *
 * Es la barrera que faltaba: la causa raíz del borrado fue un test corriendo contra "study.db".
 */
class StudyInstrumentationRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        AppContainer.databaseNameOverride = INSTRUMENTATION_DB_NAME
        // Empezar de una base limpia en cada corrida (y nunca tocar la real).
        runCatching { targetContext.deleteDatabase(INSTRUMENTATION_DB_NAME) }
        super.onCreate(arguments)
    }

    companion object {
        const val INSTRUMENTATION_DB_NAME = "study-instrumentation-test.db"
    }
}
