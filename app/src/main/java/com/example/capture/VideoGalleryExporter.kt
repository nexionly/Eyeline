package com.example.capture

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class VideoExportMetadata(
    val title: String,
    val displayName: String,
    val formattedDate: String,
    val description: String,
    val recordedAtMillis: Long,
)

sealed interface VideoExportResult {
    data class Success(
        val uri: Uri,
        val metadata: VideoExportMetadata,
    ) : VideoExportResult

    data class Failure(val message: String) : VideoExportResult
}

object VideoGalleryExporter {
    private val unsafeFilenameChars = Regex("[^A-Za-z0-9]+")

    fun buildExportMetadata(
        scriptTitle: String,
        recordedAtMillis: Long = System.currentTimeMillis(),
        locale: Locale = Locale.getDefault(),
    ): VideoExportMetadata {
        val cleanTitle = scriptTitle.trim().ifBlank { "Eyeline Take" }
        val fileSlug = cleanTitle
            .replace(unsafeFilenameChars, "_")
            .trim('_')
            .take(40)
            .ifBlank { "Eyeline" }

        val date = Date(recordedAtMillis)
        val fileStamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(date)
        val readableDate = SimpleDateFormat("MMM d, yyyy · h:mm a", locale).format(date)
        val displayName = "${fileSlug}_$fileStamp.mp4"
        val description = "Script: $cleanTitle · Recorded $readableDate"

        return VideoExportMetadata(
            title = cleanTitle,
            displayName = displayName,
            formattedDate = readableDate,
            description = description,
            recordedAtMillis = recordedAtMillis,
        )
    }

    suspend fun exportToGallery(
        context: Context,
        sourceFile: File,
        scriptTitle: String,
        recordedAtMillis: Long = System.currentTimeMillis(),
    ): VideoExportResult = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return@withContext VideoExportResult.Failure("Recorded video file was not found.")
        }

        val metadata = buildExportMetadata(
            scriptTitle = scriptTitle,
            recordedAtMillis = recordedAtMillis,
        )
        val epochSeconds = metadata.recordedAtMillis / 1000L

        return@withContext try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, metadata.displayName)
                    put(MediaStore.Video.Media.TITLE, metadata.title)
                    put(MediaStore.Video.Media.DESCRIPTION, metadata.description)
                    put(MediaStore.Video.Media.TAGS, metadata.title)
                    put(MediaStore.Video.Media.ALBUM, "Eyeline")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.DATE_TAKEN, metadata.recordedAtMillis)
                    put(MediaStore.Video.Media.DATE_ADDED, epochSeconds)
                    put(MediaStore.Video.Media.DATE_MODIFIED, epochSeconds)
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_MOVIES}/Eyeline",
                    )
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val targetUri = resolver.insert(collection, values)
                    ?: return@withContext VideoExportResult.Failure("Unable to create gallery entry.")

                try {
                    resolver.openOutputStream(targetUri)?.use { outStream ->
                        sourceFile.inputStream().use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    } ?: throw IOException("Failed to open MediaStore output stream.")

                    val finalizeValues = ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                        put(MediaStore.Video.Media.DATE_TAKEN, metadata.recordedAtMillis)
                        put(MediaStore.Video.Media.DATE_MODIFIED, epochSeconds)
                    }
                    resolver.update(targetUri, finalizeValues, null, null)
                    VideoExportResult.Success(targetUri, metadata)
                } catch (e: Exception) {
                    resolver.delete(targetUri, null, null)
                    VideoExportResult.Failure(e.localizedMessage ?: "Failed writing video to gallery.")
                }
            } else {
                // Scoped external app movies directory for API 24-28 (requires zero storage permissions)
                val moviesDir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                    "Eyeline",
                ).apply { mkdirs() }
                val destFile = File(moviesDir, metadata.displayName)
                sourceFile.copyTo(destFile, overwrite = true)
                destFile.setLastModified(metadata.recordedAtMillis)
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destFile.absolutePath),
                    arrayOf("video/mp4"),
                    null,
                )
                VideoExportResult.Success(Uri.fromFile(destFile), metadata)
            }
        } catch (e: Exception) {
            VideoExportResult.Failure(e.localizedMessage ?: "Could not save video.")
        }
    }
}
