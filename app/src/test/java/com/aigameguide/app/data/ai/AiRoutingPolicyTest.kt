package com.aigameguide.app.data.ai

import com.aigameguide.app.data.db.AiModelEntity
import com.aigameguide.app.data.model.GuideRequest
import com.aigameguide.app.data.model.SpoilerLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRoutingPolicyTest {
    @Test
    fun fiveImagesSelectHighTier() {
        val request = request(images = List(5) { "image-$it.jpg" }, question = "장면을 종합해 진행도를 알려줘")
        assertEquals(AiModelTier.HIGH, AiRoutingPolicy.desiredTier(request, AiUsageMode.BALANCED.name))
    }

    @Test
    fun imageRejectsTextOnlyModel() {
        val textOnly = model(vision = false, multi = false)
        assertFalse(AiRoutingPolicy.supports(textOnly, request(images = listOf("screen.jpg")), false))
    }

    @Test
    fun multiImageRequiresCapability() {
        val singleVision = model(vision = true, multi = false)
        val multiVision = model(vision = true, multi = true)
        val request = request(images = listOf("1.jpg", "2.jpg"))
        assertFalse(AiRoutingPolicy.supports(singleVision, request, false))
        assertTrue(AiRoutingPolicy.supports(multiVision, request, false))
    }

    @Test
    fun simpleQuestionUsesFastTier() {
        assertEquals(AiModelTier.FAST, AiRoutingPolicy.desiredTier(request(question = "이 아이템 어디 있어?"), AiUsageMode.BALANCED.name))
    }

    @Test
    fun catalogKeysAndProvidersAreConsistent() {
        val providers = AiCatalog.providers.map { it.providerId }.toSet()
        assertEquals(AiCatalog.models.size, AiCatalog.models.map { it.modelKey }.toSet().size)
        assertTrue(AiCatalog.models.all { it.providerId in providers })
    }

    private fun request(images: List<String> = emptyList(), question: String = "어디 가야 돼?") = GuideRequest(
        gameName = "테스트 게임", platform = "PC", chapter = "1", region = "시작 지역",
        mainQuest = "첫 퀘스트", progressPercent = 10, playHours = 2f, playStyle = "균형",
        spoilerLevel = SpoilerLevel.NONE, memory = "", question = question, imagePaths = images,
        hintStage = 0, forceWebSearch = false
    )

    private fun model(vision: Boolean, multi: Boolean) = AiModelEntity(
        modelKey = "test:model", providerId = "test", modelId = "model", displayName = "테스트",
        description = "", tier = AiModelTier.BALANCED.name, supportsVision = vision,
        supportsMultipleImages = multi
    )
}

