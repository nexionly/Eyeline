package com.example.ui.screens

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.capture.VideoGalleryExporter
import com.example.data.model.ScriptMetricsCalculator
import com.example.data.preferences.RecordingAspectMode
import com.example.ui.theme.DarkCharcoalBg
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.VuGreen
import com.example.ui.viewmodel.SaveState
import com.example.ui.viewmodel.ShareState
import kotlinx.coroutines.delay
import java.io.File

/**
 * Full-screen vertical playback review screen powered by Jetpack Media3 ExoPlayer.
 * Lets the creator inspect their take immediately, reset the script for a Retake,
 * jump back to Edit Script, or save directly to the phone's gallery with script title & date metadata.
 */
@OptIn(UnstableApi::class)
@Composable
fun TakeReviewScreen(
    videoFilePath: String,
    scriptTitle: String,
    recordedAtMillis: Long,
    isEdited: Boolean,
    saveState: SaveState,
    shareState: ShareState,
    onRetake: () -> Unit,
    onTrimTake: () -> Unit,
    onEditScript: () -> Unit,
    onSaveToPhone: () -> Unit,
    onShareTake: () -> Unit,
    onDismissShareError: () -> Unit,
    modifier: Modifier = Modifier,
    aspectMode: RecordingAspectMode = RecordingAspectMode.VERTICAL_9_16,
) {
    val context = LocalContext.current
    val previewMetadata = remember(scriptTitle, recordedAtMillis) {
        VideoGalleryExporter.buildExportMetadata(
            scriptTitle = scriptTitle,
            recordedAtMillis = recordedAtMillis,
        )
    }

    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isLandscapeVideo by remember(videoFilePath, aspectMode) {
        mutableStateOf(aspectMode == RecordingAspectMode.WIDE_16_9)
    }

    val exoPlayer = remember(videoFilePath) {
        ExoPlayer.Builder(context).build().apply {
            val uri = Uri.fromFile(File(videoFilePath))
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
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
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    isLandscapeVideo = videoSize.width > videoSize.height
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            val d = exoPlayer.duration
            if (d > 0L) durationMs = d
            delay(120L)
        }
    }

    val targetResizeMode = if (isLandscapeVideo) {
        AspectRatioFrameLayout.RESIZE_MODE_FIT
    } else {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("take_review_screen"),
    ) {
        // Adaptive video player: FIT for 16:9 widescreen takes, ZOOM for vertical 9:16 takes
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = targetResizeMode
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
                if (view.resizeMode != targetResizeMode) {
                    view.resizeMode = targetResizeMode
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                    } else {
                        exoPlayer.play()
                    }
                },
        )

        // Centered Pause Indicator
        AnimatedVisibility(
            visible = !isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.56f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.review_play_pause),
                    tint = Color.White,
                    modifier = Modifier.size(38.dp),
                )
            }
        }

        // Top Review Header Gradient & Script/Date Metadata Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.68f), Color.Transparent)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 12.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(DarkCharcoalBg.copy(alpha = 0.68f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("review_metadata_pill"),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = previewMetadata.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isLandscapeVideo) {
                        Text(
                            text = stringResource(R.string.review_wide_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioAmber,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(StudioAmber.copy(alpha = 0.16f))
                                .border(1.dp, StudioAmber.copy(alpha = 0.45f), CircleShape)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                .testTag("review_wide_badge"),
                        )
                    }
                    if (isEdited) {
                        Text(
                            text = stringResource(R.string.review_edited_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF161100),
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(StudioAmber)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                .testTag("review_edited_badge"),
                        )
                    }
                }
                Text(
                    text = previewMetadata.formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioAmber.copy(alpha = 0.92f),
                    maxLines = 1,
                )
            }

            IconButton(
                onClick = {
                    isMuted = !isMuted
                    exoPlayer.volume = if (isMuted) 0f else 1f
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(DarkCharcoalBg.copy(alpha = 0.65f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                    .testTag("review_mute_button"),
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

        // Bottom Thumb-Zone Scrubber & Actions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.78f),
                            Color.Black.copy(alpha = 0.94f),
                        )
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Save confirmation or error banner
            when (saveState) {
                is SaveState.Saved -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(VuGreen.copy(alpha = 0.18f))
                            .border(1.dp, VuGreen.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("review_saved_banner"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = VuGreen,
                            modifier = Modifier.size(18.dp),
                        )
                        Column {
                            Text(
                                text = "${stringResource(R.string.review_saved_banner)} · ${saveState.metadata.title}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${saveState.metadata.formattedDate} · ${saveState.metadata.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                is SaveState.Error -> {
                    Text(
                        text = saveState.message.ifBlank { stringResource(R.string.review_save_error) },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFB4B6),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF4A1517).copy(alpha = 0.85f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }

                else -> Unit
            }

            if (shareState is ShareState.Error) {
                Text(
                    text = shareState.message.ifBlank { stringResource(R.string.review_share_error) },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFFB4B6),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF4A1517).copy(alpha = 0.85f))
                        .clickable { onDismissShareError() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("review_share_error_banner"),
                )
            }

            // Playback Scrubber Bar
            if (durationMs > 0L) {
                Column {
                    Slider(
                        value = currentPositionMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                        onValueChange = { pos ->
                            currentPositionMs = pos.toLong()
                            exoPlayer.seekTo(pos.toLong())
                        },
                        valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = StudioAmber,
                            activeTrackColor = StudioAmber,
                            inactiveTrackColor = Color.White.copy(alpha = 0.28f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = ScriptMetricsCalculator.formatTimer(currentPositionMs / 1000L),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                        Text(
                            text = ScriptMetricsCalculator.formatTimer(durationMs / 1000L),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            // Secondary Action Row: Retake | Trim | Edit Script
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onRetake,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color.White.copy(alpha = 0.28f),
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = DarkCharcoalBg.copy(alpha = 0.60f),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("review_retake_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Replay,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.review_retake),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }

                // Dedicated Trim & Cut Editor Button
                OutlinedButton(
                    onClick = onTrimTake,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isEdited) StudioAmber else StudioAmber.copy(alpha = 0.72f),
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isEdited) StudioAmber.copy(alpha = 0.24f) else DarkCharcoalBg.copy(alpha = 0.68f),
                        contentColor = StudioAmber,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("review_trim_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCut,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.review_trim),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }

                OutlinedButton(
                    onClick = onEditScript,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color.White.copy(alpha = 0.24f),
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = DarkCharcoalBg.copy(alpha = 0.60f),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("review_edit_script_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.review_edit_script),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }

            // Primary Export Row: Save Video | System Share Sheet
            val isSaved = saveState is SaveState.Saved
            val isSaving = saveState is SaveState.Saving
            val isPreparingShare = shareState is ShareState.Preparing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onSaveToPhone,
                    enabled = !isSaving && !isSaved,
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSaved) VuGreen else StudioAmber,
                        contentColor = Color(0xFF161100),
                        disabledContainerColor = if (isSaved) VuGreen else StudioAmber.copy(alpha = 0.55f),
                        disabledContentColor = Color(0xFF161100),
                    ),
                    modifier = Modifier
                        .weight(1.42f)
                        .height(54.dp)
                        .testTag("review_save_video_button"),
                ) {
                    when {
                        isSaving -> {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = Color(0xFF161100),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.review_saving),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }

                        isSaved -> {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.review_saved),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }

                        else -> {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.review_save),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        }
                        onShareTake()
                    },
                    enabled = !isPreparingShare,
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color.White.copy(alpha = 0.34f),
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = DarkCharcoalBg.copy(alpha = 0.72f),
                        contentColor = Color.White,
                        disabledContainerColor = DarkCharcoalBg.copy(alpha = 0.45f),
                        disabledContentColor = Color.White.copy(alpha = 0.65f),
                    ),
                    modifier = Modifier
                        .weight(0.88f)
                        .height(54.dp)
                        .testTag("review_share_video_button"),
                ) {
                    if (isPreparingShare) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = StudioAmber,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.review_share),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
