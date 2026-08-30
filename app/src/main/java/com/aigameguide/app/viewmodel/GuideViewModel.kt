package com.aigameguide.app.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.aigameguide.app.GuideApplication
import com.aigameguide.app.data.db.GameEntity
import com.aigameguide.app.data.db.AiModelEntity
import com.aigameguide.app.data.db.AiProviderEntity
import com.aigameguide.app.data.db.AiSettingsEntity
import com.aigameguide.app.data.db.GuideQuestionEntity
import com.aigameguide.app.data.ai.AUTO_MODEL_KEY
import com.aigameguide.app.data.ai.AiUsageMode
import com.aigameguide.app.data.ai.VisionUnsupportedException
import com.aigameguide.app.data.model.GuideRequest
import com.aigameguide.app.data.model.PlayStyle
import com.aigameguide.app.data.model.SpoilerLevel
import com.aigameguide.app.data.repository.GuideRepository
import com.aigameguide.app.data.repository.ImageStore
import com.aigameguide.app.data.security.ApiKeyVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.Serializable
import java.io.File

data class ComposerState(
    val imagePaths: List<String> = emptyList(),
    val isSending: Boolean = false,
    val error: String? = null,
    val webSearch: Boolean = false,
    val temporaryModelKey: String? = null,
    val showVisionModelAction: Boolean = false
) : Serializable

data class AiUiState(
    val providers: List<AiProviderEntity> = emptyList(),
    val models: List<AiModelEntity> = emptyList(),
    val globalSettings: AiSettingsEntity = AiSettingsEntity(),
    val gameModelKey: String = AUTO_MODEL_KEY,
    val statusMessage: String? = null,
    val busyProviderId: String? = null
)

