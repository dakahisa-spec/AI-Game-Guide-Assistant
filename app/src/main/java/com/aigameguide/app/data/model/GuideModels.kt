package com.aigameguide.app.data.model

enum class Platform(val label: String) {
    PS5("PS5"), PS4("PS4"), SWITCH("Nintendo Switch"), SWITCH2("Switch 2"),
    PC("PC"), XBOX("Xbox"), OTHER("기타")
}

enum class SpoilerLevel(val label: String, val promptRule: String) {
    NONE("스포일러 없음", "정답, 보스 정체, 다음 지역명과 스토리를 숨기고 간접 힌트만 제공"),
    LIGHT("약간", "현재 막힌 지점의 직접 해결법만 말하고 이후 스토리는 숨김"),
    FULL("전체 허용", "요청과 관련된 공략을 제한 없이 제공")
}

enum class PlayStyle(val label: String) {
    MAIN("메인 스토리 위주"), BALANCED("적당히 서브퀘스트"),
    MOST("대부분의 콘텐츠"), COMPLETION("100% 목표")
}

enum class MessageRole { USER, ASSISTANT }

data class GuideSource(val title: String, val url: String)

data class GuideAnswer(
    val text: String,
    val sources: List<GuideSource>,
    val usedWeb: Boolean,
    val progressUpdate: ProgressUpdate? = null,
    val usedModelKey: String = "",
    val usedModelName: String = "",
    val autoSelectedModel: Boolean = false
)

data class ProgressUpdate(
    val chapter: String?,
    val region: String?,
    val mainQuest: String?,
    val progressPercent: Int?,
    val memoryNote: String?,
    val confidence: Double
)

data class GuideRequest(
    val gameName: String,
    val platform: String,
    val chapter: String,
    val region: String,
    val mainQuest: String,
    val progressPercent: Int,
    val playHours: Float,
    val playStyle: String,
    val spoilerLevel: SpoilerLevel,
    val memory: String,
    val question: String,
    val imagePaths: List<String>,
    val hintStage: Int,
    val forceWebSearch: Boolean
)
