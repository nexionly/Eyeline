package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ScriptMetricsCalculator
import com.example.ui.theme.DarkCharcoalBg
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.StudioAmber

/**
 * Lens-aligned vertical teleprompter overlay with:
 * - Highlighted warm-amber eye-line focus band directly beneath the front camera lens
 * - Soft top & bottom alpha edge fade
 * - 60fps frame-clocked scrolling with smooth fractional accumulation
 * - Single-tap pause/resume and direct vertical drag-to-scrub
 */
@Composable
fun TeleprompterOverlay(
    scriptText: String,
    fontSizeSp: Float,
    scrollSpeedLevel: Float,
    backgroundOpacity: Float,
    isAutoScrolling: Boolean,
    isRecording: Boolean,
    isScrollPaused: Boolean,
    scrollResetCounter: Int,
    onTapTogglePause: () -> Unit,
    modifier: Modifier = Modifier,
    overlayHeight: Dp = 232.dp,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    // Snap scroll cleanly to top whenever reset or retake is triggered
    LaunchedEffect(scrollResetCounter) {
        if (scrollResetCounter > 0) {
            scrollState.scrollTo(0)
        }
    }

    // Silky 60fps frame-clocked continuous scroll that yields gracefully while user drags
    LaunchedEffect(isAutoScrolling, scrollSpeedLevel, fontSizeSp) {
        if (!isAutoScrolling) return@LaunchedEffect
        var lastFrameTimeNanos = 0L
        var fractionalRemainder = 0f

        while (true) {
            withFrameNanos { frameTimeNanos ->
                if (lastFrameTimeNanos == 0L) {
                    lastFrameTimeNanos = frameTimeNanos
                    return@withFrameNanos
                }
                val dtSeconds = ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f)
                    .coerceIn(0f, 0.1f)
                lastFrameTimeNanos = frameTimeNanos

                if (!scrollState.isScrollInProgress && scrollState.value < scrollState.maxValue) {
                    val pxPerSec = ScriptMetricsCalculator.scrollPixelsPerSecond(
                        scrollSpeedLevel = scrollSpeedLevel,
                        fontSizeSp = fontSizeSp,
                        density = density.density,
                    )
                    val delta = pxPerSec * dtSeconds + fractionalRemainder
                    val wholePixels = delta.toInt()
                    fractionalRemainder = delta - wholePixels
                    if (wholePixels > 0) {
                        val target = (scrollState.value + wholePixels).coerceAtMost(scrollState.maxValue)
                        scrollState.dispatchRawDelta((target - scrollState.value).toFloat())
                    }
                }
            }
        }
    }

    val eyeLineTopOffset = 52.dp
    val eyeLineBandHeight = (fontSizeSp * 1.65f).coerceIn(46f, 78f).dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(overlayHeight)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DarkCharcoalBg.copy(alpha = (backgroundOpacity + 0.12f).coerceAtMost(0.95f)),
                        DarkCharcoalBg.copy(alpha = backgroundOpacity),
                        DarkCharcoalBg.copy(alpha = (backgroundOpacity * 0.72f).coerceAtLeast(0f)),
                    )
                )
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (backgroundOpacity > 0.1f) 0.10f else 0.05f),
                shape = RoundedCornerShape(22.dp),
            )
            .pointerInput(isRecording) {
                detectTapGestures(
                    onTap = {
                        if (isRecording) {
                            onTapTogglePause()
                        }
                    }
                )
            }
            .testTag("teleprompter_overlay_pane"),
    ) {
        // Highlighted Eye-Line Guide Band (anchors eyes right under the front camera lens)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val topPx = eyeLineTopOffset.toPx()
            val bandHeightPx = eyeLineBandHeight.toPx()
            val horizontalInset = 10.dp.toPx()

            // Subtle warm-amber translucent reading band
            drawRoundRect(
                color = StudioAmber.copy(alpha = 0.14f),
                topLeft = Offset(horizontalInset, topPx),
                size = Size(size.width - horizontalInset * 2f, bandHeightPx),
                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
            )

            // Crisp warm-amber left & right eye-line focus ticks
            val tickWidth = 3.dp.toPx()
            val tickHeight = bandHeightPx * 0.56f
            val tickTop = topPx + (bandHeightPx - tickHeight) / 2f
            drawRoundRect(
                color = StudioAmber.copy(alpha = 0.92f),
                topLeft = Offset(horizontalInset, tickTop),
                size = Size(tickWidth, tickHeight),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            )
            drawRoundRect(
                color = StudioAmber.copy(alpha = 0.92f),
                topLeft = Offset(size.width - horizontalInset - tickWidth, tickTop),
                size = Size(tickWidth, tickHeight),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            )
        }

        // Scrollable script text with smooth top & bottom alpha edge fade
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.13f to Color.Black,
                            0.76f to Color.Black,
                            1.0f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp),
            ) {
                // Top spacer aligns line 1 squarely inside the bright eye-line focus band
                Spacer(modifier = Modifier.height(eyeLineTopOffset + 8.dp))

                Text(
                    text = scriptText.ifBlank { "No script text yet. Tap back to add lines." },
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.42f).sp,
                    color = Color(0xFFF9F9FB),
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Bottom spacer lets the final line glide cleanly into the eye-line band
                Spacer(modifier = Modifier.height(overlayHeight - eyeLineTopOffset))
            }
        }

        // Subtle paused pill indicator while recording
        AnimatedVisibility(
            visible = isRecording && isScrollPaused,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(StudioAmber.copy(alpha = 0.92f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Pause,
                    contentDescription = null,
                    tint = Color(0xFF1B1200),
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(R.string.studio_scroll_paused),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF1B1200),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
