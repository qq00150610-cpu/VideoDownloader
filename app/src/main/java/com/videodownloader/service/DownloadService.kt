package com.videodownloader.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.videodownloader.MainActivity
import com.videodownloader.R
import com.videodownloader.VideoDownloaderApp
import com.videodownloader.data.DownloadStatus
import com.videodownloader.data.DownloadTask
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Foreground service for downloading videos with progress notifications
 */
class DownloadService : Service() {

    companion object {
        const val ACTION_DOWNLOAD = "com.videodownloader.DOWNLOAD"
        const val ACTION_CANCEL = "com.videodownloader.CANCEL"
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_TASK_ID = "task_id"

        private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
        val tasks: StateFlow<List<DownloadTask>> = _tasks

        fun cancelTask(taskId: String) {
            _tasks.value = _tasks.value.map {
                if (it.id == taskId) it.copy(status = DownloadStatus.CANCELLED) else it
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val activeJobs = mutableMapOf<String, Job>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Video"
                val quality = intent.getStringExtra(EXTRA_QUALITY) ?: "HD"
                startDownload(url, title, quality)
            }
            ACTION_CANCEL -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return START_NOT_STICKY
                cancelDownload(taskId)
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownload(url: String, title: String, quality: String) {
        val taskId = UUID.randomUUID().toString()
        val task = DownloadTask(
            id = taskId,
            url = url,
            title = title,
            quality = quality,
            status = DownloadStatus.DOWNLOADING
        )
        _tasks.value = _tasks.value + task

        startForeground(taskId.hashCode(), buildNotification(task))

        val job = serviceScope.launch {
            try {
                downloadVideo(task)
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    updateTask(taskId) {
                        it.copy(status = DownloadStatus.FAILED, error = e.message)
                    }
                    updateNotification(buildNotification(
                        task.copy(status = DownloadStatus.FAILED, error = e.message)
                    ))
                }
            }
            activeJobs.remove(taskId)
            if (activeJobs.isEmpty()) {
                stopSelf()
            }
        }
        activeJobs[taskId] = job
    }

    private suspend fun downloadVideo(task: DownloadTask) {
        val request = Request.Builder()
            .url(task.url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            updateTask(task.id) {
                it.copy(status = DownloadStatus.FAILED, error = "HTTP ${response.code}")
            }
            return
        }

        val body = response.body ?: run {
            updateTask(task.id) {
                it.copy(status = DownloadStatus.FAILED, error = "空响应")
            }
            return
        }

        val downloadDir = File(
            getExternalFilesDir(null)?.parentFile?.parentFile?.parentFile,
            "Movies/VideoDownloader"
        )
        if (!downloadDir.exists()) downloadDir.mkdirs()

        val fileName = "${task.title.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5._\\-]"), "_")}_${task.quality}.mp4"
        val file = File(downloadDir, fileName)
        val tempFile = File(downloadDir, "$fileName.tmp")

        val totalBytes = body.contentLength()
        var downloadedBytes = 0L

        body.byteStream().use { input ->
            FileOutputStream(tempFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var lastUpdateTime = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    // Check for cancellation
                    if (_tasks.value.find { it.id == task.id }?.status == DownloadStatus.CANCELLED) {
                        tempFile.delete()
                        return
                    }

                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastUpdateTime > 300) { // Update every 300ms
                        val progress = if (totalBytes > 0) {
                            ((downloadedBytes * 100) / totalBytes).toInt()
                        } else 0

                        updateTask(task.id) {
                            it.copy(progress = progress)
                        }
                        updateNotification(buildNotification(
                            task.copy(progress = progress)
                        ))
                        lastUpdateTime = now
                    }
                }
            }
        }

        // Rename temp to final
        if (tempFile.exists()) {
            tempFile.renameTo(file)
        }

        updateTask(task.id) {
            it.copy(
                progress = 100,
                status = DownloadStatus.COMPLETED,
                filePath = file.absolutePath
            )
        }
        updateNotification(buildNotification(
            task.copy(progress = 100, status = DownloadStatus.COMPLETED)
        ))

        // Scan media
        android.media.MediaScannerConnection.scanFile(
            this,
            arrayOf(file.absolutePath),
            arrayOf("video/mp4"),
            null
        )
    }

    private fun cancelDownload(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        updateTask(taskId) { it.copy(status = DownloadStatus.CANCELLED) }
    }

    private fun updateTask(taskId: String, update: (DownloadTask) -> DownloadTask) {
        _tasks.value = _tasks.value.map {
            if (it.id == taskId) update(it) else it
        }
    }

    private fun buildNotification(task: DownloadTask): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, VideoDownloaderApp.DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(task.title)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        when (task.status) {
            DownloadStatus.DOWNLOADING -> {
                builder.setContentText("下载中 ${task.progress}%")
                    .setProgress(100, task.progress, false)
                    .addAction(
                        android.R.drawable.ic_delete,
                        "取消",
                        createCancelIntent(task.id)
                    )
            }
            DownloadStatus.COMPLETED -> {
                builder.setContentText("下载完成 ✓")
                    .setProgress(0, 0, false)
                    .setOngoing(false)
            }
            DownloadStatus.FAILED -> {
                builder.setContentText("下载失败: ${task.error ?: "未知错误"}")
                    .setProgress(0, 0, false)
                    .setOngoing(false)
            }
            else -> {}
        }

        return builder.build()
    }

    private fun createCancelIntent(taskId: String): PendingIntent {
        val intent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getService(
            this, taskId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun updateNotification(notification: Notification) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(0, notification)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
