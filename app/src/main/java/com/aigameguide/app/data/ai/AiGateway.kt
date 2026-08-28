package com.aigameguide.app.data.ai

import com.aigameguide.app.data.db.AiModelEntity
import com.aigameguide.app.data.db.AiSettingsEntity
import com.aigameguide.app.data.db.GameAiPreferenceEntity
import com.aigameguide.app.data.db.GuideDao
import com.aigameguide.app.data.model.GuideAnswer
import com.aigameguide.app.data.model.GuideRequest
import com.aigameguide.app.data.security.ApiKeyVault
import kotlin.math.abs

data class AiSelection(
    val model: AiModelEntity,
    val automatic: Boolean,
    val requestedModelKey: String
)

object AiRoutingPolicy {
    fun desiredTier(request: GuideRequest, mode: String): AiModelTier {
        val needsMulti = request.imagePaths.size > 1
        val needsWeb = request.forceWebSearch || listOf("최신", "패치", "업데이트", "트로피", "버그").any(request.question.lowercase()::contains)
        return when {
            mode == AiUsageMode.SAVER.name && !needsMulti -> AiModelTier.FAST
            mode == AiUsageMode.QUALITY.name -> AiModelTier.HIGH
            needsMulti || request.question.length > 140 || listOf("전부 비교", "엔딩", "종합", "어려운 퍼즐", "빌드 분석").any(request.question::contains) -> AiModelTier.HIGH
            needsWeb || request.hintStage > 0 -> AiModelTier.BALANCED
            else -> AiModelTier.FAST
        }
    }

    fun supports(model: AiModelEntity, request: GuideRequest, needsWeb: Boolean): Boolean {
        val needsImages = request.imagePaths.isNotEmpty()
        val needsMulti = request.imagePaths.size > 1
        return model.supportsText && (!needsImages || model.supportsVision) &&
            (!needsMulti || model.supportsMultipleImages) && (!needsWeb || model.supportsWebSearch)
    }
}

