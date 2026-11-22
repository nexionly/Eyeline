package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.capture.AudioWaveformExtractor
import com.example.capture.CountdownSoundPlayer
import com.example.capture.PreRollAudioMonitor
import com.example.capture.TimelineEditState
import com.example.capture.VideoCutRenderer
import com.example.capture.VideoExportMetadata
import com.example.capture.VideoExportResult
import com.example.capture.VideoGalleryExporter
import com.example.capture.VideoRenderResult
import com.example.capture.WaveformLoadState
import com.example.data.local.ScriptEntity
import com.example.data.preferences.PrompterSettings
import com.example.data.preferences.RimLightTone
import com.example.data.preferences.StudioPreferencesRepository
import com.example.data.repository.ScriptsRepository
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.log10

sealed interface SaveState {
    data object Idle : SaveState
    data object Saving : SaveState
    data class Saved(val metadata: VideoExportMetadata) : SaveState
    data class Error(val message: String) : SaveState
}

sealed interface TrimRenderState {
    data object Idle : TrimRenderState
    data class Rendering(val progress: Float = 0f) : TrimRenderState
    data class Error(val message: String) : TrimRenderState
}

sealed interface StudioPhase {
    data object Preview : StudioPhase
    data class Countdown(val secondsRemaining: Int) : StudioPhase
    data class Recording(
        val elapsedSeconds: Long = 0L,
        val isScrollPaused: Boolean = false,
        val hasStartedCapture: Boolean = false,
        val isLeadInHold: Boolean = true,
    ) : StudioPhase

    data class Review(
        val originalVideoPath: String,
        val activeVideoPath: String = originalVideoPath,
        val recordedAtMillis: Long = System.currentTimeMillis(),
        val appliedEditState: TimelineEditState? = null,
        val saveState: SaveState = SaveState.Idle,
    ) : StudioPhase {
        val isEdited: Boolean
            get() = activeVideoPath != originalVideoPath
    }

    data class Trimming(
        val originalVideoPath: String,
        val previousActiveVideoPath: String,
        val previousAppliedEditState: TimelineEditState?,
        val recordedAtMillis: Long,
        val editState: TimelineEditState,
        val waveformState: WaveformLoadState = WaveformLoadState.Loading,
        val renderState: TrimRenderState = TrimRenderState.Idle,
    ) : StudioPhase
}

sealed interface StudioCaptureCommand {
    data object StartVideoCapture : StudioCaptureCommand
    data object StopVideoCapture : StudioCaptureCommand
}

