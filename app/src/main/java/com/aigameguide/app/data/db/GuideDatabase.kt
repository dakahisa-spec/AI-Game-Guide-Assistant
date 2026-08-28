package com.aigameguide.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [GameEntity::class, GameMemoryEntity::class, GuideQuestionEntity::class,
        ScreenshotEntity::class, WebSourceEntity::class],
    version = 1,
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
            ).build().also { INSTANCE = it }
        }
    }
}
