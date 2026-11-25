package com.example.capture

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed interface VideoShareResult {
    data class Ready(
        val chooserIntent: Intent,
        val metadata: VideoExportMetadata,
    ) : VideoShareResult

    data class Failure(val message: String) : VideoShareResult
}

/**
 * Prepares a cleanly named MP4 take in the app's FileProvider cache directory and builds
 * a standard Android System Share Sheet intent (`Intent.ACTION_SEND`) with proper read URI
 * grants and `ClipData` preview support for external apps.
 */
object VideoShareHelper {

    private const val SHARED_TAKES_DIR = "shared_takes"

    fun authorityFor(packageName: String): String = "$packageName.fileprovider"

    /**
     * Stages [sourceFile] under a script-titled filename inside `cacheDir/shared_takes/`
     * so receiving apps display a meaningful filename, and builds the system chooser [Intent].
     */
    suspend fun prepareShareIntent(
        context: Context,
        sourceFile: File,
        scriptTitle: String,
        recordedAtMillis: Long,
        chooserTitle: String,
    ): VideoShareResult = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return@withContext VideoShareResult.Failure("Recorded video file was not found.")
        }

        val metadata = VideoGalleryExporter.buildExportMetadata(
            scriptTitle = scriptTitle,
            recordedAtMillis = recordedAtMillis,
        )

        try {
            val sharedDir = File(context.cacheDir, SHARED_TAKES_DIR).apply { mkdirs() }
            // Prune stale previously staged shares to keep cache lean
            sharedDir.listFiles()?.forEach { existing ->
                if (existing.name != metadata.displayName) {
                    runCatching { existing.delete() }
                }
            }

            val stagedFile = File(sharedDir, metadata.displayName)
            sourceFile.copyTo(stagedFile, overwrite = true)
            stagedFile.setLastModified(metadata.recordedAtMillis)

            val contentUri = FileProvider.getUriForFile(
                context,
                authorityFor(context.packageName),
                stagedFile,
            )

            val chooserIntent = createChooserIntent(
                context = context,
                contentUri = contentUri,
                metadata = metadata,
                chooserTitle = chooserTitle,
            )
            VideoShareResult.Ready(chooserIntent = chooserIntent, metadata = metadata)
        } catch (e: Exception) {
            VideoShareResult.Failure(e.localizedMessage ?: "Unable to prepare video for sharing.")
        }
    }

    fun createChooserIntent(
        context: Context,
        contentUri: Uri,
        metadata: VideoExportMetadata,
        chooserTitle: String,
    ): Intent {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_TITLE, metadata.title)
            putExtra(Intent.EXTRA_SUBJECT, metadata.title)
            clipData = ClipData.newUri(context.contentResolver, metadata.title, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(sendIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    fun launchChooser(context: Context, chooserIntent: Intent): String? {
        return try {
            context.startActivity(chooserIntent)
            null
        } catch (_: ActivityNotFoundException) {
            "No compatible app found to share this video."
        } catch (e: Exception) {
            e.localizedMessage ?: "Could not open the system share sheet."
        }
    }
}
