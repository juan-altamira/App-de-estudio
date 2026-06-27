package com.estudio.antiprocrastinacion.app.data.local.db

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.estudio.antiprocrastinacion.app.data.local.dao.ContentDao
import com.estudio.antiprocrastinacion.app.data.local.dao.EventDao
import com.estudio.antiprocrastinacion.app.data.local.dao.NodeStateDao
import com.estudio.antiprocrastinacion.app.data.local.dao.SessionDao

@Database(
    entities = [
        CourseEntity::class,
        UnitEntity::class,
        OutcomeEntity::class,
        NodeEntity::class,
        ItemEntity::class,
        NodeStateEntity::class,
        ItemStateEntity::class,
        ItemOverrideEntity::class,
        NodeFormatStatEntity::class,
        ActiveSessionEntity::class,
        ReviewEventEntity::class,
        SessionEventEntity::class,
        AbandonEventEntity::class,
        ImportEventEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class StudyDatabase : RoomDatabase() {
    abstract fun contentDao(): ContentDao
    abstract fun nodeStateDao(): NodeStateDao
    abstract fun sessionDao(): SessionDao
    abstract fun eventDao(): EventDao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `item_states` (
                            `itemId` TEXT NOT NULL,
                            `stage` INTEGER NOT NULL,
                            `lastReviewedAt` INTEGER,
                            `nextReviewAt` INTEGER,
                            `timesSeen` INTEGER NOT NULL,
                            `timesFailed` INTEGER NOT NULL,
                            `timesCorrectFirstTry` INTEGER NOT NULL,
                            `timesRecoveredAfterFailure` INTEGER NOT NULL,
                            `timesAbandoned` INTEGER NOT NULL,
                            `lastOutcome` TEXT,
                            PRIMARY KEY(`itemId`)
                        )
                        """.trimIndent(),
                    )
                    database.execSQL("ALTER TABLE `active_sessions` ADD COLUMN `currentCourseTitle` TEXT NOT NULL DEFAULT ''")
                    database.execSQL("ALTER TABLE `active_sessions` ADD COLUMN `currentPromptKind` TEXT NOT NULL DEFAULT 'MANUAL'")
                    database.execSQL("ALTER TABLE `active_sessions` ADD COLUMN `payloadJson` TEXT NOT NULL DEFAULT '{}'")
                }
            }

        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `item_overrides` (
                            `itemId` TEXT NOT NULL,
                            `archived` INTEGER NOT NULL,
                            `stemOverride` TEXT,
                            `correctAnswerOverride` TEXT,
                            `optionsJsonOverride` TEXT,
                            PRIMARY KEY(`itemId`)
                        )
                        """.trimIndent(),
                    )
                }
            }
    }
}
