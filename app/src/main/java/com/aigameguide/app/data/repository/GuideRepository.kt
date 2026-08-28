package com.aigameguide.app.data.repository

import com.aigameguide.app.data.db.GameEntity
import com.aigameguide.app.data.db.GameMemoryEntity
import com.aigameguide.app.data.db.GuideDao
import com.aigameguide.app.data.db.GuideQuestionEntity
import com.aigameguide.app.data.db.ScreenshotEntity
import com.aigameguide.app.data.db.WebSourceEntity
import com.aigameguide.app.data.model.GuideAnswer
import com.aigameguide.app.data.model.GuideRequest
import com.aigameguide.app.data.model.MessageRole
import com.aigameguide.app.data.network.OpenAiGuideService
import kotlinx.coroutines.flow.Flow

class GuideRepository(
    private val dao: GuideDao,
    private val ai: OpenAiGuideService
) {
    val games: Flow<List<GameEntity>> = dao.observeGames()
    fun messages(gameId: Long): Flow<List<GuideQuestionEntity>> = dao.observeMessages(gameId)
    suspend fun addGame(game: GameEntity) = dao.insertGame(game)
    suspend fun updateGame(game: GameEntity) = dao.updateGame(game.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteGame(id: Long) = dao.deleteGame(id)
    suspend fun getMemory(gameId: Long) = dao.getMemory(gameId)?.facts.orEmpty()

    suspend fun ask(game: GameEntity, request: GuideRequest): GuideAnswer {
        val userId = dao.insertMessage(
            GuideQuestionEntity(
                gameId = game.id,
                role = MessageRole.USER.name,
                content = request.question,
                chapterSnapshot = game.chapter,
                regionSnapshot = game.region,
                spoilerLevel = game.spoilerLevel
            )
        )
        if (request.imagePaths.isNotEmpty()) {
            dao.insertScreenshots(request.imagePaths.map { ScreenshotEntity(questionId = userId, localPath = it) })
        }
        val context = dao.recentMessages(game.id, 8).reversed().joinToString("\n") { "${it.role}: ${it.content}" }
        val answer = ai.ask(request, context)
        val sourceBlock = if (answer.sources.isEmpty()) "" else answer.sources.joinToString(
            prefix = "\n\n출처\n", separator = "\n"
        ) { "• ${it.title} — ${it.url}" }
        dao.saveAnswer(
            GuideQuestionEntity(
                gameId = game.id,
                role = MessageRole.ASSISTANT.name,
                content = answer.text + sourceBlock,
                chapterSnapshot = game.chapter,
                regionSnapshot = game.region,
                spoilerLevel = game.spoilerLevel,
                usedWeb = answer.usedWeb
            ),
            answer.sources.map { WebSourceEntity(answerId = 0, title = it.title, url = it.url) }
        )
        applyProgressIfConfident(game, answer)
        return answer
    }

    private suspend fun applyProgressIfConfident(game: GameEntity, answer: GuideAnswer) {
        val p = answer.progressUpdate ?: return
        if (p.confidence < 0.85) return
        dao.updateGame(
            game.copy(
                chapter = p.chapter ?: game.chapter,
                region = p.region ?: game.region,
                mainQuest = p.mainQuest ?: game.mainQuest,
                progressPercent = p.progressPercent?.coerceIn(0, 100) ?: game.progressPercent,
                updatedAt = System.currentTimeMillis()
            )
        )
        p.memoryNote?.let { note ->
            val old = dao.getMemory(game.id)
            val facts = listOfNotNull(old?.facts?.takeIf { it.isNotBlank() }, note).joinToString("\n• ")
            dao.saveMemory(GameMemoryEntity(id = old?.id ?: 0, gameId = game.id, facts = facts, updatedAt = System.currentTimeMillis()))
        }
    }
}
