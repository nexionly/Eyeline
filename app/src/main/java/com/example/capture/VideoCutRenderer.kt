package com.example.capture

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

sealed interface VideoRenderResult {
    data class Success(val outputFile: File) : VideoRenderResult
    data class Failure(val message: String) : VideoRenderResult
}

/**
 * Hardware-accelerated on-device MP4 cut renderer built on Jetpack Media3 [Transformer].
 * Clips each kept [LongRange] from [sourceFile] and concatenates them cleanly into a single
 * contiguous MP4 in the app's cache directory while streaming progress updates.
 */
object VideoCutRenderer {

    @OptIn(UnstableApi::class)
    suspend fun renderKeptRanges(
        context: Context,
        sourceFile: File,
        keptRanges: List<LongRange>,
        outputFile: File,
        onProgress: (Float) -> Unit,
    ): VideoRenderResult {
        if (!sourceFile.exists() || keptRanges.isEmpty()) {
            return VideoRenderResult.Failure("No valid video clips to render.")
        }

        // Media3 Transformer must be created, started, queried, and cancelled on the Main Looper
        return withContext(Dispatchers.Main.immediate) {
            if (outputFile.exists()) {
                runCatching { outputFile.delete() }
            }

            val sourceUri = Uri.fromFile(sourceFile)
            val editedItems = keptRanges.map { range ->
                val clipping = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(range.first.coerceAtLeast(0L))
                    .setEndPositionMs(range.last.coerceAtLeast(range.first + 1L))
                    .build()
                val clippedItem = MediaItem.Builder()
                    .setUri(sourceUri)
                    .setClippingConfiguration(clipping)
                    .build()
                EditedMediaItem.Builder(clippedItem).build()
            }

            val sequence = EditedMediaItemSequence(editedItems)
            val composition = Composition.Builder(sequence).build()

            suspendCancellableCoroutine { continuation ->
                var progressJob: kotlinx.coroutines.Job? = null
                var transformer: Transformer? = null

                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        progressJob?.cancel()
                        onProgress(1f)
                        if (continuation.isActive) {
                            if (outputFile.exists() && outputFile.length() > 0L) {
                                continuation.resume(VideoRenderResult.Success(outputFile))
                            } else {
                                continuation.resume(
                                    VideoRenderResult.Failure("Rendered video file was empty.")
                                )
                            }
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        progressJob?.cancel()
                        runCatching { outputFile.delete() }
                        if (continuation.isActive) {
                            val detail = exportException.localizedMessage
                                ?.takeIf { it.isNotBlank() }
                                ?: "Failed to render edited video."
                            continuation.resume(VideoRenderResult.Failure(detail))
                        }
                    }
                }

                try {
                    val builtTransformer = Transformer.Builder(context.applicationContext)
                        .addListener(listener)
                        .build()
                    transformer = builtTransformer

                    onProgress(0.02f)
                    builtTransformer.start(composition, outputFile.absolutePath)

                    val progressHolder = ProgressHolder()
                    progressJob = kotlinx.coroutines.CoroutineScope(Dispatchers.Main.immediate).launch {
                        while (isActive) {
                            val state = builtTransformer.getProgress(progressHolder)
                            if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                                val fraction = (progressHolder.progress / 100f).coerceIn(0.02f, 0.99f)
                                onProgress(fraction)
                            }
                            delay(90L)
                        }
                    }
                } catch (e: Exception) {
                    progressJob?.cancel()
                    runCatching { outputFile.delete() }
                    if (continuation.isActive) {
                        continuation.resume(
                            VideoRenderResult.Failure(
                                e.localizedMessage ?: "Unable to start video rendering."
                            )
                        )
                    }
                }

                continuation.invokeOnCancellation {
                    progressJob?.cancel()
                    runCatching { transformer?.cancel() }
                    runCatching { outputFile.delete() }
                }
            }
        }
    }
}
