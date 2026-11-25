package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.CropLandscape
import androidx.compose.material.icons.rounded.CropPortrait
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.capture.AspectCropMath
import com.example.data.preferences.RecordingAspectMode
import com.example.ui.theme.DarkCharcoalBg
import com.example.ui.theme.DarkCharcoalContainer
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.TallyCrimson
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Compact one-tap aspect ratio pill (`9:16` / `16:9`) for the top studio toolbar.
 * Maintains a full 48dp minimum accessible touch target while staying visually compact.
 */
@Composable
fun AspectModeTogglePill(
    mode: RecordingAspectMode,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isWide = mode == RecordingAspectMode.WIDE_16_9
    val bgColor by animateColorAsState(
        targetValue = when {
            !enabled && isWide -> StudioAmber.copy(alpha = 0.34f)
            !enabled -> DarkCharcoalBg.copy(alpha = 0.28f)
            isWide -> StudioAmber
            else -> DarkCharcoalBg.copy(alpha = 0.65f)
        },
        animationSpec = tween(180),
        label = "aspect_pill_bg",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            !enabled -> Color.White.copy(alpha = 0.48f)
            isWide -> Color(0xFF1B1200)
            else -> Color.White
        },
        animationSpec = tween(180),
        label = "aspect_pill_fg",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> Color.White.copy(alpha = 0.10f)
            isWide -> StudioAmber
            else -> Color.White.copy(alpha = 0.18f)
        },
        animationSpec = tween(180),
        label = "aspect_pill_border",
    )

    val cdText = stringResource(R.string.studio_aspect_toggle_cd)

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = cdText }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .testTag("studio_aspect_toggle_pill"),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(CircleShape)
                .background(bgColor)
                .border(1.dp, borderColor, CircleShape)
                .padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
            Icon(
                imageVector = if (isWide) Icons.Rounded.CropLandscape else Icons.Rounded.CropPortrait,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(
                    if (isWide) R.string.studio_aspect_wide_label else R.string.studio_aspect_vertical_label
                ),
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = contentColor,
            )
        }
    }
}

/**
 * Interactive 16:9 widescreen viewfinder card rendered directly beneath the lens-aligned
 * teleprompter when holding the phone vertically in Wide 16:9 mode.
 *
 * Displays the exact horizontal 16:9 slice of the live vertical CameraX feed using matching
 * [AspectCropMath] translation coordinates, supports direct vertical drag-to-reframe with a
 * haptic center detent, double-tap to re-center, clipped 16:9 framing grid, and tally border.
 */
