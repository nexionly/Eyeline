package com.example.capture

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.math.sqrt

sealed interface WaveformLoadState {
    data object Loading : WaveformLoadState
    data class Ready(val amplitudes: List<Float>) : WaveformLoadState
    data object Unavailable : WaveformLoadState
}

/**
 * Decodes the audio track of a recorded MP4 file on [Dispatchers.IO] using Android's
 * built-in [MediaExtractor] and [MediaCodec], downsampling PCM samples into normalized
 * RMS amplitude bars so speech pauses and verbal stumbles are immediately visible.
 */
object AudioWaveformExtractor {

    const val DEFAULT_BAR_COUNT = 96

    suspend fun extractDurationMs(file: File): Long = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext 0L
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun extract(
        file: File,
        barCount: Int = DEFAULT_BAR_COUNT,
    ): WaveformLoadState = withContext(Dispatchers.IO) {
        if (!file.exists() || barCount <= 0) return@withContext WaveformLoadState.Unavailable

        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null) {
                return@withContext WaveformLoadState.Unavailable
            }

            val mime = audioFormat.getString(MediaFormat.KEY_MIME)
                ?: return@withContext WaveformLoadState.Unavailable

            val durationUs = when {
                audioFormat.containsKey(MediaFormat.KEY_DURATION) ->
                    audioFormat.getLong(MediaFormat.KEY_DURATION)
                else -> extractDurationMs(file) * 1_000L
            }.coerceAtLeast(1_000L)

            extractor.selectTrack(audioTrackIndex)
            decoder = MediaCodec.createDecoderByType(mime).apply {
                configure(audioFormat, null, null, 0)
                start()
            }

            val sumSquares = DoubleArray(barCount)
            val sampleCounts = LongArray(barCount)
            val bufferInfo = MediaCodec.BufferInfo()

            var inputDone = false
            var outputDone = false
            var loopSafety = 0
            val maxLoops = 25_000
            val usPerBucket = (durationUs.toDouble() / barCount.toDouble()).coerceAtLeast(1.0)

            while (!outputDone && loopSafety < maxLoops && currentCoroutineContext().isActive) {
                loopSafety++

                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(5_000L)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                inputDone = true
                            } else {
                                val ptsUs = extractor.sampleTime.coerceAtLeast(0L)
                                decoder.queueInputBuffer(inputIndex, 0, sampleSize, ptsUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 5_000L)
                when {
                    outputIndex >= 0 -> {
                        val outputBuffer = decoder.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val shortBuffer = outputBuffer
                                .slice()
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .asShortBuffer()
                            val totalShorts = shortBuffer.remaining()
                            if (totalShorts > 0) {
                                val bucketIndex = (bufferInfo.presentationTimeUs / usPerBucket)
                                    .toInt()
                                    .coerceIn(0, barCount - 1)
                                // Stride sampling keeps decode fast while preserving speech envelope
                                val stride = if (totalShorts > 512) 4 else 2
                                var idx = 0
                                while (idx < totalShorts) {
                                    val normalized = shortBuffer.get(idx) / 32768.0
                                    sumSquares[bucketIndex] += normalized * normalized
                                    sampleCounts[bucketIndex]++
                                    idx += stride
                                }
                            }
                        }
                        decoder.releaseOutputBuffer(outputIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }

                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (inputDone) {
                            // Give codec a brief chance to flush remaining frames
                            loopSafety += 20
                        }
                    }
                }
            }

            val totalSamples = sampleCounts.sum()
            if (totalSamples == 0L) {
                return@withContext WaveformLoadState.Unavailable
            }

            val rawRms = DoubleArray(barCount) { idx ->
                if (sampleCounts[idx] > 0L) {
                    sqrt(sumSquares[idx] / sampleCounts[idx].toDouble())
                } else {
                    0.0
                }
            }

            // Fill any empty sparse buckets via immediate neighbor interpolation
            for (i in 0 until barCount) {
                if (sampleCounts[i] == 0L) {
                    val left = if (i > 0 && sampleCounts[i - 1] > 0L) rawRms[i - 1] else 0.0
                    val right = if (i < barCount - 1 && sampleCounts[i + 1] > 0L) rawRms[i + 1] else left
                    rawRms[i] = (left + right) * 0.5
                }
            }

            val peak = rawRms.maxOrNull()?.takeIf { it > 0.0005 } ?: 1.0
            val bars = List(barCount) { idx ->
                val relative = (rawRms[idx] / peak).coerceIn(0.0, 1.0)
                // Perceptual curve: keeps quiet gaps noticeably low while boosting speech clarity
                val shaped = relative.pow(0.68).toFloat()
                if (relative < 0.04) 0.06f else shaped.coerceIn(0.08f, 1.0f)
            }

            WaveformLoadState.Ready(bars)
        } catch (_: Exception) {
            WaveformLoadState.Unavailable
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
        }
    }
}