class GuideViewModel(
    private val repository: GuideRepository,
    private val imageStore: ImageStore,
    private val keyVault: ApiKeyVault,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val games = repository.games.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val selectedId = savedStateHandle.getMutableStateFlow<Long?>("selected_game_id", null)
    val selectedGame: StateFlow<GameEntity?> = combine(games, selectedId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val messages: StateFlow<List<GuideQuestionEntity>> = selectedGame.flatMapLatest { game ->
        if (game == null) flowOf(emptyList()) else repository.messages(game.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _composer = savedStateHandle.getMutableStateFlow("composer_state", ComposerState())
    val composer = _composer.asStateFlow()
    private val _gameModelKey = MutableStateFlow(AUTO_MODEL_KEY)
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _busyProvider = MutableStateFlow<String?>(null)
    private val aiCatalog = combine(repository.aiProviders, repository.aiModels, repository.aiSettings) {
            providers, models, appSettings -> Triple(providers, models, appSettings ?: AiSettingsEntity())
    }
    private val aiSelection = combine(_gameModelKey, _statusMessage, _busyProvider) {
            gameModel, status, busy -> Triple(gameModel, status, busy)
    }
    val aiUi: StateFlow<AiUiState> = combine(aiCatalog, aiSelection) { catalog, selection ->
        AiUiState(catalog.first, catalog.second, catalog.third, selection.first, selection.second, selection.third)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiUiState())

    init {
        viewModelScope.launch {
            combine(games, selectedId) { list, id -> id == null && list.isNotEmpty() }.collect { shouldSelect ->
                if (shouldSelect) selectedId.value = games.value.firstOrNull()?.id
            }
        }
        viewModelScope.launch {
            selectedGame.collect { game ->
                _gameModelKey.value = game?.let { repository.gameModelKey(it.id) } ?: AUTO_MODEL_KEY
                _composer.value = _composer.value.copy(temporaryModelKey = null)
            }
        }
    }

    fun selectGame(id: Long) { selectedId.value = id }

    fun addGame(game: GameEntity) = viewModelScope.launch {
        val id = repository.addGame(game)
        selectedId.value = id
    }

    fun updateGame(game: GameEntity) = viewModelScope.launch { repository.updateGame(game) }
    fun deleteGame(id: Long) = viewModelScope.launch {
        repository.deleteGame(id)
        if (selectedId.value == id) selectedId.value = null
    }

    fun importImages(uris: List<Uri>) = viewModelScope.launch {
        if (uris.isEmpty()) return@launch
        val remaining = 5 - _composer.value.imagePaths.size
        if (remaining <= 0) return@launch
        val paths = imageStore.importUris(uris.take(remaining))
        _composer.value = if (paths.isEmpty()) {
            _composer.value.copy(error = "이미지를 가져오지 못했습니다. 갤러리 또는 파일 선택으로 다시 시도해 주세요.")
        } else {
            _composer.value.copy(imagePaths = (_composer.value.imagePaths + paths).take(5), error = null)
        }
    }

    fun createCameraTarget(): Pair<Uri, String>? = runCatching { imageStore.newCameraTarget() }
        .onFailure { reportImageError("카메라 저장 위치를 만들지 못했습니다.") }
        .getOrNull()
    fun acceptCameraPath(path: String) {
        val image = File(path)
        if (!image.exists() || image.length() == 0L) {
            reportImageError("촬영한 사진을 저장하지 못했습니다.")
        } else if (_composer.value.imagePaths.size < 5) {
            _composer.value = _composer.value.copy(imagePaths = _composer.value.imagePaths + path, error = null)
        }
    }
    fun reportImageError(message: String) {
        _composer.value = _composer.value.copy(error = message)
    }
    fun removeImage(path: String) {
        _composer.value = _composer.value.copy(imagePaths = _composer.value.imagePaths - path)
    }
    fun setWebSearch(enabled: Boolean) { _composer.value = _composer.value.copy(webSearch = enabled) }
    fun clearError() { _composer.value = _composer.value.copy(error = null, showVisionModelAction = false) }
    fun setTemporaryModel(modelKey: String) {
        _composer.value = _composer.value.copy(temporaryModelKey = modelKey, error = null, showVisionModelAction = false)
    }
    fun useAutoForCurrentQuestion() = setTemporaryModel(AUTO_MODEL_KEY)

    fun saveGameModel(modelKey: String) {
        val game = selectedGame.value ?: return
        viewModelScope.launch {
            repository.saveGameModel(game.id, modelKey)
            _gameModelKey.value = modelKey
            _composer.value = _composer.value.copy(temporaryModelKey = null)
        }
    }

    fun toggleFavorite(model: AiModelEntity) = viewModelScope.launch { repository.toggleFavorite(model) }

    fun saveGlobalAiSettings(defaultModelKey: String, usageMode: AiUsageMode) = viewModelScope.launch {
        repository.saveGlobalAiSettings(defaultModelKey, usageMode.name)
        _statusMessage.value = "AI 기본 설정을 저장했습니다."
    }

    fun saveProvider(providerId: String, baseUrl: String, apiKey: String, customModelId: String?) = viewModelScope.launch {
        runCatching { repository.saveProvider(providerId, baseUrl, apiKey, customModelId) }
            .onSuccess { _statusMessage.value = "Provider 설정을 저장했습니다." }
            .onFailure { _statusMessage.value = it.message }
    }

    fun testProvider(providerId: String) = viewModelScope.launch {
        _busyProvider.value = providerId
        runCatching { repository.testProvider(providerId) }
            .onSuccess { _statusMessage.value = it }
            .onFailure { _statusMessage.value = it.message ?: "연결 테스트에 실패했습니다." }
        _busyProvider.value = null
    }

    fun syncModels(providerId: String) = viewModelScope.launch {
        _busyProvider.value = providerId
        runCatching { repository.syncModels(providerId) }
            .onSuccess { _statusMessage.value = "모델 목록 ${it}개를 확인했습니다." }
            .onFailure { _statusMessage.value = it.message ?: "모델 목록을 가져오지 못했습니다." }
        _busyProvider.value = null
    }

    fun hasProviderKey(providerId: String): Boolean = keyVault.hasKey(providerId)
    fun maskedProviderKey(providerId: String): String? = keyVault.masked(providerId)
    fun clearAiStatus() { _statusMessage.value = null }

    fun sendQuestion(text: String, hintStage: Int = 0) {
        val game = selectedGame.value ?: return
        if (text.isBlank() || _composer.value.isSending) return
        viewModelScope.launch {
            _composer.value = _composer.value.copy(isSending = true, error = null)
            runCatching {
                val memory = repository.getMemory(game.id)
                repository.ask(
                    game,
                    GuideRequest(
                        gameName = game.name,
                        platform = game.platform,
                        chapter = game.chapter,
                        region = game.region,
                        mainQuest = game.mainQuest,
                        progressPercent = game.progressPercent,
                        playHours = game.playHours,
                        playStyle = runCatching { PlayStyle.valueOf(game.playStyle).label }.getOrDefault(game.playStyle),
                        spoilerLevel = runCatching { SpoilerLevel.valueOf(game.spoilerLevel) }.getOrDefault(SpoilerLevel.NONE),
                        memory = memory,
                        question = text,
                        imagePaths = _composer.value.imagePaths,
                        hintStage = hintStage,
                        forceWebSearch = _composer.value.webSearch
                    ),
                    temporaryModelKey = _composer.value.temporaryModelKey
                )
            }.onSuccess {
                _composer.value = ComposerState()
            }.onFailure { e ->
                _composer.value = _composer.value.copy(
                    isSending = false,
                    error = e.message ?: "요청에 실패했습니다.",
                    showVisionModelAction = e is VisionUnsupportedException
                )
            }
        }
    }
}

class GuideViewModelFactory(private val app: GuideApplication) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        GuideViewModel(app.repository, app.imageStore, app.keyVault, extras.createSavedStateHandle()) as T
}
