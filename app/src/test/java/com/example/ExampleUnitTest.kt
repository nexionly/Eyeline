package com.example

import com.example.capture.HardwareShutterBus
import com.example.data.model.ScriptMetricsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `word count and reading time calculate accurately`() {
        val sample = (1..135).joinToString(" ") { "word" }
        val metrics = ScriptMetricsCalculator.calculate(sample, scrollSpeedLevel = 3.0f)
        assertEquals(135, metrics.wordCount)
        assertTrue(metrics.estimatedSeconds in 55..65)
        assertEquals("01:05", ScriptMetricsCalculator.formatTimer(65L))
    }

    @Test
    fun `empty script produces zero words and zero seconds`() {
        val metrics = ScriptMetricsCalculator.calculate("   \n  ")
        assertEquals(0, metrics.wordCount)
        assertEquals(0, metrics.estimatedSeconds)
        assertEquals("0s", metrics.formattedDuration())
    }

    @Test
    fun `volume down shutter bus only intercepts when active and enabled`() {
        HardwareShutterBus.isStudioCaptureActive = false
        HardwareShutterBus.isVolumeDownShutterEnabled = true
        assertFalse(HardwareShutterBus.shouldInterceptVolumeDown())

        HardwareShutterBus.isStudioCaptureActive = true
        HardwareShutterBus.isVolumeDownShutterEnabled = false
        assertFalse(HardwareShutterBus.shouldInterceptVolumeDown())

        HardwareShutterBus.isStudioCaptureActive = true
        HardwareShutterBus.isVolumeDownShutterEnabled = true
        assertTrue(HardwareShutterBus.shouldInterceptVolumeDown())

        HardwareShutterBus.isStudioCaptureActive = false
    }

    @Test
    fun `video gallery export metadata embeds script title and formatted date`() {
        val metadata = com.example.capture.VideoGalleryExporter.buildExportMetadata(
            scriptTitle = "Morning Hook #1!",
            recordedAtMillis = 1_795_369_200_000L,
            locale = java.util.Locale.US,
        )
        assertEquals("Morning Hook #1!", metadata.title)
        assertTrue(metadata.displayName.startsWith("Morning_Hook_1_"))
        assertTrue(metadata.displayName.endsWith(".mp4"))
        assertTrue(metadata.description.contains("Script: Morning Hook #1!"))
        assertTrue(metadata.description.contains("Recorded"))
        assertEquals(1_795_369_200_000L, metadata.recordedAtMillis)
    }

    @Test
    fun `timeline edit splits, cuts middle segment, skips during playback, and merges ranges`() {
        var state = com.example.capture.TimelineEditState.initial(10_000L)
        assertFalse(state.hasRemovals)
        assertEquals(10_000L, state.keptDurationMs)
        // Cannot cut the single remaining clip
        assertFalse(state.canToggleCut(state.selectedSegmentId))

        // Split at 3s and 6s, then cut the middle [3s..6s] section
        state = state.splitAt(3_000L)
        state = state.splitAt(6_000L)
        state = state.selectSegmentAt(4_500L)
        assertTrue(state.canToggleCut(state.selectedSegmentId))
        state = state.toggleCut(state.selectedSegmentId)

        assertEquals(1, state.cutCount)
        assertEquals(7_000L, state.keptDurationMs)
        assertEquals(listOf(0L..3_000L, 6_000L..10_000L), state.mergedKeptRanges())

        // Live playback coordinator skips cleanly over [3s..6s] and loops at end
        assertEquals(null, state.nextPlaybackPosition(1_500L))
        assertEquals(6_000L, state.nextPlaybackPosition(3_100L))
        assertEquals(0L, state.nextPlaybackPosition(9_990L))

        // Undo restores the cut middle section; uncut split merges into a single continuous export range
        state = state.undo()
        assertFalse(state.hasRemovals)
        assertEquals(listOf(0L..10_000L), state.mergedKeptRanges())
    }

    @Test
    fun `timeline outer trim handles preserve middle cuts and support pulling handles back`() {
        var state = com.example.capture.TimelineEditState.initial(10_000L)
            .splitAt(4_000L)
            .splitAt(6_000L)
            .selectSegmentAt(5_000L)
        state = state.toggleCut(state.selectedSegmentId)

        // Trim leading to 1.5s and trailing to 8.5s
        state = state.recordUndoCheckpoint().trimLeadingTo(1_500L).trimTrailingTo(8_500L)
        assertEquals(1_500L, state.trimStartMs)
        assertEquals(8_500L, state.trimEndMs)
        assertEquals(listOf(1_500L..4_000L, 6_000L..8_500L), state.mergedKeptRanges())

        // Pulling left handle back to 500ms uncovers kept footage while keeping middle cut intact
        state = state.trimLeadingTo(500L)
        assertEquals(500L, state.trimStartMs)
        assertEquals(listOf(500L..4_000L, 6_000L..8_500L), state.mergedKeptRanges())

        // Reset restores full unedited clip
        state = state.reset()
        assertFalse(state.hasEdits)
        assertEquals(10_000L, state.keptDurationMs)
    }

    @Test
    fun `video share helper derives expected FileProvider authority`() {
        assertEquals(
            "com.aistudio.eyeline.vkqrmp.fileprovider",
            com.example.capture.VideoShareHelper.authorityFor("com.aistudio.eyeline.vkqrmp"),
        )
    }

    @Test
    fun `aspect crop math resolves portrait rotation and computes even 16x9 NDC crop bounds`() {
        // 1920x1080 raw sensor rotated 90 degrees => 1080x1920 upright portrait
        val (displayW, displayH) = com.example.capture.AspectCropMath.resolveDisplaySize(
            rawWidth = 1920,
            rawHeight = 1080,
            rotationDegrees = 90,
        )
        assertEquals(1080, displayW)
        assertEquals(1920, displayH)

        // Centered crop (offsetY = 0f)
        val centered = com.example.capture.AspectCropMath.computeWide16x9Crop(displayW, displayH, 0f)
        assertFalse(centered.isNoOp)
        assertEquals(1080, centered.targetWidth)
        assertEquals(608, centered.targetHeight)
        assertEquals(0, centered.targetHeight % 2)
        assertEquals(-1f, centered.left, 0.0001f)
        assertEquals(1f, centered.right, 0.0001f)
        assertEquals(-centered.bottom, centered.top, 0.0001f)
        assertTrue(centered.top > centered.bottom)

        // Top boundary (offsetY = -1f) reaches OpenGL top (+1f); bottom boundary (+1f) reaches -1f
        val topAligned = com.example.capture.AspectCropMath.computeWide16x9Crop(displayW, displayH, -1f)
        assertEquals(1f, topAligned.top, 0.0001f)
        assertTrue(topAligned.bottom > 0f)

        val bottomAligned = com.example.capture.AspectCropMath.computeWide16x9Crop(displayW, displayH, 1f)
        assertEquals(-1f, bottomAligned.bottom, 0.0001f)
        assertTrue(bottomAligned.top < 0f)

        // 720x1280 portrait rounds 405px height to strictly even 404px for hardware MediaCodec safety
        val hdCrop = com.example.capture.AspectCropMath.computeWide16x9Crop(720, 1280, 0f)
        assertEquals(720, hdCrop.targetWidth)
        assertEquals(404, hdCrop.targetHeight)
        assertEquals(0, hdCrop.targetHeight % 2)
    }

    @Test
    fun `aspect crop preview translation and vertical drag match NDC sign convention`() {
        val widthPx = 900f
        val heightPx = widthPx / com.example.capture.AspectCropMath.WIDE_ASPECT_RATIO // 506.25f
        val maxTravel = com.example.capture.AspectCropMath.previewMaxTravelPx(widthPx, heightPx)
        assertTrue(maxTravel > 500f)

        // Dragging finger DOWN (+delta) pulls the top of the frame into view (offset moves negative)
        val afterDownDrag = com.example.capture.AspectCropMath.offsetAfterVerticalDrag(
            currentOffsetY = 0f,
            dragDeltaPx = maxTravel / 2f,
            maxTravelPx = maxTravel,
        )
        assertEquals(-0.5f, afterDownDrag, 0.001f)

        // Negative offset translates the portrait preview DOWN (+Y in Compose) revealing its top
        val translationY = com.example.capture.AspectCropMath.previewTranslationYPx(
            viewportWidthPx = widthPx,
            viewportHeightPx = heightPx,
            offsetY = afterDownDrag,
        )
        assertEquals(maxTravel / 2f, translationY, 0.01f)

        // Center snap cleanly snaps tiny offsets back to 0f
        assertEquals(0f, com.example.capture.AspectCropMath.snapNearCenter(0.02f), 0.0001f)
        assertEquals(0.25f, com.example.capture.AspectCropMath.snapNearCenter(0.25f), 0.0001f)
    }
}
