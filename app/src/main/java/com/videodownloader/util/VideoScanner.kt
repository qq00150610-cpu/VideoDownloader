package com.videodownloader.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.videodownloader.data.LocalVideo
import java.io.File

/**
 * Scans for local video files on the device
 */
object VideoScanner {

    /**
     * Scan for videos in the app's download directory
     */
    fun scanDownloadedVideos(context: Context): List<LocalVideo> {
        val videos = mutableListOf<LocalVideo>()

        // Scan app-specific download folder
        val downloadDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "VideoDownloader"
        )
        if (downloadDir.exists()) {
            downloadDir.listFiles()?.filter {
                it.extension.lowercase() in listOf("mp4", "mkv", "webm", "avi", "3gp")
            }?.forEach { file ->
                videos.add(
                    LocalVideo(
                        id = file.hashCode().toLong(),
                        title = file.nameWithoutExtension,
                        path = file.absolutePath,
                        uri = Uri.fromFile(file).toString(),
                        size = file.length(),
                        dateAdded = file.lastModified()
                    )
                )
            }
        }

        return videos.sortedByDescending { it.dateAdded }
    }

    /**
     * Scan all videos on the device using MediaStore
     */
    fun scanAllVideos(context: Context): List<LocalVideo> {
        val videos = mutableListOf<LocalVideo>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Unknown"
                val data = cursor.getString(dataColumn) ?: ""
                val duration = cursor.getLong(durationColumn)
                val size = cursor.getLong(sizeColumn)
                val date = cursor.getLong(dateColumn)

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                )

                videos.add(
                    LocalVideo(
                        id = id,
                        title = name,
                        path = data,
                        uri = contentUri.toString(),
                        duration = duration,
                        size = size,
                        dateAdded = date
                    )
                )
            }
        }

        return videos
    }

    /**
     * Format file size to human-readable string
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }

    /**
     * Format duration millis to mm:ss or hh:mm:ss
     */
    fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
}
