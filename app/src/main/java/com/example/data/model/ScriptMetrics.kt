package com.example.data.model

import kotlin.math.ceil
import kotlin.math.roundToInt

data class ScriptMetrics(
    val wordCount: Int,
    val estimatedSeconds: Int,
    val estimatedWpm: Int,
) {
    fun formattedDuration(): String {
        if (estimatedSeconds <= 0) return "0s"
        val minutes = estimatedSeconds / 60
        val seconds = estimatedSeconds % 60
        return when {
            minutes == 0 -> "${seconds}s"
            seconds == 0 -> "${minutes}m"
            else -> "${minutes}m ${seconds}s"
        }
    }
}

object ScriptMetricsCalculator {
    private val whitespaceRegex = Regex("\\s+")

    /**
     * Maps scrollSpeedLevel (1f..10f) to an approximate speaking pace in WPM (95..275 WPM),
     * defaulting to ~135 WPM at the default speed level of 3.0.
     */
    fun speedLevelToWpm(scrollSpeedLevel: Float): Int {
        val clamped = scrollSpeedLevel.coerceIn(1f, 10f)
        return (95f + (clamped - 1f) * 20f).roundToInt()
    }

    /**
     * Maps scrollSpeedLevel (1f..10f) and fontSizeSp to smooth vertical scroll pixels-per-second.
     * Calibrated so 3.0x scrolls at ~24 dp/sec (~0.6 lines/sec at 28sp) for natural eye-line reading.
     */
    fun scrollPixelsPerSecond(scrollSpeedLevel: Float, fontSizeSp: Float, density: Float): Float {
        val speedNorm = scrollSpeedLevel.coerceIn(1f, 10f)
        val baseDpPerSecond = 10f + (speedNorm - 1f) * 7.0f
        val fontFactor = (fontSizeSp / 28f).coerceIn(0.75f, 1.55f)
        return baseDpPerSecond * fontFactor * density
    }

    fun calculate(content: String, scrollSpeedLevel: Float = 3.0f): ScriptMetrics {
        val trimmed = content.trim()
        val words = if (trimmed.isEmpty()) 0 else trimmed.split(whitespaceRegex).count { it.isNotBlank() }
        val wpm = speedLevelToWpm(scrollSpeedLevel)
        val seconds = if (words == 0) 0 else ceil((words.toDouble() / wpm.toDouble()) * 60.0).toInt().coerceAtLeast(1)
        return ScriptMetrics(
            wordCount = words,
            estimatedSeconds = seconds,
            estimatedWpm = wpm,
        )
    }

    fun formatTimer(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0L)
        val minutes = safe / 60L
        val seconds = safe % 60L
        return "%02d:%02d".format(minutes, seconds)
    }
}
