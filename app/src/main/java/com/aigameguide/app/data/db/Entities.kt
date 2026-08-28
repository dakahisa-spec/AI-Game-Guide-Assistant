package com.aigameguide.app.data.db

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val platform: String,
    val startDate: Long = System.currentTimeMillis(),
    val playHours: Float = 0f,
    val chapter: String = "",
    val region: String = "",
    val mainQuest: String = "",
    val progressPercent: Int = 0,
    val note: String = "",
    val spoilerLevel: String = "NONE",
    val playStyle: String = "BALANCED",
    val coverUri: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "game_memory",
    foreignKeys = [ForeignKey(
        entity = GameEntity::class,
        parentColumns = ["id"],
        childColumns = ["gameId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["gameId"], unique = true)]
)
data class GameMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: Long,
    val facts: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "guide_questions",
    foreignKeys = [ForeignKey(
        entity = GameEntity::class,
        parentColumns = ["id"],
        childColumns = ["gameId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("gameId"), Index("createdAt")]
)
data class GuideQuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: Long,
    val role: String,
    val content: String,
    val chapterSnapshot: String = "",
    val regionSnapshot: String = "",
    val spoilerLevel: String = "NONE",
    val usedWeb: Boolean = false,
    @ColumnInfo(defaultValue = "''") val actualModelKey: String = "",
    @ColumnInfo(defaultValue = "0") val autoSelectedModel: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "screenshots",
    foreignKeys = [ForeignKey(
        entity = GuideQuestionEntity::class,
        parentColumns = ["id"],
        childColumns = ["questionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("questionId")]
)
data class ScreenshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: Long,
    val localPath: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "web_sources",
    foreignKeys = [ForeignKey(
        entity = GuideQuestionEntity::class,
        parentColumns = ["id"],
        childColumns = ["answerId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("answerId")]
)
data class WebSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val answerId: Long,
    val title: String,
    val url: String
)

@Entity(tableName = "ai_providers")
data class AiProviderEntity(
    @PrimaryKey val providerId: String,
    val displayName: String,
    val providerType: String,
    val baseUrl: String,
    val supportsModelSync: Boolean = false,
    val enabled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "ai_models",
    foreignKeys = [ForeignKey(
        entity = AiProviderEntity::class,
        parentColumns = ["providerId"],
        childColumns = ["providerId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("providerId"), Index("lastUsedAt")]
)
data class AiModelEntity(
    @PrimaryKey val modelKey: String,
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val description: String,
    val tier: String,
    val supportsText: Boolean = true,
    val supportsVision: Boolean = false,
    val supportsMultipleImages: Boolean = false,
    val supportsTools: Boolean = false,
    val supportsWebSearch: Boolean = false,
    val supportsLongContext: Boolean = false,
    val reasoningLevel: Int = 1,
    val speedLevel: Int = 1,
    val costLevel: Int = 1,
    val favorite: Boolean = false,
    val enabled: Boolean = true,
    val lastUsedAt: Long = 0
)

@Entity(
    tableName = "game_ai_preferences",
    foreignKeys = [ForeignKey(
        entity = GameEntity::class,
        parentColumns = ["id"],
        childColumns = ["gameId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("modelKey")]
)
data class GameAiPreferenceEntity(
    @PrimaryKey val gameId: Long,
    val modelKey: String = "AUTO",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_settings")
data class AiSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val defaultModelKey: String = "AUTO",
    val usageMode: String = "BALANCED",
    val updatedAt: Long = System.currentTimeMillis()
)
