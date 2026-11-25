package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.preferences.RimLightTone
import com.example.ui.theme.DarkCharcoalBg
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.RimLightCoolColor
import com.example.ui.theme.RimLightNeutralColor
import com.example.ui.theme.RimLightWarmColor
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.VuAmber
import com.example.ui.theme.VuGreen
import com.example.ui.theme.VuRed

/**
 * Luminous perimeter Screen Rim Light usable in BOTH Dark and Light modes.
 * Casts soft facial fill light around all four screen edges without obscuring the
 * center camera feed or lens-aligned teleprompter text.
 */
@Composable
fun ScreenRimLightOverlay(
    enabled: Boolean,
    tone: RimLightTone,
    intensity: Float,
    modifier: Modifier = Modifier,
) {
    val animatedAlpha by animateFloatAsState(
        targetValue = if (enabled) intensity.coerceIn(0.35f, 1f) else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "rim_light_alpha",
    )

    if (animatedAlpha <= 0.01f) return

    val baseColor = when (tone) {
        RimLightTone.WARM -> RimLightWarmColor
        RimLightTone.NEUTRAL -> RimLightNeutralColor
        RimLightTone.COOL -> RimLightCoolColor
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_rim_light_overlay"),
    ) {
        val solidBand = (14.dp.toPx() + 20.dp.toPx() * animatedAlpha)
        val glowSpread = solidBand * 2.15f
        val peakColor = baseColor.copy(alpha = (0.85f + 0.15f * animatedAlpha).coerceAtMost(1f))
        val midColor = baseColor.copy(alpha = 0.42f * animatedAlpha)

        // Top luminous fill bar + soft inner diffusion
        drawRect(
            brush = Brush.verticalGradient(
                0f to peakColor,
                0.42f to peakColor,
                0.72f to midColor,
                1f to Color.Transparent,
                startY = 0f,
                endY = glowSpread,
            ),
            topLeft = Offset.Zero,
            size = Size(size.width, glowSpread),
        )

        // Bottom luminous fill bar + soft inner diffusion
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.28f to midColor,
                0.58f to peakColor,
                1f to peakColor,
                startY = size.height - glowSpread,
                endY = size.height,
            ),
            topLeft = Offset(0f, size.height - glowSpread),
            size = Size(size.width, glowSpread),
        )

        // Left luminous fill bar + soft inner diffusion
        drawRect(
            brush = Brush.horizontalGradient(
                0f to peakColor,
                0.44f to peakColor,
                0.75f to midColor,
                1f to Color.Transparent,
                startX = 0f,
                endX = glowSpread,
            ),
            topLeft = Offset.Zero,
            size = Size(glowSpread, size.height),
        )

        // Right luminous fill bar + soft inner diffusion
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.25f to midColor,
                0.56f to peakColor,
                1f to peakColor,
                startX = size.width - glowSpread,
                endX = size.width,
            ),
            topLeft = Offset(size.width - glowSpread, 0f),
            size = Size(glowSpread, size.height),
        )
    }
}

/**
 * Optional rule-of-thirds framing guide.
 * In vertical 9:16 mode, includes the right-edge TikTok/Reels safe-zone indicator.
 * In wide 16:9 mode, adapts cleanly to widescreen rule-of-thirds + broadcast corner ticks.
 */
@Composable
fun FramingGridOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
    isWideMode: Boolean = false,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(180)),
        modifier = modifier.fillMaxSize(),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("framing_grid_overlay"),
        ) {
            val lineColor = Color.White.copy(alpha = 0.24f)
            val strokePx = 1.dp.toPx()

            // Vertical rule of thirds
            val thirdW = size.width / 3f
            drawLine(lineColor, Offset(thirdW, 0f), Offset(thirdW, size.height), strokeWidth = strokePx)
            drawLine(lineColor, Offset(thirdW * 2f, 0f), Offset(thirdW * 2f, size.height), strokeWidth = strokePx)

            // Horizontal rule of thirds
            val thirdH = size.height / 3f
            drawLine(lineColor, Offset(0f, thirdH), Offset(size.width, thirdH), strokeWidth = strokePx)
            drawLine(lineColor, Offset(0f, thirdH * 2f), Offset(size.width, thirdH * 2f), strokeWidth = strokePx)

            if (!isWideMode) {
                // Vertical 9:16 TikTok/Reels right-edge safe-zone dashed line
                val safeRightX = size.width * 0.86f
                drawLine(
                    color = StudioAmber.copy(alpha = 0.30f),
                    start = Offset(safeRightX, size.height * 0.36f),
                    end = Offset(safeRightX, size.height * 0.80f),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f),
                )
            } else {
                // Subtle broadcast action-safe corner brackets for 16:9 framing
                val insetX = size.width * 0.05f
                val insetY = size.height * 0.07f
                val bracket = 12.dp.toPx()
                val bracketColor = StudioAmber.copy(alpha = 0.42f)
                val bStroke = 1.25.dp.toPx()
                // Top-left
                drawLine(bracketColor, Offset(insetX, insetY), Offset(insetX + bracket, insetY), bStroke)
                drawLine(bracketColor, Offset(insetX, insetY), Offset(insetX, insetY + bracket), bStroke)
                // Top-right
                drawLine(bracketColor, Offset(size.width - insetX, insetY), Offset(size.width - insetX - bracket, insetY), bStroke)
                drawLine(bracketColor, Offset(size.width - insetX, insetY), Offset(size.width - insetX, insetY + bracket), bStroke)
                // Bottom-left
                drawLine(bracketColor, Offset(insetX, size.height - insetY), Offset(insetX + bracket, size.height - insetY), bStroke)
                drawLine(bracketColor, Offset(insetX, size.height - insetY), Offset(insetX, size.height - insetY - bracket), bStroke)
                // Bottom-right
                drawLine(bracketColor, Offset(size.width - insetX, size.height - insetY), Offset(size.width - insetX - bracket, size.height - insetY), bStroke)
                drawLine(bracketColor, Offset(size.width - insetX, size.height - insetY), Offset(size.width - insetX, size.height - insetY - bracket), bStroke)
            }

            // Center crosshair reticle for head-and-shoulders framing
            val cx = size.width / 2f
            val cy = size.height / 2f
            val reticle = 12.dp.toPx()
            drawLine(StudioAmber.copy(alpha = 0.45f), Offset(cx - reticle, cy), Offset(cx + reticle, cy), strokePx * 1.2f)
            drawLine(StudioAmber.copy(alpha = 0.45f), Offset(cx, cy - reticle), Offset(cx, cy + reticle), strokePx * 1.2f)
        }
    }
}

