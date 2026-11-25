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
}
