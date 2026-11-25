package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.AudioStats
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WbIncandescent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.capture.HardwareShutterBus
import com.example.data.model.ScriptMetricsCalculator
import com.example.ui.components.CountdownOverlay
import com.example.ui.components.FramingGridOverlay
import com.example.ui.components.LiveAudioLevelMeter
import com.example.ui.components.QuickAdjustDrawer
import com.example.ui.components.ScreenRimLightOverlay
import com.example.ui.components.TeleprompterOverlay
import com.example.ui.theme.DarkCharcoalBg
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.TallyCrimson
import com.example.ui.viewmodel.StudioCaptureCommand
import com.example.ui.viewmodel.StudioPhase
import com.example.ui.viewmodel.StudioViewModel
import java.io.File

@Composable
fun TeleprompterStudioScreen(
    viewModel: StudioViewModel,
    onCloseStudio: () -> Unit,
    onEditScript: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current

    val script by viewModel.script.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val audioBars by viewModel.audioBars.collectAsStateWithLifecycle()
    val isMicStreamActive by viewModel.isMicStreamActive.collectAsStateWithLifecycle()
    val scrollResetCounter by viewModel.scrollResetCounter.collectAsStateWithLifecycle()
    val useFrontCamera by viewModel.useFrontCamera.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    val requiredPermissions = remember {
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }

    var hasPermissions by remember {
        mutableStateOf(
            requiredPermissions.all { perm ->
                ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    var hasRequestedOnce by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasRequestedOnce = true
        hasPermissions = requiredPermissions.all { perm -> result[perm] == true }
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    LaunchedEffect(hasPermissions) {
        viewModel.onPermissionsUpdated(hasPermissions)
    }

    // Re-check permissions & lifecycle pause/resume for polite pre-roll mic management
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val granted = requiredPermissions.all { perm ->
                        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
                    }
                    hasPermissions = granted
                    if (granted && viewModel.phase.value is StudioPhase.Preview) {
                        viewModel.startPreRollMicMonitor()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.stopPreRollMicMonitor()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopPreRollMicMonitor()
        }
    }

    // Keep screen awake while in Studio
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Screen Rim Light Brightness Boost — elevates screen brightness for genuine face fill light
    // in BOTH Dark & Light modes, and restores original brightness cleanly when off or leaving.
    val isRimLightActiveOnCamera = hasPermissions &&
        settings.rimLightEnabled &&
        phase !is StudioPhase.Review &&
        phase !is StudioPhase.Trimming

    DisposableEffect(context, isRimLightActiveOnCamera, settings.rimLightIntensity) {
        val window = context.findActivity()?.window
        if (window != null) {
            val params = window.attributes
            params.screenBrightness = if (isRimLightActiveOnCamera) {
                (0.76f + 0.24f * settings.rimLightIntensity).coerceIn(0.80f, 1.0f)
            } else {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            window.attributes = params
        }
        onDispose {
            val w = context.findActivity()?.window
            if (w != null) {
                val params = w.attributes
                params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                w.attributes = params
            }
        }
    }

    // Sync Hardware Volume Down Shutter Bus (active only during camera studio capture phases)
    LaunchedEffect(hasPermissions, phase, settings.volumeDownShutterEnabled) {
        HardwareShutterBus.isStudioCaptureActive = hasPermissions &&
            phase !is StudioPhase.Review &&
            phase !is StudioPhase.Trimming
        HardwareShutterBus.isVolumeDownShutterEnabled = settings.volumeDownShutterEnabled
    }

    DisposableEffect(Unit) {
        onDispose {
            HardwareShutterBus.isStudioCaptureActive = false
        }
    }

    LaunchedEffect(Unit) {
        HardwareShutterBus.shutterTrigger.collect {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.onShutterPressed()
        }
    }

    // Handle Android Back press cleanly across studio phases
    BackHandler {
        when (phase) {
            is StudioPhase.Countdown -> viewModel.cancelCountdown()
            is StudioPhase.Recording -> viewModel.requestStopRecording()
            is StudioPhase.Review -> viewModel.retake()
            is StudioPhase.Trimming -> viewModel.cancelTrimEditor()
            is StudioPhase.Preview -> onCloseStudio()
        }
    }

    // Permission Gate
    if (!hasPermissions) {
        StudioPermissionGate(
            hasRequestedOnce = hasRequestedOnce,
            onRequestPermission = { permissionLauncher.launch(requiredPermissions) },
            onOpenSettings = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
                context.startActivity(intent)
            },
            onBack = onCloseStudio,
        )
        return
    }

    // Instant Take Review & Dedicated Trim/Cut Editor Phases
    val currentPhase = phase
    if (currentPhase is StudioPhase.Review) {
        val shareChooserTitle = stringResource(R.string.review_share_chooser_title)
        TakeReviewScreen(
            videoFilePath = currentPhase.activeVideoPath,
            scriptTitle = script?.title.orEmpty(),
            recordedAtMillis = currentPhase.recordedAtMillis,
            isEdited = currentPhase.isEdited,
            saveState = currentPhase.saveState,
            shareState = currentPhase.shareState,
            onRetake = { viewModel.retake() },
            onTrimTake = { viewModel.openTrimEditor() },
            onEditScript = onEditScript,
            onSaveToPhone = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.saveTakeToGallery(context)
            },
            onShareTake = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.shareTake(context, shareChooserTitle)
            },
            onDismissShareError = { viewModel.dismissShareError() },
        )
        return
    }

    if (currentPhase is StudioPhase.Trimming) {
        TakeTrimEditorScreen(
            sourceVideoPath = currentPhase.originalVideoPath,
            editState = currentPhase.editState,
            waveformState = currentPhase.waveformState,
            renderState = currentPhase.renderState,
            onDurationResolved = viewModel::onTrimDurationResolved,
            onTrimDragStart = viewModel::onTrimDragStart,
            onTrimStartChange = viewModel::onTrimLeadingChanged,
            onTrimEndChange = viewModel::onTrimTrailingChanged,
            onSplitAt = viewModel::onTrimSplitAt,
            onSelectSegmentAt = viewModel::onTrimSelectSegmentAt,
            onToggleCutSelected = viewModel::onTrimToggleCutSelected,
            onUndo = viewModel::onTrimUndo,
            onReset = viewModel::onTrimReset,
            onCancel = viewModel::cancelTrimEditor,
            onApplyCuts = { viewModel.applyTrimCuts(context) },
            onCancelRender = viewModel::cancelTrimRender,
            onDismissRenderError = viewModel::dismissTrimRenderError,
        )
        return
    }

    // CameraX Preview & VideoCapture State
    var showQuickDrawer by remember { mutableStateOf(false) }
    var videoCaptureRef by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecordingRef by remember { mutableStateOf<Recording?>(null) }
    var hasRecordingStarted by remember { mutableStateOf(false) }
    var hasReceivedRecordedFrame by remember { mutableStateOf(false) }
    var isStoppingRecording by remember { mutableStateOf(false) }
    var pendingStopRequested by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }

    // Bind CameraX Preview + VideoCapture whenever camera facing or lifecycle changes
    DisposableEffect(lifecycleOwner, useFrontCamera) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        val listener = Runnable {
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preferredSelector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                val resolvedSelector = when {
                    cameraProvider.hasCamera(preferredSelector) -> preferredSelector
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> {
                        cameraError = "No camera detected on this device."
                        return@Runnable
                    }
                }

                // Query MediaCodec-backed SDR video capabilities directly so devices/emulators
                // with incomplete CamcorderProfile/EncoderProfiles never fail or spam errors.
                val cameraInfo = cameraProvider.getCameraInfo(resolvedSelector)
                val codecCaps = Recorder.getVideoCapabilities(
                    cameraInfo,
                    Recorder.VIDEO_CAPABILITIES_SOURCE_CODEC_CAPABILITIES,
                )
                val supportedSdrQualities = codecCaps.getSupportedQualities(DynamicRange.SDR)
                val preferredOrder = listOf(Quality.FHD, Quality.HD, Quality.SD, Quality.LOWEST)
                val matchedQualities = preferredOrder.filter { it in supportedSdrQualities }

                val qualitySelector = if (matchedQualities.isNotEmpty()) {
                    QualitySelector.fromOrderedList(
                        matchedQualities,
                        FallbackStrategy.lowerQualityOrHigherThan(matchedQualities.last()),
                    )
                } else {
                    QualitySelector.from(
                        Quality.LOWEST,
                        FallbackStrategy.higherQualityOrLowerThan(Quality.LOWEST),
                    )
                }

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val recorder = Recorder.Builder()
                    .setVideoCapabilitiesSource(Recorder.VIDEO_CAPABILITIES_SOURCE_CODEC_CAPABILITIES)
                    .setQualitySelector(qualitySelector)
                    .build()
                val videoCapture = VideoCapture.Builder(recorder)
                    .setDynamicRange(DynamicRange.SDR)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    resolvedSelector,
                    preview,
                    videoCapture,
                )
                videoCaptureRef = videoCapture
                cameraError = null
            } catch (e: Exception) {
                cameraError = e.localizedMessage ?: "Unable to initialize camera."
            }
        }

        cameraProviderFuture.addListener(listener, mainExecutor)

        onDispose {
            val activeRec = activeRecordingRef
            if (activeRec != null && !isStoppingRecording && hasRecordingStarted) {
                isStoppingRecording = true
                runCatching { activeRec.stop() }
            } else if (activeRec != null && !isStoppingRecording) {
                runCatching { activeRec.close() }
            }
            activeRecordingRef = null
            hasRecordingStarted = false
            hasReceivedRecordedFrame = false
            isStoppingRecording = false
            pendingStopRequested = false
            runCatching {
                if (cameraProviderFuture.isDone) {
                    cameraProviderFuture.get().unbindAll()
                }
            }
        }
    }

    // Execute VideoCapture Start/Stop commands from StudioViewModel
    LaunchedEffect(Unit) {
        viewModel.captureCommands.collect { command ->
            when (command) {
                StudioCaptureCommand.StartVideoCapture -> {
                    showQuickDrawer = false
                    val vc = videoCaptureRef
                    if (vc == null) {
                        viewModel.onRecordingFinalized(null, "Camera is not ready yet.")
                        return@collect
                    }
                    hasRecordingStarted = false
                    hasReceivedRecordedFrame = false
                    isStoppingRecording = false
                    pendingStopRequested = false

                    val tempFile = File(
                        context.cacheDir,
                        "eyeline_take_${System.currentTimeMillis()}.mp4",
                    )
                    val outputOptions = FileOutputOptions.Builder(tempFile).build()
                    @SuppressLint("MissingPermission")
                    val recording = vc.output
                        .prepareRecording(context, outputOptions)
                        .withAudioEnabled()
                        .start(ContextCompat.getMainExecutor(context)) { event ->
                            when (event) {
                                is VideoRecordEvent.Start -> {
                                    hasRecordingStarted = true
                                    viewModel.onRecordingStarted()
                                }
                                is VideoRecordEvent.Status -> {
                                    val stats = event.recordingStats
                                    if (stats.recordedDurationNanos > 0L || stats.numBytesRecorded > 0L) {
                                        hasReceivedRecordedFrame = true
                                    }
                                    val audioStats = stats.audioStats
                                    if (audioStats.hasAudio()) {
                                        viewModel.onRecordingAudioAmplitude(
                                            amplitude = audioStats.audioAmplitude,
                                            isActiveStream = audioStats.audioState == AudioStats.AUDIO_STATE_ACTIVE,
                                        )
                                    }
                                    // If user tapped Stop while tracks were warming up, stop safely now
                                    if (pendingStopRequested && !isStoppingRecording && hasReceivedRecordedFrame) {
                                        isStoppingRecording = true
                                        activeRecordingRef?.stop()
                                    }
                                }
                                is VideoRecordEvent.Finalize -> {
                                    activeRecordingRef = null
                                    hasRecordingStarted = false
                                    hasReceivedRecordedFrame = false
                                    isStoppingRecording = false
                                    pendingStopRequested = false

                                    val hasUsableFile = tempFile.exists() &&
                                        tempFile.length() > 1024L &&
                                        event.recordingStats.recordedDurationNanos > 0L
                                    val audioState = event.recordingStats.audioStats.audioState
                                    val isRecoverableFinalize =
                                        !event.hasError() ||
                                            (event.error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE && hasUsableFile)

                                    if (!isRecoverableFinalize) {
                                        val msg = when {
                                            event.error == VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA ->
                                                "Take was too short to save. Try recording for at least a second."
                                            audioState == AudioStats.AUDIO_STATE_SOURCE_ERROR ||
                                                audioState == AudioStats.AUDIO_STATE_ENCODER_ERROR ->
                                                "Microphone audio stream was unavailable. Please try again."
                                            else ->
                                                "Recording stopped with an error (${event.error})."
                                        }
                                        runCatching { tempFile.delete() }
                                        viewModel.onRecordingFinalized(null, msg)
                                    } else {
                                        viewModel.onRecordingFinalized(tempFile, null)
                                    }
                                }
                            }
                        }
                    activeRecordingRef = recording
                }

                StudioCaptureCommand.StopVideoCapture -> {
                    val rec = activeRecordingRef ?: return@collect
                    if (isStoppingRecording) return@collect
                    if (hasRecordingStarted && hasReceivedRecordedFrame) {
                        isStoppingRecording = true
                        rec.stop()
                    } else {
                        // Defer stop briefly until the muxer track has started to prevent MPEG4Writer errors
                        pendingStopRequested = true
                        launch {
                            delay(650L)
                            if (pendingStopRequested && !isStoppingRecording) {
                                isStoppingRecording = true
                                activeRecordingRef?.stop()
                            }
                        }
                    }
                }
            }
        }
    }

    val recordingPhase = currentPhase as? StudioPhase.Recording
    val isRecording = recordingPhase != null
    val isCountdown = currentPhase is StudioPhase.Countdown
    val isScrollPaused = recordingPhase?.isScrollPaused == true
    val isAutoScrollingNow = recordingPhase != null &&
        recordingPhase.hasStartedCapture &&
        !recordingPhase.isLeadInHold &&
        !recordingPhase.isScrollPaused
    val elapsedSeconds = recordingPhase?.elapsedSeconds ?: 0L

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isRecording, showQuickDrawer) {
                detectTapGestures(
                    onTap = {
                        if (showQuickDrawer) {
                            showQuickDrawer = false
                        } else if (isRecording) {
                            viewModel.toggleScrollPause()
                        }
                    }
                )
            }
            .testTag("teleprompter_studio_screen"),
    ) {
        // Layer 1: Full-screen vertical CameraX Preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        // Layer 2: Optional Framing Grid (Rule of Thirds + TikTok Safe Zone)
        FramingGridOverlay(visible = settings.showFramingGrid)

        // Layer 3: Screen Rim Light Overlay (works in BOTH Dark & Light modes!)
        ScreenRimLightOverlay(
            enabled = settings.rimLightEnabled,
            tone = settings.rimLightTone,
            intensity = settings.rimLightIntensity,
        )

        // Layer 4: Top Lens-Aligned HUD + Teleprompter Overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Compact Studio Status Strip (Close | Top-Center Live Audio Bars & REC Timer | Flip Camera)
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(
                    onClick = {
                        if (isRecording) {
                            viewModel.requestStopRecording()
                        } else {
                            onCloseStudio()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(DarkCharcoalBg.copy(alpha = 0.65f))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                        .testTag("studio_close_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.studio_close),
                        tint = Color.White,
                    )
                }

                // Geometrically Dead-Center Top Audio Level Indicator (+ compact REC timer while recording)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    AnimatedVisibility(visible = isRecording) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(TallyCrimson.copy(alpha = 0.90f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("studio_rec_timer"),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                            )
                            Text(
                                text = ScriptMetricsCalculator.formatTimer(elapsedSeconds),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                            )
                        }
                    }

                    LiveAudioLevelMeter(
                        bars = audioBars,
                        isMicActive = isMicStreamActive,
                    )
                }

                // Camera Flip button (only enabled when idle in Preview)
                IconButton(
                    onClick = { viewModel.toggleCameraFacing() },
                    enabled = currentPhase is StudioPhase.Preview,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(DarkCharcoalBg.copy(alpha = if (currentPhase is StudioPhase.Preview) 0.65f else 0.25f))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                        .testTag("studio_flip_camera_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cameraswitch,
                        contentDescription = stringResource(R.string.studio_flip_camera),
                        tint = Color.White.copy(alpha = if (currentPhase is StudioPhase.Preview) 1f else 0.4f),
                    )
                }
            }

            // Lens-Aligned Teleprompter Overlay right below the front camera lens!
            TeleprompterOverlay(
                scriptText = script?.content.orEmpty(),
                fontSizeSp = settings.fontSizeSp,
                scrollSpeedLevel = settings.scrollSpeedLevel,
                backgroundOpacity = settings.backgroundOpacity,
                isAutoScrolling = isAutoScrollingNow,
                isRecording = isRecording,
                isScrollPaused = isScrollPaused,
                scrollResetCounter = scrollResetCounter,
                onTapTogglePause = { viewModel.toggleScrollPause() },
            )

            // Unobtrusive One-Handed Micro-Hint Row + Options shortcut
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.studio_drag_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.36f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(StudioAmber.copy(alpha = 0.20f))
                        .border(1.dp, StudioAmber.copy(alpha = 0.45f), CircleShape)
                        .clickable { showQuickDrawer = !showQuickDrawer }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("studio_options_pill"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = StudioAmber,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = stringResource(R.string.action_options),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.92f),
                    )
                }
            }

            // Inline Error Banner if camera or recording reported an error
            val displayError = cameraError ?: errorMessage
            if (displayError != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF4A1517).copy(alpha = 0.90f))
                        .clickable {
                            cameraError = null
                            viewModel.clearError()
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = displayError,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // Layer 5: 3-Second Animated Countdown Overlay
        if (currentPhase is StudioPhase.Countdown) {
            CountdownOverlay(
                secondsRemaining = currentPhase.secondsRemaining,
                onCancel = { viewModel.cancelCountdown() },
            )
        }

        // Layer 6: Bottom Thumb-Zone Control Deck + Expandable Quick-Adjust Drawer
        if (!isCountdown) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.62f),
                                Color.Black.copy(alpha = 0.84f),
                            )
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimatedVisibility(
                    visible = showQuickDrawer,
                    enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = shrinkVertically(tween(180)) + fadeOut(tween(160)),
                ) {
                    QuickAdjustDrawer(
                        settings = settings,
                        onFontSizeChange = viewModel::updateFontSize,
                        onScrollSpeedChange = viewModel::updateScrollSpeed,
                        onBackgroundOpacityChange = viewModel::updateBackgroundOpacity,
                        onRimLightEnabledChange = viewModel::setRimLightEnabled,
                        onRimLightToneChange = viewModel::setRimLightTone,
                        onRimLightIntensityChange = viewModel::updateRimLightIntensity,
                        onVolumeDownShutterChange = viewModel::setVolumeDownShutterEnabled,
                        onCountdownSoundsChange = viewModel::setCountdownSoundsEnabled,
                        onThemeModeChange = viewModel::setThemeMode,
                        modifier = Modifier.heightIn(max = 360.dp),
                    )
                }

                // One-Handed Bottom Studio Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Left Cluster: Framing Grid + Screen Rim Light Quick Toggles
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StudioToolButton(
                            icon = Icons.Rounded.GridOn,
                            contentDescription = stringResource(R.string.studio_grid_toggle),
                            active = settings.showFramingGrid,
                            onClick = { viewModel.toggleFramingGrid() },
                            testTag = "studio_grid_toggle_button",
                        )

                        StudioToolButton(
                            icon = Icons.Rounded.WbIncandescent,
                            contentDescription = stringResource(R.string.studio_rim_light_toggle),
                            active = settings.rimLightEnabled,
                            onClick = { viewModel.toggleRimLight() },
                            testTag = "studio_rim_light_toggle_button",
                        )
                    }

                    // Center Hero: Tactile Morphing Record / Stop Shutter Button
                    StudioShutterButton(
                        isRecording = isRecording,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.onShutterPressed()
                        },
                    )

                    // Right Cluster: Reset Script to Top + Quick Adjust Controls Drawer
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StudioToolButton(
                            icon = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.studio_reset_scroll),
                            active = false,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.resetScrollToTop()
                            },
                            testTag = "studio_reset_scroll_button",
                        )

                        StudioToolButton(
                            icon = Icons.Rounded.Tune,
                            contentDescription = stringResource(R.string.studio_quick_controls),
                            active = showQuickDrawer,
                            onClick = { showQuickDrawer = !showQuickDrawer },
                            testTag = "studio_quick_controls_button",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioToolButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val bgColor by animateColorAsState(
        targetValue = if (active) StudioAmber else DarkCharcoalBg.copy(alpha = 0.72f),
        animationSpec = tween(180),
        label = "tool_btn_bg",
    )
    val iconTint by animateColorAsState(
        targetValue = if (active) Color(0xFF1B1200) else Color.White,
        animationSpec = tween(180),
        label = "tool_btn_tint",
    )

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(bgColor)
            .border(
                width = 1.dp,
                color = if (active) StudioAmber else Color.White.copy(alpha = 0.18f),
                shape = CircleShape,
            )
            .testTag(testTag),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun StudioShutterButton(
    isRecording: Boolean,
    onClick: () -> Unit,
) {
    val innerSize by animateDpAsState(
        targetValue = if (isRecording) 30.dp else 60.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "shutter_size",
    )
    val innerCorner by animateDpAsState(
        targetValue = if (isRecording) 8.dp else 30.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "shutter_corner",
    )

    Box(
        modifier = Modifier
            .size(78.dp)
            .clip(CircleShape)
            .border(
                width = 3.5.dp,
                color = if (isRecording) TallyCrimson.copy(alpha = 0.9f) else Color.White,
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .testTag("studio_shutter_button"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(innerSize)
                .clip(RoundedCornerShape(innerCorner))
                .background(TallyCrimson),
        )
    }
}

@Composable
private fun StudioPermissionGate(
    hasRequestedOnce: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        RoundedCornerShape(22.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Videocam,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp),
                )
            }

            Text(
                text = stringResource(R.string.permission_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )

            Text(
                text = stringResource(R.string.permission_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("permission_grant_button"),
            ) {
                Text(
                    text = stringResource(R.string.permission_grant),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            if (hasRequestedOnce) {
                OutlinedButton(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("permission_settings_button"),
                ) {
                    Text(
                        text = stringResource(R.string.permission_settings),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            TextButton(
                onClick = onBack,
                modifier = Modifier.testTag("permission_back_button"),
            ) {
                Text(text = stringResource(R.string.permission_back))
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
