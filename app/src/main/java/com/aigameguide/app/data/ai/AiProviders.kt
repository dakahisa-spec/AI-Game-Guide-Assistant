package com.aigameguide.app.data.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.aigameguide.app.data.db.AiModelEntity
import com.aigameguide.app.data.db.AiProviderEntity
import com.aigameguide.app.data.model.GuideAnswer
import com.aigameguide.app.data.model.GuideRequest
import com.aigameguide.app.data.model.GuideSource
import com.aigameguide.app.data.model.ProgressUpdate
import com.aigameguide.app.data.security.ApiKeyVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

interface AiProvider {
    val providerId: String
    suspend fun ask(model: AiModelEntity, request: GuideRequest, recentContext: String): GuideAnswer
    suspend fun fetchModels(): List<String>
}

class AiProviderFactory(private val keyVault: ApiKeyVault) {
    fun create(provider: AiProviderEntity): AiProvider = when (AiProviderType.valueOf(provider.providerType)) {
        AiProviderType.OPENAI -> OpenAiProvider(provider, keyVault)
        AiProviderType.GEMINI -> GeminiProvider(provider, keyVault)
        AiProviderType.CLAUDE -> ClaudeProvider(provider, keyVault)
        AiProviderType.OPENAI_COMPATIBLE -> OpenAiCompatibleProvider(provider, keyVault)
    }
}

internal abstract class HttpAiProvider(
    protected val config: AiProviderEntity,
    protected val keyVault: ApiKeyVault
) : AiProvider {
    override val providerId: String get() = config.providerId
    protected val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    protected fun apiKey(): String = keyVault.load(providerId)?.takeIf { it.isNotBlank() }
        ?: throw AiConfigurationException("${config.displayName} API 설정이 필요합니다.")

    protected fun baseUrl(): String = config.baseUrl.trim().trimEnd('/').takeIf { it.startsWith("https://") }
        ?: throw AiConfigurationException("${config.displayName} Base URL을 확인해 주세요.")

    protected fun execute(request: Request): JSONObject = client.newCall(request).execute().use { response ->
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val detail = runCatching {
                val json = JSONObject(raw)
                json.optJSONObject("error")?.optString("message")
                    ?: json.optString("message")
            }.getOrNull()?.takeIf { it.isNotBlank() }
            val message = when (response.code) {
                401, 403 -> "인증 실패: API Key를 확인해 주세요."
                404 -> "모델을 찾을 수 없습니다. 모델 목록을 새로고침해 주세요."
                429 -> "요청 한도를 초과했습니다. 잠시 후 다른 모델을 사용해 주세요."
                else -> detail ?: "AI 서버 오류 (${response.code})"
            }
            throw AiProviderException(message)
        }
        runCatching { JSONObject(raw) }.getOrElse { throw AiProviderException("AI 응답 형식을 읽지 못했습니다.") }
    }
}

internal class OpenAiProvider(config: AiProviderEntity, keyVault: ApiKeyVault) : HttpAiProvider(config, keyVault) {
    override suspend fun ask(model: AiModelEntity, request: GuideRequest, recentContext: String): GuideAnswer = withContext(Dispatchers.IO) {
        val useWeb = request.forceWebSearch || AiPayload.shouldSearchWeb(request.question)
        val content = JSONArray().put(JSONObject().put("type", "input_text").put("text", AiPayload.prompt(request, recentContext)))
        AiPayload.images(request.imagePaths).forEach { data ->
            content.put(JSONObject().put("type", "input_image").put("image_url", "data:image/jpeg;base64,$data").put("detail", "auto"))
        }
        val body = JSONObject()
            .put("model", model.modelId)
            .put("input", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        if (useWeb && model.supportsWebSearch) {
            body.put("tools", JSONArray().put(JSONObject().put("type", "web_search").put("search_context_size", "medium")))
                .put("include", JSONArray().put("web_search_call.action.sources"))
        }
        val json = execute(Request.Builder().url("${baseUrl()}/v1/responses")
            .header("Authorization", "Bearer ${apiKey()}")
            .post(body.toString().jsonBody()).build())
        AiPayload.parseOpenAiResponse(json, useWeb && model.supportsWebSearch)
    }

    override suspend fun fetchModels(): List<String> = withContext(Dispatchers.IO) {
        val json = execute(Request.Builder().url("${baseUrl()}/v1/models").header("Authorization", "Bearer ${apiKey()}").get().build())
        json.optJSONArray("data").stringIds("id")
    }
}

internal class OpenAiCompatibleProvider(config: AiProviderEntity, keyVault: ApiKeyVault) : HttpAiProvider(config, keyVault) {
    override suspend fun ask(model: AiModelEntity, request: GuideRequest, recentContext: String): GuideAnswer = withContext(Dispatchers.IO) {
        val content = JSONArray().put(JSONObject().put("type", "text").put("text", AiPayload.prompt(request, recentContext)))
        AiPayload.images(request.imagePaths).forEach { data ->
            content.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$data")))
        }
        val body = JSONObject().put("model", model.modelId).put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val json = execute(Request.Builder().url("${baseUrl()}/v1/chat/completions")
            .header("Authorization", "Bearer ${apiKey()}").post(body.toString().jsonBody()).build())
        val text = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
        AiPayload.answer(text, false)
    }

