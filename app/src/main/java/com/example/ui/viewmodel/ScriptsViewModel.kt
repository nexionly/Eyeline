package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.ScriptEntity
import com.example.data.preferences.PrompterSettings
import com.example.data.preferences.RimLightTone
import com.example.data.preferences.StudioPreferencesRepository
import com.example.data.repository.ScriptsRepository
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorUiState(
    val scriptId: Long = 0L,
    val title: String = "",
    val content: String = "",
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val isLoaded: Boolean = false,
)

class ScriptsViewModel(
    private val repository: ScriptsRepository,
    private val preferences: StudioPreferencesRepository,
) : ViewModel() {

    val scripts: StateFlow<List<ScriptEntity>> = repository.allScripts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList(),
    )

    val settings: StateFlow<PrompterSettings> = preferences.settingsState

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _editorState = MutableStateFlow(EditorUiState())
    val editorState: StateFlow<EditorUiState> = _editorState.asStateFlow()

    private var autoSaveJob: Job? = null

    init {
        viewModelScope.launch {
            val currentPrefs = preferences.settingsFlow.first()
            if (!currentPrefs.hasSeededStarter) {
                repository.seedStarterScriptIfNeeded {
                    preferences.markStarterSeeded()
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun loadScriptForEditor(scriptId: Long) {
        autoSaveJob?.cancel()
        if (scriptId <= 0L) {
            _editorState.value = EditorUiState(
                scriptId = 0L,
                title = "",
                content = "",
                isSaving = false,
                hasUnsavedChanges = false,
                isLoaded = true,
            )
            return
        }
        viewModelScope.launch {
            val loaded = repository.getScript(scriptId)
            _editorState.value = if (loaded != null) {
                EditorUiState(
                    scriptId = loaded.id,
                    title = loaded.title,
                    content = loaded.content,
                    isSaving = false,
                    hasUnsavedChanges = false,
                    isLoaded = true,
                )
            } else {
                EditorUiState(isLoaded = true)
            }
        }
    }

    fun onEditorTitleChanged(newTitle: String) {
        _editorState.update { it.copy(title = newTitle, hasUnsavedChanges = true) }
        scheduleAutoSave()
    }

    fun onEditorContentChanged(newContent: String) {
        _editorState.update { it.copy(content = newContent, hasUnsavedChanges = true) }
        scheduleAutoSave()
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(380L)
            persistCurrentDraft()
        }
    }

    private suspend fun persistCurrentDraft(): Long {
        val state = _editorState.value
        if (state.title.isBlank() && state.content.isBlank() && state.scriptId == 0L) {
            _editorState.update { it.copy(isSaving = false, hasUnsavedChanges = false) }
            return 0L
        }
        _editorState.update { it.copy(isSaving = true) }
        val resolvedTitle = state.title.trim().ifEmpty {
            state.content.trim().lineSequence().firstOrNull()?.take(36)?.trim().orEmpty()
        }
        val savedId = repository.saveScript(
            ScriptEntity(
                id = state.scriptId,
                title = resolvedTitle,
                content = state.content,
            )
        )
        _editorState.update {
            it.copy(
                scriptId = savedId,
                isSaving = false,
                hasUnsavedChanges = false,
            )
        }
        return savedId
    }

    fun saveDraftAndLaunchStudio(onReady: (Long) -> Unit) {
        autoSaveJob?.cancel()
        viewModelScope.launch {
            val savedId = persistCurrentDraft()
            if (savedId > 0L) {
                onReady(savedId)
            }
        }
    }

    fun flushDraftOnExit() {
        autoSaveJob?.cancel()
        viewModelScope.launch {
            persistCurrentDraft()
        }
    }

    fun duplicateScript(script: ScriptEntity) {
        viewModelScope.launch {
            repository.duplicateScript(script)
        }
    }

    fun deleteScript(scriptId: Long) {
        viewModelScope.launch {
            repository.deleteScript(scriptId)
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setRimLightEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setRimLightEnabled(enabled) }
    }

    fun setRimLightTone(tone: RimLightTone) {
        viewModelScope.launch { preferences.setRimLightTone(tone) }
    }

    fun updateRimLightIntensity(intensity: Float) {
        viewModelScope.launch { preferences.updateRimLightIntensity(intensity) }
    }

    fun updateScrollSpeed(speedLevel: Float) {
        viewModelScope.launch { preferences.updateScrollSpeed(speedLevel) }
    }

    fun setVolumeDownShutterEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setVolumeDownShutterEnabled(enabled) }
    }

    fun setCountdownSoundsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setCountdownSoundsEnabled(enabled) }
    }

    class Factory(
        private val repository: ScriptsRepository,
        private val preferences: StudioPreferencesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScriptsViewModel(repository, preferences) as T
        }
    }
}
