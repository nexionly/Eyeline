package com.example.capture

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Synthesizes crisp, audible studio chimes for the 3-2-1 countdown using AudioTrack MODE_STREAM
 * with trailing silence padding so AudioFlinger and emulator audio pipelines flush the full
 * tone cleanly without clipping or premature cutoff. Falls back to ToneGenerator if needed.
 */
object CountdownSoundPlayer {
    private const val SAMPLE_RATE = 44100
    private const val TAIL_SILENCE_MS = 280

    suspend fun playTick(secondsRemaining: Int) = withContext(Dispatchers.IO) {
        val playedWithAudioTrack = playSynthesizedChime(secondsRemaining)
        if (!playedWithAudioTrack) {
            playToneGeneratorFallback(secondsRemaining)
        }
    }

    private suspend fun playSynthesizedChime(secondsRemaining: Int): Boolean {
        val (frequencyHz, toneDurationMs, gain) = when (secondsRemaining) {
            1 -> Triple(1046.50, 175, 0.85) // C6 — bright final cue before recording starts
            else -> Triple(783.99, 145, 0.78) // G5 — crisp warm studio pip for 3 & 2
        }

        val toneSamples = (SAMPLE_RATE * toneDurationMs) / 1000
        val tailSamples = (SAMPLE_RATE * TAIL_SILENCE_MS) / 1000
        val totalSamples = toneSamples + tailSamples
        val pcm = ShortArray(totalSamples)
        val attackSamples = (SAMPLE_RATE * 0.006).toInt().coerceAtLeast(1)

        for (i in 0 until toneSamples) {
            val t = i.toDouble() / SAMPLE_RATE.toDouble()
            val attack = if (i < attackSamples) i.toDouble() / attackSamples else 1.0
            val progress = i.toDouble() / toneSamples.toDouble()
            val decay = exp(-2.8 * progress) * (1.0 - progress * progress)
            // Warm studio bell harmonic blend (fundamental + subtle 2nd harmonic)
            val wave = sin(2.0 * PI * frequencyHz * t) * 0.84 +
                sin(2.0 * PI * (frequencyHz * 2.0) * t) * 0.16
            val sample = (wave * attack * decay * gain * Short.MAX_VALUE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            pcm[i] = sample.toShort()
        }
        // Remaining tailSamples stay 0 (silence) so the audio pipeline drains the full chime cleanly

        val minBufBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufBytes <= 0) return false

        val bufferBytes = maxOf(minBufBytes * 2, pcm.size * 2)
        var track: AudioTrack? = null
        return try {
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                false
            } else {
                track.setVolume(AudioTrack.getMaxVolume())
                track.play()
                val written = track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
                if (written <= 0) {
                    false
                } else {
                    // Allow hardware/emulator mixer to finish draining the padded tone cleanly
                    delay((toneDurationMs + TAIL_SILENCE_MS + 120).toLong())
                    true
                }
            }
        } catch (_: Exception) {
            false
        } finally {
            runCatching { track?.stop() }
            runCatching { track?.release() }
        }
    }

    private suspend fun playToneGeneratorFallback(secondsRemaining: Int) {
        var toneGen: ToneGenerator? = null
        try {
            toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
            val toneType = if (secondsRemaining == 1) {
                ToneGenerator.TONE_PROP_ACK
            } else {
                ToneGenerator.TONE_PROP_BEEP
            }
            val durationMs = if (secondsRemaining == 1) 170 else 130
            toneGen.startTone(toneType, durationMs)
            delay((durationMs + 200).toLong())
        } catch (_: Exception) {
            // Safe ignore if device has no audio output
        } finally {
            runCatching { toneGen?.release() }
        }
    }
}