    override suspend fun fetchModels(): List<String> = withContext(Dispatchers.IO) {
        execute(Request.Builder().url("${baseUrl()}/v1/models").header("Authorization", "Bearer ${apiKey()}").get().build())
            .optJSONArray("data").stringIds("id")
    }
}

internal class GeminiProvider(config: AiProviderEntity, keyVault: ApiKeyVault) : HttpAiProvider(config, keyVault) {
    override suspend fun ask(model: AiModelEntity, request: GuideRequest, recentContext: String): GuideAnswer = withContext(Dispatchers.IO) {
        val parts = JSONArray().put(JSONObject().put("text", AiPayload.prompt(request, recentContext)))
        AiPayload.images(request.imagePaths).forEach { data ->
            parts.put(JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", data)))
        }
        val useWeb = (request.forceWebSearch || AiPayload.shouldSearchWeb(request.question)) && model.supportsWebSearch
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
        if (useWeb) body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        val json = execute(Request.Builder()
            .url("${baseUrl()}/v1beta/models/${model.modelId}:generateContent?key=${apiKey()}")
            .post(body.toString().jsonBody()).build())
        val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
        val out = candidate?.optJSONObject("content")?.optJSONArray("parts")
        val text = buildString { if (out != null) for (i in 0 until out.length()) append(out.optJSONObject(i)?.optString("text").orEmpty()) }
        val sources = mutableListOf<GuideSource>()
        val chunks = candidate?.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks")
        if (chunks != null) for (i in 0 until chunks.length()) {
            val web = chunks.optJSONObject(i)?.optJSONObject("web") ?: continue
            val url = web.optString("uri")
            if (url.isNotBlank()) sources += GuideSource(web.optString("title", url), url)
        }
        AiPayload.answer(text, useWeb, sources)
    }

    override suspend fun fetchModels(): List<String> = withContext(Dispatchers.IO) {
        val json = execute(Request.Builder().url("${baseUrl()}/v1beta/models?key=${apiKey()}").get().build())
        json.optJSONArray("models").stringIds("name").map { it.substringAfter("models/") }
    }
}

internal class ClaudeProvider(config: AiProviderEntity, keyVault: ApiKeyVault) : HttpAiProvider(config, keyVault) {
    override suspend fun ask(model: AiModelEntity, request: GuideRequest, recentContext: String): GuideAnswer = withContext(Dispatchers.IO) {
        val content = JSONArray().put(JSONObject().put("type", "text").put("text", AiPayload.prompt(request, recentContext)))
        AiPayload.images(request.imagePaths).forEach { data ->
            content.put(JSONObject().put("type", "image").put("source", JSONObject()
                .put("type", "base64").put("media_type", "image/jpeg").put("data", data)))
        }
        val body = JSONObject().put("model", model.modelId).put("max_tokens", 4096)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val json = execute(Request.Builder().url("${baseUrl()}/v1/messages")
            .header("x-api-key", apiKey()).header("anthropic-version", "2023-06-01")
            .post(body.toString().jsonBody()).build())
        val blocks = json.optJSONArray("content")
        val text = buildString { if (blocks != null) for (i in 0 until blocks.length()) append(blocks.optJSONObject(i)?.optString("text").orEmpty()) }
        AiPayload.answer(text, false)
    }

