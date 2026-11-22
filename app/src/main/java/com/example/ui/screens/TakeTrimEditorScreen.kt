package com.example.ui.screens

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.capture.TimelineEditState
import com.example.capture.WaveformLoadState
import com.example.ui.components.WaveformTimeline
import com.example.ui.theme.DarkCharcoalBg
import com.example.ui.theme.DarkCharcoalContainer
import com.example.ui.theme.DarkCharcoalOutline
import com.example.ui.theme.DarkCharcoalSurface
import com.example.ui.theme.DarkOnSurface
import com.example.ui.theme.DarkOnSurfaceVariant
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioAmberOnPrimary
import com.example.ui.theme.TallyCrimson
import com.example.ui.viewmodel.TrimRenderState
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.roundToInt

/**
 * Post-recording Trim & Cut Editor launched from Take Review.
 * Pairs a live cut-skipping vertical video preview with an interactive audio waveform timeline,
 * draggable start/end trim handles, split & middle-cut controls, and Media3 Transformer export.
 */
@OptIn(UnstableApi::class)
@Composable
fun TakeTrimEditorScreen(
    sourceVideoPath: String,
    editState: TimelineEditState,
    waveformState: WaveformLoadState,
    renderState: TrimRenderState,
    onDurationResolved: (Long) -> Unit,
    onTrimDragStart: () -> Unit,
    onTrimStartChange: (Long) -> Unit,
    onTrimEndChange: (Long) -> Unit,
    onSplitAt: (Long) -> Unit,
    onSelectSegmentAt: (Long) -> Unit,
    onToggleCutSelected: () -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onApplyCuts: () -> Unit,
    onCancelRender: () -> Unit,
    onDismissRenderError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val latestEditState by rememberUpdatedState(editState)

    var isPlaying by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var playheadMs by remember(sourceVideoPath) {
        mutableLongStateOf(editState.trimStartMs)
    }

    val isRendering = renderState is TrimRenderState.Rendering

    BackHandler {
        if (isRendering) {
            onCancelRender()
        } else {
            onCancel()
        }
    }

    val exoPlayer = remember(sourceVideoPath) {
        ExoPlayer.Builder(context).build().apply {
            val uri = Uri.fromFile(File(sourceVideoPath))
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val d = exoPlayer.duration
                    if (d > 0L) {
                        onDurationResolved(d)
                    }
                    val startTarget = latestEditState.trimStartMs
                    if (exoPlayer.currentPosition < startTarget) {
                        exoPlayer.seekTo(startTarget)
                        playheadMs = startTarget
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Pause preview playback cleanly while Media3 Transformer is encoding
    LaunchedEffect(isRendering) {
        if (isRendering && exoPlayer.isPlaying) {
            exoPlayer.pause()
        }
    }

    // Fast live cut-skipping playback coordinator (~40ms cadence)
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isScrubbing && !isRendering) {
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                val state = latestEditState
                if (exoPlayer.isPlaying) {
                    val skipTarget = state.nextPlaybackPosition(pos)
                    if (skipTarget != null) {
                        exoPlayer.seekTo(skipTarget)
                        playheadMs = skipTarget
                    } else {
                        playheadMs = pos.coerceIn(0L, state.durationMs)
                    }
                }
            }
            delay(40L)
        }
    }

    val selectedSegment = editState.selectedSegment
    val selectedIndex = editState.selectedSegmentIndex()
    val isSelectedCut = selectedSegment?.isRemoved == true
    val canSplit = editState.canSplitAt(playheadMs)
    val canToggleCut = editState.canToggleCut(selectedSegment?.id)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkCharcoalBg)
            .testTag("take_trim_editor_screen"),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Top 56% Viewport: Vertical Video Preview + Floating Top Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.22f)
                    .background(Color.Black),
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        }
                    },
                    update = { view ->
                        if (view.player !== exoPlayer) {
                            view.player = exoPlayer
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (!isRendering) {
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    val jump = editState.nextPlaybackPosition(playheadMs)
                                    if (jump != null) {
                                        exoPlayer.seekTo(jump)
                                        playheadMs = jump
                                    }
                                    exoPlayer.play()
                                }
                            }
                        },
                )

                // Centered Play indicator when paused
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isPlaying && !isRendering,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.56f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.review_play_pause),
                            tint = Color.White,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }

                // Floating Top Bar: Cancel | Original -> Kept Duration Pill | Mute
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onCancel,
                        enabled = !isRendering,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(DarkCharcoalBg.copy(alpha = 0.72f))
                            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                            .testTag("trim_cancel_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.trim_cancel),
                            tint = Color.White,
                        )
                    }

                    // Monospace Duration Telemetry Pill: Original -> Kept + Cut Count
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(DarkCharcoalBg.copy(alpha = 0.78f))
                            .border(
                                width = 1.dp,
                                color = if (editState.hasRemovals) {
                                    StudioAmber.copy(alpha = 0.5f)
                                } else {
                                    Color.White.copy(alpha = 0.14f)
                                },
                                shape = RoundedCornerShape(18.dp),
                            )
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("trim_duration_pill"),
                    ) {
                        Text(
                            text = if (editState.hasRemovals) {
                                "${TimelineEditState.formatPreciseTime(editState.durationMs)} → ${
                                    TimelineEditState.formatPreciseTime(editState.keptDurationMs)
                                }"
                            } else {
                                TimelineEditState.formatPreciseTime(editState.durationMs)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (editState.hasRemovals) StudioAmber else Color.White,
                        )
                        if (editState.cutCount > 0) {
                            Text(
                                text = if (editState.cutCount == 1) {
                                    stringResource(R.string.trim_cuts_badge_one)
                                } else {
                                    stringResource(R.string.trim_cuts_badge_many, editState.cutCount)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(TallyCrimson.copy(alpha = 0.35f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            isMuted = !isMuted
                            exoPlayer.volume = if (isMuted) 0f else 1f
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(DarkCharcoalBg.copy(alpha = 0.72f))
                            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                            .testTag("trim_mute_button"),
                    ) {
                        Icon(
                            imageVector = if (isMuted) {
                                Icons.AutoMirrored.Rounded.VolumeOff
                            } else {
                                Icons.AutoMirrored.Rounded.VolumeUp
                            },
                            contentDescription = stringResource(R.string.review_mute_toggle),
                            tint = Color.White,
                        )
                    }
                }
            }

            // Bottom 44% Studio Editing Console Deck
            Surface(
                color = DarkCharcoalSurface,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = DarkCharcoalOutline,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    )
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Render error banner if Transformer failed
                    if (renderState is TrimRenderState.Error) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF4A1517).copy(alpha = 0.90f))
                                .clickable { onDismissRenderError() }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("trim_render_error_banner"),
                        ) {
                            Text(
                                text = renderState.message.ifBlank {
                                    stringResource(R.string.trim_render_error)
                                },
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

                    // Row 1: Play/Pause Transport + Playhead Monospace Timecode + Active Clip Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            IconButton(
                                onClick = {
                                    if (exoPlayer.isPlaying) {
                                        exoPlayer.pause()
                                    } else {
                                        val jump = editState.nextPlaybackPosition(playheadMs)
                                        if (jump != null) {
                                            exoPlayer.seekTo(jump)
                                            playheadMs = jump
                                        }
                                        exoPlayer.play()
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(DarkCharcoalContainer)
                                    .border(1.dp, DarkCharcoalOutline, CircleShape)
                                    .testTag("trim_play_pause_button"),
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) {
                                        Icons.Rounded.Pause
                                    } else {
                                        Icons.Rounded.PlayArrow
                                    },
                                    contentDescription = stringResource(R.string.review_play_pause),
                                    tint = Color.White,
                                )
                            }

                            Column {
                                Text(
                                    text = TimelineEditState.formatPreciseTime(playheadMs),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = DarkOnSurface,
                                    modifier = Modifier.testTag("trim_playhead_timecode"),
                                )
                                Text(
                                    text = when (waveformState) {
                                        is WaveformLoadState.Loading ->
                                            stringResource(R.string.trim_loading_waveform)
                                        is WaveformLoadState.Unavailable ->
                                            stringResource(R.string.trim_no_audio_waveform)
                                        is WaveformLoadState.Ready ->
                                            stringResource(R.string.trim_hint_controls)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DarkOnSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        // Active Segment Badge (Clip N · Kept / Cut)
                        val badgeColor = if (isSelectedCut) {
                            TallyCrimson.copy(alpha = 0.22f)
                        } else {
                            StudioAmber.copy(alpha = 0.16f)
                        }
                        val badgeBorder = if (isSelectedCut) {
                            TallyCrimson.copy(alpha = 0.65f)
                        } else {
                            StudioAmber.copy(alpha = 0.45f)
                        }
                        Text(
                            text = if (isSelectedCut) {
                                stringResource(R.string.trim_segment_cut, selectedIndex + 1)
                            } else {
                                stringResource(R.string.trim_segment_kept, selectedIndex + 1)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelectedCut) Color(0xFFFFB4B6) else StudioAmber,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(badgeColor)
                                .border(1.dp, badgeBorder, CircleShape)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .testTag("trim_selected_segment_badge"),
                        )
                    }

                    // Row 2: Interactive Audio Waveform Timeline with Trim Handles & Playhead
                    WaveformTimeline(
                        editState = editState,
                        waveformState = waveformState,
                        playheadMs = playheadMs,
                        onTrimDragStart = {
                            if (exoPlayer.isPlaying) exoPlayer.pause()
                            onTrimDragStart()
                        },
                        onTrimStartChange = { newStart ->
                            onTrimStartChange(newStart)
                            playheadMs = newStart
                            exoPlayer.seekTo(newStart)
                        },
                        onTrimEndChange = { newEnd ->
                            onTrimEndChange(newEnd)
                            playheadMs = newEnd
                            exoPlayer.seekTo(newEnd)
                        },
                        onPlayheadScrub = { targetMs ->
                            if (exoPlayer.isPlaying) exoPlayer.pause()
                            playheadMs = targetMs
                            exoPlayer.seekTo(targetMs)
                            onSelectSegmentAt(targetMs)
                        },
                        onSegmentTap = { tapMs ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            playheadMs = tapMs
                            exoPlayer.seekTo(tapMs)
                            onSelectSegmentAt(tapMs)
                        },
                        onScrubbingChanged = { scrubbing ->
                            isScrubbing = scrubbing
                        },
                    )

                    // Row 3: One-Handed Editing Tools (Split | Cut/Restore | Undo | Reset)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TrimConsoleToolButton(
                            icon = Icons.Rounded.ContentCut,
                            label = stringResource(R.string.trim_split),
                            enabled = canSplit && !isRendering,
                            highlighted = false,
                            destructive = false,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSplitAt(playheadMs)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("trim_split_button"),
                        )

                        TrimConsoleToolButton(
                            icon = if (isSelectedCut) {
                                Icons.Rounded.Restore
                            } else {
                                Icons.Rounded.DeleteOutline
                            },
                            label = if (isSelectedCut) {
                                stringResource(R.string.trim_restore)
                            } else {
                                stringResource(R.string.trim_cut)
                            },
                            enabled = canToggleCut && !isRendering,
                            highlighted = isSelectedCut,
                            destructive = !isSelectedCut && canToggleCut,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleCutSelected()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("trim_toggle_cut_button"),
                        )

                        TrimConsoleToolButton(
                            icon = Icons.AutoMirrored.Rounded.Undo,
                            label = stringResource(R.string.trim_undo),
                            enabled = editState.canUndo && !isRendering,
                            highlighted = false,
                            destructive = false,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onUndo()
                            },
                            modifier = Modifier
                                .weight(0.85f)
                                .testTag("trim_undo_button"),
                        )

                        TrimConsoleToolButton(
                            icon = Icons.Rounded.RestartAlt,
                            label = stringResource(R.string.trim_reset),
                            enabled = editState.hasEdits && !isRendering,
                            highlighted = false,
                            destructive = false,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onReset()
                            },
                            modifier = Modifier
                                .weight(0.85f)
                                .testTag("trim_reset_button"),
                        )
                    }

                    // Guardrail micro-hint if selected clip is the sole kept clip
                    if (!isSelectedCut && !canToggleCut && editState.segments.size == 1) {
                        Text(
                            text = stringResource(R.string.trim_keep_one_guard),
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkOnSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }

                    // Row 4: Full-Width Primary CTA — Apply Cuts (or Done when unedited)
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onApplyCuts()
                        },
                        enabled = !isRendering,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StudioAmber,
                            contentColor = StudioAmberOnPrimary,
                            disabledContainerColor = StudioAmber.copy(alpha = 0.45f),
                            disabledContentColor = StudioAmberOnPrimary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("trim_apply_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (editState.hasRemovals) {
                                "${stringResource(R.string.trim_apply_cuts)} (${
                                    TimelineEditState.formatPreciseTime(editState.keptDurationMs)
                                })"
                            } else {
                                stringResource(R.string.trim_done_no_cuts)
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }

        // Determinate Rendering Overlay while Media3 Transformer builds the cut MP4
        if (renderState is TrimRenderState.Rendering) {
            val pct = (renderState.progress * 100f).roundToInt().coerceIn(0, 100)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.78f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .testTag("trim_rendering_overlay"),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(DarkCharcoalSurface)
                        .border(1.dp, DarkCharcoalOutline, RoundedCornerShape(24.dp))
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                ) {
                    CircularProgressIndicator(
                        progress = { renderState.progress.coerceIn(0.02f, 1f) },
                        color = StudioAmber,
                        trackColor = DarkCharcoalContainer,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(54.dp),
                    )
                    Text(
                        text = stringResource(R.string.trim_rendering_progress, pct),
                        style = MaterialTheme.typography.titleSmall,
                        color = DarkOnSurface,
                    )
                    LinearProgressIndicator(
                        progress = { renderState.progress.coerceIn(0.02f, 1f) },
                        color = StudioAmber,
                        trackColor = DarkCharcoalContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                    )
                    OutlinedButton(
                        onClick = onCancelRender,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("trim_cancel_render_button"),
                    ) {
                        Text(
                            text = stringResource(R.string.trim_cancel_render),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrimConsoleToolButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    highlighted: Boolean,
    destructive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = when {
        !enabled -> DarkCharcoalContainer.copy(alpha = 0.45f)
        highlighted -> StudioAmber.copy(alpha = 0.22f)
        destructive -> TallyCrimson.copy(alpha = 0.18f)
        else -> DarkCharcoalContainer
    }
    val borderColor = when {
        !enabled -> DarkCharcoalOutline.copy(alpha = 0.5f)
        highlighted -> StudioAmber.copy(alpha = 0.7f)
        destructive -> TallyCrimson.copy(alpha = 0.55f)
        else -> DarkCharcoalOutline
    }
    val contentColor = when {
        !enabled -> DarkOnSurfaceVariant.copy(alpha = 0.45f)
        highlighted -> StudioAmber
        destructive -> Color(0xFFFFB4B6)
        else -> DarkOnSurface
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.height(54.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(19.dp),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
