package com.aigameguide.app

import android.app.Application
import com.aigameguide.app.data.db.GuideDatabase
import com.aigameguide.app.data.network.OpenAiGuideService
import com.aigameguide.app.data.repository.GuideRepository
import com.aigameguide.app.data.repository.ImageStore
import com.aigameguide.app.data.security.AiSettings
import com.aigameguide.app.data.security.ApiKeyVault

class GuideApplication : Application() {
    lateinit var repository: GuideRepository
    lateinit var imageStore: ImageStore
    lateinit var keyVault: ApiKeyVault
    lateinit var aiSettings: AiSettings

    override fun onCreate() {
        super.onCreate()
        keyVault = ApiKeyVault(this)
        aiSettings = AiSettings(this)
        imageStore = ImageStore(this)
        val ai = OpenAiGuideService(keyVault::load) { aiSettings.model }
        repository = GuideRepository(GuideDatabase.get(this).guideDao(), ai)
    }
}