@Composable
fun Wide16x9ViewfinderCard(
    cropOffsetY: Float,
    showFramingGrid: Boolean,
    canReframe: Boolean,
    isRecording: Boolean,
    countdownSeconds: Int?,
    onViewportBoundsChanged: (Rect) -> Unit,
    onOffsetDragLive: (Float) -> Unit,
    onOffsetDragCommit: (Float) -> Unit,
    onResetCenter: () -> Unit,
    onTapDuringRecording: () -> Unit,
    onCancelCountdown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    var isDragging by remember { mutableStateOf(false) }
    var showPositionBadge by remember { mutableStateOf(false) }
    var dragOffsetState by remember { mutableFloatStateOf(cropOffsetY) }

    // Keep local drag tracker synced with DataStore updates when not actively dragging
    LaunchedEffect(cropOffsetY, isDragging) {
        if (!isDragging) {
            dragOffsetState = cropOffsetY
        }
    }

    // Auto-fade the framing offset pill shortly after drag ends
    LaunchedEffect(isDragging, cropOffsetY) {
        if (isDragging) {
            showPositionBadge = true
        } else if (showPositionBadge) {
            delay(1_250L)
            showPositionBadge = false
        }
    }

    val latestCanReframe by rememberUpdatedState(canReframe)
    val latestIsRecording by rememberUpdatedState(isRecording)
    val latestOnDragLive by rememberUpdatedState(onOffsetDragLive)
    val latestOnDragCommit by rememberUpdatedState(onOffsetDragCommit)
    val latestOnResetCenter by rememberUpdatedState(onResetCenter)
    val latestOnTapRecording by rememberUpdatedState(onTapDuringRecording)

    val borderColor by animateColorAsState(
        targetValue = when {
            isRecording -> TallyCrimson
            isDragging -> StudioAmber
            else -> Color.White.copy(alpha = 0.24f)
        },
        animationSpec = tween(160),
        label = "wide_frame_border",
    )
    val borderWidth = when {
        isRecording -> 2.5.dp
        isDragging -> 2.dp
        else -> 1.dp
    }

    val displayOffset = if (isDragging) dragOffsetState else cropOffsetY
    val offsetPercent = (abs(displayOffset) * 100f).roundToInt()
    val positionLabel = when {
        offsetPercent < 2 -> stringResource(R.string.studio_wide_center_label)
        displayOffset < 0f -> stringResource(R.string.studio_wide_up_format, offsetPercent)
        else -> stringResource(R.string.studio_wide_down_format, offsetPercent)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(AspectCropMath.WIDE_ASPECT_RATIO)
            .onGloballyPositioned { coords ->
                onViewportBoundsChanged(coords.boundsInRoot())
            }
            .clip(RoundedCornerShape(18.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(18.dp))
            .testTag("wide_16x9_viewfinder"),
        contentAlignment = Alignment.Center,
    ) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val maxTravelPx = AspectCropMath.previewMaxTravelPx(
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
        )

        // Transparent gesture surface directly over the permanently attached camera feed
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(maxTravelPx) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            if (latestCanReframe) {
                                isDragging = true
                            }
                        },
                        onDragEnd = {
                            if (isDragging) {
                                isDragging = false
                                val snapped = AspectCropMath.snapNearCenter(dragOffsetState)
                                dragOffsetState = snapped
                                latestOnDragCommit(snapped)
                            }
                        },
                        onDragCancel = {
                            if (isDragging) {
                                isDragging = false
                                val snapped = AspectCropMath.snapNearCenter(dragOffsetState)
                                dragOffsetState = snapped
                                latestOnDragCommit(snapped)
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (!latestCanReframe) return@detectVerticalDragGestures
                            change.consume()
                            val prev = dragOffsetState
                            val next = AspectCropMath.offsetAfterVerticalDrag(
                                currentOffsetY = prev,
                                dragDeltaPx = dragAmount,
                                maxTravelPx = maxTravelPx,
                            )
                            val crossedCenter =
                                (prev < 0f && next >= 0f) || (prev > 0f && next <= 0f)
                            if (crossedCenter) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            dragOffsetState = next
                            latestOnDragLive(next)
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (latestIsRecording) {
                                latestOnTapRecording()
                            }
                        },
                        onDoubleTap = {
                            if (latestCanReframe) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                dragOffsetState = 0f
                                showPositionBadge = true
                                latestOnResetCenter()
                            }
                        },
                    )
                },
        )

        // Clipped 16:9 Rule-of-Thirds + broadcast corner brackets
        FramingGridOverlay(
            visible = showFramingGrid,
            isWideMode = true,
            modifier = Modifier.fillMaxSize(),
        )

        // Subtle top-left monitor badge (`16:9 WIDE` / `16:9 REC`)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(CircleShape)
                .background(DarkCharcoalBg.copy(alpha = 0.64f))
                .border(
                    width = 1.dp,
                    color = if (isRecording) TallyCrimson.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.14f),
                    shape = CircleShape,
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(TallyCrimson),
                )
            }
            Text(
                text = stringResource(
                    if (isRecording) R.string.studio_wide_badge_recording else R.string.studio_wide_badge_preview
                ),
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.92f),
            )
        }

        // Right-edge sensor position track showing where the 16:9 band sits inside the 9:16 sensor
        AnimatedVisibility(
            visible = canReframe && (isDragging || abs(displayOffset) > 0.02f),
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(260)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp)
                .fillMaxHeight(0.68f)
                .width(6.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackRadius = size.width / 2f
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.45f),
                    cornerRadius = CornerRadius(trackRadius, trackRadius),
                )
                // Center tick
                val centerY = size.height / 2f
                drawLine(
                    color = Color.White.copy(alpha = 0.45f),
                    start = Offset(0f, centerY),
                    end = Offset(size.width, centerY),
                    strokeWidth = 1.dp.toPx(),
                )
                // Active 16:9 slice thumb proportional to (9/16)^2 ≈ 0.32 of full portrait height
                val thumbHeight = size.height * 0.32f
                val travel = (size.height - thumbHeight).coerceAtLeast(0f)
                val normalizedTop = ((displayOffset.coerceIn(-1f, 1f) + 1f) / 2f) * travel
                drawRoundRect(
                    color = if (isDragging) StudioAmber else Color.White.copy(alpha = 0.85f),
                    topLeft = Offset(0f, normalizedTop),
                    size = Size(size.width, thumbHeight),
                    cornerRadius = CornerRadius(trackRadius, trackRadius),
                )
            }
        }

        // Transient vertical offset pill while reframing
        AnimatedVisibility(
            visible = canReframe && showPositionBadge,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(240)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(DarkCharcoalBg.copy(alpha = 0.82f))
                    .border(1.dp, StudioAmber.copy(alpha = 0.55f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 5.dp)
                    .testTag("wide_crop_position_badge"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.SwapVert,
                    contentDescription = null,
                    tint = StudioAmber,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = positionLabel,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = Color.White,
                )
            }
        }

        // Countdown anchored neatly inside the 16:9 viewfinder so the teleprompter stays bright
        if (countdownSeconds != null) {
            CountdownOverlay(
                secondsRemaining = countdownSeconds,
                onCancel = onCancelCountdown,
                topPadding = 0.dp,
                compact = true,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Brief full-screen processing card shown right after stopping a 16:9 recording while
 * Media3 Transformer GPU-crops the clip into a native horizontal 16:9 MP4.
 */
@Composable
fun WideCropProcessingScreen(
    progress: Float,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val percent = (progress.coerceIn(0f, 1f) * 100f).roundToInt()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07080B))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp)
            .testTag("wide_crop_processing_screen"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(DarkCharcoalContainer)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(StudioAmber.copy(alpha = 0.16f))
                    .border(1.dp, StudioAmber.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CropLandscape,
                    contentDescription = null,
                    tint = StudioAmber,
                    modifier = Modifier.size(28.dp),
                )
            }

            Text(
                text = if (percent > 0) {
                    stringResource(R.string.studio_cropping_progress, percent)
                } else {
                    stringResource(R.string.studio_cropping_title)
                },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            Text(
                text = stringResource(R.string.studio_cropping_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.70f),
                textAlign = TextAlign.Center,
            )

            LinearProgressIndicator(
                progress = { progress.coerceIn(0.04f, 1f) },
                color = StudioAmber,
                trackColor = Color.White.copy(alpha = 0.12f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
            )

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("studio_crop_cancel_button"),
            ) {
                Text(
                    text = stringResource(R.string.studio_cropping_cancel),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.90f),
                )
            }
        }
    }
}
