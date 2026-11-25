package com.example.capture

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Hardware-accelerated on-device 16:9 widescreen cropper powered by Jetpack Media3 [Transformer]
 * and [Crop]. Converts a portrait-held CameraX recording into a native horizontal 16:9 MP4 using
 * the user's chosen vertical crop offset (`-1f..+1f`).
 */
object VideoAspectCropper {

    @OptIn(UnstableApi::class)
    suspend fun cropToWide16x9(
        context: Context,
        sourceFile: File,
        outputFile: File,
        offsetY: Float,
        onProgress: (Float) -> Unit,
    ): VideoRenderResult {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return VideoRenderResult.Failure("Recorded video file was missing.")
        }

        // Probe rotation-adjusted display dimensions off the main thread
        val (displayWidth, displayHeight) = withContext(Dispatchers.IO) {
            probeDisplayDimensions(sourceFile)
        }
        val cropSpec = AspectCropMath.computeWide16x9Crop(
            displayWidth = displayWidth,
            displayHeight = displayHeight,
            offsetY = offsetY,
        )

        if (cropSpec.isNoOp) {
            val copied = withContext(Dispatchers.IO) {
                runCatching {
                    sourceFile.copyTo(outputFile, overwrite = true)
                    outputFile.exists() && outputFile.length() > 0L
                }.getOrDefault(false)
            }
            return if (copied) {
                onProgress(1f)
                VideoRenderResult.Success(outputFile)
            } else {
                VideoRenderResult.Failure("Could not prepare widescreen video.")
            }
        }

        // Media3 Transformer must be created, started, queried, and cancelled on the Main Looper
        return withContext(Dispatchers.Main.immediate) {
            if (outputFile.exists()) {
                runCatching { outputFile.delete() }
            }

            val cropEffect = Crop(
                cropSpec.left,
                cropSpec.right,
                cropSpec.bottom,
                cropSpec.top,
            )
            // Enforce strictly even hardware-encoder dimensions matching the 16:9 crop output
            val presentationEffect = Presentation.createForWidthAndHeight(
                cropSpec.targetWidth,
                cropSpec.targetHeight,
                Presentation.LAYOUT_SCALE_TO_FIT,
            )

            val editedItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(sourceFile)))
                .setEffects(
                    Effects(
                        /* audioProcessors= */ emptyList(),
                        /* videoEffects= */ listOf(cropEffect, presentationEffect),
                    )
                )
                .build()

            suspendCancellableCoroutine { continuation ->
                var progressJob: Job? = null
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
                                    VideoRenderResult.Failure("Widescreen video output was empty.")
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
                                ?: "Could not frame video to 16:9."
                            continuation.resume(VideoRenderResult.Failure(detail))
                        }
                    }
                }

                try {
                    val builtTransformer = Transformer.Builder(context.applicationContext)
                        .addListener(listener)
                        .build()
                    transformer = builtTransformer

                    onProgress(0.04f)
                    builtTransformer.start(editedItem, outputFile.absolutePath)

                    val progressHolder = ProgressHolder()
                    progressJob = CoroutineScope(Dispatchers.Main.immediate).launch {
                        while (isActive) {
                            val state = builtTransformer.getProgress(progressHolder)
                            if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                                val fraction = (progressHolder.progress / 100f).coerceIn(0.04f, 0.99f)
                                onProgress(fraction)
                            }
                            delay(80L)
                        }
                    }
                } catch (e: Exception) {
                    progressJob?.cancel()
                    runCatching { outputFile.delete() }
                    if (continuation.isActive) {
                        continuation.resume(
                            VideoRenderResult.Failure(
                                e.localizedMessage ?: "Unable to start 16:9 video framing."
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

    private fun probeDisplayDimensions(file: File): Pair<Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 1080
            val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 1920
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            AspectCropMath.resolveDisplaySize(rawW, rawH, rotation)
        } catch (_: Exception) {
            1080 to 1920
        } finally {
            runCatching { retriever.release() }
        }
    }
}
