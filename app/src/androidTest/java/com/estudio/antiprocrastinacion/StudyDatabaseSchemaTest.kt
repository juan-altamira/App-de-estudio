package com.estudio.antiprocrastinacion

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.estudio.antiprocrastinacion.app.data.local.db.StudyDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyDatabaseSchemaTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            StudyDatabase::class.java,
        )

    @Test
    fun createVersionOneSchema() {
        helper.createDatabase(TEST_DB, 1).close()
    }

    @Test
    fun migrateVersionTwoToThree() {
        helper.createDatabase(TEST_DB, 2).close()
        helper.runMigrationsAndValidate(TEST_DB, 3, true, StudyDatabase.MIGRATION_2_3).close()
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