/**
 * Dynamic, minimalist live audio level indicator rendered as a series of subtle
 * animated vertical bars centered near the top of the studio viewport.
 */
@Composable
fun LiveAudioLevelMeter(
    bars: List<Float>,
    isMicActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val safeBars = if (bars.isEmpty()) List(7) { 0f } else bars
    val peakLevel = safeBars.maxOrNull() ?: 0f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(CircleShape)
            .background(DarkCharcoalBg.copy(alpha = 0.62f))
            .border(
                width = 1.dp,
                color = if (isMicActive && peakLevel > 0.08f) {
                    StudioAmber.copy(alpha = 0.42f)
                } else {
                    Color.White.copy(alpha = 0.14f)
                },
                shape = CircleShape,
            )
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .testTag("live_audio_level_meter"),
    ) {
        Icon(
            imageVector = if (isMicActive) Icons.Rounded.Mic else Icons.Rounded.MicOff,
            contentDescription = stringResource(R.string.studio_mic_ready),
            tint = when {
                !isMicActive -> Color.White.copy(alpha = 0.38f)
                peakLevel > 0.08f -> StudioAmber
                else -> Color.White.copy(alpha = 0.78f)
            },
            modifier = Modifier.size(14.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(20.dp),
        ) {
            safeBars.forEachIndexed { index, rawLevel ->
                val clamped = if (isMicActive) rawLevel.coerceIn(0f, 1f) else 0f
                val targetHeight = (3.5f + clamped * 16.5f).dp
                val animatedHeight by animateDpAsState(
                    targetValue = targetHeight,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "audio_bar_height_$index",
                )

                val barColor = when {
                    !isMicActive -> Color.White.copy(alpha = 0.18f)
                    clamped > 0.82f -> VuRed
                    clamped > 0.42f -> VuAmber
                    clamped > 0.06f -> Color(0xFFF6F6F8).copy(alpha = 0.92f)
                    else -> VuGreen.copy(alpha = 0.55f)
                }

                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(animatedHeight)
                        .clip(CircleShape)
                        .background(barColor),
                )
            }
        }
    }
}

/**
 * Interactive 3-second countdown anchored cleanly in the lower camera viewport so the
 * top teleprompter script and first line remain 100% bright and unobstructed during countdown.
 */
@Composable
fun CountdownOverlay(
    secondsRemaining: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    topPadding: Dp = 310.dp,
    compact: Boolean = false,
) {
    val circleSize = if (compact) 88.dp else 124.dp
    val digitFontSize = if (compact) 44.sp else 60.sp
    val spacerHeight = if (compact) 10.dp else 16.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onCancel)
            .padding(top = topPadding)
            .testTag("countdown_overlay"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(circleSize),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = DarkCharcoalBg.copy(alpha = 0.62f),
                        radius = size.minDimension / 2f,
                    )
                    drawCircle(
                        color = StudioAmber.copy(alpha = 0.16f),
                        radius = size.minDimension / 2f,
                    )
                    drawCircle(
                        color = StudioAmber,
                        radius = size.minDimension / 2f - 3.dp.toPx(),
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }

                AnimatedContent(
                    targetState = secondsRemaining,
                    transitionSpec = {
                        (scaleIn(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(180)))
                            .togetherWith(scaleOut(tween(160)) + fadeOut(tween(160)))
                    },
                    label = "countdown_digit",
                ) { digit ->
                    Text(
                        text = digit.toString(),
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = digitFontSize,
                        color = Color.White,
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacerHeight))

            Text(
                text = stringResource(R.string.studio_countdown_cancel),
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.90f),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(DarkCharcoalBg.copy(alpha = 0.72f))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
