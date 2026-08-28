package com.aigameguide.app.data.ai

import com.aigameguide.app.data.db.AiModelEntity
import com.aigameguide.app.data.db.AiProviderEntity

const val AUTO_MODEL_KEY = "AUTO"
const val LOCAL_MODEL_KEY = "LOCAL"

enum class AiProviderType { OPENAI, GEMINI, CLAUDE, OPENAI_COMPATIBLE }
enum class AiModelTier(val label: String, val badge: String) {
    FAST("빠른 모델", "⚡ 빠름"),
    BALANCED("균형 모델", "⚖ 균형"),
    HIGH("고성능 모델", "🧠 고성능")
}
enum class AiUsageMode(val label: String) {
    SAVER("절약"), BALANCED("균형"), QUALITY("최고 품질")
}

object AiCatalog {
    val providers = listOf(
        AiProviderEntity("openai", "OpenAI", AiProviderType.OPENAI.name, "https://api.openai.com", true),
        AiProviderEntity("gemini", "Google Gemini", AiProviderType.GEMINI.name, "https://generativelanguage.googleapis.com", true),
        AiProviderEntity("claude", "Anthropic Claude", AiProviderType.CLAUDE.name, "https://api.anthropic.com", false),
        AiProviderEntity("compatible", "OpenAI Compatible", AiProviderType.OPENAI_COMPATIBLE.name, "", true)
    )

    // Provider별 기본 목록은 이 한 곳에서만 관리한다. 동기화 지원 Provider는 API 결과로 갱신할 수 있다.
    val models = listOf(
        model("openai", "gpt-5.6-nano", "GPT 빠른 모델", "빠른 공략·저장 진행도 확인", AiModelTier.FAST, true, true, true, 1, 3, 1),
        model("openai", "gpt-5.6-mini", "GPT 균형 모델", "일반 공략·보스·퀘스트 분석", AiModelTier.BALANCED, true, true, true, 2, 2, 2),
        model("openai", "gpt-5.6", "GPT 고성능 모델", "복잡한 추론·다중 이미지·웹 공략 비교", AiModelTier.HIGH, true, true, true, 3, 1, 3),
        model("gemini", "gemini-2.5-flash", "Gemini 빠른 모델", "빠른 멀티모달 공략", AiModelTier.FAST, true, true, true, 1, 3, 1),
        model("gemini", "gemini-2.5-pro", "Gemini 고성능 모델", "긴 문맥·이미지 종합 분석", AiModelTier.HIGH, true, true, true, 3, 1, 3),
        model("claude", "claude-haiku-4-5", "Claude 빠른 모델", "간단한 질문과 빠른 요약", AiModelTier.FAST, true, true, false, 1, 3, 1),
        model("claude", "claude-sonnet-4-5", "Claude 균형 모델", "공략 추론·선택지 비교", AiModelTier.BALANCED, true, true, false, 2, 2, 2),
        model("claude", "claude-opus-4-1", "Claude 고성능 모델", "복잡한 퍼즐·고난도 분석", AiModelTier.HIGH, true, true, false, 3, 1, 3)
    )

    private fun model(
        provider: String, id: String, name: String, description: String, tier: AiModelTier,
        vision: Boolean, multiImage: Boolean, web: Boolean,
        reasoning: Int, speed: Int, cost: Int
    ) = AiModelEntity(
        modelKey = "$provider:$id",
        providerId = provider,
        modelId = id,
        displayName = name,
        description = description,
        tier = tier.name,
        supportsVision = vision,
        supportsMultipleImages = multiImage,
        supportsTools = web,
        supportsWebSearch = web,
        supportsLongContext = tier != AiModelTier.FAST,
        reasoningLevel = reasoning,
        speedLevel = speed,
        costLevel = cost
    )
}

fun AiModelEntity.tierValue(): AiModelTier = runCatching { AiModelTier.valueOf(tier) }.getOrDefault(AiModelTier.BALANCED)

