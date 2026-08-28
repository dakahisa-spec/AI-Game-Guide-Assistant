package com.aigameguide.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [GameEntity::class, GameMemoryEntity::class, GuideQuestionEntity::class,
        ScreenshotEntity::class, WebSourceEntity::class, AiProviderEntity::class,
        AiModelEntity::class, GameAiPreferenceEntity::class, AiSettingsEntity::class],
    version = 2,
    exportSchema = true
)
abstract class GuideDatabase : RoomDatabase() {
    abstract fun guideDao(): GuideDao

    companion object {
        @Volatile private var INSTANCE: GuideDatabase? = null

        fun get(context: Context): GuideDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                GuideDatabase::class.java,
                "ai_game_guide.db"
            ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE guide_questions ADD COLUMN actualModelKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE guide_questions ADD COLUMN autoSelectedModel INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS ai_providers (providerId TEXT NOT NULL, displayName TEXT NOT NULL, providerType TEXT NOT NULL, baseUrl TEXT NOT NULL, supportsModelSync INTEGER NOT NULL, enabled INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(providerId))")
                db.execSQL("CREATE TABLE IF NOT EXISTS ai_models (modelKey TEXT NOT NULL, providerId TEXT NOT NULL, modelId TEXT NOT NULL, displayName TEXT NOT NULL, description TEXT NOT NULL, tier TEXT NOT NULL, supportsText INTEGER NOT NULL, supportsVision INTEGER NOT NULL, supportsMultipleImages INTEGER NOT NULL, supportsTools INTEGER NOT NULL, supportsWebSearch INTEGER NOT NULL, supportsLongContext INTEGER NOT NULL, reasoningLevel INTEGER NOT NULL, speedLevel INTEGER NOT NULL, costLevel INTEGER NOT NULL, favorite INTEGER NOT NULL, enabled INTEGER NOT NULL, lastUsedAt INTEGER NOT NULL, PRIMARY KEY(modelKey), FOREIGN KEY(providerId) REFERENCES ai_providers(providerId) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_models_providerId ON ai_models(providerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_models_lastUsedAt ON ai_models(lastUsedAt)")
                db.execSQL("CREATE TABLE IF NOT EXISTS game_ai_preferences (gameId INTEGER NOT NULL, modelKey TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(gameId), FOREIGN KEY(gameId) REFERENCES games(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_game_ai_preferences_modelKey ON game_ai_preferences(modelKey)")
                db.execSQL("CREATE TABLE IF NOT EXISTS ai_settings (id INTEGER NOT NULL, defaultModelKey TEXT NOT NULL, usageMode TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("INSERT OR IGNORE INTO ai_settings(id, defaultModelKey, usageMode, updatedAt) VALUES(1, 'AUTO', 'BALANCED', strftime('%s','now') * 1000)")
            }
        }
    }
}
