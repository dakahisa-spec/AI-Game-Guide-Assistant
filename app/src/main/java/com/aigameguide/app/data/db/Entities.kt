package com.aigameguide.app.data.db

import androidx.room.Entity
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
