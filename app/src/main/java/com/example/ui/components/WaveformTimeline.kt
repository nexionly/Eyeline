package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.capture.TimelineEditState
import com.example.capture.WaveformLoadState
import com.example.ui.theme.DarkCharcoalContainer
import com.example.ui.theme.DarkCharcoalOutline
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioAmberBright
import com.example.ui.theme.StudioAmberOnPrimary
import com.example.ui.theme.TallyCrimson
import kotlin.math.abs

private enum class TimelineDragTarget {
    LEFT_HANDLE,
    RIGHT_HANDLE,
    PLAYHEAD_OR_TAP,
}

/**
 * Interactive audio waveform timeline for Eyeline's Trim & Cut Editor.
 * Supports dragging left/right trim brackets, scrubbing the playhead across audio peaks,
 * and tapping individual split segments to select them for Cut or Restore.
 */
@Composable
fun WaveformTimeline(
    editState: TimelineEditState,
    waveformState: WaveformLoadState,
    playheadMs: Long,
    onTrimDragStart: () -> Unit,
    onTrimStartChange: (Long) -> Unit,
    onTrimEndChange: (Long) -> Unit,
    onPlayheadScrub: (Long) -> Unit,
    onSegmentTap: (Long) -> Unit,
    onScrubbingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    val currentEditState by rememberUpdatedState(editState)
    val currentOnTrimDragStart by rememberUpdatedState(onTrimDragStart)
    val currentOnTrimStartChange by rememberUpdatedState(onTrimStartChange)
    val currentOnTrimEndChange by rememberUpdatedState(onTrimEndChange)
    val currentOnPlayheadScrub by rememberUpdatedState(onPlayheadScrub)
    val currentOnSegmentTap by rememberUpdatedState(onSegmentTap)
    val currentOnScrubbingChanged by rememberUpdatedState(onScrubbingChanged)

    val shimmerTransition = rememberInfiniteTransition(label = "waveform_shimmer")
    val shimmerPhase by shimmerTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "waveform_shimmer_phase",
    )

    val handleWidthDp = 16.dp
    val handleHitRadiusPx = with(density) { 28.dp.toPx() }
    val tapSlopPx = with(density) { 10.dp.toPx() }
    val handleWidthPx = with(density) { handleWidthDp.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(92.dp)
            .testTag("trim_waveform_timeline"),
    ) {
        val totalWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val trackLeftPx = handleWidthPx
        val trackRightPx = (totalWidthPx - handleWidthPx).coerceAtLeast(trackLeftPx + 1f)
        val trackSpanPx = (trackRightPx - trackLeftPx).coerceAtLeast(1f)

        fun msToX(ms: Long, durationMs: Long): Float {
            val safeDur = durationMs.coerceAtLeast(1L).toFloat()
            val fraction = (ms.toFloat() / safeDur).coerceIn(0f, 1f)
            return trackLeftPx + fraction * trackSpanPx
        }

        fun xToMs(x: Float, durationMs: Long): Long {
            val fraction = ((x - trackLeftPx) / trackSpanPx).coerceIn(0f, 1f)
            return (fraction * durationMs.coerceAtLeast(1L)).toLong()
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(trackSpanPx, handleWidthPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val stateAtDown = currentEditState
                        val dur = stateAtDown.durationMs.coerceAtLeast(1L)

                        val leftHandleX = msToX(stateAtDown.trimStartMs, dur) - handleWidthPx * 0.5f
                        val rightHandleX = msToX(stateAtDown.trimEndMs, dur) + handleWidthPx * 0.5f
                        val distLeft = abs(down.position.x - leftHandleX)
                        val distRight = abs(down.position.x - rightHandleX)

                        val target = when {
                            distLeft <= handleHitRadiusPx && distLeft <= distRight ->
                                TimelineDragTarget.LEFT_HANDLE
                            distRight <= handleHitRadiusPx ->
                                TimelineDragTarget.RIGHT_HANDLE
                            else ->
                                TimelineDragTarget.PLAYHEAD_OR_TAP
                        }

                        var totalMovePx = 0f
                        currentOnScrubbingChanged(true)

                        when (target) {
                            TimelineDragTarget.LEFT_HANDLE -> {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnTrimDragStart()
                                down.consume()
                            }
                            TimelineDragTarget.RIGHT_HANDLE -> {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnTrimDragStart()
                                down.consume()
                            }
                            TimelineDragTarget.PLAYHEAD_OR_TAP -> {
                                currentOnPlayheadScrub(xToMs(down.position.x, dur))
                                down.consume()
                            }
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                                ?: break

                            if (!change.pressed) {
                                change.consume()
                                break
                            }

                            val delta = change.positionChange()
                            totalMovePx += abs(delta.x) + abs(delta.y)
                            val latestDur = currentEditState.durationMs.coerceAtLeast(1L)

                            when (target) {
                                TimelineDragTarget.LEFT_HANDLE -> {
                                    val newMs = xToMs(change.position.x + handleWidthPx * 0.5f, latestDur)
                                    currentOnTrimStartChange(newMs)
                                }
                                TimelineDragTarget.RIGHT_HANDLE -> {
                                    val newMs = xToMs(change.position.x - handleWidthPx * 0.5f, latestDur)
                                    currentOnTrimEndChange(newMs)
                                }
                                TimelineDragTarget.PLAYHEAD_OR_TAP -> {
                                    currentOnPlayheadScrub(xToMs(change.position.x, latestDur))
                                }
                            }
                            change.consume()
                        }

                        if (target == TimelineDragTarget.PLAYHEAD_OR_TAP && totalMovePx < tapSlopPx) {
                            val tapMs = xToMs(down.position.x, currentEditState.durationMs)
                            currentOnSegmentTap(tapMs)
                        }
                        currentOnScrubbingChanged(false)
                    }
                },
        ) {
            val dur = editState.durationMs.coerceAtLeast(1L)
            val topInset = 12.dp.toPx()
            val bottomInset = 6.dp.toPx()
            val trackTop = topInset
            val trackBottom = (size.height - bottomInset).coerceAtLeast(trackTop + 12f)
            val trackHeight = trackBottom - trackTop
            val cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())

            // Base timeline track container
            drawRoundRect(
                color = DarkCharcoalContainer,
                topLeft = Offset(trackLeftPx, trackTop),
                size = Size(trackSpanPx, trackHeight),
                cornerRadius = cornerRadius,
            )

            // Segment backgrounds, diagonal cut hatching, selection framing, and split hairlines
            editState.segments.forEachIndexed { index, seg ->
                val segLeft = msToX(seg.startMs, dur)
                val segRight = msToX(seg.endMs, dur)
                val segWidth = (segRight - segLeft).coerceAtLeast(0f)
                if (segWidth <= 0.5f) return@forEachIndexed

                val isSelected = seg.id == editState.selectedSegmentId

                if (seg.isRemoved) {
                    drawRect(
                        color = Color(0xFF0C0D10).copy(alpha = 0.68f),
                        topLeft = Offset(segLeft, trackTop),
                        size = Size(segWidth, trackHeight),
                    )
                    // Subtle diagonal hatching across cut regions
                    clipRect(
                        left = segLeft,
                        top = trackTop,
                        right = segRight,
                        bottom = trackBottom,
                    ) {
                        val step = 10.dp.toPx()
                        val hatchColor = if (isSelected) {
                            TallyCrimson.copy(alpha = 0.36f)
                        } else {
                            Color.White.copy(alpha = 0.09f)
                        }
                        var x = segLeft - trackHeight
                        while (x < segRight + trackHeight) {
                            drawLine(
                                color = hatchColor,
                                start = Offset(x, trackBottom),
                                end = Offset(x + trackHeight, trackTop),
                                strokeWidth = 1.2.dp.toPx(),
                            )
                            x += step
                        }
                    }
                } else {
                    drawRect(
                        color = StudioAmber.copy(alpha = if (isSelected) 0.16f else 0.06f),
                        topLeft = Offset(segLeft, trackTop),
                        size = Size(segWidth, trackHeight),
                    )
                }

                // Selected segment top & bottom accent bars
                if (isSelected) {
                    val accent = if (seg.isRemoved) TallyCrimson.copy(alpha = 0.85f) else StudioAmber
                    drawLine(
                        color = accent,
                        start = Offset(segLeft, trackTop + 1.5.dp.toPx()),
                        end = Offset(segRight, trackTop + 1.5.dp.toPx()),
                        strokeWidth = 2.5.dp.toPx(),
                    )
                    drawLine(
                        color = accent,
                        start = Offset(segLeft, trackBottom - 1.5.dp.toPx()),
                        end = Offset(segRight, trackBottom - 1.5.dp.toPx()),
                        strokeWidth = 2.5.dp.toPx(),
                    )
                }

                // Split boundary divider between segments
                if (index > 0) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.55f),
                        start = Offset(segLeft, trackTop + 2.dp.toPx()),
                        end = Offset(segLeft, trackBottom - 2.dp.toPx()),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }
            }

            // Audio Waveform Bars
            val barCount = when (waveformState) {
                is WaveformLoadState.Ready -> waveformState.amplitudes.size.coerceAtLeast(1)
                else -> 64
            }
            val slotWidth = trackSpanPx / barCount.toFloat()
            val barWidth = (slotWidth * 0.58f).coerceIn(1.5.dp.toPx(), 4.dp.toPx())
            val maxBarHeight = trackHeight * 0.72f
            val centerY = trackTop + trackHeight * 0.5f

            for (i in 0 until barCount) {
                val barCenterX = trackLeftPx + (i + 0.5f) * slotWidth
                val barMs = ((i + 0.5f) / barCount.toFloat() * dur).toLong()
                val seg = editState.segmentAt(barMs)
                val isRemoved = seg?.isRemoved == true
                val isSelected = seg?.id == editState.selectedSegmentId

                val normalizedAmp = when (waveformState) {
                    is WaveformLoadState.Ready -> waveformState.amplitudes[i].coerceIn(0.06f, 1f)
                    is WaveformLoadState.Loading -> {
                        val wave = kotlin.math.sin((i / 8f) + shimmerPhase * 6.283f)
                        (0.24f + 0.16f * wave).coerceIn(0.08f, 0.52f)
                    }
                    is WaveformLoadState.Unavailable -> {
                        if (i % 4 == 0) 0.22f else 0.14f
                    }
                }

                val barColor = when {
                    waveformState is WaveformLoadState.Loading ->
                        Color.White.copy(alpha = 0.24f)
                    isRemoved ->
                        Color(0xFF6E727C).copy(alpha = 0.30f)
                    isSelected ->
                        StudioAmberBright
                    else ->
                        StudioAmber.copy(alpha = 0.85f)
                }

                val barHalfHeight = (normalizedAmp * maxBarHeight * 0.5f).coerceAtLeast(2.dp.toPx())
                drawLine(
                    color = barColor,
                    start = Offset(barCenterX, centerY - barHalfHeight),
                    end = Offset(barCenterX, centerY + barHalfHeight),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round,
                )
            }

            // Subtle outer track border
            drawRoundRect(
                color = DarkCharcoalOutline,
                topLeft = Offset(trackLeftPx, trackTop),
                size = Size(trackSpanPx, trackHeight),
                cornerRadius = cornerRadius,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
            )

            // Left & Right Warm Amber Trim Brackets
            val trimLeftX = msToX(editState.trimStartMs, dur)
            val trimRightX = msToX(editState.trimEndMs, dur)

            // Left bracket sits flush to the left of trimLeftX
            drawRoundRect(
                color = StudioAmber,
                topLeft = Offset(trimLeftX - handleWidthPx, trackTop),
                size = Size(handleWidthPx, trackHeight),
                cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()),
            )
            // Grip lines inside left bracket
            val gripHalfHeight = trackHeight * 0.18f
            val leftGripX = trimLeftX - handleWidthPx * 0.5f
            drawLine(
                color = StudioAmberOnPrimary,
                start = Offset(leftGripX - 1.8.dp.toPx(), centerY - gripHalfHeight),
                end = Offset(leftGripX - 1.8.dp.toPx(), centerY + gripHalfHeight),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = StudioAmberOnPrimary,
                start = Offset(leftGripX + 1.8.dp.toPx(), centerY - gripHalfHeight),
                end = Offset(leftGripX + 1.8.dp.toPx(), centerY + gripHalfHeight),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )

            // Right bracket sits flush to the right of trimRightX
            drawRoundRect(
                color = StudioAmber,
                topLeft = Offset(trimRightX, trackTop),
                size = Size(handleWidthPx, trackHeight),
                cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()),
            )
            val rightGripX = trimRightX + handleWidthPx * 0.5f
            drawLine(
                color = StudioAmberOnPrimary,
                start = Offset(rightGripX - 1.8.dp.toPx(), centerY - gripHalfHeight),
                end = Offset(rightGripX - 1.8.dp.toPx(), centerY + gripHalfHeight),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = StudioAmberOnPrimary,
                start = Offset(rightGripX + 1.8.dp.toPx(), centerY - gripHalfHeight),
                end = Offset(rightGripX + 1.8.dp.toPx(), centerY + gripHalfHeight),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )

            // High-Contrast White Playhead Needle + Top Thumb Cap
            val playheadX = msToX(playheadMs, dur)
            drawLine(
                color = Color.Black.copy(alpha = 0.45f),
                start = Offset(playheadX, trackTop - 2.dp.toPx()),
                end = Offset(playheadX, trackBottom + 2.dp.toPx()),
                strokeWidth = 4.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White,
                start = Offset(playheadX, trackTop - 3.dp.toPx()),
                end = Offset(playheadX, trackBottom + 2.dp.toPx()),
                strokeWidth = 2.2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = Color.White,
                radius = 5.5.dp.toPx(),
                center = Offset(playheadX, trackTop - 4.dp.toPx()),
            )
            drawCircle(
                color = StudioAmber,
                radius = 2.4.dp.toPx(),
                center = Offset(playheadX, trackTop - 4.dp.toPx()),
            )
        }
    }
}
