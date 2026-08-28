package com.aigameguide.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GuideDao {
    @Query("SELECT * FROM games ORDER BY updatedAt DESC")
    fun observeGames(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun getGame(id: Long): GameEntity?

    @Insert
    suspend fun insertGame(game: GameEntity): Long

    @Update
    suspend fun updateGame(game: GameEntity)

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun deleteGame(id: Long)

    @Query("SELECT * FROM guide_questions WHERE gameId = :gameId ORDER BY createdAt ASC")
    fun observeMessages(gameId: Long): Flow<List<GuideQuestionEntity>>

    @Query("SELECT * FROM guide_questions WHERE gameId = :gameId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentMessages(gameId: Long, limit: Int = 8): List<GuideQuestionEntity>

    @Insert
    suspend fun insertMessage(message: GuideQuestionEntity): Long

    @Insert
    suspend fun insertScreenshots(items: List<ScreenshotEntity>)

    @Insert
    suspend fun insertSources(items: List<WebSourceEntity>)

    @Query("SELECT * FROM game_memory WHERE gameId = :gameId LIMIT 1")
    suspend fun getMemory(gameId: Long): GameMemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMemory(memory: GameMemoryEntity)

    @Query("SELECT * FROM ai_providers ORDER BY displayName")
    fun observeAiProviders(): Flow<List<AiProviderEntity>>

    @Query("SELECT * FROM ai_providers WHERE providerId = :providerId LIMIT 1")
    suspend fun getAiProvider(providerId: String): AiProviderEntity?

    @Query("SELECT * FROM ai_models WHERE enabled = 1 ORDER BY favorite DESC, lastUsedAt DESC, providerId, costLevel")
    fun observeAiModels(): Flow<List<AiModelEntity>>

    @Query("SELECT * FROM ai_models WHERE enabled = 1")
    suspend fun getEnabledAiModels(): List<AiModelEntity>

    @Query("SELECT * FROM ai_models WHERE modelKey = :modelKey LIMIT 1")
    suspend fun getAiModel(modelKey: String): AiModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAiProviders(items: List<AiProviderEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAiProviders(items: List<AiProviderEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAiModels(items: List<AiModelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAiModels(items: List<AiModelEntity>)

    @Query("UPDATE ai_models SET favorite = :favorite WHERE modelKey = :modelKey")
    suspend fun setModelFavorite(modelKey: String, favorite: Boolean)

    @Query("UPDATE ai_models SET lastUsedAt = :usedAt WHERE modelKey = :modelKey")
    suspend fun markModelUsed(modelKey: String, usedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM game_ai_preferences WHERE gameId = :gameId LIMIT 1")
    suspend fun getGameAiPreference(gameId: Long): GameAiPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGameAiPreference(preference: GameAiPreferenceEntity)

    @Query("SELECT * FROM ai_settings WHERE id = 1 LIMIT 1")
    fun observeAiSettings(): Flow<AiSettingsEntity?>

    @Query("SELECT * FROM ai_settings WHERE id = 1 LIMIT 1")
    suspend fun getAiSettings(): AiSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAiSettings(settings: AiSettingsEntity)

    @Transaction
    suspend fun saveAnswer(answer: GuideQuestionEntity, sources: List<WebSourceEntity>): Long {
        val id = insertMessage(answer)
        if (sources.isNotEmpty()) insertSources(sources.map { it.copy(answerId = id) })
        return id
    }
}
