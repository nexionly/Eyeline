package com.example.capture

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Normalized OpenGL NDC crop rectangle (`-1f..1f` where `bottom < top` and `left < right`)
 * paired with hardware-encoder-friendly even output dimensions for Media3 Transformer.
 */
data class WideCropSpec(
    val left: Float,
    val right: Float,
    val bottom: Float,
    val top: Float,
    val targetWidth: Int,
    val targetHeight: Int,
    val isNoOp: Boolean,
)

/**
 * Pure, unit-testable coordinate math keeping the live Compose 16:9 viewfinder preview
 * and the Media3 GPU `Crop` export 100% WYSIWYG-aligned.
 *
 * Convention for [offsetY]:
 * - `0f` = dead center of the vertical portrait camera feed
 * - `-1f` = top edge of the vertical portrait camera feed (tilted toward the top lens)
 * - `+1f` = bottom edge of the vertical portrait camera feed
 */
object AspectCropMath {

    const val WIDE_ASPECT_RATIO: Float = 16f / 9f
    const val PORTRAIT_SENSOR_HEIGHT_OVER_WIDTH: Float = 16f / 9f
    const val CENTER_SNAP_THRESHOLD: Float = 0.035f

    /**
     * Resolves upright display dimensions after accounting for MP4 rotation metadata
     * (`90` or `270` degrees swaps raw encoded width and height).
     */
    fun resolveDisplaySize(
        rawWidth: Int,
        rawHeight: Int,
        rotationDegrees: Int,
    ): Pair<Int, Int> {
        val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
        return if (normalizedRotation == 90 || normalizedRotation == 270) {
            rawHeight to rawWidth
        } else {
            rawWidth to rawHeight
        }
    }

    /**
     * Computes exact Media3 `Crop(left, right, bottom, top)` NDC coordinates and strictly even
     * output dimensions to produce a native 16:9 horizontal video from an upright portrait video.
     */
    fun computeWide16x9Crop(
        displayWidth: Int,
        displayHeight: Int,
        offsetY: Float,
    ): WideCropSpec {
        val safeW = displayWidth.coerceAtLeast(2)
        val safeH = displayHeight.coerceAtLeast(2)
        val clampedOffset = offsetY.coerceIn(-1f, 1f)

        val currentAspect = safeW.toFloat() / safeH.toFloat()
        // Already 16:9 or wider (e.g., landscape external sensor) — keep clean center/full crop
        if (currentAspect >= WIDE_ASPECT_RATIO - 0.01f) {
            val evenH = toEvenAtLeast2(safeH)
            val evenW = toEvenAtLeast2((evenH * WIDE_ASPECT_RATIO).roundToInt().coerceAtMost(safeW))
            if (abs(currentAspect - WIDE_ASPECT_RATIO) <= 0.02f) {
                return WideCropSpec(
                    left = -1f,
                    right = 1f,
                    bottom = -1f,
                    top = 1f,
                    targetWidth = toEvenAtLeast2(safeW),
                    targetHeight = evenH,
                    isNoOp = true,
                )
            }
            val halfSpanX = (evenW.toFloat() / safeW.toFloat()).coerceIn(0.1f, 1f)
            return WideCropSpec(
                left = -halfSpanX,
                right = halfSpanX,
                bottom = -1f,
                top = 1f,
                targetWidth = evenW,
                targetHeight = evenH,
                isNoOp = false,
            )
        }

        val evenWidth = toEvenAtLeast2(safeW)
        val idealHeight = (evenWidth / WIDE_ASPECT_RATIO).roundToInt().coerceIn(2, safeH)
        val evenHeight = toEvenAtLeast2(idealHeight)

        // Vertical height fraction of the portrait frame kept in the 16:9 slice.
        // In NDC [-1..+1] (total span 2.0), half-span equals this exact fraction!
        val halfSpanY = (evenHeight.toFloat() / safeH.toFloat()).coerceIn(0.05f, 1f)
        val maxShiftNdc = (1f - halfSpanY).coerceAtLeast(0f)

        // OpenGL NDC has +1 at TOP and -1 at BOTTOM.
        // offsetY = -1f (top of portrait) => centerNdc = +maxShiftNdc => top = +1f.
        // offsetY = +1f (bottom of portrait) => centerNdc = -maxShiftNdc => bottom = -1f.
        val centerNdc = -clampedOffset * maxShiftNdc
        val bottom = (centerNdc - halfSpanY).coerceIn(-1f, 1f - 0.01f)
        val top = (centerNdc + halfSpanY).coerceIn(bottom + 0.01f, 1f)

        return WideCropSpec(
            left = -1f,
            right = 1f,
            bottom = bottom,
            top = top,
            targetWidth = evenWidth,
            targetHeight = evenHeight,
            isNoOp = false,
        )
    }

    /**
     * Vertical pixel translation for the live Compose `PreviewView` inside a clipped 16:9 viewport.
     * Matches [computeWide16x9Crop] sign convention:
     * - `offsetY = -1f` translates the portrait preview DOWN (`+maxTravelPx`), revealing its top edge.
     * - `offsetY = +1f` translates the portrait preview UP (`-maxTravelPx`), revealing its bottom edge.
     */
    fun previewTranslationYPx(
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        offsetY: Float,
        portraitHeightOverWidth: Float = PORTRAIT_SENSOR_HEIGHT_OVER_WIDTH,
    ): Float {
        val maxTravel = previewMaxTravelPx(
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
            portraitHeightOverWidth = portraitHeightOverWidth,
        )
        return -offsetY.coerceIn(-1f, 1f) * maxTravel
    }

    fun previewMaxTravelPx(
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        portraitHeightOverWidth: Float = PORTRAIT_SENSOR_HEIGHT_OVER_WIDTH,
    ): Float {
        val fullPortraitHeight = viewportWidthPx * portraitHeightOverWidth
        return ((fullPortraitHeight - viewportHeightPx) / 2f).coerceAtLeast(0f)
    }

    /**
     * Direct-manipulation drag update: dragging finger DOWN (`dragDeltaPx > 0`) pulls the top
     * of the portrait image into view (`offsetY` moves toward `-1f`).
     */
    fun offsetAfterVerticalDrag(
        currentOffsetY: Float,
        dragDeltaPx: Float,
        maxTravelPx: Float,
    ): Float {
        if (maxTravelPx <= 1f) return currentOffsetY.coerceIn(-1f, 1f)
        val deltaOffset = -dragDeltaPx / maxTravelPx
        return (currentOffsetY + deltaOffset).coerceIn(-1f, 1f)
    }

    fun snapNearCenter(offsetY: Float, threshold: Float = CENTER_SNAP_THRESHOLD): Float {
        val clamped = offsetY.coerceIn(-1f, 1f)
        return if (abs(clamped) <= threshold) 0f else clamped
    }

    fun toEvenAtLeast2(value: Int): Int {
        val clamped = value.coerceAtLeast(2)
        return if (clamped % 2 == 0) clamped else (clamped - 1).coerceAtLeast(2)
    }
}
