package com.aigameguide.app

import android.app.Application
import com.aigameguide.app.data.db.GuideDatabase
import com.aigameguide.app.data.ai.AiGateway
import com.aigameguide.app.data.ai.AiProviderFactory
import com.aigameguide.app.data.repository.GuideRepository
import com.aigameguide.app.data.repository.ImageStore
import com.aigameguide.app.data.security.ApiKeyVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GuideApplication : Application() {
    lateinit var repository: GuideRepository
    lateinit var imageStore: ImageStore
    lateinit var keyVault: ApiKeyVault
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        keyVault = ApiKeyVault(this)
        imageStore = ImageStore(this)
        val dao = GuideDatabase.get(this).guideDao()
        val gateway = AiGateway(dao, keyVault, AiProviderFactory(keyVault))
        repository = GuideRepository(dao, gateway)
        appScope.launch { repository.ensureAiCatalog() }
    }
}
