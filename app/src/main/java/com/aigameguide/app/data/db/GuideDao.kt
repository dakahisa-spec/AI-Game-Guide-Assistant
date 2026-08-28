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

    @Transaction
    suspend fun saveAnswer(answer: GuideQuestionEntity, sources: List<WebSourceEntity>): Long {
        val id = insertMessage(answer)
        if (sources.isNotEmpty()) insertSources(sources.map { it.copy(answerId = id) })
        return id
    }
}
