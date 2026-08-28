package com.aigameguide.app.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aigameguide.app.GuideApplication
import com.aigameguide.app.data.db.GameEntity
import com.aigameguide.app.data.db.GuideQuestionEntity
import com.aigameguide.app.data.model.GuideRequest
import com.aigameguide.app.data.model.PlayStyle
import com.aigameguide.app.data.model.SpoilerLevel
import com.aigameguide.app.data.repository.GuideRepository
import com.aigameguide.app.data.repository.ImageStore
import com.aigameguide.app.data.security.AiSettings
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

data class ComposerState(
    val imagePaths: List<String> = emptyList(),
    val isSending: Boolean = false,
    val error: String? = null,
    val webSearch: Boolean = false
)

class GuideViewModel(
    private val repository: GuideRepository,
    private val imageStore: ImageStore,
    private val keyVault: ApiKeyVault,
    private val settings: AiSettings
) : ViewModel() {
    val games = repository.games.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val selectedId = MutableStateFlow<Long?>(null)
    val selectedGame: StateFlow<GameEntity?> = combine(games, selectedId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val messages: StateFlow<List<GuideQuestionEntity>> = selectedGame.flatMapLatest { game ->
        if (game == null) flowOf(emptyList()) else repository.messages(game.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _composer = MutableStateFlow(ComposerState())
    val composer = _composer.asStateFlow()
    val hasApiKey: Boolean get() = keyVault.hasKey()
    val currentModel: String get() = settings.model

    init {
        viewModelScope.launch {
            combine(games, selectedId) { list, id -> id == null && list.isNotEmpty() }.collect { shouldSelect ->
                if (shouldSelect) selectedId.value = games.value.firstOrNull()?.id
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
        val remaining = 5 - _composer.value.imagePaths.size
        if (remaining <= 0) return@launch
        val paths = imageStore.importUris(uris.take(remaining))
        _composer.value = _composer.value.copy(imagePaths = (_composer.value.imagePaths + paths).take(5), error = null)
    }

    fun createCameraTarget(): Pair<Uri, String> = imageStore.newCameraTarget()
    fun acceptCameraPath(path: String) {
        if (_composer.value.imagePaths.size < 5) {
            _composer.value = _composer.value.copy(imagePaths = _composer.value.imagePaths + path)
        }
    }
    fun removeImage(path: String) {
        _composer.value = _composer.value.copy(imagePaths = _composer.value.imagePaths - path)
    }
    fun setWebSearch(enabled: Boolean) { _composer.value = _composer.value.copy(webSearch = enabled) }
    fun clearError() { _composer.value = _composer.value.copy(error = null) }

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
                    )
                )
            }.onSuccess {
                _composer.value = ComposerState()
            }.onFailure { e ->
                _composer.value = _composer.value.copy(isSending = false, error = e.message ?: "요청에 실패했습니다.")
            }
        }
    }

    fun saveAiSettings(apiKey: String, model: String) {
        if (apiKey.isNotBlank()) keyVault.save(apiKey)
        settings.model = model.ifBlank { "gpt-5.6" }
        _composer.value = _composer.value.copy(error = null)
    }
}

class GuideViewModelFactory(private val app: GuideApplication) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        GuideViewModel(app.repository, app.imageStore, app.keyVault, app.aiSettings) as T
}