class StudioViewModel(
    private val scriptId: Long,
    private val scriptsRepository: ScriptsRepository,
    private val preferences: StudioPreferencesRepository,
) : ViewModel() {

    val script: StateFlow<ScriptEntity?> = scriptsRepository.observeScript(scriptId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = null,
    )

    val settings: StateFlow<PrompterSettings> = preferences.settingsState

    private val _phase = MutableStateFlow<StudioPhase>(StudioPhase.Preview)
    val phase: StateFlow<StudioPhase> = _phase.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _audioBars = MutableStateFlow(List(AUDIO_BAR_COUNT) { 0f })
    val audioBars: StateFlow<List<Float>> = _audioBars.asStateFlow()

    private val _isMicStreamActive = MutableStateFlow(false)
    val isMicStreamActive: StateFlow<Boolean> = _isMicStreamActive.asStateFlow()

    private val _scrollResetCounter = MutableStateFlow(0)
    val scrollResetCounter: StateFlow<Int> = _scrollResetCounter.asStateFlow()

    private val _useFrontCamera = MutableStateFlow(true)
    val useFrontCamera: StateFlow<Boolean> = _useFrontCamera.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _captureCommands = MutableSharedFlow<StudioCaptureCommand>(extraBufferCapacity = 2)
    val captureCommands: SharedFlow<StudioCaptureCommand> = _captureCommands.asSharedFlow()

    private val preRollAudioMonitor = PreRollAudioMonitor()
    private var countdownJob: Job? = null
    private var timerJob: Job? = null
    private var leadInHoldJob: Job? = null
    private var waveformJob: Job? = null
    private var renderJob: Job? = null
    private var hasAudioPermission: Boolean = false
    private val recordingBarHistory = ArrayDeque<Float>(AUDIO_BAR_COUNT)
    private val cachedWaveforms = mutableMapOf<String, WaveformLoadState>()

    fun onPermissionsUpdated(granted: Boolean) {
        hasAudioPermission = granted
        if (granted && _phase.value is StudioPhase.Preview) {
            startPreRollMicMonitor()
        } else if (!granted) {
            preRollAudioMonitor.stopImmediately()
            _audioLevel.value = 0f
            _audioBars.value = List(AUDIO_BAR_COUNT) { 0f }
            _isMicStreamActive.value = false
        }
    }

    fun startPreRollMicMonitor() {
        if (!hasAudioPermission) return
        if (_phase.value is StudioPhase.Recording ||
            _phase.value is StudioPhase.Review ||
            _phase.value is StudioPhase.Trimming
        ) return
        preRollAudioMonitor.start(viewModelScope, barCount = AUDIO_BAR_COUNT) { snapshot ->
            _audioLevel.value = snapshot.level
            _audioBars.value = snapshot.bars
            _isMicStreamActive.value = snapshot.isStreamActive
        }
    }

    fun stopPreRollMicMonitor() {
        preRollAudioMonitor.stopImmediately()
        _audioLevel.value = 0f
        _audioBars.value = List(AUDIO_BAR_COUNT) { 0f }
        _isMicStreamActive.value = false
    }

    fun onShutterPressed() {
        when (_phase.value) {
            is StudioPhase.Preview -> beginCountdown()
            is StudioPhase.Countdown -> cancelCountdown()
            is StudioPhase.Recording -> requestStopRecording()
            is StudioPhase.Review, is StudioPhase.Trimming -> Unit
        }
    }

    private var isStopRequested: Boolean = false

    private fun beginCountdown() {
        countdownJob?.cancel()
        _errorMessage.value = null
        isStopRequested = false
        countdownJob = viewModelScope.launch {
            // Release AudioRecord immediately at countdown start so Android AudioFlinger has
            // ~2.7 seconds to free the hardware mic cleanly before CameraX starts audio capture.
            preRollAudioMonitor.stopAndRelease()
            _audioLevel.value = 0f

            for (sec in 3 downTo 1) {
                _phase.value = StudioPhase.Countdown(secondsRemaining = sec)
                if (settings.value.countdownSoundsEnabled) {
                    launch { CountdownSoundPlayer.playTick(sec) }
                }
                delay(900L)
            }
            _phase.value = StudioPhase.Recording(
                elapsedSeconds = 0L,
                isScrollPaused = false,
                hasStartedCapture = false,
                isLeadInHold = true,
            )
            _captureCommands.tryEmit(StudioCaptureCommand.StartVideoCapture)
        }
    }

    fun onRecordingStarted() {
        val current = _phase.value
        if (current is StudioPhase.Recording) {
            _phase.value = current.copy(hasStartedCapture = true, isLeadInHold = true)
            if (timerJob == null) {
                startElapsedTimer()
            }
            // Hold Line 1 steady briefly as recording begins so the creator never misses their opening words
            leadInHoldJob?.cancel()
            leadInHoldJob = viewModelScope.launch {
                delay(1_150L)
                _phase.update { state ->
                    if (state is StudioPhase.Recording) state.copy(isLeadInHold = false) else state
                }
            }
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        leadInHoldJob?.cancel()
        leadInHoldJob = null
        if (_phase.value is StudioPhase.Countdown) {
            _phase.value = StudioPhase.Preview
            startPreRollMicMonitor()
        }
    }

    fun requestStopRecording() {
        if (isStopRequested || _phase.value !is StudioPhase.Recording) return
        isStopRequested = true
        timerJob?.cancel()
        timerJob = null
        leadInHoldJob?.cancel()
        leadInHoldJob = null
        _captureCommands.tryEmit(StudioCaptureCommand.StopVideoCapture)
    }

    private fun startElapsedTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var seconds = 0L
            while (true) {
                delay(1_000L)
                seconds++
                _phase.update { current ->
                    if (current is StudioPhase.Recording) {
                        current.copy(elapsedSeconds = seconds)
                    } else {
                        current
                    }
                }
            }
        }
    }

    fun toggleScrollPause() {
        _phase.update { current ->
            if (current is StudioPhase.Recording) {
                current.copy(isScrollPaused = !current.isScrollPaused)
            } else {
                current
            }
        }
    }

    fun resetScrollToTop() {
        _scrollResetCounter.update { it + 1 }
    }

    fun toggleCameraFacing() {
        if (_phase.value is StudioPhase.Preview) {
            _useFrontCamera.update { !it }
        }
    }

    fun onRecordingAudioAmplitude(amplitude: Double, isActiveStream: Boolean = true) {
        if (_phase.value !is StudioPhase.Recording) return
        _isMicStreamActive.value = isActiveStream
        val clamped = amplitude.coerceIn(0.0, 1.0)
        // CameraX returns normalized amplitude (0..1); apply studio dB curve for legible bar motion
        val normalized = if (clamped <= 0.0001) {
            0f
        } else {
            val db = 20.0 * log10(clamped)
            ((db + 48.0) / 48.0).toFloat().coerceIn(0f, 1f)
        }
        val prev = _audioLevel.value
        val smoothed = if (normalized > prev) {
            prev * 0.35f + normalized * 0.65f
        } else {
            prev * 0.72f + normalized * 0.28f
        }
        _audioLevel.value = smoothed

        // Build symmetrical 7-bar rolling motion driven directly by CameraX AudioStats stream
        if (recordingBarHistory.size >= AUDIO_BAR_COUNT) {
            recordingBarHistory.removeFirst()
        }
        recordingBarHistory.addLast(smoothed)
        val padded = List(AUDIO_BAR_COUNT) { idx ->
            val offset = AUDIO_BAR_COUNT - recordingBarHistory.size
            val raw = if (idx < offset) 0f else recordingBarHistory[idx - offset]
            val center = (AUDIO_BAR_COUNT - 1) / 2f
            val weight = 1f - (kotlin.math.abs(idx - center) / center) * 0.28f
            (raw * weight).coerceIn(0f, 1f)
        }
        _audioBars.value = padded
    }

    fun onRecordingFinalized(outputFile: File?, errorText: String?) {
        isStopRequested = false
        timerJob?.cancel()
        timerJob = null
        leadInHoldJob?.cancel()
        leadInHoldJob = null
        if (errorText != null || outputFile == null || !outputFile.exists() || outputFile.length() == 0L) {
            _errorMessage.value = errorText ?: "Recording failed. Please try again."
            _phase.value = StudioPhase.Preview
            startPreRollMicMonitor()
        } else {
            _phase.value = StudioPhase.Review(
                originalVideoPath = outputFile.absolutePath,
                activeVideoPath = outputFile.absolutePath,
                recordedAtMillis = System.currentTimeMillis(),
                appliedEditState = null,
                saveState = SaveState.Idle,
            )
        }
    }

    fun openTrimEditor() {
        val review = _phase.value as? StudioPhase.Review ?: return
        val sourceFile = File(review.originalVideoPath)
        val cachedWaveform = cachedWaveforms[review.originalVideoPath]
        val initialEdit = review.appliedEditState ?: TimelineEditState.initial(durationMs = 1_000L)

        _phase.value = StudioPhase.Trimming(
            originalVideoPath = review.originalVideoPath,
            previousActiveVideoPath = review.activeVideoPath,
            previousAppliedEditState = review.appliedEditState,
            recordedAtMillis = review.recordedAtMillis,
            editState = initialEdit,
            waveformState = cachedWaveform ?: WaveformLoadState.Loading,
            renderState = TrimRenderState.Idle,
        )

        waveformJob?.cancel()
        waveformJob = viewModelScope.launch {
            val probedDuration = AudioWaveformExtractor.extractDurationMs(sourceFile)
            if (probedDuration > 0L) {
                onTrimDurationResolved(probedDuration)
            }
            if (cachedWaveform == null) {
                val extracted = AudioWaveformExtractor.extract(sourceFile)
                cachedWaveforms[review.originalVideoPath] = extracted
                _phase.update { current ->
                    if (current is StudioPhase.Trimming &&
                        current.originalVideoPath == review.originalVideoPath
                    ) {
                        current.copy(waveformState = extracted)
                    } else {
                        current
                    }
                }
            }
        }
    }

    fun onTrimDurationResolved(durationMs: Long) {
        if (durationMs <= 0L) return
        _phase.update { current ->
            if (current is StudioPhase.Trimming) {
                val existing = current.editState
                if (existing.durationMs == durationMs) {
                    current
                } else if (!existing.hasEdits) {
                    current.copy(editState = TimelineEditState.initial(durationMs))
                } else {
                    current
                }
            } else {
                current
            }
        }
    }

    fun onTrimDragStart() {
        updateTrimEditState { it.recordUndoCheckpoint() }
    }

    fun onTrimLeadingChanged(targetMs: Long) {
        updateTrimEditState { it.trimLeadingTo(targetMs) }
    }

    fun onTrimTrailingChanged(targetMs: Long) {
        updateTrimEditState { it.trimTrailingTo(targetMs) }
    }

    fun onTrimSplitAt(positionMs: Long) {
        updateTrimEditState { it.splitAt(positionMs) }
    }

    fun onTrimSelectSegmentAt(positionMs: Long) {
        updateTrimEditState { it.selectSegmentAt(positionMs) }
    }

    fun onTrimToggleCutSelected() {
        updateTrimEditState { state -> state.toggleCut(state.selectedSegment?.id) }
    }

    fun onTrimUndo() {
        updateTrimEditState { it.undo() }
    }

    fun onTrimReset() {
        updateTrimEditState { it.reset() }
    }

    private inline fun updateTrimEditState(transform: (TimelineEditState) -> TimelineEditState) {
        _phase.update { current ->
            if (current is StudioPhase.Trimming && current.renderState !is TrimRenderState.Rendering) {
                current.copy(editState = transform(current.editState))
            } else {
                current
            }
        }
    }

    fun cancelTrimEditor() {
        val trimming = _phase.value as? StudioPhase.Trimming ?: return
        renderJob?.cancel()
        renderJob = null
        waveformJob?.cancel()
        waveformJob = null
        _phase.value = StudioPhase.Review(
            originalVideoPath = trimming.originalVideoPath,
            activeVideoPath = trimming.previousActiveVideoPath,
            recordedAtMillis = trimming.recordedAtMillis,
            appliedEditState = trimming.previousAppliedEditState,
            saveState = SaveState.Idle,
        )
    }

    fun cancelTrimRender() {
        renderJob?.cancel()
        renderJob = null
        _phase.update { current ->
            if (current is StudioPhase.Trimming) {
                current.copy(renderState = TrimRenderState.Idle)
            } else {
                current
            }
        }
    }

    fun dismissTrimRenderError() {
        _phase.update { current ->
            if (current is StudioPhase.Trimming && current.renderState is TrimRenderState.Error) {
                current.copy(renderState = TrimRenderState.Idle)
            } else {
                current
            }
        }
    }

    fun applyTrimCuts(context: Context) {
        val trimming = _phase.value as? StudioPhase.Trimming ?: return
        if (trimming.renderState is TrimRenderState.Rendering) return

        // If no sections are removed, skip re-encoding and return the crisp original MP4
        if (!trimming.editState.hasRemovals) {
            if (trimming.previousActiveVideoPath != trimming.originalVideoPath) {
                val staleEditedPath = trimming.previousActiveVideoPath
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching { File(staleEditedPath).delete() }
                }
            }
            _phase.value = StudioPhase.Review(
                originalVideoPath = trimming.originalVideoPath,
                activeVideoPath = trimming.originalVideoPath,
                recordedAtMillis = trimming.recordedAtMillis,
                appliedEditState = null,
                saveState = SaveState.Idle,
            )
            return
        }

        val keptRanges = trimming.editState.mergedKeptRanges()
        if (keptRanges.isEmpty()) return

        val appContext = context.applicationContext
        val outputFile = File(appContext.cacheDir, "eyeline_cut_${System.currentTimeMillis()}.mp4")
        val sourceFile = File(trimming.originalVideoPath)

        _phase.value = trimming.copy(renderState = TrimRenderState.Rendering(progress = 0.02f))
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            val result = VideoCutRenderer.renderKeptRanges(
                context = appContext,
                sourceFile = sourceFile,
                keptRanges = keptRanges,
                outputFile = outputFile,
                onProgress = { progress ->
                    _phase.update { current ->
                        if (current is StudioPhase.Trimming &&
                            current.renderState is TrimRenderState.Rendering
                        ) {
                            current.copy(renderState = TrimRenderState.Rendering(progress))
                        } else {
                            current
                        }
                    }
                },
            )

            when (result) {
                is VideoRenderResult.Success -> {
                    if (trimming.previousActiveVideoPath != trimming.originalVideoPath &&
                        trimming.previousActiveVideoPath != result.outputFile.absolutePath
                    ) {
                        val stalePath = trimming.previousActiveVideoPath
                        launch(Dispatchers.IO) { runCatching { File(stalePath).delete() } }
                    }
                    _phase.value = StudioPhase.Review(
                        originalVideoPath = trimming.originalVideoPath,
                        activeVideoPath = result.outputFile.absolutePath,
                        recordedAtMillis = trimming.recordedAtMillis,
                        appliedEditState = trimming.editState,
                        saveState = SaveState.Idle,
                    )
                }

                is VideoRenderResult.Failure -> {
                    _phase.update { current ->
                        if (current is StudioPhase.Trimming) {
                            current.copy(renderState = TrimRenderState.Error(result.message))
                        } else {
                            current
                        }
                    }
                }
            }
        }
    }

    fun retake() {
        waveformJob?.cancel()
        waveformJob = null
        renderJob?.cancel()
        renderJob = null
        val pathsToDelete = when (val current = _phase.value) {
            is StudioPhase.Review -> setOf(current.originalVideoPath, current.activeVideoPath)
            is StudioPhase.Trimming -> setOf(current.originalVideoPath, current.previousActiveVideoPath)
            else -> emptySet()
        }
        if (pathsToDelete.isNotEmpty()) {
            pathsToDelete.forEach { cachedWaveforms.remove(it) }
            viewModelScope.launch(Dispatchers.IO) {
                pathsToDelete.forEach { path -> runCatching { File(path).delete() } }
            }
        }
        _scrollResetCounter.update { it + 1 }
        _phase.value = StudioPhase.Preview
        startPreRollMicMonitor()
    }

    fun saveTakeToGallery(context: Context) {
        val current = _phase.value as? StudioPhase.Review ?: return
        if (current.saveState is SaveState.Saving || current.saveState is SaveState.Saved) return

        val resolvedTitle = script.value?.title.orEmpty()
        _phase.value = current.copy(saveState = SaveState.Saving)
        viewModelScope.launch {
            val result = VideoGalleryExporter.exportToGallery(
                context = context.applicationContext,
                sourceFile = File(current.activeVideoPath),
                scriptTitle = resolvedTitle,
                recordedAtMillis = current.recordedAtMillis,
            )
            _phase.update { phaseState ->
                if (phaseState is StudioPhase.Review) {
                    when (result) {
                        is VideoExportResult.Success -> phaseState.copy(
                            saveState = SaveState.Saved(result.metadata)
                        )
                        is VideoExportResult.Failure -> phaseState.copy(
                            saveState = SaveState.Error(result.message)
                        )
                    }
                } else {
                    phaseState
                }
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // Studio Preference Mutators
    fun updateFontSize(sizeSp: Float) {
        viewModelScope.launch { preferences.updateFontSize(sizeSp) }
    }

    fun updateScrollSpeed(speed: Float) {
        viewModelScope.launch { preferences.updateScrollSpeed(speed) }
    }

    fun updateBackgroundOpacity(opacity: Float) {
        viewModelScope.launch { preferences.updateBackgroundOpacity(opacity) }
    }

    fun toggleFramingGrid() {
        viewModelScope.launch { preferences.setFramingGrid(!settings.value.showFramingGrid) }
    }

    fun toggleRimLight() {
        viewModelScope.launch { preferences.setRimLightEnabled(!settings.value.rimLightEnabled) }
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

    fun setVolumeDownShutterEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setVolumeDownShutterEnabled(enabled) }
    }

    fun setCountdownSoundsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setCountdownSoundsEnabled(enabled) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
        timerJob?.cancel()
        leadInHoldJob?.cancel()
        waveformJob?.cancel()
        renderJob?.cancel()
        preRollAudioMonitor.stopImmediately()
        val pathsToDelete = when (val current = _phase.value) {
            is StudioPhase.Review -> setOf(current.originalVideoPath, current.activeVideoPath)
            is StudioPhase.Trimming -> setOf(current.originalVideoPath, current.previousActiveVideoPath)
            else -> emptySet()
        }
        pathsToDelete.forEach { path -> runCatching { File(path).delete() } }
        cachedWaveforms.clear()
    }

    class Factory(
        private val scriptId: Long,
        private val scriptsRepository: ScriptsRepository,
        private val preferences: StudioPreferencesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StudioViewModel(scriptId, scriptsRepository, preferences) as T
        }
    }

    companion object {
        const val AUDIO_BAR_COUNT = 7
    }
}
