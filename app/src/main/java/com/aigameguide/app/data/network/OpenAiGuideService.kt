package com.aigameguide.app.data.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.aigameguide.app.data.model.GuideAnswer
import com.aigameguide.app.data.model.GuideRequest
import com.aigameguide.app.data.model.GuideSource
import com.aigameguide.app.data.model.ProgressUpdate
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

class OpenAiGuideService(
    private val apiKeyProvider: () -> String?,
    private val modelProvider: () -> String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun ask(request: GuideRequest, recentContext: String): GuideAnswer = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()?.takeIf { it.isNotBlank() }
            ?: error("API 키가 없습니다. 상단 설정에서 OpenAI API 키를 저장해 주세요.")
        val useWeb = request.forceWebSearch || request.imagePaths.isNotEmpty() || shouldSearchWeb(request.question)
        val body = createRequestJson(request, recentContext, useWeb)
        val httpRequest = Request.Builder()
            .url("https://api.openai.com/v1/responses")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(raw).getJSONObject("error").getString("message") }.getOrNull()
                error(message ?: "AI 요청 실패 (${response.code})")
            }
            parseResponse(JSONObject(raw), useWeb)
        }
    }

    private fun createRequestJson(req: GuideRequest, recent: String, useWeb: Boolean): JSONObject {
        val content = JSONArray().put(JSONObject().put("type", "input_text").put("text", buildPrompt(req, recent)))
        req.imagePaths.take(5).forEach { path ->
            encodeImage(path)?.let { data ->
                content.put(
                    JSONObject().put("type", "input_image")
                        .put("image_url", "data:image/jpeg;base64,$data")
                        .put("detail", "auto")
                )
            }
        }
        val root = JSONObject()
            .put("model", modelProvider())
            .put("input", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        if (useWeb) {
            root.put("tools", JSONArray().put(
                JSONObject().put("type", "web_search").put("search_context_size", "medium")
            ))
            root.put("include", JSONArray().put("web_search_call.action.sources"))
        }
        return root
    }

    private fun buildPrompt(r: GuideRequest, recent: String): String = """
        당신은 한국어 게임 공략 비서다. 최신 정보가 필요하면 공식 정보, 최근 전문 공략, 교차 확인된 자료 순으로 검증한다.

        [현재 게임]
        게임명: ${r.gameName}
        플랫폼: ${r.platform}
        챕터: ${r.chapter.ifBlank { "미입력" }}
        지역: ${r.region.ifBlank { "미입력" }}
        메인 퀘스트: ${r.mainQuest.ifBlank { "미입력" }}
        저장된 진행률: ${r.progressPercent}%
        플레이 시간: ${r.playHours}시간
        플레이 스타일: ${r.playStyle}
        게임별 기억: ${r.memory.ifBlank { "없음" }}
        최근 대화: ${recent.ifBlank { "없음" }}

        [답변 규칙]
        스포일러 수준: ${r.spoilerLevel.label} — ${r.spoilerLevel.promptRule}
        힌트 단계: ${r.hintStage} (1~3은 단계만큼만 힌트, 4는 정답 허용)
        첨부 이미지가 있으면 모든 이미지를 함께 보고 게임/장소/NPC/보스/퍼즐/UI를 식별하되 불확실하면 추정이라고 표시한다.
        첫 문단은 2~3문장의 '지금 할 것'으로 아주 짧게 쓴다.
        그 뒤 필요한 경우에만 '방법', '주의', '진행도·남은 시간', '출처' 제목으로 정리한다.
        놓치기 쉬운 기간 제한 요소가 현재 지점 근처에 있으면 스포일러 규칙 안에서 경고한다.
        검색 정보는 패치 날짜와 플랫폼 차이를 확인하고, 불확실하면 단정하지 않는다.

        사용자의 질문: ${r.question}

        답변 맨 끝에는 화면에 표시하지 않을 진행 갱신 후보를 정확히 아래 형식으로 한 줄 추가한다.
        <progress_update>{"chapter":null,"region":null,"mainQuest":null,"progressPercent":null,"memoryNote":null,"confidence":0.0}</progress_update>
        질문에서 확실히 확인된 사실만 채우고 confidence 0.85 이상일 때만 앱이 자동 저장한다.
    """.trimIndent()

    private fun parseResponse(json: JSONObject, usedWeb: Boolean): GuideAnswer {
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
                    collectAnnotations(block.optJSONArray("annotations"), sources)
                }
            }
            val actionSources = item.optJSONObject("action")?.optJSONArray("sources")
            collectSources(actionSources, sources)
        }
        val rawText = textParts.joinToString("\n").ifBlank { json.optString("output_text") }
        val progress = parseProgress(rawText)
        val clean = rawText.replace(Regex("<progress_update>.*?</progress_update>", RegexOption.DOT_MATCHES_ALL), "").trim()
        return GuideAnswer(clean.ifBlank { "답변을 읽지 못했습니다. 다시 질문해 주세요." }, sources.values.toList(), usedWeb, progress)
    }

    private fun collectAnnotations(items: JSONArray?, out: MutableMap<String, GuideSource>) {
        if (items == null) return
        for (i in 0 until items.length()) {
            val a = items.optJSONObject(i) ?: continue
            val url = a.optString("url")
            if (url.isNotBlank()) out[url] = GuideSource(a.optString("title", url), url)
        }
    }

    private fun collectSources(items: JSONArray?, out: MutableMap<String, GuideSource>) {
        if (items == null) return
        for (i in 0 until items.length()) {
            val s = items.optJSONObject(i) ?: continue
            val url = s.optString("url")
            if (url.isNotBlank()) out[url] = GuideSource(s.optString("title", url), url)
        }
    }

    private fun parseProgress(text: String): ProgressUpdate? = runCatching {
        val match = Regex("<progress_update>(.*?)</progress_update>", RegexOption.DOT_MATCHES_ALL).find(text) ?: return null
        val j = JSONObject(match.groupValues[1])
        ProgressUpdate(
            j.optNullableString("chapter"), j.optNullableString("region"),
            j.optNullableString("mainQuest"), if (j.isNull("progressPercent")) null else j.optInt("progressPercent"),
            j.optNullableString("memoryNote"), j.optDouble("confidence", 0.0)
        )
    }.getOrNull()

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun encodeImage(path: String): String? = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        var sample = 1
        while (options.outWidth / sample > 1800 || options.outHeight / sample > 1800) sample *= 2
        val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
        bitmap.recycle()
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    private fun shouldSearchWeb(question: String): Boolean {
        val q = question.lowercase()
        return listOf("최신", "패치", "업데이트", "버그", "오류", "dlc", "현재", "요즘", "밸런스", "빌드", "무기", "공략", "트로피", "놓치").any(q::contains)
    }
}