    override suspend fun fetchModels(): List<String> = withContext(Dispatchers.IO) {
        val json = execute(Request.Builder().url("${baseUrl()}/v1/models")
            .header("x-api-key", apiKey()).header("anthropic-version", "2023-06-01").get().build())
        json.optJSONArray("data").stringIds("id")
    }
}

class AiConfigurationException(message: String) : IllegalStateException(message)
class AiProviderException(message: String) : IllegalStateException(message)
class VisionUnsupportedException(message: String) : IllegalArgumentException(message)

private object AiPayload {
    fun prompt(r: GuideRequest, recent: String): String = """
        당신은 한국어 게임 공략 비서다. 최신 정보가 필요하면 공식 정보, 최근 전문 공략, 교차 확인된 자료 순으로 검증한다.

        [현재 게임]
        게임명: ${r.gameName} / 플랫폼: ${r.platform}
        챕터: ${r.chapter.ifBlank { "미입력" }} / 지역: ${r.region.ifBlank { "미입력" }}
        메인 퀘스트: ${r.mainQuest.ifBlank { "미입력" }} / 저장 진행률: ${r.progressPercent}%
        플레이 시간: ${r.playHours}시간 / 스타일: ${r.playStyle}
        게임별 기억: ${r.memory.ifBlank { "없음" }}
        최근 대화: ${recent.ifBlank { "없음" }}

        [답변 규칙]
        스포일러 수준: ${r.spoilerLevel.label} — ${r.spoilerLevel.promptRule}
        힌트 단계: ${r.hintStage} (1~3은 해당 단계만, 4는 정답 허용)
        첨부된 모든 이미지를 함께 분석하고 불확실한 식별은 추정이라고 표시한다.
        첫 문단은 2~3문장의 '지금 할 것'으로 짧게 쓰고, 필요할 때만 방법·주의·진행도·출처를 정리한다.
        패치·플랫폼 차이와 놓치기 쉬운 요소를 확인하되 스포일러 설정을 지킨다.

        사용자 질문: ${r.question}

        답변 맨 끝에는 화면에 표시하지 않을 진행 갱신 후보를 아래 형식으로 한 줄 추가한다.
        <progress_update>{"chapter":null,"region":null,"mainQuest":null,"progressPercent":null,"memoryNote":null,"confidence":0.0}</progress_update>
        질문에서 확실히 확인된 사실만 채우고 confidence 0.85 이상일 때만 앱이 자동 저장한다.
    """.trimIndent()

    fun images(paths: List<String>): List<String> = paths.take(5).mapNotNull(::encodeImage)

    fun shouldSearchWeb(question: String): Boolean {
        val q = question.lowercase()
        return listOf("최신", "패치", "업데이트", "버그", "오류", "dlc", "요즘", "밸런스", "빌드", "무기", "공략", "트로피", "놓치").any(q::contains)
    }

    fun parseOpenAiResponse(json: JSONObject, usedWeb: Boolean): GuideAnswer {
        val textParts = mutableListOf<String>()
        val sources = linkedMapOf<String, GuideSource>()
        val output = json.optJSONArray("output") ?: JSONArray()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            val content = item.optJSONArray("content")
            if (content != null) for (j in 0 until content.length()) {
                val block = content.optJSONObject(j) ?: continue
                if (block.optString("type") == "output_text") {
                    textParts += block.optString("text")
                    val annotations = block.optJSONArray("annotations")
                    if (annotations != null) for (k in 0 until annotations.length()) {
                        val a = annotations.optJSONObject(k) ?: continue
                        val url = a.optString("url")
                        if (url.isNotBlank()) sources[url] = GuideSource(a.optString("title", url), url)
                    }
                }
            }
            val actionSources = item.optJSONObject("action")?.optJSONArray("sources")
            if (actionSources != null) for (j in 0 until actionSources.length()) {
                val source = actionSources.optJSONObject(j) ?: continue
                val url = source.optString("url")
                if (url.isNotBlank()) sources[url] = GuideSource(source.optString("title", url), url)
            }
        }
        return answer(textParts.joinToString("\n").ifBlank { json.optString("output_text") }, usedWeb, sources.values.toList())
    }

    fun answer(raw: String, usedWeb: Boolean, sources: List<GuideSource> = emptyList()): GuideAnswer {
        val progress = parseProgress(raw)
        val clean = raw.replace(Regex("<progress_update>.*?</progress_update>", RegexOption.DOT_MATCHES_ALL), "").trim()
        return GuideAnswer(clean.ifBlank { "답변을 읽지 못했습니다. 다시 질문해 주세요." }, sources, usedWeb, progress)
    }

    private fun parseProgress(text: String): ProgressUpdate? = runCatching {
        val match = Regex("<progress_update>(.*?)</progress_update>", RegexOption.DOT_MATCHES_ALL).find(text) ?: return null
        val j = JSONObject(match.groupValues[1])
        ProgressUpdate(
            j.nullableString("chapter"), j.nullableString("region"), j.nullableString("mainQuest"),
            if (j.isNull("progressPercent")) null else j.optInt("progressPercent"),
            j.nullableString("memoryNote"), j.optDouble("confidence", 0.0)
        )
    }.getOrNull()

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun encodeImage(path: String): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 1800 || bounds.outHeight / sample > 1800) sample *= 2
        val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)
        bitmap.recycle()
        Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()
}

private fun String.jsonBody() = toRequestBody("application/json; charset=utf-8".toMediaType())

private fun JSONArray?.stringIds(key: String): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) optJSONObject(i)?.optString(key)?.takeIf { it.isNotBlank() }?.let(::add)
    }
}
