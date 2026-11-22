package com.example.capture

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.sqrt

data class AudioInputSnapshot(
    val level: Float,
    val bars: List<Float>,
    val isStreamActive: Boolean,
)

/**
 * Samples the live camcorder microphone stream (matching CameraX VideoCapture's CAMCORDER source,
 * with MIC fallback) during preview so creators can confirm active audio input before recording.
 * Releases cleanly before CameraX starts recording to hand off directly to CameraX AudioStats.
 */
class PreRollAudioMonitor {
    private var monitorJob: Job? = null

    @Volatile
    private var activeRecord: AudioRecord? = null

    @SuppressLint("MissingPermission")
    fun start(
        scope: CoroutineScope,
        barCount: Int = 7,
        onSnapshot: (AudioInputSnapshot) -> Unit,
    ) {
        if (monitorJob?.isActive == true) return

        monitorJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            if (minBuf <= 0) {
                onSnapshot(AudioInputSnapshot(0f, List(barCount) { 0f }, isStreamActive = false))
                return@launch
            }

            val bufferSize = (minBuf * 2).coerceAtLeast(1024)
            var record: AudioRecord? = null
            try {
                record = createInitializedRecord(sampleRate, channelConfig, audioFormat, bufferSize)
                if (record == null) {
                    onSnapshot(AudioInputSnapshot(0f, List(barCount) { 0f }, isStreamActive = false))
                    return@launch
                }

                activeRecord = record
                record.startRecording()

                val pcmBuffer = ShortArray(560)
                val smoothedBars = FloatArray(barCount)
                var smoothedOverall = 0f

                onSnapshot(AudioInputSnapshot(0f, List(barCount) { 0f }, isStreamActive = true))

                while (isActive) {
                    val readCount = record.read(pcmBuffer, 0, pcmBuffer.size)
                    if (readCount > 0) {
                        val segmentSize = (readCount / barCount).coerceAtLeast(1)
                        var overallSumSq = 0.0

                        for (barIdx in 0 until barCount) {
                            val start = barIdx * segmentSize
                            val end = if (barIdx == barCount - 1) readCount else (start + segmentSize).coerceAtMost(readCount)
                            var segSumSq = 0.0
                            var count = 0
                            for (i in start until end) {
                                val s = pcmBuffer[i].toDouble()
                                segSumSq += s * s
                                overallSumSq += s * s
                                count++
                            }
                            val segRms = if (count > 0) sqrt(segSumSq / count) else 0.0
                            val segDb = if (segRms > 1.0) 20.0 * log10(segRms / 32767.0) else -65.0
                            val segNorm = ((segDb + 54.0) / 48.0).toFloat().coerceIn(0f, 1f)

                            // Gentle center-weighted shaping keeps the minimalist bars balanced
                            val centerDist = kotlin.math.abs(barIdx - (barCount - 1) / 2f) / ((barCount - 1) / 2f)
                            val weighted = (segNorm * (1f - centerDist * 0.22f)).coerceIn(0f, 1f)

                            smoothedBars[barIdx] = if (weighted > smoothedBars[barIdx]) {
                                smoothedBars[barIdx] * 0.35f + weighted * 0.65f
                            } else {
                                smoothedBars[barIdx] * 0.74f + weighted * 0.26f
                            }
                        }

                        val overallRms = sqrt(overallSumSq / readCount)
                        val overallDb = if (overallRms > 1.0) 20.0 * log10(overallRms / 32767.0) else -65.0
                        val overallNorm = ((overallDb + 54.0) / 48.0).toFloat().coerceIn(0f, 1f)
                        smoothedOverall = if (overallNorm > smoothedOverall) {
                            smoothedOverall * 0.42f + overallNorm * 0.58f
                        } else {
                            smoothedOverall * 0.76f + overallNorm * 0.24f
                        }

                        onSnapshot(
                            AudioInputSnapshot(
                                level = smoothedOverall,
                                bars = smoothedBars.toList(),
                                isStreamActive = true,
                            )
                        )
                    }
                }
            } catch (_: SecurityException) {
                onSnapshot(AudioInputSnapshot(0f, List(barCount) { 0f }, isStreamActive = false))
            } catch (_: IllegalStateException) {
                onSnapshot(AudioInputSnapshot(0f, List(barCount) { 0f }, isStreamActive = false))
            } finally {
                try {
                    record?.stop()
                } catch (_: Exception) {
                    // Safe ignore
                }
                try {
                    record?.release()
                } catch (_: Exception) {
                    // Safe ignore
                }
                if (activeRecord === record) {
                    activeRecord = null
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun createInitializedRecord(
        sampleRate: Int,
        channelConfig: Int,
        audioFormat: Int,
        bufferSize: Int,
    ): AudioRecord? {
        // Prefer CAMCORDER (the exact audio source used by CameraX VideoCapture), falling back to MIC
        val sources = intArrayOf(MediaRecorder.AudioSource.CAMCORDER, MediaRecorder.AudioSource.MIC)
        for (source in sources) {
            val candidate = runCatching {
                AudioRecord(source, sampleRate, channelConfig, audioFormat, bufferSize)
            }.getOrNull()
            if (candidate != null && candidate.state == AudioRecord.STATE_INITIALIZED) {
                return candidate
            }
            candidate?.release()
        }
        return null
    }

    suspend fun stopAndRelease() {
        val job = monitorJob
        monitorJob = null
        try {
            activeRecord?.stop()
        } catch (_: Exception) {
            // Safe ignore
        }
        job?.cancelAndJoin()
    }

    fun stopImmediately() {
        val job = monitorJob
        monitorJob = null
        try {
            activeRecord?.stop()
        } catch (_: Exception) {
            // Safe ignore
        }
        job?.cancel()
    }
}
