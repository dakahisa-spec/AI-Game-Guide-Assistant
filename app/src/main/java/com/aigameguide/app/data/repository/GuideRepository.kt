package com.aigameguide.app.data.repository

import com.aigameguide.app.data.db.GameEntity
import com.aigameguide.app.data.db.AiModelEntity
import com.aigameguide.app.data.db.AiProviderEntity
import com.aigameguide.app.data.db.AiSettingsEntity
import com.aigameguide.app.data.db.GameMemoryEntity
import com.aigameguide.app.data.db.GuideDao
import com.aigameguide.app.data.db.GuideQuestionEntity
import com.aigameguide.app.data.db.ScreenshotEntity
import com.aigameguide.app.data.db.WebSourceEntity
import com.aigameguide.app.data.model.GuideAnswer
import com.aigameguide.app.data.model.GuideRequest
import com.aigameguide.app.data.model.MessageRole
import com.aigameguide.app.data.ai.AiGateway
import com.aigameguide.app.data.ai.LOCAL_MODEL_KEY
import kotlinx.coroutines.flow.Flow

class GuideRepository(
    private val dao: GuideDao,
    private val ai: AiGateway
) {
    val games: Flow<List<GameEntity>> = dao.observeGames()
    val aiProviders: Flow<List<AiProviderEntity>> = dao.observeAiProviders()
    val aiModels: Flow<List<AiModelEntity>> = dao.observeAiModels()
    val aiSettings: Flow<AiSettingsEntity?> = dao.observeAiSettings()
    fun messages(gameId: Long): Flow<List<GuideQuestionEntity>> = dao.observeMessages(gameId)
    suspend fun addGame(game: GameEntity) = dao.insertGame(game)
    suspend fun updateGame(game: GameEntity) = dao.updateGame(game.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteGame(id: Long) = dao.deleteGame(id)
    suspend fun getMemory(gameId: Long) = dao.getMemory(gameId)?.facts.orEmpty()

    suspend fun ensureAiCatalog() = ai.ensureCatalog()
    suspend fun gameModelKey(gameId: Long): String = dao.getGameAiPreference(gameId)?.modelKey ?: "AUTO"
    suspend fun saveGameModel(gameId: Long, modelKey: String) = ai.saveGameModel(gameId, modelKey)
    suspend fun saveGlobalAiSettings(defaultModelKey: String, usageMode: String) = ai.saveGlobalSettings(defaultModelKey, usageMode)
    suspend fun toggleFavorite(model: AiModelEntity) = ai.toggleFavorite(model)
    suspend fun saveProvider(providerId: String, baseUrl: String, apiKey: String, customModelId: String?) =
        ai.saveProvider(providerId, baseUrl, apiKey, customModelId)
    suspend fun testProvider(providerId: String) = ai.testProvider(providerId)
    suspend fun syncModels(providerId: String) = ai.syncModels(providerId)

    suspend fun ask(game: GameEntity, request: GuideRequest, temporaryModelKey: String? = null): GuideAnswer {
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
        val local = localAnswer(game, request.question)
        val context = dao.recentMessages(game.id, 8).reversed().joinToString("\n") { "${it.role}: ${it.content}" }
        val answer = local ?: ai.ask(game.id, request, context, temporaryModelKey)
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
                usedWeb = answer.usedWeb,
                actualModelKey = answer.usedModelKey,
                autoSelectedModel = answer.autoSelectedModel
            ),
            answer.sources.map { WebSourceEntity(answerId = 0, title = it.title, url = it.url) }
        )
        applyProgressIfConfident(game, answer)
        return answer
    }

    private fun localAnswer(game: GameEntity, question: String): GuideAnswer? {
        if (question.isBlank()) return null
        val q = question.lowercase().replace(" ", "")
        val asksProgress = listOf("저장된진행률", "내진행률", "진행률몇", "몇%로저장", "어디까지저장").any(q::contains)
        if (asksProgress) {
            val where = listOf(game.chapter, game.region, game.mainQuest).filter { it.isNotBlank() }.joinToString(" · ")
            val text = buildString {
                append("현재 앱에 저장된 진행률은 ${game.progressPercent}%입니다.")
                if (where.isNotBlank()) append("\n\n저장 위치: $where")
                append("\n플레이 시간: ${game.playHours.toInt()}시간")
            }
            return GuideAnswer(text, emptyList(), false, usedModelKey = LOCAL_MODEL_KEY, usedModelName = "로컬 DB")
        }
        return null
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