class AiGateway(
    private val dao: GuideDao,
    private val keyVault: ApiKeyVault,
    private val providerFactory: AiProviderFactory
) {
    suspend fun ensureCatalog() {
        dao.insertAiProviders(AiCatalog.providers)
        dao.insertAiModels(AiCatalog.models)
        if (dao.getAiSettings() == null) dao.saveAiSettings(AiSettingsEntity())
    }

    suspend fun ask(
        gameId: Long,
        request: GuideRequest,
        recentContext: String,
        temporaryModelKey: String?
    ): GuideAnswer {
        val gameModel = dao.getGameAiPreference(gameId)?.modelKey
        val global = dao.getAiSettings() ?: AiSettingsEntity()
        val requested = temporaryModelKey?.takeIf { it.isNotBlank() }
            ?: gameModel?.takeIf { it.isNotBlank() }
            ?: global.defaultModelKey.ifBlank { AUTO_MODEL_KEY }
        val all = dao.getEnabledAiModels()
        val explicit = requested != AUTO_MODEL_KEY
        val retiredSelection = explicit && all.none { it.modelKey == requested }
        val selected = if (explicit) {
            val saved = all.firstOrNull { it.modelKey == requested }
            if (saved == null) selectAutomatic(all, request, global.usageMode, requested)
            else validateExplicit(saved, request).let { AiSelection(it, false, requested) }
        } else selectAutomatic(all, request, global.usageMode, requested)

        if (!selected.automatic) return call(selected, request, recentContext)

        val candidates = automaticCandidates(all, request, global.usageMode)
        var lastError: Throwable? = null
        for ((index, candidate) in candidates.withIndex()) {
            runCatching { call(AiSelection(candidate, true, requested), request, recentContext) }
                .onSuccess {
                    val notice = when {
                        retiredSelection -> "이전에 선택한 AI 모델을 사용할 수 없어 자동 모델로 변경했습니다.\n\n"
                        index > 0 -> "첫 번째 자동 모델을 사용할 수 없어 호환 모델로 전환했습니다.\n\n"
                        else -> ""
                    }
                    return if (notice.isBlank()) it else it.copy(text = notice + it.text)
                }
                .onFailure { lastError = it }
        }
        throw lastError ?: AiConfigurationException("사용 가능한 AI 모델의 API 설정이 필요합니다.")
    }

    suspend fun saveGameModel(gameId: Long, modelKey: String) =
        dao.saveGameAiPreference(GameAiPreferenceEntity(gameId, modelKey))

    suspend fun saveGlobalSettings(defaultModelKey: String, usageMode: String) =
        dao.saveAiSettings(AiSettingsEntity(defaultModelKey = defaultModelKey, usageMode = usageMode))

    suspend fun toggleFavorite(model: AiModelEntity) = dao.setModelFavorite(model.modelKey, !model.favorite)

    suspend fun saveProvider(providerId: String, baseUrl: String, apiKey: String, customModelId: String?) {
        val current = dao.getAiProvider(providerId) ?: AiCatalog.providers.first { it.providerId == providerId }
        val safeUrl = baseUrl.trim().trimEnd('/')
        if (safeUrl.isNotBlank() && !safeUrl.startsWith("https://")) throw AiConfigurationException("Base URL은 https:// 주소를 입력해 주세요.")
        dao.upsertAiProviders(listOf(current.copy(baseUrl = safeUrl.ifBlank { current.baseUrl }, updatedAt = System.currentTimeMillis())))
        if (apiKey.isNotBlank()) keyVault.save(providerId, apiKey)
        customModelId?.trim()?.takeIf { it.isNotBlank() }?.let { id ->
            val item = AiModelEntity(
                modelKey = "$providerId:$id", providerId = providerId, modelId = id,
                displayName = id, description = "사용자 지정 모델", tier = AiModelTier.BALANCED.name,
                supportsVision = true, supportsMultipleImages = true, supportsTools = false,
                supportsLongContext = true, reasoningLevel = 2, speedLevel = 2, costLevel = 2
            )
            dao.upsertAiModels(listOf(item))
        }
    }

    suspend fun testProvider(providerId: String): String {
        val provider = dao.getAiProvider(providerId) ?: return "Provider를 찾을 수 없습니다."
        val models = providerFactory.create(provider).fetchModels()
        return if (models.isEmpty()) "연결 성공" else "연결 성공 · 모델 ${models.size}개 확인"
    }

    suspend fun syncModels(providerId: String): Int {
        val provider = dao.getAiProvider(providerId) ?: return 0
        val ids = providerFactory.create(provider).fetchModels().distinct()
        val existing = dao.getEnabledAiModels().filter { it.providerId == providerId }.associateBy { it.modelId }
        val added = ids.map { id -> existing[id] ?: AiModelEntity(
            modelKey = "$providerId:$id", providerId = providerId, modelId = id, displayName = id,
            description = "Provider에서 동기화된 모델", tier = AiModelTier.BALANCED.name,
            supportsVision = false, supportsMultipleImages = false, supportsTools = false,
            supportsWebSearch = false, supportsLongContext = true, reasoningLevel = 2, speedLevel = 2, costLevel = 2
        ) }
        dao.upsertAiModels(added)
        return added.size
    }

    private suspend fun call(selection: AiSelection, request: GuideRequest, recent: String): GuideAnswer {
        val providerConfig = dao.getAiProvider(selection.model.providerId)
            ?: throw AiConfigurationException("AI Provider 설정을 찾을 수 없습니다.")
        if (!keyVault.hasKey(selection.model.providerId)) {
            throw AiConfigurationException("${providerConfig.displayName} API 설정이 필요합니다.")
        }
        val answer = providerFactory.create(providerConfig).ask(selection.model, request, recent)
        dao.markModelUsed(selection.model.modelKey)
        return answer.copy(
            usedModelKey = selection.model.modelKey,
            usedModelName = selection.model.displayName,
            autoSelectedModel = selection.automatic
        )
    }

    private fun validateExplicit(model: AiModelEntity, request: GuideRequest): AiModelEntity {
        if (request.imagePaths.isNotEmpty() && !model.supportsVision) {
            throw VisionUnsupportedException("선택한 AI 모델은 이미지 분석을 지원하지 않습니다.")
        }
        if (request.imagePaths.size > 1 && !model.supportsMultipleImages) {
            throw VisionUnsupportedException("선택한 AI 모델은 여러 이미지 분석을 지원하지 않습니다.")
        }
        return model
    }

    private fun selectAutomatic(all: List<AiModelEntity>, request: GuideRequest, mode: String, requested: String): AiSelection {
        val model = automaticCandidates(all, request, mode).firstOrNull()
            ?: throw AiConfigurationException("조건에 맞는 AI 모델의 API 설정이 필요합니다.")
        return AiSelection(model, true, requested)
    }

    private fun automaticCandidates(all: List<AiModelEntity>, request: GuideRequest, mode: String): List<AiModelEntity> {
        val needsWeb = request.forceWebSearch || listOf("최신", "패치", "업데이트", "트로피", "버그").any(request.question.lowercase()::contains)
        val desiredTier = AiRoutingPolicy.desiredTier(request, mode)
        return all.asSequence()
            .filter { AiRoutingPolicy.supports(it, request, needsWeb) }
            .filter { keyVault.hasKey(it.providerId) }
            .sortedWith(compareBy<AiModelEntity>(
                { abs(it.tierValue().ordinal - desiredTier.ordinal) },
                { if (mode == AiUsageMode.QUALITY.name) -it.reasoningLevel else it.costLevel },
                { -it.speedLevel }
            ))
            .toList()
    }
}
