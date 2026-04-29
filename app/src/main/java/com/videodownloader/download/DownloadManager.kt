package com.videodownloader.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.videodownloader.data.DownloadStatus
import com.videodownloader.data.DownloadTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Manages video downloads using Android's DownloadManager
 */
class DownloadManager(private val context: Context) {

    private val systemDM = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks

    private val activeTasks = mutableMapOf<String, Long>() // taskId -> downloadManagerId

    fun getDownloadDir(): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "VideoDownloader"
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Start downloading a video
     */
    fun startDownload(
        videoUrl: String,
        title: String,
        quality: String
    ): String {
        val taskId = UUID.randomUUID().toString()
        val fileName = "${sanitizeFileName(title)}_${quality}.mp4"

        val request = DownloadManager.Request(Uri.parse(videoUrl))
            .setTitle(title)
            .setDescription("正在下载 $quality 画质")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_MOVIES,
                "VideoDownloader/$fileName"
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadId = systemDM.enqueue(request)
        activeTasks[taskId] = downloadId

        val task = DownloadTask(
            id = taskId,
            url = videoUrl,
            title = title,
            quality = quality,
            status = DownloadStatus.DOWNLOADING,
            filePath = File(getDownloadDir(), fileName).absolutePath
        )
        _tasks.value = _tasks.value + task

        // Start monitoring
        monitorDownload(taskId, downloadId)

        return taskId
    }

    private fun monitorDownload(taskId: String, downloadId: Long) {
        Thread {
            var downloading = true
            while (downloading) {
                val cursor = systemDM.query(DownloadManager.Query().setFilterById(downloadId))
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val bytesTotalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val bytesDLIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)

                    val status = cursor.getInt(statusIdx)
                    val totalBytes = cursor.getLong(bytesTotalIdx)
                    val downloadedBytes = cursor.getLong(bytesDLIdx)
                    val progress = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0

                    when (status) {
                        DownloadManager.STATUS_RUNNING -> {
                            updateTask(taskId) {
                                it.copy(progress = progress, status = DownloadStatus.DOWNLOADING)
                            }
                        }
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            updateTask(taskId) {
                                it.copy(progress = 100, status = DownloadStatus.COMPLETED)
                            }
                            downloading = false
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val reason = cursor.getInt(reasonIdx)
                            updateTask(taskId) {
                                it.copy(
                                    status = DownloadStatus.FAILED,
                                    error = "下载失败 (code: $reason)"
                                )
                            }
                            downloading = false
                        }
                        DownloadManager.STATUS_PAUSED -> {
                            // Continue waiting
                        }
                    }
                    cursor.close()
                } else {
                    downloading = false
                }
                if (downloading) Thread.sleep(500)
            }
            activeTasks.remove(taskId)
        }.start()
    }

    private fun updateTask(taskId: String, update: (DownloadTask) -> DownloadTask) {
        _tasks.value = _tasks.value.map {
            if (it.id == taskId) update(it) else it
        }
    }

    /**
     * Cancel a download
     */
    fun cancelDownload(taskId: String) {
        activeTasks[taskId]?.let { downloadId ->
            systemDM.remove(downloadId)
            activeTasks.remove(taskId)
        }
        updateTask(taskId) { it.copy(status = DownloadStatus.CANCELLED) }
    }

    /**
     * Get the output directory for downloaded videos
     */
    fun getOutputDir(): String = getDownloadDir().absolutePath

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5._\\-\\s]"), "_")
            .take(80)
            .trim()
    }
}
